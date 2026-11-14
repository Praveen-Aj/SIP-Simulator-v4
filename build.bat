@echo off
REM SIP Inspector Build Script
REM Compiles all source files and creates executable JAR

echo ========================================
echo SIP Inspector Build Script
echo ========================================

REM Switch to project directory regardless of caller working directory
cd /d "%~dp0"

REM Clean build directory
echo Cleaning build directory...
if exist build rmdir /s /q build
mkdir build

REM Compile all Java files
echo Compiling source files...
javac -encoding UTF-8 -source 1.8 -target 1.8 -cp "lib/*;src" -d build -sourcepath src src\sipinspector\Main.java src\sipinspector\Media\CliSipRegisterTest.java src\sipinspector\Media\CliSipLegTest.java src\sipinspector\Utils\AssertionUnitTest.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED - Compilation errors
    echo ========================================
    exit /b 1
)

REM Copy resources
echo Copying resources...
if exist src\Icons xcopy /E /I /Y src\Icons build\Icons
if exist scenarios xcopy /E /I /Y scenarios build\scenarios
if exist values xcopy /E /I /Y values build\values

REM Copy lib files into build
echo Copying library files...
mkdir build\lib
copy lib\*.jar build\lib\

REM Create manifest
echo Creating manifest...
echo Main-Class: sipinspector.Main> build\MANIFEST.MF
echo Class-Path: lib/miglayout-3.7.3.1-swing.jar lib/miglayout-swing-4.2.jar lib/jpcap.jar lib/flatlaf-3.2.5.jar>> build\MANIFEST.MF

REM Create JAR
echo Creating JAR file...
cd build
jar cfm ..\SIP_Simulator_v4.jar MANIFEST.MF *
set JAR_ERROR=%ERRORLEVEL%
cd ..

if %JAR_ERROR% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED - JAR creation failed
    echo ========================================
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo JAR file created: SIP_Simulator_v4.jar
echo.
echo To run: java -jar SIP_Simulator_v4.jar
echo.
