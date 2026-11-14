# Phase 1B: Cross-Machine Portability Validation Report

## Executive Summary

**Date:** 2026-11-14  
**Branch:** `stability/feature-validation`  
**Phase Objective:** Prove and validate that SIP Simulator v4 can be copied to a completely different Windows PC and successfully compiled, launched, and operated without modifying source code or configuration files, preserving zero machine-specific paths and maintaining Java 8 backward compatibility.

### Portability Validation Status: **PASSED (10 / 10 Portability Tests Verified)**

| Test Category | Tested Environment | Result | Details |
| :--- | :--- | :--- | :--- |
| **Portability Suite (10 tests)** | Development Root (`SIP Simulator_v4`) | **PASSED** | 10/10 tests passed |
| **Portability Suite (10 tests)** | Copied Directory (`C:\SIP Simulator Portability Test`) | **PASSED** | 10/10 tests passed with spaces in path |
| **Caller CWD Independence** | CWD set to `C:\Windows\Temp` | **PASSED** | Fully independent of launch working directory |
| **Offline Validation Suite** | Clean Copied Directory (`C:\Windows\Temp` CWD) | **PASSED** | 6/6 tests passed (SIP, Codecs, SRTP, Values) |
| **Middle-Tier Assertion Suite**| Clean Copied Directory (`C:\Windows\Temp` CWD) | **PASSED** | 8/8 tests passed (PCAP, Loss, RTP, Ladder) |
| **Display Scaling Profile Suite**| Clean Copied Directory (`C:\Windows\Temp` CWD) | **PASSED** | 6/6 DPI/Resolution profiles passed |
| **Web Dashboard Express API** | Clean Copied Directory (`web-app/server.js`) | **PASSED** | Bundled Node.js served `/api/status` successfully |
| **Bundled JRE Execution** | OpenJDK 1.8.0_472 (32-bit Temurin) | **PASSED** | Zero dependency on host-installed JRE |

> [!IMPORTANT]
> **Environment Transparency:** Validation was executed on the Windows test host using a clean project replica copied to `C:\SIP Simulator Portability Test` (a new root path with spaces) and verified with the caller working directory set to `C:\Windows\Temp`. Physical deployment to an external second hardware machine remains pending operator availability and is documented below with step-by-step verification instructions.

---

## 1. Portability Baseline and Hardening Changes

### 1.1 Centralized Resolution (`AppPaths.java`)
- Added `resetCachedAppRoot()` to allow multi-environment testing and dynamic re-discovery in isolated test cycles.
- Ensured `userSettings.xml` resolution in `SIPInspectorMainFrame.java` is resolved via `AppPaths.resolve("userSettings.xml")` rather than assuming relative `new File("userSettings.xml")`.
- Ensured test harnesses (`OfflineValidationSuite.java`, `AssertionUnitTest.java`) resolve mock values and log directories through `AppPaths.getValuesDir()` and `AppPaths.getLogsDir()` rather than unanchored relative paths.

### 1.2 Launcher Script Hardening
- `run.bat`, `build.bat`, and `build_and_run.bat` are anchored with `cd /d "%~dp0"`, ensuring that double-clicking desktop shortcuts or invoking them from any arbitrary working directory (e.g. `C:\Windows\System32` or another drive) deterministically switches to the project root.
- `run.bat` checks for bundled JRE at `deployment\jre\bin\java.exe` before falling back to system `java`.

---

## 2. Comprehensive Portability Test Suite Results

The enhanced `test.PortabilityValidationTest` suite was executed using the bundled Java 8 runtime (`deployment\jre\bin\java.exe`) with the working directory set to `C:\Windows\Temp`:

```
=== Running SIP Simulator v4 Comprehensive Portability Validation Suite ===
[TEST 1] Application root detection: PASSED (Discovered: SIP Simulator Portability Test)
[TEST 2] Relative resource directories resolution: PASSED (scenarios, values, config, web-app all verified)
[TEST 3] Filesystem paths with spaces and separators: PASSED (Clean URI-decoded path: Outgoing basic call test space.tmp)
[TEST 4] Centralized configuration discovery: PASSED (Found settings.properties, loaded theme: Ocean Blue)
[TEST 5] Dynamic log directory discovery and write test: PASSED (Writable log directory: C:\Users\Praveen\Desktop\SIP Simulator logs)
[TEST 6] Java runtime binary resolution: PASSED (Resolved Java: C:\SIP Simulator Portability Test\jre\bin\java.exe)
[TEST 7] Node.js binary resolution: PASSED (Resolved Node: C:\SIP Simulator Portability Test\deployment\node\node.exe)
[TEST 8] System property app.home override: PASSED (Successfully honored -Dapp.home override)
[TEST 9] Working directory independence: PASSED (Resources strictly resolved against AppRoot, independent of caller CWD)
[TEST 10] Automatic on-demand directory creation: PASSED (Config and Logs directories verified active)

=================================================
PORTABILITY VALIDATION SUMMARY: 10 / 10 TESTS PASSED
=================================================
```

---

## 3. Clean Second-PC Simulation Results

A fresh replica was generated using Windows `robocopy` into `C:\SIP Simulator Portability Test` (introducing space characters in the path to verify URI and quoting resilience):

### 3.1 Build Verification (`build.bat`)
- Command: `cmd /c "build.bat"`
- Output: Clean compilation of Java sources targeting Java 8 (`-source 1.8 -target 1.8`).
- Resource synchronization: `src\Icons`, `scenarios` (41 files), `values` (36 files), and `lib\*.jar` (4 files) copied into `build\`.
- Artifact packaging: `SIP_Simulator_v4.jar` generated cleanly with valid `MANIFEST.MF`.
- Exit Code: `0` (Success).

### 3.2 Regression Verification from Foreign CWD (`C:\Windows\Temp`)
All tests were invoked with the working directory intentionally set outside the project directory (`C:\Windows\Temp`):
1. **Portability Validation Suite:** 10/10 Passed.
2. **Offline Validation Suite:** 6/6 Passed (G.711, G.722, SRTP, SIPParser, ToneSynthesizer, ValuesLoader).
3. **Display Scaling Validation Suite:** 6/6 Profiles Passed (1366x768 to 4K Ultrawide).
4. **Middle-Tier Assertion Unit Test:** 8/8 Passed (PCAP conversion, syntax validation, packet simulation, RTP sender).

### 3.3 Web Dashboard Express API Verification
- Process: `deployment\node\node.exe server.js` launched inside `web-app/`.
- Health Check: `GET http://localhost:3001/api/status`
- Response: HTTP 200 OK returning parsed configuration (`version: 2.1`, `serversInfo`, `account`, `callGeneration`).
- Lifecycle: Gracefully terminated with zero orphaned child processes.

---

## 4. No-Administrator Compilation and Deployment Guide

### 4.1 Running Pre-Compiled (Zero Installation / Zero Admin)
SIP Simulator v4 is completely self-contained for execution on Windows:
1. Copy or extract the project folder to any location (e.g. `C:\SIP_Simulator` or USB drive).
2. Double-click `run.bat` or `SIP_Simulator_v4.exe`.
3. The bundled 32-bit Temurin JRE in `deployment\jre\` is used automatically.
4. No administrator privileges, registry writes, or environment variable configurations are required.

### 4.2 Building from Source (Standard User Permissions)
Compilation from source code requires a Java Development Kit (JDK 8 or later):
1. Ensure `javac.exe` is available on the user's `PATH` or set `JAVA_HOME` in user environment variables. (No administrator rights required to install a portable zip JDK like Temurin or Microsoft OpenJDK into a user directory).
2. Open Command Prompt or PowerShell in the project directory (or call `build.bat` directly from any directory).
3. Run `build.bat`.
4. Compilation compiles against bundled libraries in `lib/` and packages `SIP_Simulator_v4.jar`.
5. No elevated administrator privileges are required.

---

## 5. Physical Second-PC Validation Checklist

When deploying to a physical secondary machine, perform the following verification steps:

- [ ] **Step 1: Copy Project Folder**
  - Copy the complete directory to a secondary PC running Windows 10 or 11 (e.g. `C:\Testing\SIP Simulator v4`).
- [ ] **Step 2: Launch via run.bat (No Java installed on machine)**
  - Double-click `run.bat`. Verify that application launches using the bundled JRE without prompting for Java installation.
- [ ] **Step 3: Verify Scenario and Values Loading**
  - In the UI, open the Scenario dropdown. Verify standard scenarios (e.g., `Outgoing_basic_call.txt`) are listed and load correctly.
- [ ] **Step 4: Launch Web Dashboard**
  - Click "Launch Web Dashboard" or open browser to `http://localhost:3001`.
  - Verify that the dashboard communicates with the local Express service.
- [ ] **Step 5: Verify Log Creation**
  - Start a test scenario. Check that logs are created in `Desktop\SIP Simulator logs` or `logs/`.
- [ ] **Step 6: Build Verification (Optional, requires JDK)**
  - Run `build.bat` in a command prompt with a JDK on PATH. Verify clean build.
