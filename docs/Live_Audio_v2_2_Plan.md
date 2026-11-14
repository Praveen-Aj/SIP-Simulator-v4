---
description: Plan for v2.2 live two-way audio (mic + speakers)
---

# SIP Simulator v2.2 – Live Two-Way Audio Plan

## 1. Purpose and Scope

Add an optional "live audio" mode that allows:

- **Hear remote media**: play incoming RTP from the configured Remote RTP endpoint on the local speakers.
- **Send local audio**: capture audio from the system microphone and send it as RTP towards the remote endpoint.

Constraints:

- Do **not** change existing SIP signaling behavior or PCAP tone playback.
- Feature must be **off by default** and explicitly enabled by the user.
- Keep implementation modular so it can be disabled or omitted from certain builds if needed.

## 2. Requirements

### 2.1 Functional

- **F1 – Receive path**: decode RTP payloads (initially G.711 µ-law / PCMU) received on the media port and play as PCM audio on system speakers.
- **F2 – Send path**: capture PCM audio from a selected microphone device and encode to G.711 µ-law RTP, sending to the configured Remote RTP IP:port.
- **F3 – Call coupling**: live audio must be startable only when a scenario has negotiated media (e.g. after 200 OK/ACK) and Remote RTP is valid.
- **F4 – Controls**: UI controls to start/stop live audio for a running scenario.
- **F5 – Safety**: clearly indicate when microphone is active (status bar / icon / text), and ensure microphone is stopped on scenario stop/close.

### 2.2 Non-functional

- **N1 – Latency**: aim for one-way audio latency under ~250 ms on LAN.
- **N2 – Robustness**: tolerate packet loss and jitter (basic jitter buffer; no need for full VoIP-grade PLC in v1).
- **N3 – Compatibility**: work with existing G.711-based endpoints used for testing (PBX, softphones like MicroSIP, X-Lite equivalents).

## 3. High-Level Architecture

### 3.1 New Modules / Classes

- `sipinspector.Media.AudioFormatUtils`
  - Helpers for PCM <-> G.711 µ-law conversions.
- `sipinspector.Media.LiveAudioSenderThread`
  - Captures PCM frames from JavaSound, encodes to G.711, sends over RTP to Remote RTP.
- `sipinspector.Media.LiveAudioReceiverThread`
  - Receives RTP packets on media port, decodes G.711 to PCM, pushes to a playback line.
- `sipinspector.Media.AudioDeviceManager`
  - Enumerate/select input/output devices, configure sample rate (8 kHz mono) and buffer sizes.
- `sipinspector.Media.LiveAudioController`
  - High-level start/stop logic, wires ScenarioProgressDialog / SIPInspectorMainFrame to sender/receiver threads.

### 3.2 Integration Points

- **GlobalSettings / Options UI**
  - Add settings for:
    - Live audio enabled/disabled.
    - Preferred input device.
    - Preferred output device.
  - Defaults: disabled, system default devices.

- **ScenarioProgressDialog**
  - Expose hooks to start/stop live audio tied to a running scenario.
  - Possible minimal approach: toolbar button or checkbox "Live audio" that calls into `LiveAudioController`.

- **Remote RTP configuration**
  - Reuse existing Options → Remote RTP settings.
  - Live audio sends to the same IP:port as used by PCAP playback.

## 4. Media Path Details

### 4.1 Audio formats

- Use PCM 16-bit, mono, 8000 Hz for internal representation (matches standard G.711).
- Initial codec support:
  - **G.711 µ-law (PCMU, payload type 0)** – required.
  - A-law could be added later if environment needs it.

### 4.2 Sender Path (Mic → RTP)

1. Open JavaSound `TargetDataLine` for mic (16-bit PCM, 8 kHz, mono).
2. Read fixed-size frames (e.g. 20 ms = 160 samples).
3. Convert PCM to 8-bit µ-law samples.
4. Pack samples into RTP packets:
   - Set SSRC, sequence, timestamp.
   - Payload type 0.
5. Send via UDP socket bound to Local media port, destination = Remote RTP IP:port.
6. Repeat until stopped.

### 4.3 Receiver Path (RTP → Speakers)

1. Receive RTP packets on Local media port (can share logic with existing RTP sender/RemoteClient list, or separate UDP socket).
2. Filter packets belonging to the current SSRC/payload type 0.
3. Apply simple jitter buffer (queue sorted by sequence, small playout delay).
4. Decode µ-law payload to PCM.
5. Write PCM frames to JavaSound `SourceDataLine` for speakers.

## 5. UI Design Sketch

- **Options → Live Audio** tab/section:
  - Checkbox: `Enable live two-way audio (experimental)`.
  - Dropdowns: `Input device`, `Output device`.
- **Scenario Progress dialog**:
  - Button or toggle: `Live Audio` (ON/OFF).
  - When ON:
    - Start sender + receiver threads via `LiveAudioController`.
  - When OFF or on scenario stop/close:
    - Stop threads and release audio devices.

## 6. Phased Implementation Plan

### Phase 0 – Prototype (local-only, no UI)

- Implement `AudioFormatUtils` (PCM ↔ µ-law).
- Implement a console-based sender/receiver pair to validate real-time audio between two ports on the same machine.
- Measure latency and basic quality.

### Phase 1 – One-way receive (listen-only)

- Integrate `LiveAudioReceiverThread` with Remote RTP configuration.
- Add minimal control (e.g. hidden option or debug menu) to start receiver for a running call.
- Validate that media from external phone can be heard on speakers.

### Phase 2 – Full two-way audio

- Implement `LiveAudioSenderThread` and `AudioDeviceManager`.
- Wire into Options and ScenarioProgressDialog UI.
- Add clear mic-active indication.

### Phase 3 – Hardening & polish

- Basic jitter buffer tuning and buffer size adjustments.
- Error handling (device unavailable, port in use, etc.).
- Configuration persistence in `userSettings.xml`.
- Optional: logging hooks via `TLSDebugLogger` for troubleshooting (e.g. frame rates, packet loss counters).

## 7. Risks and Open Questions

- **JRE and OS differences**: JavaSound device enumeration and behavior may vary across Windows versions.
- **Codec compatibility**: if remote offers codecs other than G.711, live audio will still send/receive G.711 – this is acceptable for lab/testing, but must be documented.
- **RTP multiplexing**: need to ensure coexistence with PCAP playback; only one of PCAP or live audio should send on a given media port at a time.

## 8. Out-of-Scope for v2.2

- Wideband codecs (G.722, Opus, etc.).
- Advanced echo cancellation or noise suppression.
- Recording conversations to file.

This document is a planning artifact only; actual implementation should follow these phases and be done on a dedicated v2.2 branch.
