/*
 * Generic Transport Debug Logger
 * Instance-based logger for a single transport type (UDP/TCP/TLS).
 */
package sipinspector.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generic per-instance transport debug logger.
 * Creates a separate, timestamped log file for each transport type and session.
 * (The application currently uses the static {@link TLSDebugLogger}; this class
 * provides an instance-based alternative for isolated per-leg logging.)
 */
public class TransportDebugLogger {

    private boolean enabled = false;
    private PrintWriter logWriter = null;
    private String currentLogFile = null;
    private final String transportType;

    private static final String LOG_BASE_DIR = TLSDebugLogger.LOG_BASE_DIR;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat fileNameFormat = new SimpleDateFormat("dd MMM yyyy h.mm a");

    public TransportDebugLogger(String transportType) {
        this.transportType = transportType == null ? "LOG" : transportType.toUpperCase();
    }

    /**
     * Enable logging: create the transport directory and a new session log file.
     */
    public synchronized void enable() {
        if (!enabled) {
            try {
                // Create transport-specific directory
                String transportDir = LOG_BASE_DIR + "/" + transportType;
                File logDir = new File(transportDir);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }

                // Create session-specific log file with timestamp
                String timestamp = fileNameFormat.format(new Date());
                String uniqueSuffix = System.getProperty("logs.unique.id", "");
                if (uniqueSuffix != null && !uniqueSuffix.trim().isEmpty()) {
                    uniqueSuffix = "_" + uniqueSuffix.trim();
                } else {
                    uniqueSuffix = "";
                }
                currentLogFile = transportDir + "/" + transportType + " " + timestamp + uniqueSuffix + ".log";

                logWriter = new PrintWriter(new FileWriter(currentLogFile, false), true);
                enabled = true;

                log("=== " + transportType + " Debug Logging ENABLED ===");
                log("Session started: " + dateFormat.format(new Date()));
                log("Log file: " + currentLogFile);
                log("");
            } catch (IOException e) {
                System.err.println("Failed to enable " + transportType + " debug logging: " + e.getMessage());
            }
        }
    }

    /**
     * Disable logging and close the current log file.
     */
    public synchronized void disable() {
        if (enabled) {
            log("");
            log("=== " + transportType + " Debug Logging DISABLED ===");
            log("Session ended: " + dateFormat.format(new Date()));
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            enabled = false;
        }
    }

    /**
     * @return true if logging is currently enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Log a single message (timestamped).
     */
    public synchronized void log(String message) {
        if (enabled && logWriter != null) {
            String timestamp = dateFormat.format(new Date());
            logWriter.println(timestamp + " " + message);
            logWriter.flush();
        }
    }

    /**
     * Log a formatted message.
     */
    public void logf(String format, Object... args) {
        if (enabled) {
            log(String.format(format, args));
        }
    }

    /**
     * Log a message followed by an exception stack trace.
     */
    public synchronized void logException(String message, Throwable t) {
        if (enabled && logWriter != null) {
            String timestamp = dateFormat.format(new Date());
            logWriter.println(timestamp + " " + message);
            t.printStackTrace(logWriter);
            logWriter.flush();
        }
    }

    /**
     * @return the path of the current session log file, or null if not enabled.
     */
    public String getLogFilePath() {
        return currentLogFile;
    }
}
