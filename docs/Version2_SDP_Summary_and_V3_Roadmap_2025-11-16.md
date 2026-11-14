# SIP Simulator v2 – SDP / UI Summary and v3 Roadmap

Date: 2025-11-16

## 1. Version 2 – Detailed Summary

### 1.1 SDP offer / answer behavior

**Offer generator (`SIPMessageEntry.getOfferSDP`)**

- Respects SDP tab codec selection:
  - G.711 (PCMA) → payload 8
  - G.729 → payload 18
  - G.722 → payload 9
  - Opus → payload 111
  - DTMF telephone-event → payload 101 always appended last.
- If no codec is enabled, falls back to G.711 (PCMA) by default.

**When SRTP is OFF (`GlobalSettings.isSdpUseSavp() == false`)**

- Generates a single RTP/AVP offer:

  ```
  v=0
  o=111 843670094 843670094 IN IP4 [local_ip]
  s=-
  c=IN IP4 [local_ip]
  t=0 0
  a=sendrecv
  m=audio [media_port] RTP/AVP <payloads including 101>
  a=rtpmap:<payloads>
  a=rtpmap:101 telephone-event/8000
  a=fmtp:101 0-15
  a=ptime:20
  ```

- Payload list is ordered according to enabled codecs from the SDP tab plus 101 last.

**When SRTP is ON (`GlobalSettings.isSdpUseSavp() == true`)**

- Generates a dual-profile offer (AVP + SAVP) aligned with the previously working SRTP template.
- Structure (with codecs driven by the SDP tab, example below with only PCMA enabled):

  ```
  v=0
  o=111 843670094 843670094 IN IP4 [local_ip]
  s=-
  c=IN IP4 [local_ip]
  t=0 0

  m=audio [media_port] RTP/AVP 8 101
  a=rtcp:[media_port+1]
  a=rtpmap:8 PCMA/8000
  a=rtpmap:101 telephone-event/8000
  a=fmtp:101 0-15
  a=silenceSupp:off - - - -
  a=sendrecv
  a=ptime:20
  a=maxptime:30

  m=audio [media_port] RTP/SAVP 8 101
  a=rtcp:[media_port+1]
  a=rtpmap:8 PCMA/8000
  a=rtpmap:101 telephone-event/8000
  a=fmtp:101 0-15
  a=silenceSupp:off - - - -
  a=sendrecv
  a=ptime:20
  a=maxptime:30
  a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:kPJGvfr7reh/3NLQG8zbFF8ByxLigwdTwxfAJEke|2^31
  ```

- The actual payload list is built from the selected codecs and always ends with 101.
- The same payload list is used for both AVP and SAVP m-lines.
- `a=crypto` is only present when SRTP is enabled.

**Answer generator (`SIPMessageEntry.getAnswerSDP`)**

- Uses a single preferred answer codec from the SDP tab:
  - `GlobalSettings.getSdpAnswerCodecType()` → maps to payload 8/18/9/111.
- Builds a single m-line with the answer payload plus 101 for DTMF:
  - Uses `RTP/AVP` when SRTP is off.
  - Uses `RTP/SAVP` when SRTP is on.
- Always includes:
  - `a=rtpmap:<answer payload>`
  - `a=rtpmap:101 telephone-event/8000`
  - `a=fmtp:101 0-15`
  - `a=ptime:20`

**Custom SDP override**

- If `GlobalSettings.getCustomSDP()` returns a non-empty string, both `getOfferSDP()` and `getAnswerSDP()` return that value **as-is**, bypassing codec/SAVP logic.

### 1.2 SDP tab UI and preview behavior

**Layout changes in `SDPPanel`**

- First row (offer codecs):

  ```
  Offer codecs: [G.711 (PCMA)] [G.729] [G.722] [Opus]
  ```

  - Implemented by placing label and four checkboxes in a single MigLayout row using `split 5`.
  - No large gaps between label and first codec.

- Second row (SRTP + preferred answer codec):

  ```
  [ ] Use SRTP (RTP/SAVP)      Preferred answer codec: [ G.711 ▼ ]  [Apply SDP settings]
  ```

  - SRTP checkbox, then a gap to the preferred codec label, then combo box, then the apply button all on one line.

- Third row (SDP preview text area):

  - `JTextArea(10, 40)` wrapped in a `JScrollPane`.
  - MigLayout constraints `"span, grow, pushy"` give it vertical growth without forcing a very wide window.

**SDP preview content**

- Preview is simplified to only show essential lines derived from current settings:

  ```
  Offer:  m=audio [media_port] RTP/AVP <payloads including 101>
  Offer:  m=audio [media_port] RTP/SAVP <payloads including 101>   (only when SRTP is checked)
  SRTP:   a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:...
  Answer: m=audio [media_port] RTP/AVP|RTP/SAVP <answerPayload> 101
  ```

- The payload list is built from the same codec selection logic as in `getOfferSDP()` and always ends with 101.
- This keeps the preview readable and aligns it with the actual generated SDP.

### 1.3 Window sizing and navigation

**Main frame sizing (`SIPInspectorMainFrame`)**

- The main frame now uses:

  ```java
  pack();
  setSize(new Dimension(860, 680));
  setMinimumSize(new Dimension(780, 600));
  setLocationRelativeTo(null);
  ```

- This keeps the default size moderate while still leaving enough room for the scenario view and SDP tab.

**Intro screen bottom bar (Account card)**

- Bottom bar now uses MigLayout with a growing first column and a fixed second column:

  ```java
  JPanel introBottomBar = new JPanel(
      new MigLayout("fill, insets 0", "[grow][]", "[]"));

  introBottomBar.add(statisticsButton, "cell 0 0, left");
  introBottomBar.add(Next,             "cell 1 0, right");
  ```

- This anchors the `Next >>` button to the **bottom-right corner** of the intro screen, independent of width.
- The status bar (`EnhancedStatusBar`) remains pinned to the bottom of the main frame.

### 1.4 Completed functional goals

- All SDP generation from the SIP tab is now **PCMA-first** by default and consistent with the codec selections.
- SRTP offers present **both** RTP/AVP and RTP/SAVP media descriptions with appropriate attributes (`rtcp`, `silenceSupp`, `ptime`, `maxptime`, `crypto`).
- Answer SDPs correctly follow the preferred codec and SRTP setting.
- SDP tab UI is more compact and readable; preview mirrors the important parts of the on-wire SDP.
- Intro screen navigation is more reliable with `Next >>` fixed to the bottom-right.


## 2. Version 3 – Roadmap Ideas

These items are **future enhancements** suitable for a v3 generation of the tool. Many would require new logic and are not implemented yet.

### 2.1 SDP / media enhancements

- **Per-template SDP profiles**
  - Allow each scenario template to reference an SDP profile defining:
    - Codec priorities and allowed sets.
    - RTP vs SRTP usage per scenario.
    - Special variants: hold (`a=sendonly` / `a=inactive`), early media, re-INVITE SDPs.
  - Profiles would be centrally defined and reused across scenarios.

- **Advanced RTP options panel**
  - New UI for configuring: `ptime`, `maxptime`, `silenceSupp`, RTCP port offset.
  - Options applied consistently to generated offer/answer SDPs.

- **Future ICE / NAT helper stubs**
  - Optional attributes for testing: `a=rtcp-mux`, `a=rtcp-rsize`, `rport`-like behavior.
  - Not full ICE, but enough to emulate SBC traces.

### 2.2 Scenario engine & scripting

- **Scenario parameter sets / multi-run support**
  - Run the same scenario multiple times with different value-sets (e.g. user ranges, IP ranges) without duplicating scenario files.
  - Could be driven by a small CSV/values table or UI grid.

- **Conditional branches in scenarios**
  - Add basic conditional flow based on last response code or header values:
    - Example: `IF last_response == 4xx THEN send_retry`.
  - Integrate with existing Conditions panel and scenario tree.

- **Named timing profiles**
  - Predefined timing sets for ringing, early media, re-INVITE, and BYE sequences.
  - Scenarios reference a timing profile instead of hard-coding each `Pause` value.

### 2.3 UI / usability improvements

- **Layout presets**
  - Presets like `Compact`, `Standard`, and `Wide` that adjust:
    - SIP tree width.
    - Height of the SDP panel vs message text.
    - Font sizes and padding.

- **Detachable / dockable panels**
  - Optionally detach the Statistics panel or SDP tab into a separate window.
  - Useful for dual-monitor setups while running load scenarios.

- **Theme and accessibility improvements**
  - Expand FlatLaf usage:
    - Per-theme icons for light/dark.
    - Remember theme choice in `userSettings.xml`.
  - Improve font scaling for high-DPI displays.

### 2.4 Analysis and troubleshooting tools

- **Integrated SIP trace diff**
  - Load a captured SIP trace and diff each message against a scenario:
    - Highlight missing/extra headers and header value differences.
    - Highlight SDP differences (payloads, AVP vs SAVP, SRTP attributes).

- **Protocol health checks / validators**
  - Run rule-based checks on a scenario:
    - Missing mandatory headers (Via, From, To, Call-ID, CSeq, Contact).
    - SDP inconsistencies: offer/answer mismatches, codec mismatch, missing DTMF.
    - Transport/IP inconsistencies (From/Contact using local IP, To/Request-URI using remote IP).

### 2.5 Security & TLS/SRTP

- **TLS profile manager**
  - Multiple TLS profiles with different certificates, cipher suites, and protocol versions.
  - Per-run or per-scenario choice of TLS profile.

- **SRTP key management options**
  - Support for:
    - Static crypto keys (as now) per profile.
    - Optional key rotation or per-call keys.
  - UI for managing and testing crypto attributes.


## 3. Notes

- This document summarizes the current v2 behavior after SDP/SRTP and UI changes on 2025-11-16.
- It also outlines candidate features for a potential v3, which are **not implemented yet** but are documented here as a roadmap for future work.
