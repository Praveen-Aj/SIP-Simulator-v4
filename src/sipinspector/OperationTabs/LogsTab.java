package sipinspector.OperationTabs;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.UI.SipLadderPanel;
import sipinspector.UI.SipLadderPanel.SipMessage;
import sipinspector.Utils.DiagramExporter;
import sipinspector.Utils.EmojiIcon;
import sipinspector.Utils.LogManager;
import sipinspector.Utils.TLSDebugLogger;

/**
 * Enhanced dual-pane Logs Browser with log search filters,
 * raw text logs viewer, and visual SIP sequence ladder panel.
 * 
 * @author SIP Support Team
 */
public class LogsTab extends JPanel {

    private SIPInspectorMainFrame mainFrame;
    private JComboBox<String> folderCombo;
    private JTextField searchField;
    private DefaultListModel<String> listModel;
    private JList<String> filesList;
    
    private JTextArea rawTextArea;
    private SipLadderPanel ladderPanel;
    private JTextArea messageDetailsArea;
    private JButton exportPngBtn;
    private JButton exportPdfBtn;
    private File currentLadderFile;
    
    private JCheckBox enableLogsCheckbox;
    private Map<String, File> fileListMap = new HashMap<>();

    public LogsTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadLogFilesList();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // --- Top Control Panel ---
        JPanel topPanel = new JPanel(new MigLayout("fillx, insets 5", "[grow][][][]", "[]"));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        JLabel titleLabel = new JLabel("SIP Logs Browser");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        topPanel.add(titleLabel, "growx");

        // Enable logs checkbox
        enableLogsCheckbox = new JCheckBox("Enable Logging", GlobalSettings.logsEnabled);
        enableLogsCheckbox.setToolTipText("Enable/disable log capture monitoring");
        enableLogsCheckbox.addActionListener(e -> onToggleLogs());
        topPanel.add(enableLogsCheckbox, "");

        // Open Folder button
        JButton openFolderBtn = new JButton("Open Logs Directory");
        openFolderBtn.setToolTipText("Open active logs directory in system explorer");
        openFolderBtn.addActionListener(e -> {
            String selectedFolder = (String) folderCombo.getSelectedItem();
            LogManager.openLogsFolder(selectedFolder);
        });
        topPanel.add(openFolderBtn, "");

        add(topPanel, BorderLayout.NORTH);

        // --- Main Dual-Pane Browser (JSplitPane) ---
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setContinuousLayout(true);

        // --- Left Sidebar ---
        JPanel sidebarPanel = new JPanel(new MigLayout("fill, insets 5", "[grow]", "[][][grow]"));
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(51, 65, 85)));

        // Folder selection & Refresh
        JPanel folderAndRefresh = new JPanel(new MigLayout("fillx, insets 0", "[grow][]", "[]"));
        folderCombo = new JComboBox<>(new String[]{"UDP", "TCP", "TLS", "SIP"});
        String transport = GlobalSettings.getTransport();
        if (transport != null) {
            folderCombo.setSelectedItem(transport.toUpperCase());
        } else {
            folderCombo.setSelectedItem("UDP");
        }
        folderCombo.addActionListener(e -> loadLogFilesList());
        folderAndRefresh.add(folderCombo, "growx");

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadLogFilesList());
        folderAndRefresh.add(refreshBtn, "");
        sidebarPanel.add(folderAndRefresh, "growx, wrap");

        // Search Input
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search log files...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { loadLogFilesList(); }
            @Override
            public void removeUpdate(DocumentEvent e) { loadLogFilesList(); }
            @Override
            public void changedUpdate(DocumentEvent e) { loadLogFilesList(); }
        });
        sidebarPanel.add(searchField, "growx, wrap");

        // Files List
        listModel = new DefaultListModel<>();
        filesList = new JList<>(listModel);
        filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onFileSelected(filesList.getSelectedValue());
            }
        });
        
        JScrollPane listScrollPane = new JScrollPane(filesList);
        sidebarPanel.add(listScrollPane, "grow, push");

        mainSplitPane.setLeftComponent(sidebarPanel);

        // --- Right Content Panel ---
        JTabbedPane rightTabbedPane = new JTabbedPane();

        // Sub-Tab 1: Visual Sequence Diagram (SipLadderPanel)
        JPanel ladderTabPanel = new JPanel(new BorderLayout());

        // Export toolbar for the call-flow diagram
        JToolBar ladderToolbar = new JToolBar();
        ladderToolbar.setFloatable(false);
        exportPngBtn = new JButton("Export PNG", EmojiIcon.getExportImageIcon());
        exportPngBtn.setToolTipText("Export the call-flow diagram as a PNG image");
        exportPngBtn.setEnabled(false);
        exportPngBtn.addActionListener(e -> exportDiagram("png"));
        exportPdfBtn = new JButton("Export PDF", EmojiIcon.getExportPdfIcon());
        exportPdfBtn.setToolTipText("Export the call-flow diagram as a PDF");
        exportPdfBtn.setEnabled(false);
        exportPdfBtn.addActionListener(e -> exportDiagram("pdf"));
        ladderToolbar.add(exportPngBtn);
        ladderToolbar.add(exportPdfBtn);
        ladderTabPanel.add(ladderToolbar, BorderLayout.NORTH);

        ladderPanel = new SipLadderPanel();
        JScrollPane ladderScrollPane = new JScrollPane(ladderPanel);
        ladderScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ladderScrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        // Message detail area (below the canvas)
        messageDetailsArea = new JTextArea();
        messageDetailsArea.setEditable(false);
        messageDetailsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        messageDetailsArea.setBackground(new Color(9, 15, 30)); // extra dark slate
        messageDetailsArea.setForeground(new Color(6, 182, 212)); // cyan text
        JScrollPane detailsScrollPane = new JScrollPane(messageDetailsArea);
        detailsScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)), "Selected SIP Message Headers"));

        JSplitPane ladderSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        ladderSplitPane.setDividerLocation(400);
        ladderSplitPane.setResizeWeight(0.7);
        ladderSplitPane.setContinuousLayout(true);
        ladderSplitPane.setTopComponent(ladderScrollPane);
        ladderSplitPane.setBottomComponent(detailsScrollPane);

        ladderPanel.setSelectionListener(msg -> {
            if (msg != null) {
                messageDetailsArea.setText(msg.rawText);
                messageDetailsArea.setCaretPosition(0);
            } else {
                messageDetailsArea.setText("");
            }
        });

        ladderTabPanel.add(ladderSplitPane, BorderLayout.CENTER);
        rightTabbedPane.addTab("Visual Sequence Ladder", ladderTabPanel);

        // Sub-Tab 2: Raw Text Log Area
        rawTextArea = new JTextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rawTextArea.setBackground(new Color(15, 23, 42)); // slate-900
        rawTextArea.setForeground(new Color(241, 245, 249)); // slate-100
        JScrollPane rawScrollPane = new JScrollPane(rawTextArea);
        rightTabbedPane.addTab("Raw Log Text", rawScrollPane);

        mainSplitPane.setRightComponent(rightTabbedPane);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private void onToggleLogs() {
        boolean enabled = enableLogsCheckbox.isSelected();
        GlobalSettings.logsEnabled = enabled;
        GlobalSettings.setSetting("logsEnabled", Boolean.toString(enabled));
        GlobalSettings.saveSettings();

        if (enabled) {
            LogManager.startLogging(GlobalSettings.getTransport());
        } else {
            LogManager.stopLogging();
        }
    }

    private void loadLogFilesList() {
        String folderName = (String) folderCombo.getSelectedItem();
        String searchStr = searchField.getText().trim().toLowerCase();
        File dir = new File(TLSDebugLogger.LOG_BASE_DIR, folderName);
        
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null) {
            listModel.clear();
            return;
        }
        
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        listModel.clear();
        fileListMap.clear();
        
        for (File f : files) {
            String name = f.getName();
            if (searchStr.isEmpty() || name.toLowerCase().contains(searchStr)) {
                listModel.addElement(name);
                fileListMap.put(name, f);
            }
        }
    }

    private void onFileSelected(String filename) {
        if (filename == null) {
            rawTextArea.setText("");
            ladderPanel.clear();
            messageDetailsArea.setText("");
            currentLadderFile = null;
            setExportEnabled(false);
            return;
        }
        
        File file = fileListMap.get(filename);
        if (file == null || !file.exists()) {
            rawTextArea.setText("");
            ladderPanel.clear();
            messageDetailsArea.setText("");
            currentLadderFile = null;
            setExportEnabled(false);
            return;
        }
        
        // Read raw text
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            rawTextArea.setText(new String(bytes, "UTF-8"));
            rawTextArea.setCaretPosition(0);
        } catch (IOException e) {
            rawTextArea.setText("Error reading log file: " + e.getMessage());
        }
        
        // Load visual diagram
        ladderPanel.loadLogFile(file);
        messageDetailsArea.setText("");
        currentLadderFile = file;
        setExportEnabled(ladderPanel.hasMessages());
    }

    private void setExportEnabled(boolean enabled) {
        if (exportPngBtn != null) {
            exportPngBtn.setEnabled(enabled);
        }
        if (exportPdfBtn != null) {
            exportPdfBtn.setEnabled(enabled);
        }
    }

    private void exportDiagram(String type) {
        if (!ladderPanel.hasMessages()) {
            JOptionPane.showMessageDialog(this,
                "No diagram to export. Select a log file with SIP messages first.",
                "Export Diagram", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean pdf = "pdf".equalsIgnoreCase(type);
        String ext = pdf ? ".pdf" : ".png";
        String base = "call_flow";
        if (currentLadderFile != null) {
            String n = currentLadderFile.getName();
            int dot = n.lastIndexOf('.');
            base = (dot > 0 ? n.substring(0, dot) : n) + "_ladder";
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Call Flow Diagram");
        chooser.setSelectedFile(new File(base + ext));
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(ext)) {
            out = new File(out.getAbsolutePath() + ext);
        }
        if (out.exists()) {
            int c = JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?",
                "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) {
                return;
            }
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            BufferedImage img = ladderPanel.renderToImage(2);
            if (pdf) {
                DiagramExporter.exportPdf(img, out);
            } else {
                DiagramExporter.exportPng(img, out);
            }
            JOptionPane.showMessageDialog(this,
                "Diagram exported:\n" + out.getAbsolutePath(),
                "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Export failed: " + ex.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }
}

