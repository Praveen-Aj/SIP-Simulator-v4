package test;

import sipinspector.Call.FailureInjectionEngine;
import sipinspector.Call.FailureInjectionEngine.FailureScenario;
import sipinspector.Call.FailureInjectionEngine.FailureTestResult;
import sipinspector.Call.SipCallEngine;

/**
 * Priority 6: Controlled Failure Injection & Resource Leak Verification Test.
 *
 * Validates deterministic failure recovery and zero resource leakage under:
 * - 404 Not Found (Invalid Destination)
 * - 408 Request Timeout (Timer B transaction expiry)
 * - Call Cancellation prior to 200 OK
 */
public class ControlledFailureRecoveryTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final String SIMULATOR_EXT = "90008";
    private static final String MICROSIP_EXT = "90001";

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — CONTROLLED FAILURE & RECOVERY TEST");
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        try {
            // Test 1: 408 Request Timeout
            System.out.println("\n[TEST 1] Testing 408 Request Timeout (Timer B Expiry)...");
            FailureTestResult r1 = FailureInjectionEngine.runTest(
                    FailureScenario.TRANSACTION_TIMEOUT_408, SIMULATOR_EXT, "99990", PBX_IP);
            System.out.println("Result: " + r1.details);
            System.out.println("Clean Resources: " + r1.resourceClean + " (Sockets: " + r1.initialSockets + " -> " + r1.finalSockets + ")");
            if (r1.passed) {
                passed++;
                System.out.println("PASS: 408 Timeout Handled Deterministically.");
            } else {
                failed++;
                System.err.println("FAIL: 408 Timeout Test Failed.");
            }

            // Test 2: Call Cancellation
            System.out.println("\n[TEST 2] Testing Call Cancellation (CANCEL prior to 200 OK)...");
            FailureTestResult r2 = FailureInjectionEngine.runTest(
                    FailureScenario.CALL_CANCELLATION, SIMULATOR_EXT, MICROSIP_EXT, PBX_IP);
            System.out.println("Result: " + r2.details);
            System.out.println("Clean Resources: " + r2.resourceClean + " (Sockets: " + r2.initialSockets + " -> " + r2.finalSockets + ")");
            if (r2.passed) {
                passed++;
                System.out.println("PASS: CANCEL Handled Deterministically.");
            } else {
                failed++;
                System.err.println("FAIL: CANCEL Test Failed.");
            }

            // Test 3: 404 Invalid Destination
            System.out.println("\n[TEST 3] Testing 404 Invalid Destination against OXE...");
            FailureTestResult r3 = FailureInjectionEngine.runTest(
                    FailureScenario.INVALID_DESTINATION_404, SIMULATOR_EXT, "99999", PBX_IP);
            System.out.println("Result: " + r3.details);
            System.out.println("Clean Resources: " + r3.resourceClean + " (Sockets: " + r3.initialSockets + " -> " + r3.finalSockets + ")");
            if (r3.passed) {
                passed++;
                System.out.println("PASS: 404 Rejection Handled Deterministically.");
            } else {
                failed++;
                System.err.println("FAIL: 404 Test Failed.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n================================================================================");
        System.out.println("   CONTROLLED FAILURE RECOVERY RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("================================================================================");
        if (failed > 0) System.exit(1);
    }
}
