# SIP Simulator v4 – Authoritative Development & Portability Standards

**Document Version:** 1.0.0-AUTHORITATIVE  
**Effective Date:** November 14, 2026  
**Scope:** Entire Project (Java Desktop, Web Dashboard, Express Backend, Sockets, Tests, Build Scripts, Documentation)  

---

## 1. Core Engineering Principle: Preserve Existing Functionality

The existing SIP execution engine, message generation, scenario parsing, and socket communication are verified and operational. 
**Preserving working functionality is always higher priority than implementing new enhancements or refactorings.**

### Non-Negotiable Rules:
1. **No Regressions:** Do not rewrite or modify working SIP execution logic unless addressing a confirmed defect.
2. **Backward Compatibility:** Maintain existing scenario (`.txt`) and values (`.txt`) file syntax.
3. **Runtime Compatibility:** Strict adherence to **Java 8 bytecode** (`-source 1.8 -target 1.8`) and **Windows 10/11 compatibility**.
4. **Self-Contained Deployment:** No external package manager (Maven, Gradle, npm install) is permitted during compilation or execution of the desktop Java application. All Java dependencies remain pre-bundled in `lib/`.
5. **No Mandatory Administrator Privileges:** Compilation, packaging, and normal execution must function without elevated administrative privileges.

---

## 2. Zero Machine-Specific Paths Policy

Under no circumstances may machine-specific absolute filesystem paths be introduced into the project.

### Prohibited Patterns
```text
PROHIBITED:
  C:\Users\Praveen\...
  C:\Users\<any-specific-user>\...
  D:\Projects\...
  C:\Program Files\Java\...
  file:///C:/Users/...
```

### Centralized Path Resolution Rules

All path resolution must be centralized through designated architectural anchors:

#### A. Java Components: Use `sipinspector.Utils.AppPaths`
Never instantiate raw relative paths scattered across classes like `new File("config/...")` or `new File("scenarios/...")`. Always resolve via `sipinspector.Utils.AppPaths`:

```java
// Correct Usage:
File configFile = AppPaths.getConfigFile("settings.properties");
File scenariosDir = AppPaths.getScenariosDir();
File webAppDir = AppPaths.getWebAppDir();
File customFile = AppPaths.resolve("security/cert.pem");
String javaBin = AppPaths.getJavaExecutable();
String nodeBin = AppPaths.getNodeExecutable();
```

* `AppPaths.getAppRoot()` dynamically discovers the project root using:
  1. System properties `-Dapp.home` or `-Dsipinspector.home`
  2. The code source location of `Main.class` (JAR directory or build directory)
  3. Safe fallback to `System.getProperty("user.dir")`
* Works correctly even if the application is launched from a desktop shortcut, PowerShell running on another drive, or as a background service.
* Handles spaces in directory names without `%20` URL-encoding corruption.

#### B. JavaScript / Node.js Components: Anchor to `TRUNK_DIR`
In `web-app/server.js`, all filesystem operations must anchor to `TRUNK_DIR`:

```javascript
// Correct Usage:
const TRUNK_DIR = path.resolve(__dirname, '..');
const USER_SETTINGS_PATH = path.join(TRUNK_DIR, 'userSettings.xml');
const SCENARIOS_DIR = path.join(TRUNK_DIR, 'scenarios');
```

#### C. Windows Batch Scripts: Anchor with `cd /d "%~dp0"`
Every batch file (`.bat`) must start with directory self-anchoring immediately after `@echo off`:

```cmd
@echo off
REM Switch to batch file directory regardless of caller working directory
cd /d "%~dp0"
```

Never hardcode `C:\Program Files` or `C:\Program Files (x86)`. Use system environment variables:
```cmd
if defined ProgramFiles(x86) (
    set TOOL_PATH="%ProgramFiles(x86)%\Tool\tool.exe"
)
```

---

## 3. Complete Project Portability

The project must remain 100% portable. When copied to another Windows machine:
1. It must build and run out-of-the-box without editing source code or configuration files.
2. If optional external dependencies (e.g. Inno Setup or Node.js) are missing, the system must detect their absence and provide clear diagnostic messages rather than failing silently with hardcoded fallback paths.
3. User settings and window geometry must store relative coordinates or validate saved screen bounds against the active display before restoring.

---

## 4. Preserving the Modular Architecture

All new or modified files must strictly reside in their designated directory:

| Folder | Purpose | Restrictions |
|---|---|---|
| `src/` | Java production source code | No test harnesses or main entry mocks |
| `test/` | Automated unit, soak, and integration tests | Self-contained, runnable via `java -cp` |
| `docs/` | Technical documentation and architectural policies | Portable markdown, repository-relative links |
| `config/` | Persistent configuration (`settings.properties`) | No machine-specific values |
| `backup/` | Automated backups of scenarios and values | Managed by `BackupManager.java` |
| `web-app/` | Express.js backend and React client | Client sources in `web-app/client/` |
| `scenarios/` | SIP scenario definitions (`.txt`) | Standard format |
| `values/` | SIP scenario token value sets (`.txt`) | Standard format |
| `security/` | TLS certificates, keys, and truststores | Test assets only |
| `lib/` | Pre-bundled JAR dependencies | Java 8 compatible |

**Rule:** Do not clutter the root directory. Only essential orchestrators (`build.bat`, `run.bat`, `build_and_run.bat`) reside in the root.

---

## 5. Documentation Standards

1. **Repository-Relative Links Only:**
   - Link files using repository-relative paths: `src/sipinspector/SIPInspectorMainFrame.java`, `docs/OPEN_ISSUES.md`.
   - Never use `file:///C:/Users/...` or developer-specific local paths in markdown.
2. **Command Prompt Examples:**
   - In documentation code blocks, format terminal commands with generic prompts:
   ```text
   > cmd /c build.bat
   > java -cp "build;lib/*" sipinspector.Utils.AssertionUnitTest
   ```
   Never include local prompt paths like `C:\Users\Praveen\Downloads\...`.

---

## 6. Pre-Commit Verification Protocol

Before committing any modification, the following verification checklist must pass with zero errors:

```markdown
### Pre-Commit Checklist
- [ ] 1. Run Java clean build:
      > cmd /c build.bat
      Verify: BUILD SUCCESSFUL (0 compilation errors, 0 missing symbols).

- [ ] 2. Run Scenario Assertion Unit Tests:
      > java -cp "build;lib/*" sipinspector.Utils.AssertionUnitTest
      Verify: 8 / 8 passed.

- [ ] 3. Run Offline Protocol Validation Suite:
      > java -cp "build;lib/*" test.OfflineValidationSuite
      Verify: 6 / 6 passed (Codecs, SRTP, SIPParser, ValuesLoader).

- [ ] 4. Run Display Bounds & Scaling Validation:
      > java -cp "build;lib/*" test.DisplayScalingValidationTest
      Verify: 6 / 6 display profiles passed (1366x768 to 4K 200%).

- [ ] 5. Run Portability Validation Suite:
      > java -cp "build;lib/*" test.PortabilityValidationTest
      Verify: 7 / 7 portability checks passed.

- [ ] 6. Run Live Audio Soak Validation:
      > java -cp "build;lib/*" sipinspector.Media.LiveAudioSoakValidationMain
      Verify: 0 thread leaks, 0 socket leaks, 0 line leaks.

- [ ] 7. Build React Web Client (if web-app modified):
      > npm --prefix web-app/client run build
      Verify: Build succeeds without JSX/TypeScript errors.

- [ ] 8. Verify Git Status:
      > git status
      Ensure no unintended files, binaries, or machine-specific configs are staged.
```

---

## 7. QA Quality Gates & Verification Protocol

All feature branches, major bug fixes, and development milestones are governed by the **SIP Simulator v4 QA Engineering Framework**.

### Non-Negotiable QA Principles
1. **Independent Verification:** The QA Agent acts as an independent quality gate. DEV Agent self-assessments or passing mock tests alone cannot approve a milestone.
2. **Authoritative Quality Documents:**
   - **Protocol & Quality Charter:** [`docs/QA_AGENT_PROTOCOL.md`](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/docs/QA_AGENT_PROTOCOL.md)
   - **Test Strategy & Multi-Tier Verification:** [`docs/QA_TEST_STRATEGY.md`](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/docs/QA_TEST_STRATEGY.md)
   - **Master Test Cases & Workflows:** [`docs/QA_TEST_CASES.md`](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/docs/QA_TEST_CASES.md)
   - **UI/UX Evaluation Rubric:** [`docs/QA_UI_UX_EVALUATION.md`](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/docs/QA_UI_UX_EVALUATION.md)
   - **Milestone Audit Report Template:** [`docs/QA_REPORT_TEMPLATE.md`](file:///c:/Users/Praveen/Downloads/Python%20Scripts/SIP%20Simulator_v4/docs/QA_REPORT_TEMPLATE.md)
3. **Four Verdict Standard:** Every release candidate must receive a formal verdict: `PASS`, `PASS WITH LIMITATIONS`, `FAIL`, or `BLOCKED`. A release is rejected if any Critical (P0) or blocking High (P1) defect remains open.
4. **Rejection of Anti-Patterns:** Code presence, mock test passes, manual state setting, and unverified API responses are explicitly rejected as proof of functionality. Validated protocol execution traces are mandatory.

