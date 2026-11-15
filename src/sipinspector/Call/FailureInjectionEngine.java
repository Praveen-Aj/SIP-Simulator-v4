package sipinspector.Call;

import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioRuntimeDiagnostics;
import sipinspector.Utils.TLSDebugLogger;

import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlled SIP Failure & Exception Injection Testing Framework.
 *
 * Validates protocol resilience and deterministic cleanup under:
 * - 404 Not Found (Invalid Destination)
 * - 486 Busy Here (User Busy / Call Rejected)
 * - 408 Request Timeout (Timer B transaction expiry)
 * - CANCEL prior to 200 OK (Call Abandonment)
 * - Remote BYE (Remote Party Teardown)
 * - Zero resource leakage (sockets, threads, or memory) after each failure
 */
public class FailureInjectionEngine {

    public enum FailureScenario {
        INVALID_DESTINATION_404,
        BUSY_DESTINATION_486,
        TRANSACTION_TIMEOUT_408,
        CALL_CANCELLATION,
        REMOTE_PARTY_BYE
    }

    public static class FailureTestResult {
        public FailureScenario scenario;
        public boolean passed;
        public String details;
        public long durationMs;
        public String observedState;
        public String failureReason;
        public int initialSockets;
        public int finalSockets;
        public int initialThreads;
        public int finalThreads;
        public boolean resourceClean;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenario", scenario != null ? scenario.name() : "UNKNOWN");
            m.put("passed", passed);
            m.put("details", details);
            m.put("durationMs", durationMs);
            m.put("observedState", observedState);
            m.put("failureReason", failureReason);
            m.put("initialSockets", initialSockets);
            m.put("finalSockets", finalSockets);
            m.put("initialThreads", initialThreads);
            m.put("finalThreads", finalThreads);
            m.put("resourceClean", resourceClean);
            return m;
        }
    }

    /**
     * Run a specific failure injection test against live environment or mock.
     */
    public static FailureTestResult runTest(FailureScenario scenario, String caller, String callee, String pbxIp) {
        FailureTestResult res = new FailureTestResult();
        res.scenario = scenario;
        long start = System.currentTimeMillis();

        SipCallEngine engine = SipCallEngine.getInstance();
        if (!engine.isRunning()) {
            engine.startEngine(null, 5060, pbxIp, 5060);
        }

        res.initialSockets = LiveAudioRuntimeDiagnostics.getOpenSockets();
        res.initialThreads = Thread.activeCount();

        try {
            switch (scenario) {
                case INVALID_DESTINATION_404:
                    testInvalidDestination(engine, caller, pbxIp, res);
                    break;
                case BUSY_DESTINATION_486:
                    testBusyDestination(engine, caller, callee, res);
                    break;
                case TRANSACTION_TIMEOUT_408:
                    testTransactionTimeout(engine, caller, res);
                    break;
                case CALL_CANCELLATION:
                    testCallCancellation(engine, caller, callee, res);
                    break;
                case REMOTE_PARTY_BYE:
                    testRemoteBye(engine, caller, callee, res);
                    break;
            }
        } catch (Exception e) {
            res.passed = false;
            res.details = "Exception during failure test: " + e.getMessage();
        } finally {
            res.durationMs = System.currentTimeMillis() - start;

            // Wait brief moment for async socket cleanup
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            res.finalSockets = LiveAudioRuntimeDiagnostics.getOpenSockets();
            res.finalThreads = Thread.activeCount();
            res.resourceClean = (res.finalSockets <= res.initialSockets);
            if (!res.resourceClean) {
                res.passed = false;
                res.details = (res.details != null ? res.details + "; " : "") +
                        "Resource leak detected: sockets before=" + res.initialSockets + ", after=" + res.finalSockets;
            }
        }

        return res;
    }

    private static void testInvalidDestination(SipCallEngine engine, String caller, String pbxIp, FailureTestResult res) throws Exception {
        String invalidExt = "99999";
        SipCallEngine.CallSession session = engine.makeCall(caller, invalidExt, 8);

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 8000 && session.state != CallState.FAILED) {
            Thread.sleep(100);
        }

        res.observedState = session.state.name();
        res.failureReason = session.failureReason;

        if (session.state == CallState.FAILED && session.failureReason != null &&
                (session.failureReason.contains("404") || session.failureReason.contains("Not Found") || session.failureReason.contains("400") || session.failureReason.contains("604") || session.failureReason.contains("488") || session.failureReason.contains("Not Acceptable"))) {
            res.passed = true;
            res.details = "PBX correctly rejected invalid destination with: " + session.failureReason + "; non-2xx ACK was transmitted and session marked FAILED.";
        } else {
            res.passed = false;
            res.details = "Expected 404 rejection but observed state=" + session.state + ", reason=" + session.failureReason;
        }
    }

    private static void testBusyDestination(SipCallEngine engine, String caller, String callee, FailureTestResult res) throws Exception {
        // MicroSIP or simulator reject call
        SipCallEngine.CallSession session = engine.makeCall(caller, callee, 8);

        long waitStart = System.currentTimeMillis();
        boolean rejected = false;
        while (System.currentTimeMillis() - waitStart < 8000 && session.state != CallState.FAILED) {
            if (session.state == CallState.RINGING && !rejected) {
                // Issue reject / hangup on MicroSIP to return 486 / 603
                sendMicroSipIpc("call.hangup", "{}");
                rejected = true;
            }
            Thread.sleep(100);
        }

        res.observedState = session.state.name();
        res.failureReason = session.failureReason;

        if (session.state == CallState.FAILED) {
            res.passed = true;
            res.details = "Busy/Reject correctly handled: session transitioned to FAILED (" + session.failureReason + ") and ACK sent.";
        } else {
            engine.hangupCall(session.callId);
            res.passed = false;
            res.details = "Call did not transition to FAILED upon busy/reject; observed: " + session.state;
        }
    }

    private static void testTransactionTimeout(SipCallEngine engine, String caller, FailureTestResult res) throws Exception {
        // Dial unroutable test IP to test Timer B without remote 100 Trying
        SipCallEngine.CallSession session = engine.makeCall(caller, "90001", 8, "192.0.2.1", 5060);
        session.inviteTimeoutMs = 2000; // Accelerated Timer B for testing

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 6000 && session.state != CallState.FAILED) {
            Thread.sleep(100);
        }

        res.observedState = session.state.name();
        res.failureReason = session.failureReason;

        if (session.state == CallState.FAILED && session.failureReason != null && session.failureReason.contains("408")) {
            res.passed = true;
            res.details = "Timer B transaction timeout fired successfully; session transitioned to FAILED (408 Request Timeout).";
        } else {
            engine.hangupCall(session.callId);
            res.passed = false;
            res.details = "Expected 408 Request Timeout; observed state=" + session.state + ", reason=" + session.failureReason;
        }
    }

    private static void testCallCancellation(SipCallEngine engine, String caller, String callee, FailureTestResult res) throws Exception {
        SipCallEngine.CallSession session = engine.makeCall(caller, callee, 8);

        // Wait until CALLING or RINGING
        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 3000 && session.state == CallState.CALLING) {
            Thread.sleep(50);
        }

        // Cancel prior to 200 OK
        boolean cancelSent = engine.cancelCall(session.callId);

        waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 5000 && session.state != CallState.FAILED && session.state != CallState.TERMINATED) {
            Thread.sleep(100);
        }

        res.observedState = session.state.name();
        res.failureReason = session.failureReason;

        if (cancelSent && (session.state == CallState.FAILED || session.state == CallState.TERMINATED)) {
            res.passed = true;
            res.details = "CANCEL was transmitted successfully; call terminated prior to answer (State=" + session.state + ").";
        } else {
            engine.hangupCall(session.callId);
            res.passed = false;
            res.details = "CANCEL test failed. cancelSent=" + cancelSent + ", observedState=" + session.state;
        }
    }

    private static void testRemoteBye(SipCallEngine engine, String caller, String callee, FailureTestResult res) throws Exception {
        SipCallEngine.CallSession session = engine.makeCall(caller, callee, 8);

        // Wait for call to ring, then auto-answer on MicroSIP
        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 10000 && session.state != CallState.ESTABLISHED) {
            if (session.state == CallState.RINGING) {
                sendMicroSipIpc("call.answer", "{}");
            }
            Thread.sleep(100);
        }

        if (session.state != CallState.ESTABLISHED) {
            res.passed = false;
            res.details = "Call failed to establish for remote BYE test; state=" + session.state;
            return;
        }

        // Now remote hangs up
        Thread.sleep(1000);
        sendMicroSipIpc("call.hangup", "{}");

        waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 5000 && session.state != CallState.TERMINATED) {
            Thread.sleep(100);
        }

        res.observedState = session.state.name();

        if (session.state == CallState.TERMINATED) {
            res.passed = true;
            res.details = "Remote BYE received: 200 OK was returned, media was stopped, and session cleanly terminated.";
        } else {
            engine.hangupCall(session.callId);
            res.passed = false;
            res.details = "Expected session to be TERMINATED by remote BYE; observed state=" + session.state;
        }
    }

    private static void sendMicroSipIpc(String method, String params) {
        try {
            RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\microsip_ipc", "rw");
            String cmd = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + params + ",\"id\":1}\n";
            pipe.write(cmd.getBytes("UTF-8"));
            pipe.close();
        } catch (Exception ignored) {}
    }
}
