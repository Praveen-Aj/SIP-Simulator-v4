package test;

import sipinspector.Call.PerformanceTestEngine;
import sipinspector.Call.PerformanceTestEngine.CampaignConfig;
import sipinspector.Call.PerformanceTestEngine.CampaignResult;
import sipinspector.Call.PerformanceTestEngine.ExecutionMode;
import sipinspector.Media.LiveAudioController;

/**
 * Priority 2, 4, 5: Performance Test Engine & ITU-T E-model MOS Calculation Test.
 *
 * Validates:
 * - Accuracy of ITU-T E-model G.107 MOS score formula
 * - PerformanceTestEngine campaign lifecycle (start, execute, stop)
 * - Safe cancellation and cleanup
 */
public class PerformanceCampaignEngineTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — PERFORMANCE ENGINE & MOS VALIDATION TEST");
        System.out.println("================================================================================");

        int passed = 0;
        int failed = 0;

        try {
            // Test 1: ITU-T E-model G.107 Mathematical Model Verification
            System.out.println("\n[TEST 1] Verifying ITU-T E-model G.107 MOS Calculation Accuracy...");

            // Case A: Ideal conditions (0% loss, 0ms jitter) -> MOS ~ 4.4 - 4.5
            double mosIdeal = LiveAudioController.calculateMos(0.0, 0.0);
            System.out.println(" - 0% loss, 0ms jitter MOS: " + mosIdeal);
            if (mosIdeal >= 4.3 && mosIdeal <= 4.5) {
                System.out.println("PASS: Ideal audio yields pristine MOS (>= 4.3).");
                passed++;
            } else {
                System.err.println("FAIL: Expected MOS >= 4.3 for ideal audio, got: " + mosIdeal);
                failed++;
            }

            // Case B: Moderate network jitter & loss (2% loss, 30ms jitter) -> MOS ~ 3.8 - 4.1
            double mosMod = LiveAudioController.calculateMos(2.0, 30.0);
            System.out.println(" - 2% loss, 30ms jitter MOS: " + mosMod);
            if (mosMod >= 3.7 && mosMod < mosIdeal) {
                System.out.println("PASS: Moderate impairment yields expected mid-range MOS (3.7 - 4.1).");
                passed++;
            } else {
                System.err.println("FAIL: Expected mid-range MOS for 2% loss, got: " + mosMod);
                failed++;
            }

            // Case C: Heavy network degradation (15% loss, 100ms jitter) -> MOS ~ 1.5 - 2.5
            double mosHeavy = LiveAudioController.calculateMos(15.0, 100.0);
            System.out.println(" - 15% loss, 100ms jitter MOS: " + mosHeavy);
            if (mosHeavy >= 1.0 && mosHeavy < 2.8) {
                System.out.println("PASS: Severe degradation yields expected low MOS (< 2.8).");
                passed++;
            } else {
                System.err.println("FAIL: Expected low MOS for 15% loss, got: " + mosHeavy);
                failed++;
            }

            // Case D: Extreme loss (100% loss) -> MOS = 1.0
            double mosExtreme = LiveAudioController.calculateMos(100.0, 500.0);
            System.out.println(" - 100% loss MOS: " + mosExtreme);
            if (Math.abs(mosExtreme - 1.0) < 0.01) {
                System.out.println("PASS: Extreme loss clamps to minimum MOS 1.0.");
                passed++;
            } else {
                System.err.println("FAIL: Expected MOS 1.0 for 100% loss, got: " + mosExtreme);
                failed++;
            }

            // Test 2: Performance Test Engine Campaign Initialization & Cancellation
            System.out.println("\n[TEST 2] Verifying PerformanceTestEngine Lifecycle & Safe Cancellation...");
            PerformanceTestEngine engine = PerformanceTestEngine.getInstance();
            CampaignConfig cfg = new CampaignConfig();
            cfg.name = "Unit Test Campaign";
            cfg.mode = ExecutionMode.SEQUENTIAL;
            cfg.caller = "90008";
            cfg.callee = "90001";
            cfg.totalCalls = 1;
            cfg.durationSeconds = 10;
            cfg.autoAnswer = false;

            boolean started = engine.startCampaign(cfg);
            if (started && engine.isRunning()) {
                System.out.println("PASS: Performance campaign started successfully!");
                passed++;
            } else {
                System.err.println("FAIL: Failed to start campaign.");
                failed++;
            }

            // Test immediate safe cancellation
            Thread.sleep(500);
            engine.stopCampaign();
            Thread.sleep(300);

            if (!engine.isRunning()) {
                System.out.println("PASS: Performance campaign safely cancelled and stopped!");
                passed++;
            } else {
                System.err.println("FAIL: Campaign failed to stop.");
                failed++;
            }

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n================================================================================");
        System.out.println("   PERFORMANCE CAMPAIGN TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("================================================================================");
        if (failed > 0) System.exit(1);
    }
}
