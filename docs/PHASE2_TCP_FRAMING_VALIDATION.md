# Phase 2: TCP SIP Message Framing and Stream Reliability Validation Report

**Date:** 2026-11-14  
**Project:** SIP Simulator v4  
**Git Branch:** `stability/feature-validation`  
**Base Commit:** `590e4e5`  

---

## 1. Executive Summary & Offline Constraints

Phase 2 addressed a critical protocol-level defect documented in `OPEN_ISSUES.md` (ISSUE-002): **TCP SIP message framing and byte stream buffering**.

> [!NOTE]
> **Operational Constraint:** The Alcatel-Lucent OXE PBX remains offline. As mandated, no PBX connectivity was attempted. All validation was performed deterministically using automated local test harnesses and loopback TCP sockets.
> 
> **Strict Regression Protection:**
> - Zero changes to UDP transport (`UDPClientSocket.java`, `UDPServerSocket.java`).
> - Zero changes to TLS implementation (`TLSClientSocket.java`, `security/`).
> - Zero changes to RTP, audio processing, codec implementations, or PCAP engines.
> - Preserved full Java 8 compatibility and modular architecture.

---

## 2. Root Cause Analysis & Defect Identification

### 2.1 The Core Framing Defect in `MyPacket.java`
Prior to this phase, `MyPacket.parseBytesIntoSIPMsg(byte[] rcvdBytes)` attempted to parse SIP messages directly from raw byte chunks, with two fatal flaws:

1. **Premature Completion on Split Headers (Line 114):**
   ```java
   // In original MyPacket.java:
   int pos = tmpByteHolder.indexOf("\r\n\r\n");
   if (pos == -1) {
       messageTxt = tmpByteHolder;
       bytes = messageTxt.getBytes();
       len = bytes.length;
       complete = true; // BUG: Incorrectly marked packet complete before CRLF CRLF was received!
       return rcvdBytes.length;
   }
   ```
   *Impact:* When a TCP read delivered a partial header (e.g. `INVITE sip:... Via:...`), `complete` was flagged as `true`. The receiver thread immediately forwarded the truncated header chunk to the SIP parser, which failed with parse exceptions or dropped the transaction.

2. **Discarding Trailing Messages (Line 137):**
   ```java
   // In original MyPacket.java:
   messageTxt = tmpByteHolder.substring(0, pos + 4 + contLenInt);
   bytes = messageTxt.getBytes();
   int bytesProcessed = pos + 4 + contLenInt - len;
   len = bytes.length;
   complete = true;
   return bytesProcessed;
   ```
   *Impact:* In `TCPClientSocket.receive()`, `MyPacket` was instantiated as a local variable discarded after every `receive()` invocation. If a single TCP read contained multiple SIP messages (e.g., `100 Trying` immediately followed by `180 Ringing`), or a complete message plus the start of a subsequent message, the return value `bytesProcessed` was ignored. The trailing bytes beyond `pos + 4 + contLenInt` were permanently dropped from the socket stream.

3. **Character vs. Byte Mismatch for Content-Length:**
   `tmpByteHolder.length()` used Java `String.length()` (character count) instead of byte count. If a SIP body contained multi-byte UTF-8 characters, slicing with `substring` corrupted the payload boundary.

---

## 3. Technical Design: Dedicated Stream Framer (`SipStreamFramer.java`)

To ensure clean separation of concerns and avoid duplicating byte-stream assembly across socket classes, a dedicated component was created: **`sipinspector.Utils.SipStreamFramer`**.

### Key Architectural Characteristics:
1. **Internal Stream Accumulator:** Uses a synchronized `ByteArrayOutputStream` to buffer incoming bytes across arbitrary TCP chunk boundaries.
2. **Boundary Detection:** Scans raw byte sequences for double-CRLF (`\r\n\r\n` or `\n\n`) header boundaries. Before the terminator is received, no message is emitted.
3. **Robust Header Extraction:** Robustly extracts `Content-Length` (or compact `l:`) regardless of header casing or surrounding whitespace.
4. **Byte-Accurate Slicing:** Measures the body using byte length, ensuring multi-byte binary or Unicode SDP bodies are never truncated.
5. **FIFO Message Queue:** Emits assembled SIP messages in strict chronological order via `pollNextMessage()`.
6. **Residual Stream Retention:** Preserves all trailing bytes in the stream buffer for the subsequent socket read.
7. **Clean Socket Reset:** `framer.reset()` clears stale buffers on disconnect or socket closure.

---

## 4. Integration into `TCPClientSocket.java`

`TCPClientSocket` was updated with conservative, targeted integration:
- Holds an instance of `SipStreamFramer`.
- `receive()` first checks if `streamFramer.pollNextMessage()` has a buffered message from a previous multi-message read. If so, it returns immediately without blocking.
- Otherwise, reads available bytes from `initialChannel` and feeds them to `streamFramer`.
- Fixed Java 8 NIO compatibility: Cast `buffer` to `(java.nio.Buffer)` when calling `clear()` and `flip()` to prevent JDK 9+ covariance linkage errors (`NoSuchMethodError: ByteBuffer.clear()`).
- On socket closure (`close()`), calls `streamFramer.reset()`.

---

## 5. Automated Test Suite (`TcpSipFramingValidationTest.java`)

A comprehensive, deterministic test harness was developed covering all required framing scenarios:

| Test Case | Scenario Description | Result | Details |
| :--- | :--- | :--- | :--- |
| **Test A** | Complete message in one read | **PASS** | Exact message extracted, queue empty |
| **Test B** | Header split across reads | **PASS** | Waited for header terminator before emitting |
| **Test C** | Body split across reads | **PASS** | Waited for complete Content-Length bytes |
| **Test D** | Multiple messages in one read | **PASS** | 3 messages (100, 180, 200) extracted sequentially |
| **Test E** | Complete message + partial message | **PASS** | First message emitted, partial retained in buffer |
| **Test F** | Empty body (`Content-Length: 0`) | **PASS** | Emitted immediately after header terminator |
| **Test G** | Read timeout / buffer continuity | **PASS** | Retained partial bytes across read timeouts |
| **Test H** | Live loopback TCP channel | **PASS** | Assembled 3 split TCP packet writes over loopback |
| **Test I** | Header variations (casing, compact `l`) | **PASS** | `content-length: 128`, `l: 45`, whitespace handled |
| **Test J** | Binary-safe UTF-8 body length | **PASS** | Exact multi-byte body length preserved |
| **Test K** | `MyPacket` backward compatibility | **PASS** | Legacy `isComplete()` correctly flags `false` on split header |

---

## 6. Full Regression Test Verification

Executed with bundled 32-bit Temurin JRE 8 and Node.js v24:

1. **Clean Java Build (`build.bat`):** **PASS** (189 classes compiled, `SIP_Simulator_v4.jar` packaged).
2. **TCP SIP Framing Validation (`TcpSipFramingValidationTest`):** **11 / 11 PASS**.
3. **Portability Suite (`PortabilityValidationTest`):** **10 / 10 PASS**.
4. **Offline Protocol Suite (`OfflineValidationSuite`):** **6 / 6 PASS**.
5. **Middle-Tier Assertion Engine (`AssertionUnitTest`):** **8 / 8 PASS**.
6. **Display Scaling Suite (`DisplayScalingValidationTest`):** **6 / 6 PASS**.
7. **React Frontend Build (`npm run build`):** **PASS** (Built in 444ms).
8. **Express Server Backend (`node -c server.js`):** **PASS** (Syntax clean).

---

## 7. Files Modified & Added

- `src/sipinspector/Utils/SipStreamFramer.java` *(New stream framer utility)*
- `src/sipinspector/Socket/TCPClientSocket.java` *(Integrated stream framer, Java 8 buffer cast)*
- `src/sipinspector/MyPacket.java` *(Added byte constructor, corrected incomplete flag on split headers)*
- `test/TcpSipFramingValidationTest.java` *(New 11-test automated framing suite)*
- `docs/OPEN_ISSUES.md` *(Updated ISSUE-002 to RESOLVED)*
- `docs/REGRESSION_TEST_REPORT.md` *(Updated)*
- `docs/DEVELOPMENT_ROADMAP.md` *(Updated)*
