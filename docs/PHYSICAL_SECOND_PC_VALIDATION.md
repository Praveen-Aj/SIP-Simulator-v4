# Physical Second-PC Validation Report – Phase 1B Final Acceptance

**Date:** 2026-11-14  
**Project:** SIP Simulator v4  
**Git Branch:** `stability/feature-validation`  
**Commit Baseline:** `48d6e55` / `HEAD`  

---

## 1. Environment & Machine Inspection

A complete environment audit was performed to evaluate physical machine specifications and current permission levels:

| Parameter | Current System Value | Portability Assessment |
| :--- | :--- | :--- |
| **Hostname** | `HP-VICTUS` | Host machine identity verified |
| **Current User** | `Praveen` | Standard user space |
| **Operating System** | `Microsoft Windows 11 Home Single Language` (Build `10.0.26300`) | 64-bit Windows NT architecture |
| **Administrator Rights** | **False (Standard User)** | **VERIFIED: Zero elevated admin privileges used** |
| **Total Physical RAM** | 24,259,764 KB (~23.1 GB usable, 4.4 GB free) | Ample memory available |
| **Host System Java** | OpenJDK 21.0.9 (Microsoft-12574459) | Available for JDK source compilation |
| **Host System Node.js** | v24.14.1 | Available in system PATH |
| **Bundled JRE** | OpenJDK 1.8.0_472 (Temurin 32-bit in `deployment\jre\`) | **Verified independent runtime execution** |
| **Bundled Node.js** | Node.js v24.16.0 (in `deployment\node\`) | **Verified independent backend runtime** |

> [!IMPORTANT]
> **Physical Machine Boundary Clarification:**  
> The current workspace environment is running on the primary development computer (`HP-VICTUS`, user `Praveen`). To guarantee complete truthfulness:
> 1. Tests were executed on this system under **zero-administrator permissions** (`IsInRole(Administrator) == False`).
> 2. Zero-dependency runtime execution was verified by completely stripping `PATH` and `JAVA_HOME` (`$env:PATH = ""; $env:JAVA_HOME = ""`), proving the application functions entirely from bundled binaries.
> 3. Execution was verified from foreign working directories (`C:\Windows\Temp`) and copied project trees with spaces in the path (`C:\SIP Simulator Portability Test`).
> 4. If this session was intended to be run on a separate physical secondary laptop/desktop, that physical transfer must be initiated by copying the folder to that second machine and following the step-by-step physical validation checklist in Section 7.

---

## 2. Portable Runtime Validation

To rigorously validate cross-machine independence, the test suite was executed in an isolated process where `PATH` and `JAVA_HOME` were cleared:

```powershell
$env:PATH = "" ; $env:JAVA_HOME = "" ; & "deployment\jre\bin\java.exe" "-Djava.awt.headless=true" -cp "build;lib/*" test.PortabilityValidationTest
```

### Results:
```
=== Running SIP Simulator v4 Comprehensive Portability Validation Suite ===
[TEST 1] Application root detection: PASSED (Discovered: SIP Simulator_v4)
[TEST 2] Relative resource directories resolution: PASSED (scenarios, values, config, web-app all verified)
[TEST 3] Filesystem paths with spaces and separators: PASSED (Clean URI-decoded path: Outgoing basic call test space.tmp)
[TEST 4] Centralized configuration discovery: PASSED (Found settings.properties, loaded theme: Ocean Blue)
[TEST 5] Dynamic log directory discovery and write test: PASSED (Writable log directory: C:\Users\Praveen\Desktop\SIP Simulator logs)
[TEST 6] Java runtime binary resolution: PASSED (Resolved Java: C:\Users\Praveen\Downloads\Python Scripts\SIP Simulator_v4\jre\bin\java.exe)
[TEST 7] Node.js binary resolution: PASSED (Resolved Node: C:\Users\Praveen\Downloads\Python Scripts\SIP Simulator_v4\deployment\node\node.exe)
[TEST 8] System property app.home override: PASSED (Successfully honored -Dapp.home override)
[TEST 9] Working directory independence: PASSED (Resources strictly resolved against AppRoot, independent of caller CWD)
[TEST 10] Automatic on-demand directory creation: PASSED (Config and Logs directories verified active)

=================================================
PORTABILITY VALIDATION SUMMARY: 10 / 10 TESTS PASSED
=================================================
```

---

## 3. Build & Launch Verification

### 3.1 `build.bat` (Source Compilation)
- **Requirement:** Standard User permission, JDK 8+ on PATH or `JAVA_HOME`.
- **Command:** `cmd /c build.bat`
- **Result:** **PASS**. Clean build in 8.4 seconds; compiles Java 8 source files and packages `SIP_Simulator_v4.jar`.
- **Distinction:** Compilation requires a JDK (`javac.exe`), while execution requires only the bundled JRE.

### 3.2 `run.bat` (Application Launch)
- **Requirement:** Standard User permission, zero pre-installed Java.
- **Execution:** Launches using `deployment\jre\bin\java.exe`.
- **Result:** **PASS**. Process started and confirmed running with zero environment dependencies.

### 3.3 Foreign Working Directory Execution
- **Command:** `cd /d C:\Windows\Temp && "<ProjectDir>\run.bat"`
- **Result:** **PASS**. Batch script anchors working directory to `"%~dp0"`, ensuring proper resource discovery.

---

## 4. Desktop GUI Validation & Display Fixes

### 4.1 MigLayout Constraint Correction
During live GUI launch testing with the bundled JRE, an `IllegalArgumentException: Illegal Constraint: 'pref 80'` was caught from `MigLayout`:
- MigLayout 3.7 / 4.2 does not accept `pref <val>` as a standalone constraint; valid syntax is `w <min>:<pref>:<max>` (e.g., `w 65:80:`).
- Fixed in `SIPInspectorMainFrame.java`:
  - `LocalPort`: changed from `"wmin 65, pref 80, growx"` to `"w 65:80:, growx"`
  - `RemoteServerPort`: changed from `"wmin 65, pref 80, growx"` to `"w 65:80:, growx"`
  - `MsgScrollPane`: changed to `"w 220:260:, h 120:220:"`
  - Call parameters (`cpsField`, `MaxConcurrentCallsField`, `totCalsField`): changed to `"left, w 50:60:"`
- Recompiled and verified clean GUI initialization.

### 4.2 Display Scaling Across Profiles
Executed `test.DisplayScalingValidationTest`:
- 1366x768 @ 100%: PASS (Window: 1120x641)
- 1920x1080 @ 100%: PASS (Window: 1380x840)
- 1920x1080 @ 150%: PASS (Window: 1050x620)
- 2560x1440 @ 125%: PASS (Window: 1380x840)
- 3440x1440 Ultrawide: PASS (Window: 1380x840 clamped)
- 3840x2160 @ 200%: PASS (Window: 1380x840)

---

## 5. Web Dashboard Verification

1. **Express Server Launch:**
   - Started via `deployment\node\node.exe server.js` from `web-app/`.
   - Verified log: `Server is running on port 3001`.
2. **API Endpoint Verification:**
   - Queried `GET http://localhost:3001/api/status`.
   - Response: HTTP 200 OK returning parsed configuration (`version: 2.1`, `serversInfo`, `account`, `callGeneration`).
3. **Shutdown & Targeted Process Termination:**
   - Server lifecycle clean; only child processes spawned by the server are terminated. Unrelated Java processes are protected.

---

## 6. SIP Functionality Non-Production Validation

All SIP protocol parsing, scenario loading, and values mapping tests passed without errors:

1. **Scenario & Values Discovery:**
   - 41 scenarios verified in `scenarios/`.
   - 36 values files verified in `values/`.
   - `ValuesLoader` tested and verified token parsing (`[field0]`, `[field1]`, `[field2]`).
2. **SIP Parsing & Message Generation:**
   - `SIPParser` validated distinguishing Re-INVITE from initial INVITE and To-tag extraction.
   - Dual-format log parsing and timestamp stripping validated in `AssertionUnitTest`.
3. **Media & Codec Verification:**
   - G.711 A-law & u-law roundtrip: PASS
   - G.722 wideband codec roundtrip: PASS
   - SRTP AES_CM_128 HMAC-SHA1-80 encryption & authentication: PASS
   - ToneSynthesizer generation: PASS

---

## 7. Instructions for Physical Second-PC Validation

To repeat this validation on a separate physical computer:

1. **Copy Project Folder:**  
   Copy the `SIP Simulator_v4` folder to the target machine (e.g. `C:\SIP_Simulator_v4` or `D:\Tools\SIP Simulator_v4`).
2. **Execute Runtime Launch (No Java installed):**  
   Double-click `run.bat`. Verify the desktop UI appears immediately with Ocean Blue theme.
3. **Verify Settings & Scenarios:**  
   Confirm local IP addresses are enumerated in the dropdown and standard scenarios load.
4. **Launch Web Dashboard:**  
   Run `cd web-app && ..\deployment\node\node.exe server.js` and verify `http://localhost:3001/api/status`.
5. **Execute Test Verification (If JDK is available):**  
   Run `build.bat` followed by `java "-Djava.awt.headless=true" -cp "build;lib/*" test.PortabilityValidationTest`.
