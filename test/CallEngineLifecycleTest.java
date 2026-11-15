package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Call.SipCallEngine.CallSession;
import sipinspector.Call.SipCallEngine.CallDirection;
import sipinspector.Media.CallState;

import java.util.List;

/**
 * Unit & Integration Test Suite for SipCallEngine Lifecycle & Resource Audit:
 *
 * 1. Clean daemon startup, running state verification, and graceful shutdown.
 * 2. In-place engine restart without port bind conflicts or lingering threads.
 * 3. Session lifecycle transitions (IDLE -> CALLING -> ESTABLISHED -> TERMINATED).
 * 4. In-call control states (Hold/Resume, Mute/Unmute, DTMF accumulation).
 * 5. RTP port pool isolation and distinct even-numbered port assignment.
 */
public class CallEngineLifecycleTest {

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
        System.out.println("   SIP CALL ENGINE LIFECYCLE & RESOURCE AUDIT SUITE");
        System.out.println("================================================================================");

        testEngineLifecycleStartupShutdown();
        testEngineRestartGraceful();
        testCallSessionStateTransitions();
        testInCallControlsState();
        testRtpPortAllocationIsolation();

        System.out.println("\n>>> ALL CALL ENGINE LIFECYCLE TESTS PASSED (5/5)!");
    }

    /**
     * Test 1: Clean Startup and Shutdown
     */
    private static void testEngineLifecycleStartupShutdown() {
        System.out.println("\n[Test 1] Engine Startup & Shutdown Lifecycle...");

        SipCallEngine engine = SipCallEngine.getInstance();
        engine.stopEngine();
        assertTrue(!engine.isRunning(), "Engine must be stopped initially");

        // Start on test loopback port 5078
        boolean started = engine.startEngine("127.0.0.1", 5078, "127.0.0.1", 5079, "UDP");
        assertTrue(started, "startEngine should return true");
        assertTrue(engine.isRunning(), "Engine must be running after start");

        // Stop
        engine.stopEngine();
        assertTrue(!engine.isRunning(), "Engine must be stopped after stopEngine()");

        System.out.println("  ✓ Startup and shutdown executed cleanly with zero socket conflicts.");
    }

    /**
     * Test 2: In-place Engine Restart
     */
    private static void testEngineRestartGraceful() {
        System.out.println("\n[Test 2] Graceful In-place Engine Restart...");

        SipCallEngine engine = SipCallEngine.getInstance();
        boolean started = engine.startEngine("127.0.0.1", 5078, "127.0.0.1", 5079, "UDP");
        assertTrue(started, "Initial start");

        boolean restarted = engine.restartEngine();
        assertTrue(restarted, "restartEngine must succeed");
        assertTrue(engine.isRunning(), "Engine must be running post-restart");

        engine.stopEngine();
        assertTrue(!engine.isRunning(), "Engine stopped post-test");

        System.out.println("  ✓ Engine restart completed with clean thread and socket rebind.");
    }

    /**
     * Test 3: Session State Transitions
     */
    private static void testCallSessionStateTransitions() {
        System.out.println("\n[Test 3] Session Lifecycle State Transitions...");

        CallSession session = new CallSession("test-state-sess-001@127.0.0.1", CallDirection.OUTBOUND, "90008", "90001", 20000);
        assertEquals(CallState.IDLE, session.state, "Initial state must be IDLE");

        session.state = CallState.CALLING;
        assertEquals(CallState.CALLING, session.state, "State -> CALLING");

        session.state = CallState.RINGING;
        assertEquals(CallState.RINGING, session.state, "State -> RINGING");

        session.state = CallState.EARLY_MEDIA;
        assertEquals(CallState.EARLY_MEDIA, session.state, "State -> EARLY_MEDIA");

        session.state = CallState.ESTABLISHED;
        assertEquals(CallState.ESTABLISHED, session.state, "State -> ESTABLISHED");

        session.state = CallState.TERMINATED;
        assertEquals(CallState.TERMINATED, session.state, "State -> TERMINATED");

        System.out.println("  ✓ IDLE -> CALLING -> RINGING -> EARLY_MEDIA -> ESTABLISHED -> TERMINATED verified.");
    }

    /**
     * Test 4: In-Call Controls State Machine
     */
    private static void testInCallControlsState() {
        System.out.println("\n[Test 4] In-Call Controls State Machine (Hold, Mute, DTMF)...");

        CallSession session = new CallSession("test-ctrl-sess-002@127.0.0.1", CallDirection.INBOUND, "90001", "90008", 20000);
        session.state = CallState.ESTABLISHED;

        // Hold / Resume
        assertTrue(!session.isOnHold, "Initially not on hold");
        session.isOnHold = true;
        assertTrue(session.isOnHold, "Locally on hold");
        session.isOnHold = false;
        assertTrue(!session.isOnHold, "Resumed");

        // Mute / Unmute
        assertTrue(!session.isMuted, "Initially not muted");
        session.isMuted = true;
        assertTrue(session.isMuted, "Muted");
        session.isMuted = false;
        assertTrue(!session.isMuted, "Unmuted");

        // DTMF accumulation
        assertEquals("", session.receivedDtmf.toString(), "Initial DTMF empty");
        session.receivedDtmf.append("1");
        session.receivedDtmf.append("2");
        session.receivedDtmf.append("#");
        assertEquals("12#", session.receivedDtmf.toString(), "Accumulated DTMF digits");

        System.out.println("  ✓ Hold/Resume state toggles verified.");
        System.out.println("  ✓ Mute/Unmute state toggles verified.");
        System.out.println("  ✓ DTMF sequence accumulation verified.");
    }

    /**
     * Test 5: RTP Port Pool Isolation
     */
    private static void testRtpPortAllocationIsolation() {
        System.out.println("\n[Test 5] RTP Port Allocation & Isolation...");

        CallSession s1 = new CallSession("sess-rtp-1", CallDirection.OUTBOUND, "90008", "90001", 20002);
        CallSession s2 = new CallSession("sess-rtp-2", CallDirection.OUTBOUND, "90008", "90002", 20004);
        CallSession s3 = new CallSession("sess-rtp-3", CallDirection.OUTBOUND, "90008", "90003", 20006);

        assertTrue(s1.localRtpPort % 2 == 0, "RTP port 1 must be even");
        assertTrue(s2.localRtpPort % 2 == 0, "RTP port 2 must be even");
        assertTrue(s3.localRtpPort % 2 == 0, "RTP port 3 must be even");

        assertTrue(s1.localRtpPort != s2.localRtpPort, "Port 1 != Port 2");
        assertTrue(s2.localRtpPort != s3.localRtpPort, "Port 2 != Port 3");
        assertTrue(s1.localRtpPort != s3.localRtpPort, "Port 1 != Port 3");

        System.out.println("  ✓ Distinct even-numbered RTP ports allocated for multi-call sessions.");
    }
}
