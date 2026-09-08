$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $Root ".tools"
$Sdk = Join-Path $Root ".android-sdk"
New-Item -ItemType Directory -Force -Path $Tools,$Sdk | Out-Null
function Step($t){Write-Host "";Write-Host "==> $t" -ForegroundColor Cyan}
function Download($u,$o){if(!(Test-Path $o)){Invoke-WebRequest -Uri $u -OutFile $o -UseBasicParsing}}

Write-Host "==============================================" -ForegroundColor Magenta
Write-Host "  AmbiGovee 1.4 - installation Android TV" -ForegroundColor Magenta
Write-Host "==============================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "Avant de continuer sur la TV :" -ForegroundColor Yellow
Write-Host "  1. Parametres > A propos > Build Android TV : appuie 7 fois."
Write-Host "  2. Options pour les developpeurs > Debogage USB : ACTIVE."
Write-Host "  3. TV et PC doivent etre sur le meme reseau local."
Write-Host ""
Write-Host "Apres installation, l app te guidera pour associer Philips et scanner Govee automatiquement." -ForegroundColor Green
Read-Host "Quand c est fait, appuie sur Entree" | Out-Null

Step "Java 17+"
$JavaHome=$null;$jc=Get-Command java.exe -ErrorAction SilentlyContinue
if($jc){
 $psi=New-Object System.Diagnostics.ProcessStartInfo;$psi.FileName=$jc.Source;$psi.Arguments="-version";$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardError=$true;$psi.RedirectStandardOutput=$true
 $p=New-Object System.Diagnostics.Process;$p.StartInfo=$psi;[void]$p.Start();$v=$p.StandardOutput.ReadToEnd()+$p.StandardError.ReadToEnd();$p.WaitForExit();$major=0
 if($v -match 'version "1\.([0-9]+)'){$major=[int]$Matches[1]}elseif($v -match 'version "([0-9]+)'){$major=[int]$Matches[1]}
 if($major -ge 17){$JavaHome=Split-Path -Parent (Split-Path -Parent $jc.Source)}
}
if(!$JavaHome){
 $jdk=Join-Path $Tools "jdk17";if(!(Test-Path (Join-Path $jdk "bin\java.exe"))){
  Step "Telechargement Java 17";$zip=Join-Path $Tools "jdk17.zip";Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zip -UseBasicParsing;$tmp=Join-Path $Tools "jdk_tmp";if(Test-Path $tmp){Remove-Item $tmp -Recurse -Force};Expand-Archive $zip $tmp -Force;New-Item -ItemType Directory -Force -Path $jdk|Out-Null;$inner=Get-ChildItem $tmp -Directory|Select-Object -First 1;Copy-Item (Join-Path $inner.FullName "*") $jdk -Recurse -Force;Remove-Item $tmp -Recurse -Force
 };$JavaHome=$jdk
}
$env:JAVA_HOME=$JavaHome;$env:Path="$JavaHome\bin;$env:Path"

Step "SDK Android"
$latest=Join-Path $Sdk "cmdline-tools\latest";$sdkmanager=Join-Path $latest "bin\sdkmanager.bat"
if(!(Test-Path $sdkmanager)){$zip=Join-Path $Tools "android-cli.zip";Download "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip" $zip;$tmp=Join-Path $Tools "android_tmp";if(Test-Path $tmp){Remove-Item $tmp -Recurse -Force};Expand-Archive $zip $tmp -Force;New-Item -ItemType Directory -Force -Path $latest|Out-Null;Copy-Item (Join-Path $tmp "cmdline-tools\*") $latest -Recurse -Force;Remove-Item $tmp -Recurse -Force}
$env:ANDROID_HOME=$Sdk;$env:ANDROID_SDK_ROOT=$Sdk;(("y`n")*120)|&$sdkmanager --sdk_root=$Sdk --licenses|Out-Null;&$sdkmanager --sdk_root=$Sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0";if($LASTEXITCODE-ne 0){throw "SDK Android : echec"}

Step "Gradle"
$gd=Join-Path $Tools "gradle-8.9";$gradle=Join-Path $gd "bin\gradle.bat";if(!(Test-Path $gradle)){$zip=Join-Path $Tools "gradle-8.9-bin.zip";Download "https://services.gradle.org/distributions/gradle-8.9-bin.zip" $zip;Expand-Archive $zip $Tools -Force}

Step "Compilation AmbiGovee 1.4"
&$gradle -p $Root :app:assembleDebug --no-daemon;if($LASTEXITCODE-ne 0){throw "Compilation Android : echec"}
$apk=Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk";Copy-Item $apk (Join-Path $Root "AmbiGovee-1.4.apk") -Force

$adb=Join-Path $Sdk "platform-tools\adb.exe";&$adb start-server|Out-Null
$defaultIp="";if($defaultIp){$prompt="IP de la TV Philips [$defaultIp]"}else{$prompt="IP de la TV Philips (Parametres > Reseau)"}
$tvIp=Read-Host $prompt;if([string]::IsNullOrWhiteSpace($tvIp)){$tvIp=$defaultIp};if([string]::IsNullOrWhiteSpace($tvIp)){throw "IP TV obligatoire pour l'installation ADB."}
Step "Connexion ADB a $tvIp";&$adb connect "$tvIp`:5555"|Out-Host;Start-Sleep 2
$devices=&$adb devices|Out-String;if($devices -notmatch ([regex]::Escape("$tvIp`:5555")+"\s+device")){Write-Host "Accepte l'autorisation de debogage sur la TV puis appuie Entree." -ForegroundColor Yellow;Read-Host|Out-Null;&$adb connect "$tvIp`:5555"|Out-Null}

Step "Mise a jour / installation"
$out=&$adb install -r $apk 2>&1|Out-String;Write-Host $out
if($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE"){
 Write-Host "Ancienne signature detectee. Cette transition n'arrive qu'une fois." -ForegroundColor Yellow
 $answer=Read-Host "Desinstaller l'ancienne AmbiGovee et installer la v1.4 ? (O/N)"
 if($answer -match '^[OoYy]'){&$adb uninstall fr.ambigovee.tv|Out-Host;&$adb install $apk|Out-Host}else{throw "Installation annulee."}
}elseif($LASTEXITCODE-ne 0 -and $out -notmatch "Success"){throw "ADB n'a pas pu installer l'APK."}

Step "Lancement";&$adb shell am start -n "fr.ambigovee.tv/.MainActivity"|Out-Host
Write-Host "";Write-Host "AMBIGOVEE 1.4 INSTALLE" -ForegroundColor Green
Write-Host "Ouvre AmbiGovee sur la TV : l assistant va associer Philips par PIN puis scanner les appareils Govee LAN."
Write-Host "Les mises a jour conserveront la configuration si l APK garde la meme signature."

Write-Host "Apres installation, tu peux desactiver le Debogage USB/ADB : AmbiGovee fonctionne sans ADB." -ForegroundColor Yellow
