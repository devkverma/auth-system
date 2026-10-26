@echo off
setlocal
cd /d "%~dp0"

REM Keep the batch entry point simple; PowerShell handles .env parsing safely.
where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: Windows PowerShell is required to run sonar.ps1.
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0sonar.ps1"
exit /b %ERRORLEVEL%
