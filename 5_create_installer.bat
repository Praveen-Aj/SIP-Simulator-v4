@echo off
REM ========================================
REM Create Professional Windows Installer
REM ========================================

echo ========================================
echo Creating Windows Installer
echo ========================================
echo.

REM Check if Inno Setup is installed (per-user install checked first - no admin PCs)
set INNO_PATH="%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist %INNO_PATH% if defined ProgramFiles(x86) (
    set INNO_PATH="%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
)
if not exist %INNO_PATH% if defined ProgramFiles (
    set INNO_PATH="%ProgramFiles%\Inno Setup 6\ISCC.exe"
)

if not exist %INNO_PATH% (
    echo [FAIL] Inno Setup not found!
    echo.
    echo Please run: 4_setup_inno_setup.bat
    echo Or install from: https://jrsoftware.org/isdl.php
    echo.
    exit /b 1
)

echo Inno Setup found
echo.

REM Check prerequisites
if not exist "SIP_Simulator_v4.exe" (
    echo SIP_Simulator_v4.exe not found!
    echo Please run 2_create_exe.bat or DEPLOY_INSTALLER.bat first
    exit /b 1
)

if not exist "deployment\jre" (
    echo JRE not found!
    echo Please run 1_setup_deployment_tools.bat first
    exit /b 1
)

echo [OK] All prerequisites found
echo.
echo Compiling installer...
echo This may take 2-3 minutes...
echo.

REM Create output directory
if not exist "installer_output" mkdir installer_output

REM Compile installer
%INNO_PATH% installer_script.iss

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo [OK] INSTALLER CREATED SUCCESSFULLY!
    echo ========================================
    echo.
    echo Output: installer_output\SIP_Simulator_Setup_v4.0.exe
    echo.
    dir installer_output\SIP_Simulator_Setup_*.exe
    echo.
    echo FEATURES:
    echo [OK] Professional installation wizard
    echo [OK] Desktop shortcut created automatically
    echo [OK] Start Menu entry created
    echo [OK] Installs to Program Files
    echo [OK] Uninstaller included
    echo.
    echo DISTRIBUTION:
    echo Share this installer with your team!
    echo They just double-click and follow the wizard.
    echo.
) else (
    echo.
    echo ========================================
    echo [FAIL] INSTALLER CREATION FAILED
    echo ========================================
    echo.
    echo Check the error messages above
    echo.
)
