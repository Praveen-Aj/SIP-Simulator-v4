# SIP Simulator v4 — Repository Structure & Architectural Guide

This document details the architectural layout, directory organization, and module responsibilities of the **SIP Simulator v4** repository.

---

## 1. Directory Tree Overview

```text
SIP_Simulator_v4/
├── build.bat                   # Master Java compilation & JAR packaging script
├── run.bat                     # Desktop GUI launch script (points to bundled or system JRE)
├── run_offline_tests.bat       # Full offline regression test runner (14 suites)
├── run_oxe_live_tests.bat      # Live OXE PBX & MicroSIP qualification test runner
├── start_dashboard.bat         # Express server & React web dashboard launcher
├── SIP_Simulator_v4.jar        # Standalone executable JAR file
├── .gitignore                  # Git ignore rules for build artifacts, logs, and temp files
├── .gitattributes              # Git attribute line-ending and binary preservation rules
│
├── src/                        # Java Source Code (Java 8 Source & Target)
│   ├── sipinspector/
│   │   ├── Call/               # Centralized Call Engine, Multi-Call, & Performance
│   │   ├── Media/              # RTP/RTCP Audio Pipeline, Codecs, Jitter Buffer, & MOS
│   │   ├── Socket/             # Polymorphic Sockets (UDP, TCP Framed, TLSv1.2/1.3)
│   │   ├── Utils/              # SIP Parser, Stream Framer, State Tracker, Cryptography
│   │   ├── Tone/               # Telephony Tone Synthesizer & Audio Utilities
│   │   ├── Threads/            # Background Worker Threads (Generator, Maintainer, Receiver)
│   │   ├── AutoTest/           # Automated Test Campaign Engine
│   │   └── *.java              # Swing Desktop UI Panels & Main Controller
│   └── Icons/                  # Application branding, ICO, and PNG assets
│
├── web-app/                    # Web Observability & Management Dashboard
│   ├── server.js               # Express 4 backend proxy, child-process manager, REST API
│   ├── package.json            # Node.js backend dependencies
│   ├── public/                 # Static web assets & compiled React production build
│   └── client/                 # React 18 + Vite Frontend Application
│       ├── src/
│       │   ├── App.jsx         # Primary React Dashboard (Console, Ladder, Logs, Phone)
│       │   ├── App.css         # Modern dark-mode styling & responsive grid layouts
│       │   └── main.jsx        # React root entry point
│       ├── package.json        # Frontend dependencies
│       └── vite.config.js      # Vite build configuration (outputs to ../public)
│
├── test/                       # Comprehensive Automated Test Suites
│   ├── OfflineFullRegressionSuite.java  # Master offline runner (14 suites, 80+ tests)
│   ├── SipFeatureCompletenessTest.java   # DTMF, REFER, Replaces, Hold, Early Media, PAI
│   ├── TransportSecurityAuditTest.java   # UDP/TCP/TLS port mapping & credential audit
│   ├── CallEngineLifecycleTest.java      # Startup, shutdown, in-place restart, port pool
│   ├── OxePhase7FeatureValidationTest.java # Live OXE registration, DTMF, Hold, 0-leak audit
│   ├── OxeLiveConcurrentCallTest.java    # Live OXE 2-call concurrency qualification
│   ├── OxeProductionCallEngineTest.java  # Two-endpoint live audio & WAV verification
│   ├── MicroSipIpc.java                  # MicroSIP Windows Named Pipe JSON-RPC interface
│   └── *.java                            # Unit tests (Codecs, SRTP, Framer, MOS, Parser)
│
├── scenarios/                  # RFC 3261 Compliant SIP Scenario Templates
│   ├── Outgoing_basic_call.txt # Standard SIP Outbound Call with SDP
│   ├── Incoming_basic_call_generic.txt # Standard Inbound Call receiver & 200 OK
│   ├── Attended_transfer_*.txt # Supervised transfer scenario flows
│   ├── Register_options_*.txt  # Registration & OPTIONS healthcheck flows
│   └── *.txt                   # 40+ pre-configured scenario templates
│
├── values/                     # Variable Injection Tables (CSV / Semicolon Format)
│   ├── caller_values.txt       # Caller extension, domain, and transport values
│   ├── callee_values.txt       # Callee extension and IP parameters
│   └── *.txt                   # Token mappings corresponding to scenarios
│
├── config/                     # Configuration Files
│   ├── settings.properties     # Active application settings (Theme, SRTP key, Emulation)
│   ├── settings.properties.example # Safe template configuration for reference
│   └── rtp_diagnostics.json    # Jitter buffer and diagnostic parameters
│
├── security/                   # TLS/SSL Cryptographic Storage
│   ├── README.txt              # Keystore and certificate generation instructions
│   ├── SIPInspKeystore.jks     # Keystore placeholder (password: changeit)
│   └── SIPInspTruststore.jks   # Truststore placeholder
│
├── tones/                      # Reference Telephony Audio Tones (WAV, 8kHz Mono)
│   ├── DialTone_generated.wav
│   ├── RBT_tone.wav            # Ringback tone
│   ├── BusyTone_generated.wav  # Busy tone
│   └── Hold_tone.wav           # Music on hold
│
├── pcap/                       # Reference PCAP Audio Captures
│   ├── dtmf_0.pcap ... dtmf_9.pcap # Sample telephone-event captures
│   └── *.pcap                  # Reference network captures for playback
│
├── docs/                       # Engineering Documentation & Reports
│   ├── DEVELOPMENT_ROADMAP.md  # Complete 8-phase developmental roadmap
│   ├── REGRESSION_TEST_REPORT.md # Master regression test execution reports
│   ├── OXE_INTEGRATION_VALIDATION.md # Live Alcatel-Lucent OXE integration analysis
│   ├── PERFORMANCE_TESTING_GUIDE.md # Performance engine & REST API guide
│   └── OPEN_ISSUES.md          # Defect tracking, PBX behaviors, & technical debt
│
├── lib/                        # Java Dependencies (Compile & Runtime)
│   ├── flatlaf-3.2.5.jar       # Modern Swing Look and Feel
│   ├── miglayout-3.7.3.1-swing.jar # Responsive grid-based layout manager
│   ├── miglayout-swing-4.2.jar # Secondary layout support
│   └── jpcap.jar               # Native packet capture library
│
└── Github/                     # Dedicated GitHub Publication Workspace
    ├── README.md               # Publication overview & technical summary
    ├── REPOSITORY_STRUCTURE.md # Detailed architectural layout (this file)
    └── PUBLICATION_CHECKLIST.md # Release qualification & security verification checklist
```

---

## 2. Core Module Architecture

### 2.1 Java SIP Engine (`src/sipinspector/`)

#### 1. `sipinspector.Call`
- **`SipCallEngine.java`**: The central stateful SIP dialog engine. Manages `CallSession` instances, auto-resolves local network interfaces towards target PBXs, routes incoming SIP requests (`INVITE`, `ACK`, `BYE`, `CANCEL`, `INFO`, `REFER`, `OPTIONS`), and initiates outbound dialogs. Fully compliant with RFC 3261 Section 14.1 and RFC 6086 Section 4.2.2 regarding mid-dialog transaction failure isolation.
- **`CliCallEngine.java`**: Headless daemon controller exposing a lightweight embedded HTTP/REST control interface for Express/React automation.
- **`PerformanceTestEngine.java`**: High-concurrency performance engine supporting CPS pacing, concurrency caps, and setup latency percentiles ($p50, p95, p99$).
- **`FailureInjectionEngine.java`**: Programmatic failure injection harness (simulating 404, 486, 488, 408 Timer B, CANCEL).

#### 2. `sipinspector.Media`
- **`LiveAudioController.java`**: Coordinates real-time microphone capture, speaker playback, tone generation, and dual WAV recording (`tx_<callId>.wav` & `rx_<callId>.wav`). Computes real-time ITU-T E-model G.107 Transmission Rating Factor $R$ and MOS score ($1.0 \le \text{MOS} \le 4.5$).
- **`LiveAudioSenderThread.java`**: Packages encoded audio frames into RFC 3550 RTP packets at 20ms intervals. Generates RFC 2833 / RFC 4733 telephone-event DTMF packets (PT 101, 8kHz clock) with redundant end packets ($E=1$).
- **`LiveAudioReceiverThread.java`**: Reassembles incoming RTP streams using a sequence-ordered 10-packet bounded jitter buffer. Detects sequence discontinuities, out-of-order packets, and jitter.
- **`LiveAudioRuntimeDiagnostics.java`**: Centralized telemetry tracking active senders, active receivers, and open UDP datagram sockets.

#### 3. `sipinspector.Socket`
- **`MySocketAbstract.java`**: Polymorphic base class providing unified `send()`, `receive()`, and `close()` semantics across all transports.
- **`UDPClientSocket.java`**: Non-blocking UDP datagram transport on port 5060.
- **`TCPClientSocket.java`**: Stream-framed TCP transport on port 5060 utilizing `SipStreamFramer`.
- **`TLSClientSocket_V6.java`**: Encrypted TLSv1.2/1.3 transport on port 5061 (SIPS) with non-blocking handshake handling.

#### 4. `sipinspector.Utils`
- **`SIPParser.java`**: High-speed, zero-dependency SIP header, parameter, and body extractor.
- **`SipStreamFramer.java`**: Byte-stream accumulator that parses partial reads, detects double-CRLF headers, and segments `Content-Length` payloads over continuous TCP streams.
- **`SIPStateTracker.java`**: Tracks PBX registration state, CSeqs, and dialog tags across sequential scenario steps.
- **`SecurityManager.java`**: Handles Base64 cryptographic keys, SDES crypto attribute formulation, and SRTP context setup.
- **`AppPaths.java`**: Dynamic runtime path resolution ensuring absolute path independence across workstations, JREs, and testing scripts.

---

### 2.2 Web Dashboard & Server (`web-app/`)

- **`web-app/server.js`**:
  - Express 4 application serving as the integration bridge.
  - Safe child process management: tracks spawned PIDs and performs graceful tree termination without affecting parent Java processes or Windows Audio services.
  - Exposes REST endpoints for scenario management, test execution, campaign export, live audio device enumeration, and phone controls.
  - Provides Server-Sent Events (SSE) log streaming for real-time console feedback.
- **`web-app/client/src/App.jsx`**:
  - React 18 single-page application.
  - Tabs:
    1. **Execution Console**: Real-time stdout stream viewer.
    2. **Sequence Ladder**: SVG/HTML visual signaling ladder diagram with interactive message inspection.
    3. **UDP/TCP Logs Browser**: Multi-directory log viewer with instant search filtering.
    4. **RTP Audio Recordings**: Integrated audio player for recorded call audio.
    5. **Media & Security Diagnostics**: Jitter buffer, packet loss, and SRTP status.
    6. **Template Customizer**: In-browser editor for SIP scenarios and values.
    7. **Campaign Runner**: Batch test suite execution and pass/fail evaluation.
    8. **Simulation Control**: Real-time network delay, loss, and malformed packet emulation.
    9. **Performance & Multi-Call**: Concurrency benchmarking, CPS pacing, latency graphs, and failure injection.
    10. **Live Phone & Controls (Phase 7)**: PBX registration banner, dialer, DTMF keypad (0–9, *, #, A–D), live audio KPI tiles, hold/resume, mute, and transfer drawer.

---

### 2.3 Automated Test Suites (`test/`)

- **`test/OfflineFullRegressionSuite.java`**: Master harness running all 14 offline test suites with zero external PBX or network requirements.
- **`test/OxePhase7FeatureValidationTest.java`**: Master live qualification script verifying registration, audio establishment, RFC 2833 DTMF, Hold/Resume, line admission control, and zero-leak cleanup against Alcatel-Lucent OXE PBX.
- **`test/MicroSipIpc.java`**: Native Windows Named Pipe (`\\.\pipe\microsip_ipc`) client for programmatic control and verification of the MicroSIP softphone.
