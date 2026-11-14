@echo off
REM Run SIP Inspector with TLS Debug Logging
REM Debug output goes to logs/TLS_Debug.log

echo ========================================
echo SIP Inspector - TLS Debug Mode
echo ========================================
echo.
echo TLS debug logging ENABLED
echo Log file: logs\TLS_Debug.log
echo.
echo Press Ctrl+C to stop
echo ========================================
echo.

java "-Dtls.debug=true" -jar SIP_Simulator_v4.jar
