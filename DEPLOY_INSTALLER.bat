@echo off
REM ========================================
REM SIP Simulator - Create Windows Installer
REM ========================================
REM This creates a professional installer with desktop shortcuts

echo.
echo ========================================
echo SIP SIMULATOR - INSTALLER CREATION
echo ========================================
echo.
echo This will create a professional Windows installer:
echo [OK] Installation wizard (Next, Next, Install)
echo [OK] Desktop shortcut (automatic)
echo [OK] Start Menu entry
echo [OK] Installs to Program Files
echo [OK] Uninstaller included
echo.
echo Total time: ~15-20 minutes (first time)
echo.

REM Step 1: Build application
echo.
echo ========================================
echo STEP 1/5: Building Application
echo ========================================
call "%~dp0build.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Build failed!
    exit /b 1
)

REM Step 2: Setup deployment tools
echo.
echo ========================================
echo STEP 2/5: Setting Up Deployment Tools
echo ========================================

if not exist "deployment\launch4j\launch4jc.exe" (
    echo Deployment tools not found, downloading...
    call "%~dp01_setup_deployment_tools.bat"
    if %ERRORLEVEL% NEQ 0 (
        echo [FAIL] Tool setup failed!
        exit /b 1
    )
) else (
    echo [OK] Deployment tools already installed
)

REM Step 3: Create EXE
echo.
echo ========================================
echo STEP 3/5: Creating Windows EXE
echo ========================================
call "%~dp02_create_exe.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] EXE creation failed!
    exit /b 1
)

REM Step 4: Setup Inno Setup
echo.
echo ========================================
echo STEP 4/5: Setting Up Inno Setup
echo ========================================

set INNO_PATH="%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist %INNO_PATH% if defined ProgramFiles(x86) (
    set INNO_PATH="%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
)
if not exist %INNO_PATH% if defined ProgramFiles (
    set INNO_PATH="%ProgramFiles%\Inno Setup 6\ISCC.exe"
)

if not exist %INNO_PATH% (
    echo Inno Setup not found, downloading...
    call "%~dp04_setup_inno_setup.bat"
    if %ERRORLEVEL% NEQ 0 (
        echo [FAIL] Inno Setup installation failed!
        exit /b 1
    )
) else (
    echo [OK] Inno Setup already installed
)

REM Step 5: Create Installer
echo.
echo ========================================
echo STEP 5/5: Creating Windows Installer
echo ========================================
call "%~dp05_create_installer.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Installer creation failed!
    exit /b 1
)

echo.
echo ========================================
echo [OK][OK][OK] INSTALLER CREATED! [OK][OK][OK]
echo ========================================
echo.
echo Your professional Windows installer is ready:
echo.
echo FILE: installer_output\SIP_Simulator_Setup_v4.0.exe
echo.
dir installer_output\SIP_Simulator_Setup_*.exe
echo.
echo FEATURES:
echo [OK] Professional installation wizard
echo [OK] Desktop shortcut created automatically
echo [OK] Start Menu entry
echo [OK] Installs to Program Files
echo [OK] Uninstaller included
echo.
echo DISTRIBUTION:
echo Share this installer with your team!
echo They just double-click and follow the wizard.
echo.
echo Launching installer for quick testing...
start "" "installer_output\SIP_Simulator_Setup_v4.0.exe"
echo.
echo TESTING:
echo 1. Run through the installer wizard
echo 2. Verify desktop shortcut works
echo 3. Verify Start Menu entry works
echo 4. Test the application
echo 5. Test uninstaller
echo.
echo ========================================
echo Done.
