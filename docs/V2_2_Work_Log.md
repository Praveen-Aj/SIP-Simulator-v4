# SIP Simulator v2.2 – Work Log

Root workspace: <project-root>
Start date: 2025-11-23

Scope
-----
This work log tracks all concrete actions taken for **v2.2**, focusing on:

- PCAP tone playback improvements (see `PCAP_Tone_Playback_Plan.md`).
- Live audio (microphone → RTP) feature (see `Live_Audio_v2_2_Plan.md`).

Each entry should include:

- Date/time
- Files touched
- Scripts run (build, installer, sync)
- Short description of what changed and why

Entries
-------

### 2025-11-23 – v2.2 planning setup

- Created `docs/V2_2_Work_Log.md` to capture all v2.2 steps in detail.
- Created `docs/V2_2_Roadmap.md` to map out v2.2 phases (PCAP engine, live audio, packaging, docs).
- Decision: keep current 32-bit JPcap + Npcap path as **legacy/optional**, but **do not** invest in a 64-bit JPcap stack.
- Decision: for v2.2 PCAP playback, implement a new **pure-Java (or non-WinPcap) PCAP RTP replayer** and make it the default path, with JPcap kept as fallback.

### 2025-11-23 – Phase 1 / Step A – JPcap playback refactor

- Files:
  - `src/sipinspector/Threads/PcapRtpPlayer.java` (new interface)
  - `src/sipinspector/Threads/RTPSenderThread.java`
- Changes:
  - Introduced `PcapRtpPlayer` abstraction so `RTPSenderThread` can delegate PCAP/RTP playback to pluggable engines.
  - Moved the existing JPcap-based RTP send loop into a nested `JpcapRtpPlayer` implementing `PcapRtpPlayer`.
  - Updated `RTPSenderThread.run()` to instantiate `JpcapRtpPlayer` and call `player.play(this)`; core logic, logging, and error handling remain functionally identical.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Phase 1 / Step B – pcap.engine setting

- Files:
  - `src/sipinspector/GlobalSettings.java`
  - `config/settings.properties` (runtime-generated)
- Changes:
  - Added `pcapEngine` field with `setPcapEngine` / `getPcapEngine` helpers.
  - Persist `pcap.engine` in `config/settings.properties` via `saveSettings()` / `loadSettings()`.
  - Default engine is `"jpcap"`; setting is also exposed via `GlobalSettings.getSetting("pcap.engine")` for convenience.

### 2025-11-23 – Phase 1 / Step C – Engine selection + stub pure player

- Files:
  - `src/sipinspector/Threads/RTPSenderThread.java`
  - `src/sipinspector/Threads/PurePcapRtpPlayer.java` (new)
- Changes:
  - Updated `RTPSenderThread.run()` to select the PCAP engine via `GlobalSettings.getPcapEngine()`:
    - `"pure"` → `PurePcapRtpPlayer`.
    - Any other value → `JpcapRtpPlayer` (legacy JPcap path, current default).
  - Implemented a stub `PurePcapRtpPlayer` that logs a clear debug message and shows a warning dialog indicating that the pure PCAP engine is not implemented yet and that `pcap.engine` should remain `jpcap` for now.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Phase 2 / Step D1 – Pure PCAP parser (header + packet loop)

- Files:
  - `src/sipinspector/Threads/PurePcapRtpPlayer.java`
- Changes:
  - Replaced the stub implementation with a real PCAP reader using `DataInputStream`.
  - Parse and log the PCAP global header (magic, endianness, version, snaplen, linktype).
  - Support only Ethernet link-layer (`network == 1`) for now; show a clear error if another link type is encountered.
  - Iterate packet records in a loop, reading per-packet headers and payload bytes, logging summary info for the first few packets.
  - Present an informational dialog summarizing how many packets were parsed; RTP send is intentionally **not** implemented yet.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Phase 2 / Step D2 – Ethernet/IPv4/UDP parsing

- Files:
  - `src/sipinspector/Threads/PurePcapRtpPlayer.java`
- Changes:
  - Enhanced the per-packet loop to parse Ethernet (type), IPv4 header (version/IHL, protocol, src/dst addresses), and UDP header (src/dst ports, length).
  - Count and log UDP packets separately from total packets, including source/destination and payload length for the first few UDP packets.
  - Left RTP payload parsing and actual send behavior for later steps; pure engine still only parses/logs.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Phase 2 / Step D3 – RTP send + timing semantics

- Files:
  - `src/sipinspector/Threads/PurePcapRtpPlayer.java`
  - `src/sipinspector/Threads/RTPSenderThread.java` (helper methods only)
- Changes:
  - Added `RTPSenderThread.getOrOpenSocket()` / `closeRtpSocket()` helpers so the pure PCAP engine can reuse the existing RTP socket lifecycle.
  - Extended `PurePcapRtpPlayer` to:
    - Open/reuse the RTP socket and send UDP payloads (assumed RTP) from each parsed UDP packet to all configured `RemoteClient` destinations.
    - Use PCAP timestamps to accumulate inter-packet delays and apply sleeps in batches based on `numRTPPackets`, mirroring the JPcap timing model.
    - Respect `threadStopped` to allow scenarios to halt playback cleanly.
  - Updated logging and the final informational dialog to reflect that the pure engine now actively replays RTP payloads instead of only parsing.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Phase 3 / Step E – continuousRTP looping + error behavior

- Files:
  - `src/sipinspector/Threads/PurePcapRtpPlayer.java`
- Changes:
  - Implemented `continuousRTP` semantics for the pure PCAP engine:
    - When EOF is reached and `continuousRTP == true` and the thread is not stopped, use a `BufferedInputStream` mark/reset to restart reading from the first packet, effectively looping the PCAP indefinitely until `threadStopped` is set.
    - Reset timing state (timestamp accumulator and counters) on each loop so inter-packet delays match the original capture timing for every iteration.
  - Kept error behavior simple and explicit:
    - Structural issues (bad magic, unsupported link type, invalid lengths) produce clear debug logs and user dialogs, then stop playback.
    - No automatic fallback to JPcap; switching engines remains a configuration choice via `pcap.engine`.
- Scripts run:
  - `build_and_run.bat` – build succeeded and application launched.

### 2025-11-23 – Planning note – Logging & analysis extensions

- Files:
  - `docs/V2_2_Roadmap.md`
- Changes:
  - Recorded future v2.2 extensions for logging and analysis:
    - Capture and store PCAP artifacts alongside existing SIP logs under the `logs` folder.
    - Extend the Logs tab to expose separate views for SIP logs and PCAP logs.
    - Evaluate bundling Wireshark (or its installer) with the SIP Simulator installer and document trade-offs.
  - No implementation yet; these items are scheduled as a later phase (Phase 7 – Logging & Analysis Enhancements).

(Next entries will be appended as v2.2 work progresses.)
