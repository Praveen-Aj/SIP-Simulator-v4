package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import sipinspector.GlobalSettings;
import sipinspector.Utils.AppPaths;

/**
 * Portability Validation Test Suite for SIP Simulator v4
 *
 * Validates:
 * 1. Application root detection independent of current working directory.
 * 2. Deterministic relative resource resolution (scenarios, values, config, web-app).
 * 3. Handling of filesystem paths containing spaces without URL corruption.
 * 4. Configuration discovery and persistence using centralized AppPaths.
 * 5. Dynamic log directory discovery and write permissions.
 * 6. Runtime Java and Node.js binary resolution without machine-specific hardcoding.
 */
public class PortabilityValidationTest {

    public static void main(String[] args) {
        System.out.println("=== Running SIP Simulator v4 Portability Validation Suite ===");
        int passed = 0;
        int total = 7;

        try {
            // [TEST 1] Application Root Detection
            System.out.print("[TEST 1] Application root detection: ");
            File appRoot = AppPaths.getAppRoot();
            if (appRoot != null && appRoot.exists() && appRoot.isDirectory()) {
                File scenarios = new File(appRoot, "scenarios");
                File config = new File(appRoot, "config");
                if (scenarios.exists() || config.exists()) {
                    System.out.println("PASSED (Discovered: " + appRoot.getName() + ")");
                    passed++;
                } else {
                    System.err.println("FAILED (appRoot lacks expected subdirectories)");
                }
            } else {
                System.err.println("FAILED (appRoot is null or invalid)");
            }

            // [TEST 2] Relative Resource Resolution
            System.out.print("[TEST 2] Relative resource directories resolution: ");
            File scenariosDir = AppPaths.getScenariosDir();
            File valuesDir = AppPaths.getValuesDir();
            File configDir = AppPaths.getConfigDir();
            File webAppDir = AppPaths.getWebAppDir();

            if (scenariosDir.exists() && valuesDir.exists() && configDir.exists() && webAppDir.exists()) {
                System.out.println("PASSED (scenarios, values, config, web-app all verified)");
                passed++;
            } else {
                System.err.println("FAILED (Missing directory: scenarios=" + scenariosDir.exists()
                        + " values=" + valuesDir.exists() + " config=" + configDir.exists()
                        + " webApp=" + webAppDir.exists() + ")");
            }

            // [TEST 3] Paths with Spaces Handling
            System.out.print("[TEST 3] Filesystem paths with spaces and separators: ");
            File testPathWithSpaces = AppPaths.resolve("scenarios/Outgoing basic call test space.tmp");
            String pathStr = testPathWithSpaces.getAbsolutePath();
            if (!pathStr.contains("%20") && (pathStr.contains(" ") || pathStr.contains(File.separator))) {
                System.out.println("PASSED (Clean URI-decoded path: " + testPathWithSpaces.getName() + ")");
                passed++;
            } else {
                System.err.println("FAILED (Path contains URL encoding or invalid characters: " + pathStr + ")");
            }

            // [TEST 4] Configuration Discovery & Persistence
            System.out.print("[TEST 4] Centralized configuration discovery: ");
            File configFile = AppPaths.getConfigFile("settings.properties");
            if (configFile.exists() && configFile.isFile()) {
                GlobalSettings.loadSettings();
                String theme = GlobalSettings.getSetting("defaultTheme");
                if (theme != null && !theme.trim().isEmpty()) {
                    System.out.println("PASSED (Found settings.properties, loaded theme: " + theme + ")");
                    passed++;
                } else {
                    System.err.println("FAILED (Theme setting missing)");
                }
            } else {
                System.err.println("FAILED (settings.properties not found at " + configFile.getAbsolutePath() + ")");
            }

            // [TEST 5] Dynamic Logs Directory Discovery & Writability
            System.out.print("[TEST 5] Dynamic log directory discovery and write test: ");
            File logsDir = AppPaths.getLogsDir();
            if (logsDir != null && (logsDir.exists() || logsDir.mkdirs())) {
                File tempLog = new File(logsDir, "portability_test_" + System.currentTimeMillis() + ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tempLog)) {
                    fos.write("portability verification\n".getBytes());
                }
                boolean wrote = tempLog.exists() && tempLog.length() > 0;
                tempLog.delete();
                if (wrote) {
                    System.out.println("PASSED (Writable log directory: " + logsDir.getAbsolutePath() + ")");
                    passed++;
                } else {
                    System.err.println("FAILED (Could not write to log directory)");
                }
            } else {
                System.err.println("FAILED (Log directory null or cannot be created)");
            }

            // [TEST 6] Runtime Java Executable Resolution
            System.out.print("[TEST 6] Java runtime binary resolution: ");
            String javaBin = AppPaths.getJavaExecutable();
            if (javaBin != null && !javaBin.trim().isEmpty()) {
                boolean isValid = "java".equalsIgnoreCase(javaBin) || new File(javaBin).exists();
                if (isValid) {
                    System.out.println("PASSED (Resolved Java: " + javaBin + ")");
                    passed++;
                } else {
                    System.err.println("FAILED (Invalid Java binary: " + javaBin + ")");
                }
            } else {
                System.err.println("FAILED (Java binary null)");
            }

            // [TEST 7] Node.js Binary Resolution
            System.out.print("[TEST 7] Node.js binary resolution: ");
            String nodeBin = AppPaths.getNodeExecutable();
            if (nodeBin != null && !nodeBin.trim().isEmpty()) {
                boolean isValid = "node".equalsIgnoreCase(nodeBin) || new File(nodeBin).exists();
                if (isValid) {
                    System.out.println("PASSED (Resolved Node: " + nodeBin + ")");
                    passed++;
                } else {
                    System.err.println("FAILED (Invalid Node binary: " + nodeBin + ")");
                }
            } else {
                System.err.println("FAILED (Node binary null)");
            }

        } catch (Exception e) {
            System.err.println("CRITICAL TEST FAILURE: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=================================================");
        System.out.printf("PORTABILITY VALIDATION SUMMARY: %d / %d TESTS PASSED\n", passed, total);
        System.out.println("=================================================");

        if (passed != total) {
            System.exit(1);
        }
    }
}
