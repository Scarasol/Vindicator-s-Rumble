@echo off
setlocal DisableDelayedExpansion
where.exe pwsh.exe >nul 2>&1
if errorlevel 1 (
    echo PowerShell 7 is required to handle the Gradle cache's long paths safely.
    exit /b 1
)
if "%~1"=="" goto preview
if /i "%~1"=="/clean" goto clean
echo Usage: %~nx0 [/clean]
exit /b 2

:preview
pwsh.exe -NoProfile -File "%~dp0cleanup-limitless-cache.ps1"
exit /b %errorlevel%

:clean
pwsh.exe -NoProfile -File "%~dp0cleanup-limitless-cache.ps1" -Clean
exit /b %errorlevel%
