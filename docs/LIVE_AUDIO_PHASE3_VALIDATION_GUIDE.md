# Live Audio Phase 3 Validation Guide (Runtime + Interop)

This guide is the gate before SDP/session architecture work.

## Scope
- Real runtime validation only.
- RTP/SRTP interoperability, lifecycle stability, cleanup, diagnostics.
- No codec/features/UI expansion.

## 1. Local Soak Validation

From trunk root:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run_live_audio_phase3_validation.ps1 -Cycles 20 -MediaPort 41000
```

Pass expectations:
- `start_failures=0`
- `runtime_exceptions=0`
- `remaining_liveaudio_threads=0`
- diagnostics end at zeros for:
  - active sender/receiver threads
  - open sockets
  - open mic/speaker lines

## 2. Real Audio Runtime Validation (Manual)

1. Start SIP Simulator instance A and B (or SIP Simulator + softphone).
2. Configure valid RTP port/IP on both ends.
3. Place call with SAVP disabled (RTP/AVP), start Live Audio.
4. Verify:
   - two-way audio
   - no one-way audio
   - no crashes on rapid start/stop
5. Repeat with SAVP enabled and matching crypto.

## 3. Wireshark Validation

Capture filter example:

```text
udp.port == <media_port>
```

RTP validation:
- PT=8
- seq monotonic
- timestamp increment = 160
- SSRC stable per session

SRTP validation:
- encrypted payload observed
- auth-fail injections dropped
- malformed/truncated packets do not crash receiver

## 4. External Endpoint Interoperability

Test at least one external endpoint:
- SIP softphone (RTP)
- SRTP-capable endpoint (SAVP)

Record:
- audio quality
- setup success/failure
- one-way audio occurrences
- packet drops/auth failures
- endpoint-specific quirks

## 5. Required Start/Stop Stress Cases

- 20 start/stop cycles
- rapid stop/start while active audio
- stop scenario while audio streaming
- BYE/CANCEL teardown while audio active

## 6. Gate to Phase 4

Proceed to SDP-dialog media ownership only if:
- no leak signals
- no teardown crashes/deadlocks
- RTP/SRTP correctness verified in capture
- external endpoint interop is acceptable
