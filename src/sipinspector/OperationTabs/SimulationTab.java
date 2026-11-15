package sipinspector.OperationTabs;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import net.miginfocom.swing.MigLayout;
import sipinspector.Call.SipCallEngine;
import sipinspector.GlobalSettings;
import sipinspector.Media.LiveAudioController;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Socket.SocketSimulationHelper;
import sipinspector.Utils.SIPStateTracker;

/**
 * Network & Protocol Testing workspace:
 * Integrates network degradation emulation (latency, jitter, loss),
 * SIP fault injection, real-time dialog/transaction observability,
 * and baseline vs impaired comparative workflows.
 *
 * Implements Workstream B of Phase 8.
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class SimulationTab extends JPanel {

    private final SIPInspectorMainFrame mainFrame;

    // Network Impairment Controls
    private JCheckBox chkImpairmentEnabled;
    private JSlider delaySlider;
    private JLabel delayValueLabel;
    private JSlider lossSlider;
    private JLabel lossValueLabel;
    private JSlider jitterSlider;
    private JLabel jitterValueLabel;
    private JCheckBox chkAffectMedia;
    private JLabel lblImpairmentStatus;

    // SIP Fault Injection Controls
    private JComboBox<String> malformedCombo;
    private JLabel lblFaultStatus;

    // SIP State Monitor
    private JLabel regStateLabel;
    private JLabel dialogCountLabel;
    private JLabel txRxPacketsLabel;
    private JLabel mediaStateLabel;
    private JLabel timerStateLabel;
    private JLabel lastUpdatedLabel;
    private JTable activeDialogsTable;
    private DialogsTableModel dialogsModel;

    // Simulation Results / Comparison
    private final List<ComparisonRecord> comparisonRecords = new ArrayList<>();
    private final ComparisonTableModel comparisonModel = new ComparisonTableModel();
    private JTable comparisonTable;

    private javax.swing.Timer refreshTimer;

    public static class ComparisonRecord {
        public String stage;
        public int latencyMs;
        public int lossPct;
        public int jitterMs;
        public String fault;
        public String outcome;
        public String mos;
        public String timestamp;

        public ComparisonRecord(String stage, int latencyMs, int lossPct, int jitterMs, String fault, String outcome, String mos) {
            this.stage = stage;
            this.latencyMs = latencyMs;
            this.lossPct = lossPct;
            this.jitterMs = jitterMs;
            this.fault = fault;
            this.outcome = outcome;
            this.mos = mos;
            this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        }
    }

    public SimulationTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadSettings();
        startTracking();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(new Color(15, 23, 42)); // Slate 900
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        // --- Top Half: Impairment + Fault Injection + State Monitor ---
        JPanel topGrid = new JPanel(new GridLayout(1, 3, 10, 0));
        topGrid.setOpaque(false);

        topGrid.add(createNetworkImpairmentPanel());
        topGrid.add(createFaultInjectionPanel());
        topGrid.add(createStateMonitorPanel());

        splitPane.setTopComponent(topGrid);

        // --- Bottom Half: Simulation Results & Comparative Analysis ---
        splitPane.setBottomComponent(createComparisonPanel());

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createNetworkImpairmentPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[110!][grow][]", "[][][][][][]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "1. Network Impairment (Latency / Loss / Jitter)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(56, 189, 248)
        ));

        // Master Enable Toggle
        chkImpairmentEnabled = new JCheckBox("Enable Network Degradation", false);
        chkImpairmentEnabled.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkImpairmentEnabled.setForeground(new Color(74, 222, 128));
        chkImpairmentEnabled.setOpaque(false);
        chkImpairmentEnabled.addActionListener(e -> {
            saveSettings();
            updateImpairmentStatus();
        });
        panel.add(chkImpairmentEnabled, "span 3, wrap");

        // Latency
        panel.add(new JLabel("Latency Delay:"));
        delaySlider = new JSlider(0, 2000, 0);
        delaySlider.setMajorTickSpacing(500);
        delaySlider.setMinorTickSpacing(100);
        delaySlider.setPaintTicks(true);
        delaySlider.addChangeListener(e -> {
            delayValueLabel.setText(delaySlider.getValue() + " ms");
            saveSettings();
        });
        panel.add(delaySlider, "growx");
        delayValueLabel = new JLabel("0 ms");
        delayValueLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        delayValueLabel.setForeground(Color.WHITE);
        panel.add(delayValueLabel, "wrap");

        // Packet Loss
        panel.add(new JLabel("Packet Loss:"));
        lossSlider = new JSlider(0, 100, 0);
        lossSlider.setMajorTickSpacing(25);
        lossSlider.setMinorTickSpacing(5);
        lossSlider.setPaintTicks(true);
        lossSlider.addChangeListener(e -> {
            lossValueLabel.setText(lossSlider.getValue() + " %");
            saveSettings();
        });
        panel.add(lossSlider, "growx");
        lossValueLabel = new JLabel("0 %");
        lossValueLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        lossValueLabel.setForeground(Color.WHITE);
        panel.add(lossValueLabel, "wrap");

        // Jitter
        panel.add(new JLabel("Interarrival Jitter:"));
        jitterSlider = new JSlider(0, 500, 0);
        jitterSlider.setMajorTickSpacing(100);
        jitterSlider.setMinorTickSpacing(25);
        jitterSlider.setPaintTicks(true);
        jitterSlider.addChangeListener(e -> {
            jitterValueLabel.setText(jitterSlider.getValue() + " ms");
            saveSettings();
        });
        panel.add(jitterSlider, "growx");
        jitterValueLabel = new JLabel("0 ms");
        jitterValueLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        jitterValueLabel.setForeground(Color.WHITE);
        panel.add(jitterValueLabel, "wrap");

        // Affect Media Checkbox
        chkAffectMedia = new JCheckBox("Impair RTP Audio Media Streams (in addition to SIP)", false);
        chkAffectMedia.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkAffectMedia.setForeground(new Color(226, 232, 240));
        chkAffectMedia.setOpaque(false);
        chkAffectMedia.addActionListener(e -> saveSettings());
        panel.add(chkAffectMedia, "span 3, wrap");

        // Status & Reset
        lblImpairmentStatus = new JLabel("Status: Impairment Inactive");
        lblImpairmentStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblImpairmentStatus.setForeground(new Color(148, 163, 184));
        panel.add(lblImpairmentStatus, "span 2");

        JButton btnReset = new JButton("Reset Impairment");
        btnReset.addActionListener(e -> resetImpairment());
        panel.add(btnReset, "");

        return panel;
    }

    private JPanel createFaultInjectionPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[110!][grow]", "[][][][grow]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "2. SIP Protocol Fault Injection",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(244, 63, 94) // rose
        ));

        panel.add(new JLabel("Malformed Header:"));
        String[] anomalies = {
                "None",
                "CSeq (-9999 negative seq)",
                "Call-ID (corrupted token)",
                "Via (malformed branch ID)",
                "Expires (invalid non-numeric string)",
                "Content-Length (-1 negative length)",
                "Max-Forwards (0 hops remaining)"
        };
        malformedCombo = new JComboBox<>(anomalies);
        malformedCombo.addActionListener(e -> {
            saveSettings();
            updateFaultStatus();
        });
        panel.add(malformedCombo, "growx, wrap");

        panel.add(new JLabel("Target Traffic:"));
        JLabel lblTraffic = new JLabel("SIP Signaling (Outgoing requests)");
        lblTraffic.setForeground(new Color(148, 163, 184));
        panel.add(lblTraffic, "wrap");

        panel.add(new JLabel("Active State:"));
        lblFaultStatus = new JLabel("No fault injection active");
        lblFaultStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFaultStatus.setForeground(new Color(148, 163, 184));
        panel.add(lblFaultStatus, "wrap");

        JTextArea infoText = new JTextArea(
                "Protocol fault injection mutates outbound SIP request datagrams.\n" +
                "Use this to verify RFC 3261 parser robustness and PBX error handling (400 Bad Request / 483 Too Many Hops)."
        );
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoText.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        infoText.setBackground(new Color(15, 23, 42));
        infoText.setForeground(new Color(148, 163, 184));
        panel.add(new JScrollPane(infoText), "span 2, grow");

        return panel;
    }

    private JPanel createStateMonitorPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[120!][grow]", "[][][][][][grow]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "3. SIP State Observability Monitor",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(74, 222, 128)
        ));

        panel.add(new JLabel("Registration State:"));
        regStateLabel = new JLabel("Unregistered");
        regStateLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        regStateLabel.setForeground(new Color(239, 68, 68));
        panel.add(regStateLabel, "wrap");

        panel.add(new JLabel("Active Call Dialogs:"));
        dialogCountLabel = new JLabel("0 Dialogs");
        dialogCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dialogCountLabel.setForeground(new Color(148, 163, 184));
        panel.add(dialogCountLabel, "wrap");

        panel.add(new JLabel("Signaling Packets:"));
        txRxPacketsLabel = new JLabel("Tx: 0 | Rx: 0");
        txRxPacketsLabel.setForeground(Color.WHITE);
        panel.add(txRxPacketsLabel, "wrap");

        panel.add(new JLabel("Media State:"));
        mediaStateLabel = new JLabel("Idle (No active RTP)");
        mediaStateLabel.setForeground(new Color(148, 163, 184));
        panel.add(mediaStateLabel, "wrap");

        panel.add(new JLabel("Last Updated:"));
        lastUpdatedLabel = new JLabel("-");
        lastUpdatedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lastUpdatedLabel.setForeground(new Color(148, 163, 184));
        panel.add(lastUpdatedLabel, "wrap");

        // Active Dialogs mini-table
        dialogsModel = new DialogsTableModel();
        activeDialogsTable = new JTable(dialogsModel);
        activeDialogsTable.setRowHeight(18);
        activeDialogsTable.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        activeDialogsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        activeDialogsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        activeDialogsTable.getColumnModel().getColumn(2).setPreferredWidth(60);

        JScrollPane dScroll = new JScrollPane(activeDialogsTable);
        dScroll.setBorder(BorderFactory.createTitledBorder("Active Sessions"));
        panel.add(dScroll, "span 2, grow");

        return panel;
    }

    private JPanel createComparisonPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(new Color(15, 23, 42));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "4. Comparative Simulation Results (Baseline vs Impaired vs Recovery)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(192, 132, 252) // purple
        ));

        // Comparison Toolbar
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(new Color(30, 41, 59));

        JButton btnRecordBaseline = new JButton("Capture Baseline Snapshot");
        btnRecordBaseline.addActionListener(e -> recordSnapshot("1. Baseline"));
        bar.add(btnRecordBaseline);

        JButton btnRecordImpaired = new JButton("Capture Impaired Snapshot");
        btnRecordImpaired.addActionListener(e -> recordSnapshot("2. Impaired Call"));
        bar.add(btnRecordImpaired);

        JButton btnRecordRecovery = new JButton("Capture Post-Recovery Snapshot");
        btnRecordRecovery.addActionListener(e -> recordSnapshot("3. Post-Recovery"));
        bar.add(btnRecordRecovery);

        JButton btnClearRecords = new JButton("Clear Results");
        btnClearRecords.addActionListener(e -> {
            comparisonRecords.clear();
            comparisonModel.fireTableDataChanged();
        });
        bar.add(btnClearRecords);

        JButton btnExportCsv = new JButton("Export CSV...");
        btnExportCsv.addActionListener(e -> exportComparisonCsv());
        bar.add(btnExportCsv);

        panel.add(bar, BorderLayout.NORTH);

        comparisonTable = new JTable(comparisonModel);
        comparisonTable.setRowHeight(22);
        comparisonTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comparisonTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        comparisonTable.setDefaultRenderer(Object.class, new ComparisonCellRenderer());

        panel.add(new JScrollPane(comparisonTable), BorderLayout.CENTER);
        return panel;
    }

    private void recordSnapshot(String stageName) {
        int delay = delaySlider.getValue();
        int loss = lossSlider.getValue();
        int jitter = jitterSlider.getValue();
        String mal = (String) malformedCombo.getSelectedItem();
        boolean impaired = chkImpairmentEnabled.isSelected() && (delay > 0 || loss > 0 || jitter > 0 || !"None".equals(mal));

        LiveAudioController audio = LiveAudioController.getActiveInstance();
        String mosStr = "N/A";
        String outcome = "IDLE";
        if (audio != null && audio.isAudioRunning()) {
            double mos = audio.getEstimatedMos();
            mosStr = String.format("%.2f (est)", mos);
            outcome = "STREAMING (MOS=" + String.format("%.2f", mos) + ")";
        } else {
            int dCount = SipCallEngine.getInstance().getActiveSessions().size();
            if (dCount > 0) {
                outcome = dCount + " Active Calls";
            } else {
                outcome = SIPStateTracker.getRegistrationState();
            }
        }

        ComparisonRecord rec = new ComparisonRecord(stageName, delay, loss, jitter, mal, outcome, mosStr);
        comparisonRecords.add(rec);
        comparisonModel.fireTableDataChanged();
    }

    private void exportComparisonCsv() {
        if (comparisonRecords.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No simulation results to export.", "Empty Table", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("simulation_comparison_results.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("Timestamp,Stage,Latency(ms),Loss(%),Jitter(ms),Fault Injection,Outcome,Estimated MOS");
                for (ComparisonRecord r : comparisonRecords) {
                    pw.println(String.format("%s,\"%s\",%d,%d,%d,\"%s\",\"%s\",\"%s\"",
                            r.timestamp, r.stage, r.latencyMs, r.lossPct, r.jitterMs, r.fault, r.outcome, r.mos));
                }
                JOptionPane.showMessageDialog(this, "Exported results to: " + f.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void resetImpairment() {
        chkImpairmentEnabled.setSelected(false);
        delaySlider.setValue(0);
        lossSlider.setValue(0);
        jitterSlider.setValue(0);
        chkAffectMedia.setSelected(false);
        malformedCombo.setSelectedIndex(0);
        SocketSimulationHelper.resetCounters();
        saveSettings();
        updateImpairmentStatus();
        updateFaultStatus();
        updateStateMonitor();
    }

    private void updateImpairmentStatus() {
        boolean enabled = chkImpairmentEnabled.isSelected();
        int d = delaySlider.getValue();
        int l = lossSlider.getValue();
        int j = jitterSlider.getValue();

        if (enabled && (d > 0 || l > 0 || j > 0)) {
            lblImpairmentStatus.setText("Status: ACTIVE (" + d + "ms delay, " + l + "% loss, " + j + "ms jitter)");
            lblImpairmentStatus.setForeground(new Color(239, 68, 68)); // red / warning
        } else {
            lblImpairmentStatus.setText("Status: Impairment Inactive (Clean Network)");
            lblImpairmentStatus.setForeground(new Color(74, 222, 128)); // green
        }
    }

    private void updateFaultStatus() {
        String mal = (String) malformedCombo.getSelectedItem();
        if (mal != null && !mal.equalsIgnoreCase("None")) {
            lblFaultStatus.setText("ACTIVE: Injecting " + mal);
            lblFaultStatus.setForeground(new Color(244, 63, 94));
        } else {
            lblFaultStatus.setText("No fault injection active");
            lblFaultStatus.setForeground(new Color(148, 163, 184));
        }
    }

    private void loadSettings() {
        try {
            String enabledVal = GlobalSettings.getSetting("sim.enabled");
            if (enabledVal != null) chkImpairmentEnabled.setSelected("true".equalsIgnoreCase(enabledVal.trim()));

            String delayVal = GlobalSettings.getSetting("sim.delay");
            if (delayVal != null) delaySlider.setValue(Integer.parseInt(delayVal.trim()));

            String lossVal = GlobalSettings.getSetting("sim.loss");
            if (lossVal != null) lossSlider.setValue(Integer.parseInt(lossVal.trim()));

            String jitterVal = GlobalSettings.getSetting("sim.jitter");
            if (jitterVal != null) jitterSlider.setValue(Integer.parseInt(jitterVal.trim()));

            String affectMediaVal = GlobalSettings.getSetting("sim.affectMedia");
            if (affectMediaVal != null) chkAffectMedia.setSelected("true".equalsIgnoreCase(affectMediaVal.trim()));

            String malformedVal = GlobalSettings.getSetting("sim.malformed");
            if (malformedVal != null) {
                for (int i = 0; i < malformedCombo.getItemCount(); i++) {
                    if (malformedCombo.getItemAt(i).toLowerCase().contains(malformedVal.toLowerCase())) {
                        malformedCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } catch (Exception ignore) {}

        delayValueLabel.setText(delaySlider.getValue() + " ms");
        lossValueLabel.setText(lossSlider.getValue() + " %");
        jitterValueLabel.setText(jitterSlider.getValue() + " ms");
        updateImpairmentStatus();
        updateFaultStatus();
    }

    private void saveSettings() {
        GlobalSettings.setSetting("sim.enabled", String.valueOf(chkImpairmentEnabled.isSelected()));
        GlobalSettings.setSetting("sim.delay", String.valueOf(delaySlider.getValue()));
        GlobalSettings.setSetting("sim.loss", String.valueOf(lossSlider.getValue()));
        GlobalSettings.setSetting("sim.jitter", String.valueOf(jitterSlider.getValue()));
        GlobalSettings.setSetting("sim.affectMedia", String.valueOf(chkAffectMedia.isSelected()));

        String selected = (String) malformedCombo.getSelectedItem();
        String mal = "None";
        if (selected != null) {
            if (selected.contains("CSeq")) mal = "CSeq";
            else if (selected.contains("Call-ID")) mal = "Call-ID";
            else if (selected.contains("Via")) mal = "Via";
            else if (selected.contains("Expires")) mal = "Expires";
            else if (selected.contains("Content-Length")) mal = "Content-Length";
            else if (selected.contains("Max-Forwards")) mal = "Max-Forwards";
        }
        GlobalSettings.setSetting("sim.malformed", mal);
        GlobalSettings.saveSettings();
    }

    private void startTracking() {
        refreshTimer = new javax.swing.Timer(1000, e -> updateStateMonitor());
        refreshTimer.start();
    }

    private void updateStateMonitor() {
        // Registration state
        String regState = SIPStateTracker.getRegistrationState();
        regStateLabel.setText(regState);
        if (regState.startsWith("Registered")) {
            regStateLabel.setForeground(new Color(74, 222, 128)); // green
        } else if (regState.startsWith("Registering")) {
            regStateLabel.setForeground(new Color(56, 189, 248)); // cyan
        } else {
            regStateLabel.setForeground(new Color(239, 68, 68)); // red
        }

        // Active Dialogs
        Map<String, SipCallEngine.CallSession> sessions = SipCallEngine.getInstance().getActiveSessions();
        int dialogCount = sessions != null ? sessions.size() : SIPStateTracker.getActiveDialogCount();
        dialogCountLabel.setText(dialogCount + " active dialog" + (dialogCount == 1 ? "" : "s"));
        dialogCountLabel.setForeground(dialogCount > 0 ? new Color(74, 222, 128) : new Color(148, 163, 184));

        dialogsModel.updateSessions(sessions);

        // Signaling & Impairment Packet Counters
        long tx = sipinspector.Utils.SipMessageRepository.getInstance().getTxMessageCount();
        long rx = sipinspector.Utils.SipMessageRepository.getInstance().getRxMessageCount();
        long sipDropped = SocketSimulationHelper.getSipPacketsDropped();
        long rtpDropped = SocketSimulationHelper.getRtpPacketsDropped();
        boolean impActive = SocketSimulationHelper.isImpairmentEnabled();

        StringBuilder pktText = new StringBuilder();
        pktText.append("Tx: ").append(tx).append(" | Rx: ").append(rx);
        if (impActive || (sipDropped + rtpDropped > 0)) {
            pktText.append(" | Dropped: ").append(sipDropped + rtpDropped)
                   .append(" (SIP: ").append(sipDropped).append(", RTP: ").append(rtpDropped).append(")");
        }
        txRxPacketsLabel.setText(pktText.toString());
        if (sipDropped + rtpDropped > 0) {
            txRxPacketsLabel.setForeground(new Color(251, 191, 36)); // amber
        } else {
            txRxPacketsLabel.setForeground(Color.WHITE);
        }

        // Media State
        LiveAudioController audio = LiveAudioController.getActiveInstance();
        if (audio != null && audio.isAudioRunning()) {
            double mos = audio.getEstimatedMos();
            mediaStateLabel.setText("Streaming (Est. MOS: " + String.format("%.2f", mos) + ")");
            mediaStateLabel.setForeground(new Color(74, 222, 128));
        } else {
            mediaStateLabel.setText("Idle (No active media)");
            mediaStateLabel.setForeground(new Color(148, 163, 184));
        }

        lastUpdatedLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new Date()));
    }

    public void stopTimer() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    // --- Table Models & Cell Renderers ---

    private static class DialogsTableModel extends AbstractTableModel {
        private final String[] columns = {"Call-ID", "Remote Party", "State"};
        private final List<String[]> rows = new ArrayList<>();

        public void updateSessions(Map<String, SipCallEngine.CallSession> map) {
            rows.clear();
            if (map != null) {
                for (SipCallEngine.CallSession s : map.values()) {
                    rows.add(new String[]{s.callId, s.callee != null ? s.callee : "-", String.valueOf(s.state)});
                }
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Object getValueAt(int r, int c) { return r < rows.size() ? rows.get(r)[c] : ""; }
    }

    private class ComparisonTableModel extends AbstractTableModel {
        private final String[] columns = {"Timestamp", "Stage", "Latency", "Loss", "Jitter", "Fault Injection", "Outcome", "Est. MOS"};

        @Override public int getRowCount() { return comparisonRecords.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int r, int c) {
            if (r < 0 || r >= comparisonRecords.size()) return "";
            ComparisonRecord rec = comparisonRecords.get(r);
            switch (c) {
                case 0: return rec.timestamp;
                case 1: return rec.stage;
                case 2: return rec.latencyMs + " ms";
                case 3: return rec.lossPct + " %";
                case 4: return rec.jitterMs + " ms";
                case 5: return rec.fault;
                case 6: return rec.outcome;
                case 7: return rec.mos;
                default: return "";
            }
        }
    }

    private static class ComparisonCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(15, 23, 42) : new Color(24, 33, 53));
                c.setForeground(new Color(241, 245, 249));

                if (col == 1) { // Stage
                    setFont(getFont().deriveFont(Font.BOLD));
                    c.setForeground(new Color(56, 189, 248));
                } else if (col == 7) { // MOS
                    setFont(getFont().deriveFont(Font.BOLD));
                    String s = String.valueOf(value);
                    if (s.startsWith("4")) c.setForeground(new Color(74, 222, 128)); // green
                    else if (s.startsWith("3")) c.setForeground(new Color(251, 191, 36)); // yellow
                    else c.setForeground(new Color(239, 68, 68)); // red
                }
            } else {
                c.setBackground(new Color(14, 116, 144));
                c.setForeground(Color.WHITE);
            }
            return c;
        }
    }
}
