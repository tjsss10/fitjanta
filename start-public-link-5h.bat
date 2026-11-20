@echo off
rem Keeps the FitJanta public link running for 5 hours (renews every 60 min, keeps the laptop awake).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-public-link.ps1" -Hours 5
pause
