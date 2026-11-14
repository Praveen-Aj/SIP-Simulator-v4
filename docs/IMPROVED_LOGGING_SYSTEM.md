# ✅ Improved Logging System

## 🎉 What's New

### 1. **Separate Folders for Each Transport**
```
logs/
├── TLS/
│   ├── TLS_20251109_121800.log
│   ├── TLS_20251109_122100.log
│   └── TLS_20251109_122500.log
├── TCP/
│   ├── TCP_20251109_120000.log
│   └── TCP_20251109_121000.log
└── UDP/
    ├── UDP_20251109_115000.log
    └── UDP_20251109_120000.log
```

### 2. **Session-Based Log Files**
- Each run creates a **new log file** with timestamp
- No more appending to same file
- Easy to compare different sessions

### 3. **Reduced Redundant Logs**
- ❌ Removed: "Socket connected, waiting for data..." (repeated 100s of times)
- ✅ Only logs when **actual data is received**
- Much cleaner and easier to read!

### 4. **Ready for UDP/TCP Logging**
- Infrastructure in place
- Can enable UDP/TCP debug logging in future
- Same structure as TLS

---

## 📁 New Log Structure

### Before (Old):
```
logs/
└── TLS_Debug.log  (all sessions appended, 1000s of redundant lines)
```

### After (New):
```
logs/
├── TLS/
│   ├── TLS_20251109_121800.log  ← Session 1
│   ├── TLS_20251109_122100.log  ← Session 2
│   └── TLS_20251109_122500.log  ← Session 3
├── TCP/  (ready for future)
└── UDP/  (ready for future)
```

---

## 🔍 Cleaner Log Output

### Before (Redundant):
```
2025-11-09 12:15:18.868 === TLS RECEIVE DEBUG ===
2025-11-09 12:15:18.868 Socket connected, waiting for data...
2025-11-09 12:15:18.974 === TLS RECEIVE DEBUG ===
2025-11-09 12:15:18.975 Socket connected, waiting for data...
2025-11-09 12:15:19.080 === TLS RECEIVE DEBUG ===
2025-11-09 12:15:19.080 Socket connected, waiting for data...
2025-11-09 12:15:19.187 === TLS RECEIVE DEBUG ===
2025-11-09 12:15:19.187 Socket connected, waiting for data...
(repeated 100+ times)
```

### After (Clean):
```
2025-11-09 12:18:18.256 === TLS SEND DEBUG ===
2025-11-09 12:18:18.257 Socket: /172.19.5.10:5061
2025-11-09 12:18:18.257 --- SIP MESSAGE START ---
REGISTER sip:172.19.5.10:5061 SIP/2.0
Via: SIP/2.0/TLS 10.94.161.179:5061;branch=z9hG4bK20
...
--- SIP MESSAGE END ---
2025-11-09 12:18:18.260 TLS SEND SUCCESS!

2025-11-09 12:18:18.500 === TLS RECEIVE ===
2025-11-09 12:18:18.501 Received 456 bytes from /172.19.5.10:5061
--- RECEIVED SIP MESSAGE START ---
SIP/2.0 200 OK
...
--- RECEIVED SIP MESSAGE END ---
```

**Only logs when something actually happens!**

---

## 🚀 How to Use

### Run with TLS Debug:
```cmd
.\run_with_tls_debug.bat
```

### Log File Location:
```
logs/TLS/TLS_<timestamp>.log
```

Example: `logs/TLS/TLS_20251109_122100.log`

---

## 📊 What Gets Logged

### TLS SEND (Outgoing):
- Socket details (address, port, state)
- Full SIP message content
- Success/failure status

### TLS RECEIVE (Incoming):
- **Only when data is actually received** (no more redundant logs!)
- Bytes received
- Full SIP message content

### TLS OPEN (Connection):
- Handshake details
- Cipher suite
- Protocol version

### TLS CLOSE (Disconnect):
- Stack trace showing why
- Cleanup status

---

## 🎯 Benefits

### 1. Easy Session Comparison
```powershell
# Compare two sessions
fc logs\TLS\TLS_20251109_121800.log logs\TLS\TLS_20251109_122100.log
```

### 2. Clean Logs
- No redundant "waiting for data" messages
- Only actual events logged
- Much easier to read and analyze

### 3. Organized Structure
- Separate folders for each transport
- Easy to find specific session
- No mixing of different transport types

### 4. Session History
- Keep history of all test sessions
- Easy to go back and check old sessions
- Compare behavior over time

---

## 📋 Log File Naming

Format: `<TRANSPORT>_<YYYYMMDD>_<HHMMSS>.log`

Examples:
- `TLS_20251109_121800.log` - TLS session at 12:18:00
- `TLS_20251109_122100.log` - TLS session at 12:21:00
- `TCP_20251109_120000.log` - TCP session at 12:00:00 (future)
- `UDP_20251109_115000.log` - UDP session at 11:50:00 (future)

---

## 🔍 Finding Specific Sessions

### List All TLS Sessions:
```powershell
dir logs\TLS\
```

### Find Latest Session:
```powershell
dir logs\TLS\ | sort LastWriteTime | select -last 1
```

### Open Latest Log:
```powershell
notepad (dir logs\TLS\ | sort LastWriteTime | select -last 1).FullName
```

### Search Across All Sessions:
```powershell
# Find all REGISTER messages
Select-String "REGISTER sip:" logs\TLS\*.log

# Find all 401 responses
Select-String "401 Unauthorized" logs\TLS\*.log
```

---

## 🧹 Managing Log Files

### View Current Session:
```powershell
Get-Content logs\TLS\TLS_*.log -Wait -Tail 50 | select -last 1
```

### Delete Old Logs:
```powershell
# Delete logs older than 7 days
Get-ChildItem logs\TLS\*.log | Where-Object {$_.LastWriteTime -lt (Get-Date).AddDays(-7)} | Remove-Item
```

### Archive Logs:
```powershell
# Create archive folder
mkdir logs\archive

# Move old logs
Move-Item logs\TLS\TLS_202511*.log logs\archive\
```

---

## 📊 Log Analysis Examples

### Count Messages Per Session:
```powershell
$logFile = "logs\TLS\TLS_20251109_122100.log"

# Count sent messages
(Select-String "=== TLS SEND DEBUG ===" $logFile).Count

# Count received messages
(Select-String "=== TLS RECEIVE ===" $logFile).Count
```

### Extract All SIP Messages:
```powershell
$logFile = "logs\TLS\TLS_20251109_122100.log"
$content = Get-Content $logFile
$inMessage = $false
$messages = @()

foreach ($line in $content) {
    if ($line -match "--- SIP MESSAGE START ---") {
        $inMessage = $true
        $currentMessage = @()
    } elseif ($line -match "--- SIP MESSAGE END ---") {
        $inMessage = $false
        $messages += ($currentMessage -join "`n")
    } elseif ($inMessage) {
        $currentMessage += $line
    }
}

# Show all messages
$messages
```

---

## ✅ Summary

| Feature | Old | New |
|---------|-----|-----|
| Log Files | Single file | Session-based |
| Structure | Flat | Organized folders |
| Redundant Logs | 100s of lines | None |
| Readability | Cluttered | Clean |
| Session Tracking | Difficult | Easy |
| Transport Separation | None | TLS/TCP/UDP folders |

---

## 🎊 Result

**Much cleaner, more organized, and easier to analyze!**

- ✅ No more redundant logs
- ✅ Separate file per session
- ✅ Organized by transport type
- ✅ Easy to find and compare sessions
- ✅ Ready for UDP/TCP logging

**Perfect for debugging and analysis!** 🔍📊
