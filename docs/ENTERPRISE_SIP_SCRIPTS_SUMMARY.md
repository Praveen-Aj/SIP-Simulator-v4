# Enterprise SIP Scripts – Summary and Plan

## 1. Scope and Location

- **Enterprise scripts source (raw)**  
  `Scripts/` folder in the project root, currently at:  
  `<project-root>\Scripts\`

- **Inventory document (what this project uses as reference)**  
  `SIPInspector/trunk/docs/SCRIPTS_SCENARIO_INVENTORY.md`

This summary describes how those enterprise scripts are being analyzed and gradually turned into generalized SIP Inspector scenarios + values files, **reusing existing SIP Inspector logic only**.

---

## 2. Inventory Status

- All ~99 scripts in `Scripts/` have been read and classified.
- `SCRIPTS_SCENARIO_INVENTORY.md` contains one row per script (1–99) with:
  - **Type / category** – e.g. basic incoming, basic outgoing, REFER, supervised transfer, re-INVITE, MWI, OPTIONS, SRTP, UUI, STIR, Seplos collision/hold.
  - **Relation to base template** – which existing SIP Inspector or enterprise pattern it corresponds to.
  - **Notes** – details on call flow, variables (`set_Variable` usage), and how good a candidate it is for generalization.
- Scripts are grouped logically into families:
  - Basic incoming call (with/without 183, 480, various ringing patterns).
  - Basic outgoing call (single-leg, no re-INVITE/REFER).
  - Incoming + REFER / single-step transfer.
  - Supervised transfer with `Replaces` (VAA, Seplos, pilot scenarios).
  - Re-INVITE variants (incoming, outgoing, mixed, multiple re-INVITEs).
  - MWI / NOTIFY flows.
  - OPTIONS probes.
  - REGISTER-only or REGISTER+call flows.
  - Specialized: SRTP, UUI headers, STIR/TN-Validation, long To-tags, Seplos REFER collision/hold, etc.

### Unreadable / non-SIP files

- **Unreadable due to encoding** (need manual IDE inspection):
  - `34442 Bad 400 request`
  - `Outgoing_from_Node40_to_Node30_working`
  - `utf8_outgoing`

- **Non-SIP or out-of-scope** (not scenario files):
  - `Mar7_PAJ_test.txt` – OXE trace/log.
  - `RSI.txt`, `RSI_conference.pil`, `rsi.pil` – routing/pilot scripts.
  - `add500.C` – C++ patch module for OXE (no SIP blocks).

These are explicitly marked in `SCRIPTS_SCENARIO_INVENTORY.md` and will not be turned into SIP Inspector scenarios.

---

## 3. Constraints and Rules

- **No new logic / no new parser**  
  All work must reuse existing SIP Inspector logic:
  - Existing placeholders: `[local_ip]`, `[local_port]`, `[remote_ip]`, `[remote_port]`, `[transport]`, `[branch]`, `[call_number]`, `[cseq]`, `[cseq+1]`, `[peer_tag_param]`, `[len]`, `[media_port]`, `[ver]`, etc.
  - Existing variable mechanisms: `set_Variable=...`, `[authentication ...]`, `cmd_Pause`, `cmd_PlayPcap`, etc.
  - Existing field mechanism: `[field0]`, `[field1]`, `[field2]`, ... driven by values files.

- **No UI or core parsing changes** until template generalization is implemented and validated.

- **Strict folder structure** is respected:
  - `SIPInspectorMain.java` and launch scripts in the top-level.
  - `src/sipinspector/...` for Java sources.
  - `SIPInspector/trunk/scenarios/` for built-in and new generalized scenarios.
  - `SIPInspector/trunk/values/` for corresponding values files.
  - `Scripts/` remains the place for raw customer/field scripts.

---

## 4. Existing Template Semantics (Reviewed)

### 4.1 Outgoing/Client scenario – `scenarios/Call_client.txt`

This is the built-in **client (outgoing)** template. Key points:

- Flow:
  - REGISTER → 100* → 407* → REGISTER* (with auth) → 200.
  - INVITE → 100*/180/200 → ACK.
  - Play pcap, pause.
  - BYE → 200.

- Placeholders used:
  - Network/dialog:
    - `[remote_ip]`, `[remote_port]`
    - `[local_ip]`, `[local_port]`
    - `[transport]`
    - `[branch]`
    - `Call-ID: [call_number]@[local_ip]`
    - `CSeq: [cseq]`, `CSeq: [cseq+1]`
    - `[len]` for Content-Length.
    - `[media_port]` in SDP.
  - Tags and To-tag parameter:
    - `[peer_tag_param]` for ACK/BYE To header.
  - Version and auth:
    - `User-Agent: SIPInspector_v_[ver]`.
    - `Proxy-Authorization: [authentication username=dead; password=beef;]`.

- User identities like `1000` (caller) and `155` (callee) are currently **hardcoded**, not `[fieldX]`. This is important: we will use `[fieldX]` to generalize those while keeping the rest of the placeholder logic intact.

### 4.2 Incoming generic scenario – `Incoming_basic_call_generic.txt`

This scenario, together with its values file, shows the intended way to use `[fieldX]`:

- Values file: `values/incoming_basic_call_values.txt`.
  - Documents:
    - `field0` = response tag.
    - `field1` = SDP media port.
    - `field2` = pause duration after ACK before SIP Inspector sends BYE.
  - Example row: `1234;10162;500`.

- Scenario uses:
  - `[field0]` in `To:` tag on all responses.
  - `[field1]` in `m=audio [field1] RTP/AVP ...` in SDP.
  - `[field2]` in `cmd_Pause=[field2]`.
  - All other dynamic values still use global placeholders: `[local_ip]`, `[local_port]`, `[transport]`, `[len]`.

### 4.3 Incremental values – `values/values_incremental.txt`

- Contains e.g. `5000000+1;1+1`, confirming that **simple arithmetic** is supported in values:
  - Useful for incrementing call numbers or indices without changing the parser.

These observations define exactly how we should build generalized enterprise templates:
- Use **global placeholders** for network/dialog mechanics.
- Use **`[fieldX]` + values files** for customer-specific numbers/ports/headers.

---

## 5. Canonical "Basic Outgoing Call" Family

From `SCRIPTS_SCENARIO_INVENTORY.md`, the **basic outgoing call** family is defined as:

- Single-leg outgoing call, no re-INVITE, no REFER, no error codes.
- Typical flow:
  - `INVITE` (with SDP) → `100 Trying` → `180 Ringing` → `200 OK` (+ SDP) → `ACK` → optional `BYE/200 OK`.

### 5.1 Canonical enterprise examples

These are the primary scripts we want one generalized template to cover:

- **`Outgoing_5.10_invite`**  
  Full trunk-style outgoing call (INVITE/100/180/200/ACK/BYE), using placeholders like `[remote_ip]`, `[remote_port]`, `[peer_tag_param]`, `[cseq]`, `[call_number]`.

- **`CROXES-39026_jay_working`**  
  Similar full outgoing call with rich SDP and BYE/200; another strong template candidate.

- **`Outgoing_call_Working_Jay`**  
  Partial outgoing call (INVITE/180/200/ACK) without BYE; same basic pattern but truncated.

We explicitly **exclude** re-INVITE/REFER cases from this family.
Those are handled by separate families (re-INVITE, transfer, supervised transfer).

### 5.2 Mapping to SIP Inspector placeholder model

For the generalized **Outgoing_basic_call** scenario, we will:

- Keep using existing global placeholders for network/dialog:
  - `[remote_ip]`, `[remote_port]`
  - `[local_ip]`, `[local_port]`
  - `[transport]`, `[branch]`
  - `Call-ID: [call_number]@[local_ip]`
  - `CSeq: [cseq+1] INVITE`, `CSeq: [cseq] ACK`, `CSeq: [cseq+1] BYE`
  - `[len]` for SDP body.
  - `[peer_tag_param]` for ACK/BYE To-tag parameter.

- Introduce `[fieldX]` **only** for customer-variable parts:
  - `[field0]` – **caller user**  
    Used in `From:` URI user and `Contact:` user.
  - `[field1]` – **callee user**  
    Used in Request-URI and `To:` user.
  - `[field2]` – **media RTP port**  
    Used in SDP `m=audio [field2] RTP/AVP ...`.

This mapping allows a single scenario file to represent different enterprise scripts simply by changing values in the `.values` file:
- `Outgoing_5.10_invite`, `CROXES-39026_jay_working`, `Outgoing_call_Working_Jay` become **rows** with different caller/callee/media-port and possibly different `[remote_ip]/[remote_port]` settings.

Later, we can extend the same template or create closely related variants to handle:
- P-ANI headers (`Outgoing_PANI_jay`).
- STIR / P-Asserted-Identity (`outgoing_call_STIR_JAY`, `outgoingcall_100%working`).
- REGISTER+call patterns (e.g. `JISHNU`).

---

## 6. Implementation Plan (High-Level)

1. **Basic outgoing call family (current focus)**
   - Create `Outgoing_basic_call.txt` in `scenarios/` by adapting one canonical enterprise script and applying the mapping above.
   - Create a matching values file in `values/` with at least:
     - One row per key enterprise example.
   - Validate by running the new scenario + values in SIP Simulator and comparing SIP messages to the original `Scripts/` flows.

2. **Subsequent families (later phases)**
   - **Basic incoming** (using `Incoming_basic_call_generic.txt` pattern).
   - **Incoming + REFER / single-step transfer**.
   - **Supervised transfer with Replaces** (VAA, Seplos, pilot scripts).
   - **Re-INVITE** families.
   - **MWI/NOTIFY**, **OPTIONS**, **REGISTER-only**.
   - Specialized: SRTP, UUI, STIR, Seplos REFER collision/hold.

3. **Wiring into defaults** (only after templates are stable)
   - Use existing configuration mechanisms to expose the new enterprise templates as additional scenario/value sets.
   - Do not change the parser; only point to new files or add presets.

---

## 7. Task Tracking Snapshot

Current high-level tasks (tracked separately via TODO list):

- **Completed**
  - t1: Global summary and inventory.
  - t2: Review of existing outgoing and incoming generic templates and values.
  - t3: Definition of the canonical basic outgoing family and its enterprise members.
  - t4: Design of placeholder + values mapping for basic outgoing calls.

- **Next**
  - t5: Create generalized basic outgoing scenario file in `scenarios/`.
  - t6: Create matching values file(s) in `values/` for key enterprise scripts.
  - t7: Validate new templates by running them and comparing against original `Scripts/` traffic.
  - t8/t9: Plan next family and maintain coverage summary.

---

## 8. Work Log – 2025-11-15 (Basic Outgoing + Transport Debugging)

### 8.1 Scenario / Values Handling
- Cleaned enterprise values files (e.g. `Outgoing_basic_call_values.txt`) to remove blank lines that produced empty rows and `null` `[fieldX]` substitutions.
- Instrumented `ValuesLoader` with `TLSDebugLogger` to log:
  - Values file load path.
  - Each row loaded (index, number of fields, raw line).
  - Row selection via internal counter (which row is active for `[fieldX]`).
- Instrumented `SIPCall` to log:
  - When a call is initialized with a non-null row of values.
  - Each `[fieldX]` replacement with the actual value used.

### 8.2 UI / Usability Enhancements
- Confirmed and relied on `userSettings.xml` to auto-load the last used scenario and values at startup.
- Updated file choosers in `SIPInspectorMainFrame` so:
  - Scenario chooser defaults to the `scenarios/` folder.
  - Values chooser defaults to the `values/` folder.
- Added a TLS port warning in `NextActionPerformed`:
  - When TLS is selected, remote server is enabled, and the remote port is not `5061`, a popup warns that 5061 is the typical TLS port but does **not** block the connection.

### 8.3 Transport Debug Logging (UDP/TCP/TLS)
- Introduced unified transport debug logging via `sipinspector.Utils.TLSDebugLogger` with per-transport files under `logs/UDP`, `logs/TCP`, and `logs/TLS`.
- Added detailed send/receive logging to:
  - `UDPClientSocket` – logs `=== UDP SEND/RECV DEBUG ===` and full SIP messages.
  - `TCPClientSocket` – logs `=== TCP SEND/RECV DEBUG ===` and full SIP messages.
  - `TLSClientSocket_V6` – logs `=== TLS OPEN/SEND/RECEIVE/CLOSE ===` including cipher suite and protocol.
- Added `ReceiverThread` logging for received packets (`RECEIVER: packet received, len=..., from=...`).
- Removed high-frequency `"RECEIVER: no packet (timeout)"` logging to prevent giant log files and OS warnings while keeping all real traffic visible.

### 8.4 TLS Configuration and Handshake
- Investigated TLS handshake failures and malformed SIP logs on the server when using TLS against a non-TLS port.
- Clarified final TLS configuration (for current environment):
  - **Remote TLS port:** 5061.
  - **Local port:** any available port (5061 used during current tests on the client).
- After switching to remote port 5061, verified:
  - Successful TLS 1.3 handshake (`TLS_AES_256_GCM_SHA384`).
  - Correct INVITE/100/480 flows over TLS, matching UDP and TCP behavior.
- Archived older TLS design/fix documents into `TLS_ARCHIVE/` and kept the latest docs (`TLS_IMPLEMENTATION_COMPLETE.md`, `TLS_FINAL_STATUS.md`, `TLS_PORT_CLARIFICATION.md`, etc.) as the canonical reference.

These changes keep all original SIP Inspector logic intact while improving observability, stability, and usability for enterprise basic outgoing scenarios over UDP/TCP/TLS.

---

## 9. Enterprise Template Plan (Template 1..20)

This section fixes the **numbered template plan** that we will follow when converting
enterprise scripts under `Scripts/` into generalized SIP Inspector scenarios + values.

Template numbers are **stable IDs** and will be referenced in:
- Scenario file names under `SIPInspector/trunk/scenarios/`.
- Values file names under `SIPInspector/trunk/values/`.
- Comments and work logs in this document.

### 9.1 Conventions (Optionals and Placeholders)

- **Optional responses (`*`)**
  - We keep SIP Inspector's existing convention: `100*`, `180*`, `407*`, etc. indicate
    **optional messages** that may or may not appear on the wire.
  - Templates will continue to use `*` where the original enterprise script showed
    that a response is often present but not strictly required.

- **Global placeholders (unchanged)**
  - Network/dialog: `[local_ip]`, `[local_port]`, `[remote_ip]`, `[remote_port]`,
    `[transport]`, `[branch]`, `[len]`, `[media_port]`, `[ver]`.
  - Dialog identities: `Call-ID: [call_number]@[local_ip]`, `CSeq: [cseq]`,
    `CSeq: [cseq+1]`, `[peer_tag_param]`.
  - Authentication and variables: `[authentication ...]`, `set_Variable=...`.

- **Field-based placeholders (`[fieldX]`)**
  - Used **only** for customer-specific values: caller/callee numbers, media ports,
    P-ANI values, UUI payloads, STIR identity tokens, special headers, pause durations.
  - All `[fieldX]` values come from the matching `.values` file; we reuse the existing
    `ValuesLoader` + `SIPCall` logic (no new parser).

- **Tags and dialog parameters**
  - Templates in transfer / supervised-transfer families use existing variables:
    `set_Variable=callid`, `fromTag`, `toTag`, and re-use them via placeholders in
    REFER, BYE, re-INVITEs, and `Replaces`.
  - We do **not** invent new mechanisms for tags; we only standardize which
    `[fieldX]` map to user-visible numbers vs stored tags.

- **Delays and timing**
  - We keep using `cmd_Pause=...`.
  - Where enterprise scripts vary only by delay, we move the delay into a
    `[fieldX]` and document it in the values file (e.g. `[field2]` = pause ms).

### 9.2 Template List (High-Level)

The goal is to cover almost all enterprise scripts with **~20 templates** and
values, minimizing duplication while keeping specialized tests where they add
clear value.

Below, "Example scripts" refer to entries in `SCRIPTS_SCENARIO_INVENTORY.md`.

- **Template 1 – Outgoing basic call**  
  Category: single-leg outgoing INVITE with SDP → 100/180/200 → ACK → optional BYE.  
  Example scripts merged: `Outgoing_5.10_invite` (62), `CROXES-39026_jay_working` (14),
  `Outgoing_call_Working_Jay` (69), trunk-style red-flag INVITEs (42,45,46).  
  Notes: already implemented as `Outgoing_basic_call.txt` + values; uses `[field0]`
  = caller, `[field1]` = callee, `[field2]` = media port, with `*` on 100 Trying
  where appropriate.

- **Template 2 – Incoming basic call**  
  Category: incoming INVITE answered with 100/180/200(+SDP)/ACK, optional BYE/200.  
  Example scripts merged: `Incoming_basic_call_working_JAY` (1), `33561_incoming_200_SDP` (2),
  `Incoming_INVITE_incoming_BYE` (31), `Incoming_from_Node40_to_20_working` (33).  
  Notes: based on `Incoming_basic_call_generic.txt` + values. `[field0]` = tag,
  `[field1]` = SDP media port, `[field2]` = pause before BYE.

- **Template 3 – Incoming early-media (183/180/200) variants**  
  Category: incoming call with 183 Session Progress (with/without SDP), optional 180,
  then 200 OK/ACK.  
  Example scripts merged: `Incoming_183 with SDP` (26), `Incoming_183_200_SDP_5.30 Node` (28),
  `Rex_Inoming_183_180` (81), `incoming_183_FSNE_jay` (86), `incoming_external` (87).  
  Notes: use `*` for optional 180; `[fieldX]` for SDP ports and early-media delays.

- **Template 4 – Incoming error / rejection (480 etc.)**  
  Category: INVITE rejected (e.g. 480 Temporarily Unavailable), ACK only.  
  Example scripts merged: `Incoming_480` (29); later 4xx/5xx variants can be added
  as value/field rows while keeping the same control flow.  
  Notes: optional 100 Trying with `100*`; status code/value may be driven by `[fieldX]`.

- **Template 5 – Incoming CANCEL + retry**  
  Category: incoming INVITE → 100/180, CANCEL → 200 (CANCEL)/487 (with stored CSeq),
  then second INVITE/180/200.  
  Example scripts merged: `CROXES-34585_SAMU_JAY` (9), `CROXES-38585_jay` (13),
  `JAy_Red flag samco site` (40).  
  Notes: uses `set_Variable=cseq1` and existing CSeq placeholders; delays use `[fieldX]`.

- **Template 6 – Incoming + simple REFER (single-step transfer)**  
  Category: incoming basic call → REFER → NOTIFY/200 → BYE.  
  Example scripts merged: `34780_jay` (4), `Dec12_Incomingf_Refer` (19),
  `Incoming_node20_singlestep_local_user` (37).  
  Notes: uses stored `callid`, `fromTag`, `toTag` and `*` on intermediate responses.

- **Template 7 – Incoming + REFER with stored tags (improved)**  
  Category: like Template 6 but with more complete tag handling and BYE tear-down.  
  Example scripts merged: `34780_jay_working` (5) as the canonical form.  
  Notes: we treat this as the **main** incoming+transfer template; simpler cases
  fold into it as rows.

- **Template 8 – Supervised transfer with Replaces (VAA / pilot)**  
  Category: initial dialog (incoming 200+SDP+ACK or 180/200+ACK), then outgoing INVITE
  to second party, 100/180/200/ACK, REFER with `Replaces` built from stored
  `callid`/`totag`, 202/NOTIFY/200.  
  Example scripts merged: `INCOMING_n2_supervistranfer_n1` (21),
  `Node30_VAA_supervised_transfer_*` (53–57), `VAA_Pilot_supervised_transfer_latest.txt` (83),
  `prad_38_10_supervised_transfer_11600.txt` (92), `wrong display.txt` (99).  
  Notes: heavy use of tag/Call-ID variables; we reuse existing `Replaces` construction.

- **Template 9 – Supervised transfer with hold / collision (Seplos)**  
  Category: multi-leg supervised transfer including hold (c=0.0.0.0, `sendonly`) and
  REFER collision patterns.  
  Example scripts merged: `Seplos_out_reinvite_out_refer_Arya` (82),
  `refer_collision_seplos_Hold_arya*.txt` (93–94), `arya_2hold.txt` (85).  
  Notes: preserves complex timing and `cmd_Pause` patterns; delays become `[fieldX]`.

- **Template 10 – Outgoing with REGISTER preamble**  
  Category: REGISTER/200 followed by outgoing basic call.  
  Example scripts merged: `JISHNU` (41), `uft8_customer` (97).  
  Notes: built as Template 1 + a REGISTER block; auth remains optional via `*`.

- **Template 11 – Outgoing re-INVITE (same target)**  
  Category: outgoing INVITE with SDP, 100/180/200/ACK, then outbound re-INVITE on
  the same dialog, 100/200/ACK.  
  Example scripts merged: `Outgoing_reinvite_naveen` (74),
  `out_invite_out_reinvite_jay_working` (89), `PAJ_34971_Outgoing_Reinvite` (78).  
  Notes: reuses existing `set_Variable` for Call-ID/tags/RSeq/CSeq, no new logic.

- **Template 12 – Outgoing re-INVITE + transfer / extra legs**  
  Category: outgoing basic call followed by incoming re-INVITEs and/or additional
  outgoing INVITEs to new targets.  
  Example scripts merged: `Outcall_inc_reinvite_out_reinvite_JAY_working` (60),
  `Outgoing_from_node20_to_node30_with_reinvite` (71),
  `Outgoing_withtags_reinvite_incoming_REinvite*` (76–77), `jay_rework` (88).  
  Notes: complex but still reuses the same re-INVITE/dialog-variable pattern.

- **Template 13 – Incoming re-INVITE / voicemail variants**  
  Category: incoming basic call followed by one or more incoming re-INVITEs,
  sometimes representing voicemail or media changes.  
  Example scripts merged: `CROXES-35353 Incoming_Reinvite without SDP` (10),
  `Inc_Invite_Inc_Reinvite_VM_PAJ` (22), `Incoming_INVITE_Incoming_ReInvite` (30).  
  Notes: optional 183/180 blocks are marked with `*`; SDP ports in `[fieldX]`.

- **Template 14 – SRTP / crypto negotiation**  
  Category: INVITEs and responses with `RTP/SAVP` and `a=crypto` attributes.  
  Example scripts merged: `FSNE_incoming` (20), `Outgoing_INVITE and RE_INVITE Crypto` (65).  
  Notes: SDP crypto attributes and ports are field-driven; base flow comes from
  Templates 3 and 11.

- **Template 15 – Outgoing P-ANI and special headers**  
  Category: outgoing basic call plus P-Access-Network-Info or other location headers.  
  Example scripts merged: `Outgoing_PANI_jay` (68), `CROXES-35427 HSS No memory PAJ` (12)
  for heavy header sets.  
  Notes: header contents become `[fieldX]` columns in values files.

- **Template 16 – Outgoing INVITE with ISDN UUI**  
  Category: outgoing INVITE with `User-to-User` ISDN hex content.  
  Example scripts merged: `CROXES35567_jay_uui_*` (15–18),
  `Outgoing node30 to node40_UUI empty` (61), `Jay_UUI_Refer` (43) for pure INVITE
  examples.  
  Notes: UUI hex payload is `[fieldX]`; rest of flow reuses Template 1.

- **Template 17 – Outgoing STIR / TN-Validation**  
  Category: outgoing basic calls including STIR-related `P-Asserted-Identity`.  
  Example scripts merged: `outgoing_call_STIR_JAY` (90), `outgoingcall_100%working` (91).  
  Notes: STIR/TN header body becomes `[fieldX]`; optional 100/180 use `*`.

- **Template 18 – MWI / NOTIFY suite**  
  Category: NOTIFY-only and NOTIFY pairs for Message-Waiting-Indicator.  
  Example scripts merged: `MWI_notify_outgoing` (47), `Mwi_checkin` (49),
  `Mwi_checkin_vm_in` (50), `Mwi_checkout_vmret` (51), `Mwi_suite_outgoing` (52).  
  Notes: mailbox/user and "yes/no" values are `[fieldX]`; timing via `cmd_Pause`.

- **Template 19 – OPTIONS health-check**  
  Category: OPTIONS ping with 200 OK, with/without Route headers.  
  Example scripts merged: `Options_5.10_34027_PAJ` (58), `Options_5.10_34045_Jay` (59),
  mid-call OPTIONS parts of `Outgoing_5.10_invite_with_options` (63).  
  Notes: can be used standalone or embedded as an optional block (`*`) inside
  call templates.

- **Template 20 – REGISTER-only and mixed REGISTER/OPTIONS/NOTIFY utilities**  
  Category: simple REGISTER, REGISTER+NOTIFY, REGISTER+OPTIONS flows.  
  Example scripts merged: `reg_seplos` (95), `Jay_options` (44).  
  Notes: this template underpins many preamble sections in Templates 1, 10, 18, 19.

### 9.3 Merge vs Standalone Summary

- **Non-SIP / out-of-scope files** (Mar7_PAJ_test.txt, RSI*.pil, add500.C) remain
  **standalone** and are not mapped to templates.
- **Unreadable-encoding files** (`34442 Bad 400 request`, `Outgoing_from_Node40_to_Node30_working`,
  `utf8_outgoing`) are temporarily **standalone** until inspected in the IDE and
  then assigned to one of the above templates.
- Most remaining scripts are assigned to Templates 1..20 as listed; within each
  family, duplicates/variants are merged by:
  - Moving caller/callee/media ports, special headers, UUI/STIR payloads, and delays
    into `[fieldX]` columns in the values file.
  - Keeping dialog mechanics, tags, and optional `*` responses identical.

This plan gives us a **fixed roadmap**: we can now implement templates in numeric
order (1 → 20), updating this section as we complete each family.
