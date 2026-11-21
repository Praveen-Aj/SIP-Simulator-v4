@echo off
setlocal enabledelayedexpansion

echo ================================================================================
echo    SIP Simulator v4 - Offline Master Regression Test Suite
echo ================================================================================

cd /d "%~dp0"

REM Find Java runtime
set "JAVA_CMD=java"
if exist "runtime\jre\bin\java.exe" (
    set "JAVA_CMD=runtime\jre\bin\java.exe"
)

echo Using Java: %JAVA_CMD%
"%JAVA_CMD%" -version

echo.
echo Compiling tests...
javac -source 1.8 -target 1.8 -cp "build;lib/*;src" -d build test/*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Test compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo Executing Offline Full Regression Suite...
"%JAVA_CMD%" -cp "build;lib/*" test.OfflineFullRegressionSuite
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% equ 0 (
    echo.
    echo ================================================================================
    echo [SUCCESS] ALL OFFLINE REGRESSION SUITES PASSED CLEANLY!
    echo ================================================================================
) else (
    echo.
    echo ================================================================================
    echo [FAILURE] ONE OR MORE REGRESSION SUITES FAILED! (Exit Code: %EXIT_CODE%)
    echo ================================================================================
)

exit /b %EXIT_CODE%
