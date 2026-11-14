@echo off
REM Sync SIP Simulator v2 changes to _v3 folder
REM This script copies all modified files to the _v3 clone for future v3 development

setlocal enabledelayedexpansion

REM Define source and destination
set SOURCE_DIR=%~dp0
set DEST_DIR=%SOURCE_DIR%..\trunk_v3

REM Check if destination exists
if not exist "%DEST_DIR%" (
    echo Error: _v3 folder not found at %DEST_DIR%
    echo Please ensure SIp_Simulator_v2_v3 folder exists
    pause
    exit /b 1
)

echo.
echo ========================================
echo SIP Simulator v2 to v3 Sync
echo ========================================
echo Source: %SOURCE_DIR%
echo Destination: %DEST_DIR%
echo.

REM Sync source code
echo Syncing source code...
xcopy "%SOURCE_DIR%src" "%DEST_DIR%\src" /E /I /Y /Q
if errorlevel 1 goto error

REM Sync scenarios
echo Syncing scenarios...
xcopy "%SOURCE_DIR%scenarios" "%DEST_DIR%\scenarios" /E /I /Y /Q
if errorlevel 1 goto error

REM Sync values
echo Syncing values...
xcopy "%SOURCE_DIR%values" "%DEST_DIR%\values" /E /I /Y /Q
if errorlevel 1 goto error

REM Sync documentation
echo Syncing documentation...
copy "%SOURCE_DIR%README.md" "%DEST_DIR%\README.md" /Y >nul
copy "%SOURCE_DIR%CHANGELOG.md" "%DEST_DIR%\CHANGELOG.md" /Y >nul
if errorlevel 1 goto error

REM Sync build files
echo Syncing build files...
copy "%SOURCE_DIR%build.bat" "%DEST_DIR%\build.bat" /Y >nul
if exist "%SOURCE_DIR%MANIFEST.MF" (
    copy "%SOURCE_DIR%MANIFEST.MF" "%DEST_DIR%\MANIFEST.MF" /Y >nul
    if errorlevel 1 goto error
)

REM Sync lib folder
echo Syncing libraries...
xcopy "%SOURCE_DIR%lib" "%DEST_DIR%\lib" /E /I /Y /Q
if errorlevel 1 goto error

echo.
echo ========================================
echo Sync Complete!
echo ========================================
echo All changes have been synced to _v3 folder
echo Ready for v3 development
echo.
pause
exit /b 0

:error
echo.
echo ========================================
echo Sync Failed!
echo ========================================
echo An error occurred during sync
echo Please check the paths and try again
echo.
pause
exit /b 1
