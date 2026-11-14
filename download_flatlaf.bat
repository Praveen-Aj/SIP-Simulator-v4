@echo off
REM Download FlatLaf library for modern Look & Feel

echo ========================================
echo FlatLaf Download Helper
echo ========================================
echo.
echo This script will help you download FlatLaf library
echo.
echo Option 1: Manual Download
echo   1. Open browser to: https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/
echo   2. Download: flatlaf-3.2.5.jar
echo   3. Save to: lib\flatlaf-3.2.5.jar
echo.
echo Option 2: PowerShell Download (Auto)
echo   Press any key to download automatically...
echo.

echo.
echo Downloading FlatLaf 3.2.5...
echo.

powershell -Command "& {Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/flatlaf-3.2.5.jar' -OutFile 'lib\flatlaf-3.2.5.jar'}"

if exist lib\flatlaf-3.2.5.jar (
    echo.
    echo ========================================
    echo SUCCESS! FlatLaf downloaded!
    echo ========================================
    echo.
    echo File saved to: lib\flatlaf-3.2.5.jar
    echo Size: 
    dir lib\flatlaf-3.2.5.jar | find "flatlaf"
    echo.
    echo Next steps:
    echo 1. Run build.bat to rebuild with FlatLaf
    echo 2. Run the application to see modern theme
    echo.
) else (
    echo.
    echo ========================================
    echo DOWNLOAD FAILED!
    echo ========================================
    echo.
    echo Please download manually:
    echo 1. Go to: https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/
    echo 2. Download: flatlaf-3.2.5.jar
    echo 3. Save to: lib\flatlaf-3.2.5.jar
    echo.
)
