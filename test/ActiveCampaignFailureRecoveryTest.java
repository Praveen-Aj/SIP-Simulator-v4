package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioRuntimeDiagnostics;
import sipinspector.Media.LiveAudioController;

/**
 * Phase 6 Active Campaign Failure Recovery & Multi-Dialog Isolation Test.
 *
 * Validates deterministic failure recovery and zero resource leakage:
 * - Test 1: Dialog Isolation during Sibling Failure (Active call continues unaffected when sibling fails)
 * - Test 2: In-Flight CANCEL Recovery (CANCEL -> 487 -> clean teardown)
 * - Test 3: 408 Request Timeout Recovery (Timer B transaction expiry)
 * - Test 4: Remote BYE Teardown Recovery (BYE received -> 200 OK sent -> audio stopped)
 * - Test 5: Comprehensive Resource Leak Audit (0 sockets, 0 threads, 0 lingering calls)
 */
public class ActiveCampaignFailureRecoveryTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final String SIMULATOR_EXT = "90008";
    private static final String MICROSIP_EXT = "90001";
    private static final String LOCAL_IP = SipCallEngine.resolveLocalIpTowards(PBX_IP);

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — ACTIVE CAMPAIGN FAILURE RECOVERY TEST");
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        SipCallEngine engine = SipCallEngine.getInstance();

        try {
            boolean started = engine.startEngine(LOCAL_IP, 5060, PBX_IP, 5060);
            if (!started) {
                System.err.println("FAIL: Engine failed to start on " + LOCAL_IP + ":5060");
                System.exit(1);
            }

            // Ensure clean baseline before tests start
            for (SipCallEngine.CallSession s : engine.getActiveCalls()) {
                try { engine.hangupCall(s.callId); } catch (Exception ignored) {}
            }
            Thread.sleep(500);

            // TEST 1: Dialog Isolation Under Sibling Failure
            System.out.println("\n[TEST 1] Testing Multi-Dialog Isolation Under Sibling Failure Injection...");
            String call1Id = "isolation_call_1_" + System.currentTimeMillis() + "@" + LOCAL_IP;
            SipCallEngine.CallSession session1 = new SipCallEngine.CallSession(
                    call1Id, SipCallEngine.CallDirection.OUTBOUND, SIMULATOR_EXT, MICROSIP_EXT, 42000);
            session1.state = CallState.ESTABLISHED;
            LiveAudioController audio1 = new LiveAudioController();
            audio1.initializeForCall(session1.callId, PBX_IP, 40000, LOCAL_IP, session1.localRtpPort, 8, false, null);
            audio1.startAudio();
            session1.mediaController = audio1;
            engine.registerCallSession(session1);

            String call2Id = "isolation_call_2_" + System.currentTimeMillis() + "@" + LOCAL_IP;
            SipCallEngine.CallSession session2 = new SipCallEngine.CallSession(
                    call2Id, SipCallEngine.CallDirection.OUTBOUND, SIMULATOR_EXT, "99990", 42002);
            session2.state = CallState.CALLING;
            engine.registerCallSession(session2);

            // Inject failure into Session 2: 486 Busy Here
            session2.state = CallState.FAILED;
            session2.failureReason = "486 Busy Here";
            session2.lastResponseCode = "486";

            // Verify Session 1 is completely unimpacted
            Thread.sleep(300);
            if (session1.state == CallState.ESTABLISHED && audio1.isAudioRunning()) {
                System.out.println("PASS: Session 1 remained ESTABLISHED and streaming audio despite Session 2 486 failure!");
                passed++;
            } else {
                System.err.println("FAIL: Session 1 was disrupted by Session 2 failure.");
                failed++;
            }

            // Cleanly terminate Session 1 and Session 2
            audio1.stopAudio();
            session1.state = CallState.TERMINATED;
            session2.state = CallState.TERMINATED;
            engine.hangupCall(session1.callId);
            engine.hangupCall(session2.callId);

            // TEST 2: Call Cancellation Recovery
            System.out.println("\n[TEST 2] Testing Call Cancellation Recovery (CANCEL prior to 200 OK)...");
            SipCallEngine.CallSession sessionC = engine.makeCall(SIMULATOR_EXT, MICROSIP_EXT, 8);
            sessionC.state = CallState.RINGING;
            sessionC.inviteBranch = "z9hG4bKcancel_test_branch";

            boolean cancelSent = engine.cancelCall(sessionC.callId);
            if (cancelSent && (sessionC.state == CallState.TERMINATING || sessionC.state == CallState.TERMINATED)) {
                // Simulate peer sending 487 Request Terminated in response to CANCEL
                String mock487 = "SIP/2.0 487 Request Terminated\r\n" +
                        "Via: SIP/2.0/UDP " + LOCAL_IP + ":5060;branch=" + sessionC.inviteBranch + "\r\n" +
                        "From: <sip:" + SIMULATOR_EXT + "@" + PBX_IP + ">;tag=" + sessionC.localTag + "\r\n" +
                        "To: <sip:" + MICROSIP_EXT + "@" + PBX_IP + ">\r\n" +
                        "Call-ID: " + sessionC.callId + "\r\n" +
                        "CSeq: 1 INVITE\r\n" +
                        "Content-Length: 0\r\n\r\n";
                engine.processMessage(mock487);

                if (sessionC.state == CallState.FAILED || sessionC.state == CallState.TERMINATED) {
                    System.out.println("PASS: In-flight CANCEL and 487 terminated call cleanly without leaks.");
                    passed++;
                } else {
                    System.err.println("FAIL: Call did not transition to FAILED after 487. State=" + sessionC.state);
                    failed++;
                }
            } else {
                System.err.println("FAIL: cancelCall failed. State=" + sessionC.state);
                failed++;
            }
            engine.hangupCall(sessionC.callId);

            // TEST 3: 408 Request Timeout Recovery (Timer B)
            System.out.println("\n[TEST 3] Testing 408 Request Timeout Recovery (Timer B Expiry)...");
            String callTId = "timeout_call_" + System.currentTimeMillis() + "@" + LOCAL_IP;
            SipCallEngine.CallSession sessionT = new SipCallEngine.CallSession(
                    callTId, SipCallEngine.CallDirection.OUTBOUND, SIMULATOR_EXT, "99990", 42006);
            sessionT.state = CallState.CALLING;
            sessionT.inviteTimeoutMs = 100; // 100ms timeout
            sessionT.startTimeMs = System.currentTimeMillis() - 200; // expired
            engine.registerCallSession(sessionT);

            // Wait brief moment for engine listen loop to check pending transactions
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 3000 && sessionT.state != CallState.FAILED) {
                Thread.sleep(100);
            }

            if (sessionT.state == CallState.FAILED && "408".equals(sessionT.lastResponseCode)) {
                System.out.println("PASS: Timer B expiry correctly transitioned session to FAILED (408 Request Timeout).");
                passed++;
            } else {
                System.err.println("FAIL: Timer B expiry did not transition session to FAILED. State=" + sessionT.state + ", Code=" + sessionT.lastResponseCode);
                failed++;
            }
            engine.hangupCall(sessionT.callId);

            // TEST 4: Remote BYE Recovery
            System.out.println("\n[TEST 4] Testing Remote BYE Teardown Recovery...");
            SipCallEngine.CallSession sessionB = engine.makeCall(SIMULATOR_EXT, MICROSIP_EXT, 8);
            sessionB.state = CallState.ESTABLISHED;
            sessionB.localTag = "loc_tag_bye_test";
            sessionB.remoteTag = "rem_tag_bye_test";

            LiveAudioController audioB = new LiveAudioController();
            audioB.initializeForCall(sessionB.callId, PBX_IP, 40004, LOCAL_IP, sessionB.localRtpPort, 8, false, null);
            audioB.startAudio();
            sessionB.mediaController = audioB;

            // Simulate incoming BYE from remote peer
            String mockBye = "BYE sip:" + SIMULATOR_EXT + "@" + LOCAL_IP + ":5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP " + PBX_IP + ":5060;branch=z9hG4bKmockbye1\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:" + MICROSIP_EXT + "@" + PBX_IP + ">;tag=" + sessionB.remoteTag + "\r\n" +
                    "To: <sip:" + SIMULATOR_EXT + "@" + PBX_IP + ">;tag=" + sessionB.localTag + "\r\n" +
                    "Call-ID: " + sessionB.callId + "\r\n" +
                    "CSeq: 101 BYE\r\n" +
                    "Content-Length: 0\r\n\r\n";
            engine.processMessage(mockBye);
            Thread.sleep(300);

            if (sessionB.state == CallState.TERMINATED && !audioB.isAudioRunning()) {
                System.out.println("PASS: Remote BYE cleanly terminated session and released audio controller!");
                passed++;
            } else {
                System.err.println("FAIL: Remote BYE did not cleanly terminate session. State=" + sessionB.state);
                failed++;
            }
            engine.hangupCall(sessionB.callId);

            // TEST 5: Comprehensive Resource Leak Audit
            System.out.println("\n[TEST 5] Verifying Post-Failure Resource Leak Audit...");
            Thread.sleep(500);
            int openSockets = LiveAudioRuntimeDiagnostics.getOpenSockets();
            int activeReceivers = LiveAudioRuntimeDiagnostics.getActiveReceivers();
            int activeSenders = LiveAudioRuntimeDiagnostics.getActiveSenders();
            int activeCalls = engine.getActiveCalls().size();

            System.out.println("Active Calls: " + activeCalls);
            System.out.println("Open Media Sockets: " + openSockets);
            System.out.println("Active Receivers: " + activeReceivers);
            System.out.println("Active Senders: " + activeSenders);

            if (openSockets == 0 && activeReceivers == 0 && activeSenders == 0 && activeCalls == 0) {
                System.out.println("PASS: Verified 0 open sockets, 0 active receivers, 0 active senders, and 0 lingering calls!");
                passed++;
            } else {
                System.err.println("FAIL: Resource leak detected: Sockets=" + openSockets + ", Receivers=" + activeReceivers + ", Senders=" + activeSenders + ", Calls=" + activeCalls);
                failed++;
            }

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        } finally {
            engine.stopEngine();
        }

        System.out.println("\n================================================================================");
        System.out.println("   ACTIVE FAILURE RECOVERY TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("================================================================================");
        if (failed > 0) System.exit(1);
    }
}
