package sipinspector.OperationTabs;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.EmojiIcon;

/**
 * Campaigns Operation Tab for desktop SIP Inspector.
 * High-intelligence campaign runner with automated failure classification,
 * stage detection, evidence-based triage, and multi-level execution console.
 *
 * Implements Workstream A of Phase 8.
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class CampaignsTab extends JPanel {

    private SIPInspectorMainFrame mainFrame;
    private List<CampaignItem> campaigns = new ArrayList<>();
    private List<CampaignItem> filteredCampaigns = new ArrayList<>();

    private JTable campaignsTable;
    private CampaignsTableModel tableModel;
    private JComboBox<String> filterCombo;

    // KPI Summary Header Labels
    private JLabel lblTotalCount;
    private JLabel lblPassCount;
    private JLabel lblFailCount;
    private JLabel lblBlockCount;
    private JLabel lblPendingCount;
    private JLabel lblRunningCount;
    private JLabel lblTotalDuration;
    private JLabel lblOverallResult;

    // Failure Analysis Panel Components
    private JPanel failureAnalysisCard;
    private JLabel lblFaTitle;
    private JLabel lblFaStage;
    private JLabel lblFaCertainty;
    private JLabel lblFaExpected;
    private JLabel lblFaActual;
    private JLabel lblFaMethodCode;
    private JLabel lblFaCallId;
    private JLabel lblFaCSeq;
    private JLabel lblFaTimestamp;
    private JLabel lblFaClassification;
    private JTextArea txtFaReason;
    private JTextArea txtFaEvidence;
    private JTextArea txtFaSuggestedSteps;

    // Technical Logs Components
    private JTextArea consoleArea;
    private JComboBox<String> logLevelCombo;
    private JTextField logSearchField;
    private JCheckBox autoScrollCheck;
    private final List<String> allConsoleLogs = Collections.synchronizedList(new ArrayList<>());

    private JButton runBtn;
    private JButton stopBtn;
    private JButton addBtn;
    private JButton editBtn;
    private JButton removeBtn;

    private boolean campaignRunning = false;
    private volatile boolean abortCampaign = false;
    private volatile Process activeCallerProcess = null;
    private volatile Process activeCalleeProcess = null;
    private volatile Process activeTargetProcess = null;

    private static String javaBin() {
        String home = System.getProperty("java.home");
        if (home != null && !home.isEmpty()) {
            File exe = new File(home, "bin" + File.separator + "java.exe");
            if (exe.exists()) return exe.getAbsolutePath();
            File exeNix = new File(home, "bin" + File.separator + "java");
            if (exeNix.exists()) return exeNix.getAbsolutePath();
        }
        return "java";
    }

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
        public String targetScenario = "";
        public String targetValues = "";
        public String expectedStatus = "PASS"; // "PASS" or "FAIL"

        public String callerValuesInline = "";
        public String calleeValuesInline = "";
        public String targetValuesInline = "";

        public String pbxIp = "";
        public String pbxPort = "";

        // Runtime Execution State
        public String runStatus = "Idle"; // "Idle", "Pending", "Running", "Completed", "Aborted", "Blocked"
        public String runResult = ""; // "PASS", "FAIL", "BLOCKED"
        public String duration = ""; // seconds

        // Failure Intelligence (Workstream A)
        public String failureStage = "";
        public String expectedCondition = "";
        public String actualCondition = "";
        public String failureMethod = "";
        public int failureResponseCode = 0;
        public String failureCallId = "";
        public String failureCSeq = "";
        public String failureTimestamp = "";
        public String failureClassification = "";
        public String causeCertainty = ""; // "Confirmed cause", "Probable cause", "Unknown cause"
        public String failureReason = "";
        public String supportingMessages = "";
        public String suggestedInvestigation = "";
    }

    public CampaignsTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadCampaigns();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(new Color(15, 23, 42)); // Slate 900

        // --- Main Split Pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(520);
        splitPane.setResizeWeight(0.45);
        splitPane.setContinuousLayout(true);

        // ====================================================================
        // LEFT PANE: Test Case Table & Execution Controls
        // ====================================================================
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(15, 23, 42));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(51, 65, 85)));

        // Top Toolbar
        JPanel leftTopBar = new JPanel(new MigLayout("fillx, insets 6", "[][grow][][][][]", "[]"));
        leftTopBar.setBackground(new Color(30, 41, 59));
        leftTopBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        JLabel leftTitle = new JLabel("Campaign Test Cases");
        leftTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        leftTitle.setForeground(Color.WHITE);
        leftTopBar.add(leftTitle, "");

        filterCombo = new JComboBox<>(new String[]{"All Tests", "PASS Only", "FAIL Only", "BLOCKED Only", "Pending Only"});
        filterCombo.addActionListener(e -> applyFilter());
        leftTopBar.add(filterCombo, "");

        addBtn = new JButton("+ Add");
        addBtn.setToolTipText("Add new test case");
        addBtn.addActionListener(e -> showAddTestCaseDialog());
        leftTopBar.add(addBtn, "");

        editBtn = new JButton("Edit");
        editBtn.setToolTipText("Edit selected test case");
        editBtn.addActionListener(e -> editSelectedTestCase());
        leftTopBar.add(editBtn, "");

        removeBtn = new JButton("Remove");
        removeBtn.setToolTipText("Remove selected test case");
        removeBtn.addActionListener(e -> removeSelectedTestCase());
        leftTopBar.add(removeBtn, "");

        leftPanel.add(leftTopBar, BorderLayout.NORTH);

        // Campaigns Table
        tableModel = new CampaignsTableModel();
        campaignsTable = new JTable(tableModel);
        campaignsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        campaignsTable.setRowHeight(26);
        campaignsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        campaignsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        campaignsTable.setDefaultRenderer(Object.class, new CampaignsCellRenderer());

        int[] widths = {40, 180, 70, 70, 80, 60};
        for (int i = 0; i < widths.length && i < campaignsTable.getColumnCount(); i++) {
            campaignsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        campaignsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = campaignsTable.getSelectedRow();
                    if (row >= 0 && row < filteredCampaigns.size()) {
                        displayFailureAnalysis(filteredCampaigns.get(row));
                    }
                }
            }
        });

        campaignsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedTestCase();
            }
        });

        JScrollPane tableScroll = new JScrollPane(campaignsTable);
        tableScroll.setBorder(null);
        leftPanel.add(tableScroll, BorderLayout.CENTER);

        // Bottom Controls & Execution
        JPanel leftBottomBar = new JPanel(new MigLayout("fillx, insets 8", "[grow][grow]", "[][]"));
        leftBottomBar.setBackground(new Color(30, 41, 59));
        leftBottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(51, 65, 85)));

        lblOverallResult = new JLabel("OVERALL RESULT: NOT EXECUTED");
        lblOverallResult.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblOverallResult.setForeground(new Color(148, 163, 184));
        leftBottomBar.add(lblOverallResult, "span 2, growx, wrap");

        runBtn = new JButton("▶ Execute Campaign Batch");
        runBtn.setBackground(new Color(6, 182, 212)); // cyan
        runBtn.setForeground(Color.BLACK);
        runBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        runBtn.addActionListener(e -> startCampaignRun());
        leftBottomBar.add(runBtn, "growx");

        stopBtn = new JButton("■ Terminate Campaign Batch");
        stopBtn.setBackground(new Color(239, 68, 68)); // red
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> terminateCampaignRun());
        leftBottomBar.add(stopBtn, "growx");

        leftPanel.add(leftBottomBar, BorderLayout.SOUTH);
        splitPane.setLeftComponent(leftPanel);

        // ====================================================================
        // RIGHT PANE: Summary + Failure Analysis + Multi-Level Logs
        // ====================================================================
        JTabbedPane rightTabbedPane = new JTabbedPane();

        // 1. Campaign Summary & Failure Intelligence View
        rightTabbedPane.addTab("Summary & Failure Intelligence", createSummaryAndIntelligencePanel());

        // 2. Technical Execution Logs
        rightTabbedPane.addTab("Execution Console Logs", createExecutionConsolePanel());

        splitPane.setRightComponent(rightTabbedPane);
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createSummaryAndIntelligencePanel() {
        JPanel container = new JPanel(new BorderLayout(5, 5));
        container.setBackground(new Color(15, 23, 42));
        container.setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- KPI Tiles Banner ---
        JPanel kpiGrid = new JPanel(new GridLayout(1, 7, 8, 0));
        kpiGrid.setBackground(new Color(15, 23, 42));
        kpiGrid.setBorder(new EmptyBorder(0, 0, 8, 0));

        kpiGrid.add(createKpiCard("TOTAL TESTS", lblTotalCount = new JLabel("0", SwingConstants.CENTER), new Color(148, 163, 184)));
        kpiGrid.add(createKpiCard("PASSED", lblPassCount = new JLabel("0", SwingConstants.CENTER), new Color(74, 222, 128)));
        kpiGrid.add(createKpiCard("FAILED", lblFailCount = new JLabel("0", SwingConstants.CENTER), new Color(239, 68, 68)));
        kpiGrid.add(createKpiCard("BLOCKED", lblBlockCount = new JLabel("0", SwingConstants.CENTER), new Color(251, 191, 36)));
        kpiGrid.add(createKpiCard("PENDING", lblPendingCount = new JLabel("0", SwingConstants.CENTER), new Color(148, 163, 184)));
        kpiGrid.add(createKpiCard("RUNNING", lblRunningCount = new JLabel("0", SwingConstants.CENTER), new Color(56, 189, 248)));
        kpiGrid.add(createKpiCard("DURATION", lblTotalDuration = new JLabel("0.0s", SwingConstants.CENTER), new Color(192, 132, 252)));

        container.add(kpiGrid, BorderLayout.NORTH);

        // --- Failure Analysis & Diagnostic Card ---
        failureAnalysisCard = new JPanel(new BorderLayout(6, 6));
        failureAnalysisCard.setBackground(new Color(24, 33, 53));
        failureAnalysisCard.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "Failure Analysis & Diagnostic Triage",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(56, 189, 248)
        ));

        // Header of Analysis
        JPanel faHeader = new JPanel(new MigLayout("fillx, insets 4", "[][grow][][]", "[]"));
        faHeader.setBackground(new Color(30, 41, 59));

        lblFaTitle = new JLabel("Select a test case to view failure analysis");
        lblFaTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblFaTitle.setForeground(Color.WHITE);
        faHeader.add(lblFaTitle, "growx");

        lblFaStage = new JLabel("Stage: -");
        lblFaStage.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFaStage.setForeground(new Color(56, 189, 248));
        faHeader.add(lblFaStage, "");

        lblFaCertainty = new JLabel("-");
        lblFaCertainty.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFaCertainty.setForeground(new Color(251, 191, 36));
        faHeader.add(lblFaCertainty, "");

        failureAnalysisCard.add(faHeader, BorderLayout.NORTH);

        // Center: Details Grid + Evidence + Suggested Steps
        JPanel faContent = new JPanel(new MigLayout("fill, insets 8", "[130!][grow][130!][grow]", "[][][][grow][grow]"));
        faContent.setBackground(new Color(24, 33, 53));

        faContent.add(new JLabel("Expected Condition:"));
        lblFaExpected = new JLabel("-");
        lblFaExpected.setForeground(new Color(74, 222, 128));
        faContent.add(lblFaExpected, "");

        faContent.add(new JLabel("Actual Condition:"));
        lblFaActual = new JLabel("-");
        lblFaActual.setForeground(new Color(239, 68, 68));
        faContent.add(lblFaActual, "wrap");

        faContent.add(new JLabel("SIP Method / Code:"));
        lblFaMethodCode = new JLabel("-");
        lblFaMethodCode.setForeground(Color.WHITE);
        faContent.add(lblFaMethodCode, "");

        faContent.add(new JLabel("Call-ID / CSeq:"));
        lblFaCallId = new JLabel("-");
        lblFaCallId.setForeground(new Color(226, 232, 240));
        faContent.add(lblFaCallId, "wrap");

        faContent.add(new JLabel("Classification:"));
        lblFaClassification = new JLabel("-");
        lblFaClassification.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFaClassification.setForeground(new Color(244, 63, 94));
        faContent.add(lblFaClassification, "span 3, wrap");

        // Failure Reason
        faContent.add(new JLabel("Failure Reason:"), "top");
        txtFaReason = new JTextArea(2, 40);
        txtFaReason.setEditable(false);
        txtFaReason.setLineWrap(true);
        txtFaReason.setWrapStyleWord(true);
        txtFaReason.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtFaReason.setBackground(new Color(15, 23, 42));
        txtFaReason.setForeground(new Color(241, 245, 249));
        faContent.add(new JScrollPane(txtFaReason), "span 3, growx, wrap");

        // Supporting SIP Messages & Traces
        faContent.add(new JLabel("Supporting Evidence:"), "top");
        txtFaEvidence = new JTextArea();
        txtFaEvidence.setEditable(false);
        txtFaEvidence.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtFaEvidence.setBackground(new Color(10, 15, 30));
        txtFaEvidence.setForeground(new Color(226, 232, 240));
        faContent.add(new JScrollPane(txtFaEvidence), "span 3, grow, wrap");

        // Suggested Investigation Steps
        faContent.add(new JLabel("Suggested Steps:"), "top");
        txtFaSuggestedSteps = new JTextArea(3, 40);
        txtFaSuggestedSteps.setEditable(false);
        txtFaSuggestedSteps.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtFaSuggestedSteps.setBackground(new Color(15, 23, 42));
        txtFaSuggestedSteps.setForeground(new Color(56, 189, 248));
        faContent.add(new JScrollPane(txtFaSuggestedSteps), "span 3, growx");

        failureAnalysisCard.add(faContent, BorderLayout.CENTER);
        container.add(failureAnalysisCard, BorderLayout.CENTER);

        return container;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBackground(new Color(30, 41, 59));
        card.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(new Color(148, 163, 184));
        card.add(lblTitle, BorderLayout.NORTH);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createExecutionConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(15, 23, 42));

        // Console Toolbar
        JPanel topBar = new JPanel(new MigLayout("fillx, insets 5", "[][][grow][][][][]", "[]"));
        topBar.setBackground(new Color(30, 41, 59));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        topBar.add(new JLabel("Log Level:"), "");
        logLevelCombo = new JComboBox<>(new String[]{"ALL", "ERROR", "WARN", "INFO", "DEBUG"});
        logLevelCombo.addActionListener(e -> filterConsoleLogs());
        topBar.add(logLevelCombo, "");

        logSearchField = new JTextField();
        logSearchField.putClientProperty("JTextField.placeholderText", "Search console logs...");
        logSearchField.addActionListener(e -> filterConsoleLogs());
        topBar.add(logSearchField, "growx");

        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        autoScrollCheck.setOpaque(false);
        autoScrollCheck.setForeground(new Color(226, 232, 240));
        topBar.add(autoScrollCheck, "");

        JButton copyBtn = new JButton("Copy All");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(consoleArea.getText()), null);
            JOptionPane.showMessageDialog(this, "Console logs copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });
        topBar.add(copyBtn, "");

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            allConsoleLogs.clear();
            consoleArea.setText("");
        });
        topBar.add(clearBtn, "");

        JButton exportHtmlBtn = new JButton("Export HTML");
        exportHtmlBtn.addActionListener(e -> exportReport());
        topBar.add(exportHtmlBtn, "");

        JButton exportCsvBtn = new JButton("Export CSV");
        exportCsvBtn.addActionListener(e -> exportCsvReport());
        topBar.add(exportCsvBtn, "");

        panel.add(topBar, BorderLayout.NORTH);

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBackground(new Color(5, 7, 10));
        consoleArea.setForeground(new Color(241, 243, 249));
        consoleArea.setCaretColor(Color.CYAN);

        JScrollPane scroll = new JScrollPane(consoleArea);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void displayFailureAnalysis(CampaignItem item) {
        if (item == null) return;
        lblFaTitle.setText(item.name + " (" + (item.runResult.isEmpty() ? item.runStatus : item.runResult) + ")");
        lblFaStage.setText("Stage: " + (item.failureStage.isEmpty() ? (item.runResult.equals("PASS") ? "Completed" : "N/A") : item.failureStage));
        lblFaCertainty.setText(item.causeCertainty.isEmpty() ? "-" : item.causeCertainty);
        if ("Confirmed cause".equalsIgnoreCase(item.causeCertainty)) {
            lblFaCertainty.setForeground(new Color(239, 68, 68)); // red
        } else if ("Probable cause".equalsIgnoreCase(item.causeCertainty)) {
            lblFaCertainty.setForeground(new Color(251, 191, 36)); // amber
        } else {
            lblFaCertainty.setForeground(new Color(148, 163, 184)); // slate
        }

        lblFaExpected.setText(item.expectedCondition.isEmpty() ? item.expectedStatus : item.expectedCondition);
        lblFaActual.setText(item.actualCondition.isEmpty() ? (item.runResult.isEmpty() ? item.runStatus : item.runResult) : item.actualCondition);
        lblFaMethodCode.setText((item.failureMethod.isEmpty() ? "-" : item.failureMethod) +
                (item.failureResponseCode > 0 ? " (Code: " + item.failureResponseCode + ")" : ""));
        lblFaCallId.setText((item.failureCallId.isEmpty() ? "-" : item.failureCallId) +
                (item.failureCSeq.isEmpty() ? "" : " | CSeq: " + item.failureCSeq));
        lblFaClassification.setText(item.failureClassification.isEmpty() ? (item.runResult.equals("PASS") ? "None (Test Passed)" : "Pending execution") : item.failureClassification);

        txtFaReason.setText(item.failureReason.isEmpty() ? (item.runResult.equals("PASS") ? "Test case completed successfully with all assertions satisfied." : "No failure details recorded.") : item.failureReason);
        txtFaReason.setCaretPosition(0);

        txtFaEvidence.setText(item.supportingMessages.isEmpty() ? "(No supporting message traces captured yet)" : item.supportingMessages);
        txtFaEvidence.setCaretPosition(0);

        txtFaSuggestedSteps.setText(item.suggestedInvestigation.isEmpty() ?
                (item.runResult.equals("PASS") ? "• Verification complete. No further investigation required." :
                        "• Execute single test leg manually to reproduce.\n• Verify PBX route and credentials.") :
                item.suggestedInvestigation);
        txtFaSuggestedSteps.setCaretPosition(0);
    }

    private void applyFilter() {
        String filter = (String) filterCombo.getSelectedItem();
        filteredCampaigns.clear();
        for (CampaignItem c : campaigns) {
            if ("PASS Only".equals(filter) && !"PASS".equals(c.runResult)) continue;
            if ("FAIL Only".equals(filter) && !"FAIL".equals(c.runResult)) continue;
            if ("BLOCKED Only".equals(filter) && !"BLOCKED".equals(c.runResult)) continue;
            if ("Pending Only".equals(filter) && ("PASS".equals(c.runResult) || "FAIL".equals(c.runResult))) continue;
            filteredCampaigns.add(c);
        }
        tableModel.fireTableDataChanged();
        updateSummaryKpis();
    }

    private void updateSummaryKpis() {
        int total = campaigns.size();
        int passed = 0;
        int failed = 0;
        int blocked = 0;
        int pending = 0;
        int running = 0;
        double totalSecs = 0.0;

        for (CampaignItem c : campaigns) {
            if ("Running".equalsIgnoreCase(c.runStatus)) {
                running++;
            } else if ("PASS".equalsIgnoreCase(c.runResult)) {
                passed++;
            } else if ("BLOCKED".equalsIgnoreCase(c.runResult) || "Blocked".equalsIgnoreCase(c.runStatus)) {
                blocked++;
            } else if ("FAIL".equalsIgnoreCase(c.runResult)) {
                failed++;
            } else {
                pending++;
            }

            if (c.duration != null && !c.duration.isEmpty()) {
                try { totalSecs += Double.parseDouble(c.duration); } catch (Exception ignore) {}
            }
        }

        lblTotalCount.setText(String.valueOf(total));
        lblPassCount.setText(String.valueOf(passed));
        lblFailCount.setText(String.valueOf(failed));
        lblBlockCount.setText(String.valueOf(blocked));
        lblPendingCount.setText(String.valueOf(pending));
        lblRunningCount.setText(String.valueOf(running));
        lblTotalDuration.setText(String.format("%.1fs", totalSecs));

        if (running > 0) {
            lblOverallResult.setText("OVERALL RESULT: RUNNING (" + running + " active)");
            lblOverallResult.setForeground(new Color(56, 189, 248));
        } else if (failed > 0) {
            lblOverallResult.setText("OVERALL RESULT: FAIL (" + failed + " failures, " + passed + " passed)");
            lblOverallResult.setForeground(new Color(239, 68, 68));
        } else if (blocked > 0) {
            lblOverallResult.setText("OVERALL RESULT: BLOCKED (" + blocked + " blocked tests)");
            lblOverallResult.setForeground(new Color(251, 191, 36));
        } else if (passed > 0 && passed == total) {
            lblOverallResult.setText("OVERALL RESULT: 100% PASS (" + passed + "/" + total + " passed)");
            lblOverallResult.setForeground(new Color(74, 222, 128));
        } else {
            lblOverallResult.setText("OVERALL RESULT: NOT EXECUTED");
            lblOverallResult.setForeground(new Color(148, 163, 184));
        }
    }

    private void loadCampaigns() {
        File file = new File("campaigns.json");
        if (!file.exists()) {
            this.campaigns = new ArrayList<>();
            this.filteredCampaigns = new ArrayList<>();
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String json = new String(bytes, "UTF-8");
            this.campaigns = JsonCampaignsHelper.parse(json);
            applyFilter();
        } catch (Exception e) {
            logToConsole("[SYSTEM] [ERROR] Failed to load campaigns.json: " + e.getMessage());
        }
    }

    private void saveCampaigns() {
        try {
            String json = JsonCampaignsHelper.serialize(this.campaigns);
            Files.write(new File("campaigns.json").toPath(), json.getBytes("UTF-8"));
        } catch (Exception e) {
            logToConsole("[SYSTEM] [ERROR] Failed to save campaigns.json: " + e.getMessage());
        }
    }

    private void removeSelectedTestCase() {
        int selectedRow = campaignsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= filteredCampaigns.size()) {
            JOptionPane.showMessageDialog(this, "Select a test case to remove.", "Select Row", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CampaignItem item = filteredCampaigns.get(selectedRow);
        campaigns.remove(item);
        saveCampaigns();
        applyFilter();
    }

    private void editSelectedTestCase() {
        int selectedRow = campaignsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= filteredCampaigns.size()) {
            JOptionPane.showMessageDialog(this, "Select a test case to edit.", "Select Row", JOptionPane.WARNING_MESSAGE);
            return;
        }
        showTestCaseDialog(filteredCampaigns.get(selectedRow));
    }

    private void showAddTestCaseDialog() {
        showTestCaseDialog(null);
    }

    private void showTestCaseDialog(CampaignItem existing) {
        boolean editing = (existing != null);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                editing ? "Edit Campaign Test Case" : "Add Campaign Test Case", true);
        dialog.setLayout(new MigLayout("fillx, insets 15", "[130!][grow]", "[][][][][][][][]"));

        JTextField nameField = new JTextField(editing ? existing.name : "Test Case " + (campaigns.size() + 1));
        dialog.add(new JLabel("Test Name:"));
        dialog.add(nameField, "growx, wrap");

        // Scenarios
        JComboBox<String> callerScen = new JComboBox<>(listScenarios());
        if (editing && existing.callerScenario != null) callerScen.setSelectedItem(existing.callerScenario);
        dialog.add(new JLabel("Caller Scenario:"));
        dialog.add(callerScen, "growx, wrap");

        JComboBox<String> callerVal = new JComboBox<>(listValues());
        if (editing && existing.callerValues != null) callerVal.setSelectedItem(existing.callerValues);
        dialog.add(new JLabel("Caller Values:"));
        dialog.add(callerVal, "growx, wrap");

        JComboBox<String> calleeScen = new JComboBox<>(listScenarios());
        if (editing && existing.calleeScenario != null) calleeScen.setSelectedItem(existing.calleeScenario);
        dialog.add(new JLabel("Callee Scenario:"));
        dialog.add(calleeScen, "growx, wrap");

        JComboBox<String> calleeVal = new JComboBox<>(listValues());
        if (editing && existing.calleeValues != null) calleeVal.setSelectedItem(existing.calleeValues);
        dialog.add(new JLabel("Callee Values:"));
        dialog.add(calleeVal, "growx, wrap");

        JComboBox<String> expectedStatus = new JComboBox<>(new String[]{"PASS", "FAIL"});
        if (editing && existing.expectedStatus != null) expectedStatus.setSelectedItem(existing.expectedStatus);
        dialog.add(new JLabel("Expected Outcome:"));
        dialog.add(expectedStatus, "growx, wrap");

        JTextField pbxIpField = new JTextField(editing && existing.pbxIp != null ? existing.pbxIp : "");
        dialog.add(new JLabel("PBX IP Override:"));
        dialog.add(pbxIpField, "growx, wrap");

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton(editing ? "Save Changes" : "Add Test Case");
        saveBtn.addActionListener(e -> {
            CampaignItem item = editing ? existing : new CampaignItem();
            item.name = nameField.getText().trim();
            item.callerScenario = (String) callerScen.getSelectedItem();
            item.callerValues = (String) callerVal.getSelectedItem();
            item.calleeScenario = (String) calleeScen.getSelectedItem();
            item.calleeValues = (String) calleeVal.getSelectedItem();
            item.expectedStatus = (String) expectedStatus.getSelectedItem();
            item.pbxIp = pbxIpField.getText().trim();
            if (!editing) campaigns.add(item);
            saveCampaigns();
            applyFilter();
            dialog.dispose();
        });
        btns.add(saveBtn);
        dialog.add(btns, "span 2, growx");

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String[] listScenarios() {
        File dir = new File("scenarios");
        if (!dir.exists()) return new String[]{""};
        File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null) return new String[]{""};
        List<String> list = new ArrayList<>();
        list.add("");
        for (File f : files) list.add(f.getName());
        Collections.sort(list);
        return list.toArray(new String[0]);
    }

    private String[] listValues() {
        File dir = new File("values");
        if (!dir.exists()) return new String[]{""};
        File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null) return new String[]{""};
        List<String> list = new ArrayList<>();
        list.add("");
        for (File f : files) list.add(f.getName());
        Collections.sort(list);
        return list.toArray(new String[0]);
    }

    private void startCampaignRun() {
        if (campaigns.isEmpty() || campaignRunning) return;
        campaignRunning = true;
        abortCampaign = false;

        runBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        addBtn.setEnabled(false);
        removeBtn.setEnabled(false);

        for (CampaignItem c : campaigns) {
            c.runStatus = "Pending";
            c.runResult = "";
            c.duration = "";
            c.failureStage = "";
            c.actualCondition = "";
            c.failureClassification = "";
            c.causeCertainty = "";
            c.failureReason = "";
            c.supportingMessages = "";
            c.suggestedInvestigation = "";
        }
        applyFilter();
        allConsoleLogs.clear();
        consoleArea.setText("");

        logToConsole("[SYSTEM] [INFO] Starting batch test campaign containing " + campaigns.size() + " test cases...");
        new Thread(this::runCampaignSequentially).start();
    }

    private void terminateCampaignRun() {
        abortCampaign = true;
        logToConsole("[SYSTEM] [WARN] Terminating active campaign execution by user request...");
        if (activeCallerProcess != null) activeCallerProcess.destroy();
        if (activeCalleeProcess != null) activeCalleeProcess.destroy();
        if (activeTargetProcess != null) activeTargetProcess.destroy();
    }

    private void runCampaignSequentially() {
        long campaignStart = System.currentTimeMillis();

        for (int i = 0; i < campaigns.size(); i++) {
            if (abortCampaign) {
                logToConsole("[SYSTEM] [WARN] Campaign execution aborted by user.");
                break;
            }

            final int index = i;
            CampaignItem item = campaigns.get(i);

            SwingUtilities.invokeLater(() -> {
                item.runStatus = "Running";
                tableModel.fireTableDataChanged();
                updateSummaryKpis();
                displayFailureAnalysis(item);
            });

            logToConsole("[SYSTEM] [INFO] [TestCase " + (i + 1) + "/" + campaigns.size() + "] Executing: " + item.name + "...");
            long startTime = System.currentTimeMillis();
            boolean pass = executeSingleTestCase(item);
            long durationMs = System.currentTimeMillis() - startTime;
            double durationSecs = durationMs / 1000.0;

            SwingUtilities.invokeLater(() -> {
                item.duration = String.format("%.1f", durationSecs);
                if (abortCampaign) {
                    item.runStatus = "Aborted";
                    item.runResult = "BLOCKED";
                } else {
                    item.runStatus = "Completed";
                }
                tableModel.fireTableDataChanged();
                updateSummaryKpis();
                displayFailureAnalysis(item);
            });

            logToConsole("[SYSTEM] [INFO] Finished: " + item.name + " -> " + item.runResult + " (" + String.format("%.1f", durationSecs) + "s)");

            if (abortCampaign) break;

            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                break;
            }
        }

        SwingUtilities.invokeLater(() -> {
            campaignRunning = false;
            runBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            addBtn.setEnabled(true);
            removeBtn.setEnabled(true);
            updateSummaryKpis();
            logToConsole("[SYSTEM] [INFO] ==========================================");
            logToConsole("[SYSTEM] [INFO] Campaign execution finished. " + lblOverallResult.getText());
            logToConsole("[SYSTEM] [INFO] ==========================================");
        });
    }

    private static final String CALLEE_PORT = "15060";
    private static final String CALLER_PORT = "15070";

    private boolean executeSingleTestCase(CampaignItem item) {
        List<String> logsBuffer = Collections.synchronizedList(new ArrayList<>());
        boolean isRegister = item.callerScenario != null && item.callerScenario.toLowerCase().contains("reg");

        List<File> tempValuesFiles = new ArrayList<>();
        String callerValuesPath = "values/" + (item.callerValues != null ? item.callerValues : "");
        String calleeValuesPath = "values/" + (item.calleeValues != null ? item.calleeValues : "");

        String callerUser = "70200";
        String calleeUser = "70201";

        item.expectedCondition = "Assert " + item.expectedStatus + " (SIP dialog & media completion)";

        boolean executionFailed = false;
        String hardErrorReason = "";

        try {
            if (isRegister) {
                item.failureStage = "SIP Registration";
                List<String> regCmd = new ArrayList<>();
                regCmd.add(javaBin());
                if (item.callerScenario != null && !item.callerScenario.trim().isEmpty()) {
                    regCmd.add("-DscenarioPath=scenarios/" + item.callerScenario);
                }
                if (item.callerValues != null && !item.callerValues.trim().isEmpty()) {
                    regCmd.add("-DvaluesPath=" + callerValuesPath);
                }
                if (item.pbxIp != null && !item.pbxIp.trim().isEmpty()) {
                    regCmd.add("-DremoteIP=" + item.pbxIp.trim());
                }
                regCmd.add("-cp");
                regCmd.add(runtimeClasspath());
                regCmd.add("sipinspector.Media.CliSipRegisterTest");

                ProcessBuilder pb = new ProcessBuilder(regCmd);
                pb.directory(new File("."));
                activeCallerProcess = pb.start();

                Thread out = new Thread(new StreamGobbler(activeCallerProcess.getInputStream(), "[LOGS] ", logsBuffer));
                Thread err = new Thread(new StreamGobbler(activeCallerProcess.getErrorStream(), "[LOGS] [ERROR] ", logsBuffer));
                out.start();
                err.start();

                activeCallerProcess.waitFor();
                out.join(1000);
                err.join(1000);
            } else {
                item.failureStage = "SIP Signaling Setup & Media";
                List<String> calleeCmd = buildLegCmd(item, item.calleeScenario, calleeValuesPath, "callee", "callee", CALLEE_PORT, calleeUser, null);
                ProcessBuilder pbCallee = new ProcessBuilder(calleeCmd);
                pbCallee.directory(new File("."));
                activeCalleeProcess = pbCallee.start();

                Thread calleeOut = new Thread(new StreamGobbler(activeCalleeProcess.getInputStream(), "[LOGS] [CALLEE] ", logsBuffer));
                Thread calleeErr = new Thread(new StreamGobbler(activeCalleeProcess.getErrorStream(), "[LOGS] [CALLEE-ERROR] ", logsBuffer));
                calleeOut.start();
                calleeErr.start();

                Thread.sleep(2000);

                if (abortCampaign) {
                    activeCalleeProcess.destroy();
                    return false;
                }

                List<String> callerCmd = buildLegCmd(item, item.callerScenario, callerValuesPath, "caller", "caller", CALLER_PORT, callerUser, calleeUser);
                ProcessBuilder pbCaller = new ProcessBuilder(callerCmd);
                pbCaller.directory(new File("."));
                activeCallerProcess = pbCaller.start();

                Thread callerOut = new Thread(new StreamGobbler(activeCallerProcess.getInputStream(), "[LOGS] [CALLER] ", logsBuffer));
                Thread callerErr = new Thread(new StreamGobbler(activeCallerProcess.getErrorStream(), "[LOGS] [CALLER-ERROR] ", logsBuffer));
                callerOut.start();
                callerErr.start();

                activeCallerProcess.waitFor();
                activeCalleeProcess.destroy();

                callerOut.join(1000);
                callerErr.join(1000);
                calleeOut.join(1000);
                calleeErr.join(1000);
            }
        } catch (Exception ex) {
            executionFailed = true;
            hardErrorReason = ex.getMessage();
            logToConsole("[SYSTEM] [ERROR] Subprocess launch exception: " + ex.getMessage());
        } finally {
            if (activeCallerProcess != null) { try { activeCallerProcess.destroyForcibly(); } catch (Exception ignore) {} activeCallerProcess = null; }
            if (activeCalleeProcess != null) { try { activeCalleeProcess.destroyForcibly(); } catch (Exception ignore) {} activeCalleeProcess = null; }
            if (activeTargetProcess != null) { try { activeTargetProcess.destroyForcibly(); } catch (Exception ignore) {} activeTargetProcess = null; }
        }

        if (abortCampaign) {
            item.runResult = "BLOCKED";
            item.failureClassification = "Aborted by operator";
            item.causeCertainty = "Confirmed cause";
            item.failureReason = "Test execution aborted before completion.";
            return false;
        }

        // --- Failure Intelligence Analysis ---
        List<String> lines;
        synchronized (logsBuffer) {
            lines = new ArrayList<>(logsBuffer);
        }

        boolean sawPassMarker = false;
        boolean sawFailMarker = false;
        StringBuilder evidenceBuilder = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        item.failureTimestamp = sdf.format(new Date());

        for (String line : lines) {
            if (line.contains("CAMPAIGN_RESULT: PASS")) sawPassMarker = true;
            if (line.contains("CAMPAIGN_RESULT: FAIL")) sawFailMarker = true;
            if (item.failureCallId.isEmpty()) {
                String extractedCid = extractCallIdFromLogLine(line);
                if (extractedCid != null && !extractedCid.isEmpty()) {
                    item.failureCallId = extractedCid;
                }
            }
            if (line.contains("CSeq:") && item.failureCSeq.isEmpty()) {
                item.failureCSeq = line.substring(line.indexOf("CSeq:") + 5).trim();
            }
            if (line.contains("[ERROR]") || line.contains("488") || line.contains("401") || line.contains("404")
                    || line.contains("408") || line.contains("Timeout") || line.contains("Exception") || line.contains("CAMPAIGN_RESULT")) {
                evidenceBuilder.append(line).append("\n");
            }
        }

        item.supportingMessages = evidenceBuilder.toString();
        if (item.supportingMessages.isEmpty() && !lines.isEmpty()) {
            int start = Math.max(0, lines.size() - 8);
            for (int k = start; k < lines.size(); k++) item.supportingMessages += lines.get(k) + "\n";
        }

        boolean actualCallSuccess = sawPassMarker && !sawFailMarker && !executionFailed;
        String actualOutcome = actualCallSuccess ? "PASS" : "FAIL";
        boolean match = actualOutcome.equalsIgnoreCase(item.expectedStatus);

        item.actualCondition = actualCallSuccess ? "Call answered & media validated" : "Signaling or media did not complete";

        if (match) {
            item.runResult = "PASS";
            item.failureClassification = "None";
            item.causeCertainty = "Confirmed cause";
            item.failureReason = "Test satisfied expected condition (" + item.expectedStatus + ").";
            item.suggestedInvestigation = "• None. Test passed verified baseline.";
            return true;
        } else {
            // Failed condition - classify evidence
            item.runResult = "FAIL";
            classifyFailureEvidence(item, lines, hardErrorReason);
            return false;
        }
    }

    private static final java.util.regex.Pattern CALL_ID_LOG_PATTERN =
            java.util.regex.Pattern.compile("(?i)(?:Call-ID|i)[=:]\\s*([^\\r\\n\\s]+)");

    public static String extractCallIdFromLogLine(String line) {
        if (line == null) return null;
        java.util.regex.Matcher m = CALL_ID_LOG_PATTERN.matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    public static void classifyFailureEvidence(CampaignItem item, List<String> lines, String hardError) {
        String allText = (lines != null) ? String.join(" ", lines) : "";

        if (hardError != null && !hardError.isEmpty()) {
            item.failureClassification = "Internal application exception";
            item.failureStage = "Subprocess Startup";
            item.causeCertainty = "Confirmed cause";
            item.failureReason = "Subprocess execution failed: " + hardError;
            item.suggestedInvestigation = "• Check JVM heap and classpath permissions.\n• Ensure Java 8 is present.";
            return;
        }

        if (allText.contains("Address already in use") || allText.contains("BindException")) {
            item.failureClassification = "Socket binding failure";
            item.failureStage = "Socket Binding";
            item.causeCertainty = "Confirmed cause";
            item.failureReason = "Local SIP/RTP socket port conflict detected.";
            item.suggestedInvestigation = "• Check for lingering background processes holding ports 15060/15070.\n• Wait 5s for OS socket TIME_WAIT cleanup.";
            return;
        }

        if (allText.contains("488") || allText.contains("Not Acceptable Here")) {
            item.failureClassification = "Unexpected SIP response (488 Not Acceptable Here)";
            item.failureStage = "INVITE Negotiation";
            item.causeCertainty = "Confirmed cause";
            item.failureResponseCode = 488;
            item.failureMethod = "INVITE";
            item.failureReason = "OXE PBX rejected request with 488 Not Acceptable Here (Call admission limit or media mismatch).";
            item.suggestedInvestigation = "• Inspect PBX subscriber profile (single-line vs multi-line configuration).\n• Verify codec payload type matches remote SDP.";
            return;
        }

        if (allText.contains("401 Unauthorized") || allText.contains("407 Proxy Authentication")) {
            item.failureClassification = "Authentication failure";
            item.failureStage = "SIP Authentication";
            item.causeCertainty = "Confirmed cause";
            item.failureResponseCode = allText.contains("401") ? 401 : 407;
            item.failureReason = "PBX challenged credentials with 401/407 and MD5 digest was missing or rejected.";
            item.suggestedInvestigation = "• Verify SIP authentication username and secret password in scenario values.";
            return;
        }

        if (allText.contains("408") || allText.contains("Request Timeout") || allText.contains("Timer B")) {
            item.failureClassification = "SIP transaction timeout (Timer B)";
            item.failureStage = "Transaction Timeout";
            item.causeCertainty = "Confirmed cause";
            item.failureResponseCode = 408;
            item.failureReason = "Remote PBX/subscriber failed to respond within 32-second Timer B window.";
            item.suggestedInvestigation = "• Check routing and firewall UDP 5060 reachability to PBX.\n• Check if remote extension is registered.";
            return;
        }

        if (allText.contains("404 Not Found") || allText.contains("User not found")) {
            item.failureClassification = "Unexpected SIP response (404 Not Found)";
            item.failureStage = "INVITE Routing";
            item.causeCertainty = "Confirmed cause";
            item.failureResponseCode = 404;
            item.failureReason = "Dialed extension number does not exist on target PBX.";
            item.suggestedInvestigation = "• Verify dialed digits in values file against PBX dialplan.";
            return;
        }

        if (allText.contains("486 Busy") || allText.contains("Busy Here")) {
            item.failureClassification = "Unexpected SIP response (486 Busy Here)";
            item.failureStage = "Call Delivery";
            item.causeCertainty = "Confirmed cause";
            item.failureResponseCode = 486;
            item.failureReason = "Called endpoint reported busy.";
            item.suggestedInvestigation = "• Ensure callee endpoint is idle before launching test.";
            return;
        }

        if (allText.contains("SDP") || allText.contains("m=audio 0")) {
            item.failureClassification = "SDP negotiation failure";
            item.failureStage = "SDP Negotiation";
            item.causeCertainty = "Probable cause";
            item.failureReason = "SDP offer/answer codec or transport port negotiation rejected.";
            item.suggestedInvestigation = "• Ensure PCMA/PCMU/G.722 codecs are supported by both endpoints.";
            return;
        }

        if (allText.contains("RTP timeout") || allText.contains("no media")) {
            item.failureClassification = "Media failure / RTP timeout";
            item.failureStage = "In-Call Media";
            item.causeCertainty = "Probable cause";
            item.failureReason = "Signaling completed but no bidirectional RTP packets were received.";
            item.suggestedInvestigation = "• Verify firewall allows UDP ports 42000-50000.\n• Check NAT/routing.";
            return;
        }

        // Generic fallback
        item.failureClassification = "Scenario execution failure";
        item.failureStage = "Scenario Execution";
        item.causeCertainty = "Probable cause";
        item.failureReason = "Expected outcome '" + item.expectedStatus + "' was not satisfied by scenario run.";
        item.suggestedInvestigation = "• Inspect execution console logs for raw SIP response traces.";
    }

    private List<String> buildLegCmd(CampaignItem item, String scenarioFile, String valuesPath,
                                     String logId, String mode, String port, String user, String calleeUser) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-DscenarioPath=scenarios/" + scenarioFile);
        cmd.add("-DvaluesPath=" + valuesPath);
        cmd.add("-Dlogs.unique.id=" + logId);
        if (item.pbxIp != null && !item.pbxIp.trim().isEmpty()) {
            cmd.add("-DremoteIP=" + item.pbxIp.trim());
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
        return cmd;
    }

    private void logToConsole(String message) {
        allConsoleLogs.add(message);
        SwingUtilities.invokeLater(() -> {
            if (matchesLogLevel(message)) {
                consoleArea.append(message + "\n");
                if (autoScrollCheck.isSelected()) {
                    consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
                }
            }
        });
    }

    private boolean matchesLogLevel(String line) {
        String lvl = (String) logLevelCombo.getSelectedItem();
        if (lvl == null || lvl.equals("ALL")) return true;
        if (lvl.equals("ERROR")) return line.contains("[ERROR]");
        if (lvl.equals("WARN")) return line.contains("[WARN]") || line.contains("[ERROR]");
        if (lvl.equals("INFO")) return line.contains("[INFO]") || line.contains("[WARN]") || line.contains("[ERROR]");
        if (lvl.equals("DEBUG")) return true;
        return true;
    }

    private void filterConsoleLogs() {
        String keyword = logSearchField.getText().trim().toLowerCase();
        StringBuilder sb = new StringBuilder();
        synchronized (allConsoleLogs) {
            for (String line : allConsoleLogs) {
                if (!matchesLogLevel(line)) continue;
                if (!keyword.isEmpty() && !line.toLowerCase().contains(keyword)) continue;
                sb.append(line).append("\n");
            }
        }
        consoleArea.setText(sb.toString());
        if (autoScrollCheck.isSelected()) {
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        }
    }

    // --- Table Model & Cell Renderer ---
    private class CampaignsTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Test Case", "Expected", "Actual", "Status", "Duration"};

        @Override public int getRowCount() { return filteredCampaigns.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= filteredCampaigns.size()) return "";
            CampaignItem item = filteredCampaigns.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return item.name;
                case 2: return item.expectedStatus;
                case 3: return item.runResult.isEmpty() ? "-" : item.runResult;
                case 4: return item.runStatus;
                case 5: return item.duration.isEmpty() ? "-" : item.duration + "s";
                default: return "";
            }
        }
    }

    private class CampaignsCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (row < 0 || row >= filteredCampaigns.size()) return c;

            CampaignItem item = filteredCampaigns.get(row);

            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(15, 23, 42) : new Color(24, 33, 53));
                c.setForeground(new Color(241, 245, 249));

                if (col == 3 || col == 4) { // Actual & Status
                    String status = String.valueOf(value);
                    if ("PASS".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status) && "PASS".equals(item.runResult)) {
                        c.setForeground(new Color(74, 222, 128)); // green
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("FAIL".equalsIgnoreCase(status)) {
                        c.setForeground(new Color(239, 68, 68)); // red
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("BLOCKED".equalsIgnoreCase(status) || "Blocked".equalsIgnoreCase(status)) {
                        c.setForeground(new Color(251, 191, 36)); // amber
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if ("Running".equalsIgnoreCase(status)) {
                        c.setForeground(new Color(56, 189, 248)); // cyan
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }
            } else {
                c.setBackground(new Color(14, 116, 144));
                c.setForeground(Color.WHITE);
            }
            return c;
        }
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream is;
        private final String prefix;
        private final List<String> buffer;

        public StreamGobbler(InputStream is, String prefix, List<String> buffer) {
            this.is = is;
            this.prefix = prefix;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.add(line);
                }
            } catch (IOException ignore) {}
        }
    }

    private void exportReport() {
        if (campaigns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No campaigns configured to export.", "Empty Campaign", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("campaign_report.html"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(dest)) {
                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n<html>\n<head>\n")
                        .append("    <title>SIP Simulator Campaign Report</title>\n")
                        .append("    <style>\n")
                        .append("        body { font-family: 'Segoe UI', sans-serif; background-color: #0b0f19; color: #f1f5f9; padding: 30px; }\n")
                        .append("        .container { max-width: 960px; margin: 0 auto; background: #111827; border: 1px solid #1f2937; border-radius: 8px; padding: 24px; }\n")
                        .append("        h1 { color: #38bdf8; }\n")
                        .append("        .tc-card { background: #1f2937; border: 1px solid #374151; border-radius: 6px; padding: 12px; margin-bottom: 12px; }\n")
                        .append("        .pass { color: #4ade80; font-weight: bold; }\n")
                        .append("        .fail { color: #ef4444; font-weight: bold; }\n")
                        .append("        .blocked { color: #fbbf24; font-weight: bold; }\n")
                        .append("    </style>\n</head>\n<body>\n<div class=\"container\">\n")
                        .append("<h1>SIP Simulator Campaign Intelligence Report</h1>\n")
                        .append("<p>Generated: ").append(new Date()).append(" | Overall Result: ").append(lblOverallResult.getText()).append("</p>\n");

                for (CampaignItem c : campaigns) {
                    String cls = "pass";
                    if ("FAIL".equals(c.runResult)) cls = "fail";
                    else if ("BLOCKED".equals(c.runResult)) cls = "blocked";

                    html.append("<div class=\"tc-card\">\n")
                            .append("<h3>").append(c.name).append(" - <span class=\"").append(cls).append("\">").append(c.runResult).append("</span></h3>\n")
                            .append("<p><strong>Expected:</strong> ").append(c.expectedStatus).append(" | <strong>Duration:</strong> ").append(c.duration).append("s</p>\n");
                    if (!c.runResult.equals("PASS")) {
                        html.append("<p><strong>Classification:</strong> ").append(c.failureClassification).append(" (").append(c.causeCertainty).append(")</p>\n")
                                .append("<p><strong>Reason:</strong> ").append(c.failureReason).append("</p>\n");
                    }
                    html.append("</div>\n");
                }
                html.append("</div></body></html>");
                fw.write(html.toString());
                JOptionPane.showMessageDialog(this, "Report exported to: " + dest.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportCsvReport() {
        if (campaigns.isEmpty()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("campaign_report.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(dest))) {
                pw.println("#,Test Case,Expected,Actual,Status,Duration,Classification,Certainty,Reason");
                for (int i = 0; i < campaigns.size(); i++) {
                    CampaignItem c = campaigns.get(i);
                    pw.println(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                            i + 1, c.name, c.expectedStatus, c.runResult, c.runStatus, c.duration,
                            c.failureClassification, c.causeCertainty, c.failureReason.replace("\"", "\"\"")));
                }
                JOptionPane.showMessageDialog(this, "CSV exported to: " + dest.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

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
                    } else val = "";
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
                sb.append("  {\n")
                        .append("    \"name\": \"").append(escape(item.name)).append("\",\n")
                        .append("    \"callerScenario\": \"").append(escape(item.callerScenario)).append("\",\n")
                        .append("    \"callerValues\": \"").append(escape(item.callerValues)).append("\",\n")
                        .append("    \"calleeScenario\": \"").append(escape(item.calleeScenario)).append("\",\n")
                        .append("    \"calleeValues\": \"").append(escape(item.calleeValues)).append("\",\n")
                        .append("    \"targetScenario\": \"").append(escape(item.targetScenario)).append("\",\n")
                        .append("    \"targetValues\": \"").append(escape(item.targetValues)).append("\",\n")
                        .append("    \"expectedStatus\": \"").append(escape(item.expectedStatus)).append("\",\n")
                        .append("    \"callerValuesInline\": \"").append(escape(item.callerValuesInline)).append("\",\n")
                        .append("    \"calleeValuesInline\": \"").append(escape(item.calleeValuesInline)).append("\",\n")
                        .append("    \"targetValuesInline\": \"").append(escape(item.targetValuesInline)).append("\",\n")
                        .append("    \"pbxIp\": \"").append(escape(item.pbxIp)).append("\",\n")
                        .append("    \"pbxPort\": \"").append(escape(item.pbxPort)).append("\"\n")
                        .append("  }");
                if (i < items.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            return sb.toString();
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
