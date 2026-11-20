@echo off
rem Double-click to create a public https link for FitJanta (works on any network).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-public-link.ps1"
pause
