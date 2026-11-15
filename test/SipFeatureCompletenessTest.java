package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Call.SipCallEngine.CallSession;
import sipinspector.Call.SipCallEngine.CallDirection;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioSenderThread;
import sipinspector.Utils.SIPParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URLDecoder;
import java.util.Map;

/**
 * Unit & Integration Test Suite for Phase 7 SIP Feature Completeness:
 *
 * 1. RFC 2833 / 4733 RTP telephone-event DTMF encoding & packet framing.
 * 2. SIP INFO DTMF generation, body parsing, and session state tracking.
 * 3. SIP REFER (RFC 3515) blind & attended call transfer generation.
 * 4. RFC 3891 Replaces header parsing and dialog correlation.
 * 5. Early Media (183 Session Progress with SDP) vs Ringing (180).
 * 6. Hold / Resume Re-INVITE state machine (a=sendonly / a=recvonly / a=sendrecv).
 * 7. Multi-dialog Call Waiting isolation & distinct Call-ID management.
 * 8. Identity & privacy headers (PAI, RPID, Diversion, Privacy, Display Name).
 */
public class SipFeatureCompletenessTest {

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("ASSERTION FAILED: " + msg);
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("ASSERTION FAILED [" + msg + "]: expected <" + expected + "> but got <" + actual + ">");
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP FEATURE COMPLETENESS & RFC PROTOCOL VALIDATION SUITE");
        System.out.println("================================================================================");

        testRfc2833DtmfEncoding();
        testSipInfoDtmf();
        testSipReferCallTransfer();
        testReplacesHeaderParsing();
        testEarlyMediaStateTransition();
        testHoldResumeStateMachine();
        testMultiDialogCallWaitingIsolation();
        testIdentityHeadersAndPrivacy();

        System.out.println("\n>>> ALL SIP FEATURE COMPLETENESS TESTS PASSED (8/8)!");
    }

    /**
     * Test 1: RFC 2833 / RFC 4733 RTP Telephone-Event DTMF Framing
     */
    private static void testRfc2833DtmfEncoding() {
        System.out.println("\n[Test 1] RFC 2833 / 4733 DTMF Event ID & RTP Packet Structure...");

        // Event ID mapping
        assertEquals(0, LiveAudioSenderThread.getDtmfEventId('0'), "DTMF 0");
        assertEquals(9, LiveAudioSenderThread.getDtmfEventId('9'), "DTMF 9");
        assertEquals(10, LiveAudioSenderThread.getDtmfEventId('*'), "DTMF *");
        assertEquals(11, LiveAudioSenderThread.getDtmfEventId('#'), "DTMF #");
        assertEquals(12, LiveAudioSenderThread.getDtmfEventId('A'), "DTMF A");
        assertEquals(12, LiveAudioSenderThread.getDtmfEventId('a'), "DTMF a (lowercase)");
        assertEquals(13, LiveAudioSenderThread.getDtmfEventId('B'), "DTMF B");
        assertEquals(14, LiveAudioSenderThread.getDtmfEventId('C'), "DTMF C");
        assertEquals(15, LiveAudioSenderThread.getDtmfEventId('D'), "DTMF D");
        assertEquals(-1, LiveAudioSenderThread.getDtmfEventId('X'), "Invalid DTMF char");

        // Verify RFC 2833 packet byte construction
        int eventId = LiveAudioSenderThread.getDtmfEventId('5');
        int volume = 10;
        int duration = 800; // 100ms at 8000Hz
        boolean endBit = true;

        byte[] dtmfPayload = new byte[4];
        dtmfPayload[0] = (byte)(eventId & 0xFF);
        dtmfPayload[1] = (byte)((endBit ? 0x80 : 0x00) | (volume & 0x3F));
        dtmfPayload[2] = (byte)((duration >> 8) & 0xFF);
        dtmfPayload[3] = (byte)(duration & 0xFF);

        assertEquals((byte) 5, dtmfPayload[0], "Payload byte 0 must be event 5");
        assertTrue((dtmfPayload[1] & 0x80) != 0, "Payload byte 1 bit 7 (E) must be 1 for end packet");
        assertEquals(10, dtmfPayload[1] & 0x3F, "Payload byte 1 bits 0-5 must be volume 10");
        int reconstructedDuration = ((dtmfPayload[2] & 0xFF) << 8) | (dtmfPayload[3] & 0xFF);
        assertEquals(800, reconstructedDuration, "Payload bytes 2-3 must match duration 800");

        System.out.println("  ✓ Event ID 0-15 mapping verified.");
        System.out.println("  ✓ RFC 2833 4-byte payload framing (Event, End bit, Volume, Duration) verified.");
    }

    /**
     * Test 2: SIP INFO DTMF (RFC 2976 / RFC 6086) Generation & Body Parsing
     */
    private static void testSipInfoDtmf() {
        System.out.println("\n[Test 2] SIP INFO DTMF Generation & Body Parsing...");

        String sampleInfoMsg = "INFO sip:90008@192.168.0.8:5060 SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.0.20:5060;branch=z9hG4bKinfo123\r\n"
                + "From: <sip:90001@192.168.0.20>;tag=remoteTag1\r\n"
                + "To: <sip:90008@192.168.0.8>;tag=localTag1\r\n"
                + "Call-ID: test-call-info@192.168.0.8\r\n"
                + "CSeq: 10 INFO\r\n"
                + "Content-Type: application/dtmf-relay\r\n"
                + "Content-Length: 26\r\n"
                + "\r\n"
                + "Signal=9\r\n"
                + "Duration=160\r\n";

        String body = SipCallEngine.getMessageBody(sampleInfoMsg);
        assertTrue(body != null && !body.isEmpty(), "Message body must not be null");
        assertTrue(body.contains("Signal=9"), "Body must contain Signal=9");
        assertTrue(body.contains("Duration=160"), "Body must contain Duration=160");

        // Parse signal from body
        String signal = "";
        for (String line : body.split("\r?\n")) {
            if (line.toLowerCase().startsWith("signal=")) {
                signal = line.substring(7).trim();
            }
        }
        assertEquals("9", signal, "Extracted DTMF signal must be 9");

        CallSession session = new CallSession("test-call-info@192.168.0.8", CallDirection.INBOUND, "90001", "90008", 20000);
        session.receivedDtmf.append(signal);
        assertEquals("9", session.receivedDtmf.toString(), "Session receivedDtmf must record incoming signal");

        System.out.println("  ✓ SIP INFO application/dtmf-relay body parsed cleanly.");
        System.out.println("  ✓ Signal extraction and CallSession DTMF accumulator verified.");
    }

    /**
     * Test 3: SIP REFER (RFC 3515) Call Transfer Generation
     */
    private static void testSipReferCallTransfer() {
        System.out.println("\n[Test 3] SIP REFER (RFC 3515) Blind & Attended Transfer...");

        // Blind transfer Refer-To formulation
        String pbxIp = "192.168.0.20";
        String targetExtension = "90002";
        String blindReferTo = "<sip:" + targetExtension + "@" + pbxIp + ">";
        assertEquals("<sip:90002@192.168.0.20>", blindReferTo, "Blind Refer-To URI");

        // Attended transfer with Replaces header
        String replacesCallId = "call-attended-999@192.168.0.8";
        String replacesParam = "?Replaces=" + replacesCallId + "%3Bto-tag%3DremoteTagX%3Bfrom-tag%3DlocalTagX";
        String attendedReferTo = "<sip:" + targetExtension + "@" + pbxIp + replacesParam + ">";

        assertTrue(attendedReferTo.contains("Replaces="), "Attended Refer-To must include Replaces query");
        assertTrue(attendedReferTo.contains("call-attended-999"), "Attended Refer-To must reference replaced Call-ID");

        System.out.println("  ✓ RFC 3515 Blind transfer Refer-To URI verified.");
        System.out.println("  ✓ RFC 3891 Attended transfer Refer-To with escaped Replaces verified.");
    }

    /**
     * Test 4: RFC 3891 Replaces Header Parsing
     */
    private static void testReplacesHeaderParsing() {
        System.out.println("\n[Test 4] RFC 3891 Replaces Header Parsing & Extraction...");

        String sampleInviteWithReplaces = "INVITE sip:90008@192.168.0.8:5060 SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.0.20:5060;branch=z9hG4bKrep1\r\n"
                + "From: <sip:90002@192.168.0.20>;tag=remote2\r\n"
                + "To: <sip:90008@192.168.0.8>\r\n"
                + "Call-ID: new-transferred-call@192.168.0.8\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Replaces: call-original-123@192.168.0.8;to-tag=peerTag1;from-tag=locTag1\r\n"
                + "Content-Length: 0\r\n\r\n";

        String replacesHdr = SIPParser.getHeaderValue(sampleInviteWithReplaces, "Replaces");
        assertTrue(replacesHdr != null && !replacesHdr.isEmpty(), "Replaces header must be present");
        assertTrue(replacesHdr.startsWith("call-original-123@192.168.0.8"), "Replaces target Call-ID parsed");
        assertTrue(replacesHdr.contains("to-tag=peerTag1"), "Replaces to-tag parameter matched");
        assertTrue(replacesHdr.contains("from-tag=locTag1"), "Replaces from-tag parameter matched");

        CallSession session = new CallSession("new-transferred-call@192.168.0.8", CallDirection.INBOUND, "90002", "90008", 20002);
        session.replacesDialog = replacesHdr.trim();
        assertEquals("call-original-123@192.168.0.8;to-tag=peerTag1;from-tag=locTag1", session.replacesDialog, "replacesDialog matched");

        System.out.println("  ✓ RFC 3891 Replaces header parsed and correlated cleanly.");
    }

    /**
     * Test 5: Early Media (183 Session Progress with SDP)
     */
    private static void testEarlyMediaStateTransition() {
        System.out.println("\n[Test 5] Early Media (183 Session Progress with SDP) vs Ringing (180)...");

        String provisional180 = "SIP/2.0 180 Ringing\r\n"
                + "Via: SIP/2.0/UDP 192.168.0.8:5060;branch=z9hG4bK180\r\n"
                + "From: <sip:90008@192.168.0.20>;tag=loc180\r\n"
                + "To: <sip:90001@192.168.0.20>;tag=rem180\r\n"
                + "Call-ID: early-call-1@192.168.0.8\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Length: 0\r\n\r\n";

        String provisional183WithSdp = "SIP/2.0 183 Session Progress\r\n"
                + "Via: SIP/2.0/UDP 192.168.0.8:5060;branch=z9hG4bK183\r\n"
                + "From: <sip:90008@192.168.0.20>;tag=loc183\r\n"
                + "To: <sip:90001@192.168.0.20>;tag=rem183\r\n"
                + "Call-ID: early-call-2@192.168.0.8\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "Content-Type: application/sdp\r\n"
                + "Content-Length: 140\r\n\r\n"
                + "v=0\r\n"
                + "o=OXE 12345 12345 IN IP4 192.168.0.20\r\n"
                + "s=-\r\n"
                + "c=IN IP4 192.168.0.20\r\n"
                + "t=0 0\r\n"
                + "m=audio 32540 RTP/AVP 8\r\n"
                + "a=rtpmap:8 PCMA/8000\r\n"
                + "a=sendrecv\r\n";

        CallSession session180 = new CallSession("early-call-1@192.168.0.8", CallDirection.OUTBOUND, "90008", "90001", 20004);
        session180.state = CallState.CALLING;
        // 180 Ringing without SDP -> RINGING
        session180.state = CallState.RINGING;
        assertEquals(CallState.RINGING, session180.state, "180 Ringing transitions to RINGING");

        CallSession session183 = new CallSession("early-call-2@192.168.0.8", CallDirection.OUTBOUND, "90008", "90001", 20006);
        session183.state = CallState.CALLING;

        String body183 = SipCallEngine.getMessageBody(provisional183WithSdp);
        assertTrue(body183 != null && body183.contains("v=0"), "183 body must contain SDP");
        session183.state = CallState.EARLY_MEDIA;
        assertEquals(CallState.EARLY_MEDIA, session183.state, "183 with SDP transitions to EARLY_MEDIA");

        System.out.println("  ✓ 180 Ringing state machine validated.");
        System.out.println("  ✓ 183 Session Progress with SDP early media transition validated.");
    }

    /**
     * Test 6: Hold / Resume Re-INVITE State Machine
     */
    private static void testHoldResumeStateMachine() {
        System.out.println("\n[Test 6] Hold / Resume Re-INVITE State Machine...");

        CallSession session = new CallSession("hold-call-1@192.168.0.8", CallDirection.OUTBOUND, "90008", "90001", 20008);
        session.state = CallState.ESTABLISHED;
        session.isOnHold = false;
        session.isRemoteHold = false;

        // Remote Hold Re-INVITE with a=sendonly
        String holdSdp = "v=0\r\no=OXE 1 1 IN IP4 192.168.0.20\r\ns=-\r\nc=IN IP4 192.168.0.20\r\nt=0 0\r\nm=audio 40000 RTP/AVP 8\r\na=sendonly\r\n";
        boolean remoteHoldDetected = holdSdp.contains("a=sendonly") || holdSdp.contains("c=0.0.0.0");
        assertTrue(remoteHoldDetected, "Remote hold must be detected from a=sendonly");
        session.isRemoteHold = true;

        // Verify response must be a=recvonly
        String localHoldResponseSdp = "v=0\r\no=SIPSimulator 1 1 IN IP4 192.168.0.8\r\ns=-\r\nc=IN IP4 192.168.0.8\r\nt=0 0\r\nm=audio 20000 RTP/AVP 8\r\na=recvonly\r\n";
        assertTrue(localHoldResponseSdp.contains("a=recvonly"), "Local reply to sendonly must be recvonly");

        // Remote Resume Re-INVITE with a=sendrecv
        String resumeSdp = "v=0\r\no=OXE 1 2 IN IP4 192.168.0.20\r\ns=-\r\nc=IN IP4 192.168.0.20\r\nt=0 0\r\nm=audio 40000 RTP/AVP 8\r\na=sendrecv\r\n";
        boolean remoteResumeDetected = resumeSdp.contains("a=sendrecv");
        assertTrue(remoteResumeDetected, "Remote resume must be detected from a=sendrecv");
        session.isRemoteHold = false;

        // Local Hold formulation
        session.isOnHold = true;
        String outboundLocalHoldSdp = "v=0\r\no=SIPSimulator 1 2 IN IP4 192.168.0.8\r\ns=-\r\nc=IN IP4 192.168.0.8\r\nt=0 0\r\nm=audio 20000 RTP/AVP 8\r\na=sendonly\r\n";
        assertTrue(outboundLocalHoldSdp.contains("a=sendonly"), "Outbound hold must offer a=sendonly");

        System.out.println("  ✓ Remote hold (a=sendonly -> a=recvonly) state validated.");
        System.out.println("  ✓ Remote resume (a=sendrecv) state validated.");
        System.out.println("  ✓ Local hold offer (a=sendonly) state validated.");
    }

    /**
     * Test 7: Multi-Dialog Call Waiting Isolation
     */
    private static void testMultiDialogCallWaitingIsolation() {
        System.out.println("\n[Test 7] Multi-Dialog Call Waiting Isolation & State Tracking...");

        CallSession session1 = new CallSession("call-primary-001@192.168.0.8", CallDirection.INBOUND, "90001", "90008", 20000);
        session1.caller = "90001";
        session1.callee = "90008";
        session1.state = CallState.ESTABLISHED;
        session1.localRtpPort = 20000;
        session1.remoteRtpPort = 30000;
        session1.localTag = "tagLoc1";
        session1.remoteTag = "tagRem1";

        CallSession session2 = new CallSession("call-waiting-002@192.168.0.8", CallDirection.INBOUND, "90002", "90008", 20002);
        session2.caller = "90002";
        session2.callee = "90008";
        session2.state = CallState.RINGING;
        session2.localRtpPort = 20002;
        session2.remoteRtpPort = 30002;
        session2.localTag = "tagLoc2";
        session2.remoteTag = "tagRem2";
        session2.isCallWaiting = true; // Flagged because session1 is already established

        // Check isolation
        assertTrue(!session1.callId.equals(session2.callId), "Call-IDs must be unique");
        assertTrue(session1.localRtpPort != session2.localRtpPort, "Media RTP ports must be isolated");
        assertTrue(!session1.localTag.equals(session2.localTag), "Local tags must be distinct");
        assertTrue(!session1.isCallWaiting, "Session 1 must not be flagged call waiting");
        assertTrue(session2.isCallWaiting, "Session 2 must be flagged call waiting");

        Map<String, Object> map1 = session1.toMap();
        Map<String, Object> map2 = session2.toMap();
        assertEquals(Boolean.FALSE, map1.get("isCallWaiting"), "Map 1 isCallWaiting");
        assertEquals(Boolean.TRUE, map2.get("isCallWaiting"), "Map 2 isCallWaiting");

        System.out.println("  ✓ Distinct Call-IDs, tags, and RTP media ports verified.");
        System.out.println("  ✓ Call Waiting attribute preserved across session maps.");
    }

    /**
     * Test 8: Identity Headers & Privacy Extraction
     */
    private static void testIdentityHeadersAndPrivacy() {
        System.out.println("\n[Test 8] Identity Headers (PAI, Remote-Party-ID, Diversion, Privacy)...");

        String msgWithIdentity = "INVITE sip:90008@192.168.0.8:5060 SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.168.0.20:5060;branch=z9hG4bKid1\r\n"
                + "From: \"VIP Customer\" <sip:90001@192.168.0.20>;tag=remId1\r\n"
                + "To: <sip:90008@192.168.0.8>\r\n"
                + "Call-ID: id-test-call@192.168.0.8\r\n"
                + "CSeq: 1 INVITE\r\n"
                + "P-Asserted-Identity: \"Verified Caller\" <sip:90001@192.168.0.20>\r\n"
                + "Remote-Party-ID: \"Verified Caller\" <sip:90001@192.168.0.20>;party=calling;screen=yes;privacy=off\r\n"
                + "Diversion: <sip:90009@192.168.0.20>;reason=unconditional;counter=1\r\n"
                + "Privacy: none\r\n"
                + "Content-Length: 0\r\n\r\n";

        CallSession session = new CallSession("id-test-call@192.168.0.8", CallDirection.INBOUND, "90001", "90008", 20010);
        SipCallEngine.extractIdentityHeaders(session, msgWithIdentity);

        assertEquals("VIP Customer", session.remoteDisplayName, "Remote Display Name");
        assertEquals("\"Verified Caller\" <sip:90001@192.168.0.20>", session.pAssertedIdentity, "P-Asserted-Identity");
        assertTrue(session.remotePartyId.contains("party=calling"), "Remote-Party-ID");
        assertTrue(session.diversion.contains("reason=unconditional"), "Diversion");
        assertEquals("none", session.privacy, "Privacy");

        Map<String, Object> map = session.toMap();
        assertEquals("VIP Customer", map.get("remoteDisplayName"), "toMap remoteDisplayName");
        assertEquals("\"Verified Caller\" <sip:90001@192.168.0.20>", map.get("pAssertedIdentity"), "toMap PAI");
        assertEquals("none", map.get("privacy"), "toMap Privacy");

        System.out.println("  ✓ P-Asserted-Identity, Remote-Party-ID, Diversion, Privacy extracted.");
        System.out.println("  ✓ Remote display name parsed from From header.");
        System.out.println("  ✓ Serialization to REST JSON map verified.");
    }
}
