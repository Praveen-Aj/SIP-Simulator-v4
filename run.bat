@echo off
REM SIP Simulator v4 Run Script
REM Launches the SIP Simulator application with Web Dashboard support

echo ========================================
echo Starting SIP Simulator v4 with Web Dashboard
echo ========================================
echo.

if exist "deployment\jre\bin\java.exe" (
    set "JAVA_CMD=deployment\jre\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

%JAVA_CMD% ^
    -Djava.library.path=lib ^
    -cp "SIP_Simulator_v4.jar;lib/*" sipinspector.Main

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo Application exited with error
    echo ========================================
)
