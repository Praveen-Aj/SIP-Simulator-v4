@echo off
REM ========================================
REM SIP Simulator - Create Distribution Package
REM ========================================

echo ========================================
echo Creating Distribution Package
echo ========================================
echo.

REM Check if EXE exists
if not exist "SIP_Simulator_v4.exe" (
    echo [FAIL] SIP_Simulator_v4.exe not found!
    echo Please run 2_create_exe.bat first
    exit /b 1
)

REM Check if JRE exists
if not exist "deployment\jre" (
    echo [FAIL] JRE not found!
    echo Please run 1_setup_deployment_tools.bat first
    exit /b 1
)

echo Creating package directory...
if exist "SIP_Simulator_Package" rmdir /s /q SIP_Simulator_Package
mkdir SIP_Simulator_Package

echo.
echo Copying files...
echo - Executable
copy SIP_Simulator_v4.exe SIP_Simulator_Package\ >nul
copy SIP_Simulator_v4.jar SIP_Simulator_Package\ >nul

echo - Java Runtime
xcopy deployment\jre SIP_Simulator_Package\jre\ /E /I /Q >nul

echo - Libraries
xcopy lib SIP_Simulator_Package\lib\ /E /I /Q >nul

echo - Scenarios
xcopy scenarios SIP_Simulator_Package\scenarios\ /E /I /Q >nul

echo - Icons
xcopy src\Icons SIP_Simulator_Package\Icons\ /E /I /Q >nul

echo - Security
if exist security xcopy security SIP_Simulator_Package\security\ /E /I /Q >nul

echo - Fonts
if exist fonts xcopy fonts SIP_Simulator_Package\fonts\ /E /I /Q >nul

echo - Values
if exist values xcopy values SIP_Simulator_Package\values\ /E /I /Q >nul

echo - Config
if exist config xcopy config SIP_Simulator_Package\config\ /E /I /Q >nul

echo - Web Dashboard
if exist deployment\webapp_dist xcopy deployment\webapp_dist SIP_Simulator_Package\web-app\ /E /I /Q >nul

echo - Node runtime ^(bundled for Web Dashboard^)
if exist deployment\node xcopy deployment\node SIP_Simulator_Package\node\ /E /I /Q >nul

REM Create README
echo Creating README...
(
echo ========================================
echo SIP Simulator v4.0
echo ========================================
echo.
echo INSTALLATION:
echo 1. Extract this folder anywhere on your computer
echo 2. Double-click SIP_Simulator_v4.exe to run
echo.
echo REQUIREMENTS:
echo - Windows 7 or later
echo - No Java installation needed ^(bundled^)
echo.
echo FEATURES:
echo - UDP/TCP/TLS transport protocols
echo - SIP message testing
echo - Scenario management
echo - Statistics and export
echo - Multiple themes
echo.
echo FOLDER STRUCTURE:
echo - SIP_Simulator_v4.exe     Main application
echo - jre\                  Java Runtime ^(bundled^)
echo - lib\                  Application libraries
echo - scenarios\            SIP test scenarios
echo - Icons\                UI icons
echo - security\             TLS certificates
echo - fonts\                Emoji fonts
echo.
echo SUPPORT:
echo For issues or questions, contact your team lead.
echo.
echo ========================================
) > SIP_Simulator_Package\README.txt

REM Create logs directory
mkdir SIP_Simulator_Package\logs >nul 2>&1

echo.
echo Creating ZIP archive...
powershell -Command "Compress-Archive -Path 'SIP_Simulator_Package\*' -DestinationPath 'SIP_Simulator_v4.0_Portable.zip' -Force"

if exist "SIP_Simulator_v4.0_Portable.zip" (
    echo.
    echo ========================================
    echo [OK] PACKAGE CREATED SUCCESSFULLY!
    echo ========================================
    echo.
    echo Package folder: SIP_Simulator_Package\
    echo ZIP archive: SIP_Simulator_v4.0_Portable.zip
    echo.
    echo Package size:
    dir SIP_Simulator_v4.0_Portable.zip | find "SIP_Simulator"
    echo.
    echo DISTRIBUTION:
    echo 1. Share the ZIP file with your team
    echo 2. They extract and run SIP_Simulator_v4.exe
    echo 3. No installation or Java needed!
    echo.
    echo TESTING:
    echo - Test the package on a different computer
    echo - Extract to a different path
    echo Please check launch4j_config.xml
    echo.
) else (
    echo.
    echo ========================================
    echo PACKAGE CREATION FAILED
    echo ========================================
    echo.
)

pause
