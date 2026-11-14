# 🎉 What's New in SIP Inspector

## ✅ Latest Updates - GUI Debug Logging

### **NEW: Dynamic Debug Logging from GUI!** 🎯

You can now enable/disable debug logging **while the app is running**!

#### How to Use:
1. Start SIP Inspector normally: `.\run.bat` or `java -jar SIPInspector.jar`
2. Go to **Options** menu
3. Enable logging for any transport:
   - ✅ **UDP Debug Logging** → logs to `logs/UDP/`
   - ✅ **TCP Debug Logging** → logs to `logs/TCP/`
   - ✅ **TLS Debug Logging** → logs to `logs/TLS/`
4. Logs are created immediately with timestamp

#### Benefits:
- ✅ No need to restart app
- ✅ Enable only when needed
- ✅ Toggle on/off anytime
- ✅ Separate logs per transport
- ✅ Session-based log files

---

## 📋 Complete Feature List

### Transport Support:
- ✅ UDP (basic SIP)
- ✅ TCP (reliable transport)
- ✅ TLS (secure SIP with TLS 1.2/1.3)

### Logging Features:
- ✅ **SIP Logging** (Options → SIP Logging)
  - All transports in one file
  - Timestamped messages
  - Send and receive logging
  
- ✅ **Debug Logging** (Options → UDP/TCP/TLS Debug)
  - Separate logs per transport
  - Full SIP message content
  - Socket/connection details
  - Session-based files

### Scenario Features:
- ✅ **Conditions** - Branch based on responses
- ✅ **Commands** - Pause, set variables, execute actions
- ✅ **Variables** - Dynamic value substitution

### Advanced Features:
- ✅ **Remote RTP** - Media configuration
- ✅ **QoS** - Traffic prioritization
- ✅ **Find/Replace** - Search in messages
- ✅ **SDP Calculator** - Media calculations

---

## 📁 File Organization

```
SIPInspector/trunk/
├── SIPInspector.jar           # Main application
├── run.bat                    # Normal mode
├── run_with_udp_debug.bat     # UDP debug (command line)
├── run_with_tcp_debug.bat     # TCP debug (command line)
├── run_with_tls_debug.bat     # TLS debug (command line)
├── logs/                      # Debug logs (auto-created)
│   ├── UDP/                   # UDP sessions
│   ├── TCP/                   # TCP sessions
│   └── TLS/                   # TLS sessions
├── security/                  # TLS certificates (auto-created)
├── scenarios/                 # SIP scenarios
└── Documentation/
    ├── HOW_TO_RUN.md
    ├── COMPLETE_LOGGING_GUIDE.md
    ├── EXISTING_FEATURES_ANALYSIS.md
    ├── FEATURES_AND_CAPABILITIES.md
    └── FUTURE_ENHANCEMENTS_ROADMAP.md
```

---

## 🚀 Quick Start Guide

### 1. Normal Use:
```cmd
.\run.bat
```

### 2. With Debug Logging (GUI):
```cmd
.\run.bat
# Then: Options → UDP/TCP/TLS Debug Logging
```

### 3. With Debug Logging (Command Line):
```cmd
.\run_with_tls_debug.bat  # For TLS
.\run_with_tcp_debug.bat  # For TCP
.\run_with_udp_debug.bat  # For UDP
```

---

## 📊 Options Menu

```
Options
├── SIP Logging (Ctrl+L)        # All transports, one file
├── Remote RTP...               # Media configuration
├── QoS                         # Traffic prioritization
├── ────────────────            # Separator
├── UDP Debug Logging           # UDP debug to logs/UDP/
├── TCP Debug Logging           # TCP debug to logs/TCP/
└── TLS Debug Logging           # TLS debug to logs/TLS/
```

---

## 🎯 Use Cases

### Basic Testing:
1. Enable **SIP Logging** (Options → SIP Logging)
2. Run your scenario
3. Check log file for all messages

### Deep Troubleshooting:
1. Enable **Transport Debug Logging** (Options → UDP/TCP/TLS Debug)
2. Run your scenario
3. Check `logs/<TRANSPORT>/` for detailed debug info

### TLS Issues:
1. Enable **TLS Debug Logging**
2. Check handshake details
3. Verify cipher suite and protocol
4. See full SIP messages with transport headers

---

## 📚 Documentation

### Getting Started:
- `HOW_TO_RUN.md` - How to run the application
- `COMPLETE_LOGGING_GUIDE.md` - Complete logging guide

### Features:
- `EXISTING_FEATURES_ANALYSIS.md` - All existing features explained
- `FEATURES_AND_CAPABILITIES.md` - Complete feature list

### Future:
- `FUTURE_ENHANCEMENTS_ROADMAP.md` - Planned enhancements

---

## 🔄 Recent Changes

### Version: Current (with GUI Debug Logging)
- ✅ Added UDP/TCP/TLS debug logging menu items
- ✅ Dynamic enable/disable from GUI
- ✅ Organized log folder structure
- ✅ Session-based log files
- ✅ Reduced redundant logs
- ✅ Full SIP message content in logs

### Previous Updates:
- ✅ TLS 1.2/1.3 support (Version 6 implementation)
- ✅ Improved socket handling
- ✅ Better timeout management
- ✅ Certificate auto-generation

---

## 🎉 What's Next?

See `FUTURE_ENHANCEMENTS_ROADMAP.md` for planned features:
- 📊 Call Flow Diagram Viewer
- 📈 Statistics Dashboard
- 🦈 Wireshark Integration
- 🎨 Modern Look & Feel
- ✏️ Enhanced Message Editor
- And much more!

---

## 💡 Tips & Tricks

### Tip 1: Use Both Logging Types
- **SIP Logging** for general message history
- **Debug Logging** for detailed troubleshooting

### Tip 2: Compare Sessions
```powershell
# Compare two TLS sessions
fc logs\TLS\TLS_20251109_120000.log logs\TLS\TLS_20251109_121500.log
```

### Tip 3: Search Across Logs
```powershell
# Find all REGISTER messages
Select-String "REGISTER sip:" logs\*\*.log
```

### Tip 4: Clean Old Logs
```powershell
# Delete logs older than 7 days
Get-ChildItem logs\*\*.log | Where-Object {$_.LastWriteTime -lt (Get-Date).AddDays(-7)} | Remove-Item
```

---

## ✅ Summary

**SIP Inspector now has:**
- ✅ Complete transport support (UDP/TCP/TLS)
- ✅ Flexible logging (GUI + command line)
- ✅ Organized log structure
- ✅ Dynamic enable/disable
- ✅ Rich scenario features
- ✅ Comprehensive documentation

**Ready for production use and future enhancements!** 🚀
