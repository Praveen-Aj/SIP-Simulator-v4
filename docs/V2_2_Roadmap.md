# SIP Simulator v2.2 – Roadmap

Root workspace: <project-root>
Date: 2025-11-23

Overview
--------

v2.2 focuses on **audio**:

1. Make **PCAP tone playback** reliable on modern systems without depending on fragile JPcap/Npcap native bindings.
2. Add **live audio** (microphone capture → RTP send) using the same RTP send pipeline.

This roadmap is a living document; phases may be refined as we learn more, but the overall sequence should remain stable.

Phase 1 – PCAP Engine Strategy & Design
---------------------------------------

Goals:

- Decide long-term PCAP approach and document the decision.
- Avoid further investment in 64-bit JPcap/Npcap chains.

Tasks:

- Confirm legacy path:
  - Keep existing 32-bit JPcap + Npcap with WinPcap API mode as a **legacy/fallback**.
  - Clearly document limitations and error signatures (e.g., `nativeOpenOffline` errors).
- Define new v2.2 PCAP architecture:
  - New **pure-Java PCAP RTP replayer** that reads our shipped tone PCAPs.
  - Scope limited to Ethernet + IPv4 + UDP + RTP for RBT/hold/DTMF/guide PCAPs.
- Update `PCAP_Tone_Playback_Plan.md` with final architecture notes and this decision.

Phase 2 – Implement Pure-Java PCAP RTP Replayer
-----------------------------------------------

Goals:

- Implement a new engine that can replay tone PCAPs without JPcap.
- Keep existing JPcap code in place as a fallback, but no longer required for basic tones.

Tasks:

- Add a new Java component (e.g., `PurePcapRtpPlayer` – final name to be chosen in code):
  - Read PCAP global header (endianness, link type).
  - Iterate packets: per-packet header → payload.
  - Parse Ethernet + IPv4 + UDP headers to locate RTP payload.
  - Use capture timestamps (or simplified timing) to schedule sends.
  - Send RTP payloads via existing UDP socket classes.
- Add unit/integration style tests where practical (e.g., internal test harness or debug logs) to verify:
  - File opens correctly.
  - Packet count and RTP SSRC/sequence/timestamp behave as expected.

Phase 3 – Integrate New Engine into RTPSenderThread / cmd_PlayPcap
------------------------------------------------------------------

Goals:

- Wire the new engine into the existing PCAP playback pathway without breaking current behavior.
- Provide a clean way to switch between legacy JPcap and the new engine.

Tasks:

- Identify integration points:
  - `RTPSenderThread` and the logic triggered by `cmd_PlayPcap`.
- Introduce a strategy/flag:
  - Config or static flag to toggle between **new PCAP engine** and **legacy JPcap**.
  - Default to **new engine** for v2.2, with legacy fallback available for troubleshooting.
- Ensure error handling and logging:
  - Clear messages when the new engine fails to parse a file.
  - Optional fallback to JPcap, or a user-visible error dialog with guidance.

Phase 4 – PCAP Testing, Validation, and Documentation
-----------------------------------------------------

Goals:

- Validate that all shipped tone PCAPs work reliably using the new engine.
- Document expected behavior and known limitations.

Tasks:

- Test all standard tone PCAPs under `pcap/`:
  - RBT, hold, guide tone, conversation, DTMF 0–9, `*`, `#`.
- Compare behavior (where possible) against the legacy JPcap path.
- Update docs:
  - `PCAP_Tone_Playback_Plan.md`
  - `VERSION_2_SUMMARY.md` (v2.2 section later)
  - Any user-facing docs that mention JPcap or Npcap requirements.

Phase 5 – Live Audio (Microphone → RTP)
---------------------------------------

Goals:

- Implement live audio capture and RTP send, aligned with `Live_Audio_v2_2_Plan.md`.
- Reuse as much of the RTP sender pipeline as possible.

High-level tasks (detailed in `Live_Audio_v2_2_Plan.md`):

- Design audio capture pipeline:
  - Choose Java audio API for microphone capture (e.g., `javax.sound.sampled`), respecting existing project constraints.
  - Decide on audio format (e.g., PCM 8kHz/16-bit) and mapping to RTP payload (PCMU/PCMA or raw PCM if acceptable).
- Implement an RTP sender for live audio:
  - Packetize captured audio into RTP frames compatible with current call flows.
  - Use existing transport stack (UDP/TCP/TLS where applicable).
- UI integration:
  - Minimal controls to start/stop live audio send for a selected call/endpoint.
  - Basic level/health indicators (e.g., simple text/log messages initially).
- Logging and diagnostics:
  - Extend debug logging so live audio sessions can be traced similarly to PCAP playback.

Phase 6 – Packaging, Installer, and Final v2.2 Docs
---------------------------------------------------

Goals:

- Ensure v2.2 build/EXE/installer include all new capabilities and docs.
- Keep the versioning and installer behavior consistent with the v2.1 rules.

Tasks:

- Versioning & builds:
  - Bump version constants from 2.1 → 2.2 in:
    - `SIPInspectorMainFrame` (About dialog).
    - `launch4j_config.xml` (file/product version).
    - `installer_script.iss` (`MyAppVersion`).
  - Rebuild JAR, EXE, and installer (`build_and_run.bat`, `2_create_exe.bat`, `5_create_installer.bat`).
- Docs:
  - Update `VERSION_2_SUMMARY.md` with v2.2 section (PCAP engine, live audio).
  - Update or add any user-facing docs that describe new audio behavior.
- Git and sync:
  - Commit changes with clear v2.2 messages.
  - Run `SYNC_TO_V3.bat` to mirror into `_v3` clone.

Phase 7 – Logging & Analysis Enhancements (Future v2.2 extension)
------------------------------------------------------------------

Goals:

- Improve troubleshooting and analysis around PCAP-based audio and SIP signaling.

Tasks:

- PCAP capture logging:
  - Add the ability to save selected PCAP traffic into the existing `logs` folder alongside current text logs (e.g., per-run PCAP files or extracts).
  - Ensure log naming and rotation are consistent with the existing logging strategy.
- Logs UI improvements:
  - Extend the existing Logs tab to expose two sub-views or tabs:
    - **SIP logs** – current textual SIP/log output.
    - **PCAP logs** – list or view of captured PCAP artifacts relevant to the run.
  - Keep UI changes minimal and aligned with current Swing layout.
- Wireshark integration (to evaluate):
  - Investigate whether to bundle Wireshark itself or its installer with the SIP Simulator installer.
  - Document size/licensing implications and decide if v2.2 should:
    - Bundle Wireshark,
    - Or simply provide a shortcut/guide to an external Wireshark install.

Out of Scope for v2.2
---------------------

- Major UI redesigns not directly related to PCAP/live audio/logging.
- New protocol features unrelated to audio (e.g., new SIP methods or templates) unless they are strictly required by the audio work.

(As v2.2 progresses, this roadmap can be refined, but new work should be mapped to one of these phases or explicitly marked as an extension.)
