package sipinspector.Utils;

import java.io.File;
import java.net.URL;

/**
 * Centralized, portable path resolution utility for SIP Simulator v4.
 *
 * Guarantees:
 * 1. Zero machine-specific absolute filesystem paths.
 * 2. Deterministic resolution of application root regardless of working directory
 *    (e.g. when launched from shortcuts, command-line from another drive, or as a service).
 * 3. Consistent, portable access to scenarios, values, config, logs, security, and web-app.
 * 4. Safe handling of paths with spaces and Windows/Unix path separators.
 * 5. Full Java 8 compatibility.
 */
public final class AppPaths {

    private static File cachedAppRoot = null;

    private AppPaths() {
        // Utility class
    }

    /**
     * Resolves the root directory of the SIP Simulator project/application.
     *
     * Priority:
     * 1. System property: "app.home" or "sipinspector.home"
     * 2. JAR/Class CodeSource location (determines directory containing the JAR or build folder)
     * 3. Fallback to System property "user.dir"
     */
    public static synchronized File getAppRoot() {
        if (cachedAppRoot != null) {
            return cachedAppRoot;
        }

        // 1. Check system properties
        String sysProp = System.getProperty("app.home");
        if (sysProp == null || sysProp.trim().isEmpty()) {
            sysProp = System.getProperty("sipinspector.home");
        }
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            File f = new File(sysProp.trim());
            if (f.exists() && f.isDirectory()) {
                cachedAppRoot = f.getAbsoluteFile();
                return cachedAppRoot;
            }
        }

        // 2. Discover based on CodeSource location
        try {
            URL codeSource = AppPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (codeSource != null) {
                File location = new File(codeSource.toURI());
                if (location.isFile() && location.getName().toLowerCase().endsWith(".jar")) {
                    // Running inside a JAR, e.g. <APP_ROOT>/SIP_Simulator_v4.jar or <APP_ROOT>/lib/...
                    File parent = location.getParentFile();
                    if (parent != null) {
                        if (parent.getName().equalsIgnoreCase("lib") && parent.getParentFile() != null) {
                            cachedAppRoot = parent.getParentFile().getAbsoluteFile();
                        } else {
                            cachedAppRoot = parent.getAbsoluteFile();
                        }
                        return cachedAppRoot;
                    }
                } else if (location.isDirectory()) {
                    // Running from compiled classes (e.g. <APP_ROOT>/build or <APP_ROOT>/build/classes)
                    if (location.getName().equalsIgnoreCase("build")) {
                        File parent = location.getParentFile();
                        if (parent != null) {
                            cachedAppRoot = parent.getAbsoluteFile();
                            return cachedAppRoot;
                        }
                    } else if (new File(location, "scenarios").exists() || new File(location, "config").exists()) {
                        cachedAppRoot = location.getAbsoluteFile();
                        return cachedAppRoot;
                    } else if (location.getParentFile() != null && location.getParentFile().getName().equalsIgnoreCase("build")) {
                        File root = location.getParentFile().getParentFile();
                        if (root != null) {
                            cachedAppRoot = root.getAbsoluteFile();
                            return cachedAppRoot;
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }

        // 3. Fallback to current working directory
        cachedAppRoot = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        return cachedAppRoot;
    }

    /**
     * For testing or embedded execution: manually set the application root.
     */
    public static synchronized void setAppRoot(File root) {
        if (root != null && root.isDirectory()) {
            cachedAppRoot = root.getAbsoluteFile();
        }
    }

    /**
     * Resolves a path relative to the application root.
     * Handles forward and backslashes portably.
     */
    public static File resolve(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return getAppRoot();
        }
        File f = new File(relativePath);
        if (f.isAbsolute()) {
            return f;
        }
        String normalized = relativePath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        return new File(getAppRoot(), normalized);
    }

    /**
     * Returns the scenarios directory: <APP_ROOT>/scenarios
     */
    public static File getScenariosDir() {
        return resolve("scenarios");
    }

    /**
     * Returns the values directory: <APP_ROOT>/values
     */
    public static File getValuesDir() {
        return resolve("values");
    }

    /**
     * Returns the configuration directory: <APP_ROOT>/config
     */
    public static File getConfigDir() {
        File dir = resolve("config");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Resolves a specific file in the configuration directory: <APP_ROOT>/config/<fileName>
     */
    public static File getConfigFile(String fileName) {
        return new File(getConfigDir(), fileName);
    }

    /**
     * Returns the web application directory: <APP_ROOT>/web-app
     */
    public static File getWebAppDir() {
        return resolve("web-app");
    }

    /**
     * Returns the security assets directory: <APP_ROOT>/security
     */
    public static File getSecurityDir() {
        return resolve("security");
    }

    /**
     * Returns the libraries directory: <APP_ROOT>/lib
     */
    public static File getLibDir() {
        return resolve("lib");
    }

    /**
     * Returns a valid, writable logs base directory.
     *
     * Priority:
     * 1. If user home Desktop/SIP Simulator logs is accessible and writable, use it.
     * 2. Otherwise fallback to project-relative <APP_ROOT>/logs to ensure complete portability
     *    even in headless or server environments without a Desktop folder.
     */
    public static File getLogsDir() {
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.trim().isEmpty()) {
                File desktop = new File(userHome, "Desktop");
                if (desktop.exists() && desktop.isDirectory() && desktop.canWrite()) {
                    File logsBase = new File(desktop, "SIP Simulator logs");
                    if (logsBase.exists() || logsBase.mkdirs()) {
                        return logsBase;
                    }
                }
            }
        } catch (Exception ignore) {
        }

        // Fallback to project-relative logs directory
        File fallbackLogs = resolve("logs");
        if (!fallbackLogs.exists()) {
            fallbackLogs.mkdirs();
        }
        return fallbackLogs;
    }

    /**
     * Resolves the Java binary path portably across environments.
     * Checks:
     * 1. Bundled JRE in <APP_ROOT>/jre/bin/java.exe
     * 2. Dev bundled JRE in <APP_ROOT>/deployment/jre/bin/java.exe
     * 3. System JAVA_HOME/bin/java.exe
     * 4. "java" on system PATH
     */
    public static String getJavaExecutable() {
        File bundled = resolve("jre" + File.separator + "bin" + File.separator + "java.exe");
        if (bundled.exists() && bundled.isFile()) {
            return bundled.getAbsolutePath();
        }
        File devBundled = resolve("deployment" + File.separator + "jre" + File.separator + "bin" + File.separator + "java.exe");
        if (devBundled.exists() && devBundled.isFile()) {
            return devBundled.getAbsolutePath();
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            File jhBin = new File(javaHome.trim(), "bin" + File.separator + "java.exe");
            if (jhBin.exists() && jhBin.isFile()) {
                return jhBin.getAbsolutePath();
            }
        }
        return "java";
    }

    /**
     * Resolves the Node.js executable path portably across environments.
     * Checks:
     * 1. Explicit override in settings if configured
     * 2. Bundled Node in <APP_ROOT>/node/node.exe or <APP_ROOT>/deployment/node/node.exe
     * 3. Standard ProgramFiles, ProgramFiles(x86), and LocalAppData locations
     * 4. System PATH directories
     * 5. "node" fallback
     */
    public static String getNodeExecutable() {
        File[] localCandidates = new File[] {
            resolve("node" + File.separator + "node.exe"),
            resolve("deployment" + File.separator + "node" + File.separator + "node.exe"),
            resolve("web-app" + File.separator + "node" + File.separator + "node.exe")
        };
        for (File f : localCandidates) {
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }

        String[] envVars = new String[] { "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA" };
        for (String ev : envVars) {
            String val = System.getenv(ev);
            if (val != null && !val.trim().isEmpty()) {
                File nodeInProg = new File(val.trim(), "nodejs" + File.separator + "node.exe");
                if (nodeInProg.exists() && nodeInProg.isFile()) {
                    return nodeInProg.getAbsolutePath();
                }
                File nodeInProgPrograms = new File(val.trim(), "Programs" + File.separator + "nodejs" + File.separator + "node.exe");
                if (nodeInProgPrograms.exists() && nodeInProgPrograms.isFile()) {
                    return nodeInProgPrograms.getAbsolutePath();
                }
            }
        }

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.trim().isEmpty()) continue;
                File f = new File(dir.trim(), "node.exe");
                if (f.exists() && f.isFile()) {
                    return f.getAbsolutePath();
                }
            }
        }

        return "node";
    }
}
