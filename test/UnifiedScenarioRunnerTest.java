/*
 * SIP Simulator v4 - Phase 9 Validation
 * UnifiedScenarioRunnerTest: Validates headless scenario parsing and variable substitution (Workstream H)
 */
package test;

import sipinspector.Scenario.UnifiedScenarioRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class UnifiedScenarioRunnerTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: UNIFIED SCENARIO RUNNER (WORKSTREAM H) VALIDATION");
        System.out.println("================================================================================");

        testScenarioParsingAndVariableSubstitution();

        System.out.println(">>> ALL UNIFIED SCENARIO RUNNER TESTS PASSED!");
    }

    private static void testScenarioParsingAndVariableSubstitution() {
        System.out.print("[TEST 1/1] Headless Scenario Loading & Parsing... ");
        File tempScenario = null;
        try {
            tempScenario = File.createTempFile("test_scenario_", ".txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(tempScenario))) {
                pw.println("# SEND");
                pw.println("INVITE sip:[callee]@[remote_ip]:[remote_port] SIP/2.0");
                pw.println("Via: SIP/2.0/UDP [local_ip]:[local_port];branch=z9hG4bK[branch]");
                pw.println("From: <sip:[caller]@[remote_ip]>;tag=[from_tag]");
                pw.println("To: <sip:[callee]@[remote_ip]>");
                pw.println("Call-ID: [call_id]");
                pw.println("CSeq: 1 INVITE");
                pw.println("Content-Length: 0");
                pw.println("");
                pw.println("# PAUSE 100");
                pw.println("");
                pw.println("# RECEIVE");
                pw.println("SIP/2.0 200 OK");
                pw.println("");
            }

            UnifiedScenarioRunner runner = new UnifiedScenarioRunner();
            runner.setNetworkConfig("127.0.0.1", 5060, "192.168.0.20", 5060, "UDP");

            Map<String, String> vars = new HashMap<>();
            vars.put("caller", "90008");
            vars.put("callee", "90001");
            vars.put("branch", "b12345");
            vars.put("from_tag", "t98765");
            vars.put("call_id", "test-scen-call-01@127.0.0.1");
            runner.setVariables(vars);

            boolean loaded = runner.loadScenarioFile(tempScenario);
            if (!loaded) {
                throw new RuntimeException("Failed to load scenario file: " + tempScenario.getAbsolutePath());
            }

            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("Scenario parser test failed: " + e.getMessage(), e);
        } finally {
            if (tempScenario != null && tempScenario.exists()) {
                tempScenario.delete();
            }
        }
    }
}
