package sipinspector.OperationTabs;

import java.io.IOException;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.BackupManager;
import sipinspector.Utils.IntegerValidator;

public class SettingsTab extends JPanel {
    private final SIPInspectorMainFrame mainFrame;
    private JCheckBox protectScenarioCheck;
    private JCheckBox protectValuesCheck;

    public SettingsTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
    }

    private void initComponents() {
        setLayout(new java.awt.BorderLayout());
        setName("Settings");
        JPanel innerPanel = new JPanel(new MigLayout("fillx, gapx 20, gapy 8", "[right]15[left, grow]", ""));

        // APPEARANCE
        innerPanel.add(new JLabel("<html><b>Appearance</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("Default Theme:"), "right");
        String[] themes = { "Classic Blue", "Ocean Blue", "Sunset Orange", "Forest Green", "Purple Dream" };
        final JComboBox<String> themeCombo = new JComboBox<>(themes);
        String savedTheme = GlobalSettings.getSetting("defaultTheme");
        if (savedTheme != null)
            themeCombo.setSelectedItem(savedTheme);
        else
            themeCombo.setSelectedItem("Ocean Blue");
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            GlobalSettings.setSetting("defaultTheme", selected);
            mainFrame.applyThemeByDisplayName(selected);
        });
        innerPanel.add(themeCombo, "left, wrap");
        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // NETWORK DEFAULTS
        innerPanel.add(new JLabel("<html><b>Network Defaults</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("Default Transport:"), "right");
        JPanel transportPanel = new JPanel(new MigLayout("ins 0", "[][][][]", ""));
        ButtonGroup transportGroup = new ButtonGroup();
        final JRadioButton udpRadio = new JRadioButton("UDP");
        final JRadioButton tcpRadio = new JRadioButton("TCP");
        final JRadioButton tlsRadio = new JRadioButton("TLS");
        transportGroup.add(udpRadio);
        transportGroup.add(tcpRadio);
        transportGroup.add(tlsRadio);
        String savedTransport = GlobalSettings.getSetting("defaultTransport");
        if ("TCP".equals(savedTransport))
            tcpRadio.setSelected(true);
        else if ("TLS".equals(savedTransport))
            tlsRadio.setSelected(true);
        else
            udpRadio.setSelected(true);
        udpRadio.addActionListener(e -> GlobalSettings.setSetting("defaultTransport", "UDP"));
        tcpRadio.addActionListener(e -> GlobalSettings.setSetting("defaultTransport", "TCP"));
        tlsRadio.addActionListener(e -> GlobalSettings.setSetting("defaultTransport", "TLS"));
        transportPanel.add(udpRadio);
        transportPanel.add(tcpRadio);
        transportPanel.add(tlsRadio);
        innerPanel.add(transportPanel, "left, wrap");

        // LOCAL PORTS
        innerPanel.add(new JLabel("<html><b>Local Port (Bind):</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("UDP/TCP Local Port:"), "right");
        final JTextField udpTcpLocalPortField = new JTextField(6);
        String savedUdpTcpLocalPort = GlobalSettings.getSetting("defaultUdpTcpLocalPort");
        udpTcpLocalPortField.setText(savedUdpTcpLocalPort != null ? savedUdpTcpLocalPort : "5060");
        udpTcpLocalPortField.setInputVerifier(new IntegerValidator());
        udpTcpLocalPortField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("defaultUdpTcpLocalPort", udpTcpLocalPortField.getText());
            }
        });
        innerPanel.add(udpTcpLocalPortField, "left, wrap");
        innerPanel.add(new JLabel("TLS Local Port:"), "right");
        final JTextField tlsLocalPortField = new JTextField(6);
        String savedTlsLocalPort = GlobalSettings.getSetting("defaultTlsLocalPort");
        tlsLocalPortField.setText(savedTlsLocalPort != null ? savedTlsLocalPort : "5061");
        tlsLocalPortField.setInputVerifier(new IntegerValidator());
        tlsLocalPortField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("defaultTlsLocalPort", tlsLocalPortField.getText());
            }
        });
        innerPanel.add(tlsLocalPortField, "left, wrap");
        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // REMOTE PORTS
        innerPanel.add(new JLabel("<html><b>Remote Port (Server):</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("UDP/TCP Remote Port:"), "right");
        final JTextField udpTcpRemotePortField = new JTextField(6);
        String savedUdpTcpRemotePort = GlobalSettings.getSetting("defaultUdpTcpRemotePort");
        udpTcpRemotePortField.setText(savedUdpTcpRemotePort != null ? savedUdpTcpRemotePort : "5060");
        udpTcpRemotePortField.setInputVerifier(new IntegerValidator());
        udpTcpRemotePortField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("defaultUdpTcpRemotePort", udpTcpRemotePortField.getText());
            }
        });
        innerPanel.add(udpTcpRemotePortField, "left, wrap");
        innerPanel.add(new JLabel("TLS Remote Port:"), "right");
        final JTextField tlsRemotePortField = new JTextField(6);
        String savedTlsRemotePort = GlobalSettings.getSetting("defaultTlsRemotePort");
        tlsRemotePortField.setText(savedTlsRemotePort != null ? savedTlsRemotePort : "5061");
        tlsRemotePortField.setInputVerifier(new IntegerValidator());
        tlsRemotePortField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("defaultTlsRemotePort", tlsRemotePortField.getText());
            }
        });
        innerPanel.add(tlsRemotePortField, "left, wrap");
        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // LOGGING
        innerPanel.add(new JLabel("<html><b>Logging</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("Enable logging:"), "right");
        final JCheckBox loggingCheckbox = new JCheckBox("Capture SIP + transport logs");
        loggingCheckbox.setSelected(Boolean.parseBoolean(GlobalSettings.getSetting("logsEnabled"))
                || GlobalSettings.logsEnabled);
        loggingCheckbox.addActionListener(e -> {
            boolean enabled = loggingCheckbox.isSelected();
            GlobalSettings.logsEnabled = enabled;
            GlobalSettings.setSetting("logsEnabled", Boolean.toString(enabled));
            GlobalSettings.saveSettings();
            if (enabled) {
                sipinspector.Utils.LogManager.startLogging(GlobalSettings.getTransport());
            } else {
                sipinspector.Utils.LogManager.stopLogging();
            }
        });
        innerPanel.add(loggingCheckbox, "left, wrap");
        innerPanel.add(new JLabel("Max Log Files (per transport):"), "right");
        final JTextField maxLogFilesField = new JTextField(6);
        String savedMaxLogFiles = GlobalSettings.getSetting("maxLogFiles");
        maxLogFilesField.setText(savedMaxLogFiles != null ? savedMaxLogFiles : "10");
        maxLogFilesField.setInputVerifier(new IntegerValidator());
        maxLogFilesField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("maxLogFiles", maxLogFilesField.getText());
            }
        });
        innerPanel.add(maxLogFilesField, "left, wrap");
        innerPanel.add(new JLabel("Log Retention (days):"), "right");
        final JTextField logRetentionField = new JTextField(6);
        String savedLogRetention = GlobalSettings.getSetting("logRetentionDays");
        logRetentionField.setText(savedLogRetention != null ? savedLogRetention : "7");
        logRetentionField.setInputVerifier(new IntegerValidator());
        logRetentionField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                GlobalSettings.setSetting("logRetentionDays", logRetentionField.getText());
            }
        });
        innerPanel.add(logRetentionField, "left, wrap");
        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // FILE MANAGEMENT
        innerPanel.add(new JLabel("<html><b>File Management</b></html>"), "span, wrap");

        // Scenario Protection
        innerPanel.add(new JLabel("Scenario Protection:"), "right");
        protectScenarioCheck = new JCheckBox("Protect scenario files from overwrite");
        // Use the persisted boolean flag, not the KeyWordsMap
        boolean scenarioProtected = GlobalSettings.isScenarioProtected;
        protectScenarioCheck.setSelected(scenarioProtected);
        protectScenarioCheck.addActionListener(e -> {
            GlobalSettings.isScenarioProtected = protectScenarioCheck.isSelected();
            GlobalSettings.setSetting("isScenarioProtected", protectScenarioCheck.isSelected() ? "true" : "false");
            GlobalSettings.saveSettings(); // Persist to disk
        });
        innerPanel.add(protectScenarioCheck, "left, wrap");

        // Values Protection
        innerPanel.add(new JLabel("Values Protection:"), "right");
        protectValuesCheck = new JCheckBox("Protect values files from overwrite");
        // Use the persisted boolean flag, not the KeyWordsMap
        boolean valuesProtected = GlobalSettings.isValuesProtected;
        protectValuesCheck.setSelected(valuesProtected);
        protectValuesCheck.addActionListener(e -> {
            GlobalSettings.isValuesProtected = protectValuesCheck.isSelected();
            GlobalSettings.setSetting("isValuesProtected", protectValuesCheck.isSelected() ? "true" : "false");
            GlobalSettings.saveSettings(); // Persist to disk
        });
        innerPanel.add(protectValuesCheck, "left, wrap");

        // Backup/Restore buttons
        innerPanel.add(new JLabel("Backup & Restore:"), "right");
        JPanel backupPanel = new JPanel(new MigLayout("ins 0", "[][]", ""));
        JButton backupButton = new JButton("Backup Files");
        backupButton.setToolTipText("Create backup of scenario and values files");
        backupButton.addActionListener(e -> onBackupFiles());
        backupPanel.add(backupButton);

        JButton restoreButton = new JButton("Restore Files");
        restoreButton.setToolTipText("Restore from backup");
        restoreButton.addActionListener(e -> onRestoreFiles());
        backupPanel.add(restoreButton);
        innerPanel.add(backupPanel, "left, wrap");
        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // DIAGNOSTICS
        innerPanel.add(new JLabel("<html><b>Diagnostics</b></html>"), "span, wrap");
        innerPanel.add(new JLabel("Open the logs folder:"), "right");
        JButton openLogsButton = new JButton("Open Logs Folder...");
        openLogsButton.addActionListener(e -> mainFrame.openLogsFolder());
        innerPanel.add(openLogsButton, "left, wrap");

        innerPanel.add(new JSeparator(SwingConstants.HORIZONTAL), "span, growx, wrap, gaptop 10, gapbottom 10");

        // SAVE BUTTON
        innerPanel.add(new JLabel(""), "right"); // Empty label for alignment
        JButton saveButton = new JButton("SAVE ALL SETTINGS");
        saveButton.setToolTipText("Save all settings to disk");
        saveButton.addActionListener(e -> {
            GlobalSettings.saveSettings();
            JOptionPane.showMessageDialog(this, "All settings saved successfully!", "Settings Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        innerPanel.add(saveButton, "left, wrap");

        JScrollPane scrollPane = new JScrollPane(innerPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, java.awt.BorderLayout.CENTER);
    }

    private void onBackupFiles() {
        try {
            StringBuilder message = new StringBuilder("Backup created:\n");

            // Backup scenarios folder
            java.io.File scenariosDir = new java.io.File("scenarios");
            if (scenariosDir.exists() && scenariosDir.isDirectory()) {
                String scenariosBackup = BackupManager.backupFile("scenarios", "scenario");
                message.append("- Scenarios folder: ").append(new java.io.File(scenariosBackup).getName()).append("\n");
            }

            // Backup values folder
            java.io.File valuesDir = new java.io.File("values");
            if (valuesDir.exists() && valuesDir.isDirectory()) {
                String valuesBackup = BackupManager.backupFile("values", "values");
                message.append("- Values folder: ").append(new java.io.File(valuesBackup).getName()).append("\n");
            }

            JOptionPane.showMessageDialog(this, message.toString(), "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to create backup: " + ex.getMessage(), "Backup Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRestoreFiles() {
        try {
            // Restore scenarios folder
            java.util.List<String> scenariosBackups = BackupManager.getBackups("scenarios", "scenario");
            if (!scenariosBackups.isEmpty()) {
                String[] displayNames = new String[scenariosBackups.size()];
                for (int i = 0; i < scenariosBackups.size(); i++) {
                    displayNames[i] = BackupManager.getBackupDisplayName(scenariosBackups.get(i));
                }

                String selected = (String) JOptionPane.showInputDialog(this, "Select scenarios backup to restore:",
                        "Restore Scenarios", JOptionPane.QUESTION_MESSAGE, null, displayNames, displayNames[0]);
                if (selected != null) {
                    for (int i = 0; i < displayNames.length; i++) {
                        if (displayNames[i].equals(selected)) {
                            int confirm = JOptionPane.showConfirmDialog(this,
                                    "Restore scenarios folder?\nThis will overwrite all current scenarios.", "Confirm",
                                    JOptionPane.YES_NO_OPTION);
                            if (confirm == JOptionPane.YES_OPTION) {
                                BackupManager.restoreFile(scenariosBackups.get(i), "scenarios");
                                JOptionPane.showMessageDialog(this, "Scenarios folder restored successfully!",
                                        "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
                            }
                            break;
                        }
                    }
                }
            }

            // Restore values folder
            java.util.List<String> valuesBackups = BackupManager.getBackups("values", "values");
            if (!valuesBackups.isEmpty()) {
                String[] displayNames = new String[valuesBackups.size()];
                for (int i = 0; i < valuesBackups.size(); i++) {
                    displayNames[i] = BackupManager.getBackupDisplayName(valuesBackups.get(i));
                }

                String selected = (String) JOptionPane.showInputDialog(this, "Select values backup to restore:",
                        "Restore Values", JOptionPane.QUESTION_MESSAGE, null, displayNames, displayNames[0]);
                if (selected != null) {
                    for (int i = 0; i < displayNames.length; i++) {
                        if (displayNames[i].equals(selected)) {
                            int confirm = JOptionPane.showConfirmDialog(this,
                                    "Restore values folder?\nThis will overwrite all current values.", "Confirm",
                                    JOptionPane.YES_NO_OPTION);
                            if (confirm == JOptionPane.YES_OPTION) {
                                BackupManager.restoreFile(valuesBackups.get(i), "values");
                                JOptionPane.showMessageDialog(this, "Values folder restored successfully!",
                                        "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to restore backup: " + ex.getMessage(), "Restore Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Refresh checkboxes after settings are loaded
     * Called by SIPInspectorMainFrame after loadSettings()
     */
    public void refreshSettingsCheckboxes() {
        if (protectScenarioCheck != null) {
            protectScenarioCheck.setSelected(GlobalSettings.isScenarioProtected);
        }

        if (protectValuesCheck != null) {
            protectValuesCheck.setSelected(GlobalSettings.isValuesProtected);
        }
    }
}
