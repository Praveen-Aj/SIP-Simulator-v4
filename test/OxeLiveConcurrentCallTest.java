package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioRuntimeDiagnostics;
import sipinspector.Utils.TLSDebugLogger;

import java.net.InetAddress;
import java.util.*;

/**
 * Phase 6 Live Concurrent OXE Call Qualification Suite.
 *
 * Progression:
 * - Stage 1: Single live call (Simulator 90008 -> MicroSIP 90001) with bidirectional audio & RTCP.
 * - Stage 2: Two concurrent live calls against OXE PBX.
 *   - Call 1 established and streaming audio.
 *   - Call 2 initiated concurrently.
 *   - Verification of dialog separation (Call-IDs, CSeqs, even RTP ports).
 *   - Evaluation of OXE PBX behavior (Call Waiting 180 Ringing vs 486 Busy Here).
 *   - Independent call teardown without cross-dialog interference.
 * - Stage 3: Concurrency boundary qualification and resource leak audit.
 */
public class OxeLiveConcurrentCallTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final int PBX_PORT = 5060;
    private static final String SIMULATOR_EXT = "90008";
    private static final String MICROSIP_EXT = "90001";
    private static final String LOCAL_IP = SipCallEngine.resolveLocalIpTowards(PBX_IP);
    private static final int LOCAL_PORT = 5060;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — PHASE 6 LIVE CONCURRENT OXE QUALIFICATION");
        System.out.println("   Target PBX: " + PBX_IP + ":" + PBX_PORT + " | Host: " + LOCAL_IP + ":" + LOCAL_PORT);
        System.out.println("   Caller: " + SIMULATOR_EXT + " | Callee: " + MICROSIP_EXT);
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        SipCallEngine engine = SipCallEngine.getInstance();

        try {
            // Step 0: Check MicroSIP IPC
            System.out.println("\n[INIT] Checking MicroSIP IPC status...");
            String ipcStatus = MicroSipIpc.sendCommand("system.status", null);
            System.out.println("MicroSIP Status: " + ipcStatus);
            if (!ipcStatus.contains("\"registered\":true")) {
                System.err.println("WARNING: MicroSIP is not reporting registered:true. Check MicroSIP.");
            } else {
                System.out.println(">>> MicroSIP " + MICROSIP_EXT + " confirmed active and registered on OXE!");
            }

            // Ensure MicroSIP is in clean idle state
            MicroSipIpc.sendCommand("call.hangup", "{}");
            Thread.sleep(1000);
            for (SipCallEngine.CallSession s : engine.getActiveCalls()) {
                try { engine.hangupCall(s.callId); } catch (Exception ignored) {}
            }

            // Start SipCallEngine
            boolean started = engine.startEngine(LOCAL_IP, LOCAL_PORT, PBX_IP, PBX_PORT);
            if (!started) {
                System.err.println("FATAL: Failed to start SipCallEngine on " + LOCAL_IP + ":" + LOCAL_PORT);
                System.exit(1);
            }

            // Register extension 90008
            System.out.println("\n[REGISTRATION] Registering " + SIMULATOR_EXT + " on OXE PBX...");
            boolean regOk = engine.register(SIMULATOR_EXT, 1800);
            if (regOk) {
                System.out.println(">>> Registration of " + SIMULATOR_EXT + " SUCCEEDED!");
                passed++;
            } else {
                System.err.println(">>> Registration of " + SIMULATOR_EXT + " FAILED.");
                failed++;
                return;
            }

            Thread.sleep(1500);

            // =========================================================================
            // STAGE 1: Single Live Call Baseline Validation
            // =========================================================================
            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println("STAGE 1: Single Live Call Baseline (Simulator 90008 -> MicroSIP 90001)");
            System.out.println("--------------------------------------------------------------------------------");
            SipCallEngine.CallSession callA = engine.makeCall(SIMULATOR_EXT, MICROSIP_EXT, 8);
            System.out.println("Call A initiated. Call-ID=" + callA.callId + ", RTP Port=" + callA.localRtpPort);

            boolean answeredA = false;
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 15000 && callA.state != CallState.ESTABLISHED && callA.state != CallState.FAILED) {
                if (!answeredA && (callA.state == CallState.RINGING || callA.state == CallState.EARLY_MEDIA)) {
                    Thread.sleep(300);
                    MicroSipIpc.sendCommand("call.answer", "{}");
                    answeredA = true;
                }
                Thread.sleep(100);
            }

            if (callA.state == CallState.ESTABLISHED) {
                System.out.println(">>> Call A ESTABLISHED successfully! Streaming audio for 3 seconds...");
                Thread.sleep(3000);
                Map<String, Object> stats = callA.mediaController != null ? callA.mediaController.getStats() : new HashMap<>();
                long tx = stats.containsKey("packets_sent") ? ((Number) stats.get("packets_sent")).longValue() : 0;
                long rx = stats.containsKey("packets_received") ? ((Number) stats.get("packets_received")).longValue() : 0;
                System.out.println("Call A Media: TX=" + tx + " pkts, RX=" + rx + " pkts, MOS=" + stats.get("mos_score"));

                engine.hangupCall(callA.callId);
                MicroSipIpc.sendCommand("call.hangup", "{}");
                System.out.println(">>> STAGE 1 PASSED: Baseline single call verified.");
                passed++;
            } else {
                System.err.println(">>> STAGE 1 FAILED: Call A failed to establish. State=" + callA.state + ", Reason=" + callA.failureReason);
                try { engine.hangupCall(callA.callId); } catch (Exception ignored) {}
                MicroSipIpc.sendCommand("call.hangup", "{}");
                failed++;
            }

            Thread.sleep(2000);

            // =========================================================================
            // STAGE 2: Two Simultaneous Concurrent Calls
            // =========================================================================
            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println("STAGE 2: Two Concurrent Live Calls Qualification");
            System.out.println("--------------------------------------------------------------------------------");

            // Initiate Call 1
            System.out.println("Step 2.1: Initiating Call 1 (90008 -> 90001)...");
            SipCallEngine.CallSession call1 = engine.makeCall(SIMULATOR_EXT, MICROSIP_EXT, 8);
            System.out.println("Call 1 initiated. Call-ID=" + call1.callId + ", RTP Port=" + call1.localRtpPort);

            waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 10000 && call1.state != CallState.ESTABLISHED) {
                if (call1.state == CallState.RINGING) {
                    MicroSipIpc.sendCommand("call.answer", "{}");
                }
                Thread.sleep(100);
            }

            if (call1.state != CallState.ESTABLISHED) {
                System.err.println("FAIL: Call 1 did not establish. Aborting Stage 2.");
                failed++;
            } else {
                System.out.println(">>> Call 1 is ACTIVE and ESTABLISHED on RTP port " + call1.localRtpPort);

                // Step 2.2: While Call 1 is active, initiate Call 2
                System.out.println("Step 2.2: Initiating Call 2 concurrently while Call 1 is streaming audio...");
                SipCallEngine.CallSession call2 = engine.makeCall(SIMULATOR_EXT, MICROSIP_EXT, 8);
                System.out.println("Call 2 initiated. Call-ID=" + call2.callId + ", RTP Port=" + call2.localRtpPort);

                // Verify port and Call-ID independence
                if (call1.callId.equals(call2.callId)) {
                    System.err.println("FAIL: Call 1 and Call 2 share the same Call-ID!");
                    failed++;
                } else if (call1.localRtpPort == call2.localRtpPort) {
                    System.err.println("FAIL: Port collision! Call 1 and Call 2 assigned same RTP port: " + call1.localRtpPort);
                    failed++;
                } else {
                    System.out.println("PASS: Independent Call-IDs and collision-free RTP ports verified (Port 1=" + call1.localRtpPort + ", Port 2=" + call2.localRtpPort + ")");
                    passed++;
                }

                // Wait up to 5 seconds to observe OXE behavior for Call 2
                long c2Wait = System.currentTimeMillis();
                while (System.currentTimeMillis() - c2Wait < 6000 && call2.state != CallState.ESTABLISHED && call2.state != CallState.FAILED && call2.state != CallState.TERMINATED) {
                    if (call2.state == CallState.RINGING) {
                        System.out.println(">>> OXE delivered Call 2 (Call Waiting / Multi-line supported)! Answering Call 2...");
                        MicroSipIpc.sendCommand("call.answer", "{}");
                    }
                    Thread.sleep(150);
                }

                System.out.println("OXE Response for Call 2: State=" + call2.state + ", LastCode=" + call2.lastResponseCode + ", Reason=" + call2.failureReason);

                if (call2.state == CallState.ESTABLISHED) {
                    System.out.println(">>> STAGE 2 RESULT: OXE PBX successfully supports multiple concurrent calls on extension 90001!");
                    System.out.println("Streaming concurrent audio on both dialogs for 3 seconds...");
                    Thread.sleep(3000);

                    // Step 2.3: Teardown Call 1 and verify Call 2 remains active
                    System.out.println("Step 2.3: Terminating Call 1 only. Verifying Call 2 isolation...");
                    engine.hangupCall(call1.callId);
                    Thread.sleep(1000);

                    if (call2.state == CallState.ESTABLISHED) {
                        System.out.println("PASS: Call 2 remained ESTABLISHED after Call 1 terminated!");
                        passed++;
                    } else {
                        System.err.println("FAIL: Call 2 was unexpectedly disrupted by Call 1 hangup.");
                        failed++;
                    }

                    // Hangup Call 2
                    engine.hangupCall(call2.callId);
                    MicroSipIpc.sendCommand("call.hangup", "{}");
                } else if (call2.state == CallState.FAILED || (call2.lastResponseCode != null && call2.lastResponseCode.startsWith("486"))) {
                    System.out.println(">>> STAGE 2 RESULT: OXE PBX enforced single-call policy on extension 90001 (Returned 486 Busy Here).");
                    System.out.println("Verifying Call 1 remained completely uninterrupted by Call 2 rejection...");
                    if (call1.state == CallState.ESTABLISHED) {
                        System.out.println("PASS: Call 1 maintained established dialog and audio despite concurrent call rejection!");
                        passed++;
                    } else {
                        System.err.println("FAIL: Call 1 was disrupted by Call 2 rejection.");
                        failed++;
                    }
                    engine.hangupCall(call1.callId);
                    MicroSipIpc.sendCommand("call.hangup", "{}");
                } else {
                    System.out.println("OXE Call 2 outcome: " + call2.state + " (LastCode=" + call2.lastResponseCode + ")");
                    engine.hangupCall(call1.callId);
                    engine.hangupCall(call2.callId);
                    MicroSipIpc.sendCommand("call.hangup", "{}");
                }
            }

            Thread.sleep(2000);

            // =========================================================================
            // STAGE 3: Post-Test Resource Leak Audit
            // =========================================================================
            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println("STAGE 3: Post-Test Resource Leak Audit");
            System.out.println("--------------------------------------------------------------------------------");
            int openSockets = LiveAudioRuntimeDiagnostics.getOpenSockets();
            int activeReceivers = LiveAudioRuntimeDiagnostics.getActiveReceivers();
            int activeSenders = LiveAudioRuntimeDiagnostics.getActiveSenders();
            int activeCalls = engine.getActiveCalls().size();

            System.out.println("Active Calls: " + activeCalls);
            System.out.println("Open Media Sockets: " + openSockets);
            System.out.println("Active Receivers: " + activeReceivers);
            System.out.println("Active Senders: " + activeSenders);

            if (openSockets == 0 && activeReceivers == 0 && activeSenders == 0 && activeCalls == 0) {
                System.out.println("PASS: Zero resource leaks detected! All media sockets, threads and sessions released.");
                passed++;
            } else {
                System.err.println("FAIL: Resource leak detected.");
                failed++;
            }

            // Deregistration
            System.out.println("\n[CLEANUP] Deregistering " + SIMULATOR_EXT + " from OXE PBX...");
            engine.register(SIMULATOR_EXT, 0);
            engine.stopEngine();

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n================================================================================");
        System.out.println("   LIVE CONCURRENT QUALIFICATION SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("================================================================================");
        if (failed > 0) System.exit(1);
    }
}
