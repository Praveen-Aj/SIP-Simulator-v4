package sipinspector.OperationTabs;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
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
import sipinspector.SIPInspectorMainFrame;
import sipinspector.UI.SipLadderPanel;
import sipinspector.UI.SipLadderPanel.SipMessage;
import sipinspector.Utils.EmojiIcon;
import sipinspector.Utils.SipMessageRepository;
import sipinspector.Utils.SipMessageRepository.SipMessageRecord;

/**
 * SIP Message Explorer: Comprehensive live signaling inspector,
 * protocol filter, raw message viewer, and interactive call flow ladder.
 *
 * Implements Workstream D of Phase 8.
 * Java 8 compatible.
 *
 * @author SIP Support Team
 */
public class SipMessageExplorerTab extends JPanel implements SipMessageRepository.MessageListener {

    private final SIPInspectorMainFrame mainFrame;
    private final SipMessageRepository repository = SipMessageRepository.getInstance();

    private final List<SipMessageRecord> displayedMessages = new ArrayList<>();
    private final MessageTableModel tableModel = new MessageTableModel();
    private JTable messageTable;

    // Filters
    private JComboBox<String> methodCombo;
    private JComboBox<String> responseCombo;
    private JComboBox<String> directionCombo;
    private JComboBox<String> transportCombo;
    private JTextField searchField;
    private JCheckBox autoScrollCheck;
    private JLabel statusCountLabel;

    // Details components
    private JLabel lblStartLine;
    private JLabel lblEndpoints;
    private JLabel lblTransport;
    private JLabel lblCallId;
    private JLabel lblCSeq;
    private JTable headersTable;
    private HeadersTableModel headersModel;
    private JTextArea sdpTextArea;
    private JPanel sdpContainerPanel;
    private JTextArea rawTextArea;
    private SipLadderPanel ladderPanel;
    private JComboBox<String> ladderCallIdCombo;

    private SipMessageRecord selectedRecord = null;

    public SipMessageExplorerTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        repository.addListener(this);
        refreshMessages();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(new Color(15, 23, 42)); // Slate 900

        // --- 1. Top Filter & Toolbar Panel ---
        JPanel topToolbar = new JPanel(new MigLayout("fillx, insets 6", "[][grow][][][][][][]", "[]"));
        topToolbar.setBackground(new Color(30, 41, 59)); // Slate 800
        topToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        JLabel titleLbl = new JLabel("SIP Message Explorer", EmojiIcon.getSearchIcon(), SwingConstants.LEFT);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(Color.WHITE);
        topToolbar.add(titleLbl, "");

        // Search Field
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search content, Call-ID, IP...");
        searchField.addActionListener(e -> applyFilter());
        topToolbar.add(searchField, "growx");

        // Method Filter
        String[] methods = {
                "All Types", "INVITE", "REGISTER", "ACK", "BYE", "CANCEL",
                "OPTIONS", "PRACK", "UPDATE", "INFO", "REFER", "NOTIFY", "SUBSCRIBE"
        };
        methodCombo = new JComboBox<>(methods);
        methodCombo.setToolTipText("Filter by SIP method");
        methodCombo.addActionListener(e -> applyFilter());
        topToolbar.add(methodCombo, "");

        // Response Code Filter
        String[] responses = {
                "All Responses", "1xx Provisional", "2xx Success", "3xx Redirection",
                "4xx Client Error", "5xx Server Error", "6xx Global Failure",
                "100 Trying", "180 Ringing", "183 Session Progress", "200 OK",
                "401 Unauthorized", "403 Forbidden", "404 Not Found", "407 Proxy Auth Required",
                "408 Request Timeout", "480 Temporarily Unavailable", "486 Busy Here",
                "487 Request Terminated", "488 Not Acceptable Here", "500 Server Error", "503 Unavailable"
        };
        responseCombo = new JComboBox<>(responses);
        responseCombo.setToolTipText("Filter by SIP response code");
        responseCombo.addActionListener(e -> applyFilter());
        topToolbar.add(responseCombo, "");

        // Direction Filter
        directionCombo = new JComboBox<>(new String[]{"All Directions", "Inbound (←)", "Outbound (→)"});
        directionCombo.setToolTipText("Filter by direction");
        directionCombo.addActionListener(e -> applyFilter());
        topToolbar.add(directionCombo, "");

        // Transport Filter
        transportCombo = new JComboBox<>(new String[]{"All Transports", "UDP", "TCP", "TLS"});
        transportCombo.setToolTipText("Filter by transport protocol");
        transportCombo.addActionListener(e -> applyFilter());
        topToolbar.add(transportCombo, "");

        // Auto-Scroll Toggle
        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        autoScrollCheck.setOpaque(false);
        autoScrollCheck.setForeground(new Color(226, 232, 240));
        topToolbar.add(autoScrollCheck, "");

        // Actions: Clear & Export
        JButton btnClear = new JButton("Clear");
        btnClear.setToolTipText("Clear all captured SIP messages");
        btnClear.addActionListener(e -> clearAllMessages());
        topToolbar.add(btnClear, "");

        JButton btnExport = new JButton("Export...");
        btnExport.setToolTipText("Export message list to a text file");
        btnExport.addActionListener(e -> exportMessages());
        topToolbar.add(btnExport, "");

        add(topToolbar, BorderLayout.NORTH);

        // --- 2. Main Dual Split Pane ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setDividerLocation(320);
        mainSplit.setResizeWeight(0.45);
        mainSplit.setContinuousLayout(true);

        // --- Top Half: Live Message Table ---
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBackground(new Color(15, 23, 42));

        messageTable = new JTable(tableModel);
        messageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageTable.setRowHeight(24);
        messageTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        messageTable.setDefaultRenderer(Object.class, new MessageCellRenderer());

        // Column widths
        int[] colWidths = {50, 95, 45, 60, 180, 160, 160, 240, 100};
        for (int i = 0; i < colWidths.length && i < messageTable.getColumnCount(); i++) {
            messageTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
        }

        messageTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = messageTable.getSelectedRow();
                    if (row >= 0 && row < displayedMessages.size()) {
                        onMessageSelected(displayedMessages.get(row));
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(messageTable);
        tableScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));
        tableContainer.add(tableScroll, BorderLayout.CENTER);

        // Table bottom bar: Message count
        JPanel tableStatus = new JPanel(new BorderLayout());
        tableStatus.setBackground(new Color(30, 41, 59));
        tableStatus.setBorder(new EmptyBorder(3, 8, 3, 8));
        statusCountLabel = new JLabel("0 messages captured");
        statusCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusCountLabel.setForeground(new Color(148, 163, 184));
        tableStatus.add(statusCountLabel, BorderLayout.WEST);
        tableContainer.add(tableStatus, BorderLayout.SOUTH);

        mainSplit.setTopComponent(tableContainer);

        // --- Bottom Half: Multi-Tab Detail Inspector ---
        JTabbedPane detailsTabbedPane = new JTabbedPane();

        // Sub-Tab 1: Message Inspector
        detailsTabbedPane.addTab("Message Inspector", createInspectorPanel());

        // Sub-Tab 2: Call Flow (Ladder Diagram)
        detailsTabbedPane.addTab("Call Flow (Ladder Diagram)", createLadderViewPanel());

        // Sub-Tab 3: Raw SIP Text
        detailsTabbedPane.addTab("Raw SIP Message", createRawTextPanel());

        mainSplit.setBottomComponent(detailsTabbedPane);

        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(new Color(15, 23, 42));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Metadata Header
        JPanel metaPanel = new JPanel(new MigLayout("fillx, insets 6", "[100!][grow][100!][grow]", "[][][]"));
        metaPanel.setBackground(new Color(30, 41, 59));
        metaPanel.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));

        metaPanel.add(new JLabel("Start Line:"), "");
        lblStartLine = new JLabel("(No message selected)");
        lblStartLine.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblStartLine.setForeground(new Color(56, 189, 248)); // cyan
        metaPanel.add(lblStartLine, "growx, span 3, wrap");

        metaPanel.add(new JLabel("Endpoints:"), "");
        lblEndpoints = new JLabel("-");
        lblEndpoints.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblEndpoints.setForeground(Color.WHITE);
        metaPanel.add(lblEndpoints, "growx");

        metaPanel.add(new JLabel("Transport:"), "");
        lblTransport = new JLabel("-");
        lblTransport.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTransport.setForeground(new Color(74, 222, 128)); // green
        metaPanel.add(lblTransport, "wrap");

        metaPanel.add(new JLabel("Call-ID:"), "");
        lblCallId = new JLabel("-");
        lblCallId.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblCallId.setForeground(new Color(241, 245, 249));
        metaPanel.add(lblCallId, "growx");

        metaPanel.add(new JLabel("CSeq:"), "");
        lblCSeq = new JLabel("-");
        lblCSeq.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblCSeq.setForeground(Color.WHITE);
        metaPanel.add(lblCSeq, "wrap");

        panel.add(metaPanel, BorderLayout.NORTH);

        // Center Split: Headers Table on Left, SDP on Right
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setDividerLocation(450);
        centerSplit.setContinuousLayout(true);

        // Headers Table
        headersModel = new HeadersTableModel();
        headersTable = new JTable(headersModel);
        headersTable.setRowHeight(20);
        headersTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        JPanel headersPanel = new JPanel(new BorderLayout());
        headersPanel.setBackground(new Color(15, 23, 42));
        headersPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "SIP Headers",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(56, 189, 248)
        ));
        headersPanel.add(new JScrollPane(headersTable), BorderLayout.CENTER);
        centerSplit.setLeftComponent(headersPanel);

        // SDP Body Panel
        sdpContainerPanel = new JPanel(new BorderLayout());
        sdpContainerPanel.setBackground(new Color(15, 23, 42));
        sdpContainerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "SDP Media Description (Session Description Protocol)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(74, 222, 128)
        ));

        sdpTextArea = new JTextArea();
        sdpTextArea.setEditable(false);
        sdpTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sdpTextArea.setBackground(new Color(10, 15, 30));
        sdpTextArea.setForeground(new Color(226, 232, 240));
        sdpContainerPanel.add(new JScrollPane(sdpTextArea), BorderLayout.CENTER);
        centerSplit.setRightComponent(sdpContainerPanel);

        panel.add(centerSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLadderViewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(15, 23, 42));

        // Ladder Toolbar: Call-ID filter and refresh
        JPanel bar = new JPanel(new MigLayout("fillx, insets 4", "[][grow][]", "[]"));
        bar.setBackground(new Color(30, 41, 59));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        bar.add(new JLabel("Call Flow Filter:"), "");
        ladderCallIdCombo = new JComboBox<>(new String[]{"All Calls / Scenarios"});
        ladderCallIdCombo.addActionListener(e -> updateLadderDiagram());
        bar.add(ladderCallIdCombo, "growx");

        JButton btnSync = new JButton("Update Diagram");
        btnSync.addActionListener(e -> updateLadderDiagram());
        bar.add(btnSync, "");

        panel.add(bar, BorderLayout.NORTH);

        ladderPanel = new SipLadderPanel();
        ladderPanel.setSelectionListener(msg -> {
            // Find corresponding record in table
            if (msg != null && msg.rawText != null) {
                for (int i = 0; i < displayedMessages.size(); i++) {
                    SipMessageRecord rec = displayedMessages.get(i);
                    if (rec.rawMessage.trim().equals(msg.rawText.trim())) {
                        messageTable.setRowSelectionInterval(i, i);
                        messageTable.scrollRectToVisible(messageTable.getCellRect(i, 0, true));
                        break;
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(ladderPanel);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRawTextPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(15, 23, 42));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        topBar.setBackground(new Color(30, 41, 59));
        JButton btnCopy = new JButton("Copy Raw Message");
        btnCopy.addActionListener(e -> {
            String text = rawTextArea.getText();
            if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                JOptionPane.showMessageDialog(this, "SIP message copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        topBar.add(btnCopy);
        panel.add(topBar, BorderLayout.NORTH);

        rawTextArea = new JTextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rawTextArea.setBackground(new Color(5, 8, 16));
        rawTextArea.setForeground(new Color(241, 245, 249));
        rawTextArea.setCaretColor(Color.CYAN);

        panel.add(new JScrollPane(rawTextArea), BorderLayout.CENTER);
        return panel;
    }

    private void onMessageSelected(SipMessageRecord record) {
        this.selectedRecord = record;
        if (record == null) return;

        lblStartLine.setText(record.startLine);
        lblEndpoints.setText(String.format("From: %s  --->  To: %s", record.source, record.destination));
        lblTransport.setText(record.transport + " (" + record.direction + ")");
        lblCallId.setText(record.callId.isEmpty() ? "(none)" : record.callId);
        lblCSeq.setText(record.cseq.isEmpty() ? "-" : record.cseq);

        headersModel.setHeaders(record.headers);

        if (record.sdpBody != null && !record.sdpBody.trim().isEmpty()) {
            sdpTextArea.setText(record.sdpBody);
            sdpTextArea.setCaretPosition(0);
        } else {
            sdpTextArea.setText("(No SDP payload in this message)");
        }

        rawTextArea.setText(record.rawMessage);
        rawTextArea.setCaretPosition(0);
    }

    private void applyFilter() {
        String method = (String) methodCombo.getSelectedItem();
        String response = (String) responseCombo.getSelectedItem();
        String direction = (String) directionCombo.getSelectedItem();
        String transport = (String) transportCombo.getSelectedItem();
        String keyword = searchField.getText();

        String mFilter = null;
        if (method != null && !method.equals("All Types")) {
            mFilter = method;
        } else if (response != null && !response.equals("All Responses")) {
            if (response.contains(" ")) {
                mFilter = response.substring(0, response.indexOf(' ')).trim();
            } else {
                mFilter = response.trim();
            }
        }

        String dFilter = null;
        if (direction != null) {
            if (direction.startsWith("Inbound")) dFilter = "IN";
            else if (direction.startsWith("Outbound")) dFilter = "OUT";
        }

        String tFilter = null;
        if (transport != null && !transport.equals("All Transports")) {
            tFilter = transport;
        }

        List<SipMessageRecord> filtered = repository.filter(mFilter, null, dFilter, tFilter, keyword);

        synchronized (displayedMessages) {
            displayedMessages.clear();
            displayedMessages.addAll(filtered);
        }

        tableModel.fireTableDataChanged();
        statusCountLabel.setText(String.format("%d of %d messages displayed", displayedMessages.size(), repository.getMessageCount()));

        if (autoScrollCheck.isSelected() && !displayedMessages.isEmpty()) {
            int last = displayedMessages.size() - 1;
            messageTable.scrollRectToVisible(messageTable.getCellRect(last, 0, true));
        }

        updateLadderCombo();
        updateLadderDiagram();
    }

    public void refreshMessages() {
        applyFilter();
    }

    private void clearAllMessages() {
        int opt = JOptionPane.showConfirmDialog(this,
                "Clear all captured SIP messages from the repository?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            repository.clear();
            lblStartLine.setText("(No message selected)");
            lblEndpoints.setText("-");
            lblTransport.setText("-");
            lblCallId.setText("-");
            lblCSeq.setText("-");
            headersModel.setHeaders(Collections.emptyMap());
            sdpTextArea.setText("");
            rawTextArea.setText("");
            ladderPanel.clear();
        }
    }

    private void exportMessages() {
        List<SipMessageRecord> msgs = repository.getAllMessages();
        if (msgs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No messages to export.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("sip_messages_export.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("================================================================================");
                pw.println("   SIP Simulator v4 - Exported SIP Message Trace");
                pw.println("   Timestamp: " + new Date());
                pw.println("   Total Messages: " + msgs.size());
                pw.println("================================================================================");
                pw.println();
                for (SipMessageRecord rec : msgs) {
                    pw.println(String.format("[%s] [%s] [%s] %s -> %s",
                            rec.timeFormatted, rec.transport, rec.direction, rec.source, rec.destination));
                    pw.println(rec.rawMessage);
                    pw.println("--------------------------------------------------------------------------------");
                }
                JOptionPane.showMessageDialog(this, "Exported " + msgs.size() + " messages to: " + f.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting messages: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateLadderCombo() {
        Set<String> callIds = new LinkedHashSet<>();
        synchronized (displayedMessages) {
            for (SipMessageRecord rec : displayedMessages) {
                if (!rec.callId.isEmpty()) {
                    callIds.add(rec.callId);
                }
            }
        }
        Object current = ladderCallIdCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("All Calls / Scenarios");
        for (String cid : callIds) {
            model.addElement(cid);
        }
        ladderCallIdCombo.setModel(model);
        if (current != null) {
            ladderCallIdCombo.setSelectedItem(current);
        }
    }

    private void updateLadderDiagram() {
        String selectedCid = (String) ladderCallIdCombo.getSelectedItem();
        boolean filterCid = selectedCid != null && !selectedCid.equals("All Calls / Scenarios");

        List<SipMessage> ladderMsgs = new ArrayList<>();
        synchronized (displayedMessages) {
            for (SipMessageRecord rec : displayedMessages) {
                if (filterCid && !rec.callId.equals(selectedCid)) {
                    continue;
                }
                SipMessage sm = new SipMessage();
                sm.source = rec.direction.equals("OUT") ? "LOCAL" : rec.source;
                sm.target = rec.direction.equals("OUT") ? rec.destination : "LOCAL";
                sm.direction = rec.direction.toLowerCase();
                sm.label = rec.methodOrResponse;
                sm.callId = rec.callId;
                sm.cseq = rec.cseq;
                sm.rawText = rec.rawMessage;
                ladderMsgs.add(sm);
            }
        }
        ladderPanel.setMessages(ladderMsgs);
    }

    @Override
    public void onMessageAdded(SipMessageRecord record) {
        SwingUtilities.invokeLater(this::applyFilter);
    }

    @Override
    public void onMessagesCleared() {
        SwingUtilities.invokeLater(this::applyFilter);
    }

    // --- Table Models & Cell Renderers ---

    private class MessageTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Time", "Dir", "Trans", "Method / Response", "Source", "Destination", "Call-ID", "CSeq"};

        @Override
        public int getRowCount() { return displayedMessages.size(); }
        @Override
        public int getColumnCount() { return columns.length; }
        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= displayedMessages.size()) return "";
            SipMessageRecord rec = displayedMessages.get(row);
            switch (col) {
                case 0: return rec.id;
                case 1: return rec.timeFormatted;
                case 2: return rec.direction.equals("OUT") ? "→" : "←";
                case 3: return rec.transport;
                case 4: return rec.methodOrResponse;
                case 5: return rec.source;
                case 6: return rec.destination;
                case 7: return rec.callId;
                case 8: return rec.cseq;
                default: return "";
            }
        }
    }

    private class MessageCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row < 0 || row >= displayedMessages.size()) return c;

            SipMessageRecord rec = displayedMessages.get(row);

            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(15, 23, 42) : new Color(24, 33, 53));

                // Color code the Method / Response column
                if (column == 4) {
                    if (rec.isRequest) {
                        c.setForeground(new Color(56, 189, 248)); // cyan
                    } else if (rec.responseCode >= 200 && rec.responseCode < 300) {
                        c.setForeground(new Color(74, 222, 128)); // green
                    } else if (rec.responseCode >= 100 && rec.responseCode < 200) {
                        c.setForeground(new Color(250, 204, 21)); // yellow
                    } else {
                        c.setForeground(new Color(244, 63, 94)); // rose / red
                    }
                } else if (column == 2) {
                    // Direction arrow
                    c.setForeground(rec.direction.equals("OUT") ? new Color(56, 189, 248) : new Color(74, 222, 128));
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    c.setForeground(new Color(226, 232, 240));
                    setHorizontalAlignment(SwingConstants.LEFT);
                }
            } else {
                c.setBackground(new Color(14, 116, 144)); // cyan-700
                c.setForeground(Color.WHITE);
            }
            return c;
        }
    }

    private static class HeadersTableModel extends AbstractTableModel {
        private final String[] columns = {"Header Name", "Header Value"};
        private final List<Map.Entry<String, String>> entries = new ArrayList<>();

        public void setHeaders(Map<String, String> map) {
            entries.clear();
            if (map != null) {
                entries.addAll(map.entrySet());
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return entries.size(); }
        @Override
        public int getColumnCount() { return columns.length; }
        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < 0 || row >= entries.size()) return "";
            Map.Entry<String, String> e = entries.get(row);
            return col == 0 ? e.getKey() : e.getValue();
        }
    }
}
