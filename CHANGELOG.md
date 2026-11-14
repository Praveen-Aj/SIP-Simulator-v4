# Changelog - SIP Simulator v4

All notable changes for the SIP Simulator project (originally SIP Inspector) are documented here.

---

## [4.0] - June 2026

### Major Features

#### React Web Dashboard Integration
- **Vite + React Dashboard**: Designed and deployed a responsive dark-themed dashboard frontend.
- **Node.js Express Backend (`server.js`)**: Runs a background helper process on port `3001` managing file systems, process runs, and simulation settings.
- **Dynamic Search Select**: Custom search filters for scenarios and values files in the dashboard dropdown menu.
- **SIP CRLF Line Ending Enforcer**: Automates `\r\n` CRLF formatting checks on web edits to avoid PBX packet dropping issues.

#### Interactive Visual Sequence Diagram
- **Desktop & Web Timelines**: Displays a sequence ladder drawing vertical timelines for local/remote bindings.
- **Color-Coded signaling arrows**: Visualizes inbound transactions in green and outbound in cyan, highlighting the active message in pink.
- **Message detail inspector**: Interactive click triggers on diagram arrows display complete headers/bodies instantly.
- **Ladder timeline normalization**: Anchors the local binding (`SIP Simulator(LOCAL)`) leftmost to keep rendering stable.

#### Telecom Integration & Value Customization
- **SIP Presets Injector Panel**: Fast header injection triggers for telecom SBC/PBX debugging, including Alcatel-Lucent OXE User-to-User Information (UUI) context, Call Diversion (history redirection), calling line override (P-Asserted-Identity), Privacy headers, and custom X-Headers.
- **Value preservation overrides**: Added tracking variable (`hasBeenEdited`) to skip default account settings fallback when a user manually modifies individual value variables.
- **Unexpected NOTIFY Auto-Response**: Automatically responds `200 OK` to incoming unexpected `NOTIFY` requests from call servers (such as OXE) even if not present in scenario scripts.

#### Real-time Emulation & State Observability
- **Unified Network Emulation**: Real-time packet delay (ms) and packet drop (%) sliders.
- **SIP Anomaly Injection**: Selectors to corruptExpires, tampered Branch/Call-ID, and corrupted CSeq headers on outgoing streams.
- **State Machine Monitor**: Dynamic active call/dialog count tracking and REGISTER expires countdown timers.
- **Unified RTP Exporter**: Exports live PCAP stream or microphone audio packet metrics to `config/rtp_diagnostics.json` every 500ms.
- **Modeless Dialog Execution**: Replaced modal progress dialogues with modeless forms to permit dashboard exploration during active call loops, adding window listeners to automatically shut down active threads.
- **logs directory portability**: Replaced hardcoded logs folder directory string (`C:\Users\<username>\...`) with Node.js native `os.homedir()` calls to ensure out-of-the-box logs viewing on any PC.

---

## [3.0] - March 2026

### Major Features
- **TCP and TLS Socket Support**: Added fully native TCP and TLS socket connection engines.
- **Live Microphone Audio Stream**: Added real-time voice stream recording and playback support.
- **Launch4j executable generation**: Structured `.exe` packaging configurations around compile outputs.
- **Log Retention Policy**: Added automated background cleaning threads to rotate and purge old trace files.

---

## [2.0] - November 2025

### Major Changes

#### UI/UX Improvements
- **New Application Icon**: Replaced SIP Inspector icon with custom SIP Simulator icon (cyan/turquoise cube design)
- **Enhanced Status Bar**: Added real-time status indicators for Scenario, Values, and Logs
- **Statistics Dashboard**: New statistics panel with message counts, success rates, and session duration
- **Tabbed Interface**: Reorganized tabs above messages/output window for better workflow
- **Logs Tab**: Quick access to UDP, TCP, and TLS debug logs

#### Scenario & Values Management
- **Combined Scenario & Values Tab**: Unified interface for loading scenarios and values
- **Auto-Load Feature**: Reg_client scenario and values load automatically on startup
- **Edit Values Tab**: Session-only value editing without modifying files
- **Auto-Matching Values**: Values file automatically selected when scenario is chosen
- **Missing Values Files**: Created 7 new values files for previously unsupported scenarios:
  - Reg_client_values.txt
  - Reg_client_series_values.txt
  - Reg_server_values.txt
  - PRACK_client_example_values.txt
  - PRACK_server_example_values.txt
  - SUBSCRIBE_dialog_client_values.txt
  - SUBSCRIBE_dialog_server_values.txt

#### File Management
- **File Protection**: Protect scenario and values folders from accidental overwrites
- **Backup System**: Automatic backup of scenarios and values folders
- **Restore Functionality**: Restore from previous backups in Settings
- **Persistent Settings**: Protection settings saved across sessions

#### Logging & Debugging
- **Logs Toggle**: Enable/disable log monitoring from Logs tab
- **Status Sync**: Logs status correctly syncs on startup
- **Debug Logging**: Enhanced debug logging for Edit Values functionality
- **Log Retention**: Configurable log retention and maximum log files

#### Navigation
- **Back Button in Statistics**: Added back button to return from Statistics panel to main screen
- **Improved Navigation**: Fixed CardLayout navigation for seamless panel switching

### Bug Fixes
- **Logs Status Initialization**: Fixed logs status not syncing on startup
- **Edit Values Reflection**: Added debug logging to track value changes (pending full integration)
- **Statistics Navigation**: Fixed inability to return from Statistics panel
- **Default Scenario Loading**: Reg_client now loads automatically on startup

---

## [1.0] - Original SIP Inspector

Original SIP Inspector by Zarko Coklin with:
- Basic SIP scenario execution
- UDP transport support
- Simple value substitution
- Basic logging

---

## Migration Guide (v1 → v4)

### What's New
- All v1 scenarios are compatible with v4
- Dual-GUI design: Native Desktop Java Swing and dynamic React Web Dashboard
- Dynamic network latency, loss, and protocol anomaly injection controls
- Session-only value customizer and one-click telecom presets injector
- Auto-load feature and visual call sequences trace engine

### What Changed
- Application name: "SIP Inspector" → "SIP Simulator"
- Logs location: Dynamically organized under the current user's desktop `SIP Simulator logs` directory
- Default scenario: Now loads Reg_client on startup

### What's Compatible
- All existing scenario files (.txt)
- All existing values files (.txt)
- All existing settings and configurations

### Recommended Actions
1. Update any scripts referencing "SIP Inspector" to use "SIP Simulator"
2. Check network latency/drop sliders in the simulation panel to debug client retransmission times
3. Launch the Web Dashboard from the tools menu to browse trace diagnostics visually

---

## Version Numbering
- **v4.0**: Active production build (Web Dashboard & Simulation integration)
- **v3.0**: JRE executable bundle with TCP/TLS support
- **v2.0**: Enhanced desktop tabs GUI
- **v1.0**: Baseline code repository

---

**Last Updated**: June 2026
