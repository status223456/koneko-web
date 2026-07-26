@echo off
REM Development mode: runs straight from the sources with gradle run, so the
REM .vue files are read from disk on every request (set LEVEL=DEV in .env).
setlocal

cd /d "%~dp0"

if not exist ".env" copy /y ".env.example" ".env" >nul
if not exist "config.yml" copy /y "config.example.yml" "config.yml" >nul

call gradlew.bat run --console=plain

endlocal
