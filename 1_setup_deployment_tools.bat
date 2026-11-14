@echo off
REM ========================================
REM SIP Simulator - Deployment Tools Setup
REM ========================================
REM This script downloads and sets up all tools needed for deployment

echo ========================================
echo SIP Simulator - Deployment Setup
echo ========================================
echo.
echo This will download:
echo 1. Launch4j (EXE wrapper)
echo 2. Portable JRE 8 (Java Runtime)
echo.
echo Total download size: ~50 MB
echo.

REM Create deployment directory
if not exist "deployment" mkdir deployment
cd deployment

echo.
echo [1/2] Downloading Launch4j...
echo ----------------------------------------
powershell -Command "$ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://sourceforge.net/projects/launch4j/files/launch4j-3/3.50/launch4j-3.50-win32.zip/download' -OutFile 'launch4j.zip' -UseBasicParsing"

if exist launch4j.zip (
    echo ✓ Launch4j downloaded
    echo Extracting...
    powershell -Command "Expand-Archive -Path 'launch4j.zip' -DestinationPath '.' -Force"
    del launch4j.zip
    echo ✓ Launch4j ready
) else (
    echo ✗ Launch4j download failed
    echo Please download manually from: https://launch4j.sourceforge.net/
    exit /b 1
)

echo.
echo [2/2] Downloading Portable JRE 8...
echo ----------------------------------------
echo NOTE: This is a large file (~40 MB), please wait...
powershell -Command "$ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u392-b08/OpenJDK8U-jre_x64_windows_hotspot_8u392b08.zip' -OutFile 'jre.zip' -UseBasicParsing"

if exist jre.zip (
    echo ✓ JRE downloaded
    echo Extracting...
    powershell -Command "Expand-Archive -Path 'jre.zip' -DestinationPath '.' -Force"
    
    REM Rename JRE folder to 'jre'
    for /d %%i in (jdk8u*-jre) do ren "%%i" jre
    
    del jre.zip
    echo ✓ JRE ready
) else (
    echo ✗ JRE download failed
    echo Please download manually from: https://adoptium.net/temurin/releases/?version=8
    exit /b 1
)

cd ..

echo.
echo ========================================
echo ✓ DEPLOYMENT TOOLS READY!
echo ========================================
echo.
echo Tools installed in 'deployment' folder:
echo - deployment\launch4j\
echo - deployment\jre\
echo.
echo Next step: Run 2_create_exe.bat
echo.
