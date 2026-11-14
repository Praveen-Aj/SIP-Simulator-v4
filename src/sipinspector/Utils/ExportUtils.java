/*
 * Export Utilities
 * Enhanced export functionality for scenarios, statistics, and logs
 */
package sipinspector.Utils;

import javax.swing.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for exporting data in various formats
 * @author SIP Support Team
 */
public class ExportUtils {
    
    /**
     * Export scenario to JSON format
     */
    public static boolean exportScenarioToJSON(String scenarioContent, File outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // Simple JSON export
            writer.write("{\n");
            writer.write("  \"scenario\": {\n");
            writer.write("    \"exportDate\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\",\n");
            writer.write("    \"content\": " + escapeJSON(scenarioContent) + "\n");
            writer.write("  }\n");
            writer.write("}\n");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Export statistics to CSV format
     */
    public static boolean exportStatisticsToCSV(int sent, int received, int invite, int register, 
                                                int bye, int ok200, int unauthorized401, 
                                                int forbidden403, int notFound404, int error500,
                                                File outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // CSV Header
            writer.write("Metric,Value\n");
            
            // Message counts
            writer.write("Messages Sent," + sent + "\n");
            writer.write("Messages Received," + received + "\n");
            writer.write("Total Messages," + (sent + received) + "\n");
            writer.write("\n");
            
            // Message types
            writer.write("INVITE Messages," + invite + "\n");
            writer.write("REGISTER Messages," + register + "\n");
            writer.write("BYE Messages," + bye + "\n");
            writer.write("\n");
            
            // Response codes
            writer.write("200 OK," + ok200 + "\n");
            writer.write("401 Unauthorized," + unauthorized401 + "\n");
            writer.write("403 Forbidden," + forbidden403 + "\n");
            writer.write("404 Not Found," + notFound404 + "\n");
            writer.write("500 Server Error," + error500 + "\n");
            writer.write("\n");
            
            // Timestamp
            writer.write("Export Date," + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Export message log to text file
     */
    public static boolean exportMessageLog(String logContent, File outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String separator = "================================================================================\n";
            writer.write(separator);
            writer.write("SIP Simulator - Message Log Export\n");
            writer.write("Export Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            writer.write(separator + "\n");
            writer.write(logContent);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Show export dialog and export data
     */
    public static void showExportDialog(JFrame parent, String title, String defaultFileName, 
                                       String fileExtension, ExportCallback callback) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setSelectedFile(new File(defaultFileName + fileExtension));
        
        int result = FileChooserUtils.showSaveDialog(parent, fileChooser);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            // Add extension if not present
            if (!file.getName().endsWith(fileExtension)) {
                file = new File(file.getAbsolutePath() + fileExtension);
            }
            
            // Check if file exists
            if (file.exists()) {
                int confirm = JOptionPane.showConfirmDialog(parent,
                    "File already exists. Overwrite?",
                    "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            
            // Perform export
            boolean success = callback.export(file);
            
            if (success) {
                JOptionPane.showMessageDialog(parent,
                    "✓ Export successful!\n\nFile: " + file.getAbsolutePath(),
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                    "✗ Export failed!\n\nPlease check file permissions and try again.",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Escape string for JSON
     */
    private static String escapeJSON(String str) {
        if (str == null) return "\"\"";
        
        StringBuilder sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
    
    /**
     * Callback interface for export operations
     */
    public interface ExportCallback {
        boolean export(File file);
    }
}
