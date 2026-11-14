# How to Run SIP Inspector

## 🚀 Quick Start

### Normal Mode (No Debug Logging)
**Double-click:** `run.bat`

**Or from command line:**
```cmd
.\run.bat
```

---

### TLS Debug Mode (Logs to File)
**Double-click:** `run_with_tls_debug.bat`

**Or from command line:**
```cmd
.\run_with_tls_debug.bat
```

**Or manually:**
```cmd
java "-Dtls.debug=true" -jar SIPInspector.jar
```

**Note:** Quotes around `-Dtls.debug=true` are required in PowerShell!

---

## 📁 Log Files

When TLS debug is enabled, logs are written to:
```
logs/TLS_Debug.log
```

The `logs/` folder is created automatically.

---

## 🔍 Viewing TLS Debug Logs

### While App is Running:
```powershell
# In another PowerShell window
Get-Content logs\TLS_Debug.log -Wait -Tail 50
```

### After Testing:
```cmd
notepad logs\TLS_Debug.log
```

---

## 📋 What Gets Logged

### TLS Debug Logs (`logs/TLS_Debug.log`):
- TLS connection establishment
- TLS handshake details (cipher suite, protocol)
- Message send operations
- Message receive operations
- Socket close events with stack traces

### Example:
```
2025-11-09 12:10:45.123 === TLS OPEN DEBUG ===
2025-11-09 12:10:45.124 Remote: /172.19.5.10:5061
2025-11-09 12:10:45.450 TLS handshake completed successfully!
2025-11-09 12:10:45.451 Cipher suite: TLS_AES_256_GCM_SHA384
2025-11-09 12:10:46.100 === TLS SEND DEBUG ===
2025-11-09 12:10:46.105 TLS SEND SUCCESS!
```

---

## 🎯 When to Use Each Mode

### Use Normal Mode When:
- ✅ Everything is working
- ✅ You want clean console output
- ✅ Normal testing/usage

### Use TLS Debug Mode When:
- 🔍 Troubleshooting TLS connection issues
- 🔍 Finding out why sockets close
- 🔍 Debugging message send/receive problems
- 🔍 Analyzing TLS handshake failures

---

## 🧹 Managing Log Files

### View Current Log:
```cmd
notepad logs\TLS_Debug.log
```

### Clear Old Logs:
```cmd
del logs\TLS_Debug.log
```

### Archive Logs:
```cmd
move logs\TLS_Debug.log logs\TLS_Debug_backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%.log
```

---

## ⚙️ Future: UDP and TCP Debug Logs

The `logs/` folder is ready for additional debug logs:
- `logs/TLS_Debug.log` - TLS operations (current)
- `logs/TCP_Debug.log` - TCP operations (future)
- `logs/UDP_Debug.log` - UDP operations (future)

---

## ✅ Summary

| Script | Debug Logging | Log Location |
|--------|---------------|--------------|
| `run.bat` | Disabled | None |
| `run_with_tls_debug.bat` | TLS only | `logs/TLS_Debug.log` |

**Tip:** Always start with normal mode. Enable debug only when troubleshooting!
