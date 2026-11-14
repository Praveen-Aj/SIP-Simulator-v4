@echo off
REM SIP Simulator - Build and Run
REM 1) Compile and package JAR via build.bat
REM 2) Launch application via run.bat if build succeeds

echo ========================================
echo BUILD AND RUN - SIP Simulator
echo ========================================

echo Stripping UTF-8 BOMs (if any) from SIPInspectorMainFrame.java...
python -c "from pathlib import Path; p=Path('src/sipinspector/SIPInspectorMainFrame.java'); data=p.read_bytes(); cleaned=data.lstrip(b'\xef\xbb\xbf'); changed=data!=cleaned; print('Removed BOM from SIPInspectorMainFrame.java' if changed else 'No BOM detected in SIPInspectorMainFrame.java'); p.write_bytes(cleaned) if changed else None"

set SKIP_BUILD_PAUSE=1
call "%~dp0build.bat"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD AND RUN ABORTED - BUILD FAILED
    echo ========================================
    goto :EOF
)

echo.
echo ========================================
echo BUILD SUCCESSFUL - STARTING APPLICATION
echo ========================================

echo.
call "%~dp0run.bat"

echo.
echo ========================================
echo BUILD AND RUN FINISHED
echo ========================================
