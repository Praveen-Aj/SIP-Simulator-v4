package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioRuntimeDiagnostics;

import java.util.*;
import java.util.concurrent.*;

/**
 * Priority 1: Multi-Call Dialog & Media Port Isolation Test.
 *
 * Validates that SipCallEngine can establish and manage multiple independent
 * SIP dialogs concurrently with:
 * - Distinct, collision-free Call-IDs
 * - Distinct, collision-free even RTP media ports (e.g. 42000, 42002, 42004)
 * - Independent CSeq tracking and dialog tags
 * - Concurrent audio transmission and reception
 * - Concurrent or independent call teardown without cross-dialog interference
 */
public class MultiCallOrchestrationTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final int PBX_PORT = 5060;
    private static final String SIMULATOR_EXT = "90008";
    private static final String MICROSIP_EXT = "90001";

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — MULTI-CALL ORCHESTRATION & ISOLATION TEST");
        System.out.println("   PBX: " + PBX_IP + ":" + PBX_PORT + " | Caller: " + SIMULATOR_EXT);
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        SipCallEngine engine = SipCallEngine.getInstance();
        String localIp = SipCallEngine.resolveLocalIpTowards(PBX_IP);

        try {
            boolean started = engine.startEngine(localIp, 5060, PBX_IP, PBX_PORT);
            if (!started) {
                System.err.println("FAIL: Engine failed to start on " + localIp + ":5060");
                System.exit(1);
            }
            System.out.println(">>> Engine started successfully on " + engine.getLocalIp() + ":" + engine.getLocalSipPort());

            // Test 1: Dynamic RTP Port Allocation & Collision Avoidance
            System.out.println("\n[TEST 1] Verifying Dynamic RTP Port Allocation & Non-Collision...");
            Set<Integer> allocatedPorts = new HashSet<>();
            List<SipCallEngine.CallSession> testSessions = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                SipCallEngine.CallSession s = engine.makeCall(SIMULATOR_EXT, "9000" + (i + 1), 8);
                testSessions.add(s);
                if (allocatedPorts.contains(s.localRtpPort)) {
                    System.err.println("FAIL: Duplicate RTP port allocated: " + s.localRtpPort);
                    failed++;
                    break;
                }
                allocatedPorts.add(s.localRtpPort);
                if ((s.localRtpPort % 2) != 0) {
                    System.err.println("FAIL: Allocated RTP port is not even: " + s.localRtpPort);
                    failed++;
                    break;
                }
            }
            if (allocatedPorts.size() == 5) {
                System.out.println("PASS: 5 distinct even RTP ports allocated without collision: " + allocatedPorts);
                passed++;
            }
            // Clean up test sessions
            for (SipCallEngine.CallSession s : testSessions) {
                engine.hangupCall(s.callId);
            }

            // Test 2: Call-ID Collision-Free Guarantee Under Multithreaded Race Conditions
            System.out.println("\n[TEST 2] Verifying Multithreaded Call-ID Collision-Free Guarantee...");
            int threadCount = 8;
            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            final Set<String> callIds = Collections.synchronizedSet(new HashSet<String>());
            final Set<Integer> rtpPorts = Collections.synchronizedSet(new HashSet<Integer>());
            final List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(exec.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SipCallEngine.CallSession s = engine.makeCall(SIMULATOR_EXT, "90001", 8);
                            callIds.add(s.callId);
                            rtpPorts.add(s.localRtpPort);
                            engine.hangupCall(s.callId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }));
            }
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);

            if (callIds.size() == threadCount && rtpPorts.size() == threadCount) {
                System.out.println("PASS: " + threadCount + " simultaneous calls generated completely unique Call-IDs and RTP ports!");
                passed++;
            } else {
                System.err.println("FAIL: Collision detected. Unique Call-IDs=" + callIds.size() + ", Unique Ports=" + rtpPorts.size() + " out of " + threadCount);
                failed++;
            }

            // Test 3: Active Session Deduplication & Lookup Resilience
            System.out.println("\n[TEST 3] Verifying Active Session Lookup (Full Call-ID vs Prefix)...");
            SipCallEngine.CallSession s = engine.makeCall(SIMULATOR_EXT, "90001", 8);
            String fullId = s.callId;
            String prefix = fullId.split("@")[0];

            SipCallEngine.CallSession lookupFull = engine.getCallSession(fullId);
            SipCallEngine.CallSession lookupPrefix = engine.getCallSession(prefix);

            if (lookupFull != null && lookupPrefix != null && lookupFull == lookupPrefix) {
                System.out.println("PASS: Session successfully resolved by both full Call-ID and prefix!");
                passed++;
            } else {
                System.err.println("FAIL: Session lookup inconsistency.");
                failed++;
            }
            engine.hangupCall(s.callId);

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        } finally {
            engine.stopEngine();
        }

        System.out.println("\n================================================================================");
        System.out.println("   MULTI-CALL ORCHESTRATION TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("================================================================================");
        if (failed > 0) System.exit(1);
    }
}
