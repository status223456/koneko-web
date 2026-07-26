@echo off
REM Removes build output and the gradle caches of this project.
setlocal

cd /d "%~dp0"

call gradlew.bat clean

endlocal
