package test;

import java.io.PrintStream;

/**
 * Master Offline Full Regression Test Suite for SIP Simulator v4.
 *
 * Runs all non-PBX unit, integration, protocol, and performance engine tests
 * with zero external PBX or network dependencies.
 *
 * Returns exit code 0 if all tests pass; non-zero if any test fails.
 */
public class OfflineFullRegressionSuite {

    private static class SuiteRunner {
        int passedSuites = 0;
        int failedSuites = 0;

        void run(String suiteName, Runnable runnable) {
            System.out.println("\n################################################################################");
            System.out.println("   RUNNING SUITE: " + suiteName);
            System.out.println("################################################################################");
            try {
                runnable.run();
                System.out.println(">>> SUITE PASSED: " + suiteName);
                passedSuites++;
            } catch (Throwable t) {
                System.err.println(">>> SUITE FAILED: " + suiteName + " -> " + t.getMessage());
                t.printStackTrace();
                failedSuites++;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP SIMULATOR V4 — FULL OFFLINE REGRESSION TEST HARNESS");
        System.out.println("================================================================================");

        long startTime = System.currentTimeMillis();
        SuiteRunner runner = new SuiteRunner();

        // 1. Core Offline Validation (Codecs, SRTP, Parser, Synthesizer)
        runner.run("OfflineValidationSuite", () -> OfflineValidationSuite.main(new String[0]));

        // 2. Portability & Runtime Resolution
        runner.run("PortabilityValidationTest", () -> PortabilityValidationTest.main(new String[0]));

        // 3. UI Display & DPI Scaling
        runner.run("DisplayScalingValidationTest", () -> DisplayScalingValidationTest.main(new String[0]));

        // 4. TCP SIP Message Stream Framing & Buffer Reliability
        runner.run("TcpSipFramingValidationTest", () -> TcpSipFramingValidationTest.main(new String[0]));

        // 5. Security & SDES Master Key/Salt Cryptographic Parsing
        runner.run("SecurityManagerKeyParsingTest", () -> SecurityManagerKeyParsingTest.main(new String[0]));

        // 6. Call Flow & Dialog State Machine (INVITE, Re-INVITE Hold/Resume, Codecs)
        runner.run("CallFlowValidationTest", () -> CallFlowValidationTest.main(new String[0]));

        // 7. Multi-Call Dialog & Media Port Isolation
        runner.run("MultiCallOrchestrationTest", () -> MultiCallOrchestrationTest.main(new String[0]));

        // 8. SIP Transaction Reliability (Timer A Retransmission, Timer B Timeout)
        runner.run("SipTransactionReliabilityTest", () -> SipTransactionReliabilityTest.main(new String[0]));

        // 9. Controlled Failure Recovery (404, 408, CANCEL)
        runner.run("ControlledFailureRecoveryTest", () -> ControlledFailureRecoveryTest.main(new String[0]));

        // 10. Performance Campaign Engine & ITU-T E-Model G.107 MOS Validation
        runner.run("PerformanceCampaignEngineTest", () -> PerformanceCampaignEngineTest.main(new String[0]));

        // 11. Active Multi-Dialog Campaign Failure Recovery & Resource Leak Audit
        runner.run("ActiveCampaignFailureRecoveryTest", () -> ActiveCampaignFailureRecoveryTest.main(new String[0]));

        // 12. Phase 7: SIP Feature Completeness (DTMF, Transfer, Replaces, Early Media, Hold/Resume, Identity)
        runner.run("SipFeatureCompletenessTest", () -> SipFeatureCompletenessTest.main(new String[0]));

        // 13. Phase 7: Transport (UDP/TCP/TLS) & Security Credentials Sanitization Audit
        runner.run("TransportSecurityAuditTest", () -> {
            try {
                TransportSecurityAuditTest.main(new String[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 14. Phase 7: SIP Call Engine Lifecycle & Resource Rebind Audit
        runner.run("CallEngineLifecycleTest", () -> CallEngineLifecycleTest.main(new String[0]));

        // 15. Phase 7.1: Engine Protocol Integrity & Remediation Audit
        runner.run("EngineProtocolIntegrityTest", () -> EngineProtocolIntegrityTest.main(new String[0]));

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("\n================================================================================");
        System.out.println("   OFFLINE REGRESSION MASTER SUMMARY");
        System.out.println("   Total Test Suites Executed: " + (runner.passedSuites + runner.failedSuites));
        System.out.println("   Passed Suites:              " + runner.passedSuites);
        System.out.println("   Failed Suites:              " + runner.failedSuites);
        System.out.println("   Total Elapsed Time:         " + (elapsed / 1000.0) + " seconds");
        System.out.println("================================================================================");

        if (runner.failedSuites > 0) {
            System.err.println("REGRESSION HARNESS FAILED: One or more test suites reported failures.");
            System.exit(1);
        } else {
            System.out.println("ALL OFFLINE REGRESSION SUITES PASSED CLEANLY!");
            System.exit(0);
        }
    }
}
