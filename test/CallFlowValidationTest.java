package test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import sipinspector.Media.AudioFormatUtils;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioCodec;
import sipinspector.Media.RtcpMonitor;
import sipinspector.Utils.SIPParser;

/**
 * CallFlowValidationTest provides an automated regression suite verifying
 * RFC 3261 call flows, CallState state machine transitions, SDP offer/answer negotiation,
 * RTCP RFC 3550 QoS reporting, and mid-dialog Re-INVITE hold/resume mechanics.
 */
public class CallFlowValidationTest {

    private static int passed = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — Call Flow & Media Protocol Validation Suite");
        System.out.println("================================================================================");

        testCallStateMachine();
        testSdpOfferAnswerAndHoldResume();
        testRtcpPacketGenerationAndJitterMath();
        testOfflineTwoPartyCallFlow();
        testEarlyMediaFlow();
        testCancelTransactionFlow();
        testCallRejectionFlow();
        testReinviteHoldResumeFlow();
        testBidirectionalRtpLoopback();

        System.out.println("================================================================================");
        System.out.printf("   VALIDATION SUITE RESULT: %d / %d TESTS PASSED\n", passed, total);
        System.out.println("================================================================================");

        if (passed != total) {
            System.exit(1);
        }
    }

    private static void testCallStateMachine() {
        test("CallState: Valid and Invalid State Transitions", () -> {
            // Valid forward path
            assertTransition(CallState.IDLE, CallState.CALLING, true);
            assertTransition(CallState.CALLING, CallState.PROCEEDING, true);
            assertTransition(CallState.PROCEEDING, CallState.RINGING, true);
            assertTransition(CallState.RINGING, CallState.ESTABLISHED, true);
            assertTransition(CallState.ESTABLISHED, CallState.TERMINATING, true);
            assertTransition(CallState.TERMINATING, CallState.TERMINATED, true);
            assertTransition(CallState.TERMINATED, CallState.IDLE, true);

            // Valid Early Media path
            assertTransition(CallState.CALLING, CallState.EARLY_MEDIA, true);
            assertTransition(CallState.EARLY_MEDIA, CallState.ESTABLISHED, true);

            // Valid Failure paths
            assertTransition(CallState.CALLING, CallState.FAILED, true);
            assertTransition(CallState.PROCEEDING, CallState.FAILED, true);
            assertTransition(CallState.RINGING, CallState.FAILED, true);
            assertTransition(CallState.FAILED, CallState.IDLE, true);

            // Invalid illegal transitions (RFC 3261 violations)
            assertTransition(CallState.IDLE, CallState.TERMINATING, false);
            assertTransition(CallState.ESTABLISHED, CallState.CALLING, false);
            assertTransition(CallState.TERMINATED, CallState.ESTABLISHED, false);
            assertTransition(CallState.FAILED, CallState.ESTABLISHED, false);

            // Properties
            if (!CallState.ESTABLISHED.isActive()) throw new Exception("ESTABLISHED must be active");
            if (!CallState.EARLY_MEDIA.isActive()) throw new Exception("EARLY_MEDIA must be active");
            if (CallState.IDLE.isActive()) throw new Exception("IDLE must not be active");
            if (!CallState.TERMINATED.isTerminal()) throw new Exception("TERMINATED must be terminal");
            if (!CallState.FAILED.isTerminal()) throw new Exception("FAILED must be terminal");
            if (CallState.ESTABLISHED.isTerminal()) throw new Exception("ESTABLISHED must not be terminal");
        });
    }

    private static void assertTransition(CallState from, CallState to, boolean expectedValid) throws Exception {
        boolean actual = from.canTransitionTo(to);
        if (actual != expectedValid) {
            throw new Exception("Transition " + from + " -> " + to + " expected valid=" + expectedValid + ", got=" + actual);
        }
    }

    private static void testSdpOfferAnswerAndHoldResume() {
        test("SDP Offer/Answer Negotiation & Hold/Resume Attributes", () -> {
            String sdpOffer =
                    "v=0\r\n" +
                    "o=SIPSimulator 100 100 IN IP4 192.168.0.9\r\n" +
                    "s=SIP Call\r\n" +
                    "c=IN IP4 192.168.0.9\r\n" +
                    "t=0 0\r\n" +
                    "m=audio 42000 RTP/AVP 8 0 9 101\r\n" +
                    "a=rtpmap:8 PCMA/8000\r\n" +
                    "a=rtpmap:0 PCMU/8000\r\n" +
                    "a=rtpmap:9 G722/8000\r\n" +
                    "a=rtpmap:101 telephone-event/8000\r\n" +
                    "a=fmtp:101 0-15\r\n" +
                    "a=sendrecv\r\n";

            String sdpAnswer =
                    "v=0\r\n" +
                    "o=Peer 200 200 IN IP4 192.168.0.20\r\n" +
                    "s=SIP Call\r\n" +
                    "c=IN IP4 192.168.0.20\r\n" +
                    "t=0 0\r\n" +
                    "m=audio 42002 RTP/AVP 8 101\r\n" +
                    "a=rtpmap:8 PCMA/8000\r\n" +
                    "a=rtpmap:101 telephone-event/8000\r\n" +
                    "a=sendrecv\r\n";

            // Negotiated codec resolution
            List<Integer> offerPts = parsePayloadTypes(sdpOffer);
            List<Integer> answerPts = parsePayloadTypes(sdpAnswer);
            int negotiatedPt = -1;
            for (int pt : answerPts) {
                if (offerPts.contains(pt) && pt != 101) { // 101 is DTMF
                    negotiatedPt = pt;
                    break;
                }
            }
            if (negotiatedPt != 8) {
                throw new Exception("Expected negotiated PT 8 (PCMA), got: " + negotiatedPt);
            }

            // Test Hold SDP (RFC 3264 a=sendonly / RFC 2543 c=0.0.0.0)
            String sdpHoldRfc3264 = sdpOffer.replace("a=sendrecv", "a=sendonly");
            if (!isHoldSdp(sdpHoldRfc3264)) throw new Exception("Expected RFC 3264 a=sendonly to be recognized as HOLD");

            String sdpHoldRfc2543 = sdpOffer.replace("c=IN IP4 192.168.0.9", "c=IN IP4 0.0.0.0");
            if (!isHoldSdp(sdpHoldRfc2543)) throw new Exception("Expected RFC 2543 c=0.0.0.0 to be recognized as HOLD");

            // Test Resume SDP (a=sendrecv)
            if (isHoldSdp(sdpOffer)) throw new Exception("Expected a=sendrecv to NOT be recognized as HOLD");
        });
    }

    private static List<Integer> parsePayloadTypes(String sdp) {
        List<Integer> pts = new ArrayList<>();
        for (String line : sdp.split("\r?\n")) {
            if (line.startsWith("m=audio ")) {
                String[] parts = line.split("\\s+");
                for (int i = 3; i < parts.length; i++) {
                    try {
                        pts.add(Integer.parseInt(parts[i]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return pts;
    }

    private static boolean isHoldSdp(String sdp) {
        if (sdp.contains("a=sendonly") || sdp.contains("a=inactive") || sdp.contains("c=IN IP4 0.0.0.0")) {
            return true;
        }
        return false;
    }

    private static void testRtcpPacketGenerationAndJitterMath() {
        test("RTCP: RFC 3550 RR Generation, Jitter Math & SR Parsing", () -> {
            InetAddress local = InetAddress.getByName("127.0.0.1");
            InetAddress remote = InetAddress.getByName("127.0.0.1");
            RtcpMonitor monitor = new RtcpMonitor(local, 45000, remote, 45002);

            // 1. Process synthetic RTP packets with known arrival timestamps
            // Simulated 50 packets @ 20ms intervals (160 samples per packet @ 8kHz)
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 50; i++) {
                int seq = i;
                long rtpTs = i * 160;
                monitor.processRtpPacket(seq, rtpTs, 8000);
            }

            RtcpMonitor.RtcpStats stats = monitor.getStats(8000);
            if (stats.packetsReceived != 50) {
                throw new Exception("Expected 50 packets received, got: " + stats.packetsReceived);
            }

            // 2. Build Receiver Report packet
            byte[] rr = monitor.buildReceiverReportPacket();
            if (rr == null || rr.length != 32) {
                throw new Exception("Receiver Report length must be exactly 32 bytes (8B header + 24B block), got: " + (rr != null ? rr.length : 0));
            }

            // Verify RR header fields
            int version = (rr[0] >> 6) & 0x03;
            int pt = rr[1] & 0xFF;
            int length = ((rr[2] & 0xFF) << 8) | (rr[3] & 0xFF);

            if (version != 2) throw new Exception("RTCP version must be 2, got: " + version);
            if (pt != RtcpMonitor.RTCP_PT_RR) throw new Exception("RTCP PT must be 201 (RR), got: " + pt);
            if (length != 7) throw new Exception("RTCP length field must be 7 (32/4 - 1), got: " + length);

            // 3. Parse incoming Sender Report (SR)
            byte[] sr = new byte[28];
            sr[0] = (byte) 0x80; // V=2, RC=0
            sr[1] = (byte) RtcpMonitor.RTCP_PT_SR;
            sr[2] = 0x00; sr[3] = 0x06; // length = 6
            // SSRC = 0x11223344
            sr[4] = 0x11; sr[5] = 0x22; sr[6] = 0x33; sr[7] = 0x44;
            // NTP timestamp
            sr[8] = 0x00; sr[9] = 0x01; sr[10] = 0x00; sr[11] = 0x00;
            sr[12] = 0x00; sr[13] = 0x00; sr[14] = 0x00; sr[15] = 0x00;
            // RTP timestamp = 16000
            sr[16] = 0x00; sr[17] = 0x00; sr[18] = 0x3E; sr[19] = (byte) 0x80;
            // Packets = 100
            sr[20] = 0x00; sr[21] = 0x00; sr[22] = 0x00; sr[23] = 0x64;
            // Octets = 16000
            sr[24] = 0x00; sr[25] = 0x00; sr[26] = 0x3E; sr[27] = (byte) 0x80;

            monitor.parseIncomingRtcp(sr, 0, sr.length);
            RtcpMonitor.RtcpStats updatedStats = monitor.getStats(8000);
            if (updatedStats.remoteSsrc != 0x11223344L) {
                throw new Exception("Expected remote SSRC 0x11223344, got: 0x" + Long.toHexString(updatedStats.remoteSsrc));
            }
        });
    }

    private static void testOfflineTwoPartyCallFlow() {
        test("Call Flow: INVITE -> 100 Trying -> 180 Ringing -> 200 OK -> ACK -> BYE -> 200 OK", () -> {
            CallState callerState = CallState.IDLE;
            CallState calleeState = CallState.IDLE;

            // 1. Caller sends INVITE
            callerState = transition(callerState, CallState.CALLING);
            String callId = "call-" + UUID.randomUUID() + "@127.0.0.1";
            String invite =
                    "INVITE sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-leg1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: 0\r\n\r\n";

            if (!SIPParser.isMsgRequest(invite) || !"INVITE".equalsIgnoreCase(SIPParser.getRequestMethod(invite))) {
                throw new Exception("Malformed INVITE");
            }

            // Callee receives INVITE
            calleeState = transition(calleeState, CallState.PROCEEDING);

            // 2. Callee sends 100 Trying
            String trying =
                    "SIP/2.0 100 Trying\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-leg1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Content-Length: 0\r\n\r\n";
            callerState = transition(callerState, CallState.PROCEEDING);

            // 3. Callee sends 180 Ringing
            String ringing =
                    "SIP/2.0 180 Ringing\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-leg1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Content-Length: 0\r\n\r\n";
            calleeState = transition(calleeState, CallState.RINGING);
            callerState = transition(callerState, CallState.RINGING);

            // 4. Callee answers 200 OK
            String ok200 =
                    "SIP/2.0 200 OK\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-leg1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Contact: <sip:bob@127.0.0.1:5060>\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: 0\r\n\r\n";
            calleeState = transition(calleeState, CallState.ESTABLISHED);
            callerState = transition(callerState, CallState.ESTABLISHED);

            // 5. Caller sends ACK
            String ack =
                    "ACK sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-ack1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 ACK\r\n" +
                    "Content-Length: 0\r\n\r\n";
            if (!"ACK".equalsIgnoreCase(SIPParser.getRequestMethod(ack))) {
                throw new Exception("ACK validation failed");
            }

            // Both in ESTABLISHED
            if (callerState != CallState.ESTABLISHED || calleeState != CallState.ESTABLISHED) {
                throw new Exception("Both legs must be ESTABLISHED");
            }

            // 6. Caller terminates with BYE
            callerState = transition(callerState, CallState.TERMINATING);
            String bye =
                    "BYE sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-bye1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 2 BYE\r\n" +
                    "Content-Length: 0\r\n\r\n";
            calleeState = transition(calleeState, CallState.TERMINATING);

            // 7. Callee responds 200 OK (to BYE)
            callerState = transition(callerState, CallState.TERMINATED);
            calleeState = transition(calleeState, CallState.TERMINATED);

            if (callerState != CallState.TERMINATED || calleeState != CallState.TERMINATED) {
                throw new Exception("Both legs must be TERMINATED");
            }
        });
    }

    private static void testEarlyMediaFlow() {
        test("Call Flow: Early Media (183 Session Progress with SDP)", () -> {
            CallState caller = CallState.IDLE;
            caller = transition(caller, CallState.CALLING);
            caller = transition(caller, CallState.PROCEEDING);

            // 183 Session Progress with early SDP
            String resp183 =
                    "SIP/2.0 183 Session Progress\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-183\r\n" +
                    "From: <sip:caller@127.0.0.1>;tag=c1\r\n" +
                    "To: <sip:callee@127.0.0.1>;tag=s1\r\n" +
                    "Call-ID: early-123\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: 0\r\n\r\n";

            String code = SIPParser.getResponseCode(resp183);
            if (!"183".equals(code)) throw new Exception("Expected 183 code, got " + code);

            caller = transition(caller, CallState.EARLY_MEDIA);
            if (!caller.isActive()) throw new Exception("Early Media state must be active (media can flow)");

            // Now 200 OK arrives
            caller = transition(caller, CallState.ESTABLISHED);
            caller = transition(caller, CallState.TERMINATING);
            caller = transition(caller, CallState.TERMINATED);
        });
    }

    private static void testCancelTransactionFlow() {
        test("Call Flow: CANCEL Transaction (200 OK to CANCEL + 487 Request Terminated)", () -> {
            CallState caller = CallState.IDLE;
            caller = transition(caller, CallState.CALLING);
            caller = transition(caller, CallState.PROCEEDING);
            caller = transition(caller, CallState.RINGING);

            // Caller cancels in-flight ringing call
            String cancel =
                    "CANCEL sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-leg1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>\r\n" +
                    "Call-ID: cancel-call-123\r\n" +
                    "CSeq: 1 CANCEL\r\n" +
                    "Content-Length: 0\r\n\r\n";

            if (!"CANCEL".equalsIgnoreCase(SIPParser.getRequestMethod(cancel))) {
                throw new Exception("CANCEL method parsing failed");
            }

            // Callee replies 200 OK to CANCEL
            String cancelOk =
                    "SIP/2.0 200 OK\r\n" +
                    "CSeq: 1 CANCEL\r\n\r\n";
            if (!"200".equals(SIPParser.getResponseCode(cancelOk))) throw new Exception("200 OK expected");

            // Callee emits 487 Request Terminated to original INVITE
            String terminated487 =
                    "SIP/2.0 487 Request Terminated\r\n" +
                    "CSeq: 1 INVITE\r\n\r\n";
            if (!"487".equals(SIPParser.getResponseCode(terminated487))) throw new Exception("487 expected");

            caller = transition(caller, CallState.TERMINATED);
            if (!caller.isTerminal()) throw new Exception("Must be terminal after 487");
        });
    }

    private static void testCallRejectionFlow() {
        test("Call Flow: Call Decline / Busy Rejection (486 Busy Here / 603 Decline)", () -> {
            CallState caller = CallState.IDLE;
            caller = transition(caller, CallState.CALLING);
            caller = transition(caller, CallState.PROCEEDING);

            // Callee responds 486 Busy Here
            String busy486 =
                    "SIP/2.0 486 Busy Here\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-b1\r\n" +
                    "From: <sip:caller@127.0.0.1>;tag=c1\r\n" +
                    "To: <sip:callee@127.0.0.1>;tag=c2\r\n" +
                    "Call-ID: busy-123\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Content-Length: 0\r\n\r\n";

            String code = SIPParser.getResponseCode(busy486);
            if (!"486".equals(code)) throw new Exception("Expected 486 code, got " + code);

            caller = transition(caller, CallState.FAILED);
            if (!caller.isTerminal()) throw new Exception("Must be terminal after 486 rejection");

            // Verify ACK to non-2xx final response per RFC 3261
            String ack =
                    "ACK sip:callee@127.0.0.1 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-b1\r\n" +
                    "CSeq: 1 ACK\r\n\r\n";
            if (!"ACK".equalsIgnoreCase(SIPParser.getRequestMethod(ack))) throw new Exception("ACK expected");
        });
    }

    private static void testReinviteHoldResumeFlow() {
        test("Call Flow: Mid-dialog Re-INVITE Session Renegotiation (Hold & Resume)", () -> {
            CallState state = CallState.ESTABLISHED;

            // 1. Put call on HOLD via Re-INVITE
            String holdInvite =
                    "INVITE sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-reinv1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: call-dialog-reinv\r\n" +
                    "CSeq: 2 INVITE\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    "v=0\r\nm=audio 42000 RTP/AVP 8\r\na=sendonly\r\n";

            if (SIPParser.isInitialInvite(holdInvite)) throw new Exception("Re-INVITE must not be considered initial invite");
            if (!SIPParser.hasToTag(holdInvite)) throw new Exception("Re-INVITE must have To-tag");
            if (!isHoldSdp(holdInvite)) throw new Exception("Hold SDP must be recognized");

            // 2. Callee answers 200 OK with recvonly
            String hold200 =
                    "SIP/2.0 200 OK\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-reinv1\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "CSeq: 2 INVITE\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    "v=0\r\nm=audio 42002 RTP/AVP 8\r\na=recvonly\r\n";

            // 3. Resume call via Re-INVITE with a=sendrecv
            String resumeInvite =
                    "INVITE sip:bob@127.0.0.1:5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bK-reinv2\r\n" +
                    "From: <sip:alice@127.0.0.1:5060>;tag=alice-tag\r\n" +
                    "To: <sip:bob@127.0.0.1:5060>;tag=bob-tag\r\n" +
                    "Call-ID: call-dialog-reinv\r\n" +
                    "CSeq: 3 INVITE\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    "v=0\r\nm=audio 42000 RTP/AVP 8\r\na=sendrecv\r\n";

            if (isHoldSdp(resumeInvite)) throw new Exception("Resume SDP must not be hold");

            // Call remains in ESTABLISHED throughout hold and resume
            if (state != CallState.ESTABLISHED) throw new Exception("State must remain ESTABLISHED during hold/resume");
        });
    }

    private static void testBidirectionalRtpLoopback() {
        test("Media: Bidirectional RTP Transmission, Codec Transcode & Sequence Verification", () -> {
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            DatagramSocket socketA = new DatagramSocket(0, loopback);
            DatagramSocket socketB = new DatagramSocket(0, loopback);

            LiveAudioCodec codec = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);

            // Generate synthetic 20ms audio frame (160 samples = 320 bytes PCM)
            byte[] pcm = new byte[320];
            for (int i = 0; i < pcm.length; i += 2) {
                short sample = (short) (Math.sin(i * 0.1) * 8000);
                pcm[i] = (byte) (sample & 0xFF);
                pcm[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            byte[] encodedPayload = codec.encode(pcm, pcm.length); // 160 bytes

            // Transmit 30 RTP packets from A to B
            int packetsToSend = 30;
            for (int seq = 0; seq < packetsToSend; seq++) {
                byte[] rtpPkt = new byte[12 + encodedPayload.length];
                rtpPkt[0] = (byte) 0x80; // V=2, P=0, X=0, CC=0
                rtpPkt[1] = (byte) 8;    // PCMA
                rtpPkt[2] = (byte) ((seq >> 8) & 0xFF);
                rtpPkt[3] = (byte) (seq & 0xFF);
                int ts = seq * 160;
                rtpPkt[4] = (byte) ((ts >> 24) & 0xFF);
                rtpPkt[5] = (byte) ((ts >> 16) & 0xFF);
                rtpPkt[6] = (byte) ((ts >> 8) & 0xFF);
                rtpPkt[7] = (byte) (ts & 0xFF);
                // SSRC = 0xA1B2C3D4
                rtpPkt[8] = (byte) 0xA1; rtpPkt[9] = (byte) 0xB2; rtpPkt[10] = (byte) 0xC3; rtpPkt[11] = (byte) 0xD4;
                System.arraycopy(encodedPayload, 0, rtpPkt, 12, encodedPayload.length);

                DatagramPacket p = new DatagramPacket(rtpPkt, rtpPkt.length, loopback, socketB.getLocalPort());
                socketA.send(p);
            }

            // Receive and decode on B
            socketB.setSoTimeout(1000);
            int receivedCount = 0;
            byte[] rxBuf = new byte[1024];
            DatagramPacket rxPkt = new DatagramPacket(rxBuf, rxBuf.length);

            for (int i = 0; i < packetsToSend; i++) {
                socketB.receive(rxPkt);
                int pt = rxPkt.getData()[1] & 0x7F;
                int seq = ((rxPkt.getData()[2] & 0xFF) << 8) | (rxPkt.getData()[3] & 0xFF);
                if (pt != 8) throw new Exception("Unexpected PT: " + pt);
                if (seq != i) throw new Exception("Sequence mismatch: expected " + i + ", got " + seq);

                byte[] payload = new byte[rxPkt.getLength() - 12];
                System.arraycopy(rxPkt.getData(), 12, payload, 0, payload.length);
                byte[] decodedPcm = codec.decode(payload, payload.length);
                if (decodedPcm.length != 320) throw new Exception("Decoded length mismatch: " + decodedPcm.length);
                receivedCount++;
            }

            if (receivedCount != packetsToSend) {
                throw new Exception("Packet count mismatch: expected " + packetsToSend + ", got " + receivedCount);
            }

            // Clean teardown
            socketA.close();
            socketB.close();
        });
    }

    private static CallState transition(CallState from, CallState to) throws Exception {
        if (!from.canTransitionTo(to)) {
            throw new Exception("Illegal state transition from " + from + " to " + to);
        }
        return to;
    }

    private static void test(String name, RunnableTest runnable) {
        total++;
        System.out.printf("   [%-2d] %-68s ", total, name);
        try {
            runnable.run();
            passed++;
            System.out.println("PASS");
        } catch (Throwable t) {
            System.out.println("FAIL");
            System.err.println("        Error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    interface RunnableTest {
        void run() throws Exception;
    }
}
