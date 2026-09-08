@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo =============================================
echo   AmbiGovee 1.5 - Installation / Mise a jour
echo =============================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer.ps1"
echo.
pause
