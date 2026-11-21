package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Non-production test validating repository agent governance,
 * file presence, link portability, and declaration integrity.
 *
 * Strict Java 8 compatibility.
 */
public class GovernanceIntegrityValidationTest {

    private static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError("GOVERNANCE VALIDATION FAILED: " + message);
        }
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   AGENT GOVERNANCE & INTEGRITY VALIDATION TEST");
        System.out.println("================================================================================");

        File root = new File(".");

        // 1. Root Level Instruction Files Presence
        System.out.print("[TEST 1/5] Root Level Instruction Files Presence... ");
        File agentsMd = new File(root, "AGENTS.md");
        File claudeMd = new File(root, "CLAUDE.md");
        assertTrue("AGENTS.md must exist at repository root", agentsMd.exists() && agentsMd.length() > 500);
        assertTrue("CLAUDE.md must exist at repository root", claudeMd.exists() && claudeMd.length() > 200);
        System.out.println("PASSED");

        // 2. Mandatory QA & Development Documentation Suite Presence
        System.out.print("[TEST 2/5] Mandatory QA & Development Document Suite Presence... ");
        List<String> requiredDocs = Arrays.asList(
            "docs/QA_AGENT_PROTOCOL.md",
            "docs/QA_TEST_STRATEGY.md",
            "docs/QA_TEST_CASES.md",
            "docs/QA_UI_UX_EVALUATION.md",
            "docs/QA_REPORT_TEMPLATE.md",
            "docs/DEVELOPMENT_STANDARDS.md",
            "docs/CODING_RULES.md",
            "docs/TESTING_CHECKLIST.md",
            "docs/OPEN_ISSUES.md",
            "docs/DEVELOPMENT_ROADMAP.md"
        );

        for (String docPath : requiredDocs) {
            File docFile = new File(root, docPath);
            assertTrue("Required document missing: " + docPath, docFile.exists() && docFile.length() > 200);
        }
        System.out.println("PASSED (" + requiredDocs.size() + " documents verified)");

        // 3. Link Portability & Zero Machine-Specific Paths
        System.out.print("[TEST 3/5] Markdown Link Portability (No machine-specific file:/// links)... ");
        File docsDir = new File(root, "docs");
        File[] mdFiles = docsDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (mdFiles != null) {
            for (File md : mdFiles) {
                try {
                    String content = readFile(md);
                    // Match markdown hyperlink destinations like (file:///C:/Users...) or (file:///c:/Users...)
                    boolean hasMachineSpecificLink = content.contains("(file:///C:/Users") ||
                                                     content.contains("(file:///c:/Users");
                    assertTrue("Machine-specific file URL found in " + md.getName(), !hasMachineSpecificLink);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read " + md.getName(), e);
                }
            }
        }
        System.out.println("PASSED");

        // 4. CLAUDE.md Delegation to AGENTS.md
        System.out.print("[TEST 4/5] CLAUDE.md Clean Delegation to AGENTS.md... ");
        try {
            String claudeContent = readFile(claudeMd);
            assertTrue("CLAUDE.md must explicitly reference AGENTS.md", claudeContent.contains("AGENTS.md"));
            assertTrue("CLAUDE.md must reference QA_AGENT_PROTOCOL.md", claudeContent.contains("QA_AGENT_PROTOCOL.md"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CLAUDE.md", e);
        }
        System.out.println("PASSED");

        // 5. AGENTS.md Governance Content & Declaration Integrity
        System.out.print("[TEST 5/5] AGENTS.md Declaration Templates & Rules Integrity... ");
        try {
            String agentsContent = readFile(agentsMd);
            assertTrue("AGENTS.md must contain DEV AGENT DECLARATION", agentsContent.contains("DEV AGENT DECLARATION"));
            assertTrue("AGENTS.md must contain QA AGENT DECLARATION", agentsContent.contains("QA AGENT DECLARATION"));
            assertTrue("AGENTS.md must enforce Java 8", agentsContent.contains("-source 1.8 -target 1.8"));
            assertTrue("AGENTS.md must forbid modifying src/** during QA", agentsContent.contains("src/**"));
            assertTrue("AGENTS.md must forbid modifying web-app/** during QA", agentsContent.contains("web-app/**"));
            assertTrue("AGENTS.md must forbid git force-push/reset", agentsContent.contains("force-push"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read AGENTS.md", e);
        }
        System.out.println("PASSED");

        System.out.println(">>> ALL AGENT GOVERNANCE INTEGRITY CHECKS PASSED CLEANLY!");
    }
}
