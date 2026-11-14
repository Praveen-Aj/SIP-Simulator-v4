@echo off
REM Run SIP Inspector with TCP Debug Logging
REM Debug output goes to logs/TCP/TCP_<timestamp>.log

echo ========================================
echo SIP Inspector - TCP Debug Mode
echo ========================================
echo.
echo TCP debug logging ENABLED
echo Log file: logs\TCP\TCP_<timestamp>.log
echo.
echo Press Ctrl+C to stop
echo ========================================
echo.

java "-Dtcp.debug=true" -jar SIP_Simulator_v4.jar
