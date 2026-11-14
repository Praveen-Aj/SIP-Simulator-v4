# SIP Simulator v4 - Implementation & Changes Documentation

This document logs all changes, enhancements, and bug fixes applied to the SIP Simulator repository to transition the codebase into **v4 (Web Dashboard Enabled)**, with specific support for telecom PBX environments (such as Alcatel-Lucent OmniPCX Enterprise - OXE).

---

## 1. Java Core & Launcher Fixes

### A. Fixed Launcher JAR Mismatch (`run.bat`)
* **Problem**: The web option was not appearing in the Java Swing application because the startup batch files were executing `SIP_Simulator_v3.jar` instead of the newly built `SIP_Simulator_v4.jar`.
* **Fix**: Updated `run.bat` to correctly load `SIP_Simulator_v4.jar` on class path compilation.

### B. Auto-Start Express Web Server (`SIPInspectorMainFrame.java`)
* **Feature**: Added a background process manager in the Java Swing frame class to automatically start and stop the Express web server when launching the web dashboard.
* **Mechanism**:
  1. Checks port `3001` via socket probe to see if the web server is already running.
  2. If the port is offline, uses `ProcessBuilder` to spawn `node server.js` inside the `web-app/` directory in a background thread.
  3. Uses a SwingWorker background thread to poll the port status every 500ms (up to 8s).
  4. Once port `3001` becomes reachable, launches the default OS web browser pointing to `http://localhost:3001`.
  5. Implemented a JVM shutdown hook (`Runtime.getRuntime().addShutdownHook`) and window disposal listeners to kill the background Node process (`webServerProcess.destroy()`) on application exit, ensuring no orphan Node processes are left running.

---

## 2. Web Dashboard UI/UX & React Frontend Improvements

### A. Custom Searchable Select Components (`App.jsx`)
* **Problem**: Selecting scenarios and values from the web dashboard on Windows/Chrome rendered native browser dropdown popups with a blinding white background, which looked out of place and was unreadable against the dark theme.
* **Fix**: Replaced all native `<select>` and `<option>` tags with a custom-engineered React `<CustomSelect>` component.
* **Telecom Search Filter**: Added a real-time text filter search bar inside the dropdown if the items list exceeds 5. This makes navigating the simulator's **29 scenarios** and **32 values files** exceptionally quick and easy.
* **Trigger Styling**: The triggers now conform entirely to dark-theme gradients with hover animations, focus glows, and text-ellipsis truncation.

### B. SIP Template & Values Customizer
* **Feature**: Added an interactive web-based text editor tab to view, edit, and inject custom headers directly into scenario and values files.
* **Telecom/OXE Presets Panel**: Added one-click header injectors useful for telecom PBX and SBC debugging:
  * **Alcatel-Lucent OXE User-to-User Information (UUI)**: Inserts `User-to-User: 04C801C002C4083030303030303030;encoding=hex\r\n` to send binary payload routing context.
  * **PBX Call Diversion (Forwarding)**: Inserts `Diversion: <sip:70200@[local_ip]>;reason=unconditional;screen=no;privacy=off\r\n` to simulate redirected calls.
  * **P-Asserted-Identity (PAI)**: Inserts `P-Asserted-Identity: <sip:[field0]@[local_ip]>\r\n` to test caller CLI overrides.
  * **History-Info**: Inserts forwarding index headers for redirection chains.
  * **Privacy**: Inserts standard identity privacy tags (`Privacy: id\r\n`).
  * **Custom Injector**: Input form to write any proprietary `X-Header: Value` structure.

---

## 3. Desktop Java Application (Swing GUI) Enhancement

### A. Visual Sequence Diagram (`SipLadderPanel.java` [NEW])
* **Feature**: Custom component written in Java Swing to render interactive call ladders.
* **Drawing Mechanics**:
  - Overrides `paintComponent` to draw custom vertical host timelines on a dark background (`slate-900` / `0x0F172A`).
  - Draws horizontal color-coded lines and custom polygon arrowheads. Green indicates incoming traffic; cyan indicates outgoing traffic; pink (`0xEC4899`) represents the active highlighted selection.
  - Automatically filters and sorts the hosts, placing the local host (or port `5060` binding) on the leftmost timeline for stable presentation.
  - Centered labels show message names and CSeq values on dark backgrounds for legibility.
  - Integrated click hit-testing matching mouse Y-coordinate hits within 15px of a message line to trigger interactive header previews.

### B. Interactive Logs Explorer (`LogsTab.java` [MODIFY])
* **Feature**: Replaced the basic directory launcher with an integrated explorer console.
* **Layout**:
  - Horizontal `JSplitPane` dividing the sidebar and visual workspace.
  - Sidebar: Folder toggle (`UDP`/`TCP`/`TLS`/`SIP`), live file search box (`JTextField`), list container (`JList`) displaying all files sorted by modified date (latest first).
  - Workspace: `JTabbedPane` with:
    - *Visual Sequence Ladder*: Contains the drawing canvas `SipLadderPanel` and a headers detail box (`JTextArea` styled slate-950) in a vertical `JSplitPane` layout.
    - *Raw Log Text*: Monospaced text panel displaying complete, unparsed logs.

### C. Batch Test Campaign Runner (`CampaignsTab.java` [NEW])
* **Feature**: Full Java Swing batch automation utility.
* **Design & Logic**:
  - Test batch configuration stored inside `campaigns.json` in the root workspace folder, shared dynamically with the Web Dashboard.
  - Interactive table (`JTable`) displaying test parameters (scenario paths, expected outcomes, run statuses, duration metrics).
  - Add TestCase Dialog with auto-completion (automatically scans `scenarios/` and `values/` and suggests corresponding values lists when selecting scenario templates).
  - Sequential runner thread executing headless processes using `ProcessBuilder` (equivalent to web SSE streams). Spawns callee process, sleeps 3s, spawns caller process, streams stdout/stderr output lines via `StreamGobbler` background threads, waits for completion, destroys callee, and performs programmatic evaluation.
  - **Result Evaluation**: Inspects logs for lack of exceptions and presence of normal SIP indicators (`INVITE`, `200 OK`, `BYE` for calls; `200 OK` for registers) to mark success/failure against user-defined expected statuses.

---

## 4. Express Web Server Backend Updates (`server.js`)

### A. File Loading & Saving APIs
* **Endpoint `GET /api/files/:type/:filename`**: Dynamically serves scenario or values text files after passing strict directory and path-traversal safety checks (blocking `..`, `/`, and `\`).
* **Endpoint `PUT /api/files/:type/:filename`**: Saves edits from the web dashboard back to disk.

### B. SIP CRLF Line Ending Enforcer
* **Problem**: SIP parsers in enterprise PBXs (like OXE) or SBCs are extremely strict about CRLF (`\r\n`) line endings. If scenario files are edited on Linux/Mac, they save with LF (`\n`) endings, which causes the PBX to drop incoming SIP packets.
* **Fix**: Implemented automatic line-ending normalization in the `PUT` endpoint. Every file saved through the dashboard is pre-processed to convert all line endings to strict `\r\n` format before writing to disk.

---

#### 5. Phase 5: SIP Protocol Realism, Simulation & State Observability

### A. Core Socket Network Emulation
* **UDP Latency & Loss (`UDPClientSocket.java`)**: Added delay injection and probability-based packet drop filters inside the socket's `send()` and `receive()` loops. Latency delay is set in milliseconds, and packet drops are checked against random percentages.
* **SIP Anomaly Injection**: Added `corruptPacketIfNeeded` helper to intercept outgoing packets and modify headers when malformed simulations are active (`CSeq` to invalid numbers, `Call-ID` replacement, `Via` branch tampering, and corrupt `Expires` fields).

### B. SIP Dialog & Registration State Monitor
* **`SIPStateTracker.java` [NEW]**: Real-time state machine parsing REGISTER responses to track active expires counts, and INVITE/BYE messages to monitor concurrent dialog counts.
* **`MySocketAbstract.java`**: Injected update hooks inside outgoing and incoming message logs.

### C. Unified Emulation UI (Desktop & Web Dashboard)
* **`SimulationTab.java` [NEW]**: Desktop panel containing slides for latency and packet loss, a dropdown for anomaly injection, and dynamic status monitors updating timers every second.
* **Web Emulation Control (`App.jsx`)**: Integrated identical sliders and drop controls on the browser tab, communicating with settings via new `/api/simulation` REST routes in `server.js` synced with `config/settings.properties`.

---

## 6. Compilation & Deployment

* Recompiled the client bundle using Vite (`npm run build`).
* Output assets (`index-BSr0GWkP.css` and `index-DpjeNEiN.js`) copy into `web-app/public/assets/`.
* Tested the integration: Java Swing app and Web Dashboard both compile and communicate perfectly!
* Desktop application successfully compiled using Java 8 compiler flags:
  `javac -encoding UTF-8 -source 1.8 -target 1.8 -cp "lib/*;src" -d build -sourcepath src ...`
* Application compiled and packaged successfully into `SIP_Simulator_v4.jar` (run via `build_and_run.bat`).
