/*
 * Transport Debug Logger - Unified logging for UDP/TCP/TLS
 */
package sipinspector.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Centralized transport debug logging (UDP/TCP/TLS)
 * Can be enabled/disabled via system property or programmatically
 * Creates separate log files for each session
 */
public class TLSDebugLogger {

    private static boolean enabled = false;
    private static PrintWriter logWriter = null;
    public static final String LOG_BASE_DIR = System.getProperty("user.home")
            + File.separator + "Desktop"
            + File.separator + "SIP Simulator logs";
    private static String currentLogFile = null;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    // Human-readable file name format, e.g. "22 Nov 2025 6.14 PM"
    private static SimpleDateFormat fileNameFormat = new SimpleDateFormat("dd MMM yyyy h.mm a");

    private static final int MAX_LOG_FILES = 10;
    private static final long MAX_LOG_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L; // 7 days

    static {
        // Check system property for debug flag
        String debugFlag = System.getProperty("tls.debug");
        if ("true".equalsIgnoreCase(debugFlag)) {
            enable();
        }
    }

    /**
     * Enable debug logging for specified transport
     * 
     * @param transport "UDP", "TCP", or "TLS"
     */
    public static synchronized void enable(String transport) {
        if (!enabled) {
            try {
                // Create transport-specific directory
                String transportDir = LOG_BASE_DIR + "/" + transport.toUpperCase();
                File logDir = new File(transportDir);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }

                applyRetentionPolicy(logDir);

                // Create session-specific log file with timestamp, e.g. "UDP 22 Nov 2025 6.21 PM.log"
                String timestamp = fileNameFormat.format(new Date());
                String uniqueSuffix = System.getProperty("logs.unique.id", "");
                if (uniqueSuffix != null && !uniqueSuffix.trim().isEmpty()) {
                    uniqueSuffix = "_" + uniqueSuffix.trim();
                } else {
                    uniqueSuffix = "";
                }
                currentLogFile = transportDir + "/" + transport.toUpperCase() + " " + timestamp + uniqueSuffix + ".log";

                logWriter = new PrintWriter(new FileWriter(currentLogFile, false), true);
                enabled = true;
                log("=== " + transport.toUpperCase() + " Debug Logging ENABLED ===");
                log("Session started: " + dateFormat.format(new Date()));
                log("Log file: " + currentLogFile);
                log("");
            } catch (IOException e) {
                System.err.println("Failed to enable debug logging: " + e.getMessage());
            }
        }
    }

    /**
     * Enable TLS debug logging (backward compatibility)
     */
    public static synchronized void enable() {
        enable("TLS");
    }

    /**
     * Disable TLS debug logging
     */
    public static synchronized void disable() {
        if (enabled) {
            log("=== TLS Debug Logging DISABLED ===");
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            enabled = false;
        }
    }

    /**
     * Check if logging is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Log a message
     */
    public static synchronized void log(String message) {
        if (enabled && logWriter != null) {
            String timestamp = dateFormat.format(new Date());
            logWriter.println(timestamp + " " + message);
            logWriter.flush();
        }
    }

    /**
     * Log with format
     */
    public static void logf(String format, Object... args) {
        if (enabled) {
            log(String.format(format, args));
        }
    }

    /**
     * Log exception
     */
    public static synchronized void logException(String message, Throwable t) {
        if (enabled && logWriter != null) {
            String timestamp = dateFormat.format(new Date());
            logWriter.println(timestamp + " " + message);
            t.printStackTrace(logWriter);
            logWriter.flush();
        }
    }

    /**
     * Get log file path
     */
    public static String getLogFilePath() {
        return currentLogFile;
    }

    private static void applyRetentionPolicy(File logDir) {
        File[] files = logDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null || files.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();

        for (File f : files) {
            long age = now - f.lastModified();
            if (age > MAX_LOG_AGE_MILLIS) {
                f.delete();
            }
        }

        files = logDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null || files.length <= MAX_LOG_FILES) {
            return;
        }

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                long diff = f1.lastModified() - f2.lastModified();
                if (diff < 0) {
                    return -1;
                } else if (diff > 0) {
                    return 1;
                }
                return 0;
            }
        });

        int toDelete = files.length - MAX_LOG_FILES;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }
}
