# Contributing to SIP Simulator v4

Thank you for your interest in contributing to **SIP Simulator v4**!

This project is a mission-critical telecom protocol simulation, testing, and diagnostic platform. To maintain stability, cross-platform portability, and compatibility with physical PBX hardware, all contributors must adhere to the following development standards and contribution guidelines.

---

## 1. Development Standards & Ground Rules

### 1.1 Strict Java 8 Compatibility is Mandatory
All Java code within `src/` and `test/` must remain 100% compatible with Java 8 (1.8.0).
- Always compile with:
  ```cmd
  javac -source 1.8 -target 1.8
  ```
- **Do not use** Java 9+ language features or standard library APIs (such as `List.of()`, `Map.of()`, `var`, `java.net.http.HttpClient`, or Java module system constructs).
- Never introduce dependencies requiring Java 9 or higher.

### 1.2 Path Resolution & Portability
- **Never use hardcoded absolute paths** (e.g., `C:\Users\...` or `/home/...`).
- Always resolve filesystem paths dynamically through `sipinspector.Utils.AppPaths`:
  - Scenarios: `AppPaths.getScenariosDir()`
  - Values: `AppPaths.getValuesDir()`
  - Configurations: `AppPaths.getConfigDir()`
  - Runtime logs: `AppPaths.getLogsDir()`
- In web and Node.js components, resolve paths relative to `__dirname` and `TRUNK_DIR`.

### 1.3 Telecom PBX Safety Guidelines
When executing tests against a physical PBX (such as Alcatel-Lucent Enterprise OXE):
1. **Never perform uncontrolled load flooding:** Cap call initiation rates at safe limits ($CPS \le 2.0$) and concurrent channels $\le 5$ unless authorized.
2. **Preserve Endpoint B Configurations:** Do not modify credentials, ports, or settings on reference softphones (e.g., MicroSIP extension `90001`).
3. **Always Clean Up Active Dialogs:** Ensure test scripts implement graceful teardown (sending `BYE` and `CANCEL`) and deregister extensions on shutdown.
4. **Never Force-Kill Java Processes:** Windows DirectSound audio handles can deadlock the Windows Audio Service (`audiosrv`) if `java.exe` is killed via `taskkill /F`. Always terminate child processes via graceful signals or tree termination.

---

## 2. Development Workflow

1. **Branching Strategy:**
   - Create a dedicated feature branch from `stability/feature-validation` (or `main`):
     ```cmd
     git checkout -b feature/your-feature-name
     ```
2. **Code Implementation:**
   - Follow standard Java and JavaScript conventions.
   - Ensure new SIP features preserve RFC compliance and maintain existing dialog isolation.
3. **Build & Offline Validation:**
   - Run the master compilation script:
     ```cmd
     build.bat
     ```
   - Run the complete offline regression harness:
     ```cmd
     run_offline_tests.bat
     ```
   - **Every offline regression test suite (14/14) must pass with 100% success.**
4. **Live PBX Validation (if applicable):**
   - If changes affect signaling or media negotiation, execute:
     ```cmd
     run_oxe_live_tests.bat
     ```
5. **Commit & Pull Request:**
   - Commit changes with clear, descriptive commit messages adhering to standard format (`feat(...)`, `fix(...)`, `docs(...)`).
   - Submit your pull request for architectural review.

---

## 3. Security Guidelines

- **Zero Credential Exposure:** Never commit SIP authentication passwords, private TLS keys, PKCS#12 certificates, or API tokens to the repository.
- Use generic placeholders (`[username]`, `[password]`, `[field0]`) in scenario templates and values files.
- Ensure any new REST API endpoints sanitize sensitive session fields before serialization.

---

## 4. License Notice

This software is currently **unlicensed** (All Rights Reserved). No license grant is implied by its presence in this repository. Unauthorized reproduction, distribution, or commercial use is prohibited without prior written authorization from the project copyright holders.
