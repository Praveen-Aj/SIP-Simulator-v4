# SIP Simulator v4 - Comprehensive User Guide & Tutorial

Welcome to the **SIP Simulator v4 User Guide**. This document provides detailed, step-by-step instructions on how to use every feature in the simulator's workspace, covering both the desktop Java Swing interface and the React-based Web Dashboard.

---

## 🚀 Quick Start: Run a Test Call in 3 Steps

If you are starting the application for the first time, follow this quick-start tutorial to load a scenario, execute a run, and analyze the resulting message exchanges:

1. **Load Scenarios & Values**:
   - Go to the **Scenario & Values Loader** tab.
   - Select `Reg_client.txt` from the **Scenario File** dropdown menu.
   - The system will automatically select its paired variables configuration file, `Reg_client_values.txt`, in the **Values File** dropdown.
   - Click the **Load** button to parse the variables and messages into active memory.

2. **Execute the Scenario**:
   - Switch to the **Scenario Editor** tab (first tab at the top-left of the application workspace).
   - Click the **Run** button on the main toolbar.

3. **Inspect the Visual Signaling Flow**:
   - Navigate to the **Logs** tab.
   - Select the `UDP` transport folder from the sidebar dropdown.
   - Click the latest log file listed in the sidebar.
   - Switch to the **Visual Sequence Ladder** sub-tab.
   - Click on any color-coded message arrow in the sequence timeline to inspect its raw SIP headers in the bottom details pane.

---

## 💡 Feature Guides & Step-by-Step Workflows

### 1. Scenario Editor (SIP Code Constructor)
* **Purpose**: Compose, modify, reorder, and execute individual SIP messages.
* **Step-by-Step Instructions**:
  1. **Select a Message Block**: Click on any signaling message in the left-hand **Message Tree** (e.g. `INVITE`, `180 Ringing`, `200 OK`). The raw SIP headers will populate in the editor pane at the bottom.
  2. **Add Standard Headers**: Select a standard header tag from the left dropdown list (e.g. `Contact`, `Content-Type`, `User-Agent`) and click **<< Add**. The tag will append to the current message template.
  3. **Reorder Transmission Sequence**: Highlight a message block in the list and click the **Up/Down Arrow Buttons** on the toolbar to change its order in the transaction flow.
  4. **Perform Raw Text Edits**: Double-click lines inside the bottom raw scenario script pane to manually edit text headers, body variables, or SDP parameters.
  5. **Save Changes**: Click the **Save** button on the editor toolbar to commit changes to the scenario file.

---

### 2. Scenario & Values Loader
* **Purpose**: Load pre-existing RFC-compliant scenario templates, preview resolved message payloads, or convert raw Wireshark/pcap packet captures into test scripts.
* **Step-by-Step Instructions**:
  1. **Select a Scenario Template**: Click the **Scenario File** dropdown list and select a template (e.g., `Outgoing_basic_call.txt`).
  2. **Auto-map Variable Values**: The loader will automatically detect and suggest its corresponding parameters values profile in the **Values File** dropdown. Click **Load** to parse the templates.
  3. **Verify Resolved Preview**: Review the **Resolved SIP Message Preview** on the right side. It compiles the first SIP message block and replaces all brackets placeholders (such as `[remote_ip]`, `[local_port]`, or `[call_id]`) with actual bind parameters. Ensure this preview matches the expected outbound syntax before running.
  4. **Convert a PCAP Trace**:
     - Click the **Import PCAP File** button.
     - Select a standard Wireshark/tcpdump packet capture file (`.pcap` or `.pcapng`) containing SIP signaling.
     - The converter parses call flow directions (drawing standard arrows `---------->` and `<----------`), generates a scenario script, and extracts parameters into a values profile. The files are saved automatically in `scenarios/` and `values/` and are ready to be loaded.

---

### 3. Edit Values (Grid Customizer & Telecom Presets)
* **Purpose**: Customize IP addresses, UDP ports, user accounts, and test advanced enterprise PBX configurations (such as Alcatel-Lucent OmniPCX Enterprise).
* **Step-by-Step Instructions**:
  1. **Edit Fields in the Grid**: Double-click any cell in the variables table (e.g., updating local bindings, usernames, passwords, or SDP audio formats), type the new value, and press **Enter** to save.
  2. **Inject Telecom Presets**: Select a target row in the grid and click one of the preset helper buttons at the bottom:
     - **Inject UUI Header**: Injets hex-encoded User-to-User Information context (e.g. `User-to-User: 04C801C002C4083030303030303030;encoding=hex\r\n`) for enterprise call routing.
     - **Inject Call Diversion**: Inserts a `Diversion` header simulating forwarding redirection (e.g. unconditional, busy, or no-reply forwarding).
     - **Inject P-Asserted-Identity**: Inserts a `P-Asserted-Identity` header to simulate CLI (calling line ID) overrides.
     - **Custom Injection**: Type any custom proprietary header tag and value (e.g. `X-Custom-PBX-Header: Value`).
  3. All modifications are instantly stored in active memory and applied to subsequent simulation runs.

---

### 4. Logs (Visual Sequence Diagram Explorer)
* **Purpose**: Interactive logging console to inspect and visualize SIP messages using vertical host timeline ladders.
* **Step-by-Step Instructions**:
  1. **Choose Transport Directory**: Select the target protocol subfolder (`UDP`, `TCP`, `TLS`, or `SIP`) from the dropdown.
  2. **Search and Load Logs**: Locate log files sorted by modified date (latest first). Use the text filter search bar to find log files quickly. Click a filename to load it.
  3. **Inspect Raw Logs**: Switch to the **Raw Log Text** sub-tab to scroll through raw text console printouts.
  4. **Visualize Sequence Timelines**: Switch to the **Visual Sequence Ladder** sub-tab. Host timelines are drawn vertically. Message arrows show exchange directions (cyan/blue for outgoing, green for incoming).
  5. **View Packet Details**: Click on any message arrow inside the ladder canvas. The bottom slate-themed detail panel will instantly render the exact raw SIP headers and bodies for that packet.

---

### 5. Campaigns (Batch Test Runner)
* **Purpose**: Run automated regression suites sequentially to check codebase stability, PBX routing, and socket binds.
* **Step-by-Step Instructions**:
  1. **Add Test Cases**: Click **Add Test Case**. In the modal dialog, select a scenario template, select the values profile (auto-suggested based on scenario), and specify the **Expected Status** (`PASS` or `FAIL`). Click **Save** to write the configuration to `campaigns.json`.
  2. **Run the Campaign Suite**: Click **Execute Campaign Batch**.
  3. **Monitor Live Console Output**: The bottom panel streams stdout/stderr output lines from the spawned headless execution processes in real-time.
  4. **Review Results**: The runner parses the generated log files and checks for expected response codes. The test cases in the table will update with a green **PASS** or red **FAIL** indicator.

---

### 6. Simulation (Network Anomalies & State Tracking)
* **Purpose**: Inject socket latency, drop packets, or tamper with SIP protocol structures in real-time.
* **Step-by-Step Instructions**:
  1. **Inject Network Delays**: Adjust the **Latency (Delay)** slider to inject socket delays (in milliseconds) to outgoing and incoming transmissions.
  2. **Inject Packet Loss**: Adjust the **Packet Loss** slider to drop a percentage of transmissions randomly, checking how client/server timers handle retransmission.
  3. **Tamper with Headers**: Select an option from the **Anomaly Injection** dropdown list (e.g. *Corrupt CSeq*, *Tamper Call-ID*, *Corrupt Expires*, *Tamper Via Branch*). Outbound packets will be corrupted on-the-fly.
  4. **Monitor Session States**: The **Active Sessions & Registration States** panel displays active session timers, expires countdowns, and dialogue tracking.

---

### 7. RTP Inspector (Media & TLS Auditing)
* **Purpose**: Real-time media analysis, DTMF event checks, and TLS session verification.
* **Step-by-Step Instructions**:
  1. **Negotiate Media**: Execute a call scenario negotiating SDP/RTP (e.g., `Outgoing_basic_call.txt`).
  2. **Inspect RTP Metrics**: Read columns in the grid showing SSRC channels, RFC 3550 jitter calculation depth (ms), and timestamp drift.
  3. **Audit TLS Handshakes**: When testing secure connections (TLS or mutual-TLS), the **TLS Security Details** table will populate with the active cipher suite, protocol version, Common Name (CN), issuer, and certificate expiration dates. Use this to detect expiring certificates.

---

### 8. Web Dashboard Integration
* **Purpose**: Control scenarios, customization, logs, and campaigns through a synchronized web browser dashboard.
* **Step-by-Step Instructions**:
  1. **Launch Dashboard**: Go to the menu **Tools -> Launch Web Dashboard**. The application will automatically start the background Express Node server on port `3001` and open the browser.
  2. **Navigate & Customize**: Use custom searchable dropdowns in the browser client.
  3. **Line Ending Enforcer**: Every edit saved in the web dashboard is normalized to strict CRLF (`\r\n`) endings to prevent PBX systems from dropping packages due to LF formatting issues.
  4. **Export Sequence Diagrams**: Completed campaigns can be exported containing inline vertical SVG sequence ladder flow diagrams.
