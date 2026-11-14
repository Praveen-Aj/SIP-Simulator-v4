package sipinspector.OperationTabs;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;

/**
 * Campaigns Operation Tab for desktop SIP Inspector.
 * Replicates all features of the Web campaigns runner.
 * 
 * @author SIP Support Team
 */
public class CampaignsTab extends JPanel {

    private SIPInspectorMainFrame mainFrame;
    private List<CampaignItem> campaigns = new ArrayList<>();
    
    private JTable campaignsTable;
    private CampaignsTableModel tableModel;
    
    private JTextArea consoleArea;
    private JButton runBtn;
    private JButton stopBtn;
    private JButton addBtn;
    private JButton editBtn;
    private JButton removeBtn;

    private boolean campaignRunning = false;
    private volatile boolean abortCampaign = false;
    // volatile: started/nulled on the campaign worker thread, but read and
    // destroy()'d from the EDT (terminateCampaignRun). Without volatile the EDT
    // could observe a stale null and fail to kill a just-spawned SIP UA leg,
    // leaking the subprocess and its bound UDP ports past a "Terminate" click.
    private volatile Process activeCallerProcess = null;
    private volatile Process activeCalleeProcess = null;
    private volatile Process activeTargetProcess = null;
    private List<Thread> activeGobblers = new ArrayList<>();

    /**
     * Absolute path to the java executable of the currently running JVM.
     * This makes campaign subprocesses work even when 'java' is not on the
     * system PATH (e.g. this PC, or a packaged install using the bundled JRE).
     */
    private static String javaBin() {
        String home = System.getProperty("java.home");
        if (home != null && !home.isEmpty()) {
            File exe = new File(home, "bin" + File.separator + "java.exe");
            if (exe.exists()) {
                return exe.getAbsolutePath();
            }
            File exeNix = new File(home, "bin" + File.separator + "java");
            if (exeNix.exists()) {
                return exeNix.getAbsolutePath();
            }
        }
        return "java";
    }

    /**
     * Classpath for campaign-spawned subprocesses. NOT simply
     * System.getProperty("java.class.path") - when the app itself is launched
     * with "java -jar SIP_Simulator_v4.jar", that property is JUST the jar's
     * own path (the JVM ignores -cp/manifest Class-Path resolution for -jar
     * launches at the property-reporting level), so blindly forwarding it to
     * a child started with an explicit "-cp" flag silently drops the
     * lib/*.jar dependencies (MigLayout, jpcap, flatlaf) the manifest would
     * normally pull in - the likely cause of the intermittent
     * NoClassDefFoundError seen on campaign-spawned legs. Instead, derive an
     * absolute, self-contained classpath from where this class actually
     * loaded from, so it's correct regardless of how the main app was
     * launched (packaged jar, dev/IDE run, or installed EXE).
     */
    private static String runtimeClasspath() {
        try {
            File codeSource = new File(
                    CampaignsTab.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File appRoot = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            StringBuilder cp = new StringBuilder(codeSource.getAbsolutePath());
            File libDir = new File(appRoot, "lib");
            if (libDir.isDirectory()) {
                cp.append(File.pathSeparator).append(new File(libDir, "*").getAbsolutePath());
            }
            return cp.toString();
        } catch (Exception e) {
            String cp = System.getProperty("java.class.path");
            return (cp == null || cp.trim().isEmpty()) ? "build;lib/*" : cp;
        }
    }

    public static class CampaignItem {
        public String name = "";
        public String callerScenario = "";
        public String callerValues = "";
        public String calleeScenario = "";
        public String calleeValues = "";
        // Optional 3rd leg for attended-transfer style tests. Empty = 2-way test.
        public String targetScenario = "";
        public String targetValues = "";
        public String expectedStatus = "PASS"; // "PASS" or "FAIL"

        // Inline field values (semicolon-delimited row, e.g. "70200;70201;42002").
        // When non-empty these OVERRIDE the picked values file for that leg: the
        // runner writes a temp values file from this row. Empty = use the file
        // as-is. Lets the user decide exactly what to test without editing files.
        public String callerValuesInline = "";
        public String calleeValuesInline = "";
        public String targetValuesInline = "";

        // Per-test PBX (remote SIP target) override. Empty = use the global
        // Account/Settings value from userSettings.xml. Lets each test target a
        // specific PBX without changing global settings.
        public String pbxIp = "";
        public String pbxPort = "";

        // Runtime variables (not persisted in base campaigns.json save, or overwritten on reload)
        public String runStatus = "Idle"; // "Idle", "Pending", "Running", "Completed", "Aborted"
        public String runResult = ""; // "PASS", "FAIL"
        public String duration = ""; // seconds
    }

    public CampaignsTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadCampaigns();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // --- Main Split Pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        // --- Left Pane: Test Case List & Builder ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(51, 65, 85)));

        // Top Toolbar
        JPanel leftTopBar = new JPanel(new MigLayout("fillx, insets 5", "[grow][][]", "[]"));
        leftTopBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));
        JLabel leftTitle = new JLabel("Campaign Test Cases");
        leftTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        leftTopBar.add(leftTitle, "growx");

        addBtn = new JButton("+ Add Test Case");
        addBtn.addActionListener(e -> showAddTestCaseDialog());
        leftTopBar.add(addBtn, "");

        editBtn = new JButton("Edit");
        editBtn.setToolTipText("Edit the selected test case (values, PBX, scenarios)");
        editBtn.addActionListener(e -> editSelectedTestCase());
        leftTopBar.add(editBtn, "");

        removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> removeSelectedTestCase());
        leftTopBar.add(removeBtn, "");
        leftPanel.add(leftTopBar, BorderLayout.NORTH);

        // Campaigns Table
        tableModel = new CampaignsTableModel();
        campaignsTable = new JTable(tableModel);
        campaignsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Double-click a row to edit that test case.
        campaignsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedTestCase();
            }
        });
        JScrollPane tableScrollPane = new JScrollPane(campaignsTable);
        leftPanel.add(tableScrollPane, BorderLayout.CENTER);

        // Bottom Controls
        JPanel leftBottomBar = new JPanel(new MigLayout("fillx, insets 8", "[grow][grow]", "[]"));
        leftBottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(51, 65, 85)));

        runBtn = new JButton("▶ Execute Campaign Batch");
        runBtn.setBackground(new Color(6, 182, 212)); // cyan
        runBtn.setForeground(Color.BLACK);
        runBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        runBtn.addActionListener(e -> startCampaignRun());
        leftBottomBar.add(runBtn, "growx");

        stopBtn = new JButton("■ Terminate Campaign Batch");
        stopBtn.setBackground(new Color(239, 68, 68)); // red
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> terminateCampaignRun());
        leftBottomBar.add(stopBtn, "growx");

        leftPanel.add(leftBottomBar, BorderLayout.SOUTH);
        splitPane.setLeftComponent(leftPanel);

        // --- Right Pane: Console Output Log ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel rightTopBar = new JPanel(new MigLayout("fillx, insets 5", "[grow][][][]", "[]"));
        rightTopBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));
        JLabel rightTitle = new JLabel("Campaign Run Console");
        rightTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        rightTopBar.add(rightTitle, "growx");

        JButton exportBtn = new JButton("Export HTML Report");
        exportBtn.addActionListener(e -> exportReport());
        rightTopBar.add(exportBtn, "");

        JButton exportCsvBtn = new JButton("Export CSV");
        exportCsvBtn.setToolTipText("Export campaign results as CSV (Excel-friendly)");
        exportCsvBtn.addActionListener(e -> exportCsvReport());
        rightTopBar.add(exportCsvBtn, "");

        JButton clearBtn = new JButton("Clear Logs");
        clearBtn.addActionListener(e -> consoleArea.setText(""));
        rightTopBar.add(clearBtn, "");
        rightPanel.add(rightTopBar, BorderLayout.NORTH);

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBackground(new Color(5, 7, 10)); // terminal black
        consoleArea.setForeground(new Color(241, 243, 249)); // light gray text
        consoleArea.setCaretColor(Color.CYAN);
        JScrollPane consoleScrollPane = new JScrollPane(consoleArea);
        rightPanel.add(consoleScrollPane, BorderLayout.CENTER);

        splitPane.setRightComponent(rightPanel);

        add(splitPane, BorderLayout.CENTER);
    }

    private void loadCampaigns() {
        File file = new File("campaigns.json");
        if (!file.exists()) {
            this.campaigns = new ArrayList<>();
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String json = new String(bytes, "UTF-8");
            this.campaigns = JsonCampaignsHelper.parse(json);
            tableModel.fireTableDataChanged();
        } catch (Exception e) {
            e.printStackTrace();
            logToConsole("[SYSTEM] [ERROR] Failed to load campaigns.json: " + e.getMessage());
        }
    }

    private void saveCampaigns() {
        try {
            String json = JsonCampaignsHelper.serialize(this.campaigns);
            Files.write(new File("campaigns.json").toPath(), json.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
            logToConsole("[SYSTEM] [ERROR] Failed to save campaigns.json: " + e.getMessage());
        }
    }

    private void removeSelectedTestCase() {
        int selectedRow = campaignsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a test case to remove.", "Select Row", JOptionPane.WARNING_MESSAGE);
            return;
        }
        campaigns.remove(selectedRow);
        saveCampaigns();
        tableModel.fireTableDataChanged();
    }

    private void editSelectedTestCase() {
        int selectedRow = campaignsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= campaigns.size()) {
            JOptionPane.showMessageDialog(this, "Select a test case to edit.", "Select Row", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showTestCaseDialog(campaigns.get(selectedRow));
    }

    private void showAddTestCaseDialog() {
        showTestCaseDialog(null);
    }

    /**
     * Add (existing == null) or Edit a campaign test case. Beyond scenario/values
     * file pickers, this exposes: (a) an editable inline VALUES row per leg
     * (auto-filled from the picked file, but the user can override to decide
     * exactly what to test), and (b) a per-test PBX (remote) IP/port override
     * (blank = use the global Account/Settings PBX).
     */
    private void showTestCaseDialog(CampaignItem existing) {
        boolean editing = (existing != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                editing ? "Edit Test Case" : "Add Test Case", true);
        JPanel content = new JPanel(new MigLayout("fillx, insets 15", "[130!][grow]", ""));
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(content), BorderLayout.CENTER);
        dialog.setSize(560, 720);
        dialog.setLocationRelativeTo(this);

        JTextField nameField = new JTextField(editing ? existing.name : "Test " + (campaigns.size() + 1));

        List<String> availableScenarios = scanFiles("scenarios");
        List<String> availableValues = scanFiles("values");

        JComboBox<String> callerScenCombo = new JComboBox<>(availableScenarios.toArray(new String[0]));
        JComboBox<String> callerValCombo = new JComboBox<>(availableValues.toArray(new String[0]));
        JComboBox<String> calleeScenCombo = new JComboBox<>(availableScenarios.toArray(new String[0]));
        JComboBox<String> calleeValCombo = new JComboBox<>(availableValues.toArray(new String[0]));
        JComboBox<String> targetScenCombo = new JComboBox<>(availableScenarios.toArray(new String[0]));
        JComboBox<String> targetValCombo = new JComboBox<>(availableValues.toArray(new String[0]));

        // Editable inline values rows (field0;field1;field2...). Auto-filled from
        // the picked values file, overridable by the user.
        JTextField callerValInline = new JTextField();
        JTextField calleeValInline = new JTextField();
        JTextField targetValInline = new JTextField();
        callerValInline.setToolTipText("Semicolon-delimited values (e.g. 70200;70201;42002). Overrides the file above; leave matching the file to use it as-is.");
        calleeValInline.setToolTipText(callerValInline.getToolTipText());
        targetValInline.setToolTipText(callerValInline.getToolTipText());

        // Auto-match a values file when a scenario is picked.
        java.util.function.BiConsumer<JComboBox<String>, JComboBox<String>> autoMatch = (scen, val) -> {
            String selected = (String) scen.getSelectedItem();
            if (selected != null) {
                String match = selected.replace(".txt", "") + "_values.txt";
                for (int i = 0; i < val.getItemCount(); i++) {
                    if (val.getItemAt(i).equalsIgnoreCase(match)) { val.setSelectedIndex(i); break; }
                }
            }
        };
        callerScenCombo.addActionListener(e -> autoMatch.accept(callerScenCombo, callerValCombo));
        calleeScenCombo.addActionListener(e -> autoMatch.accept(calleeScenCombo, calleeValCombo));
        targetScenCombo.addActionListener(e -> autoMatch.accept(targetScenCombo, targetValCombo));
        // When a values file is picked, load its row into the editable inline field.
        callerValCombo.addActionListener(e -> callerValInline.setText(readValuesRow((String) callerValCombo.getSelectedItem())));
        calleeValCombo.addActionListener(e -> calleeValInline.setText(readValuesRow((String) calleeValCombo.getSelectedItem())));
        targetValCombo.addActionListener(e -> targetValInline.setText(readValuesRow((String) targetValCombo.getSelectedItem())));

        JRadioButton passRadio = new JRadioButton("PASS (Expected Ok)", true);
        JRadioButton failRadio = new JRadioButton("FAIL (Expected Rejection)");
        ButtonGroup bg = new ButtonGroup();
        bg.add(passRadio);
        bg.add(failRadio);

        JCheckBox transferCheck = new JCheckBox("3-way Transfer Test (adds a Target UA)");
        JLabel targetScenLabel = new JLabel("Target Scenario:");
        JLabel targetValLabel = new JLabel("Target Values:");
        JLabel targetValInlineLabel = new JLabel("Target Values (edit):");

        // PBX override fields.
        String globalPbxIp = sipinspector.GlobalSettings.getRemoteIPAddr();
        String globalPbxPort = sipinspector.GlobalSettings.getRemotePort();
        JTextField pbxIpField = new JTextField();
        JTextField pbxPortField = new JTextField();
        String pbxHint = "blank = use global (" + (globalPbxIp != null ? globalPbxIp : "?")
                + ":" + (globalPbxPort != null ? globalPbxPort : "5060") + ")";
        pbxIpField.setToolTipText("PBX / remote SIP IP for this test. " + pbxHint);
        pbxPortField.setToolTipText("PBX / remote SIP port for this test. " + pbxHint);

        // --- Layout ---
        content.add(new JLabel("Test Name:"));
        content.add(nameField, "growx, wrap");

        content.add(new JLabel("Caller Scenario:"));
        content.add(callerScenCombo, "growx, wrap");
        content.add(new JLabel("Caller Values file:"));
        content.add(callerValCombo, "growx, wrap");
        content.add(new JLabel("Caller Values (edit):"));
        content.add(callerValInline, "growx, wrap");

        content.add(new JLabel("Callee Scenario:"));
        content.add(calleeScenCombo, "growx, wrap");
        content.add(new JLabel("Callee Values file:"));
        content.add(calleeValCombo, "growx, wrap");
        content.add(new JLabel("Callee Values (edit):"));
        content.add(calleeValInline, "growx, wrap");

        content.add(transferCheck, "spanx 2, wrap");
        content.add(targetScenLabel);
        content.add(targetScenCombo, "growx, wrap");
        content.add(targetValLabel);
        content.add(targetValCombo, "growx, wrap");
        content.add(targetValInlineLabel);
        content.add(targetValInline, "growx, wrap");

        content.add(new JSeparator(), "spanx 2, growx, wrap");
        content.add(new JLabel("PBX IP (remote):"));
        content.add(pbxIpField, "growx, wrap");
        content.add(new JLabel("PBX Port (remote):"));
        content.add(pbxPortField, "growx, wrap");
        JLabel pbxHintLabel = new JLabel(pbxHint);
        pbxHintLabel.setFont(pbxHintLabel.getFont().deriveFont(Font.ITALIC, 10f));
        content.add(pbxHintLabel, "skip 1, wrap");

        content.add(new JLabel("Expected Outcome:"));
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        radioPanel.add(passRadio);
        radioPanel.add(failRadio);
        content.add(radioPanel, "growx, wrap");

        Runnable applyTransferEnabled = () -> {
            boolean on = transferCheck.isSelected();
            targetScenLabel.setEnabled(on); targetValLabel.setEnabled(on); targetValInlineLabel.setEnabled(on);
            targetScenCombo.setEnabled(on); targetValCombo.setEnabled(on); targetValInline.setEnabled(on);
        };
        transferCheck.addActionListener(e -> applyTransferEnabled.run());

        // --- Initialize selections ---
        if (callerScenCombo.getItemCount() > 0) callerScenCombo.setSelectedIndex(0);
        if (calleeScenCombo.getItemCount() > 0) calleeScenCombo.setSelectedIndex(0);
        if (targetScenCombo.getItemCount() > 0) targetScenCombo.setSelectedIndex(0);

        if (editing) {
            nameField.setText(existing.name);
            if (existing.callerScenario != null) callerScenCombo.setSelectedItem(existing.callerScenario);
            if (existing.callerValues != null) callerValCombo.setSelectedItem(existing.callerValues);
            if (existing.calleeScenario != null) calleeScenCombo.setSelectedItem(existing.calleeScenario);
            if (existing.calleeValues != null) calleeValCombo.setSelectedItem(existing.calleeValues);
            boolean hasTarget = existing.targetScenario != null && !existing.targetScenario.trim().isEmpty();
            transferCheck.setSelected(hasTarget);
            if (hasTarget) {
                targetScenCombo.setSelectedItem(existing.targetScenario);
                if (existing.targetValues != null) targetValCombo.setSelectedItem(existing.targetValues);
            }
            // Inline fields: explicit override if set, else the picked file's row
            // (the combo listeners already filled them from the file).
            if (existing.callerValuesInline != null && !existing.callerValuesInline.trim().isEmpty())
                callerValInline.setText(existing.callerValuesInline);
            if (existing.calleeValuesInline != null && !existing.calleeValuesInline.trim().isEmpty())
                calleeValInline.setText(existing.calleeValuesInline);
            if (existing.targetValuesInline != null && !existing.targetValuesInline.trim().isEmpty())
                targetValInline.setText(existing.targetValuesInline);
            pbxIpField.setText(existing.pbxIp != null ? existing.pbxIp : "");
            pbxPortField.setText(existing.pbxPort != null ? existing.pbxPort : "");
            if ("FAIL".equals(existing.expectedStatus)) failRadio.setSelected(true); else passRadio.setSelected(true);
        }
        applyTransferEnabled.run();

        JButton saveBtn = new JButton(editing ? "Save Changes" : "Add to Batch");
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Name is required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            CampaignItem item = editing ? existing : new CampaignItem();
            item.name = name;
            item.callerScenario = (String) callerScenCombo.getSelectedItem();
            item.callerValues = (String) callerValCombo.getSelectedItem();
            item.calleeScenario = (String) calleeScenCombo.getSelectedItem();
            item.calleeValues = (String) calleeValCombo.getSelectedItem();
            item.callerValuesInline = callerValInline.getText().trim();
            item.calleeValuesInline = calleeValInline.getText().trim();
            if (transferCheck.isSelected()) {
                item.targetScenario = (String) targetScenCombo.getSelectedItem();
                item.targetValues = (String) targetValCombo.getSelectedItem();
                item.targetValuesInline = targetValInline.getText().trim();
            } else {
                item.targetScenario = "";
                item.targetValues = "";
                item.targetValuesInline = "";
            }
            item.pbxIp = pbxIpField.getText().trim();
            item.pbxPort = pbxPortField.getText().trim();
            item.expectedStatus = passRadio.isSelected() ? "PASS" : "FAIL";
            if (!editing) {
                item.runStatus = "Idle";
                item.runResult = "";
                item.duration = "";
                campaigns.add(item);
            }
            saveCampaigns();
            tableModel.fireTableDataChanged();
            dialog.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private List<String> scanFiles(String subDir) {
        List<String> list = new ArrayList<>();
        File dir = new File(subDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    list.add(f.getName());
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    private void startCampaignRun() {
        if (campaigns.isEmpty() || campaignRunning) return;
        campaignRunning = true;
        abortCampaign = false;
        
        runBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        addBtn.setEnabled(false);
        removeBtn.setEnabled(false);

        // Reset statuses
        for (CampaignItem c : campaigns) {
            c.runStatus = "Pending";
            c.runResult = "";
            c.duration = "";
        }
        tableModel.fireTableDataChanged();
        consoleArea.setText("");

        logToConsole("[SYSTEM] Starting batch test campaign containing " + campaigns.size() + " test cases...");

        new Thread(this::runCampaignSequentially).start();
    }

    private void terminateCampaignRun() {
        abortCampaign = true;
        logToConsole("[SYSTEM] Terminating active campaign execution...");
        
        if (activeCallerProcess != null) {
            activeCallerProcess.destroy();
        }
        if (activeCalleeProcess != null) {
            activeCalleeProcess.destroy();
        }
        if (activeTargetProcess != null) {
            activeTargetProcess.destroy();
        }
        
        stopBtn.setEnabled(false);
    }

    private void runCampaignSequentially() {
        for (int i = 0; i < campaigns.size(); i++) {
            if (abortCampaign) {
                logToConsole("[SYSTEM] Campaign execution aborted by user.");
                break;
            }

            final int index = i;
            CampaignItem item = campaigns.get(i);
            
            SwingUtilities.invokeLater(() -> {
                item.runStatus = "Running";
                tableModel.fireTableRowsUpdated(index, index);
            });

            logToConsole("[SYSTEM] [TestCase " + (i + 1) + "/" + campaigns.size() + "] Executing: " + item.name + "...");
            
            long startTime = System.currentTimeMillis();
            boolean pass = executeSingleTestCase(item);
            long durationMs = System.currentTimeMillis() - startTime;
            double durationSecs = durationMs / 1000.0;

            SwingUtilities.invokeLater(() -> {
                item.runStatus = abortCampaign ? "Aborted" : "Completed";
                item.runResult = pass ? "PASS" : "FAIL";
                item.duration = String.format("%.1f", durationSecs);
                tableModel.fireTableRowsUpdated(index, index);
            });

            logToConsole("[SYSTEM] Finished: " + item.name + " -> " + (pass ? "PASS" : "FAIL") + " (" + String.format("%.1f", durationSecs) + "s)");

            if (abortCampaign) break;

            // Wait 2 seconds before next test case
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }

        // Roll up results for the end-of-run summary
        int passed = 0, failed = 0, skipped = 0;
        for (CampaignItem c : campaigns) {
            if ("PASS".equals(c.runResult)) passed++;
            else if ("FAIL".equals(c.runResult)) failed++;
            else skipped++;
        }
        final int fPassed = passed, fFailed = failed, fSkipped = skipped;

        SwingUtilities.invokeLater(() -> {
            campaignRunning = false;
            runBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            addBtn.setEnabled(true);
            removeBtn.setEnabled(true);
            logToConsole("[SYSTEM] Campaign execution batch finished.");
            logToConsole("[SYSTEM] ==========================================");
            logToConsole("[SYSTEM] RESULT SUMMARY: " + fPassed + " passed, " + fFailed + " failed"
                    + (fSkipped > 0 ? ", " + fSkipped + " not executed" : "")
                    + " (of " + campaigns.size() + " test cases)");
            logToConsole("[SYSTEM] Use 'Export HTML Report' or 'Export CSV' to save this run.");
            logToConsole("[SYSTEM] ==========================================");
        });
    }

    /**
     * Isolated local SIP ports for spawned campaign-runner UAs. Chosen away
     * from the app's own default (5060) so a campaign run never collides
     * with the main GUI's own socket, or with a previous test case's
     * still-shutting-down process.
     */
    private static final String TARGET_PORT = "15080";
    private static final String CALLEE_PORT = "15060";
    private static final String CALLER_PORT = "15070";

    /**
     * Read field N (0-based, semicolon-separated) from the first
     * non-comment, non-blank line of a values file. This is the
     * authoritative source for "who am I" / "who am I calling" - NOT
     * GlobalSettings or a numbering heuristic - so the spawned UA always
     * registers/dials as the identity the scenario author actually intended.
     */
    private String readValuesField(String valuesFileName, int fieldIndex, String fallback) {
        if (valuesFileName == null || valuesFileName.trim().isEmpty()) {
            return fallback;
        }
        File f = new File("values", valuesFileName);
        if (!f.exists()) {
            return fallback;
        }
        try {
            for (String line : Files.readAllLines(f.toPath())) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }
                String[] parts = trimmed.split(";");
                return fieldIndex < parts.length ? parts[fieldIndex].trim() : fallback;
            }
        } catch (IOException ignore) {
        }
        return fallback;
    }

    /**
     * Read the first non-comment data row of a values file as its raw
     * semicolon-delimited string (e.g. "70200;70201;42002"), or "" if none.
     * Used to auto-fill the editable inline-values field in the dialog.
     */
    private String readValuesRow(String valuesFileName) {
        if (valuesFileName == null || valuesFileName.trim().isEmpty()) {
            return "";
        }
        File f = new File("values", valuesFileName);
        if (!f.exists()) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(f.toPath())) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                    continue;
                }
                return trimmed;
            }
        } catch (IOException ignore) {
        }
        return "";
    }

    /** Extract a semicolon-delimited field from a values row, or fallback. */
    private String getRowField(String row, int index, String fallback) {
        if (row == null || row.trim().isEmpty()) {
            return fallback;
        }
        String[] parts = row.trim().split(";");
        return index < parts.length && !parts[index].trim().isEmpty() ? parts[index].trim() : fallback;
    }

    /**
     * Resolve the effective values ROW for a leg: the inline override if the
     * user set one, else the first data row of the picked file.
     */
    private String effectiveValuesRow(String inline, String fileName) {
        if (inline != null && !inline.trim().isEmpty()) {
            return inline.trim();
        }
        return readValuesRow(fileName);
    }

    /**
     * If an inline values row is set for a leg, write it to a temp values file
     * and return that path (relative, e.g. "values/temp_campaign_caller_1.txt");
     * otherwise return the picked file path ("values/<file>"). Temp files are
     * tracked in {@code tempValuesFiles} for cleanup.
     */
    private String resolveValuesPath(String inline, String fileName, String leg, List<File> tempFiles) {
        if (inline != null && !inline.trim().isEmpty()) {
            try {
                File tmp = new File("values", "temp_campaign_" + leg + ".txt");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(tmp))) {
                    pw.println("// Auto-generated by Campaigns (inline values) - safe to delete");
                    pw.println(inline.trim());
                }
                tempFiles.add(tmp);
                return "values/" + tmp.getName();
            } catch (IOException e) {
                logToConsole("[SYSTEM] [ERROR] Could not write temp values for " + leg + ": " + e.getMessage());
            }
        }
        return "values/" + fileName;
    }

    /**
     * Build a CliSipLegTest process command, injecting the per-test PBX
     * override (-DremoteIP/-DremotePort) when the test case specifies one.
     */
    private ProcessBuilder buildLeg(CampaignItem item, String scenarioFile, String valuesPath,
                                    String logId, String mode, String port, String user, String calleeUser) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-DscenarioPath=scenarios/" + scenarioFile);
        cmd.add("-DvaluesPath=" + valuesPath);
        cmd.add("-Dlogs.unique.id=" + logId);
        if (item.pbxIp != null && !item.pbxIp.trim().isEmpty()) {
            cmd.add("-DremoteIP=" + item.pbxIp.trim());
        }
        if (item.pbxPort != null && !item.pbxPort.trim().isEmpty()) {
            cmd.add("-DremotePort=" + item.pbxPort.trim());
        }
        cmd.add("-cp");
        cmd.add(runtimeClasspath());
        cmd.add("sipinspector.Media.CliSipLegTest");
        cmd.add(mode);
        cmd.add(port);
        cmd.add(user);
        if (calleeUser != null) {
            cmd.add(calleeUser);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File("."));
        return pb;
    }

    private boolean executeSingleTestCase(CampaignItem item) {
        List<String> logsBuffer = Collections.synchronizedList(new ArrayList<>());
        // Registration-only test: caller scenario name contains "reg" (e.g.
        // Reg_client.txt). Null-guarded because a freshly-added in-memory item can
        // hold a null scenario before a save/reload normalizes it to "".
        boolean isRegister = item.callerScenario != null
                && item.callerScenario.toLowerCase().contains("reg")
                && (item.calleeScenario == null || item.calleeScenario.trim().isEmpty()
                    || item.calleeScenario.toLowerCase().contains("reg"));
        boolean isTransfer = item.targetScenario != null && !item.targetScenario.trim().isEmpty();

        // Temp values files written from inline overrides; deleted in finally.
        List<File> tempValuesFiles = new ArrayList<>();
        // Effective values PATHS (inline override -> temp file, else picked file).
        String callerValuesPath = resolveValuesPath(item.callerValuesInline, item.callerValues, "caller", tempValuesFiles);
        String calleeValuesPath = resolveValuesPath(item.calleeValuesInline, item.calleeValues, "callee", tempValuesFiles);
        // Only resolve target path if this is a transfer (3-way) test; avoid unused temp file for 2-way tests.
        String targetValuesPath = isTransfer
            ? resolveValuesPath(item.targetValuesInline, item.targetValues, "target", tempValuesFiles)
            : "";
        // Dialed identities come from the EFFECTIVE row (inline if set, else file).
        String callerUser = getRowField(effectiveValuesRow(item.callerValuesInline, item.callerValues), 0, "70200");
        String calleeUser = getRowField(effectiveValuesRow(item.calleeValuesInline, item.calleeValues), 0, "70201");
        String targetUser = getRowField(effectiveValuesRow(item.targetValuesInline, item.targetValues), 0, "70202");
        if (item.pbxIp != null && !item.pbxIp.trim().isEmpty()) {
            logToConsole("[SYSTEM] Test '" + item.name + "' PBX override: "
                    + item.pbxIp.trim() + ":" + (item.pbxPort != null && !item.pbxPort.trim().isEmpty() ? item.pbxPort.trim() : "5060"));
        }

        try {
            if (isRegister) {
                // Registration CLI test - now honors the test case's CHOSEN caller
                // scenario + values (via -DscenarioPath/-DvaluesPath) and the PBX
                // override, instead of a hardcoded scenario + username-synthesized
                // values.
                List<String> regCmd = new ArrayList<>();
                regCmd.add(javaBin());
                if (item.callerScenario != null && !item.callerScenario.trim().isEmpty()) {
                    regCmd.add("-DscenarioPath=scenarios/" + item.callerScenario);
                }
                if (item.callerValues != null && !item.callerValues.trim().isEmpty()
                        || (item.callerValuesInline != null && !item.callerValuesInline.trim().isEmpty())) {
                    regCmd.add("-DvaluesPath=" + callerValuesPath);
                }
                if (item.pbxIp != null && !item.pbxIp.trim().isEmpty()) {
                    regCmd.add("-DremoteIP=" + item.pbxIp.trim());
                }
                if (item.pbxPort != null && !item.pbxPort.trim().isEmpty()) {
                    regCmd.add("-DremotePort=" + item.pbxPort.trim());
                }
                regCmd.add("-cp");
                regCmd.add(runtimeClasspath());
                regCmd.add("sipinspector.Media.CliSipRegisterTest");
                ProcessBuilder pb = new ProcessBuilder(regCmd);
                pb.directory(new File("."));

                activeCallerProcess = pb.start();

                Thread outGobbler = new Thread(new StreamGobbler(activeCallerProcess.getInputStream(), "[LOGS] ", logsBuffer, consoleArea));
                Thread errGobbler = new Thread(new StreamGobbler(activeCallerProcess.getErrorStream(), "[LOGS] [ERROR] ", logsBuffer, consoleArea));

                outGobbler.start();
                errGobbler.start();

                activeCallerProcess.waitFor();

                outGobbler.join(1000);
                errGobbler.join(1000);
            } else if (isTransfer) {
                // Three-way attended-transfer flow: Target, Callee (transferee) and
                // Caller (transferor). Identities and values rows are resolved above
                // (inline override -> temp file, else picked file); PBX override is
                // injected by buildLeg.
                logToConsole("[SYSTEM] Spawning Attended Transfer call instances (3-Legs)...");

                // 1. Launch Target
                logToConsole("[SYSTEM] Launching Target (" + targetUser + ") on port " + TARGET_PORT + "...");
                ProcessBuilder pbTarget = buildLeg(item, item.targetScenario, targetValuesPath, "target",
                        "callee", TARGET_PORT, targetUser, null);
                activeTargetProcess = pbTarget.start();

                Thread targetOut = new Thread(new StreamGobbler(activeTargetProcess.getInputStream(), "[LOGS] [TARGET] ", logsBuffer, consoleArea));
                Thread targetErr = new Thread(new StreamGobbler(activeTargetProcess.getErrorStream(), "[LOGS] [TARGET-ERROR] ", logsBuffer, consoleArea));
                targetOut.start();
                targetErr.start();

                // 2. Launch Callee (transferee)
                logToConsole("[SYSTEM] Launching Callee (" + calleeUser + ") on port " + CALLEE_PORT + "...");
                ProcessBuilder pbCallee = buildLeg(item, item.calleeScenario, calleeValuesPath, "callee",
                        "callee", CALLEE_PORT, calleeUser, null);
                activeCalleeProcess = pbCallee.start();

                Thread calleeOut = new Thread(new StreamGobbler(activeCalleeProcess.getInputStream(), "[LOGS] [CALLEE] ", logsBuffer, consoleArea));
                Thread calleeErr = new Thread(new StreamGobbler(activeCalleeProcess.getErrorStream(), "[LOGS] [CALLEE-ERROR] ", logsBuffer, consoleArea));
                calleeOut.start();
                calleeErr.start();

                // Wait 3 seconds
                Thread.sleep(3000);

                if (abortCampaign) {
                    activeCalleeProcess.destroy();
                    activeTargetProcess.destroy();
                    return false;
                }

                // 3. Launch Caller (transferor)
                logToConsole("[SYSTEM] Launching Caller (" + callerUser + ") on port " + CALLER_PORT + ", calling " + calleeUser + "...");
                ProcessBuilder pbCaller = buildLeg(item, item.callerScenario, callerValuesPath, "caller",
                        "caller", CALLER_PORT, callerUser, calleeUser);
                activeCallerProcess = pbCaller.start();

                Thread callerOut = new Thread(new StreamGobbler(activeCallerProcess.getInputStream(), "[LOGS] [CALLER] ", logsBuffer, consoleArea));
                Thread callerErr = new Thread(new StreamGobbler(activeCallerProcess.getErrorStream(), "[LOGS] [CALLER-ERROR] ", logsBuffer, consoleArea));
                callerOut.start();
                callerErr.start();

                // Wait for Alice to complete
                activeCallerProcess.waitFor();

                // Destroy Bob and Charlie
                activeCalleeProcess.destroy();
                activeTargetProcess.destroy();

                callerOut.join(1000);
                callerErr.join(1000);
                calleeOut.join(1000);
                calleeErr.join(1000);
                targetOut.join(1000);
                targetErr.join(1000);

            } else {
                // Two-way call test: caller and callee are each spawned as their
                // own background UA, each dialing using the identity (field0) from
                // its EFFECTIVE values (inline override or picked file, resolved
                // above), each honoring the per-test PBX override via buildLeg.
                logToConsole("[SYSTEM] Spawning two-way call instances...");
                logToConsole("[SYSTEM] Launching Callee (" + calleeUser + ") on port " + CALLEE_PORT + "...");

                ProcessBuilder pbCallee = buildLeg(item, item.calleeScenario, calleeValuesPath, "callee",
                        "callee", CALLEE_PORT, calleeUser, null);
                activeCalleeProcess = pbCallee.start();

                Thread calleeOut = new Thread(new StreamGobbler(activeCalleeProcess.getInputStream(), "[LOGS] [CALLEE] ", logsBuffer, consoleArea));
                Thread calleeErr = new Thread(new StreamGobbler(activeCalleeProcess.getErrorStream(), "[LOGS] [CALLEE-ERROR] ", logsBuffer, consoleArea));
                calleeOut.start();
                calleeErr.start();

                // Wait 3 seconds
                Thread.sleep(3000);

                if (abortCampaign) {
                    activeCalleeProcess.destroy();
                    return false;
                }

                logToConsole("[SYSTEM] Launching Caller (" + callerUser + ") on port " + CALLER_PORT + ", calling " + calleeUser + "...");

                ProcessBuilder pbCaller = buildLeg(item, item.callerScenario, callerValuesPath, "caller",
                        "caller", CALLER_PORT, callerUser, calleeUser);
                activeCallerProcess = pbCaller.start();

                Thread callerOut = new Thread(new StreamGobbler(activeCallerProcess.getInputStream(), "[LOGS] [CALLER] ", logsBuffer, consoleArea));
                Thread callerErr = new Thread(new StreamGobbler(activeCallerProcess.getErrorStream(), "[LOGS] [CALLER-ERROR] ", logsBuffer, consoleArea));
                callerOut.start();
                callerErr.start();

                // Wait for caller to complete
                activeCallerProcess.waitFor();

                // Once caller completes, terminate callee immediately
                activeCalleeProcess.destroy();

                callerOut.join(1000);
                callerErr.join(1000);
                calleeOut.join(1000);
                calleeErr.join(1000);
            }
        } catch (Exception e) {
            logToConsole("[SYSTEM] [ERROR] Subprocess launch exception: " + e.getMessage());
            return false;
        } finally {
            if (activeCallerProcess != null) {
                try { activeCallerProcess.destroyForcibly(); } catch (Exception ex) {}
                activeCallerProcess = null;
            }
            if (activeCalleeProcess != null) {
                try { activeCalleeProcess.destroyForcibly(); } catch (Exception ex) {}
                activeCalleeProcess = null;
            }
            if (activeTargetProcess != null) {
                try { activeTargetProcess.destroyForcibly(); } catch (Exception ex) {}
                activeTargetProcess = null;
            }
            // Remove any temp values files written from inline overrides.
            for (File tmp : tempValuesFiles) {
                if (!tmp.delete()) {
                    logToConsole("[SYSTEM] [WARN] Could not delete temp values file: " + tmp.getName());
                }
            }
        }

        if (abortCampaign) return false;

        // Result Analysis - snapshot the buffer once so indices below are stable.
        List<String> logLines;
        synchronized (logsBuffer) {
            logLines = new ArrayList<>(logsBuffer);
        }

        // Verdict is driven by explicit, machine-readable result markers that the
        // spawned legs print to STDOUT (see CliSipLegTest / CliSipRegisterTest):
        //   "CAMPAIGN_RESULT: PASS ..."  or  "CAMPAIGN_RESULT: FAIL ...".
        //
        // The previous heuristic scanned stdout for raw SIP text ("INVITE sip:",
        // "200 OK", "Registered successfully"), but SIP signaling is written to a
        // debug FILE (TLSDebugLogger), never to stdout - so those strings NEVER
        // appeared in the captured buffer and the verdict was decoupled from the
        // real call outcome (call tests always FAILed; FAIL-expected tests passed
        // for the wrong reason). The markers are emitted from authoritative state
        // (SDP media negotiated for calls; SIPStateTracker registration state for
        // REGISTER), one per leg, so a multi-leg test passes only if a PASS marker
        // is present and no leg emitted a FAIL.
        boolean sawPass = false;
        boolean sawFail = false;
        for (String line : logLines) {
            if (line.contains("CAMPAIGN_RESULT: PASS")) {
                sawPass = true;
            } else if (line.contains("CAMPAIGN_RESULT: FAIL")) {
                sawFail = true;
            }
        }
        // A hard subprocess launch/crash error still counts as a failure even if a
        // stale PASS somehow appeared.
        boolean hardError = false;
        for (String line : logLines) {
            if (line.contains("[SYSTEM] [ERROR] Subprocess launch exception")
                    || line.contains("Failed to open socket")) {
                hardError = true;
                break;
            }
        }

        boolean pass = sawPass && !sawFail && !hardError;

        String runOutcome = pass ? "PASS" : "FAIL";
        return runOutcome.equals(item.expectedStatus);
    }

    private void logToConsole(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(message + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    // --- Campaigns JTable Model ---
    private class CampaignsTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Name", "Type", "Caller Scenario", "Callee Scenario", "Expected", "Status", "Result", "Secs"};

        @Override
        public int getRowCount() { return campaigns.size(); }
        @Override
        public int getColumnCount() { return columns.length; }
        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CampaignItem item = campaigns.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return item.name;
                case 2: return (item.targetScenario != null && !item.targetScenario.trim().isEmpty())
                        ? "3-way (Transfer)" : "2-way";
                case 3: return item.callerScenario;
                case 4: return item.calleeScenario;
                case 5: return item.expectedStatus;
                case 6: return item.runStatus;
                case 7: return item.runResult;
                case 8: return item.duration;
                default: return "";
            }
        }
    }

    // --- Background stdout/stderr stream consumer ---
    private static class StreamGobbler implements Runnable {
        private final InputStream is;
        private final String prefix;
        private final List<String> buffer;
        private final JTextArea console;

        public StreamGobbler(InputStream is, String prefix, List<String> buffer, JTextArea console) {
            this.is = is;
            this.prefix = prefix;
            this.buffer = buffer;
            this.console = console;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String logLine = prefix + line;
                    buffer.add(line);
                    SwingUtilities.invokeLater(() -> {
                        console.append(logLine + "\n");
                        console.setCaretPosition(console.getDocument().getLength());
                    });
                }
            } catch (IOException e) {
                // Stream closed
            }
        }
    }

    // --- Tiny Self-Contained JSON parser/serializer ---
    private static class JsonCampaignsHelper {
        public static List<CampaignItem> parse(String json) {
            List<CampaignItem> items = new ArrayList<>();
            if (json == null || json.trim().isEmpty()) return items;
            
            int index = 0;
            while ((index = json.indexOf('{', index)) != -1) {
                int endIndex = json.indexOf('}', index);
                if (endIndex == -1) break;
                String block = json.substring(index + 1, endIndex);
                CampaignItem item = parseObject(block);
                items.add(item);
                index = endIndex + 1;
            }
            return items;
        }

        private static CampaignItem parseObject(String block) {
            CampaignItem item = new CampaignItem();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([^,\\s]+))");
            java.util.regex.Matcher matcher = pattern.matcher(block);
            while (matcher.find()) {
                String key = matcher.group(1);
                String val = matcher.group(2);
                if (val == null) {
                    val = matcher.group(3);
                    if (val != null) {
                        val = val.trim();
                        if (val.equalsIgnoreCase("null")) val = "";
                    } else {
                        val = "";
                    }
                }
                
                switch (key) {
                    case "name": item.name = val; break;
                    case "callerScenario": item.callerScenario = val; break;
                    case "callerValues": item.callerValues = val; break;
                    case "calleeScenario": item.calleeScenario = val; break;
                    case "calleeValues": item.calleeValues = val; break;
                    case "targetScenario": item.targetScenario = val; break;
                    case "targetValues": item.targetValues = val; break;
                    case "expectedStatus": item.expectedStatus = val; break;
                    case "callerValuesInline": item.callerValuesInline = val; break;
                    case "calleeValuesInline": item.calleeValuesInline = val; break;
                    case "targetValuesInline": item.targetValuesInline = val; break;
                    case "pbxIp": item.pbxIp = val; break;
                    case "pbxPort": item.pbxPort = val; break;
                }
            }
            return item;
        }

        public static String serialize(List<CampaignItem> items) {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < items.size(); i++) {
                CampaignItem item = items.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": \"").append(escape(item.name)).append("\",\n");
                sb.append("    \"callerScenario\": \"").append(escape(item.callerScenario)).append("\",\n");
                sb.append("    \"callerValues\": \"").append(escape(item.callerValues)).append("\",\n");
                sb.append("    \"calleeScenario\": \"").append(escape(item.calleeScenario)).append("\",\n");
                sb.append("    \"calleeValues\": \"").append(escape(item.calleeValues)).append("\",\n");
                sb.append("    \"targetScenario\": \"").append(escape(item.targetScenario)).append("\",\n");
                sb.append("    \"targetValues\": \"").append(escape(item.targetValues)).append("\",\n");
                sb.append("    \"expectedStatus\": \"").append(escape(item.expectedStatus)).append("\",\n");
                sb.append("    \"callerValuesInline\": \"").append(escape(item.callerValuesInline)).append("\",\n");
                sb.append("    \"calleeValuesInline\": \"").append(escape(item.calleeValuesInline)).append("\",\n");
                sb.append("    \"targetValuesInline\": \"").append(escape(item.targetValuesInline)).append("\",\n");
                sb.append("    \"pbxIp\": \"").append(escape(item.pbxIp)).append("\",\n");
                sb.append("    \"pbxPort\": \"").append(escape(item.pbxPort)).append("\"\n");
                sb.append("  }");
                if (i < items.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("]");
            return sb.toString();
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f");
        }
    }

    private void exportReport() {
        if (campaigns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No campaigns configured to export.", "Empty Campaign", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("campaign_report.html"));
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(dest)) {
                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n<html>\n<head>\n")
                    .append("    <title>SIP Simulator Campaign Report</title>\n")
                    .append("    <style>\n")
                    .append("        body { font-family: 'Segoe UI', sans-serif; background-color: #0b0f19; color: #f1f5f9; padding: 40px; margin: 0; }\n")
                    .append("        .container { max-width: 900px; margin: 0 auto; background: #111827; border: 1px solid #1f2937; border-radius: 12px; padding: 32px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3); }\n")
                    .append("        h1 { color: #00f0ff; margin-top: 0; border-bottom: 2px solid #1f2937; padding-bottom: 12px; font-size: 24px; }\n")
                    .append("        .meta { color: #9ca3af; font-size: 14px; margin-bottom: 24px; }\n")
                    .append("        .tc-card { background: #1f2937; border: 1px solid #374151; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n")
                    .append("        .tc-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #374151; padding-bottom: 8px; margin-bottom: 8px; }\n")
                    .append("        .tc-title { font-weight: bold; font-size: 16px; color: #fff; }\n")
                    .append("        .tc-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }\n")
                    .append("        .tc-badge.pass { background: rgba(74, 222, 128, 0.1); color: #4ade80; border: 1px solid #4ade80; }\n")
                    .append("        .tc-badge.fail { background: rgba(239, 68, 68, 0.1); color: #ef4444; border: 1px solid #ef4444; }\n")
                    .append("        .tc-badge.pending { background: rgba(156, 163, 175, 0.1); color: #9ca3af; border: 1px solid #9ca3af; }\n")
                    .append("        .tc-details { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 13px; color: #9ca3af; }\n")
                    .append("    </style>\n")
                    .append("</head>\n<body>\n")
                    .append("    <div class=\"container\">\n")
                    .append("        <h1>SIP Simulator E2E Campaign Report</h1>\n")
                    .append("        <div class=\"meta\">Generated on ").append(new Date().toString()).append(" | Total Test Cases: ").append(campaigns.size()).append("</div>\n");

                // Result rollup: passed / failed / not-executed + total duration
                int passed = 0, failed = 0, pending = 0;
                double totalDuration = 0;
                for (CampaignItem tc : campaigns) {
                    if ("PASS".equals(tc.runResult)) passed++;
                    else if ("FAIL".equals(tc.runResult)) failed++;
                    else pending++;
                    if (tc.duration != null && !tc.duration.isEmpty()) {
                        try { totalDuration += Double.parseDouble(tc.duration); } catch (NumberFormatException ignore) {}
                    }
                }
                String overallColor = failed > 0 ? "#ef4444" : (passed > 0 ? "#4ade80" : "#9ca3af");
                String overallText = failed > 0 ? "FAILURES DETECTED" : (passed > 0 ? "ALL EXECUTED TESTS PASSED" : "NOT EXECUTED");
                html.append("        <div class=\"tc-card\" style=\"border-color:").append(overallColor).append(";\">\n")
                    .append("            <div class=\"tc-title\" style=\"color:").append(overallColor).append("; margin-bottom:8px;\">Summary: ")
                    .append(overallText).append("</div>\n")
                    .append("            <div class=\"tc-details\">\n")
                    .append("                <div><strong>Passed:</strong> ").append(passed).append(" / ").append(campaigns.size()).append("</div>\n")
                    .append("                <div><strong>Failed:</strong> ").append(failed).append("</div>\n")
                    .append("                <div><strong>Not Executed:</strong> ").append(pending).append("</div>\n")
                    .append("                <div><strong>Total Duration:</strong> ").append(String.format("%.1f", totalDuration)).append("s</div>\n")
                    .append("            </div>\n")
                    .append("        </div>\n");

                for (int i = 0; i < campaigns.size(); i++) {
                    CampaignItem tc = campaigns.get(i);
                    String badgeClass = "pending";
                    if ("PASS".equals(tc.runResult)) badgeClass = "pass";
                    else if ("FAIL".equals(tc.runResult)) badgeClass = "fail";
                    
                    String resultText = tc.runResult;
                    if (resultText == null || resultText.isEmpty()) {
                        resultText = tc.runStatus;
                    }
                    if (resultText == null || resultText.isEmpty()) {
                        resultText = "Not Executed";
                    }
                    
                    html.append("        <div class=\"tc-card\">\n")
                        .append("            <div class=\"tc-header\">\n")
                        .append("                <span class=\"tc-title\">").append(i + 1).append(". ").append(htmlEscape(tc.name)).append("</span>\n")
                        .append("                <span class=\"tc-badge ").append(badgeClass).append("\">").append(htmlEscape(resultText)).append("</span>\n")
                        .append("            </div>\n")
                        .append("            <div class=\"tc-details\">\n")
                        .append("                <div><strong>Caller Scenario:</strong> ").append(htmlEscape(tc.callerScenario)).append("</div>\n")
                        .append("                <div><strong>Callee Scenario:</strong> ").append(htmlEscape(tc.calleeScenario)).append("</div>\n")
                        .append("                <div><strong>Caller Values:</strong> ").append(htmlEscape(tc.callerValues)).append("</div>\n")
                        .append("                <div><strong>Callee Values:</strong> ").append(htmlEscape(tc.calleeValues)).append("</div>\n")
                        .append("                <div><strong>Expected Outcome:</strong> ").append(htmlEscape(tc.expectedStatus)).append("</div>\n")
                        .append("                <div><strong>Duration:</strong> ").append(tc.duration != null && !tc.duration.isEmpty() ? tc.duration + "s" : "-").append("</div>\n")
                        .append("            </div>\n")
                        .append("        </div>\n");
                }
                
                html.append("    </div>\n</body>\n</html>\n");
                fw.write(html.toString());
                JOptionPane.showMessageDialog(this, "Campaign report exported successfully to:\n" + dest.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting report: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportCsvReport() {
        if (campaigns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No campaigns configured to export.", "Empty Campaign", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("campaign_report.csv"));
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = chooser.getSelectedFile();
        if (!dest.getName().toLowerCase().endsWith(".csv")) {
            dest = new File(dest.getAbsolutePath() + ".csv");
        }
        try (FileWriter fw = new FileWriter(dest)) {
            fw.write("SIP Simulator Campaign Report\n");
            fw.write("Generated," + csvEscape(new Date().toString()) + "\n");

            int passed = 0, failed = 0, pending = 0;
            for (CampaignItem tc : campaigns) {
                if ("PASS".equals(tc.runResult)) passed++;
                else if ("FAIL".equals(tc.runResult)) failed++;
                else pending++;
            }
            fw.write("Total," + campaigns.size() + ",Passed," + passed + ",Failed," + failed + ",Not Executed," + pending + "\n");
            fw.write("\n");
            fw.write("#,Name,Result,Status,Duration (s),Caller Scenario,Caller Values,Callee Scenario,Callee Values,Expected Outcome\n");
            for (int i = 0; i < campaigns.size(); i++) {
                CampaignItem tc = campaigns.get(i);
                String result = (tc.runResult == null || tc.runResult.isEmpty()) ? "NOT EXECUTED" : tc.runResult;
                fw.write((i + 1) + ","
                        + csvEscape(tc.name) + ","
                        + csvEscape(result) + ","
                        + csvEscape(tc.runStatus) + ","
                        + csvEscape(tc.duration) + ","
                        + csvEscape(tc.callerScenario) + ","
                        + csvEscape(tc.callerValues) + ","
                        + csvEscape(tc.calleeScenario) + ","
                        + csvEscape(tc.calleeValues) + ","
                        + csvEscape(tc.expectedStatus) + "\n");
            }
            JOptionPane.showMessageDialog(this, "Campaign CSV exported successfully to:\n" + dest.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Escape text for safe inclusion in the HTML report. Without this a test
     * name or filename containing &amp;, &lt;, or &gt; corrupts the report layout
     * (or injects markup). The CSV/JSON paths already escape; HTML did not.
     */
    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
