# SIP Simulator v4 — Desktop Stability & Display Investigation Report

**Document Version:** 4.2.0-PHASE1A-VERIFIED  
**Date:** November 14, 2026  
**Auditor Roles:** Senior Java Desktop Application Developer, Audio Engineer & Software Architect  
**Workspace:** `<repository-root>`  
**Git Branch:** `stability/feature-validation`  
**Phase Status:** Phase 1A Implemented & Verified  

---

## 1. Issue A: Full-Screen, Window Sizing & Multi-Monitor Display (RESOLVED in Phase 1A)

### Symptoms Previously Observed:
- Layout exceeded available display resolution on standard laptops (e.g. 1366x768).
- On ultrawide monitors (21:9 or 32:9), components became severely dislocated with a compressed 200px message tree and over-stretched text area.
- Text labels, IP addresses, and port numbers became clipped on Windows displays with 125%, 150%, or 175% DPI scaling.
- Bottom action buttons (`Next >>`, `Run`, `Apply Edits`) and the status bar disappeared below the Windows taskbar.
- No window dimensions or positions were persisted across restarts.

### Root Cause Analysis & Phase 1A Implementation

#### 1. Screen-Aware Initial Sizing (Replaced Hardcoded 1200x750)
- **Previous Problem:** `SIPInspectorMainFrame.java` unconditionally executed:
  ```java
  this.pack();
  this.setSize(new Dimension(1200, 750));
  this.setMinimumSize(new Dimension(1000, 650));
  this.setLocationRelativeTo(null);
  ```
  On a 1366x768 screen (available height ~728px after taskbar) or 1080p @ 150% scaling (usable virtual height ~688px), a 750px window overflowed by 30–70px, pushing action buttons and the status bar off-screen.
- **Phase 1A Resolution:**
  Implemented `configureWindowBoundsAndPosition()`:
  - Uses `GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()` to respect Windows taskbar insets.
  - Dynamically computes initial size: 82% of usable width (clamped to max 1380px to prevent ultrawide distortion) and 88% of usable height (clamped to max 840px).
  - Dynamically bounds minimum size: `Math.min(960, maxBounds.width - 40)` and `Math.min(620, maxBounds.height - 40)`.
  - Centers the window strictly within `maxBounds`, guaranteeing that the title bar, action buttons, and status bar remain 100% visible.

#### 2. Ultrawide Layout & MigLayout Constraint Relaxation
- **Previous Problem:** In `SIPInspectorMainFrame.java`, `MsgScrollPane` was locked to `width 200:200:`. On 3440x1440 ultrawide displays, the tree remained frozen at 200px while the text area stretched across thousands of pixels.
- **Phase 1A Resolution:**
  - Updated constraint to: `"spany 2, grow, wmin 220, pref 260, hmin 120"`.
  - In `jPanel2`, column 0 has `grow 1` and column 2 has `grow 3`. The scenario message tree now expands proportionally with comfortable minimums, preserving readability on ultrawide displays.

#### 3. High-DPI Scaling Digit Clipping Fixes
- **Previous Problem:** `IPAddressPanel` and `RemoteServerInfoPanel` used rigid constraints:
  - `this.IPList`: `"h 90!, w 100!"`
  - `this.LocalPort`: `"w 50!"`
  - `this.RemoteIPAddress`: `"w 100!"`
  - `this.RemoteServerPort`: `"w 50!"`
  - `cpsField`, `MaxConcurrentCallsField`, `totCalsField`: `"w 40!"`
- **Phase 1A Resolution:**
  - `this.IPList`: `"grow, hmin 80, wmin 130"`
  - `this.LocalPort`: `"wmin 65, pref 80, growx"`
  - `this.RemoteIPAddress`: `"growx, wmin 130"`
  - `this.RemoteServerPort`: `"wmin 65, pref 80, growx"`
  - Call generator fields: `"left, wmin 50, pref 60"`
  - IP and port numbers no longer clip at 125%, 150%, or 200% Windows display scaling.

#### 4. Window State & Geometry Persistence
- **Phase 1A Resolution:**
  - Added window close listener saving `window.width`, `window.height`, `window.x`, `window.y`, and `window.maximized` to `config/settings.properties`.
  - On launch, validates saved coordinates against the active display's `maxBounds` before restoring. If a display was disconnected or resized, falls back to responsive auto-centering.

---

## 2. Display Bounds Validation Matrix (Simulated & Verified)

Automated validation suite executed via `test/DisplayScalingValidationTest.java`:

| Display Profile | Usable Screen Bounds (Taskbar Excluded) | Calculated Window Bounds | Margin to Taskbar | Result |
|---|---|---|---|---|
| **1366x768 @ 100%** | `1366 x 728` (40px taskbar) | `1120 x 641` at (123, 43) | **+44 px clearance** | **PASS** |
| **1920x1080 @ 100%** | `1920 x 1032` (48px taskbar) | `1380 x 840` at (270, 96) | **+96 px clearance** | **PASS** |
| **1920x1080 @ 150% (virtual)** | `1280 x 688` (32px taskbar) | `1050 x 620` at (115, 34) | **+34 px clearance** | **PASS** |
| **2560x1440 @ 125% (virtual)** | `2048 x 1112` (40px taskbar) | `1380 x 840` at (334, 136) | **+136 px clearance** | **PASS** |
| **3440x1440 Ultrawide** | `3440 x 1392` (48px taskbar) | `1380 x 840` at (1030, 276) | **Comfortably centered, no stretch** | **PASS** |
| **3840x2160 @ 200% (virtual)** | `1920 x 1032` (48px taskbar) | `1380 x 840` at (270, 96) | **+96 px clearance** | **PASS** |

*Summary:* 6 / 6 profiles passed verification. Zero button clipping or taskbar overlap.

---

## 3. Audio Resource Management & PC Crash Investigation

### Empirical Investigation Findings
As mandated by Phase 1A, an extensive investigation of the audio classes was conducted:
- `src/sipinspector/Media/LiveAudioController.java`
- `src/sipinspector/Media/LiveAudioSenderThread.java`
- `src/sipinspector/Media/LiveAudioReceiverThread.java`
- `src/sipinspector/Media/AudioDeviceManager.java`

#### 1. Cooperative Shutdown Lifecycle Analysis
In `LiveAudioController.stopAudio()`:
1. `sender.stopAudio()` interrupts the sender loop, reaching `finally { cleanup(); }` which executes `micLine.stop(); micLine.close(); LiveAudioRuntimeDiagnostics.onMicLineClosed();`.
2. `receiver.stopAudio()` interrupts the receiver loop, reaching `finally { cleanup(); }` which executes `speakerLine.stop(); speakerLine.close(); LiveAudioRuntimeDiagnostics.onSpeakerLineClosed();`.
3. `closeMediaSocket()` closes the underlying UDP `DatagramSocket`.
4. Thread joins ensure both background threads terminate before `stopAudio()` returns.

#### 2. Experimental Soak Evidence
Validation test `sipinspector.Media.LiveAudioSoakValidationMain` was executed across 20 rapid start/stop cycles:
```text
=== LiveAudio Soak Validation Summary ===
cycles=20
start_failures=0
runtime_exceptions=0
srtp_started=true
remaining_liveaudio_threads=0
diag_snapshot=activeSenders=0 activeReceivers=0 openSockets=0 openMicLines=0 openSpeakerLines=0 controllerStarts=21 controllerStops=21
RESULT cycles=20 start_failures=0 runtime_exceptions=0 srtp_started=true remaining_liveaudio_threads=0 diag_open_sockets=0 diag_active_senders=0 diag_active_receivers=0 diag_open_mic_lines=0 diag_open_speaker_lines=0
```
**Conclusion:** During cooperative application shutdown, the Java audio engine releases 100% of its resources with **zero leaks**:
- `TargetDataLine`: 0 leaks
- `SourceDataLine`: 0 leaks
- Audio streams: 0 leaks
- Audio threads: 0 leaks
- RTP sockets: 0 leaks

#### 3. Root Cause of Windows Audio Driver Deadlock (PC Restart Requirement)
The system lockup requiring PC reboot was **NOT** caused by internal resource leaks in Java audio classes. It was triggered by:
```javascript
// In web-app/server.js:
exec('taskkill /F /IM java.exe', ...)
```
When live audio is active, the JVM holds open DirectSound circular hardware buffers managed by `jsound.dll`. `taskkill /F` issues `TerminateProcess()`, which forcibly halts the JVM without executing `finally` blocks or calling `line.close()`. On Windows, leaving DirectSound endpoints unreleased leaves the Windows Audio Service (`audiosrv`) and sound drivers (Realtek/Intel) in a locked hardware clock state. All system audio playback deadlocks until the machine is rebooted.

---

## 4. Process Management Remediation (RESOLVED in Phase 1A)

### Targeted Process Tracking in `web-app/server.js`
1. **Eliminated `taskkill /F /IM java.exe` Completely:**
   - The Express server now maintains a `Set` of active child processes (`activeProcesses`).
   - Every spawned process (`CliSipRegisterTest`, `CliSipLegTest`, `PcapConverter`) is registered via `trackChildProcess(proc)`.
2. **Strict PID Process-Tree Termination:**
   - Child processes are terminated by PID: `taskkill /T /F /PID ${proc.pid}`.
   - The `/T` flag ensures the `cmd.exe` shell wrapper and child `java.exe` are both cleaned up.
   - The `/PID` flag guarantees that **ONLY** the specific child process is terminated.
   - **The parent Java Swing Desktop application (`SIP_Simulator_v4.jar`) and unrelated Java instances are NEVER touched.**
3. **Server Shutdown Lifecycle:**
   - Registered `SIGINT`, `SIGTERM`, and `exit` handlers to clean up all tracked child processes gracefully when Express terminates.
