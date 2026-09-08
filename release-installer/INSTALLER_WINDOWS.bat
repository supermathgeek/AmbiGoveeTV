@echo off
chcp 65001 >nul
cd /d "%~dp0"
title AmbiGovee - Installation Android TV
echo.
echo =============================================
echo   AmbiGovee - installation rapide sur TV
echo =============================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-prebuilt.ps1"
echo.
pause
