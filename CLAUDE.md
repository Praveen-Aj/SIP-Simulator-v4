# CLAUDE.md — Agent Operating Instructions for Claude

> [!IMPORTANT]
> **Authoritative Instruction Source:**
> This repository uses [`AGENTS.md`](AGENTS.md) as its primary, non-negotiable instruction source for all AI agents.
> All Claude-based agents operating in this repository **must read and adhere strictly to [`AGENTS.md`](AGENTS.md)**.
> Do not introduce conflicting or divergent rules.

---

## 1. Persona and Mandatory Visible Declaration

Claude agents operate under two strictly segregated personas:
- **DEV Agent:** Reads `AGENTS.md`, `docs/DEVELOPMENT_STANDARDS.md`, `docs/DEVELOPMENT_ROADMAP.md`, `docs/OPEN_ISSUES.md`. Output the **DEV Agent Visible Declaration** (from `AGENTS.md §4.1`) before modifying any code.
- **QA Agent:** Reads `AGENTS.md` and the 6 mandatory QA documents (`docs/QA_AGENT_PROTOCOL.md`, `docs/QA_TEST_STRATEGY.md`, `docs/QA_TEST_CASES.md`, `docs/QA_UI_UX_EVALUATION.md`, `docs/QA_REPORT_TEMPLATE.md`, `docs/DEVELOPMENT_STANDARDS.md`). Output the **QA Agent Visible Declaration** (from `AGENTS.md §4.2`) before evaluating. **Strictly prohibited from modifying `src/**` or `web-app/**`.**

---

## 2. Essential Commands

### Build Commands
```cmd
:: Clean Java 8 compilation (mandatory -source 1.8 -target 1.8)
cmd /c build.bat

:: Build React web client (if web-app is modified)
npm --prefix web-app/client run build

:: Check Node.js backend syntax
node --check web-app/server.js
```

### Test Commands
```cmd
:: Execute master offline full regression test suite (22 suites)
cmd /c run_offline_tests.bat

:: Run single test suite
java -cp "build;lib/*" test.OfflineValidationSuite
java -cp "build;lib/*" test.PortabilityValidationTest
java -cp "build;lib/*" test.DisplayScalingValidationTest
java -cp "build;lib/*" test.HeadlessSipEndpointTest
```

---

## 3. Core Architectural Constraints
- **Strict Java 8 Bytecode:** All Java code must compile and run on Java 8 runtime.
- **No Machine-Specific Paths:** All paths must be repository-relative (`docs/QA_AGENT_PROTOCOL.md`). Never use `C:\Users\...` or hardcoded local URLs.
- **Git Safety:** Clean linear commit history on `stability/feature-validation`. Never reset, rebase, squash, force-push, or amend.
- **Preserve Existing Functionality:** Working SIP signaling, RTP pipelines, and scenario execution must not be rewritten without verified defect evidence.

---

## 4. Documentation Map
- Comprehensive Agent Protocol: [`AGENTS.md`](AGENTS.md)
- Development Standards: [`docs/DEVELOPMENT_STANDARDS.md`](docs/DEVELOPMENT_STANDARDS.md)
- QA Operating Protocol: [`docs/QA_AGENT_PROTOCOL.md`](docs/QA_AGENT_PROTOCOL.md)
- Master Test Strategy: [`docs/QA_TEST_STRATEGY.md`](docs/QA_TEST_STRATEGY.md)
- Master Test Cases: [`docs/QA_TEST_CASES.md`](docs/QA_TEST_CASES.md)
- UI/UX Usability Scorecard: [`docs/QA_UI_UX_EVALUATION.md`](docs/QA_UI_UX_EVALUATION.md)
- Milestone QA Report Template: [`docs/QA_REPORT_TEMPLATE.md`](docs/QA_REPORT_TEMPLATE.md)
