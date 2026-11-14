@echo off
REM Create ICO file for EXE from pre-built ICO asset

echo Copying SIPSimulator.ico to SIP_Simulator.ico...

copy /Y "src\Icons\SIPSimulator.ico" "SIP_Simulator.ico" >NUL

if exist SIP_Simulator.ico (
    echo.
    echo [OK] Icon file created successfully!
) else (
    echo.
    echo [FAIL] Icon creation failed
    echo Using default icon instead
)
