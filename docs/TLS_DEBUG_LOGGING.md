# TLS Debug Logging - Usage Guide

## ✅ Clean Console Output with File Logging

All TLS debug messages now go to a **separate log file** instead of cluttering the console!

---

## 🚀 How to Run

### Option 1: Run WITHOUT Debug Logging (Clean Console)
```cmd
cd "<project-root>"
java -jar SIPInspector.jar
```
**Result:** Clean console, no debug messages

### Option 2: Run WITH Debug Logging (File Output)

**Easy way - Double-click:**
```
run_with_tls_debug.bat
```

**Or from command line:**
```cmd
cd "<project-root>"
.\run_with_tls_debug.bat
```

**Or manually (PowerShell - note the quotes!):**
```powershell
java "-Dtls.debug=true" -jar SIPInspector.jar
```

**Result:** All TLS debug goes to `logs/TLS_Debug.log` file

---

## 📁 Log File Location

When debug is enabled, all TLS debug output is written to:
```
logs/TLS_Debug.log
```

The `logs/` folder is automatically created if it doesn't exist.

**Future logs:**
- `logs/TLS_Debug.log` - TLS operations (current)
- `logs/TCP_Debug.log` - TCP operations (future)
- `logs/UDP_Debug.log` - UDP operations (future)

---

## 📋 What Gets Logged

### TLS Connection:
```
2025-11-09 12:06:45.123 === TLS OPEN DEBUG ===
2025-11-09 12:06:45.124 Remote: /172.19.5.10:5061
2025-11-09 12:06:45.125 Local: /10.94.161.179:5061
2025-11-09 12:06:45.126 Initializing SSL context...
2025-11-09 12:06:45.200 SSL context initialized
2025-11-09 12:06:45.201 Starting TLS handshake...
2025-11-09 12:06:45.450 TLS handshake completed successfully!
2025-11-09 12:06:45.451 Cipher suite: TLS_AES_256_GCM_SHA384
2025-11-09 12:06:45.452 Protocol: TLSv1.3
2025-11-09 12:06:45.453 === TLS OPEN COMPLETE - SUCCESS ===
```

### TLS Send:
```
2025-11-09 12:06:46.100 === TLS SEND DEBUG ===
2025-11-09 12:06:46.101 sslSocket: Socket[addr=/172.19.5.10,port=5061,localport=5061]
2025-11-09 12:06:46.102 sslSocket.isConnected(): true
2025-11-09 12:06:46.103 Packet length: 349
2025-11-09 12:06:46.104 Writing 349 bytes to SSL socket...
2025-11-09 12:06:46.105 TLS SEND SUCCESS!
2025-11-09 12:06:46.106 === TLS SEND COMPLETE ===
```

### TLS Receive:
```
2025-11-09 12:06:46.200 === TLS RECEIVE DEBUG ===
2025-11-09 12:06:46.201 Socket connected, waiting for data...
2025-11-09 12:06:46.202 Received 456 bytes
2025-11-09 12:06:46.203 TLS RECEIVE SUCCESS - SIP message complete
```

### TLS Close (with stack trace):
```
2025-11-09 12:06:47.100 === TLS CLOSE DEBUG ===
2025-11-09 12:06:47.101 Closing TLS socket...
2025-11-09 12:06:47.102 Stack trace for close():
java.lang.Exception: Close called
    at sipinspector.Socket.TLSClientSocket_V6.close(TLSClientSocket_V6.java:163)
    at sipinspector.SIPCall.closeSocket(SIPCall.java:456)
    at sipinspector.Threads.CallMaintainerThread.run(CallMaintainerThread.java:89)
2025-11-09 12:06:47.103 SSL socket closed
2025-11-09 12:06:47.104 === TLS CLOSE COMPLETE ===
```

---

## 🔍 Viewing the Log File

### While App is Running:
```powershell
# In another PowerShell window, tail the log file
Get-Content logs\TLS_Debug.log -Wait -Tail 50
```

### After Testing:
```cmd
# View the entire log
notepad logs\TLS_Debug.log

# Or in PowerShell
Get-Content logs\TLS_Debug.log
```

---

## 🎯 Use Cases

### Normal Usage (No Debug):
```cmd
java -jar SIPInspector.jar
```
- Clean console
- Normal SIP Inspector operation
- No performance impact

### Troubleshooting TLS Issues:
```cmd
java -Dtls.debug=true -jar SIPInspector.jar
```
- All TLS operations logged to file
- Stack traces for close events
- Detailed send/receive information
- Find out why sockets close

### Comparing Runs:
```cmd
# First run
java -Dtls.debug=true -jar SIPInspector.jar
# ... test ...
# Close app
mv TLS_Debug.log TLS_Debug_run1.log

# Second run
java -Dtls.debug=true -jar SIPInspector.jar
# ... test ...
# Close app
mv TLS_Debug.log TLS_Debug_run2.log

# Compare
fc TLS_Debug_run1.log TLS_Debug_run2.log
```

---

## 📊 Log File Features

### Timestamps:
Every line has millisecond-precision timestamp

### Stack Traces:
Full stack traces for exceptions and close events

### Auto-Flush:
Log is flushed after every write (safe even if app crashes)

### Append Mode:
Log appends to existing file (doesn't overwrite)

---

## 🧹 Cleaning Up

### Delete Old Logs:
```cmd
del TLS_Debug.log
```

### Archive Logs:
```cmd
# Create logs directory
mkdir logs

# Move old log
move TLS_Debug.log logs\TLS_Debug_%date:~-4,4%%date:~-10,2%%date:~-7,2%.log
```

---

## ✅ Summary

| Mode | Command | Console Output | Log File |
|------|---------|----------------|----------|
| **Normal** | `java -jar SIPInspector.jar` | Clean | None |
| **Debug** | `java -Dtls.debug=true -jar SIPInspector.jar` | Clean | `TLS_Debug.log` |

---

## 🎯 Next Steps

1. **Test without debug** first (clean console)
2. **If issues occur**, enable debug logging
3. **Check TLS_Debug.log** for detailed information
4. **Share log file** if you need help debugging

The console stays clean, and all debug info is in the log file! 🎉
