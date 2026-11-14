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
- **File:** `src/sipinspector/Utils/MyPacket.java` (Line 114, Line 137)
- **Code:**
  ```java
  // Line 114:
  if (headerEnd == -1) {
      complete = true; // Incorrectly marks packet as complete when CRLF CRLF is not yet received
  }
  // Line 137:
  byte[] truncated = Arrays.copyOf(data, expectedLength); // Drops trailing segment bytes
  ```
- **Root Cause:**
  1. In stream-oriented protocols (TCP/TLS), packets may arrive fragmented across multiple TCP segments. When an incomplete header arrives without `\r\n\r\n`, `headerEnd == -1`. Setting `complete = true` causes `SIPScenarioRunner` to attempt parsing a broken, half-formed SIP message.
  2. When multiple SIP messages are coalesced into a single TCP segment, `Arrays.copyOf(data, expectedLength)` consumes only the first message and discards all remaining bytes from the stream buffer, causing subsequent SIP transactions (such as `200 OK` followed immediately by `ACK` or `BYE`) to be lost.
- **Impact:** Scenarios using TCP or TLS transport intermittently fail, time out, or throw parse errors under heavy load or packet fragmentation.
- **Recommended Fix:**
  Introduce a stream accumulation buffer (`ByteArrayOutputStream` or sliding byte queue) in `TCPClientSocket` and `TLSClientSocket_V6`. Retain bytes until `headerEnd != -1` AND `availableBytes >= headerEnd + 4 + contentLength`. Do not discard leftover stream bytes.
- **Regression Risk:** Medium. Must ensure UDP transport (which is datagram-oriented and not streamed) continues to treat each datagram as an independent packet without buffering.

---

### ISSUE-003: Campaign Log Directory Disconnect in HTML Export
- **Severity:** **P1 (High)**
- **File:** `web-app/server.js` (Line 1029)
- **Code:**
  ```javascript
  const logDir = path.join(TRUNK_DIR, 'logs', transport.toUpperCase());
  ```
- **Root Cause:**
  `TLSDebugLogger.java` and `SIPScenarioRunner.java` write runtime SIP trace logs to:
  `<UserHome>/Desktop/SIP Simulator logs/<TRANSPORT>/`
  However, `web-app/server.js` in `/api/campaigns/export` expects logs to be located at `TRUNK_DIR/logs/<transport>` (which is empty in production).
- **Impact:** Exported campaign HTML reports and PDF summaries have blank or missing SIP sequence diagrams, even though the tests executed successfully and logs were written to the desktop folder.
- **Recommended Fix:**
  Standardize log directory resolution in `server.js` to look in the user desktop path or make the log path configurable via `config/settings.properties`, with automatic fallback if the local workspace `logs/` directory is empty.
- **Regression Risk:** Minimal. Affects only report generation file path resolution.

---

### ISSUE-004: Swing EDT Blocking During Socket Connection
- **Severity:** **P1 (High)**
- **File:** `src/sipinspector/SIPInspectorMainFrame.java` (Lines 1450–1490)
- **Root Cause:**
  When the user clicks "Run" or "Next >>", `openSocket()` and DNS resolution (`InetAddress.getByName(...)`) are called directly from the Swing Event Dispatch Thread (EDT).
  If the destination IP or domain is unreachable, or if a TLS handshake hangs, the Swing EDT freezes completely for 5 to 30 seconds. During this time, the desktop UI shows "(Not Responding)", mouse clicks buffer, and users suspect a crash.
- **Impact:** UI freezes, white screens, and unresponsiveness during network delays or unreachable target PBXs.
- **Recommended Fix:**
  Execute `openSocket()` and network initialization inside a `SwingWorker` background thread, showing an indeterminate progress bar or status text ("Connecting to PBX...") while keeping the Swing EDT responsive.
- **Regression Risk:** Low to Medium. Ensure state transitions (enabling/disabling Run button) are synchronized properly on the EDT.

---

### ISSUE-005: Interactive `JOptionPane` in Headless TLS Execution
- **Severity:** **P1 (High)**
- **File:** `src/sipinspector/Sockets/TLSClientSocket_V6.java` (Line 169, Line 175)
- **Code:**
  ```java
  int choice = JOptionPane.showConfirmDialog(null, 
      "The server certificate is self-signed or untrusted. Do you want to accept it?",
      "TLS Certificate Warning", JOptionPane.YES_NO_OPTION);
  ```
- **Root Cause:**
  When executing a test campaign triggered from the Web Dashboard or CLI, the Java process runs headlessly. When an untrusted PBX certificate is encountered, `JOptionPane.showConfirmDialog` blocks the thread waiting for graphical user input that never renders on screen. The campaign hangs indefinitely.
- **Impact:** Automated test campaigns stall permanently when encountering self-signed PBX certificates.
- **Recommended Fix:**
  Check `GraphicsEnvironment.isHeadless()` or inspect a configuration property `tls.trustAllCerts` or `tls.autoAcceptUntrusted`. If headless or configured to auto-accept, log a security warning and proceed without popping up modal dialogs.
- **Regression Risk:** Very low. Preserves desktop interactive behavior when running with GUI, while unblocking web/CLI execution.

---

### ISSUE-006: PKCS#1 RSA Private Key Parsing Limitation
- **Severity:** **P2 (Medium)**
- **File:** `src/sipinspector/SecurityManager.java` (Lines 200–240)
- **Root Cause:**
  The custom key parser uses standard Java `PKCS8EncodedKeySpec` with `KeyFactory.getInstance("RSA")`.
  If a user loads a legacy OpenSSL private key formatted as `-----BEGIN RSA PRIVATE KEY-----` (PKCS#1), standard Java throws `InvalidKeySpecException: Must use PKCS#8 format`.
- **Impact:** Users importing traditional OpenSSL RSA private keys encounter TLS initialization failure.
- **Recommended Fix:**
  Use the pre-bundled BouncyCastle library (`lib/bcpkix-jdk15on-170.jar`), which includes `PEMParser` and `JcaPEMKeyConverter` to seamlessly parse both PKCS#1 and PKCS#8 keys.
- **Regression Risk:** Very low. BouncyCastle is already on the classpath in `lib/`.

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

## 3. Suspected Fragile Areas

### FRAGILE-001: 32-bit vs 64-bit Architecture Mismatch with `jpcap.dll`
- **Location:** `lib/jpcap.jar`, native `jpcap.dll` in system root / deployment.
- **Description:** `jpcap.dll` is compiled for 32-bit Windows x86. Running the application on a standard 64-bit JDK without the bundled 32-bit JRE produces `UnsatisfiedLinkError: %1 is not a valid Win32 application`.
- **Mitigation:** The deployment scripts (`run.bat`, `deployment/jre`) currently point to the bundled 32-bit Temurin JRE. Ensure documentation clearly specifies that 32-bit JRE is mandatory for Jpcap capture features, or migrate packet inspection to pure Java PCAP parsing for offline files.

### FRAGILE-002: RTP Jitter Buffer Memory Bounds Under Extreme Delay
- **Location:** `src/sipinspector/Rtp/JitterBuffer.java`
- **Description:** The jitter buffer queue does not have a strict hard cap on packet retention if an endpoint sends high-bandwidth RTP but playback stalls.
- **Mitigation:** Introduce a maximum queue size (e.g. 50 packets / 1000ms equivalent) and drop oldest packets (tail-drop or head-drop) to prevent memory ballooning during prolonged network stalling.

### FRAGILE-003: Race Conditions on Rapid Abort in `SIPScenarioRunner_V2`
- **Location:** `src/sipinspector/SIPScenarioRunner_V2.java`
- **Description:** If a user clicks "Stop" while a scenario is in the middle of a `pause` step or socket read, the thread interruption may leave socket handles open for a few seconds until the underlying socket timeout fires.
- **Mitigation:** Explicitly invoke `socket.close()` within the cancel/stop handler rather than relying solely on `thread.interrupt()`.

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
