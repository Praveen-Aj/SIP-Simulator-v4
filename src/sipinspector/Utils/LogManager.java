/*
 *  SIP Simulator v2 - open source SIP signaling simulator.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sipinspector.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Calendar;

import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.UI.EnhancedStatusBar;

/**
 * Coordinates all logging (SIP transcripts + transport debug logs).
 */
public final class LogManager {

    // Human-readable SIP log file name format, e.g. "22 Nov 2025 6.14 PM"
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("dd MMM yyyy h.mm a");
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private static FileOutputStream sipLogStream;
    private static String sipLogPath;
    private static String currentTransport = "UDP";

    private LogManager() {
        // Utility
    }

    public static synchronized void startLogging(String transport) {
        String normalizedTransport = normalizeTransport(transport);
        currentTransport = normalizedTransport;
        ensureSipLogStream();
        TLSDebugLogger.enable(normalizedTransport);
        GlobalSettings.logsEnabled = true;
        GlobalSettings.setSetting("logsEnabled", "true");
        GlobalSettings.saveSettings();
        updateStatusBar(true);
    }

    public static synchronized void stopLogging() {
        TLSDebugLogger.disable();
        if (sipLogStream != null) {
            try {
                sipLogStream.close();
            } catch (IOException ignore) {
            }
            sipLogStream = null;
            sipLogPath = null;
        }
        GlobalSettings.logsEnabled = false;
        GlobalSettings.setSetting("logsEnabled", "false");
        GlobalSettings.saveSettings();
        updateStatusBar(false);
    }

    public static synchronized void onTransportChanged(String transport) {
        currentTransport = normalizeTransport(transport);
        if (GlobalSettings.logsEnabled) {
            TLSDebugLogger.disable();
            TLSDebugLogger.enable(currentTransport);
        }
    }

    public static synchronized FileOutputStream getSipLogStream() {
        return sipLogStream;
    }

    public static synchronized boolean isLoggingActive() {
        return sipLogStream != null && GlobalSettings.logsEnabled;
    }

    /**
     * Write an RTP/audio event (session start/stop, DTMF, stats) into the
     * same SIP log file browsed in the Logs tab, so media events show up
     * alongside SIP messages in "Raw Log Text". Does not affect the ladder
     * diagram, which only recognizes SIP message markers.
     */
    public static void logRtpEvent(String message) {
        FileOutputStream fos = sipLogStream;
        if (fos == null || !GlobalSettings.logsEnabled) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
        String time = sdf.format(Calendar.getInstance().getTime());
        String txt = "==== RTP/AUDIO EVENT [" + time + "] ====" + System.lineSeparator()
                + message + System.lineSeparator()
                + "====================================" + System.lineSeparator();
        try {
            synchronized (fos) {
                fos.write(txt.getBytes());
            }
        } catch (IOException ignore) {
        }
    }

    public static void openLogsFolder(String subFolder) {
        try {
            File dir = subFolder == null
                    ? new File(TLSDebugLogger.LOG_BASE_DIR)
                    : new File(TLSDebugLogger.LOG_BASE_DIR, subFolder);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                new ProcessBuilder("open", dir.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
            }
        } catch (IOException e) {
            System.err.println("Failed to open logs folder: " + e.getMessage());
        }
    }

    private static void ensureSipLogStream() {
        if (sipLogStream != null) {
            return;
        }
        try {
            File dir = new File(TLSDebugLogger.LOG_BASE_DIR, "SIP");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String timestamp = FILE_NAME_FORMAT.format(new Date());
            sipLogPath = new File(dir, "SIP " + timestamp + ".log").getAbsolutePath();
            sipLogStream = new FileOutputStream(sipLogPath, true);
            cleanupOldLogs(dir);
        } catch (IOException e) {
            System.err.println("Failed to open SIP log file: " + e.getMessage());
        }
    }

    private static void cleanupOldLogs(File dir) {
        File[] files = dir.listFiles((file) -> file.isFile() && file.getName().endsWith(".log"));
        if (files == null || files.length == 0) {
            return;
        }
        int maxFiles = parseInt(GlobalSettings.getSetting("maxLogFiles"), 10);
        int retentionDays = parseInt(GlobalSettings.getSetting("logRetentionDays"), 7);
        long cutoff = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            boolean beyondMax = i >= maxFiles;
            boolean beyondRetention = file.lastModified() < cutoff;
            if (beyondMax || beyondRetention) {
                file.delete();
            }
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static String normalizeTransport(String transport) {
        if (transport == null || transport.isEmpty()) {
            transport = GlobalSettings.getTransport();
        }
        if (transport == null || transport.isEmpty()) {
            return "UDP";
        }
        return transport.toUpperCase();
    }

    private static void updateStatusBar(boolean enabled) {
        EnhancedStatusBar statusBar = SIPInspectorMainFrame.getStatusBar();
        if (statusBar != null) {
            statusBar.setLogsEnabled(enabled);
        }
    }
}
