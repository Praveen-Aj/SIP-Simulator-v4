# SIP Simulator v4 — Performance, Reliability & Multi-Call Testing Guide

## 1. Overview

SIP Simulator v4 features a production-grade telecom performance, multi-call orchestration, protocol compliance, and audio quality analysis engine. The platform is designed to execute controlled multi-call campaigns, measure signaling latency, verify media stream integrity under realistic network conditions, and calculate standardized MOS scores without overloading live PBX infrastructure.

---

## 2. Architecture & Components

```
+---------------------------------------------------------------------------------------+
|                                    React Web UI                                       |
|  - Real-time KPI Tiles (CPS, Latency, Loss, Jitter, MOS) | Interactive Campaign Setup |
|  - Live Call Stream Table | Failure Injection Trigger    | JVM Resource Telemetry     |
+-------------------------------------------+-------------------------------------------+
                                            | REST API / JSON
                                            v
+---------------------------------------------------------------------------------------+
|                                 Node.js Express Server                                |
|  - Endpoints: /api/perf/*, /api/health, /api/call/*                                   |
|  - Process Management & Reverse Proxy to Java Daemon (Port 5065)                      |
+-------------------------------------------+-------------------------------------------+
                                            | Loopback HTTP (127.0.0.1:5065)
                                            v
+---------------------------------------------------------------------------------------+
|                          CliCallEngine (Java 8 Daemon)                                |
+---------------------+-------------------------------+---------------------------------+
                      |                               |
                      v                               v
+-----------------------------------+   +-----------------------------------------------+
|     PerformanceTestEngine         |   |          FailureInjectionEngine               |
| - SINGLE_CALL, SEQUENTIAL         |   | - 404 Not Found (Invalid Destination)         |
| - CONCURRENT (Semaphore bounded)  |   | - 486 Busy Here (User Busy / Decline)         |
| - RATE_CONTROLLED (Target CPS)    |   | - 408 Request Timeout (Timer B Expiry)        |
| - LONG_DURATION (Soak Profiler)   |   | - CANCEL Call (Abandonment Before 200 OK)     |
| - E-Model G.107 MOS Calculation   |   | - Remote BYE (Inbound Teardown & 200 OK)      |
+---------------------+-------------+   +-----------------------+-----------------------+
                      |                                         |
                      +--------------------+--------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
|                              SipCallEngine & Media Stack                              |
| - RFC 3261 Signaling Engine (Timer A/B, CANCEL, Non-2xx ACK, Duplicate 200 OK/BYE)   |
| - Multi-Dialog State Isolation (Collision-Free Call-IDs: call<seq>_<ts>@<ip>)         |
| - Dynamic Non-Colliding RTP Port Allocator (Even ports 42000-50000, test-bound)       |
| - LiveAudioController & LiveAudioReceiverThread (RFC 3550 sequence arithmetic)        |
| - Dynamic Local IP Route Resolution (SipCallEngine.resolveLocalIpTowards)             |
+---------------------------------------------------------------------------------------+
```

---

## 3. Multi-Call Orchestration & Resource Isolation

### 3.1 Collision-Free Dialog Identifiers
To prevent race conditions during high-concurrency campaigns:
- **Call-ID Formulation:** `call<AtomicLong>_<System.currentTimeMillis>@<localIp>`
- **Via Branch:** `z9hG4bKinv<System.currentTimeMillis>_<AtomicLong>`
- **From Tag:** `tag<UniqueInteger>`

### 3.2 Dynamic RTP Port Allocation
RTP specifications require even ports with consecutive odd ports for RTCP. To guarantee zero collisions during rapid call setup/teardown cycles, `allocateRtpPort()` executes a test-bind loop:
```java
private int allocateRtpPort() {
    for (int i = 0; i < 500; i++) {
        int p = nextRtpPort.getAndAdd(2);
        if (p > 50000 || p < 42000) {
            nextRtpPort.set(42000);
            p = 42000;
        }
        try {
            java.net.DatagramSocket s = new java.net.DatagramSocket(p);
            s.close();
            return p;
        } catch (Exception ignored) {
            // Port currently in use, advance to next
        }
    }
    return nextRtpPort.getAndAdd(2);
}
```

### 3.3 Dynamic IP Resolution
The simulator dynamically resolves the host's active network adapter towards the PBX using the OS routing table (`SipCallEngine.resolveLocalIpTowards(remoteIp)`), eliminating hardcoded IP addresses.

---

## 4. Execution Modes

| Mode | Description | Typical Use Case |
|---|---|---|
| `SINGLE_CALL` | Executes 1 call, validates two-way RTP, and tears down. | Smoke testing, quick sanity check |
| `SEQUENTIAL` | Runs $N$ calls back-to-back with configurable inter-call pause. | Baseline regression, setup latency measurement |
| `CONCURRENT` | Maintains up to $C$ simultaneous active dialogs up to $N$ total calls. | Concurrency stress, channel capacity validation |
| `RATE_CONTROLLED` | Initiates calls at a targeted CPS (Calls Per Second) with concurrency throttling. | Capacity planning, rate limiting validation |
| `LONG_DURATION` | Single call active for extended duration (e.g., 5m, 15m, 30m) with periodic metric snapshots every 5s. | Soak testing, memory leak detection, media drift |

---

## 5. ITU-T E-Model G.107 MOS Calculation

The simulator calculates real-time and aggregate Mean Opinion Score (MOS) values based on the ITU-T Recommendation G.107 E-model:

$$R = R_0 - I_s - I_d - I_{e,\text{eff}} + A$$

### 5.1 Parameter Definitions
- **$R_0$ (Basic Signal-to-Noise Ratio):** Default $93.2$ for narrowband (G.711 PCMA/PCMU).
- **$I_s$ (Simultaneous Impairment):** Default $0$.
- **$I_d$ (Delay Impairment):** Derived from estimated one-way delay:
  $$\text{Delay} = 20\text{ ms (network)} + 2 \times \text{Jitter (ms)}$$
  $$\text{If Delay} > 177.3\text{ ms:} \quad I_d = 0.024 \times \text{Delay} + 0.11 \times (\text{Delay} - 177.3)$$
  $$\text{Else:} \quad I_d = 0.024 \times \text{Delay}$$
- **$I_{e,\text{eff}}$ (Effective Equipment Impairment):**
  $$I_{e,\text{eff}} = I_e + (95 - I_e) \times \frac{P_{\text{loss}}}{P_{\text{loss}} + B_{pl}}$$
  Where $I_e = 0$ (G.711 baseline), $B_{pl} = 19.0$ (robustness factor with ITU-T Appendix I Packet Loss Concealment), and $P_{\text{loss}}$ is packet loss percentage ($0-100\%$).
- **$A$ (Advantage Factor):** $0$ for wireline PBX integration.

### 5.2 R-Factor to MOS Transformation
$$\text{If } R \le 0: \quad \text{MOS} = 1.0$$
$$\text{If } 0 < R < 100: \quad \text{MOS} = 1.0 + 0.035 \times R + R \times (R - 60) \times (100 - R) \times 7 \times 10^{-6}$$
$$\text{If } R \ge 100: \quad \text{MOS} = 4.5$$

### 5.3 Audio Quality Benchmark Scale
| MOS Range | User Satisfaction | Network Quality |
|---|---|---|
| **4.3 – 4.5** | Very Satisfied | Pristine audio, $<1\%$ loss, $<20$ ms jitter |
| **4.0 – 4.2** | Satisfied | Good audio, $<3\%$ loss, $<40$ ms jitter |
| **3.5 – 3.9** | Some Users Dissatisfied | Fair quality, perceptible impairments |
| **2.5 – 3.4** | Many Users Dissatisfied | Poor quality, noticeable clipping or dropouts |
| **1.0 – 2.4** | Nearly All Users Dissatisfied | Unacceptable audio, severe packet loss or silence |

---

## 6. Controlled Failure Injection Scenarios

| Scenario | Trigger / Injection | Expected Protocol Behavior |
|---|---|---|
| `INVALID_DESTINATION_404` | Outbound INVITE to invalid extension (e.g. `99999`) | PBX returns `404 Not Found` or `488 Not Acceptable Here`; simulator transmits non-2xx `ACK`; session cleanly marked `FAILED`. |
| `BUSY_DESTINATION_486` | Inbound or outbound call rejected by user | Remote sends `486 Busy Here` / `603 Decline`; simulator transmits `ACK`; media sockets closed cleanly. |
| `TRANSACTION_TIMEOUT_408` | INVITE to non-responsive destination | Timer B ($64 \times T1 = 16\text{s}$, accelerated to $2\text{s}$ during automated tests) expires; state marked `FAILED` with `408 Request Timeout`; transaction terminated. |
| `CALL_CANCELLATION` | `engine.cancelCall(callId)` sent while in `CALLING` or `RINGING` | Simulator sends RFC 3261 `CANCEL`; remote returns `200 OK` to CANCEL and `487 Request Terminated` to INVITE; simulator returns `ACK` for 487; session marked `FAILED`. |
| `REMOTE_PARTY_BYE` | Active call terminated by remote party | Simulator receives inbound `BYE`; returns `200 OK`; stops audio streaming and recording; state marked `TERMINATED`. |

---

## 7. REST API Reference

The embedded daemon exposes these endpoints on port `5065` (proxied by the Express web server):

### Campaign Management
- **`POST /api/perf/start`**
  ```json
  {
    "name": "Production Load Test",
    "mode": "CONCURRENT",
    "caller": "90008",
    "callee": "90001",
    "totalCalls": 5,
    "maxConcurrent": 2,
    "callsPerSecond": 1.0,
    "durationSeconds": 5,
    "delayBetweenCallsMs": 500,
    "payloadType": 8,
    "autoAnswer": true
  }
  ```
- **`POST /api/perf/stop`**: Aborts running campaign and systematically hangs up active calls.
- **`GET /api/perf/status`**: Returns real-time campaign progress, active metrics, and latest results.
- **`GET /api/perf/results`**: Lists historical campaign JSON reports saved in `logs/perf_campaigns/`.

### Protocol & Anomaly Injection
- **`POST /api/perf/failure-test`**
  ```json
  {
    "scenario": "INVALID_DESTINATION_404",
    "caller": "90008",
    "callee": "90001"
  }
  ```
  Returns detailed execution report with socket and thread counts before and after to verify zero resource leakage.

### System & Health Monitoring
- **`GET /api/health`**: Returns engine status, active dialog counts, JVM memory usage (Used, Free, Max MB), thread count, and open media sockets.
- **`POST /api/call/cancel`**: `{"callId": "call1_...@"}` cancels an in-flight call prior to answer.

---

## 8. Safety & Live PBX Protection Guidelines

When conducting tests against a production OXE PBX:
1. **Never exceed safe concurrency limits:** Keep concurrent calls $\le 5$ channels unless authorized.
2. **Cap call rates:** Avoid setting CPS $> 2.0$ against live PBX hardware to prevent CPU exhaustion on the PBX call server.
3. **Never modify MicroSIP 90001 credentials:** Endpoint B must remain undisturbed on extension 90001.
4. **Always clean up active dialogs:** In the event of test interruption, call `POST /api/perf/stop` or invoke `MicroSipIpc.sendCommand("call.hangup", "{}")`.
