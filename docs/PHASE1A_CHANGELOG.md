# SIP Simulator v4 — Phase 1A Changelog: Desktop Stability & Display Fixes

**Phase:** 1A (Desktop Stability & Display Fixes)  
**Date:** November 14, 2026  
**Git Branch:** `stability/feature-validation`  
**Status:** Completed & Validated  

---

## 1. Executive Summary

Phase 1A was executed strictly within the requested scope:
1. **Desktop Window Management & Sizing:** Replaced hardcoded dimensions with dynamic working area bounds respecting taskbar insets.
2. **High-DPI Scaling & Layout:** Relaxed rigid MigLayout constraints causing IP address, port number, and counter field clipping.
3. **Ultrawide Monitor Support:** Replaced rigid message tree width locks with responsive proportional layout.
4. **Targeted Process Lifecycle:** Completely eliminated `taskkill /F /IM java.exe`, implementing targeted PID tree termination and graceful shutdown handlers.
5. **Audio Lifecycle Investigation:** Verified that the Java audio engine cleanly releases 100% of audio lines, sockets, and threads with zero leaks during cooperative shutdowns.

**Critical Constraint Upheld:** No SIP execution logic, socket implementations, audio processing, RTP, PCAP, SIP parsing, TCP framing, or TLS code was modified.

---

## 2. Modified Files & Rationale

### 1. `web-app/server.js`
- **Location:** Lines 40–100, 230–265, 300–355, 1240–1245
- **Reason for Modification:**
  - `taskkill /F /IM java.exe` was previously executed whenever a test stopped or started, forcefully killing all `java.exe` instances machine-wide, orphaning active DirectSound hardware handles and locking the Windows Audio Service.
- **Changes Made:**
  - Removed `exec('taskkill /F /IM java.exe')`.
  - Implemented `activeProcesses` `Set` and `trackChildProcess(proc)` helper.
  - Replaced indiscriminate process killing with targeted PID tree termination: `taskkill /T /F /PID ${proc.pid}`. This terminates only the specific child process and its `cmd.exe` wrapper, leaving the parent Swing Desktop application (`SIP_Simulator_v4.jar`) and unrelated Java instances completely untouched.
  - Added process cleanup on Express server exit (`SIGINT`, `SIGTERM`, `exit`).

### 2. `src/sipinspector/SIPInspectorMainFrame.java`
- **Location:** Lines 10–25 (imports), Lines 245–255 (constructor), Lines 680–705 (Local Info), Lines 760–795 (Remote Server Info), Line 830 (MsgScrollPane), Lines 960–995 (Call generator fields), Lines 1270–1380 (Window management).
- **Reason for Modification:**
  - Hardcoded `1200x750` size caused window overflow on 1366x768 screens and 1080p @ 150% scaling, clipping the bottom navigation buttons (`Next >>`, `Run`) and status bar below the taskbar.
  - Rigid `width 200:200:` locked the SIP message tree on ultrawide monitors.
  - Rigid `w 50!`, `w 100!`, `w 40!` clipped IP digits, port numbers, and call counters under Windows DPI scaling.
- **Changes Made:**
  - Added `configureWindowBoundsAndPosition()`: queries `GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()`, calculates responsive 82% width (clamped to max 1380px) and 88% height (clamped to max 840px), ensures minimum size never exceeds screen bounds, and centers within the usable working area.
  - Added `saveWindowState()`: persists window bounds and maximized state to `settings.properties` on window close.
  - Relaxed `MsgScrollPane` MigLayout constraint to `"spany 2, grow, wmin 220, pref 260, hmin 120"`.
  - Relaxed `IPList` to `"grow, hmin 80, wmin 130"`.
  - Relaxed `LocalPort` and `RemoteServerPort` to `"wmin 65, pref 80, growx"`.
  - Relaxed `RemoteIPAddress` to `"growx, wmin 130"`.
  - Relaxed `cpsField`, `MaxConcurrentCallsField`, `totCalsField` to `"left, wmin 50, pref 60"`.

### 3. `src/sipinspector/GlobalSettings.java`
- **Location:** Lines 330–345, Lines 390–405
- **Reason for Modification:**
  - Provide persistent storage for user-adjusted window coordinates and maximized state.
- **Changes Made:**
  - Added persistence for `window.width`, `window.height`, `window.x`, `window.y`, and `window.maximized` in `saveSettings()` and `loadSettings()`.

### 4. `test/DisplayScalingValidationTest.java` (New Automated Suite)
- **Reason:**
  - Validate window sizing, taskbar margin, ultrawide clamping, and DPI scaling across 6 distinct screen configurations.

---

## 3. Validation & Regression Test Results

| Test Target | Execution Command | Result | Details |
|---|---|---|---|
| **Java 8 Build** | `cmd /c build.bat` | **PASS** | 187 classes compiled cleanly (`-source 1.8 -target 1.8`). `SIP_Simulator_v4.jar` generated. |
| **Middle-Tier Assertions** | `java -cp "build;lib/*" sipinspector.Utils.AssertionUnitTest` | **8/8 PASS** | All 8 scenario assertion tests passed. |
| **Offline Protocol Suite** | `java -cp "build;lib/*" test.OfflineValidationSuite` | **6/6 PASS** | Codecs, SRTP, SIP parser, and values loader verified. |
| **Live Audio Soak** | `java -cp "build;lib/*" sipinspector.Media.LiveAudioSoakValidationMain` | **20/20 PASS** | 20 rapid start/stop cycles: 0 thread leaks, 0 socket leaks, 0 audio line leaks. |
| **Display Bounds Suite** | `java -cp "build;lib/*" test.DisplayScalingValidationTest` | **6/6 PASS** | Verified across 1366x768, 1080p 100%, 1080p 150%, 1440p 125%, 3440x1440 ultrawide, 4K 200%. |
| **React Frontend Build** | `npm --prefix web-app/client run build` | **PASS** | Built in 437ms, zero errors. |
| **Express Backend REST API** | `node web-app/server.js` | **PASS** | Verified `/api/status` and `/api/test/stop` (clean exit without `taskkill /IM java.exe`). |

---

## 4. Compliance Verification

- [x] **Preserve existing functionality:** Zero changes to SIP scenario runner, socket classes, audio DSP, RTP, codecs, or SIP parser.
- [x] **No architectural rewrite:** Changes localized strictly to UI geometry and process lifecycle.
- [x] **Java 8 compatibility:** Build complies with `-source 1.8 -target 1.8`.
- [x] **Self-contained deployment:** No new external dependencies or Maven/Gradle plugins added.
- [x] **Git protection:** Working tree cleanly tracked on `stability/feature-validation` branch.
