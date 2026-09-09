#!/usr/bin/env python3
"""
AmbiGoveeTV cross-platform desktop installer.

The packaged app opens a local browser UI, downloads official Android Platform
Tools from Google and the latest AmbiGoveeTV APK from GitHub Releases, installs
the app through ADB, attaches it to the active Android TV user profile and
launches the Android TV interface.

The release workflow packages this script with PyInstaller, so end users do not
need Python installed.
"""

from __future__ import annotations

import http.server
import ipaddress
import json
import os
import platform
import shutil
import socket
import socketserver
import subprocess
import sys
import threading
import time
import urllib.request
import webbrowser
import zipfile
from pathlib import Path
from typing import Any

PACKAGE = "fr.ambigovee.tv"
MAIN_ACTIVITY = "fr.ambigovee.tv/.MainActivity"
APK_URL = "https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV.apk"

TOOLS_URLS = {
    "Windows": "https://dl.google.com/android/repository/platform-tools-latest-windows.zip",
    "Darwin": "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip",
    "Linux": "https://dl.google.com/android/repository/platform-tools-latest-linux.zip",
}

APP_VERSION = "1.0"


class InstallerError(Exception):
    def __init__(self, code: str, detail: str = ""):
        super().__init__(detail or code)
        self.code = code
        self.detail = detail


class State:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.value: dict[str, Any] = {
            "stage": "ready",
            "progress": 0,
            "detail": "",
            "busy": False,
            "os": platform.system(),
        }

    def set(self, stage: str, progress: int, detail: str = "", busy: bool = True) -> None:
        with self.lock:
            self.value = {
                "stage": stage,
                "progress": max(0, min(100, int(progress))),
                "detail": detail,
                "busy": busy,
                "os": platform.system(),
            }

    def get(self) -> dict[str, Any]:
        with self.lock:
            return dict(self.value)


STATE = State()


def cache_root() -> Path:
    if platform.system() == "Windows":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    elif platform.system() == "Darwin":
        base = Path.home() / "Library" / "Caches"
    else:
        base = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache"))
    path = base / "AmbiGoveeTV" / "Installer"
    path.mkdir(parents=True, exist_ok=True)
    return path


def validate_tv_ip(value: str) -> str:
    value = (value or "").strip()
    try:
        ip = ipaddress.ip_address(value)
    except ValueError as exc:
        raise InstallerError("invalid_ip") from exc

    if ip.version != 4 or not ip.is_private or ip.is_loopback or ip.is_link_local:
        raise InstallerError("invalid_ip")
    return str(ip)


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temp = destination.with_suffix(destination.suffix + ".part")
    if temp.exists():
        temp.unlink()

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "AmbiGoveeTV-Installer/" + APP_VERSION},
    )

    try:
        with urllib.request.urlopen(request, timeout=45) as response, temp.open("wb") as out:
            shutil.copyfileobj(response, out)
    except Exception as exc:
        if temp.exists():
            temp.unlink()
        raise InstallerError("download_failed", str(exc)) from exc

    if not temp.exists() or temp.stat().st_size < 10_000:
        if temp.exists():
            temp.unlink()
        raise InstallerError("download_failed", "Downloaded file is unexpectedly small.")

    temp.replace(destination)


def ensure_adb() -> Path:
    system = platform.system()
    if system not in TOOLS_URLS:
        raise InstallerError("unsupported_os", system)

    root = cache_root()
    executable = "adb.exe" if system == "Windows" else "adb"
    adb = root / "platform-tools" / executable

    if adb.exists():
        if system != "Windows":
            adb.chmod(adb.stat().st_mode | 0o111)
        return adb

    STATE.set("download_tools", 10)

    archive = root / "platform-tools.zip"
    download(TOOLS_URLS[system], archive)

    extract = root / "_platform_tools_extract"
    if extract.exists():
        shutil.rmtree(extract)
    extract.mkdir(parents=True)

    try:
        with zipfile.ZipFile(archive, "r") as zf:
            zf.extractall(extract)
    except Exception as exc:
        raise InstallerError("tools_extract_failed", str(exc)) from exc
    finally:
        archive.unlink(missing_ok=True)

    source = extract / "platform-tools"
    target = root / "platform-tools"
    if target.exists():
        shutil.rmtree(target)

    if not source.exists():
        raise InstallerError("tools_extract_failed", "platform-tools folder missing")

    shutil.move(str(source), str(target))
    shutil.rmtree(extract, ignore_errors=True)

    adb = target / executable
    if not adb.exists():
        raise InstallerError("tools_extract_failed", "ADB executable missing")

    if system != "Windows":
        adb.chmod(adb.stat().st_mode | 0o111)

    return adb


def subprocess_flags() -> dict[str, Any]:
    if platform.system() != "Windows":
        return {}
    return {"creationflags": getattr(subprocess, "CREATE_NO_WINDOW", 0)}


def run(command: list[str], timeout: int = 60) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
            check=False,
            **subprocess_flags(),
        )
    except subprocess.TimeoutExpired as exc:
        raise InstallerError("command_timeout", " ".join(command)) from exc
    except Exception as exc:
        raise InstallerError("command_failed", str(exc)) from exc


def adb_cmd(adb: Path, serial: str | None, *args: str, timeout: int = 60) -> subprocess.CompletedProcess[str]:
    command = [str(adb)]
    if serial:
        command += ["-s", serial]
    command += list(args)
    return run(command, timeout=timeout)


def device_state(adb: Path, serial: str) -> str:
    result = adb_cmd(adb, None, "devices")
    for raw in result.stdout.splitlines():
        line = raw.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if parts and parts[0] == serial:
            return parts[1] if len(parts) > 1 else "unknown"
    return "missing"


def wait_for_authorization(adb: Path, serial: str, seconds: int = 90) -> None:
    deadline = time.time() + seconds
    while time.time() < deadline:
        state = device_state(adb, serial)
        if state == "device":
            return

        STATE.set("waiting_authorization", 38, state)
        try:
            adb_cmd(adb, None, "connect", serial, timeout=12)
        except InstallerError:
            pass
        time.sleep(2)

    raise InstallerError("authorization_timeout")


def get_active_user(adb: Path, serial: str) -> int:
    result = adb_cmd(adb, serial, "shell", "am", "get-current-user", timeout=15)
    try:
        return int(result.stdout.strip())
    except ValueError:
        return 0


def package_visible(adb: Path, serial: str, user_id: int) -> bool:
    result = adb_cmd(
        adb, serial, "shell", "pm", "list", "packages", "--user",
        str(user_id), PACKAGE, timeout=20
    )
    return f"package:{PACKAGE}" in result.stdout


def attach_package_to_user(adb: Path, serial: str, user_id: int) -> bool:
    adb_cmd(
        adb, serial, "shell", "cmd", "package", "install-existing",
        "--user", str(user_id), PACKAGE, timeout=30
    )
    adb_cmd(
        adb, serial, "shell", "pm", "enable",
        "--user", str(user_id), PACKAGE, timeout=20
    )
    return package_visible(adb, serial, user_id)


def launch_app(adb: Path, serial: str, user_id: int) -> bool:
    result = adb_cmd(
        adb, serial, "shell", "am", "start", "--user", str(user_id), "-W",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LEANBACK_LAUNCHER",
        "-p", PACKAGE, timeout=30
    )
    if "Status: ok" in result.stdout or "Activity:" in result.stdout:
        return True

    result = adb_cmd(
        adb, serial, "shell", "am", "start", "--user", str(user_id), "-W",
        "-n", MAIN_ACTIVITY, timeout=30
    )
    return "Status: ok" in result.stdout or "Activity:" in result.stdout


def install_worker(ip_value: str) -> None:
    try:
        ip = validate_tv_ip(ip_value)
        root = cache_root()

        STATE.set("prepare", 4)
        adb = ensure_adb()
        adb_cmd(adb, None, "start-server", timeout=20)

        STATE.set("download_apk", 20)
        apk = root / "AmbiGoveeTV.apk"
        download(APK_URL, apk)

        serial = f"{ip}:5555"
        STATE.set("connecting", 32)
        adb_cmd(adb, None, "connect", serial, timeout=20)

        if device_state(adb, serial) != "device":
            wait_for_authorization(adb, serial)

        active_user = get_active_user(adb, serial)

        STATE.set("installing", 52, str(active_user))
        for user_id in sorted({active_user, 0}):
            adb_cmd(
                adb, serial, "shell", "am", "force-stop",
                "--user", str(user_id), PACKAGE, timeout=15
            )

        result = adb_cmd(adb, serial, "install", "-r", str(apk), timeout=240)
        output = result.stdout.strip()
        if "Success" not in output:
            if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output:
                raise InstallerError("signature_mismatch", output)
            raise InstallerError("install_failed", output)

        STATE.set("activating", 73)
        ready = attach_package_to_user(adb, serial, active_user)

        if active_user != 0:
            try:
                attach_package_to_user(adb, serial, 0)
            except Exception:
                pass

        if not ready:
            raise InstallerError("profile_activation_failed")

        STATE.set("launching", 90)
        if not launch_app(adb, serial, active_user):
            raise InstallerError("launch_failed")

        STATE.set("success", 100, busy=False)

    except InstallerError as exc:
        STATE.set("error:" + exc.code, STATE.get().get("progress", 0), exc.detail, busy=False)
    except Exception as exc:
        STATE.set("error:unexpected", STATE.get().get("progress", 0), str(exc), busy=False)


HTML = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#080b12">
<title>AmbiGovee Installer</title>
<style>
:root{color-scheme:dark;--bg:#070a10;--panel:#101621;--panel2:#151c29;--line:#273248;--text:#f6f7fb;--muted:#909bad;--purple:#795cff;--green:#66e0a3;--yellow:#f3c969;--red:#ff8296}
*{box-sizing:border-box}body{margin:0;min-height:100vh;color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:radial-gradient(900px 480px at 12% 0%,rgba(108,77,255,.22),transparent 60%),radial-gradient(700px 500px at 100% 100%,rgba(107,44,150,.18),transparent 62%),var(--bg)}
button,input{font:inherit}.shell{max-width:1080px;margin:auto;padding:34px 24px 54px}.top{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:34px}.brand{display:flex;align-items:center;gap:13px;font-weight:850;font-size:22px}.mark{width:42px;height:42px;border-radius:14px;background:linear-gradient(135deg,#8d76ff,#5c3fe0);box-shadow:0 10px 30px rgba(106,76,255,.35);display:grid;place-items:center;font-size:22px}.lang{display:flex;background:#111722;border:1px solid var(--line);border-radius:14px;padding:4px}.lang button{border:0;background:transparent;color:#7f899a;padding:8px 11px;border-radius:10px;font-weight:800;cursor:pointer}.lang button.active{background:#29203f;color:#e9e4ff}.grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(320px,.75fr);gap:22px}.card{background:linear-gradient(145deg,rgba(20,27,39,.96),rgba(12,17,26,.96));border:1px solid var(--line);border-radius:28px;box-shadow:0 24px 70px rgba(0,0,0,.28)}.hero{padding:34px}.kicker{color:#ac9cff;font-size:12px;letter-spacing:.16em;font-weight:900;text-transform:uppercase}h1{font-size:42px;line-height:1.04;margin:12px 0 14px;letter-spacing:-.035em}.lead{color:#a7b0c0;font-size:17px;line-height:1.55;max-width:700px}.req{display:grid;grid-template-columns:repeat(3,1fr);gap:11px;margin-top:27px}.req .item{background:#0d131d;border:1px solid #222c3d;border-radius:18px;padding:16px}.req .icon{font-size:20px;margin-bottom:10px}.req b{display:block;font-size:14px}.req span{display:block;color:#7f8a9d;font-size:12px;line-height:1.4;margin-top:5px}.form{margin-top:28px;padding-top:24px;border-top:1px solid #232c3e}.label{font-size:13px;color:#b7c0cf;font-weight:800;margin-bottom:9px}.iprow{display:flex;gap:11px}input{min-width:0;flex:1;background:#090e16;border:1px solid #344058;color:white;border-radius:16px;padding:16px 18px;outline:none;font-size:18px}input:focus{border-color:#8b75ff;box-shadow:0 0 0 3px rgba(139,117,255,.14)}.primary{border:0;border-radius:16px;padding:0 22px;color:white;font-weight:900;background:linear-gradient(135deg,#6c50e9,#8c72ff);cursor:pointer;box-shadow:0 10px 28px rgba(102,76,232,.27)}.primary:disabled{opacity:.48;cursor:default}.side{padding:25px;display:flex;flex-direction:column;min-height:540px}.side h2{font-size:18px;margin:0 0 18px}.steps{display:flex;flex-direction:column;gap:10px}.step{display:flex;gap:12px;align-items:center;padding:12px;border:1px solid #222c3d;border-radius:16px;background:#0c121b;transition:.2s}.dot{width:32px;height:32px;border-radius:11px;display:grid;place-items:center;background:#1a2230;color:#738095;font-weight:900}.step .txt b{display:block;font-size:13px}.step .txt span{display:block;font-size:11px;color:#778397;margin-top:3px}.step.active{border-color:#6552a5;background:#181329}.step.active .dot{background:#6d52e7;color:white}.step.done{border-color:#265b48}.step.done .dot{background:#164a36;color:#7ce4b2}.progress{height:7px;background:#0a0f17;border-radius:99px;overflow:hidden;margin:20px 2px 8px}.progress>div{height:100%;width:0;background:linear-gradient(90deg,#6d51eb,#a38fff);transition:width .35s}.status{margin-top:auto;padding:15px 16px;border-radius:17px;border:1px solid #29344a;background:#0d141f;color:#c2cad7;font-size:13px;line-height:1.45;white-space:pre-wrap}.status.warn{border-color:#705d30;background:#201b10;color:#f4ce7e}.status.bad{border-color:#773647;background:#251218;color:#ff9aad}.status.ok{border-color:#28654d;background:#10251c;color:#83e4b5}.small{color:#606c80;font-size:11px;line-height:1.45;margin-top:13px;text-align:center}.success{display:none;text-align:center;padding:38px 16px 14px}.success.show{display:block}.check{width:82px;height:82px;border-radius:28px;background:linear-gradient(135deg,#156542,#2e9c68);display:grid;place-items:center;margin:0 auto 18px;font-size:42px;box-shadow:0 15px 45px rgba(31,142,91,.28)}.success h2{font-size:29px;margin:0 0 9px}.success p{color:#9ba6b8;line-height:1.55;margin:0 auto 20px;max-width:480px}.doneBtn{border:1px solid #38445a;background:#141b27;color:#eef1f6;border-radius:15px;padding:13px 19px;font-weight:800;cursor:pointer}.footer{margin-top:22px;color:#596577;font-size:11px;text-align:center}@media(max-width:800px){.grid{grid-template-columns:1fr}.req{grid-template-columns:1fr}.iprow{flex-direction:column}.primary{min-height:54px}h1{font-size:34px}}
</style>
</head>
<body>
<div class="shell">
<header class="top"><div class="brand"><div class="mark">🌈</div><div>AmbiGovee</div></div><div class="lang"><button id="enBtn">EN</button><button id="frBtn">FR</button></div></header>
<div class="grid">
<section class="card hero">
<div id="mainContent">
<div class="kicker" data-i18n="kicker"></div><h1 data-i18n="title"></h1><div class="lead" data-i18n="lead"></div>
<div class="req">
<div class="item"><div class="icon">📺</div><b data-i18n="reqTvTitle"></b><span data-i18n="reqTvBody"></span></div>
<div class="item"><div class="icon">📡</div><b data-i18n="reqNetTitle"></b><span data-i18n="reqNetBody"></span></div>
<div class="item"><div class="icon">🔢</div><b data-i18n="reqIpTitle"></b><span data-i18n="reqIpBody"></span></div>
</div>
<div class="form"><div class="label" data-i18n="ipLabel"></div><div class="iprow"><input id="ip" inputmode="decimal" placeholder="192.168.1.100" autocomplete="off"><button class="primary" id="installBtn" data-i18n="install"></button></div></div>
</div>
<div class="success" id="success"><div class="check">✓</div><h2 data-i18n="successTitle"></h2><p data-i18n="successBody"></p><button class="doneBtn" id="closeBtn" data-i18n="close"></button></div>
</section>
<aside class="card side">
<h2 data-i18n="progressTitle"></h2>
<div class="steps">
<div class="step" data-step="prepare"><div class="dot">1</div><div class="txt"><b data-i18n="stepPrepare"></b><span data-i18n="stepPrepareSub"></span></div></div>
<div class="step" data-step="connecting"><div class="dot">2</div><div class="txt"><b data-i18n="stepConnect"></b><span data-i18n="stepConnectSub"></span></div></div>
<div class="step" data-step="installing"><div class="dot">3</div><div class="txt"><b data-i18n="stepInstall"></b><span data-i18n="stepInstallSub"></span></div></div>
<div class="step" data-step="activating"><div class="dot">4</div><div class="txt"><b data-i18n="stepActivate"></b><span data-i18n="stepActivateSub"></span></div></div>
<div class="step" data-step="launching"><div class="dot">5</div><div class="txt"><b data-i18n="stepLaunch"></b><span data-i18n="stepLaunchSub"></span></div></div>
</div>
<div class="progress"><div id="bar"></div></div><div class="status" id="status"></div><div class="small" data-i18n="privacy"></div>
</aside>
</div>
<div class="footer" data-i18n="footer"></div>
</div>
<script>
const T={
en:{kicker:"Android TV / Google TV installer",title:"Install AmbiGovee on your Philips TV",lead:"No ADB commands to type. This installer downloads everything it needs, connects to your TV, installs AmbiGoveeTV and launches it.",reqTvTitle:"Enable ADB on the TV",reqTvBody:"Developer Options → USB / ADB debugging.",reqNetTitle:"Same local network",reqNetBody:"Your computer and Philips TV must be on the same LAN/Wi-Fi.",reqIpTitle:"TV IP address",reqIpBody:"Find it in the Philips network settings.",ipLabel:"Philips TV local IP address",install:"INSTALL AMBIGOVEE",progressTitle:"Installation",stepPrepare:"Prepare",stepPrepareSub:"Download official tools and APK",stepConnect:"Connect to TV",stepConnectSub:"You may need to approve ADB on the TV",stepInstall:"Install app",stepInstallSub:"Update or first installation",stepActivate:"Activate TV profile",stepActivateSub:"Handles Android TV multi-user profiles",stepLaunch:"Launch AmbiGovee",stepLaunchSub:"Open the TV interface",ready:"Ready. Enable ADB on the TV, then enter its IP address.",prepare:"Preparing the installer…",download_tools:"Downloading Android Platform Tools from Google…",download_apk:"Downloading the latest AmbiGoveeTV release…",connecting:"Connecting to the Philips TV…",waiting_authorization:"Look at the TV and accept the ADB / debugging authorization.",installing:"Installing AmbiGoveeTV…",activating:"Activating AmbiGoveeTV on the TV profile…",launching:"Launching AmbiGoveeTV…",success:"Installation complete.",successTitle:"AmbiGovee is ready!",successBody:"Installation is complete. Continue on the TV: scan the Philips QR code, enter the PIN on your phone, then enable LAN Control in Govee Home and add your lights.",close:"CLOSE INSTALLER",privacy:"Local installation. The installer does not upload your TV IP or pairing credentials.",footer:"AmbiGoveeTV • Open-source • Independent project",invalid_ip:"Enter a valid private IPv4 address, for example 192.168.1.100.",download_failed:"Download failed. Check the computer's Internet connection.",authorization_timeout:"The TV did not authorize ADB in time. Check the TV screen, approve debugging and try again.",signature_mismatch:"An older AmbiGovee build is signed with a different Android key. Do not remove an existing configured installation unless you understand the data-loss risk.",install_failed:"Android refused the APK installation.",profile_activation_failed:"The APK was installed, but Android TV did not activate it on the current TV profile.",launch_failed:"AmbiGovee was installed, but Android TV refused to launch the interface.",unsupported_os:"This operating system is not supported by this installer yet.",tools_extract_failed:"Android Platform Tools could not be prepared.",command_timeout:"A system command took too long.",command_failed:"A required system command failed.",unexpected:"Unexpected installer error."},
fr:{kicker:"Installateur Android TV / Google TV",title:"Installe AmbiGovee sur ta TV Philips",lead:"Aucune commande ADB à taper. L'installateur télécharge ce qu'il faut, se connecte à la TV, installe AmbiGoveeTV puis la lance.",reqTvTitle:"Active ADB sur la TV",reqTvBody:"Options développeur → Débogage USB / ADB.",reqNetTitle:"Même réseau local",reqNetBody:"L'ordinateur et la TV Philips doivent être sur le même LAN / Wi-Fi.",reqIpTitle:"Adresse IP de la TV",reqIpBody:"Tu la trouveras dans les réglages réseau Philips.",ipLabel:"Adresse IP locale de la TV Philips",install:"INSTALLER AMBIGOVEE",progressTitle:"Installation",stepPrepare:"Préparation",stepPrepareSub:"Outils officiels et dernière APK",stepConnect:"Connexion à la TV",stepConnectSub:"La TV peut demander d'autoriser ADB",stepInstall:"Installation",stepInstallSub:"Première installation ou mise à jour",stepActivate:"Activation du profil TV",stepActivateSub:"Gestion automatique des profils Android TV",stepLaunch:"Lancement",stepLaunchSub:"Ouverture de l'interface TV",ready:"Prêt. Active ADB sur la TV puis entre son adresse IP.",prepare:"Préparation de l'installateur…",download_tools:"Téléchargement d'Android Platform Tools depuis Google…",download_apk:"Téléchargement de la dernière version d'AmbiGoveeTV…",connecting:"Connexion à la TV Philips…",waiting_authorization:"Regarde la TV et accepte l'autorisation ADB / débogage.",installing:"Installation d'AmbiGoveeTV…",activating:"Activation d'AmbiGoveeTV sur le profil de la TV…",launching:"Lancement d'AmbiGoveeTV…",success:"Installation terminée.",successTitle:"AmbiGovee est prêt !",successBody:"L'installation est terminée. Continue sur la TV : scanne le QR Philips, entre le PIN sur ton téléphone, puis active Contrôle LAN dans Govee Home et ajoute tes lumières.",close:"FERMER L'INSTALLATEUR",privacy:"Installation locale. L'adresse IP de ta TV et les identifiants d'association ne sont pas envoyés sur Internet.",footer:"AmbiGoveeTV • Open source • Projet indépendant",invalid_ip:"Entre une adresse IPv4 privée valide, par exemple 192.168.1.100.",download_failed:"Téléchargement impossible. Vérifie la connexion Internet de l'ordinateur.",authorization_timeout:"La TV n'a pas autorisé ADB à temps. Regarde l'écran de la TV, accepte le débogage puis réessaie.",signature_mismatch:"Une ancienne version d'AmbiGovee utilise une autre clé Android. Ne supprime pas une installation déjà configurée sans tenir compte du risque de perte de données.",install_failed:"Android a refusé l'installation de l'APK.",profile_activation_failed:"L'APK est installée, mais Android TV ne l'a pas activée sur le profil actuellement utilisé.",launch_failed:"AmbiGovee est installée, mais Android TV refuse de lancer l'interface.",unsupported_os:"Ce système n'est pas encore pris en charge par cet installateur.",tools_extract_failed:"Impossible de préparer Android Platform Tools.",command_timeout:"Une commande système a pris trop de temps.",command_failed:"Une commande système nécessaire a échoué.",unexpected:"Erreur inattendue de l'installateur."}
};
let lang=(localStorage.getItem("ambigoveeLang")||((navigator.language||"en").toLowerCase().startsWith("fr")?"fr":"en"));const $=s=>document.querySelector(s);function tx(k){return (T[lang]&&T[lang][k])||T.en[k]||k}let lastState={stage:"ready",progress:0,busy:false};
function applyLang(){document.documentElement.lang=lang;document.querySelectorAll("[data-i18n]").forEach(e=>e.textContent=tx(e.dataset.i18n));$("#enBtn").classList.toggle("active",lang==="en");$("#frBtn").classList.toggle("active",lang==="fr");render(lastState)}
$("#enBtn").onclick=()=>{lang="en";localStorage.setItem("ambigoveeLang",lang);applyLang()};$("#frBtn").onclick=()=>{lang="fr";localStorage.setItem("ambigoveeLang",lang);applyLang()};
const order=["prepare","connecting","installing","activating","launching"];function stageGroup(stage){if(["prepare","download_tools","download_apk"].includes(stage))return"prepare";if(["connecting","waiting_authorization"].includes(stage))return"connecting";if(stage==="installing")return"installing";if(stage==="activating")return"activating";if(stage==="launching"||stage==="success")return"launching";return null}
function render(s){if(!s)return;lastState=s;$("#bar").style.width=(s.progress||0)+"%";const current=stageGroup(s.stage),ci=order.indexOf(current);document.querySelectorAll(".step").forEach(el=>{const i=order.indexOf(el.dataset.step);el.classList.toggle("active",i===ci&&s.stage!=="success");el.classList.toggle("done",s.stage==="success"||(ci>=0&&i<ci))});const status=$("#status");status.className="status";let key=s.stage;if(s.stage.startsWith("error:")){key=s.stage.slice(6);status.classList.add("bad")}else if(s.stage==="waiting_authorization")status.classList.add("warn");else if(s.stage==="success")status.classList.add("ok");status.textContent=tx(key)+(s.detail&&s.stage.startsWith("error:")?"\n"+s.detail:"");const success=s.stage==="success";$("#mainContent").style.display=success?"none":"block";$("#success").classList.toggle("show",success);$("#installBtn").disabled=!!s.busy;$("#ip").disabled=!!s.busy}
async function poll(){try{const r=await fetch("/api/state",{cache:"no-store"});render(await r.json())}catch(e){}setTimeout(poll,500)}
$("#installBtn").onclick=async()=>{const ip=$("#ip").value.trim();try{const r=await fetch("/api/start",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({ip})});render(await r.json())}catch(e){}};
$("#ip").addEventListener("keydown",e=>{if(e.key==="Enter")$("#installBtn").click()});$("#closeBtn").onclick=()=>fetch("/api/shutdown",{method:"POST"}).finally(()=>window.close());applyLang();poll();
</script>
</body>
</html>
"""


class ReusableTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "AmbiGoveeInstaller/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        return

    def send_bytes(self, code: int, content_type: str, payload: bytes) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def send_json(self, obj: Any, code: int = 200) -> None:
        self.send_bytes(code, "application/json; charset=utf-8", json.dumps(obj).encode("utf-8"))

    def do_GET(self) -> None:
        if self.path in ("/", "/index.html"):
            self.send_bytes(200, "text/html; charset=utf-8", HTML.encode("utf-8"))
            return
        if self.path == "/api/state":
            self.send_json(STATE.get())
            return
        if self.path == "/favicon.ico":
            self.send_bytes(204, "image/x-icon", b"")
            return
        self.send_json({"error": "not_found"}, 404)

    def do_POST(self) -> None:
        if self.path == "/api/start":
            length = min(int(self.headers.get("Content-Length", "0") or 0), 4096)
            try:
                data = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
            except Exception:
                self.send_json({"stage": "error:invalid_request", "progress": 0, "busy": False}, 400)
                return

            if STATE.get().get("busy"):
                self.send_json(STATE.get(), 409)
                return

            try:
                ip = validate_tv_ip(str(data.get("ip", "")))
            except InstallerError as exc:
                STATE.set("error:" + exc.code, 0, busy=False)
                self.send_json(STATE.get(), 400)
                return

            STATE.set("prepare", 2, busy=True)
            threading.Thread(target=install_worker, args=(ip,), daemon=True).start()
            self.send_json(STATE.get())
            return

        if self.path == "/api/shutdown":
            self.send_json({"ok": True})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return

        self.send_json({"error": "not_found"}, 404)


def free_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def self_test() -> int:
    assert validate_tv_ip("192.168.1.51") == "192.168.1.51"
    for bad in ("", "999.1.1.1", "8.8.8.8", "127.0.0.1", "169.254.1.1"):
        try:
            validate_tv_ip(bad)
        except InstallerError:
            pass
        else:
            raise AssertionError("accepted invalid IP: " + bad)

    for item in ("AmbiGovee is ready!", "AmbiGovee est prêt !", "waiting_authorization", "/api/start", "/api/state"):
        assert item in HTML, item

    print("AmbiGovee installer self-test: OK")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()

    if platform.system() not in TOOLS_URLS:
        raise SystemExit("Unsupported OS: " + platform.system())

    port = free_local_port()
    url = f"http://127.0.0.1:{port}/"
    server = ReusableTCPServer(("127.0.0.1", port), Handler)
    threading.Timer(0.6, lambda: webbrowser.open(url, new=1)).start()

    try:
        server.serve_forever(poll_interval=0.25)
    finally:
        server.server_close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
