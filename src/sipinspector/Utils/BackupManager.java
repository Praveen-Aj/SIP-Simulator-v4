/*
 * Backup Manager
 * Handles file backup and restore operations
 */
package sipinspector.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Utility class for managing file backups and restores
 * Creates timestamped backups in backups/ folder
 * 
 * @author SIP Support Team
 */
public class BackupManager {

    private static final String BACKUP_DIR = "backups";
    private static final String SCENARIOS_BACKUP_DIR = BACKUP_DIR + File.separator + "scenarios_backup";
    private static final String VALUES_BACKUP_DIR = BACKUP_DIR + File.separator + "values_backup";
    private static final String TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss";

    static {
        // Create backup directories if they don't exist
        createDirectoryIfNotExists(SCENARIOS_BACKUP_DIR);
        createDirectoryIfNotExists(VALUES_BACKUP_DIR);
    }

    /**
     * Create a backup of a file with timestamp
     * 
     * @param sourceFile Path to the file to backup
     * @param backupType Type of backup ("scenario" or "values")
     * @return Path to the created backup file
     * @throws IOException If backup creation fails
     */
    public static String backupFile(String sourceFile, String backupType) throws IOException {
        File source = new File(sourceFile);
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + sourceFile);
        }

        String backupDir = backupType.equalsIgnoreCase("scenario") ? SCENARIOS_BACKUP_DIR : VALUES_BACKUP_DIR;
        createDirectoryIfNotExists(backupDir);

        // Create backup filename with timestamp
        String fileName = source.getName();
        String timestamp = new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date());
        String backupFileName = fileName.replaceAll("\\.txt$", "") + "_" + timestamp + ".bak";
        File backupFile = new File(backupDir + File.separator + backupFileName);

        // Copy file to backup location
        Files.copy(source.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return backupFile.getAbsolutePath();
    }

    /**
     * Get list of available backups for a file
     * 
     * @param originalFile Path to the original file
     * @param backupType   Type of backup ("scenario" or "values")
     * @return List of backup file paths
     */
    public static List<String> getBackups(String originalFile, String backupType) {
        List<String> backups = new ArrayList<>();
        String backupDir = backupType.equalsIgnoreCase("scenario") ? SCENARIOS_BACKUP_DIR : VALUES_BACKUP_DIR;
        File backupFolder = new File(backupDir);

        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            return backups;
        }

        File originalFileObj = new File(originalFile);
        String originalFileName = originalFileObj.getName().replaceAll("\\.txt$", "");

        // Find all backups for this file
        File[] files = backupFolder
                .listFiles((dir, name) -> name.startsWith(originalFileName) && name.endsWith(".bak"));

        if (files != null) {
            // Sort by date (newest first)
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                backups.add(file.getAbsolutePath());
            }
        }

        return backups;
    }

    /**
     * Restore a file from backup
     * 
     * @param backupFile Path to the backup file
     * @param targetFile Path where to restore the file
     * @throws IOException If restore fails
     */
    public static void restoreFile(String backupFile, String targetFile) throws IOException {
        File backup = new File(backupFile);
        if (!backup.exists()) {
            throw new IOException("Backup file does not exist: " + backupFile);
        }

        File target = new File(targetFile);
        Files.copy(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Delete a backup file
     * 
     * @param backupFile Path to the backup file to delete
     * @return true if deletion was successful, false otherwise
     */
    public static boolean deleteBackup(String backupFile) {
        File backup = new File(backupFile);
        return backup.delete();
    }

    /**
     * Get formatted display name for a backup file
     * Shows original filename and backup timestamp
     * 
     * @param backupFile Path to the backup file
     * @return Formatted display name
     */
    public static String getBackupDisplayName(String backupFile) {
        File file = new File(backupFile);
        String name = file.getName();

        // Extract timestamp from filename (format: name_yyyyMMdd_HHmmss.bak)
        if (name.contains("_")) {
            int lastUnderscore = name.lastIndexOf("_");
            int dotIndex = name.lastIndexOf(".");
            if (lastUnderscore > 0 && dotIndex > lastUnderscore) {
                String timestamp = name.substring(lastUnderscore + 1, dotIndex);
                String originalName = name.substring(0, lastUnderscore);

                // Format timestamp for display
                try {
                    Date date = new SimpleDateFormat(TIMESTAMP_FORMAT).parse(timestamp);
                    String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
                    return originalName + " - " + formattedDate;
                } catch (Exception e) {
                    return name;
                }
            }
        }

        return name;
    }

    /**
     * Create directory if it doesn't exist
     * 
     * @param dirPath Path to the directory
     */
    private static void createDirectoryIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Get the backup directory path
     * 
     * @return Path to backup directory
     */
    public static String getBackupDirectory() {
        return new File(BACKUP_DIR).getAbsolutePath();
    }

    /**
     * Get scenarios backup directory path
     * 
     * @return Path to scenarios backup directory
     */
    public static String getScenariosBackupDirectory() {
        return new File(SCENARIOS_BACKUP_DIR).getAbsolutePath();
    }

    /**
     * Get values backup directory path
     * 
     * @return Path to values backup directory
     */
    public static String getValuesBackupDirectory() {
        return new File(VALUES_BACKUP_DIR).getAbsolutePath();
    }
}
