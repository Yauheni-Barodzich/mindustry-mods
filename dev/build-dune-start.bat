@echo off
setlocal
cd /d "%~dp0dune-start"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dune-start\build.ps1" %*
exit /b %ERRORLEVEL%
