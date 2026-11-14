@echo off
REM Run SIP Inspector with UDP Debug Logging
REM Debug output goes to logs/UDP/UDP_<timestamp>.log

echo ========================================
echo SIP Inspector - UDP Debug Mode
echo ========================================
echo.
echo UDP debug logging ENABLED
echo Log file: logs\UDP\UDP_<timestamp>.log
echo.
echo Press Ctrl+C to stop
echo ========================================
echo.

java "-Dudp.debug=true" -jar SIP_Simulator_v4.jar
