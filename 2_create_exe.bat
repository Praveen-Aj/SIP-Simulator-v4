@echo off
REM ========================================
REM SIP Simulator - Create Windows EXE
REM ========================================

echo ========================================
echo Creating Windows Executable
echo ========================================
echo.

REM Check if JAR exists
if not exist "SIP_Simulator_v4.jar" (
    echo [FAIL] SIP_Simulator_v4.jar not found!
    echo Please run build.bat first
    exit /b 1
)

REM Check if Launch4j exists
if not exist "deployment\launch4j\launch4jc.exe" (
    echo Launch4j not found!
    echo Please run 1_setup_deployment_tools.bat first
    exit /b 1
)

echo JAR file found
echo Launch4j found
echo.

REM (Re)create icon file from the latest PNG so the EXE icon is always up to date
echo Creating icon file...
call "%~dp0create_icon.bat"

echo Creating EXE wrapper...

REM Run Launch4j
deployment\launch4j\launch4jc.exe launch4j_config.xml

if exist "SIP_Simulator_v4.exe" (
    echo.
    echo ========================================
    echo [OK] EXE CREATED SUCCESSFULLY!
    echo ========================================
    echo.
    echo File: SIP_Simulator_v4.exe
    echo Size: 
    dir SIP_Simulator_v4.exe | find "SIP_Simulator_v4.exe"
    echo.
    echo Next step: Run 3_create_package.bat
    echo.
) else (
    echo.
    echo ========================================
    echo [FAIL] EXE CREATION FAILED
    echo ========================================
    echo.
    echo Please check launch4j_config.xml
    echo.
)
