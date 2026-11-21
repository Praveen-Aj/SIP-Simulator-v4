# Changelog - SIP Simulator v4

All notable changes for the SIP Simulator project (originally SIP Inspector) are documented here.

---

## [8.3] - November 2026

### Independent QA Revalidation & Product Acceptance
- **Defect Remediation Verification:** Independently verified resolution of all 9 defects (DEF-01 through DEF-09) against commit `9c3535d` (`docs/PHASE_8_3_INDEPENDENT_QA_REPORT.md`).
- **Regression Suite Execution:** Re-verified all 16 master offline test suites (100% clean PASS in 12.68s) targeting Java 8.
- **Web Frontend & Backend Qualification:** Validated React 19 + Vite 8 production build (493ms) and Node.js Express server syntax (`node --check server.js`).
- **Live OXE Signaling & Response Formatting:** Confirmed live UDP/TCP/TLS OPTIONS and REGISTER 200 OK exchange with Alcatel-Lucent OmniPCX Enterprise PBX (`192.168.0.20:5060`). Confirmed clean single Via response formatting on unsolicited NOTIFY.
- **Architectural & Environmental Baseline:** Formally documented architectural limitations (LIM-01 through LIM-06) and environmental separation of the MicroSIP named pipe IPC dependency.

---

## [8.2] - November 2026

### QA Defect Remediation & Engineering Consistency
- **DEF-01 Fixed (RTCP RTT Telemetry):** In `RtpInspectorTab.java`, eliminated the hardcoded `"< 15 ms (Direct LAN)"` string literal. Bound the RTT metric directly to `stats.get("rtcp_rtt_ms")` reported by `LiveAudioController.getStats()`. When RTCP reports have not arrived, gracefully displays `"RTT unavailable — no valid RTCP measurement"`.
- **DEF-02 Fixed (Call-ID Drilldown Navigation):** In `RtpInspectorTab.java` and `SipMessageExplorerTab.java`, wired the "Inspect in SIP Message Explorer" button to pre-populate the Call-ID filter in `SipMessageExplorerTab.setCallIdFilter(callId)`, automatically filter the message table, and select the corresponding Call-ID row.
- **DEF-03 Fixed (Live Impairment Packet Counters):** In `SocketSimulationHelper.java` and `SimulationTab.java`, implemented atomic packet counters (`sipPacketsProcessed`, `sipPacketsDropped`, `rtpPacketsProcessed`, `rtpPacketsDropped`). Wired the UI refresh timer to update `txRxPacketsLabel` in real time with actual processed and dropped packet metrics.
- **DEF-04 Fixed (SIP 200 OK Response Formatting):** In `SipCallEngine.java`, resolved the `Via: Via:` duplicate header defect on `200 OK` responses to incoming NOTIFY, OPTIONS, BYE, and CANCEL requests. Implemented RFC 3261 Section 8.2.6 compliant `format200OkResponse()` and `getViaHeadersForResponse()` to preserve multi-Via headers and maintain exact CRLF boundaries.
- **DEF-05 Fixed (Composite AND Filtering in Message Explorer):** In `SipMessageRepository.java` and `SipMessageExplorerTab.java`, replaced the mutually exclusive Method/Response-code branching with composite AND filtering (`filter(method, responseCode, callId, direction, transport, searchKeyword)`). Both Method and Response Code filters can now be applied concurrently.
- **DEF-06 Fixed (Call-ID Regex Extraction):** In `CampaignsTab.java`, replaced fragile substring matching (`line.indexOf("call")` which erroneously matched "caller" and "calling") with robust regex pattern `(?i)(?:Call-ID|i)[=:]\s*([^\r\n\s]+)`.
- **DEF-07 Fixed (Message Diff Dialog Integration):** Integrated `MessageDiffDialog.java` into the application. Wired into `Tools -> SIP Utilities -> Compare SIP Messages...` and added a `Compare...` button on the `SipMessageExplorerTab` toolbar.
- **DEF-08 Fixed (Structured Settings Dialog):** Implemented `src/sipinspector/Dialogs/StructuredSettingsDialog.java`, providing a full 5-tab modal preferences dialog (Network & Sockets, SIP Signaling & Identity, Media & Audio Codecs, Diagnostics & Logging, Appearance & Theme) with field validation, live reset, and persistent saving via `GlobalSettings`. Wired to `Options -> Preferences / Settings...`.
- **DEF-09 Fixed (Genuine Test Assertions):** Rewrote `test/Phase8WorkspaceIntelligenceTest.java` with 6 genuine behavior-level test cases, verifying composite filtering, live counters, failure triage classification logic (`CampaignsTab.classifyFailureEvidence()`), 200 OK response formatting (zero duplicate Via), RTCP RTT telemetry, and settings validation.

---

## [8.1] - November 2026

### Independent QA Audit & Acceptance
- Executed comprehensive independent quality assurance audit (`docs/PHASE_8_1_INDEPENDENT_QA_REPORT.md`) against target commit `3629ccf`.
- Validated live physical registration and bidirectional audio call against Alcatel-Lucent OmniPCX Enterprise (OXE R101.1) and MicroSIP.
- Discovered 9 specific defects (DEF-01 through DEF-09) spanning protocol formatting, UI binding, and test assertion fidelity.
- Issued conditional acceptance pending Phase 8.2 remediation.

---

## [8.0] - November 2026

### Engineering Workspace Consolidation & Diagnostic Intelligence
- **Campaigns Workspace Redesign:** Implemented real-time KPI summary cards, structured test case matrix, and **Evidence-Based Failure Analysis Triage Panel** with automated classification (`PBX_REJECTION`, `PROTOCOL_ERROR`, `MEDIA_FAILURE`, `TIMEOUT`, `CONFIG_ERROR`, `NETWORK_UNREACHABLE`), certainty rating, and concrete investigation steps.
- **Unified Network & Protocol Testing Workspace:** Transformed `SimulationTab.java` into a 4-panel test harness featuring Network Impairment (delay, loss, jitter affecting signaling and media), SIP Fault Injection, SIP State Observability Monitor, and Comparative Simulation workflow with CSV export.
- **RTP Inspector Workspace:** Upgraded media telemetry with active session selector, measured TX/RX packet metrics, RFC 3550 sequence discontinuity detection, duplicate packet accounting, out-of-order tracking, RTCP jitter/loss, and 60-sample real-time sliding sparklines.
- **SIP Message Explorer:** Implemented central in-memory ring repository (`SipMessageRepository`) capturing all transport datagrams without duplication, multi-criteria filtering, structured headers/SDP/raw tabs, and embedded real-time `SipLadderPanel` visual call flow ladder.
- **Desktop vs. Web Responsibility Model:** Formally established operational separation: Desktop Swing UI for authoring, manual calling, deep diagnostics; React Web UI for operational monitoring and campaign execution.

---

## [7.1] - November 2026

### Independent Source Audit & Portability Remediation
- Dynamic DTMF negotiation supporting RFC 4733 dynamic payload types (PT 101, PT 120 on OXE).
- Early media qualification requiring active unicast RTP port > 0 in 183 Session Progress.
- Independent local (`a=sendonly`) and remote (`a=recvonly`) hold/resume state tracking.
- Network interface decoupling: replaced hardcoded IP literals with dynamic local IP discovery.
- Expanded master offline regression test suite to 15 suites.

---

## [7.0] - November 2026

### End-to-End Productization & Operational Validation
- Validated live against physical Alcatel-Lucent OmniPCX Enterprise (OXE R101.1 n4.524.2) and MicroSIP 3.22.16.
- Established live bidirectional audio (G.711 A-law PCMA PT=8) with ITU-T G.107 MOS score 4.39.
- Verified in-call RFC 2833 DTMF and mid-dialog Re-INVITE Hold/Resume.
- Validated PBX single-line admission control policy (488 Not Acceptable Here) with clean dialog isolation.
- Published clean packaging and deployment artifacts in `Github/`.

---

## [6.0] - November 2026

### Live Performance Qualification & Operational Hardening
- Validated multi-call performance campaigns with CPS pacing and setup latency percentiles (p50, p95, p99).
- Hardened failure recovery against 408 Request Timeout (Timer B), 487 Request Terminated (CANCEL), and 404 Not Found.
- Hardened audio pipeline against DirectSound resource exhaustion.

---

## [5.0] - November 2026

### Multi-Call Orchestration & Telecom Compliance
- Multi-call orchestration engine supporting sequential, concurrent, and soak campaign executions.
- Mid-dialog RFC compliance (RFC 3261 Section 14.1 and RFC 6086 Section 4.2.2).
- ITU-T E-Model G.107 Mean Opinion Score (MOS) calculation.
- Automated HTML and JSON performance report generator.

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
