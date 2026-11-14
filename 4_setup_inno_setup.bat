@echo off
REM ========================================
REM Download and Setup Inno Setup
REM ========================================

echo ========================================
echo Downloading Inno Setup Compiler
echo ========================================
echo.
echo Inno Setup creates professional Windows installers
echo with desktop shortcuts and Start Menu entries.
echo.

if not exist "deployment" mkdir deployment
cd deployment

echo Downloading Inno Setup (~3 MB)...
powershell -Command "$ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://jrsoftware.org/download.php/is.exe' -OutFile 'innosetup_installer.exe' -UseBasicParsing"

if exist innosetup_installer.exe (
    echo.
    echo ✓ Inno Setup downloaded
    echo.
    echo ========================================
    echo INSTALLATION REQUIRED
    echo ========================================
    echo.
    echo Please install Inno Setup:
    echo 1. The installer will open now
    echo 2. Click "Next" through the wizard
    echo 3. Use default installation path
    echo 4. Complete the installation
    echo.
    echo After installation, run: 5_create_installer.bat
    echo.
    
    REM Run installer
    start /wait innosetup_installer.exe
    
    echo.
    echo ========================================
    echo ✓ Inno Setup Installation Complete
    echo ========================================
    echo.
    echo Next step: Run 5_create_installer.bat
    echo.
) else (
    echo.
    echo ✗ Download failed
    echo.
    echo Please download manually from:
    echo https://jrsoftware.org/isdl.php
    echo.
)

cd ..
