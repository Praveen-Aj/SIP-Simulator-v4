# SIP Simulator v4

A powerful SIP (Session Initiation Protocol) testing, simulation, and diagnostic tool for VoIP engineers and telecom developers, featuring a fully synchronized **Desktop Java Client** and a responsive **React Web Dashboard**.

---

## 🚀 What's New in Version 4

### 💻 Synchronization Across Desktop App & Web Dashboard
Version 4 achieves full feature parity between the primary Java Desktop Client and the Web Dashboard. All major additions are available in both:

1. **📊 SIP Log Visual Ladder (Sequence Diagram)**
   * Parses standard UDP/TCP/TLS log files into structured sequence timelines.
   * Renders vertical timelines for local/remote bindings and hostnames.
   * Draws color-coded signaling arrows (outgoing in cyan, incoming in green) with request/response method names and sequence tags (`CSeq`).
   * **Interactive details panel**: Click on any message arrow in the diagram to inspect its raw headers and bodies instantly in a dark detail area.

2. **🔍 Interactive Search Log Browser**
   * Multi-directory support: choose between UDP, TCP, TLS, and SIP folders.
   * Search filter bar: instantly find log files by name.
   * Quick-switch raw log text view vs visual sequence ladder.

3. **⚙️ Test Campaign Runner**
   * Run automated regression suites sequentially in background CLI instances.
   * Read and write test configurations dynamically to a shared `campaigns.json` registry.
   * Stream live standard outputs and errors into an integrated terminal view in real-time.
   * Auto-evaluate success/failure checks comparing actual results to your expected outcomes (`PASS`/`FAIL`).

4. **📞 Production Call Engine & Audio Management (Phase 4)**
   * Centralized `SipCallEngine` managing concurrent dialogs with strict RFC 3261 `CallState` enforcement.
   * Multi-codec negotiation: PCMA (PT=8), PCMU (PT=0), wideband G.722 (PT=9).
   * Dual WAV audio recording (`tx_<callId>.wav` & `rx_<callId>.wav`) per dialog with RMS energy and peak amplitude analysis.
   * Mid-dialog session renegotiation: Call Hold (`a=sendonly`) and Resume (`a=sendrecv`) via Re-INVITE sequences.
   * Media controls: Microphone mute with comfort silence, speaker mute, graceful tone synthesis fallback.
   * Embedded REST API daemon and Express proxy endpoints (`/api/call/*`, `/api/audio/devices`).
   * Validated against live Alcatel-Lucent OmniPCX Enterprise (OXE) PBX and MicroSIP (8/8 tests passed).

---

## 📂 Project Structure

```text
SIP_Simulator_v4/
├── scenarios/          # SIP scenario templates (RFC 3261 compliant)
├── values/            # Variables files for scenarios
├── logs/              # Captured UDP/TCP/TLS logs
├── recordings/        # Real-time voice stream recordings (.wav)
├── security/          # Keystore and TLS certificates
├── lib/               # Dependency JARs (FlatLaf, Jpcap, MigLayout)
├── src/               # Java Swing source code
└── web-app/           # React + Express Web Dashboard
    ├── client/        # Vite + React dashboard frontend
    ├── public/        # Compiled Vite assets served by Express
    └── server.js      # Express server backend
```

---

## 📋 Development Standards & Portability

All modifications to this repository must adhere to the permanent development policy defined in [docs/DEVELOPMENT_STANDARDS.md](docs/DEVELOPMENT_STANDARDS.md):
- **Preserve Existing Functionality:** Working SIP execution, scenario parsing, and socket behavior must never suffer regressions.
- **Zero Machine-Specific Paths:** All paths must resolve dynamically via `sipinspector.Utils.AppPaths` in Java or relative to `TRUNK_DIR` in Node.js.
- **Java 8 & Windows Portability:** Strictly preserve `-source 1.8 -target 1.8` bytecode compatibility and no-admin execution.
- **Pre-Commit Verification:** Run `build.bat`, `AssertionUnitTest`, `OfflineValidationSuite`, `DisplayScalingValidationTest`, and `PortabilityValidationTest` before committing.

---

## 🛠️ Getting Started

### Requirements
* **Java 8** or higher
* **Node.js** (required on the system PATH to run the Web Dashboard)

### Compiling and Packaging
To compile the Java source and create the final launcher JAR:
```cmd
build_and_run.bat
```
To build the static web dashboard assets:
```bash
cd web-app/client
npm run build
```

### Running
Run the executable JAR directly:
```bash
java -Djava.library.path=lib -cp "SIP_Simulator_v4.jar;lib/*" sipinspector.Main
```
Or use the convenience scripts:
* `run.bat` - Standard launch.
* `run_with_udp_debug.bat` - Runs Java with stdout diagnostic streams.

Once the Java Swing UI opens, go to **Tools → Launch Web Dashboard** to automatically launch the browser at `http://localhost:3001`.

---

## 💡 Feature Guide & Step-by-Step Instructions

This guide provides comprehensive, step-by-step workflows for each feature inside the SIP Simulator v4 workspace (available in both the Java Swing client and the React Web Dashboard).

---

### 🚀 Quick Start Tutorial: Running a Call Flow

If you are new to the simulator, follow this simple 3-step workflow to execute and inspect a test call:
1. **Load Scenarios & Values**: Navigate to the **Scenario & Values Loader** tab. Select `Reg_client.txt` from the **Scenario** dropdown. The **Values** dropdown will auto-select `Reg_client_values.txt`. Click **Load**.
2. **Execute Call**: Switch to the **Scenario Editor** tab at the top-left, and click the **Run** button on the main toolbar.
3. **Inspect the Visual Flow**: Navigate to the **Logs** tab. In the sidebar dropdown, select the `UDP` transport folder, click the latest log file, click the **Visual Sequence Ladder** sub-tab, and click on any message arrow in the timeline diagram to view the raw SIP headers.

---

### 1. Scenario Editor (SIP Code Constructor)
* **What it does**: The central workspace to compose, verify, reorder, and execute individual SIP messages.
* **Step-by-Step Usage**:
  1. **Select a message**: Click on any signaling message in the left-hand **Message Tree** (e.g. `INVITE`, `180 Ringing`, `200 OK`). The raw SIP headers will populate in the editor at the bottom.
  2. **Add standard headers**: Select a standard header tag from the left dropdown list (e.g. `Contact`, `Content-Type`) and click **<< Add**. The tag will append to the current message template.
  3. **Reorder transmission sequence**: Highlight a message block in the list and click the **Up/Down Arrow Buttons** to change its order in the transaction flow.
  4. **Perform raw text edits**: Double-click lines inside the bottom raw scenario script pane to manually edit text headers, body variables, or SDP parameters.
  5. **Save Changes**: Click the **Save** button on the editor toolbar to commit changes to the scenario file.

### 2. Scenario & Values Loader
* **What it does**: Load pre-existing RFC-compliant scenario templates, preview resolved message payloads, or convert raw Wireshark/pcap packet captures into test scripts.
* **Step-by-Step Usage**:
  1. **Select a scenario**: Click the **Scenario File** dropdown list and select a template (e.g., `Outgoing_basic_call.txt`).
  2. **Auto-map variable values**: The loader will automatically detect and suggest its corresponding parameters values profile in the **Values File** dropdown. Click **Load** to parse the templates.
  3. **Verify Resolved Preview**: Review the **Resolved SIP Message Preview** on the right side. It compiles the first SIP message block and replaces all brackets placeholders (such as `[remote_ip]`, `[local_port]`, or `[call_id]`) with actual bind parameters. Ensure this preview matches the expected outbound syntax.
  4. **Convert a PCAP Trace**:
     - Click the **Import PCAP File** button.
     - Select a standard Wireshark/tcpdump packet capture file (`.pcap` or `.pcapng`) containing SIP signaling.
     - The converter parses call flow directions (drawing standard arrows `---------->` and `<----------`), generates a scenario script, and extracts parameters into a values profile. The files are saved automatically in `scenarios/` and `values/` and are ready to be loaded.

### 3. Edit Values (Grid Customizer & Telecom Presets)
* **What it does**: Customize IP addresses, UDP ports, user accounts, and test advanced enterprise PBX configurations.
* **Step-by-Step Usage**:
  1. **Edit fields in the grid**: Double-click any cell in the variables table (e.g., updating local bindings, usernames, passwords, or SDP audio formats), type the new value, and press **Enter** to save.
  2. **Inject Telecom Presets (Alcatel-Lucent OXE & PBX structures)**: Select a target row in the grid and click one of the preset helper buttons at the bottom:
     - **Inject UUI Header**: Injects hex-encoded User-to-User Information context (e.g. `User-to-User: 04C801C002C4083030303030303030;encoding=hex\r\n`) for enterprise call routing.
     - **Inject Call Diversion**: Inserts a `Diversion` header simulating forwarding redirection (e.g. unconditional, busy, or no-reply forwarding).
     - **Inject P-Asserted-Identity**: Inserts a `P-Asserted-Identity` header to simulate CLI (calling line ID) overrides.
     - **Custom Injection**: Type any custom proprietary header tag and value (e.g. `X-Custom-PBX-Header: Value`).
  3. **Value Preservation**: Any manual customization is flagged as edited in memory (`hasBeenEdited` state), protecting your edits from being overwritten by default template settings.

### 4. Logs (Visual Sequence Diagram Explorer)
* **What it does**: Interactive logging console to inspect and visualize SIP messages using vertical host timeline ladders.
* **Step-by-Step Usage**:
  1. **Choose transport directory**: Select the target protocol subfolder (`UDP`, `TCP`, `TLS`, or `SIP`) from the dropdown.
  2. **Search and load logs**: Locate log files sorted by modified date (latest first). Use the text filter search bar to find log files quickly. Click a filename to load it.
  3. **Inspect Raw Logs**: Switch to the **Raw Log Text** sub-tab to scroll through raw text console printouts.
  4. **Visualize Sequence timelines**: Switch to the **Visual Sequence Ladder** sub-tab. Host timelines are drawn vertically. message arrows show exchange directions (cyan for outgoing, green for incoming).
  5. **View Packet Details**: Click on any message arrow inside the ladder canvas. The bottom slate-themed detail panel will instantly render the exact raw SIP headers and bodies for that packet.

### 5. Campaigns (Batch Test Runner)
* **What it does**: Run automated regression suites sequentially to check codebase stability, PBX routing, and socket binds.
* **Step-by-Step Usage**:
  1. **Add Test Cases**: Click **Add Test Case**. In the modal dialog, select a scenario template, select the values profile (auto-suggested based on scenario), and specify the **Expected Status** (`PASS` or `FAIL`). Click **Save** to write the configuration to `campaigns.json`.
  2. **Run the campaign suite**: Click **Execute Campaign Batch**.
  3. **Monitor Live Console Output**: The bottom panel streams stdout/stderr output lines from the spawned headless execution processes in real-time.
  4. **Review Results**: The runner parses the generated log files and checks for expected response codes. The test cases in the table will update with a green **PASS** or red **FAIL** indicator.

### 6. Simulation (Network Anomalies & State Tracking)
* **What it does**: Inject socket latency, drop packets, tamper with SIP protocol structures, or handle unexpected requests in real-time.
* **Step-by-Step Usage**:
  1. **Inject network delays**: Adjust the **Latency (Delay)** slider to inject socket delays (in milliseconds) to outgoing and incoming transmissions.
  2. **Inject packet loss**: Adjust the **Packet Loss** slider to drop a percentage of transmissions randomly, checking how client/server timers handle retransmission.
  3. **Tamper with headers**: Select an option from the **Anomaly Injection** dropdown list (e.g. *Corrupt CSeq*, *Tamper Call-ID*, *Corrupt Expires*, *Tamper Via Branch*). Outbound packets will be corrupted on-the-fly.
  4. **Auto-Respond to Unexpected NOTIFY Requests**: Call servers (like Alcatel-Lucent OXE) sometimes emit spontaneous `NOTIFY` check-ins expecting a `200 OK`. The simulator intercepts these unexpected packets and automatically responds with `200 OK` (even if the `NOTIFY` is not part of the active scenario trace), preventing transaction timeouts.
  5. **Monitor Session states**: The **Active Sessions & Registration States** panel displays active session timers, expires countdowns, and dialogue tracking.

### 7. RTP Inspector (Media & TLS Auditing)
* **What it does**: Real-time media analysis, DTMF event checks, and TLS session verification.
* **Step-by-Step Usage**:
  1. **Negotiate media**: Execute a call scenario negotiating SDP/RTP (e.g., `Outgoing_basic_call.txt`).
  2. **Inspect RTP metrics**: Read columns in the grid showing SSRC channels, RFC 3550 jitter calculation depth (ms), and timestamp drift.
  3. **Modeless Execution Navigation**: The active execution dialog runs in modeless format. This allows you to freely click onto other tabs (like the **Logs Browser** or **RTP Inspector**) while the call is live, without locking out the Swing application window.
  4. **Audit TLS handshakes**: When testing secure connections (TLS or mutual-TLS), the **TLS Security Details** table will populate with the active cipher suite, protocol version, Common Name (CN), issuer, and certificate expiration dates. Use this to detect expiring certificates.

### 8. Web Dashboard Integration
* **What it does**: Control scenarios, customization, logs, and campaigns through a synchronized web browser dashboard.
* **Step-by-Step Usage**:
  1. **Launch Dashboard**: Go to the menu **Tools -> Launch Web Dashboard**. The application will automatically start the background Express Node server on port `3001` and open the browser.
  2. **Navigate & Customize**: Use custom searchable dropdowns in the browser client to swap files or configure variables.
  3. **Logs Portability**: The backend logs browser dynamically resolves to the user's home directory (`os.homedir()`). The web dashboard is fully portable and immediately tracks logs on any PC without configuration.
  4. **Line Ending Enforcer**: Every edit saved in the web dashboard is normalized to strict CRLF (`\r\n`) endings to prevent PBX systems from dropping packages due to LF formatting issues.
  5. **Export Sequence Diagrams**: Completed campaigns can be exported containing inline vertical SVG sequence ladder flow diagrams.

---

## 📖 Documentation & Changes
* **Full Changelog**: See [CHANGELOG.md](CHANGELOG.md).
* **Detailed v4 Implementation Report**: See [v4_DOCUMENTATION.md](v4_DOCUMENTATION.md).

---

**Current Version**: 4.0  
**License**: Distributed under the GNU General Public License version 3.
