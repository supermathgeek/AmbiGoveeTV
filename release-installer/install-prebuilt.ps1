$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $Root ".platform-tools"
$Package = "fr.ambigovee.tv"

function Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

function Ensure-Adb {
    if (Test-Path (Join-Path $Tools "adb.exe")) {
        return
    }

    Step "Telechargement Android Platform Tools"

    $zip = Join-Path $Root "platform-tools.zip"
    $tmp = Join-Path $Root "_platform_tmp"

    Invoke-WebRequest `
        -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" `
        -OutFile $zip `
        -UseBasicParsing

    if (Test-Path $tmp) {
        Remove-Item $tmp -Recurse -Force
    }

    Expand-Archive $zip $tmp -Force

    if (Test-Path $Tools) {
        Remove-Item $Tools -Recurse -Force
    }

    Move-Item (Join-Path $tmp "platform-tools") $Tools
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}

function Wait-PackageReady([string]$adb) {
    for ($i = 0; $i -lt 20; $i++) {
        $path = (& $adb shell pm path $Package 2>&1 | Out-String).Trim()

        if ($path -match "^package:") {
            Start-Sleep -Milliseconds 800
            return
        }

        Start-Sleep -Milliseconds 700
    }

    throw "Android n'a pas rendu AmbiGovee disponible apres l'installation."
}

function Launch-AmbiGovee([string]$adb) {
    Step "Lancement AmbiGovee"

    # IMPORTANT :
    # On ne force plus ".MainActivity".
    # Android resout lui-meme l'activite MAIN + LEANBACK_LAUNCHER.
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $result = & $adb shell am start -W `
            -a android.intent.action.MAIN `
            -c android.intent.category.LEANBACK_LAUNCHER `
            -p $Package 2>&1 | Out-String

        Write-Host $result

        if ($result -match "Status:\s+ok" -or
            $result -match "Activity:" -or
            $result -match "cmp=fr\.ambigovee\.tv") {
            return
        }

        Start-Sleep -Seconds 2
    }

    # Fallback Android TV : lancement par le launcher, sans nom de classe.
    $fallback = & $adb shell monkey `
        -p $Package `
        -c android.intent.category.LEANBACK_LAUNCHER `
        1 2>&1 | Out-String

    Write-Host $fallback

    if ($fallback -match "Events injected:\s+1") {
        return
    }

    throw "AmbiGovee est installe mais Android TV ne trouve pas son activite de lancement."
}

Write-Host "=============================================" -ForegroundColor Magenta
Write-Host "  AmbiGovee - installation rapide sur TV" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "Sur la TV, fais d'abord :" -ForegroundColor Yellow
Write-Host "  1. Parametres > A propos > Build Android TV : appuie 7 fois."
Write-Host "  2. Options pour les developpeurs > Debogage USB / ADB : active."
Write-Host "  3. Verifie que PC et TV sont sur le meme reseau."
Write-Host ""
Read-Host "Quand c'est fait, appuie sur Entree" | Out-Null

$apk = Get-ChildItem $Root -Filter "AmbiGovee*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (!$apk) {
    throw "APK AmbiGovee introuvable dans le dossier de l'installateur."
}

Ensure-Adb

$adb = Join-Path $Tools "adb.exe"
& $adb start-server | Out-Null

$tvIp = Read-Host "Adresse IP de la TV Philips (exemple 192.168.1.100)"
if ([string]::IsNullOrWhiteSpace($tvIp)) {
    throw "Adresse IP obligatoire."
}

Step "Connexion a la TV"
& $adb connect "$tvIp`:5555" | Out-Host
Start-Sleep -Seconds 1

$devices = & $adb devices | Out-String
if ($devices -notmatch ([regex]::Escape("$tvIp`:5555") + "\s+device")) {
    Write-Host "Regarde la TV et accepte 'Autoriser le debogage USB'." -ForegroundColor Yellow
    Read-Host "Puis appuie sur Entree" | Out-Null
    & $adb connect "$tvIp`:5555" | Out-Host
    Start-Sleep -Seconds 1

    $devices = & $adb devices | Out-String
    if ($devices -notmatch ([regex]::Escape("$tvIp`:5555") + "\s+device")) {
        throw "La TV n'est pas connectee en ADB."
    }
}

Step "Preparation de la mise a jour"

# Coupe l'ancienne version avant de la remplacer.
# C'est indispensable pour la transition depuis la 1.7.1 qui pouvait
# relancer son propre PackageInstaller en boucle.
& $adb shell am force-stop $Package | Out-Null

# Bloque temporairement l'auto-install de l'ancienne version pendant le remplacement.
& $adb shell appops set $Package REQUEST_INSTALL_PACKAGES ignore 2>$null | Out-Null

Start-Sleep -Milliseconds 600

Step "Installation / mise a jour"
$out = & $adb install -r $apk.FullName 2>&1 | Out-String
Write-Host $out

if ($out -notmatch "Success") {
    if ($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        throw "Signature Android differente. Ne desinstalle pas sans sauvegarde : utilise une APK signee avec la cle officielle AmbiGovee."
    }

    throw "Installation ADB impossible."
}

# adb install peut rendre la main avant que le launcher de certaines TV
# ait fini de rafraichir le package. On attend explicitement.
Wait-PackageReady $adb

Step "Verification de l'installation"
$version = & $adb shell dumpsys package $Package 2>&1 |
    Select-String "versionName=|versionCode=" |
    Select-Object -First 2

$version | ForEach-Object { Write-Host $_ }

# La nouvelle version peut a nouveau gerer ses futures mises a jour.
& $adb shell appops set $Package REQUEST_INSTALL_PACKAGES allow 2>$null | Out-Null

Start-Sleep -Seconds 2

Launch-AmbiGovee $adb

Write-Host ""
Write-Host "AmbiGovee est installe et lance." -ForegroundColor Green
Write-Host "La premiere configuration se fait ensuite directement sur la TV." -ForegroundColor Green
Write-Host "Pour Philips, suis le QR code affiche par AmbiGovee puis saisis le PIN depuis ton telephone."
Write-Host ""
Write-Host "Apres installation, tu peux desactiver le Debogage USB / ADB." -ForegroundColor Yellow
