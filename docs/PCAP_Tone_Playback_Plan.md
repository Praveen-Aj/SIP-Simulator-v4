# SIP Simulator v2.1 – PCAP Tone Playback Plan

## 1. Purpose

Define how SIP Simulator v2.1 will use PCAP captures to play RTP audio (tones, announcements) as part of SIP scenarios, using the existing `cmd_PlayPcap` command and Commands tab.

Target tones:
- Ringback tone (RBT / early media)
- Conversation tone (speech / comfort noise)
- Hold / MOH tone
- Guide / IVR-style announcement

> Note: As of this plan, **no tone PCAP files exist yet**. They must be captured or generated and placed under the `pcap` folder before these scenarios will produce audio.

---

## 2. Existing capabilities in the code

### 2.1 Commands tab (UI)

Class: `sipinspector.ControlTabs.CommandsPanel`

Key elements:
- **Pause** – inserts `cmd_Pause=<ms>` into a scenario.
- **Play pcap** – inserts `cmd_PlayPcap=<path>;continuous=true|false`.
- **Set Remote** – inserts `cmd_setRemote=<ip:port>`.
- **New Dialog** – inserts `cmd_NewDialog`.

For `Play pcap`:
- File path is chosen via a file chooser; value written as `cmd_PlayPcap=<full path>`.
- `Cont.` checkbox controls `continuous=true` vs `continuous=false`.

### 2.2 Scenario parsing

Class: `sipinspector.ScenarioEntries.ScenarioEntry`

- Lines starting with `cmd_PlayPcap=` are parsed into `CommandPlayPcapEntry` objects:
  - `TYPE.COMMAND_PLAYPCAP`
  - `DIRECTION.NONE`

### 2.3 CommandPlayPcapEntry

Class: `sipinspector.ScenarioEntries.CommandPlayPcapEntry`

Behavior:
- Parses `cmd_PlayPcap=<filePath>[;continuous=true|false]`.
- Stores:
  - `path` – full/relative PCAP file path.
  - `filename` – just the file name (for display).
  - `continuousRTP` – defaults to `true` unless explicitly `continuous=false`.
- `processEntry(SIPCall call, ScenarioProgressDialog dialog)`:
  - Increments expected count and updates statistics.
  - Calls `dialog.getRTPThread().addRemoteClient(call.getRemoteClient())`.
  - If RTP thread is not already running:
    - `dialog.startRTPThread(path, continuousRTP)`.
  - Advances to next scenario entry.

### 2.4 ScenarioProgressDialog and RTPSenderThread

Class: `sipinspector.ScenarioProgressDialog`

- Creates an `RTPSenderThread` per scenario run in `spawnThreads()`.
- `startRTPThread(String path, boolean continuousRTP)` configures and starts RTP playback using the PCAP file.

Class: `sipinspector.Threads.RTPSenderThread`

Responsibilities:
- Uses **Jpcap** (`JpcapCaptor`) to read packets from the capture file.
- Sends RTP packets via `DatagramSocket` to all configured `RemoteClient` destinations.
- Respects `continuousRTP` flag:
  - `false`: plays file once, then pauses/stops based on scenario.
  - `true`: loops the PCAP file by reopening at EOF until scenario ends.
- Uses `GlobalSettings` for:
  - Local IP (`getLocalIP()`)
  - Local media port (`getMediaPort()`)
  - QoS / DSCP (`getSetting("QoS")`).

### 2.5 Existing PCAP-based scenarios

Under `scenarios\archive_default` there are sample usages:

- `Call_client.txt`
- `Call_server.txt`
- `Server_conditional_registration.txt`

They contain entries like:

```text
cmd_PlayPcap=.\pcap\test.pcap
cmd_PlayPcap=.\pcap\test.pcap;continuous=true
```

These demonstrate that the PCAP playback path is already end-to-end wired.

---

## 3. Standard PCAP assets for tones

All tone PCAP files should live under:

```text
SIPInspector\trunk\pcap\
```

Recommended filenames and purposes (none exist yet; they must be created/captured):

| Tone type          | Suggested filename             | Usage                                           | Notes |
|--------------------|--------------------------------|-------------------------------------------------|-------|
| Ringback (RBT)     | `RBT_tone.pcap`               | Early media / ringing phase                     | Likely continuous loop |
| Conversation tone  | `Conversation_tone.pcap`      | Speech/comfort noise during active call         | Continuous or finite |
| Hold / MOH         | `Hold_tone.pcap`              | Music-on-hold or simple hold beep               | Continuous loop |
| Guide / IVR prompt | `Guide_tone.pcap`             | Short announcement / instruction message        | Typically one-shot |
| Custom test tone   | `Test_tone_XXX.pcap`          | Miscellaneous testing                            | Optional |

**Action required (future):**
- Capture or synthesize these PCAP files using a lab environment or media generator.
- Ensure codec aligns with test environment (e.g. G.711 µ-law).
- Place them under `pcap\` with the agreed filenames.

### 3.1 Built-in WAV → PCAP workflow (Windows-friendly)

To keep everything reproducible inside the repo, the following folders/scripts exist:

```
SIPInspector\trunk\tones\           # Stores source WAVs (RBT.wav, RBT_8k.wav, etc.)
SIPInspector\trunk\tools\generate_tone_pcaps.py  # WAV→PCAP converter
SIPInspector\trunk\pcap\            # Output PCAPs (RBT_tone.pcap, ...)
```

Steps to regenerate all tone PCAPs:

1. **Generate speech WAVs via PowerShell TTS** (run once per tone):
   ```powershell
   cd "C:\Users\<you>\Desktop\tones"
   # Use the script snippet from this doc (Section 2) to speak the phrases.
   ```
2. **Normalize to 8 kHz mono with ffmpeg**:
   ```powershell
   $names = "RBT","Hold","Guide","Conversation"
   foreach ($name in $names) {
       ffmpeg -y -i "$name.wav" -ar 8000 -ac 1 "${name}_8k.wav"
   }
   ```
3. **Convert WAVs → PCAP using the helper script**:
   ```powershell
   cd "SIPInspector\trunk"
   python tools\generate_tone_pcaps.py `
       --input-dir tones `
       --output-dir pcap `
       --prefix "_8k"
   ```

The script emits `pcap\RBT_tone.pcap`, `pcap\Hold_tone.pcap`, etc. and can be rerun any time you refresh the WAVs.

---

## 4. Scenario patterns using PCAP tones

### 4.1 Generic requirements for any PCAP-based scenario

1. **Remote RTP destination must be valid**
   - Either global Remote RTP configuration in Options.
   - Or explicit command in the scenario: `cmd_setRemote=<IP>:<port>`.
2. **PCAP path must be correct**
   - Example: `cmd_PlayPcap=.\pcap\RBT_tone.pcap;continuous=true`.
3. **Jpcap must be installed and accessible**, as per existing Play Pcap feature.

### 4.2 RBT tone scenario (early media)

Use-case: play RBT tone (ringback) towards the UE while call is in ringing/early media state.

Typical flow (simplified):

1. INVITE/180/183 exchange to establish media.
2. Once remote SDP/ports are known and Remote RTP is configured:
   - Optionally insert `cmd_setRemote=<IP>:<port>`.
   - Insert `cmd_PlayPcap=.\\pcap\\RBT_tone.pcap;continuous=true`.
3. RBT plays until:
   - Final 200 OK / ACK and answer; or
   - Call fails; or
   - Scenario completes.

> Reference implementation: `scenarios/Incoming_basic_call_with_RBT_tone.txt` uses `cmd_PlayPcap=.\pcap\RBT_tone.pcap;continuous=true` right after the 180 Ringing response. Its paired values file is `values/incoming_basic_call_with_RBT_tone_values.txt`.

### 4.3 Conversation tone scenario

Use-case: simulate voice/media during an established call.

Typical flow:

1. Call setup to 200 OK + ACK (media fully established).
2. After ACK, insert:
   - `cmd_setRemote=<IP>:<port>` if necessary.
   - `cmd_PlayPcap=.\\pcap\\Conversation_tone.pcap;continuous=true` (or `continuous=false` if finite sample).
3. Optional pause or hangup after some time (`cmd_Pause=<ms>` then BYE).

### 4.4 Hold / MOH tone scenario

Use-case: emulate placing a call on hold and playing MOH.

Typical flow:

1. Establish call as usual (INVITE, 200 OK, ACK).
2. When placing on hold (e.g. sending re-INVITE with hold SDP):
   - Ensure `cmd_setRemote` is configured appropriately.
   - Insert `cmd_PlayPcap=.\\pcap\\Hold_tone.pcap;continuous=true`.
3. When resuming from hold:
   - Either end the scenario (and RTP playback stops with call), or
   - Use a separate scenario for off-hold leg.

### 4.5 Guide / IVR announcement scenario

Use-case: play a recorded prompt, e.g. “Your call is being recorded” or menu guidance.

Typical flow:

1. Establish media (e.g. 183/200 with SDP).
2. Insert:
   - `cmd_setRemote=<IP>:<port>` if needed.
   - `cmd_PlayPcap=.\\pcap\\Guide_tone.pcap;continuous=false` (one-shot).
3. After playback, proceed with other SIP signaling (e.g. BYE, re-INVITE, etc.).

---

## 5. Using the Commands tab to insert PCAP commands

For **any** scenario (existing template or new):

1. Open the scenario in the Scenario & Values tab.
2. Select the point in the message tree where PCAP playback should begin (after SDP is known and Remote RTP is valid).
3. Switch to the **Commands** tab.
4. To set RTP destination (if not configured globally):
   - Select **Set Remote**.
   - Enter `IP:port` of the remote media endpoint.
   - Add the command to the scenario tree.
5. To play PCAP:
   - Select **Play pcap**.
   - Use `...` button to browse to a file under `pcap\` (e.g. `.\pcap\RBT_tone.pcap`).
   - Check **Cont.** if tone should loop (RBT, hold, MOH), or uncheck for one-shot (guide/announcement).
   - Add the command to the scenario tree.
6. Save scenario and run it via Scenario & Values tab.

The resulting scenario lines will look like:

```text
cmd_setRemote=10.0.0.50:4000
cmd_PlayPcap=.\pcap\RBT_tone.pcap;continuous=true
```

---

## 6. Implementation phases

### Phase 1 – Prepare PCAP tone assets (pending)

- Capture/generate at least the following PCAPs:
  - `pcap\RBT_tone.pcap`
  - `pcap\Hold_tone.pcap`
  - `pcap\Conversation_tone.pcap`
  - `pcap\Guide_tone.pcap`
- Validate:
  - Proper codec (e.g. G.711) for test environment.
  - PCAP contains only the desired media flow (clean trace).

### Phase 2 – Create example scenarios using tone PCAPs

Proposed template scenarios (names TBD):

- `Incoming_call_with_RBT_pcap.txt`
- `Outgoing_call_with_conversation_pcap.txt`
- `Hold_resume_with_MOH_pcap.txt`
- `IVR_guide_with_pcap_prompt.txt`

Each scenario should:
- Configure Remote RTP (via Options or `cmd_setRemote=`).
- Insert `cmd_PlayPcap` at the right point in the flow.
- Optionally use `cmd_Pause` to control call duration before BYE.

### Phase 3 – Testing and validation

For each PCAP-based scenario:

1. Configure Remote RTP to a test endpoint (e.g. softphone, media analyzer, or Wireshark box).
2. Run the scenario from Scenario & Values tab.
3. Confirm:
   - RTP is received at the expected IP/port.
   - Audio/tone matches the intended behavior (RBT, hold, etc.).
   - `continuous=true` loops as expected until scenario completes.
   - `continuous=false` plays once and then stops.
4. Log any issues and adjust PCAPs or scenario placement accordingly.

---

## 7. Open items / decisions

- **PCAP source**: decide how and where to capture or synthesize each tone PCAP.
- **Codec standardization**: pick a default codec (e.g. G.711 µ-law) and stick to it for all tone PCAPs.
- **Scenario naming convention**: define a clear naming scheme for PCAP-based scenarios (e.g. `*_pcap` suffix).
- **Documentation**: once PCAPs and scenarios are finalized, update README/docs to mention available PCAP-tone scenarios and how to use them.

This document is the high-level design and usage guide for PCAP-based tones in SIP Simulator v2.1. Actual media files and scenarios should follow this plan.
