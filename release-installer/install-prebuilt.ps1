$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $Root ".platform-tools"

function Step($t){ Write-Host ""; Write-Host "==> $t" -ForegroundColor Cyan }

Write-Host "AmbiGovee - installation rapide" -ForegroundColor Magenta
Write-Host ""
Write-Host "Sur la TV, fais d'abord :" -ForegroundColor Yellow
Write-Host "  1. Parametres > A propos > Build Android TV : appuie 7 fois."
Write-Host "  2. Options pour les developpeurs > Debogage USB / ADB : active."
Write-Host "  3. Verifie que PC et TV sont sur le meme reseau."
Write-Host ""
Read-Host "Quand c'est fait, appuie sur Entree" | Out-Null

$apk = Get-ChildItem $Root -Filter "AmbiGovee*.apk" | Select-Object -First 1
if(!$apk){ throw "APK AmbiGovee introuvable dans le dossier de l'installateur." }

if(!(Test-Path (Join-Path $Tools "adb.exe"))){
    Step "Telechargement Android Platform Tools"
    $zip = Join-Path $Root "platform-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip -UseBasicParsing
    $tmp = Join-Path $Root "_platform_tmp"
    if(Test-Path $tmp){ Remove-Item $tmp -Recurse -Force }
    Expand-Archive $zip $tmp -Force
    if(Test-Path $Tools){ Remove-Item $Tools -Recurse -Force }
    Move-Item (Join-Path $tmp "platform-tools") $Tools
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
}

$adb = Join-Path $Tools "adb.exe"
& $adb start-server | Out-Null

$tvIp = Read-Host "Adresse IP de la TV Philips (exemple 192.168.1.100)"
if([string]::IsNullOrWhiteSpace($tvIp)){ throw "Adresse IP obligatoire." }

Step "Connexion a la TV"
& $adb connect "$tvIp`:5555" | Out-Host
Start-Sleep 2

$devices = & $adb devices | Out-String
if($devices -notmatch ([regex]::Escape("$tvIp`:5555") + "\s+device")){
    Write-Host "Regarde la TV et accepte 'Autoriser le debogage USB'." -ForegroundColor Yellow
    Read-Host "Puis appuie sur Entree" | Out-Null
    & $adb connect "$tvIp`:5555" | Out-Host
}

Step "Installation / mise a jour"
$out = & $adb install -r $apk.FullName 2>&1 | Out-String
Write-Host $out
if($out -notmatch "Success"){
    if($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE"){
        throw "La version installee utilise une autre signature. Desinstalle l'ancienne version avant cette transition."
    }
    throw "Installation ADB impossible."
}

Step "Lancement AmbiGovee"
& $adb shell am start -n "fr.ambigovee.tv/.MainActivity" | Out-Host
Write-Host ""
Write-Host "AmbiGovee est installe." -ForegroundColor Green
Write-Host "La suite se fait directement sur la TV : association Philips par PIN puis scan Govee LAN."
Write-Host "Une fois l app installee, tu peux desactiver le Debogage USB/ADB : AmbiGovee n en a pas besoin pour fonctionner." -ForegroundColor Yellow
