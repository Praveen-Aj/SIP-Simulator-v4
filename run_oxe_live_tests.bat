@echo off
setlocal enabledelayedexpansion

echo ================================================================================
echo    SIP Simulator v4 - Live OXE PBX Integration ^& Qualification Suite
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
javac -source 1.8 -target 1.8 -cp "build;lib/*" -d build test/OxeLiveConcurrentCallTest.java test/OxeProductionCallEngineTest.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Live test compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo Executing Live OXE Concurrent Qualification...
"%JAVA_CMD%" -cp "build;lib/*" test.OxeLiveConcurrentCallTest
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% equ 0 (
    echo.
    echo ================================================================================
    echo [SUCCESS] ALL LIVE OXE TESTS PASSED CLEANLY!
    echo ================================================================================
) else (
    echo.
    echo ================================================================================
    echo [FAILURE] LIVE OXE TESTS FAILED! (Exit Code: %EXIT_CODE%)
    echo ================================================================================
)

exit /b %EXIT_CODE%
