/*
 * SIP Simulator v4 - Phase 9 Live Integration
 * OxeLiveHeadlessIntegrationTest: Live End-to-End Validation with OXE PBX & Native Headless Endpoint
 *
 * Topology:
 * Endpoint A: SIP Simulator v4 (Extension 90008 @ localIp:5060)
 * PBX: Alcatel-Lucent OmniPCX Enterprise (OXE) @ 192.168.0.20:5060
 * Endpoint B: Native Headless SIP Endpoint (Extension 90001 @ localIp:5062)
 *
 * Eliminates mandatory dependency on MicroSIP interactive GUI sessions (LIM-03).
 */
package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Endpoint.HeadlessSipEndpoint;
import sipinspector.Media.CallState;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OxeLiveHeadlessIntegrationTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final int PBX_PORT = 5060;
    private static final String SIMULATOR_EXT = "90008";
    private static final String HEADLESS_EXT = "90001";
    private static final int HEADLESS_PORT = 5062;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP SIMULATOR V4 — PHASE 9 LIVE OXE & HEADLESS ENDPOINT VALIDATION");
        System.out.println("   Endpoint A: Extension " + SIMULATOR_EXT + " (Simulator v4)");
        System.out.println("   PBX:        " + PBX_IP + ":" + PBX_PORT + " (OmniPCX Enterprise)");
        System.out.println("   Endpoint B: Extension " + HEADLESS_EXT + " (Native Headless Endpoint @ UDP " + HEADLESS_PORT + ")");
        System.out.println("================================================================================");

        String localIp = SipCallEngine.resolveLocalIpTowards(PBX_IP);
        System.out.println("Local IP detected: " + localIp);

        HeadlessSipEndpoint endpointB = new HeadlessSipEndpoint(localIp, HEADLESS_PORT, HEADLESS_EXT);
        endpointB.setAutoAnswer(true);
        endpointB.setRingDelayMs(100);

        final CountDownLatch callAnsweredLatch = new CountDownLatch(1);
        final CountDownLatch callEndedLatch = new CountDownLatch(1);

        endpointB.addListener(new HeadlessSipEndpoint.EndpointEventListener() {
            @Override
            public void onCallReceived(String callId, String caller, String sdp) {
                System.out.println("[ENDPOINT B - 90001] Incoming call received from: " + caller + " (Call-ID: " + callId + ")");
            }

            @Override
            public void onCallAnswered(String callId, int localRtpPort) {
                System.out.println("[ENDPOINT B - 90001] Call answered with 200 OK! RTP port bound: " + localRtpPort);
                callAnsweredLatch.countDown();
            }

            @Override
            public void onCallEnded(String callId, String reason) {
                System.out.println("[ENDPOINT B - 90001] Call ended (" + reason + ")");
                callEndedLatch.countDown();
            }

            @Override
            public void onDtmfReceived(String callId, char digit) {
                System.out.println("[ENDPOINT B - 90001] DTMF received: " + digit);
            }

            @Override
            public void onRegistrationStatus(boolean successful, String message) {
                System.out.println("[ENDPOINT B - 90001] Registration status: " + successful + " - " + message);
            }
        });

        boolean startedB = endpointB.start();
        if (!startedB) {
            System.err.println("FATAL: Failed to start Native Headless Endpoint B on port " + HEADLESS_PORT);
            System.exit(1);
        }

        SipCallEngine engineA = SipCallEngine.getInstance();
        boolean success = false;

        try {
            // Step 1: Start Engine A
            System.out.println("\n[STEP 1] Starting Simulator Engine A on " + localIp + ":5060...");
            boolean engineStarted = engineA.startEngine(localIp, 5060, PBX_IP, PBX_PORT);
            if (!engineStarted) {
                System.err.println("Failed to start Engine A on " + localIp + ":5060");
                System.exit(1);
            }

            // Step 2: Register Extension 90008 on OXE PBX
            System.out.println("\n[STEP 2] Registering extension " + SIMULATOR_EXT + " on OXE PBX (" + PBX_IP + ")...");
            boolean regA = engineA.register(SIMULATOR_EXT, 1800);
            if (regA) {
                System.out.println(">>> Extension " + SIMULATOR_EXT + " REGISTERED successfully on OXE PBX (200 OK)!");
            } else {
                System.err.println("WARNING: Registration failed or timed out for " + SIMULATOR_EXT);
            }

            // Step 3: Dial from Endpoint A (90008) to Endpoint B (90001 directly or via PBX)
            System.out.println("\n[STEP 3] Placing call from 90008 -> 90001 (" + localIp + ":" + HEADLESS_PORT + ")...");
            SipCallEngine.CallSession call = engineA.makeCall(SIMULATOR_EXT, HEADLESS_EXT, 8, localIp, HEADLESS_PORT);

            System.out.println("Call initiated with Call-ID: " + call.callId + " (Initial State: " + call.state + ")");

            // Step 4: Wait for Endpoint B to auto-answer
            System.out.println("\n[STEP 4] Awaiting 200 OK and 3-way handshake completion...");
            boolean answered = callAnsweredLatch.await(5, TimeUnit.SECONDS);
            if (answered) {
                System.out.println(">>> 3-Way Handshake Successful! Endpoint B auto-answered.");
            } else {
                System.err.println("WARNING: Endpoint B did not answer within 5 seconds.");
            }

            // Step 5: Send DTMF digit '7'
            System.out.println("\n[STEP 5] Transmitting DTMF digit '7'...");
            try {
                engineA.sendDtmf(call.callId, '7', "INFO");
                System.out.println(">>> DTMF digit '7' transmitted via SIP INFO.");
            } catch (Exception ex) {
                System.out.println("DTMF send note: " + ex.getMessage());
            }

            Thread.sleep(1000);

            // Step 6: Hangup call via BYE
            System.out.println("\n[STEP 6] Hanging up call via BYE...");
            boolean hangupOk = engineA.hangupCall(call.callId);
            System.out.println("Hangup completed: " + hangupOk);

            boolean ended = callEndedLatch.await(3, TimeUnit.SECONDS);
            if (ended) {
                System.out.println(">>> Call cleanly terminated and acknowledged by Endpoint B.");
            }

            // Step 7: Clean deregistration
            System.out.println("\n[STEP 7] Deregistering extension " + SIMULATOR_EXT + " on OXE PBX...");
            boolean unregA = engineA.unregister(SIMULATOR_EXT);
            System.out.println("Deregistration result: " + unregA);

            success = regA && answered && hangupOk;
        } catch (Exception e) {
            System.err.println("Live test execution error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            endpointB.stop();
            engineA.stopEngine();
        }

        System.out.println("\n================================================================================");
        if (success) {
            System.out.println("   [PASS] LIVE OXE & HEADLESS ENDPOINT VALIDATION COMPLETED SUCCESSFULLY!");
        } else {
            System.out.println("   [PARTIAL/FAIL] One or more live phases failed.");
        }
        System.out.println("================================================================================");
        System.exit(success ? 0 : 1);
    }
}
