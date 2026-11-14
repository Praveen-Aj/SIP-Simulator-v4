# 📊 Complete Logging Guide - All Transports

## ✅ All Transport Logging Implemented!

UDP, TCP, and TLS now have complete debug logging! 🎉

---

## 🚀 Quick Start

### 1. Normal Mode (No Debug)
```cmd
.\run.bat
```
- Clean console
- No debug logs
- Use for normal operation

### 2. UDP Debug Mode
```cmd
.\run_with_udp_debug.bat
```
- Logs to: `logs/UDP/UDP_<timestamp>.log`
- Full SIP message content
- Socket initialization details

### 3. TCP Debug Mode
```cmd
.\run_with_tcp_debug.bat
```
- Logs to: `logs/TCP/TCP_<timestamp>.log`
- Full SIP message content
- Connection establishment details

### 4. TLS Debug Mode
```cmd
.\run_with_tls_debug.bat
```
- Logs to: `logs/TLS/TLS_<timestamp>.log`
- Full SIP message content
- Handshake and cipher details

---

## 📁 Log Structure

```
logs/
├── UDP/
│   ├── UDP_20251109_120000.log
│   ├── UDP_20251109_121500.log
│   └── UDP_20251109_123000.log
├── TCP/
│   ├── TCP_20251109_120500.log
│   ├── TCP_20251109_122000.log
│   └── TCP_20251109_123500.log
└── TLS/
    ├── TLS_20251109_121000.log
    ├── TLS_20251109_122500.log
    └── TLS_20251109_124000.log
```

**Each session creates a NEW log file!**

---

## 📋 What Gets Logged

### UDP Logs:
```
2025-11-09 12:30:00.123 === UDP SOCKET INIT ===
2025-11-09 12:30:00.124 Local: 10.94.161.179:5060
2025-11-09 12:30:00.125 UDP socket created successfully

2025-11-09 12:30:05.256 === UDP SEND DEBUG ===
2025-11-09 12:30:05.257 To: 172.19.5.10:5060
2025-11-09 12:30:05.258 Packet length: 349 bytes
2025-11-09 12:30:05.259 --- SIP MESSAGE START ---
REGISTER sip:172.19.5.10:5060 SIP/2.0
Via: SIP/2.0/UDP 10.94.161.179:5060;branch=z9hG4bK123
...
--- SIP MESSAGE END ---
2025-11-09 12:30:05.260 UDP SEND SUCCESS!
```

### TCP Logs:
```
2025-11-09 12:30:00.123 === TCP OPEN DEBUG ===
2025-11-09 12:30:00.124 Remote: 172.19.5.10:5060
2025-11-09 12:30:00.125 Local: 10.94.161.179:5060
2025-11-09 12:30:00.350 TCP connection established
2025-11-09 12:30:00.351 === TCP OPEN COMPLETE ===

2025-11-09 12:30:05.256 === TCP SEND DEBUG ===
2025-11-09 12:30:05.257 Socket: /172.19.5.10:5060
2025-11-09 12:30:05.258 Packet length: 349 bytes
2025-11-09 12:30:05.259 --- SIP MESSAGE START ---
REGISTER sip:172.19.5.10:5060 SIP/2.0
Via: SIP/2.0/TCP 10.94.161.179:5060;branch=z9hG4bK123
...
--- SIP MESSAGE END ---
2025-11-09 12:30:05.260 TCP SEND SUCCESS!
```

### TLS Logs:
```
2025-11-09 12:30:00.123 === TLS OPEN DEBUG ===
2025-11-09 12:30:00.124 Remote: /172.19.5.10:5061
2025-11-09 12:30:00.125 Local: /10.94.161.179:5061
2025-11-09 12:30:00.200 Starting TLS handshake...
2025-11-09 12:30:00.450 TLS handshake completed successfully!
2025-11-09 12:30:00.451 Cipher suite: TLS_AES_256_GCM_SHA384
2025-11-09 12:30:00.452 Protocol: TLSv1.3

2025-11-09 12:30:05.256 === TLS SEND DEBUG ===
2025-11-09 12:30:05.257 Socket: /172.19.5.10:5061
2025-11-09 12:30:05.258 Packet length: 349 bytes
2025-11-09 12:30:05.259 --- SIP MESSAGE START ---
REGISTER sip:172.19.5.10:5061 SIP/2.0
Via: SIP/2.0/TLS 10.94.161.179:5061;branch=z9hG4bK123
...
--- SIP MESSAGE END ---
2025-11-09 12:30:05.260 TLS SEND SUCCESS!
```

---

## 🎯 When to Use Each Mode

| Transport | Use When | Log Location |
|-----------|----------|--------------|
| **UDP** | Testing basic SIP | `logs/UDP/` |
| **TCP** | Testing reliable transport | `logs/TCP/` |
| **TLS** | Testing secure SIP | `logs/TLS/` |

---

## 🔍 Viewing Logs

### Live Tail (While Running):
```powershell
# UDP
Get-Content logs\UDP\UDP_*.log -Wait -Tail 50 | select -last 1

# TCP
Get-Content logs\TCP\TCP_*.log -Wait -Tail 50 | select -last 1

# TLS
Get-Content logs\TLS\TLS_*.log -Wait -Tail 50 | select -last 1
```

### View Latest Log:
```powershell
# UDP
notepad (dir logs\UDP\ | sort LastWriteTime | select -last 1).FullName

# TCP
notepad (dir logs\TCP\ | sort LastWriteTime | select -last 1).FullName

# TLS
notepad (dir logs\TLS\ | sort LastWriteTime | select -last 1).FullName
```

---

## 📊 Existing SIP Logging (GUI)

**Don't forget the built-in SIP logging!**

### Enable from GUI:
1. Open SIP Inspector
2. Go to **Options → SIP Logging** (or press Ctrl+L)
3. Logs ALL messages to `YYYY-MM-DD_HH-mm-ss.log`

### What it logs:
- ALL transports (UDP/TCP/TLS)
- Sent and received messages
- Timestamps
- Source and destination

### When to use:
- ✅ Normal testing (all transports)
- ✅ Complete message history
- ✅ Simple troubleshooting

---

## 🆚 Debug Logs vs SIP Logging

| Feature | Debug Logs | SIP Logging (GUI) |
|---------|------------|-------------------|
| **Enable** | Command line flag | GUI checkbox |
| **Transports** | Separate per transport | All together |
| **Sessions** | New file per session | Append to same file |
| **Details** | Socket/connection details | Messages only |
| **Use Case** | Deep troubleshooting | General logging |

**Use BOTH for complete visibility!**

---

## 💡 Pro Tips

### 1. Compare Transports:
```powershell
# Run UDP test
.\run_with_udp_debug.bat
# ... test ...
# Close app

# Run TCP test
.\run_with_tcp_debug.bat
# ... test ...
# Close app

# Compare logs
fc logs\UDP\UDP_*.log logs\TCP\TCP_*.log
```

### 2. Search Across All Logs:
```powershell
# Find all REGISTER messages
Select-String "REGISTER sip:" logs\*\*.log

# Find all 401 responses
Select-String "401 Unauthorized" logs\*\*.log

# Find specific Call-ID
Select-String "Call-ID: 12345" logs\*\*.log
```

### 3. Count Messages by Transport:
```powershell
# UDP messages
(Select-String "=== UDP SEND" logs\UDP\*.log).Count

# TCP messages
(Select-String "=== TCP SEND" logs\TCP\*.log).Count

# TLS messages
(Select-String "=== TLS SEND" logs\TLS\*.log).Count
```

### 4. Clean Old Logs:
```powershell
# Delete logs older than 7 days
Get-ChildItem logs\*\*.log | Where-Object {$_.LastWriteTime -lt (Get-Date).AddDays(-7)} | Remove-Item
```

---

## 🎨 Manual Command Line

### UDP Debug:
```powershell
java "-Dudp.debug=true" -jar SIPInspector.jar
```

### TCP Debug:
```powershell
java "-Dtcp.debug=true" -jar SIPInspector.jar
```

### TLS Debug:
```powershell
java "-Dtls.debug=true" -jar SIPInspector.jar
```

**Note:** Quotes required in PowerShell!

---

## ✅ Summary

| Mode | Script | Log Location | Use For |
|------|--------|--------------|---------|
| **Normal** | `run.bat` | None | Production |
| **UDP Debug** | `run_with_udp_debug.bat` | `logs/UDP/` | UDP troubleshooting |
| **TCP Debug** | `run_with_tcp_debug.bat` | `logs/TCP/` | TCP troubleshooting |
| **TLS Debug** | `run_with_tls_debug.bat` | `logs/TLS/` | TLS troubleshooting |
| **SIP Logging** | GUI (Ctrl+L) | `YYYY-MM-DD_HH-mm-ss.log` | All transports |

---

## 🎉 Complete Logging System!

**You now have:**
- ✅ UDP debug logging
- ✅ TCP debug logging
- ✅ TLS debug logging
- ✅ Built-in SIP logging (GUI)
- ✅ Organized folder structure
- ✅ Session-based files
- ✅ Full SIP message content
- ✅ Clean, readable logs

**Perfect for debugging any transport protocol!** 🚀📊
