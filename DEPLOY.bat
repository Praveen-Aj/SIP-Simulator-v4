@echo off
REM ========================================
REM SIP Simulator - ONE-CLICK DEPLOYMENT
REM ========================================
REM This script runs all deployment steps automatically

echo.
echo ========================================
echo SIP SIMULATOR - DEPLOYMENT WIZARD
echo ========================================
echo.
echo This will:
echo 1. Build the application
echo 2. Download deployment tools (if needed)
echo 3. Create Windows EXE
echo 4. Package for distribution
echo.
echo Total time: ~10-15 minutes (first time)
echo.

REM Step 1: Build
echo.
echo ========================================
echo STEP 1/4: Building Application
echo ========================================
call "%~dp0build.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Build failed!
    exit /b 1
)

REM Step 2: Check if deployment tools exist
echo.
echo ========================================
echo STEP 2/4: Checking Deployment Tools
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
echo STEP 3/4: Creating Windows EXE
echo ========================================
call "%~dp02_create_exe.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] EXE creation failed!
    exit /b 1
)

REM Step 4: Create Package
echo.
echo ========================================
echo STEP 4/4: Creating Distribution Package
echo ========================================
call "%~dp03_create_package.bat"
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Package creation failed!
    exit /b 1
)

echo.
echo ========================================
echo [OK][OK][OK] DEPLOYMENT COMPLETE! [OK][OK][OK]
echo ========================================
echo.
echo Your distribution package is ready:
echo.
echo FILE: SIP_Simulator_v4.0_Portable.zip
echo.
echo NEXT STEPS:
echo 1. Test the package on your machine
echo 2. Extract to a different folder
echo 3. Run SIP_Simulator.exe
echo 4. Verify all features work
echo 5. Share ZIP with your team
echo.
echo ========================================
