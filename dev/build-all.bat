@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-all.ps1" %*
exit /b %ERRORLEVEL%
