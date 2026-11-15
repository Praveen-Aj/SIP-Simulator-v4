# SIP Simulator v4 – Open Issues, Confirmed Defects & Technical Debt

**Version:** 4.0.0  
**Date:** November 14, 2026  
**Status:** Baseline Documented  
**Branch:** `stability/feature-validation`  

---

## 1. Overview & Classification

This document records all confirmed bugs, suspected fragile areas, telecom PBX-dependent behaviors, and architectural technical debt discovered during the technical audit of SIP Simulator v4.

Each issue is classified with:
- **Severity:** P0 (Critical / Crash / System Lockup), P1 (High / Functional Defect), P2 (Medium / Feature Incompleteness), P3 (Low / Code Quality / Minor Glitch).
- **Location:** Source file and exact line numbers.
- **Root Cause & Impact:** Technical explanation of what fails and why.
- **Recommended Targeted Fix:** Surgical remediation preserving existing functionality.
- **Regression Risk:** Potential side effects and verification requirements.

---

## 2. Confirmed Defects

### ISSUE-001: Force-Killing `java.exe` Freezes Windows Audio Service (System Lockup)
- **Severity:** **P0 (Critical)**
- **Status:** **RESOLVED in Phase 1A**
- **File:** `web-app/server.js` (Line 56, Line 442)
- **Root Cause:**
  When a user stops a test or starts an SSE log stream from the web dashboard, Express previously executed `taskkill /F /IM java.exe`. 
  `taskkill /F` issued a kernel-level `TerminateProcess()` call, immediately terminating the JVM without shutdown hooks or `finally` blocks, leaving native DirectSound endpoints (`TargetDataLine` / `SourceDataLine`) open. On Windows, this deadlocked `audiosrv` and required a PC reboot.
- **Phase 1A Resolution:**
  1. Completely removed `taskkill /F /IM java.exe`.
  2. Implemented strict child process tracking via `activeProcesses` `Set` and `trackChildProcess(proc)`.
  3. Replaced global process termination with targeted PID tree termination: `taskkill /T /F /PID ${proc.pid}`.
  4. Registered graceful shutdown listeners on `SIGINT`, `SIGTERM`, and `exit`.
  5. The parent Swing Desktop application (`SIP_Simulator_v4.jar`) and unrelated Java applications are now completely protected.

---

### ISSUE-002: TCP Stream Framing & Packet Truncation Defect
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 2**
- **File:** `src/sipinspector/Utils/MyPacket.java` (Line 114, Line 137), `src/sipinspector/Socket/TCPClientSocket.java`
- **Root Cause:**
  When a TCP read delivered a partial header, `MyPacket.parseBytesIntoSIPMsg` previously set `complete = true` prematurely before `\r\n\r\n` was received. Furthermore, when multiple messages arrived in a single socket read, trailing bytes were discarded.
- **Phase 2 Resolution:**
  1. Implemented dedicated `sipinspector.Utils.SipStreamFramer` to accumulate raw bytes, detect double-CRLF boundaries, and extract byte-accurate `Content-Length` payloads.
  2. Integrated `SipStreamFramer` into `TCPClientSocket.receive()` to queue multiple messages and maintain residual stream buffers across reads.
  3. Fixed `MyPacket.java` legacy parsing so `complete = false` is maintained on partial headers.
  4. Created `test.TcpSipFramingValidationTest` (11/11 tests passed).

---

### ISSUE-003: Campaign Log Directory Disconnect in HTML Export
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED**
- **File:** `web-app/server.js` (Lines 42–65, Lines 180–205, Lines 460–480, Lines 1130–1160)
- **Root Cause:**
  `TLSDebugLogger.java` and desktop scenario executions write runtime SIP trace logs to:
  `<UserHome>/Desktop/SIP Simulator logs/<TRANSPORT>/`
  However, `web-app/server.js` in `/api/campaigns/export` previously expected logs to be located only at `TRUNK_DIR/logs/<transport>` (which is empty in production).
- **Resolution:**
  1. Updated `web-app/server.js` to utilize `resolveLogsDir(transport)` and `findLogFilePath(filename)` across all supported transport directories (`UDP`, `TCP`, `TLS`, `SIP`).
  2. Aggregated candidate directories from standard user desktop, OneDrive desktop, and project root logs.
  3. Deduplicated log files by normalized canonical path. Tested with `node -c web-app/server.js`.

---

### ISSUE-004: Swing EDT Blocking During Socket Connection
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED**
- **File:** `src/sipinspector/SIPInspectorMainFrame.java`, `src/sipinspector/Socket/TCPClientSocket.java`
- **Root Cause:**
  When clicking "Next >>", `openSocket()` was called directly on the Event Dispatch Thread (EDT). Unreachable target IP addresses caused the OS TCP connect loop to hang for 20+ seconds, locking the UI.
- **Resolution:**
  1. Wrapped `openSocket()` invocation in `SIPInspectorMainFrame.java` within a `SwingWorker<Boolean, Void>`, keeping the Swing EDT responsive while updating cursor to `WAIT_CURSOR` and disabling button re-clicks.
  2. Added a 5000ms explicit connection timeout on non-blocking `finishConnect()` in `TCPClientSocket.java`, preventing indefinite hanging during silent packet drops.

---

### ISSUE-005: Interactive `JOptionPane` in Headless TLS Execution
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED**
- **File:** `src/sipinspector/Socket/TLSClientSocket_V6.java`, `src/sipinspector/Socket/TLSClientSocket.java`, `src/sipinspector/Socket/TCPClientSocket.java`
- **Root Cause:**
  In headless mode (CLI test legs and web dashboard executions), `JOptionPane.showMessageDialog` threw `HeadlessException` or blocked waiting for user interaction when socket errors occurred.
- **Resolution:**
  1. Implemented safe error reporting checking `GraphicsEnvironment.isHeadless()` and `Boolean.getBoolean("sip.headless")`.
  2. Headless execution outputs diagnostics to `TLSDebugLogger` and `System.err` without modal GUI interruptions.

---

### ISSUE-006: PKCS#1 RSA Private Key Parsing Limitation
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED**
- **File:** `src/sipinspector/Utils/SecurityManager.java`
- **Root Cause:**
  `SecurityManager.java` used standard Java `PKCS8EncodedKeySpec`, which fails with `InvalidKeySpecException` when loading OpenSSL `-----BEGIN RSA PRIVATE KEY-----` (PKCS#1) keys.
- **Resolution:**
  1. Implemented pure Java 8 ASN.1 DER parser `parsePkcs1PrivateKey` extracting the 9 RSA parameters (`modulus`, `publicExponent`, `privateExponent`, `primeP`, `primeQ`, `primeExponentP`, `primeExponentQ`, `crtCoefficient`) into `RSAPrivateCrtKeySpec`.
  2. Transparently supports both PKCS#1 and PKCS#8 formats with zero external dependencies.
  3. Created `test.SecurityManagerKeyParsingTest` (2/2 tests passed, verifying SHA256withRSA signature generation and verification).

---

### ISSUE-007: Window Sizing & Display Scaling Cutoffs on High-DPI & Ultrawide Displays
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED in Phase 1A**
- **File:** `src/sipinspector/SIPInspectorMainFrame.java`, `src/sipinspector/GlobalSettings.java`
- **Root Cause:**
  1. Window size was fixed at `1200x750` without checking screen working area or insets, causing bottom action buttons and status bar to be pushed off-screen on 1366x768 and 1080p @ 150% scaling.
  2. Ultrawide displays locked the message tree to a narrow 200px strip via `width 200:200:`.
  3. Rigid pixel constraints (`w 50!`, `w 100!`, `w 40!`) caused text clipping on scaled displays.
- **Phase 1A Resolution:**
  1. Implemented dynamic screen-aware bounds via `GraphicsEnvironment.getMaximumWindowBounds()`, reserving clearance for the Windows taskbar.
  2. Bounded minimum dimensions dynamically to never exceed available screen working area.
  3. Relaxed MigLayout constraints on `MsgScrollPane` (`"wmin 220, pref 260, hmin 120"`), allowing proportional expansion across ultrawide monitors.
  4. Relaxed IP and port constraints (`"grow, hmin 80, wmin 130"` and `"wmin 65, pref 80, growx"`), eliminating high-DPI digit clipping.
  5. Implemented window bounds and maximized state persistence in `config/settings.properties`. Verified across 6 resolution profiles (1366x768 to 4K 200%).

---

### ISSUE-008: Missing Formal Call State Machine & Transition Rules
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED in Phase 3**
- **File:** `src/sipinspector/Media/CallState.java`
- **Root Cause:**
  Call sessions previously tracked states primarily via dialog count integers and ad-hoc string comparisons in `SIPStateTracker`, lacking explicit compile-time state definitions and transition enforcement per RFC 3261.
- **Phase 3 Resolution:**
  1. Created explicit `CallState` enum (`IDLE`, `CALLING`, `PROCEEDING`, `RINGING`, `EARLY_MEDIA`, `ESTABLISHED`, `TERMINATING`, `TERMINATED`, `FAILED`).
  2. Implemented RFC 3261 state transition validation via `canTransitionTo(CallState)` and `isValidTransition(from, to)`.
  3. Added `isActive()` and `isTerminal()` state queries.
  4. Verified in automated test suite `test.CallFlowValidationTest`.

---

### ISSUE-009: Missing RTCP Quality Monitoring & RFC 3550 Telemetry
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED in Phase 3**
- **File:** `src/sipinspector/Media/RtcpMonitor.java`, `src/sipinspector/Media/LiveAudioController.java`, `src/sipinspector/Media/LiveAudioReceiverThread.java`
- **Root Cause:**
  While SDP advertised `a=rtcp:[media_port+1]`, no active RTCP background packet generator or receiver existed to capture network jitter, fraction lost, cumulative packet loss, or round-trip time.
- **Phase 3 Resolution:**
  1. Implemented `RtcpMonitor` running on `localMediaPort + 1` (UDP).
  2. Implemented RFC 3550 Receiver Report (RR, PT=201) generation and Sender Report (SR, PT=200) parsing.
  3. Implemented RFC 3550 Appendix A.8 interarrival jitter calculations and packet loss tracking.
  4. Integrated telemetry seamlessly into `LiveAudioController` and `LiveAudioReceiverThread` without breaking changes.
  5. Verified in automated test suite `test.CallFlowValidationTest`.

---

### ISSUE-010: Two-Endpoint Live Call Integration & Bidirectional RTP Audio Validation
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 3 (OXE Integration)**
- **File:** `test/OxeLiveEndpointTest.java`, `src/sipinspector/Media/LiveAudioCodec.java`, `src/sipinspector/Media/CallState.java`
- **Root Cause:**
  Prior live testing against OXE lacked a second registered subscriber endpoint to answer calls and generate audio, resulting in call forwarding failure due to unprovisioned test extensions.
- **Resolution:**
  1. Discovered and integrated provisioned subscriber extensions: Endpoint A (Simulator `90008`) and Endpoint B (MicroSIP 3.22.16 `90001`).
  2. Developed automated two-endpoint test harness `test.OxeLiveEndpointTest` controlling MicroSIP via Windows Named Pipe JSON-RPC interface (`\\.\pipe\microsip_ipc`).
  3. Executed Test A (Outbound Call: Simulator `90008` -> MicroSIP `90001`): `INVITE` -> `100 Trying` -> `180 Ringing` -> `200 OK` -> `ACK` -> Bidirectional RTP (144 packets TX, 201 packets RX) -> `BYE` -> `200 OK`.
  4. Executed Test B (Inbound Call: MicroSIP `90001` -> Simulator `90008`): `INVITE` -> `180 Ringing` -> `200 OK` -> `ACK` -> Bidirectional RTP (147 packets TX, 201 packets RX) -> `BYE` -> `200 OK`.
  5. Verified Test C (Bidirectional RTP PCMA PT=8, 20ms frames) and Test D (Graceful teardown via `BYE` -> `200 OK`) in both directions.
  6. Preserved MicroSIP `90001` registration and configuration intact throughout execution.

---

### ISSUE-011: Production Call Engine, Dual WAV Audio Recording & REST Management
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 4**
- **File:** `src/sipinspector/Call/SipCallEngine.java`, `src/sipinspector/Call/CliCallEngine.java`, `src/sipinspector/Media/RtpWavRecorder.java`, `web-app/server.js`, `test/OxeProductionCallEngineTest.java`
- **Root Cause:**
  Prior architecture relied on test scaffolding scripts (`CliSipLegTest`, `OxeLiveEndpointTest`) to establish calls, lacking a centralized production-grade call orchestrator, multi-codec audio capture, dual WAV recording, mid-dialog hold/resume, and interactive REST management endpoints.
- **Phase 4 Resolution:**
  1. Implemented centralized `SipCallEngine.java` managing concurrent dialogs, RFC 3261 state machine, automatic incoming call dispatching, media attachment, and dual WAV recording.
  2. Implemented multi-codec PCM recording in `RtpWavRecorder.java` (PCMA PT=8, PCMU PT=0, G.722 PT=9) with RMS power calculation and peak amplitude analysis.
  3. Added mid-dialog session renegotiation for Call Hold (`a=sendonly`) and Call Resume (`a=sendrecv`) via Re-INVITE sequences with OXE.
  4. Added graceful fallback to tone synthesis when physical microphones are unavailable, and comfort silence generation when muted.
  5. Implemented `CliCallEngine.java` with lightweight embedded HTTP management daemon (`daemon 5065`).
  6. Added Express REST endpoints in `web-app/server.js` (`/api/call/*`, `/api/audio/devices`).
  7. Validated 100% passing across all 8 live test cases against OXE PBX and MicroSIP in `test.OxeProductionCallEngineTest`.

---

### ISSUE-008: Dynamic Local IP Binding on DHCP Host Environments
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 5**
- **File:** `src/sipinspector/Call/SipCallEngine.java`
- **Root Cause:**
  When development/testing machines switch networks or receive new DHCP addresses, hardcoded IP configurations throw `java.net.BindException: Cannot assign requested address`.
- **Resolution:**
  Implemented `SipCallEngine.resolveLocalIpTowards(remoteIp)`, which queries the Windows/OS routing table using a lightweight connected UDP datagram socket to dynamically detect the exact local interface towards the target PBX (`192.168.0.20`), eliminating manual configuration.

---

### ISSUE-009: Multi-Call Dialog Concurrency & Media Port Collision
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 5**
- **File:** `src/sipinspector/Call/SipCallEngine.java`, `src/sipinspector/Call/PerformanceTestEngine.java`
- **Root Cause:**
  Rapid multi-call recycling or simultaneous call initiation risked Call-ID collisions and port collisions if an even RTP port was still in TIME_WAIT or held by an asynchronous OS thread.
- **Resolution:**
  1. Guaranteed globally unique, collision-free Call-IDs: `call<AtomicLong>_<timestamp>@<localIp>`.
  2. Implemented test-binding port allocator (`allocateRtpPort()`) testing `new DatagramSocket(port)` across the 42000-50000 range.
  3. Validated with `test.MultiCallOrchestrationTest` (8 simultaneous multithreaded calls, zero collisions).

---

### ISSUE-010: RFC 3261 Timer A/B Retransmissions & CANCEL Support
- **Severity:** **P1 (High)**
- **Status:** **RESOLVED in Phase 5**
- **File:** `src/sipinspector/Call/SipCallEngine.java`
- **Root Cause:**
  Outbound INVITEs previously lacked RFC 3261 Timer A exponential backoff retransmissions (500ms -> 1s -> 2s -> 4s) and Timer B transaction timeout (16s), causing indefinite hangs if an intermediate router silently dropped packets. CANCEL was not fully integrated into active session dialogs.
- **Resolution:**
  1. Integrated `checkPendingTransactions()` into non-blocking `listenLoop()`, servicing Timer A and Timer B timeouts every 100ms.
  2. Implemented `cancelCall(callId)` sending RFC 3261 CANCEL with matching branch/CSeq/tags and handling 487 Request Terminated with ACK.
  3. Added duplicate 200 OK retransmission ACK and duplicate BYE 200 OK handling.
  4. Validated with `test.SipTransactionReliabilityTest` (3/3 passed).

---

### ISSUE-011: Real-Time RTP Quality Analysis & ITU-T E-Model G.107 MOS Calculation
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED in Phase 5 & 6**
- **File:** `src/sipinspector/Media/LiveAudioReceiverThread.java`, `src/sipinspector/Media/LiveAudioController.java`
- **Root Cause:**
  RTP receiver metrics previously lacked sequence discontinuity tracking, duplicate packet counts, out-of-order packet tracking, and standardized Mean Opinion Score (MOS) computation.
- **Resolution:**
  1. Implemented RFC 3550 sequence difference arithmetic in `LiveAudioReceiverThread.validateRxRtp`.
  2. Implemented ITU-T E-model G.107 transmission rating factor $R$ calculation with Appendix I PLC parameterization ($B_{pl} = 19.0$) and explicit one-way delay separation ($d = 10\text{ms} + 20\text{ms} + 2 \times \text{jitter}$).
  3. Overloaded `calculateMos` supporting explicit one-way delay injection and validated boundary clamping ($R \le 0 \implies \text{MOS} = 1.0$, $R \ge 100 \implies \text{MOS} = 4.5$).
  4. Validated with `test.PerformanceCampaignEngineTest` across ideal, moderate, severe, and extreme network degradation profiles.

---

### ISSUE-012: PBX Single-Line Outgoing Policy Enforcement on Live OXE Extension
- **Severity:** **P2 (Telecom Specific / Informational)**
- **Status:** **COMPREHENSIVELY QUALIFIED & VERIFIED in Phase 7**
- **File:** `test/OxePhase7FeatureValidationTest.java`, `test/OxeLiveConcurrentCallTest.java`
- **Root Cause & Diagnosis:**
  When initiating a second simultaneous outbound call from extension 90008 to extension 90001 while Call 1 is established (or even when Call 1 is placed on HOLD with `a=sendonly`), Alcatel-Lucent OmniPCX Enterprise (OXE R101.1 n4.524.2) returns `SIP/2.0 488 Not Acceptable Here` with `Reason: SIP;cause=488;text="Outgoing call isn't allowed here"`.
  Investigation confirmed this is an architectural PBX subscriber profile policy in OXE: extension 90008 is configured as a single-line terminal (`Multi-line = False` / `Number of keys = 1`), and extension 90001 has no secondary line appearance. OXE rejects simultaneous outgoing line seizure at the call admission control level.
- **Resolution & Qualification:**
  1. Verified dialog isolation in `SipCallEngine`: Call 1 maintained established dialog, active audio, and zero jitter interruption during Call 2 rejection.
  2. Tested Re-INVITE Hold (`a=sendonly`) followed by Call 2: confirmed that OXE still rejects Call 2 with 488, proving the constraint is subscriber profile line seizure, not media port conflict.
  3. Validated clean teardown and post-test zero resource leakage in both `test.OxePhase7FeatureValidationTest` and `test.OxeLiveConcurrentCallTest`.

---

### ISSUE-013: Map Multi-Key Cleanup in Active Session Registry
- **Severity:** **P2 (Medium)**
- **Status:** **RESOLVED in Phase 6**
- **File:** `src/sipinspector/Call/SipCallEngine.java`
- **Root Cause:**
  `activeSessions` stores entries by both full Call-ID (`call<N>_<ts>@<ip>`) and prefix (`call<N>_<ts>`). When teardown occurred through asynchronous responses or CANCEL, deleting one key without the other left ghost sessions in `activeSessions`, skewing `getActiveCalls()` count.
- **Resolution:**
  1. Centralized cleanup into `cleanupSessionMaps(session, callId)`, removing normalized prefix, full ID, and original call ID atomically.
  2. Updated `getActiveCalls()` to deduplicate sessions using `LinkedHashSet<CallSession>`.
  3. Verified in `test.ActiveCampaignFailureRecoveryTest` and `test.OfflineFullRegressionSuite` (zero lingering calls).

---

### ISSUE-014: Mid-Dialog Request Failure Prematurely Terminated Established Dialogs
- **Severity:** **P1 (High / Protocol Compliance)**
- **Status:** **RESOLVED in Phase 7**
- **File:** `src/sipinspector/Call/SipCallEngine.java` (Line 1379–1410)
- **Root Cause:**
  When `handleIncomingResponse` received a 4xx, 5xx, or 6xx failure response, it unconditionally called `session.setState(CallState.FAILED)`, stopped media, and cleaned up session maps. When testing in-call SIP INFO DTMF on OXE (which returns `488 Not Acceptable Here` because OXE does not support INFO for DTMF), this bug incorrectly aborted the active call.
- **RFC Compliance Fix:**
  Per RFC 3261 Section 14.1 and RFC 6086 Section 4.2.2, rejection of a mid-dialog request (e.g. INFO, REFER, or Re-INVITE) fails only that sub-transaction; the established dialog MUST remain active. Updated `handleIncomingResponse` so that if `session.state == CallState.ESTABLISHED`, mid-dialog 4xx/5xx/6xx responses are logged/acknowledged without changing session state or terminating audio media.

---

### ISSUE-015: OXE SIP Registration Minimum Expires Policy
- **Severity:** **P2 (Telecom Specific / Interoperability)**
- **Status:** **RESOLVED in Phase 7**
- **File:** `src/sipinspector/Call/SipCallEngine.java`, `test/OxePhase7FeatureValidationTest.java`
- **Root Cause:**
  When registering with `Expires: 600`, OXE PBX rejects the registration with `SIP/2.0 423 Registration Too Brief` and header `Min-Expires: 1800`.
- **Resolution:**
  Updated default registration expiration parameter to 1800 seconds across `SipCallEngine.register()`, CLI engine, and all live test suites, achieving 100% reliable 200 OK registrations.

---

## 3. Suspected Fragile Areas

### FRAGILE-001: 32-bit vs 64-bit Architecture Mismatch with `jpcap.dll`
- **Location:** `lib/jpcap.jar`, native `jpcap.dll` in system root / deployment.
- **Status:** **MITIGATED & DOCUMENTED**
- **Description:** `jpcap.dll` is compiled for 32-bit Windows x86. Running the application on a standard 64-bit JDK without the bundled 32-bit JRE produces `UnsatisfiedLinkError: %1 is not a valid Win32 application`.
- **Mitigation:** The deployment scripts (`run.bat`, `deployment/jre`) point to the bundled 32-bit Temurin JRE. For packet playback, `PurePcapRtpPlayer` provides 100% pure Java PCAP parsing independent of native DLLs.

### FRAGILE-002: RTP Jitter Buffer Memory Bounds Under Extreme Delay
- **Location:** `src/sipinspector/Media/LiveAudioReceiverThread.java`
- **Status:** **VERIFIED & SECURED**
- **Description:** The jitter buffer queue requires a strict hard cap on packet retention if an endpoint sends high-bandwidth RTP but playback stalls.
- **Resolution:** Verified that `LiveAudioReceiverThread.java` enforces `JITTER_BUFFER_SIZE = 10` (~200ms) with sequence-ordered oldest-packet drop via `jitterBuffer.poll()` and increments `packetsDropped`.

### FRAGILE-003: Race Conditions on Rapid Abort in Scenario Threads
- **Location:** `src/sipinspector/Threads/` (`CallGeneratorThread`, `CallMaintainerThread`, `PauseMaintainerThread`, `ReceiverThread`)
- **Status:** **RESOLVED**
- **Description:** Rapidly clicking "Stop" while threads were sleeping in `Thread.sleep()` or waiting on I/O could cause delays in shutting down runner loops.
- **Resolution:** Added explicit `interrupt()` calls within `stopThread()` across `CallGeneratorThread.java`, `CallMaintainerThread.java`, `PauseMaintainerThread.java`, and `ReceiverThread.java` to instantly break sleep/wait states.

---

## 4. PBX-Dependent Features (Alcatel-Lucent OmniPCX Enterprise)

The following telecom features require a physical or virtual Alcatel-Lucent OXE PBX for end-to-end operational verification:

| Telecom Feature | SIP Headers Involved | OXE Behavior / Requirement | Offline Simulation Status |
|---|---|---|---|
| **User-to-User Info (UUI)** | `User-to-User: <hex>;encoding=hex` | Used by OXE CCD (Call Center Distribution) for agent screen pop and routing data. | Verified syntax and token substitution; requires live OXE CCD to verify IVR data transfer. |
| **Call Diversion / Forwarding** | `Diversion: <sip:...>;reason=unconditional` | OXE tracks forwarding chains and displays original calling party. | Verified header injection; requires OXE multi-line forward test. |
| **P-Asserted-Identity (PAI)** | `P-Asserted-Identity: "Name" <sip:...>` | Used on OXE SIP trunks for CLI presentation and billing identity. | Verified regex and assertion engine; verified working. |
| **SIP History-Info** | `History-Info: <sip:...;index=1>` | RFC 4244 / RFC 7044 routing history representation. | Header generator implemented; requires OXE trunk interworking validation. |
| **Blind & Attended Transfer** | `REFER`, `Replaces`, `NOTIFY` | OXE SIP extension transfer handling. | Scenario templates present; requires live OXE call-state verification. |

---

## 5. Technical Debt & Architectural Assessment

1. **Dual Execution Engines:**
   - Both `SIPScenarioRunner.java` and `SIPScenarioRunner_V2.java` coexist in the codebase. `V2` adds assertions and better timing, but legacy code paths in certain dialogs still invoke `SIPScenarioRunner`.
   - *Target:* Gradually standardize all executions on `SIPScenarioRunner_V2` while preserving backward compatibility.

2. **Monolithic Swing MainFrame:**
   - `SIPInspectorMainFrame.java` exceeds 2,000 lines, intermingling UI layout, event handling, socket creation, file I/O, and process management.
   - *Target:* Decouple into distinct Controller and Service layers without altering existing UI behavior.

3. **Inconsistent Logging Paths:**
   - Traces were previously scattered between `<Desktop>/SIP Simulator logs/`, workspace `logs/`, and temporary system directories.
   - *Status (Phase 1A/1B):* **RESOLVED** via centralized `sipinspector.Utils.AppPaths.getLogsDir()` and `AppPaths.resolve()`, prioritizing desktop logs while providing automatic fallback to project-relative `logs/` for headless/server environments.
