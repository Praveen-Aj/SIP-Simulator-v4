# SIP Simulator v4 — GitHub Publication Workspace

This directory serves as the centralized workspace and documentation staging area for the publication and repository management of **SIP Simulator v4**.

---

## 1. Project Purpose & Overview

**SIP Simulator v4** is an enterprise-grade telecom protocol simulation, performance qualification, and diagnostic testing platform for VoIP engineers, PBX system administrators, and telecom software developers.

It bridges legacy telecom appliances and modern web observability by combining:
1. A **Java 8 Desktop Client** (Swing, FlatLaf, MigLayout) for high-performance protocol simulation, packet crafting, and local audio streaming.
2. A **React 18 + Vite Web Dashboard** backed by an Express REST API daemon for remote campaign management, ladder diagram trace visualization, live phone controls, and automated reporting.

The platform was engineered and qualified against physical enterprise telephony infrastructure, specifically the **Alcatel-Lucent OmniPCX Enterprise (OXE R101.1 n4.524.2)** PBX and RFC-compliant softphones (**MicroSIP 3.22.16**).

---

## 2. Major Capabilities

- **Strict RFC 3261 SIP Dialog Lifecycle Engine:**
  Stateful transaction tracking (`IDLE`, `CALLING`, `PROCEEDING`, `RINGING`, `EARLY_MEDIA`, `ESTABLISHED`, `TERMINATING`, `TERMINATED`, `FAILED`) with Timer A/B retransmissions, CANCEL handling, non-2xx ACKs, and mid-dialog preservation (RFC 3261 §14.1 & RFC 6086 §4.2.2).
- **Multi-Transport Support:**
  - **UDP:** High-throughput RFC 3261 transport on port 5060 with non-blocking datagram channels.
  - **TCP:** Byte-stream framing with double-CRLF boundary detection and `Content-Length` payload accumulation (`SipStreamFramer`).
  - **TLS:** Secure signaling on port 5061 (SIPS URI) supporting TLSv1.2/1.3, customizable truststores, and non-blocking certificate handling.
- **Telecom Audio & Codec Processing:**
  - Standard codecs: G.711 A-law (PCMA, PT=8), G.711 $\mu$-law (PCMU, PT=0), wideband G.722 (PT=9).
  - SRTP encryption with AES-CM-128 and HMAC-SHA1-80 authentication (RFC 3711 / RFC 4568 SDES).
  - Dual WAV audio recording (`tx_<callId>.wav` and `rx_<callId>.wav`) with peak amplitude and RMS energy analysis.
- **Advanced SIP Feature Support:**
  - **DTMF:** In-band RFC 2833 / RFC 4733 RTP telephone-events (PT 101, 8kHz clock) with redundant end packets ($E=1$) and out-of-band SIP INFO (`application/dtmf-relay`).
  - **Call Transfer:** RFC 3515 `REFER` (blind transfer) and RFC 3891 `Replaces` (attended transfer).
  - **Call Hold & Resume:** Mid-dialog Re-INVITE state machine with `a=sendonly`, `a=recvonly`, and `a=sendrecv`.
  - **Early Media:** Provisional 183 Session Progress with SDP negotiation and background audio playback.
  - **Identity & Privacy:** Automated parsing of `P-Asserted-Identity`, `Remote-Party-ID`, `Diversion`, `Privacy`, and caller display names.
- **Performance & Multi-Call Campaigns:**
  - Real-time ITU-T E-model G.107 Mean Opinion Score (MOS) calculation with packet loss concealment (PLC, $B_{pl}=19.0$), jitter tracking, and mouth-to-ear delay modeling.
  - Rate-controlled CPS (Calls Per Second), concurrency capping, and soak testing.
  - Setup latency percentiles (min, avg, p50, p95, p99, max) and SIP response code distributions.
  - Machine-readable JSON and human-readable HTML reporting.

---

## 3. Technology Stack

| Layer | Component | Version / Details |
|---|---|---|
| **Core Engine** | Java (JDK/JRE) | **Java 8 (1.8.0) Mandatory** |
| **GUI Framework** | Java Swing | FlatLaf 3.2.5 (Dark/Light/Ocean), MigLayout 3.7.3.1 |
| **Packet Capture** | JPCAP & Pure Java | Pure Java PCAP parser fallback |
| **Web Server** | Node.js & Express | Node.js 18+ / Express 4.18 |
| **Web Client** | React & Vite | React 18, Vite 5, Lucide Icons |
| **Target PBX** | Alcatel-Lucent OXE | OmniPCX Enterprise R101.1 n4.524.2 |
| **Reference Softphone** | MicroSIP | MicroSIP 3.22.16 with Windows Named Pipe IPC |

---

## 4. Why Java 8 is Mandatory

The core SIP engine, audio pipeline, and desktop GUI are strictly compiled with:
```cmd
javac -source 1.8 -target 1.8
```
**Rationale:**
1. **Telecom Appliance Compatibility:** Many on-premises PBX diagnostic consoles, maintenance laptops, and embedded field tools run validated Java 8 JRE environments where modern Java versions (Java 17/21+) are not permitted.
2. **Native Driver & DirectSound Stability:** Java 8 provides reliable DirectSound audio endpoint capture (`TargetDataLine` / `SourceDataLine`) without requiring modern module boundary exemptions (`--add-opens`).
3. **Standalone Portability:** The bundled JRE located in `deployment/jre` is a lightweight 32-bit Temurin JRE 8, enabling execution on fresh Windows workstations without installing system-wide JDKs.

---

## 5. Build and Execution Instructions

### Prerequisites
- Windows 10/11 (or Windows Server).
- Java 8 JDK (or higher with `-source 1.8 -target 1.8` support) available on `PATH`, or bundled `deployment/jre`.
- Node.js 18+ (for web dashboard only; bundled runtime available in `deployment/node`).

### Step-by-Step Build
1. **Compile Java Engine & Package JAR:**
   ```cmd
   build.bat
   ```
   *Compiles all source files into `build/`, copies scenarios/values/icons, and packages `SIP_Simulator_v4.jar`.*

2. **Compile React Web Client:**
   ```cmd
   npm --prefix web-app/client run build
   ```
   *Compiles frontend assets into `web-app/public/assets/`.*

### Launching the Application
- **Option A: Desktop Swing Application:**
  ```cmd
  run.bat
  ```
- **Option B: Web Dashboard & REST Daemon:**
  ```cmd
  start_dashboard.bat
  ```
  *Launches Express backend on port 3001 and opens browser at `http://localhost:3001`.*

---

## 6. Test Execution Instructions

The project provides two independent test harnesses:

### 1. Offline Regression Suite (Zero Network/PBX Dependencies)
```cmd
run_offline_tests.bat
```
- **Harness:** `test.OfflineFullRegressionSuite`
- **Coverage:** 14 test suites covering 80+ unit and integration tests.
- **Verification:** Validates codecs, SRTP, framing, scaling, call flows, multi-dialog isolation, Timer A/B, failure recovery, G.107 MOS calculation, DTMF encoding, and security credentials sanitization.
- **Duration:** ~12.5 seconds (100% PASS).

### 2. Live OXE PBX Integration Suite (Requires Live PBX & MicroSIP)
```cmd
run_oxe_live_tests.bat
```
- **Harness:** `test.OxePhase7FeatureValidationTest` & `test.OxeLiveConcurrentCallTest`
- **Target PBX:** Alcatel-Lucent OmniPCX Enterprise (`192.168.0.20:5060`)
- **Endpoints:** Extension `90008` (Simulator) $\leftrightarrow$ Extension `90001` (MicroSIP).
- **Verification:** Live registration (`Expires: 1800`), outbound call setup, bidirectional PCMA RTP audio, RFC 2833 DTMF, Re-INVITE Hold & Resume, secondary line seizure admission qualification, and zero-leak resource audit.

---

## 7. Current Development Phase & Roadmap

- **Completed Phases:**
  - **Phase 1A/1B/1C:** Desktop UI stability, high-DPI scaling adaptation, dynamic portability resolution (`AppPaths`).
  - **Phase 2:** TCP byte-stream framing (`SipStreamFramer`), buffer accumulation, headless TLS cert acceptance.
  - **Phase 3:** SDES cryptographic master key/salt parsing, bounded jitter buffer, initial Re-INVITE dialog tracking.
  - **Phase 4:** Production call engine (`SipCallEngine`), dual WAV capture, live OXE two-endpoint integration.
  - **Phase 5:** Multi-call orchestration, CPS rate control, RFC 3261 Timer A/B, CANCEL, E-Model MOS, failure injection.
  - **Phase 6:** Live concurrent OXE qualification, latency percentiles (p50/p95/p99), HTML reporting, offline regression harness.
  - **Phase 7:** SIP feature completeness (RFC 2833 & INFO DTMF, REFER transfer, Replaces, Early Media, Hold/Resume, Identity headers), React Phone tab, 14 offline regression suites.
- **Current Milestone:** **Phase 7 Completed & Validated.**
- **Next Milestone:** **Phase 8 — Advanced Protocol Extensions & Enterprise Interop.**

---

## 8. Known Limitations & Telecom Considerations

1. **OXE Single-Line Subscriber Profile:**
   Alcatel-Lucent OmniPCX Enterprise extension `90008` is provisioned as a single-line terminal (`Multi-line = False`). OXE rejects simultaneous outgoing line seizure with `488 Not Acceptable Here` (`cause=488;text="Outgoing call isn't allowed here"`). The simulator isolates this rejection cleanly without affecting active calls.
2. **OXE Registration Expiry:**
   OXE enforces `Min-Expires: 1800`. Registrations with shorter expiry return `423 Registration Too Brief`. The simulator uses 1800 seconds by default.
3. **OXE DTMF Signaling:**
   OXE accepts and relays in-band RFC 2833 / RFC 4733 telephone-events (PT 101). Out-of-band SIP INFO for DTMF is rejected with 488 by OXE subscriber lines.
4. **JPCAP 32-bit Architecture:**
   `lib/jpcap.jar` and `jpcap.dll` require a 32-bit JVM for live packet capture. For pure packet playback and SIP testing, the simulator includes a 100% pure Java PCAP reader that functions across all 32-bit and 64-bit architectures.
