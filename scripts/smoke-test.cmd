@echo off
rem Double-click launcher for smoke-test.ps1 (Windows).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-test.ps1"
echo.
echo Results written to %~dp0smoke-test.log
pause
