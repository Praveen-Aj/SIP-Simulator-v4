# SIP Simulator v4 — Desktop Stability & Display Investigation Report

**Document Version:** 4.1.0-STABILITY  
**Date:** November 2026  
**Auditor Roles:** Senior Java Desktop Application Developer & Software Architect  
**Workspace:** `c:\Users\Praveen\Downloads\Python Scripts\SIP Simulator_v4`  
**Git Branch:** `stability/feature-validation`

---

## 1. Issue A: Full-Screen, Window Sizing & Multi-Monitor Display Investigation

### Symptoms Observed:
- Layout exceeds available display resolution on standard laptops.
- On ultrawide monitors (21:9 or 32:9), components become severely dislocated or stretched with massive blank gaps.
- Text labels, port numbers, or tree entries become clipped or truncated on Windows displays with 125%, 150%, or 175% DPI scaling.
- Bottom action buttons (`Next >>`, `Run`, `Apply Edits`) and the `EnhancedStatusBar` can become pushed off-screen below the Windows taskbar.

### Root Cause Analysis

#### 1. Hardcoded Initial Dimensions vs Available Screen Insets
In [SIPInspectorMainFrame.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/SIPInspectorMainFrame.java#L1268-L1271):
```java
this.pack();
this.setSize(new Dimension(1200, 750));
this.setMinimumSize(new Dimension(1000, 650));
this.setLocationRelativeTo(null);
```
- **The Problem:** The application hardcodes an initial window size of **1200 x 750 pixels** and centers it on the screen without querying the screen's actual working area (excluding taskbar).
- **Impact on 1366x768 Laptops:** A standard 768p laptop display with a 40px Windows taskbar has an available vertical height of only **728 pixels**. A 750px window plus title bar (30px) requires **780 pixels**. Consequently, the window overflows the screen by ~50 pixels, pushing the bottom navigation buttons (`Next >>`, `Run`) and `EnhancedStatusBar` completely off-screen.
- **Impact on 1080p Laptops with 150% DPI Scaling:** At 150% scaling, 1080p yields an effective virtual height of **720 pixels** (1080 / 1.5). The 750px window immediately overflows.

#### 2. Ultrawide Stretching & Rigid Constraint Collisions
In [SIPInspectorMainFrame.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/SIPInspectorMainFrame.java#L816-L830):
```java
jPanel2.setLayout(new MigLayout("fill, insets 10", "[grow 1] [] [grow 3] []", "[] [] [] [] [grow] []"));
jPanel2.add((Component) this.MsgScrollPane, "spany 2, grow, width 200:200:, height 100:220:");
```
- **The Problem:** `MsgScrollPane` (the SIP Message tree on the left of the Scenario Editor) has a locked constraint: `width 200:200:`. Meanwhile, column 2 has `[grow 3]`.
- **Impact on Ultrawide Displays (e.g., 2560x1080, 3440x1440, 5120x1440):** When maximized on an ultrawide screen, the message tree remains locked to a narrow 200px column while the right-hand text editor stretches across 2000–3000 pixels. Text becomes difficult to read across the wide expanse, and arrow alignment breaks visually.

#### 3. Strict Pixel Constraints under Windows DPI Scaling
In [SIPInspectorMainFrame.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/SIPInspectorMainFrame.java#L688-L700):
```java
this.IPAddressPanel.add((Component) this.IPList, "h 90!, w 100!");
this.LocalInfoPanel.add((Component) this.IPAddressPanel, "w 50%!, spany 2");
this.IPPortPanel.add((Component) this.LocalPort, "w 50!");
this.LocalInfoPanel.add((Component) this.IPPortPanel, "w 50%, wrap");
```
- **The Problem:** The exclamation marks `!` in MigLayout (`w 50!`, `w 100!`, `h 90!`) enforce **rigid, unscalable physical pixel limits**.
- **Impact:** At 150% or 200% Windows display scaling, fonts scale up, but `w 50!` keeps the text field strictly 50 pixels wide. A 4-digit port number (`5060`) with larger font requires 65–80 pixels, resulting in clipped digits and truncated IP addresses in `IPList`.

#### 4. Total Absence of Window Position & Size Persistence
The application does not persist window bounds or maximized state in `userSettings.xml` or `config/settings.properties`. Every single launch unconditionally forces the window back to `1200 x 750` at the center of the primary display, ignoring the user's monitor preference in multi-monitor setups.

---

### Recommended Display Fixes (Minimal Risk, High Impact)

1. **Intelligent Screen-Aware Initial Sizing:**
   Query the default screen device's `GraphicsConfiguration` and subtract taskbar insets:
   ```java
   GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
           .getDefaultScreenDevice().getDefaultConfiguration();
   Rectangle screenBounds = gc.getBounds();
   Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
   int usableWidth = screenBounds.width - insets.left - insets.right;
   int usableHeight = screenBounds.height - insets.top - insets.bottom;

   int initialWidth = Math.min(1200, (int) (usableWidth * 0.90));
   int initialHeight = Math.min(750, (int) (usableHeight * 0.90));
   this.setSize(new Dimension(initialWidth, initialHeight));
   this.setMinimumSize(new Dimension(Math.min(950, usableWidth), Math.min(600, usableHeight)));
   this.setLocation(screenBounds.x + (usableWidth - initialWidth) / 2,
                    screenBounds.y + (usableHeight - initialHeight) / 2);
   ```
2. **DPI-Adaptive MigLayout Constraints:**
   Replace rigid `w 50!` and `w 100!` with scalable minimum/preferred bounds:
   - Replace `"w 50!"` with `"wmin 60, growx"`.
   - Replace `"width 200:200:"` with `"width 220:260:350"`.
3. **Window State Persistence:**
   Save `window.x`, `window.y`, `window.width`, `window.height`, and `window.maximized` to `config/settings.properties` on window close, and restore them on startup if within valid screen bounds.

---

## 2. Issue B: Application Crashes and System Recovery (Why PC Restart Was Required)

### Root Cause Analysis

#### 1. PRIMARY CULPRIT: Windows Sound Subsystem Lockup via `taskkill /F /IM java.exe`
- **Source File:** [web-app/server.js](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/web-app/server.js#L56)
- **The Code:**
  ```javascript
  function killActiveProcesses() {
      // ...
      return new Promise((resolve) => {
          exec('taskkill /F /IM java.exe', (err, stdout, stderr) => {
              resolve();
          });
      });
  }
  ```
- **How It Causes PC Lockup:**
  1. The Java application's media engine ([LiveAudioSenderThread.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/Media/LiveAudioSenderThread.java) and [LiveAudioReceiverThread.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/Media/LiveAudioReceiverThread.java)) opens hardware lines from the Windows Audio mixer (`TargetDataLine` for mic, `SourceDataLine` for speaker) using the JRE's native `jsound.dll` DirectSound/WASAPI audio subsystem.
  2. When the user stops a test in the Web UI, or when `/api/stream/register` or `/api/stream/call` starts, `killActiveProcesses()` executes `taskkill /F /IM java.exe`.
  3. `taskkill /F` issues the Windows Win32 API call `TerminateProcess()`. This **instantly terminates the JVM at the OS kernel level**. No Java shutdown hooks run, no `finally` blocks execute, and `micLine.close()` / `speakerLine.close()` are **NEVER called**.
  4. On Windows, terminating a process that holds an open DirectSound or WASAPI stream leaves the Windows Audio Service (`audiosrv.dll`) and vendor sound card drivers (Realtek HD Audio, Intel Smart Sound, Conexant) in an **unreleased, locked hardware streaming state**.
  5. Subsequent audio operations fail with `LineUnavailableException`. YouTube, web browser video, system sounds, and music players buffer indefinitely waiting for the audio hardware clock. The Windows Audio service cannot be restarted via `services.msc` because the kernel audio driver thread is blocked. **The only recovery is a complete PC reboot.**

#### 2. SECONDARY CULPRIT: Jpcap Native Library (`jpcap.dll`) & Npcap Kernel Resource Leaks
- **Configuration:** `config/settings.properties` defaults to `pcap.engine=jpcap`.
- **Mechanism:** `JpcapRtpPlayer` loads the 32-bit native library `lib/jpcap.dll` to interface with WinPcap/Npcap kernel drivers for packet injection.
- **Impact:** If `taskkill /F` or an unhandled crash occurs while Npcap has open packet capture handles, kernel non-paged pool memory and adapter filter bindings can leak, causing network adapters to drop packets or stop routing until reboot.

#### 3. TERTIARY CULPRIT: Swing Event Dispatch Thread (EDT) Deadlocks on Socket Connect
- **Source File:** [SIPInspectorMainFrame.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/SIPInspectorMainFrame.java#L1429-L1433)
- **The Code:**
  ```java
  if (this.mySocket == null) {
      ok = this.openSocket();
  }
  ```
- **Mechanism:** `openSocket()` performs TCP socket channel connection and TLS handshakes (`sslSocket.startHandshake()`) synchronously on the Swing EDT. If the remote PBX (e.g. `192.168.0.70`) drops packets, the GUI completely freezes and stops repainting. Users perceive the app as dead and terminate it via Task Manager (`End Task`), which also calls `TerminateProcess()`, triggering the audio driver leak described in Culprit 1.

#### 4. QUATERNARY CULPRIT: Modal GUI Dialogs in Headless CLI Subprocesses
- **Source File:** [TLSClientSocket_V6.java](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/src/sipinspector/Socket/TLSClientSocket_V6.java#L149-L170)
- **The Code:**
  ```java
  JOptionPane.showMessageDialog(null, "TLS connection timeout...", "TLS Timeout", JOptionPane.ERROR_MESSAGE);
  ```
- **Mechanism:** When Express spawns `CliSipLegTest` or `CliSipRegisterTest` in the background, if TLS fails, it invokes `JOptionPane.showMessageDialog()`. In a background process, this dialog either throws a headless error or pops up an invisible modal dialog, causing the spawned `java.exe` process to hang indefinitely. Over time, dozens of orphaned `java.exe` processes accumulate, exhausting memory, ports, and handles.

---

## 3. Reproduction Steps & Evidence

| Failure Scenario | Step-by-Step Reproduction | Observable Result |
|---|---|---|
| **System Audio Freeze** | 1. Start a Two-Way Call from Web UI (`/api/stream/call`).<br/>2. Allow audio streaming to begin.<br/>3. Click "Stop Test" in Web UI (triggers `taskkill /F /IM java.exe`). | Java GUI crashes; Windows audio device remains locked; browsers cannot play audio until reboot. |
| **GUI Freeze on Bad PBX** | 1. In Settings, select TCP or TLS transport.<br/>2. Set Remote IP to an unreachable address (e.g., `192.168.254.254`).<br/>3. Click "Next >>" or "Run". | Swing UI freezes solid for 10+ seconds; window title displays "(Not Responding)". |
| **Orphaned Background JVMs** | 1. Run 5 consecutive campaign tests with TLS enabled.<br/>2. Induce handshake failure.<br/>3. Check Windows Task Manager. | 5+ hidden `java.exe` processes linger in Task Manager holding TCP ports and memory. |

---

## 4. Proposed Fixes & Safety Guidelines

1. **Eliminate `taskkill /F /IM java.exe` Immediately:**  
   Replace with targeted PID tracking using Node's `child.pid` and `taskkill /F /PID <pid> /T`. Never kill all `java.exe` processes globally.
2. **Graceful Audio Line Shutdown:**  
   Ensure that `LiveAudioController.stopAudio()` is always called prior to process exit, and register a clean JVM runtime shutdown hook in `Main.java` and `CliSipLegTest.java` that invokes `micLine.close()` and `speakerLine.close()`.
3. **Guard Sockets Against GUI Calls in Headless Mode:**  
   Check `GraphicsEnvironment.isHeadless()` before showing `JOptionPane`, logging errors to `TLSDebugLogger` instead.
4. **Move Socket Open Operations off the Swing EDT:**  
   Execute `openSocket()` inside a background `SwingWorker` thread with a progress modal and cancellation support.
5. **Prefer Pure Java PCAP Engine by Default:**  
   Change default setting in `config/settings.properties` to `pcap.engine=pure` to prevent dependency on native `jpcap.dll` and Npcap kernel drivers.
