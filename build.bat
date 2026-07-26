@echo off
REM Builds the runnable fat jar: build\libs\koneko-web-shaded.jar
setlocal

cd /d "%~dp0"

call gradlew.bat shadowJar %*
if errorlevel 1 (
    echo.
    echo Build FAILED.
    endlocal
    exit /b 1
)

echo.
echo Built build\libs\koneko-web-shaded.jar
endlocal
