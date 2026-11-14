# SIP Simulator Local Coding Rules & Standards

This document captures the minimum rules every contributor must follow when working in this workspace. Keep it in sync with Cascade customizations so the assistant can rely on the same source of truth.

## 1. Repository Hygiene
- **Git required**: All work must be committed to the local repo (`SIPInspector/trunk`). Never work outside version control.
- **Branching**: Use feature branches for risky work; keep `master` stable and always build before merging.
- **Commit cadence**: Commit after every logically complete change (restore fixes, add docs, etc.) with descriptive messages.

## 2. Build & Validation
- Run `build_and_run.bat` after code or scenario edits. A successful run is mandatory before reporting completion.
- Do not ship failing builds. Document any temporary breakage directly in the commit message if unavoidable.

## 3. File & Folder Structure
- Respect the existing layout (`src`, `scenarios`, `values`, `security`, etc.).
- Create new docs under `docs/` and new utilities under the appropriate package (e.g., `sipinspector.Utils`).
- Never duplicate logic—prefer extending the existing classes (`SIPInspectorMainFrame`, socket hierarchy, scenario loaders, etc.).

## 4. Coding Conventions
- Java code must follow the style already used in `src/sipinspector` (brace placement, logging style, etc.).
- When porting logic from the working executable, decompile only the necessary class and verify formatting before committing.
- Remove UTF-8 BOMs, ensure files end with a newline, and avoid mixed line-endings.

## 5. Documentation & Instructions
- Update this file whenever rules change and mirror the updates in Cascade under:
  - **Rules**: “SIP Simulator – Coding Rules” (summary of sections 1–4).
  - **Workflows**: “Build & Commit Flow” (steps: edit → run `build_and_run.bat` → commit → report).
  - **Memories**: Key reminders (e.g., “Always rebuild after coding changes”, “Always update scripts scenario inventory”).
- Keep deployment and testing instructions up to date in `docs/`.

## 6. Backups & Sync
- Maintain the `_v3` clone in sync with `trunk` after every change (use `SYNC_TO_V3.bat`).
- Never delete the working installer artifacts (e.g., `SIP_Simulator_Setup_final_working_v2.0.exe`).

> **Reminder:** These rules exist so we never lose fixes again. If you break them, leave a trail (commit notes, issue logs) explaining why.

## 7. Cascade Customizations (Rules · Workflows · Memories)

Use the Cascade Customizations pane so assistants always follow the latest guidance. Mirror the content below:

### Rules (Global + Project)
1. **SIP Simulator – Coding Rules & Standards**
   - Summarize Sections 1–6 of this document.
   - Emphasize “no new logic,” “respect folder structure,” “run build_and_run.bat,” and “keep `_v3` in sync.”
2. **Security & Backup Directive**
   - Never delete installer artifacts or certificates under `security/`.
   - Always snapshot scenarios/values before editing.

### Workflow: “Build & Commit Flow”
1. Sync or branch as needed (`git pull` / create feature branch).
2. Make changes (code, scenarios, values, docs).
3. Run `build_and_run.bat` → confirm launch succeeds.
4. If applicable, run `SYNC_TO_V3.bat` to mirror the `_v3` clone.
5. `git status` → `git add` → `git commit -m "<summary>"`.
6. Update Cascade memories/rules if new instructions emerged.
7. Report results (including build status) back to the user.

### Memories (add each as separate entries)
- “Always rebuild after coding changes (run build_and_run.bat).”
- “Always update scripts scenario inventory when scenarios change.”
- “Keep `_v3` clone synchronized after each change.”
- “Never delete working installer artifacts (final v2 exe).”
- “Use Cascade rules/workflows before starting tasks.”

> Once these are entered, Cascade will remind every contributor about mandatory builds, scenario tracking, and backup rules.
