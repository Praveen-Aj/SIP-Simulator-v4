package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-production test validating repository agent governance,
 * file presence, generic link portability, and declaration integrity.
 *
 * Strict Java 8 compatibility.
 */
public class GovernanceIntegrityValidationTest {

    // Regex matching any markdown hyperlink target: [text](target) or ![alt](target)
    private static final Pattern MD_LINK_PATTERN = Pattern.compile("!?\\[[^\\]]*\\]\\(([^\\)]+)\\)");

    // Generic pattern detecting machine-specific links/URIs across any drive letter (A-Z)
    // Matches file:///C:/, file:///D:/, file:///c:/, file://C:/, C:\..., D:/Projects/..., etc.
    private static final Pattern GENERIC_MACHINE_LINK_PATTERN = Pattern.compile(
        "(?i)^(?:file:(?:/{2,3}|\\\\{2,3})[a-z]:[/\\\\]|" + // file:///C:/, file:///d:/, file://e:/
        "file:(?:/{2,3}|\\\\{2,3})|" +                       // absolute file:// links regardless of drive
        "[a-z]:[/\\\\]|" +                                   // C:\, D:/, etc.
        "/?[a-z]:[/\\\\]|" +                                 // /C:/
        "[a-z]:[/\\\\](?:users|projects|desktop|downloads)|" + // C:\Users, D:/Projects
        "file:[^\\s)]*)"                                     // any remaining file: protocol URI
    );

    // Generic pattern detecting absolute paths embedded in raw text or inline code
    private static final Pattern GENERIC_ABSOLUTE_PATH_PATTERN = Pattern.compile(
        "(?i)(?:file:(?:/{2,3}|\\\\{2,3})[a-z]:[/\\\\]|" +  // file:///C:/, file:///D:/
        "[a-z]:\\\\(?:users|projects|desktop|downloads)\\\\|" + // C:\Users\, D:\Projects\
        "[a-z]:/(?:users|projects|desktop|downloads)/)"     // C:/Users/, D:/Projects/
    );

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

    /**
     * Evaluates whether a markdown link target is machine-specific.
     * Pure function used for runtime auditing and synthetic vector self-testing.
     */
    public static boolean isMachineSpecificLink(String target) {
        if (target == null || target.trim().isEmpty()) {
            return false;
        }
        String clean = target.trim();
        // Remove trailing title or anchor if enclosed
        int spaceIdx = clean.indexOf(' ');
        if (spaceIdx > 0) {
            clean = clean.substring(0, spaceIdx);
        }
        return GENERIC_MACHINE_LINK_PATTERN.matcher(clean).find();
    }

    /**
     * Determines whether a line containing a path is an explicit documentation rule,
     * negative example, or placeholder rather than an active machine path.
     */
    private static boolean isDocumentationExampleOrRule(String line) {
        String lower = line.toLowerCase();
        return lower.contains("prohibited:") ||
               lower.contains("never use") ||
               lower.contains("prohibited") ||
               lower.contains("e.g.") ||
               lower.contains("example") ||
               lower.contains("<username>") ||
               lower.contains("<any-specific-user>") ||
               lower.contains("<you>") ||
               lower.contains("<teammate>") ||
               lower.contains("[test ");
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   AGENT GOVERNANCE & INTEGRITY VALIDATION TEST");
        System.out.println("================================================================================");

        File root = new File(".");

        // 1. Root Level Instruction Files Presence
        System.out.print("[TEST 1/6] Root Level Instruction Files Presence... ");
        File agentsMd = new File(root, "AGENTS.md");
        File claudeMd = new File(root, "CLAUDE.md");
        assertTrue("AGENTS.md must exist at repository root", agentsMd.exists() && agentsMd.length() > 500);
        assertTrue("CLAUDE.md must exist at repository root", claudeMd.exists() && claudeMd.length() > 200);
        System.out.println("PASSED");

        // 2. Mandatory QA & Development Documentation Suite Presence
        System.out.print("[TEST 2/6] Mandatory QA & Development Document Suite Presence... ");
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

        // 3. Generic Machine-Path Detection Synthetic Vector Self-Test
        System.out.print("[TEST 3/6] Generic Machine-Path Detection Self-Test on Vector Matrix... ");
        List<String> invalidTestVectors = Arrays.asList(
            "file:///C:/Users/Praveen/Downloads/test.md",
            "file:///D:/Workspace/repo/test.md",
            "file:///E:/Telecom/data.txt",
            "file:///c:/Users/Praveen/test.md",
            "file:///d:/Projects/SIP_Simulator/test.md",
            "C:\\Users\\Praveen\\Desktop",
            "D:\\Users\\Admin\\Documents",
            "C:/Projects/SIP_Simulator",
            "D:/Projects/voip",
            "file://localhost/C:/Users/test",
            "file://D:/something",
            "E:\\Projects\\app"
        );

        for (String invalidVector : invalidTestVectors) {
            assertTrue("Expected vector to be flagged as machine-specific: " + invalidVector,
                isMachineSpecificLink(invalidVector));
        }

        List<String> validTestVectors = Arrays.asList(
            "docs/QA_AGENT_PROTOCOL.md",
            "QA_AGENT_PROTOCOL.md",
            "../AGENTS.md",
            "https://github.com/Praveen-Aj/SIP-Simulator-v4",
            "http://localhost:3000",
            "#section-1",
            "images/diagram.png"
        );

        for (String validVector : validTestVectors) {
            assertTrue("Expected vector to be accepted as portable: " + validVector,
                !isMachineSpecificLink(validVector));
        }
        System.out.println("PASSED (" + (invalidTestVectors.size() + validTestVectors.size()) + " vectors tested)");

        // 4. Live Markdown Link Portability Audit Across Repository
        System.out.print("[TEST 4/6] Repository-Wide Markdown Link Portability Audit... ");
        List<File> mdFilesToScan = new ArrayList<File>();
        File docsDir = new File(root, "docs");
        File[] docFiles = docsDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (docFiles != null) {
            mdFilesToScan.addAll(Arrays.asList(docFiles));
        }
        File[] rootFiles = root.listFiles((dir, name) -> name.endsWith(".md"));
        if (rootFiles != null) {
            mdFilesToScan.addAll(Arrays.asList(rootFiles));
        }

        int totalLinksAudited = 0;
        for (File md : mdFilesToScan) {
            try {
                String content = readFile(md);
                Matcher matcher = MD_LINK_PATTERN.matcher(content);
                while (matcher.find()) {
                    String linkTarget = matcher.group(1);
                    boolean isMachineLink = isMachineSpecificLink(linkTarget);
                    assertTrue("Machine-specific markdown link found in " + md.getName() + " -> " + linkTarget,
                        !isMachineLink);
                    totalLinksAudited++;
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan " + md.getName(), e);
            }
        }
        System.out.println("PASSED (" + totalLinksAudited + " links verified across " + mdFilesToScan.size() + " files)");

        // 5. CLAUDE.md Delegation to AGENTS.md
        System.out.print("[TEST 5/6] CLAUDE.md Clean Delegation to AGENTS.md... ");
        try {
            String claudeContent = readFile(claudeMd);
            assertTrue("CLAUDE.md must explicitly reference AGENTS.md", claudeContent.contains("AGENTS.md"));
            assertTrue("CLAUDE.md must reference QA_AGENT_PROTOCOL.md", claudeContent.contains("QA_AGENT_PROTOCOL.md"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CLAUDE.md", e);
        }
        System.out.println("PASSED");

        // 6. AGENTS.md Governance Content & Declaration Integrity
        System.out.print("[TEST 6/6] AGENTS.md Declaration Templates & Rules Integrity... ");
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

        System.out.println("\n>>> ALL AGENT GOVERNANCE INTEGRITY CHECKS PASSED CLEANLY!");
    }
}
