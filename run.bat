@echo off
REM Builds if needed and starts the frontend.
setlocal

cd /d "%~dp0"

if not exist ".env" (
    echo No .env found - copying .env.example
    copy /y ".env.example" ".env" >nul
)

if not exist "config.yml" (
    echo No config.yml found - copying config.example.yml
    copy /y "config.example.yml" "config.yml" >nul
)

if not exist "build\libs\koneko-web-shaded.jar" (
    call gradlew.bat shadowJar
    if errorlevel 1 (
        echo Build FAILED.
        endlocal
        exit /b 1
    )
)

java -jar "build\libs\koneko-web-shaded.jar"

endlocal
