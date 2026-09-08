$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $Root ".platform-tools"
$Package = "fr.ambigovee.tv"
$MainActivity = "fr.ambigovee.tv/.MainActivity"

function Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

function Ensure-Adb {
    $adb = Join-Path $Tools "adb.exe"
    if (Test-Path $adb) { return $adb }

    Step "Telechargement Android Platform Tools"

    $zip = Join-Path $Root "platform-tools.zip"
    $tmp = Join-Path $Root "_platform_tmp"

    Invoke-WebRequest `
        -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" `
        -OutFile $zip `
        -UseBasicParsing

    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive $zip $tmp -Force

    if (Test-Path $Tools) { Remove-Item $Tools -Recurse -Force }
    Move-Item (Join-Path $tmp "platform-tools") $Tools

    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force

    return (Join-Path $Tools "adb.exe")
}

function Get-ActiveUser([string]$adb) {
    try {
        $raw = (& $adb shell am get-current-user 2>&1 | Out-String).Trim()
        if ($raw -match '^\d+$') { return [int]$raw }
    } catch {}
    return 0
}

function Package-VisibleForUser([string]$adb, [int]$userId) {
    $visible = (& $adb shell pm list packages --user $userId $Package 2>&1 | Out-String)
    return ($visible -match 'package:fr\.ambigovee\.tv')
}

function Ensure-PackageForUser([string]$adb, [int]$userId) {
    # Sur certaines Philips/Android TV, adb install -r remplace bien l'APK globale
    # mais conserve "installed=false" sur le profil qui affiche réellement l'interface.
    # install-existing rattache alors le même APK au bon utilisateur SANS désinstaller.
    try {
        $result = (& $adb shell cmd package install-existing --user $userId $Package 2>&1 | Out-String).Trim()
        if ($result) {
            Write-Host "  Profil $userId : $result"
        }
    } catch {}

    try {
        & $adb shell pm enable --user $userId $Package 2>$null | Out-Null
    } catch {}

    Start-Sleep -Milliseconds 350
    return (Package-VisibleForUser $adb $userId)
}

function Launch-ForUser([string]$adb, [int]$userId) {
    # 1) Intent officiel Android TV.
    $result = (& $adb shell am start --user $userId -W `
        -a android.intent.action.MAIN `
        -c android.intent.category.LEANBACK_LAUNCHER `
        -p $Package 2>&1 | Out-String)

    Write-Host $result

    if ($result -match 'Status:\s+ok' -or $result -match 'Activity:') {
        return $true
    }

    # 2) Fallback explicite. Le manifest AmbiGovee déclare cette Activity.
    $result = (& $adb shell am start --user $userId -W -n $MainActivity 2>&1 | Out-String)
    Write-Host $result

    return ($result -match 'Status:\s+ok' -or $result -match 'Activity:')
}

Write-Host "=============================================" -ForegroundColor Magenta
Write-Host "  AmbiGovee - installation rapide sur TV" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "Sur la TV :" -ForegroundColor Yellow
Write-Host "  1. Active les Options pour les developpeurs."
Write-Host "  2. Active le Debogage USB / ADB."
Write-Host "  3. PC et TV doivent etre sur le meme reseau."
Write-Host ""
Read-Host "Quand c'est fait, appuie sur Entree" | Out-Null

$apk = Get-ChildItem $Root -Filter "AmbiGovee*.apk" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (!$apk) {
    throw "AmbiGoveeTV.apk est introuvable dans ce dossier."
}

$adb = Ensure-Adb
& $adb start-server | Out-Null

$tvIp = Read-Host "Adresse IP de la TV Philips"
if ([string]::IsNullOrWhiteSpace($tvIp)) {
    throw "Adresse IP obligatoire."
}

Step "Connexion a la TV"
& $adb connect "$tvIp`:5555" | Out-Host
Start-Sleep -Seconds 1

$devices = & $adb devices | Out-String
if ($devices -notmatch ([regex]::Escape("$tvIp`:5555") + "\s+device")) {
    Write-Host "Accepte l'autorisation de debogage sur la TV." -ForegroundColor Yellow
    Read-Host "Puis appuie sur Entree" | Out-Null

    & $adb connect "$tvIp`:5555" | Out-Host
    Start-Sleep -Seconds 1

    $devices = & $adb devices | Out-String
    if ($devices -notmatch ([regex]::Escape("$tvIp`:5555") + "\s+device")) {
        throw "La TV n'est pas connectee en ADB."
    }
}

$activeUser = Get-ActiveUser $adb
$targetUsers = @($activeUser, 0) | Select-Object -Unique

Write-Host "Profil Android actif : $activeUser"

Step "Arret de l'ancienne version"
foreach ($userId in $targetUsers) {
    try {
        & $adb shell am force-stop --user $userId $Package 2>$null | Out-Null
    } catch {}
}

Step "Installation / mise a jour"
$out = & $adb install -r $apk.FullName 2>&1 | Out-String
Write-Host $out

if ($out -notmatch "Success") {
    if ($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        throw "La signature Android ne correspond pas. Utilise uniquement l'APK officielle AmbiGovee."
    }
    throw "L'installation ADB a echoue."
}

Start-Sleep -Seconds 1

Step "Activation sur le bon profil TV"
$readyUsers = @()

foreach ($userId in $targetUsers) {
    if (Ensure-PackageForUser $adb $userId) {
        $readyUsers += [int]$userId
        Write-Host "  Profil $userId : AmbiGovee disponible" -ForegroundColor Green
    } else {
        Write-Host "  Profil $userId : non utilise" -ForegroundColor DarkGray
    }
}

$readyUsers = $readyUsers | Select-Object -Unique

if (!$readyUsers -or $readyUsers.Count -eq 0) {
    Write-Host ""
    Write-Host "Diagnostic Android :" -ForegroundColor Yellow
    & $adb shell dumpsys package $Package | Out-Host
    throw "L'APK est installee mais aucun profil TV ne voit AmbiGovee."
}

Step "Version installee"
& $adb shell dumpsys package $Package |
    Select-String "versionName=|versionCode=" |
    Select-Object -First 2 |
    ForEach-Object { Write-Host $_ }

Step "Lancement AmbiGovee"
$launched = $false

# Le profil actif est prioritaire, puis user 0 en secours pour les TV multi-profils.
foreach ($userId in $readyUsers) {
    if (Launch-ForUser $adb $userId) {
        Write-Host "AmbiGovee lance sur le profil $userId." -ForegroundColor Green
        $launched = $true
        break
    }
}

if (!$launched) {
    throw "AmbiGovee est installe mais Android TV refuse de lancer l'interface."
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host "  AmbiGovee est pret" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""
Write-Host "La suite se fait sur la TV :" -ForegroundColor White
Write-Host "  Philips : scanne le QR code puis saisis le PIN sur ton telephone."
Write-Host "  Govee   : active Controle LAN dans Govee Home puis lance la recherche."
Write-Host ""
Write-Host "Tu peux ensuite desactiver le Debogage USB / ADB." -ForegroundColor Yellow
