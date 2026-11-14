/*
 * Combined Scenario + Values Panel
 * Unified interface for loading scenarios and values
 */
package sipinspector.OperationTabs;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JFileChooser;
import javax.swing.JSplitPane;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.LoadScenario;
import sipinspector.Utils.ValuesLoader;
import sipinspector.Utils.PcapConverter;

/**
 * Combined Scenario and Values loading panel
 * Allows users to select and load scenarios and values from dropdowns
 * Auto-loads matching values when scenario is selected
 * 
 * @author SIP Support Team
 */
public class ScenarioValuesPanel extends JPanel {

    private JComboBox<String> scenarioDropdown;
    private JButton loadScenarioButton;

    private JComboBox<String> valuesDropdown;
    private JButton loadValuesButton;

    private JLabel scenarioStatusLabel;
    private JLabel valuesStatusLabel;
    
    private JButton importPcapButton;
    private JTextArea txtPreview;

    private Vector<String> scenarioFiles;
    private Vector<String> valuesFiles;
    private SIPInspectorMainFrame mainFrame;
    private EditValuesTab editValuesTab;

    private String currentScenarioFile;
    private String currentValuesFile;
    private boolean scenarioDirty;
    private boolean valuesDirty;
    private boolean suppressSelectionEvents;

    public ScenarioValuesPanel(SIPInspectorMainFrame mainFrame, EditValuesTab editValuesTab) {
        this.mainFrame = mainFrame;
        this.editValuesTab = editValuesTab;
        this.scenarioFiles = new Vector<>();
        this.valuesFiles = new Vector<>();

        initComponents();
        populateDropdowns();
        setupListeners();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        JPanel selectionPanel = new JPanel(new MigLayout("fillx, insets 5", "[100!][grow][60!]", "[][][][][]"));
        selectionPanel.setBorder(BorderFactory.createTitledBorder("Scenario & Values"));

        // Scenario section
        JLabel scenarioLabel = new JLabel("Scenario:");
        selectionPanel.add(scenarioLabel, "left");

        scenarioDropdown = new JComboBox<>();
        scenarioDropdown.setToolTipText("Select a scenario file");
        selectionPanel.add(scenarioDropdown, "grow");

        loadScenarioButton = new JButton("Load");
        loadScenarioButton.setToolTipText("Load selected scenario");
        selectionPanel.add(loadScenarioButton, "w 60!, wrap");

        scenarioStatusLabel = new JLabel("Not loaded");
        scenarioStatusLabel.setForeground(Color.RED);
        scenarioStatusLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
        selectionPanel.add(scenarioStatusLabel, "span, left, wrap");

        // Values section - directly below scenario
        JLabel valuesLabel = new JLabel("Values:");
        selectionPanel.add(valuesLabel, "left");

        valuesDropdown = new JComboBox<>();
        valuesDropdown.setToolTipText("Select a values file");
        selectionPanel.add(valuesDropdown, "grow");

        loadValuesButton = new JButton("Load");
        loadValuesButton.setToolTipText("Load selected values");
        selectionPanel.add(loadValuesButton, "w 60!, wrap");

        valuesStatusLabel = new JLabel("Not loaded");
        valuesStatusLabel.setForeground(Color.RED);
        valuesStatusLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
        selectionPanel.add(valuesStatusLabel, "span, left, wrap");

        // Import PCAP row
        JLabel pcapLabel = new JLabel("Import Captures:");
        selectionPanel.add(pcapLabel, "left");
        importPcapButton = new JButton("Import PCAP File");
        importPcapButton.setToolTipText("Convert a PCAP Wireshark SIP capture to scenario template");
        selectionPanel.add(importPcapButton, "span, grow, wrap");

        add(selectionPanel, BorderLayout.NORTH);

        // Preview Section
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "Resolved SIP Message Preview (First Block / Flow)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(56, 189, 248)
        ));

        txtPreview = new JTextArea();
        txtPreview.setEditable(false);
        txtPreview.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtPreview.setBackground(new Color(15, 23, 42));
        txtPreview.setForeground(new Color(226, 232, 240));
        
        JScrollPane scroll = new JScrollPane(txtPreview);
        previewPanel.add(scroll, BorderLayout.CENTER);

        add(previewPanel, BorderLayout.CENTER);
    }

    private void populateDropdowns() {
        // Populate scenario dropdown
        File scenarioDir = new File("scenarios");
        if (scenarioDir.exists() && scenarioDir.isDirectory()) {
            File[] files = scenarioDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    scenarioFiles.add(file.getName());
                }
            }
        }
        scenarioDropdown.setModel(new DefaultComboBoxModel<>(scenarioFiles));

        // Populate values dropdown
        File valuesDir = new File("values");
        if (valuesDir.exists() && valuesDir.isDirectory()) {
            File[] files = valuesDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    valuesFiles.add(file.getName());
                }
            }
        }
        valuesDropdown.setModel(new DefaultComboBoxModel<>(valuesFiles));

        // Auto-load Reg_client scenario and values on startup
        autoLoadDefaultScenario();
    }

    private void autoLoadDefaultScenario() {
        // Select Reg_client scenario
        for (int i = 0; i < scenarioDropdown.getItemCount(); i++) {
            if ("Reg_client.txt".equals(scenarioDropdown.getItemAt(i))) {
                scenarioDropdown.setSelectedIndex(i);
                break;
            }
        }

        // Select matching values
        for (int i = 0; i < valuesDropdown.getItemCount(); i++) {
            if ("Reg_client_values.txt".equals(valuesDropdown.getItemAt(i))) {
                valuesDropdown.setSelectedIndex(i);
                break;
            }
        }

        // Auto-load both scenario and values
        javax.swing.SwingUtilities.invokeLater(() -> {
            onLoadScenario();
            onLoadValues();
        });
    }

    private void setupListeners() {
        loadScenarioButton.addActionListener(e -> {
            onLoadScenario();
            onLoadValues();
        });
        loadValuesButton.addActionListener(e -> {
            onLoadScenario();
            onLoadValues();
        });
        scenarioDropdown.addActionListener(e -> onScenarioSelectionChanged());
        valuesDropdown.addActionListener(e -> onValuesSelectionChanged());
        importPcapButton.addActionListener(e -> onImportPcap());
    }

    private void onScenarioSelectionChanged() {
        if (suppressSelectionEvents) {
            return;
        }
        String selectedScenario = (String) scenarioDropdown.getSelectedItem();
        matchValuesForScenario(selectedScenario);
        updateScenarioDirtyState();
        updatePreview();
    }

    private void onValuesSelectionChanged() {
        if (suppressSelectionEvents) {
            return;
        }
        updateValuesDirtyState();
        updatePreview();
    }

    private void onLoadScenario() {
        if (mainFrame == null) {
            return;
        }
        String selectedScenario = (String) scenarioDropdown.getSelectedItem();
        if (selectedScenario == null || selectedScenario.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a scenario", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String scenarioPath = "scenarios" + File.separator + selectedScenario;
        GlobalSettings.setSetting("scenarioPath", scenarioPath);

        // Load scenario using existing LoadScenario utility
        LoadScenario loader = new LoadScenario(mainFrame, scenarioPath);
        loader.execute();

        // Update status
        scenarioStatusLabel.setText("Status: Loaded - " + selectedScenario);
        scenarioStatusLabel.setForeground(new Color(0, 150, 0));
        currentScenarioFile = selectedScenario;
        scenarioDirty = false;

        // Update main frame status bar
        if (mainFrame != null && mainFrame.getStatusBar() != null) {
            mainFrame.getStatusBar().setScenarioLoaded(true);
        }
        updatePreview();
    }

    private void onLoadValues() {
        if (mainFrame == null) {
            return;
        }
        String selectedValues = (String) valuesDropdown.getSelectedItem();
        if (selectedValues == null || selectedValues.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a values file", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String valuesPath = "values" + File.separator + selectedValues;
        GlobalSettings.setSetting("valuesPath", valuesPath);

        // Load values directly into the main frame's active ValuesLoader
        mainFrame.getValuesLoader().loadValuesFromFile(valuesPath);
        mainFrame.getValuesLoader().setCounter(0);

        // Update status
        valuesStatusLabel.setText("Status: Loaded - " + selectedValues);
        valuesStatusLabel.setForeground(new Color(0, 150, 0));
        currentValuesFile = selectedValues;
        valuesDirty = false;

        // Update main frame status bar
        if (mainFrame != null && mainFrame.getStatusBar() != null) {
            mainFrame.getStatusBar().setValuesLoaded(true);
        }

        // Populate EditValuesTab with loaded values
        if (editValuesTab != null) {
            // Get first row of values from main frame's ValuesLoader
            java.util.Map<String, String> valuesMap = new java.util.HashMap<>();
            sipinspector.Utils.SIValue[] firstRow = mainFrame.getValuesLoader().getRowOfValues();
            // Reset counter because getRowOfValues increments it
            mainFrame.getValuesLoader().setCounter(0);
            if (firstRow != null && firstRow.length > 0) {
                for (int i = 0; i < firstRow.length; i++) {
                    String fieldKey = "field" + i;
                    String fieldValue = firstRow[i].getValue();
                    valuesMap.put(fieldKey, fieldValue);
                }
            }
            editValuesTab.populateFields(selectedValues, valuesMap);
        }
        updatePreview();
    }

    public void refreshDropdowns() {
        scenarioFiles.clear();
        valuesFiles.clear();
        populateDropdowns();
    }

    public boolean confirmPendingLoads(java.awt.Component parent) {
        if (!scenarioDirty && !valuesDirty) {
            return true;
        }

        StringBuilder message = new StringBuilder("You changed selections without loading:\n");
        if (scenarioDirty) {
            message.append("- Scenario: ").append(scenarioDropdown.getSelectedItem()).append("\n");
        }
        if (valuesDirty) {
            message.append("- Values: ").append(valuesDropdown.getSelectedItem()).append("\n");
        }
        message.append("\nLoad them now?");

        Object[] options = { "Load Now", "Discard", "Cancel" };
        int choice = JOptionPane.showOptionDialog(parent,
                message.toString(),
                "Pending Loads",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
            return false;
        }

        if (choice == 0) {
            if (scenarioDirty) {
                onLoadScenario();
            }
            if (valuesDirty) {
                onLoadValues();
            }
            return true;
        }

        // Discard pending changes by restoring last loaded selections
        revertScenarioSelection();
        revertValuesSelection();
        return true;
    }

    private void matchValuesForScenario(String selectedScenario) {
        if (selectedScenario == null || selectedScenario.isEmpty()) {
            return;
        }
        String scenarioBase = selectedScenario.replace(".txt", "");
        String matchingValues = scenarioBase + "_values.txt";
        for (int i = 0; i < valuesDropdown.getItemCount(); i++) {
            String valuesFile = valuesDropdown.getItemAt(i);
            if (valuesFile.equalsIgnoreCase(matchingValues)) {
                suppressSelectionEvents = true;
                valuesDropdown.setSelectedIndex(i);
                suppressSelectionEvents = false;
                updateValuesDirtyState();
                break;
            }
        }
    }

    private void updateScenarioDirtyState() {
        String selectedScenario = (String) scenarioDropdown.getSelectedItem();
        if (selectedScenario == null || selectedScenario.isEmpty()) {
            scenarioDirty = false;
            scenarioStatusLabel.setText("Select a scenario");
            scenarioStatusLabel.setForeground(Color.RED);
            return;
        }
        if (selectedScenario.equals(currentScenarioFile)) {
            scenarioDirty = false;
            scenarioStatusLabel.setText("Status: Loaded - " + selectedScenario);
            scenarioStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            scenarioDirty = true;
            scenarioStatusLabel.setText("Pending load - " + selectedScenario);
            scenarioStatusLabel.setForeground(new Color(200, 100, 0));
        }
    }

    private void updateValuesDirtyState() {
        String selectedValues = (String) valuesDropdown.getSelectedItem();
        if (selectedValues == null || selectedValues.isEmpty()) {
            valuesDirty = false;
            valuesStatusLabel.setText("Select a values file");
            valuesStatusLabel.setForeground(Color.RED);
            return;
        }
        if (selectedValues.equals(currentValuesFile)) {
            valuesDirty = false;
            valuesStatusLabel.setText("Status: Loaded - " + selectedValues);
            valuesStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            valuesDirty = true;
            valuesStatusLabel.setText("Pending load - " + selectedValues);
            valuesStatusLabel.setForeground(new Color(200, 100, 0));
        }
    }

    private void revertScenarioSelection() {
        if (currentScenarioFile == null) {
            scenarioDirty = false;
            scenarioStatusLabel.setText("Not loaded");
            scenarioStatusLabel.setForeground(Color.RED);
            return;
        }
        suppressSelectionEvents = true;
        scenarioDropdown.setSelectedItem(currentScenarioFile);
        suppressSelectionEvents = false;
        updateScenarioDirtyState();
    }

    private void revertValuesSelection() {
        if (currentValuesFile == null) {
            valuesDirty = false;
            valuesStatusLabel.setText("Not loaded");
            valuesStatusLabel.setForeground(Color.RED);
            return;
        }
        suppressSelectionEvents = true;
        valuesDropdown.setSelectedItem(currentValuesFile);
        suppressSelectionEvents = false;
        updateValuesDirtyState();
    }

    private void updatePreview() {
        if (txtPreview == null) return;
        
        String selectedScenario = (String) scenarioDropdown.getSelectedItem();
        String selectedValues = (String) valuesDropdown.getSelectedItem();
        
        if (selectedScenario == null || selectedScenario.isEmpty()) {
            txtPreview.setText("Select a scenario to view resolved preview.");
            return;
        }
        
        File scFile = new File("scenarios" + File.separator + selectedScenario);
        if (!scFile.exists()) {
            txtPreview.setText("Scenario file not found.");
            return;
        }
        
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(scFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            
            String text = sb.toString();
            
            if (selectedValues != null && !selectedValues.isEmpty()) {
                File valFile = new File("values" + File.separator + selectedValues);
                if (valFile.exists()) {
                    // Mirror ValuesLoader.doInBackground()'s parsing exactly (skip
                    // '//'/'#' comment lines, split data rows on ';') so the preview
                    // matches the row the real Run/Load path resolves.
                    List<String[]> rows = new ArrayList<>();
                    try (BufferedReader br = new BufferedReader(new FileReader(valFile))) {
                        String vline;
                        while ((vline = br.readLine()) != null) {
                            vline = vline.trim();
                            if (vline.startsWith("//") || vline.startsWith("#")) {
                                continue;
                            }
                            rows.add(vline.split(";"));
                        }
                    }
                    if (!rows.isEmpty()) {
                        String[] values = rows.get(0);
                        for (int i = 0; i < values.length; i++) {
                            String val = values[i].trim();
                            if (i == 0) {
                                String accountUser = GlobalSettings.getUsername();
                                if (accountUser != null && !accountUser.isEmpty()) {
                                    if (rows.size() <= 1 || val.equals("1000") || val.equals("70200")) {
                                        val = accountUser;
                                    }
                                }
                            }
                            text = text.replace("[field" + i + "]", val);
                        }
                    }
                }
            }
            
            String localIp = GlobalSettings.getLocalIP();
            String remoteIp = GlobalSettings.getRemoteIPAddr();
            String localPort = GlobalSettings.getLocalPort();
            String remotePort = GlobalSettings.getRemotePort();
            String mediaPort = GlobalSettings.getMediaPort();
            String service = GlobalSettings.getUsername();
            String username = GlobalSettings.getAuthUsername();
            String password = GlobalSettings.getAuthPassword();
            String transport = GlobalSettings.getTransport();
            
            if (localIp != null) text = text.replace("[local_ip]", localIp);
            if (remoteIp != null) text = text.replace("[remote_ip]", remoteIp);
            if (localPort != null) text = text.replace("[local_port]", localPort);
            if (remotePort != null) text = text.replace("[remote_port]", remotePort);
            if (mediaPort != null) text = text.replace("[media_port]", mediaPort);
            if (service != null) text = text.replace("[service]", service);
            if (username != null) text = text.replace("[username]", username);
            if (password != null) text = text.replace("[password]", password);
            if (transport != null) text = text.replace("[transport]", transport);
            
            List<String> warnings = validateScenario(sb.toString());
            if (!warnings.isEmpty()) {
                StringBuilder warnText = new StringBuilder();
                warnText.append("=== ⚠️ SCENARIO SYNTAX WARNINGS ===\n");
                for (String w : warnings) {
                    warnText.append("- ").append(w).append("\n");
                }
                warnText.append("===================================\n\n");
                text = warnText.toString() + text;
            }

            txtPreview.setText(text);
            txtPreview.setCaretPosition(0);
            
        } catch (Exception e) {
            txtPreview.setText("Error rendering preview:\n" + e.getMessage());
        }
    }

    private List<String> validateScenario(String content) {
        List<String> warnings = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return warnings;
        }

        String[] blocks = content.split("(?=u->|u<-|---------->|<----------)");
        int blockIdx = 1;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            String[] lines = trimmed.split("\\r?\\n");
            if (lines.length == 0) continue;

            boolean onlyComments = true;
            for (String l : lines) {
                String tl = l.trim();
                if (!tl.isEmpty() && !tl.startsWith("//")) {
                    onlyComments = false;
                    break;
                }
            }
            if (onlyComments) continue;

            String firstLine = lines[0].trim();
            if (firstLine.endsWith("*")) {
                firstLine = firstLine.substring(0, firstLine.length() - 1).trim();
            }
            if (!"u->".equals(firstLine) && !"u<-".equals(firstLine) && 
                !"---------->".equals(firstLine) && !"<----------".equals(firstLine)) {
                warnings.add("Block " + blockIdx + ": Does not start with a valid direction arrow (e.g., ----------> or u->). Found: " + (firstLine.length() > 15 ? firstLine.substring(0, 15) : firstLine));
                blockIdx++;
                continue;
            }

            int openBrackets = 0;
            int closeBrackets = 0;
            for (int i = 0; i < trimmed.length(); i++) {
                if (trimmed.charAt(i) == '[') openBrackets++;
                else if (trimmed.charAt(i) == ']') closeBrackets++;
            }
            if (openBrackets != closeBrackets) {
                warnings.add("Block " + blockIdx + ": Unbalanced brackets. Found " + openBrackets + " '[' and " + closeBrackets + " ']'.");
            }

            if (lines.length > 1) {
                boolean containsSip = false;
                for (String l : lines) {
                    if (l.contains("SIP/2.0") || l.startsWith("INVITE") || l.startsWith("ACK") || l.startsWith("BYE") || l.startsWith("CANCEL")) {
                        containsSip = true;
                        break;
                    }
                }
                if (containsSip) {
                    if (!block.endsWith("\n\n") && !block.endsWith("\r\n\r\n") && !block.endsWith("\n\r\n")) {
                        warnings.add("Block " + blockIdx + ": Message block might be missing trailing double CRLF separator line.");
                    }
                }
            }
            blockIdx++;
        }
        return warnings;
    }

    private void onImportPcap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Standard SIP PCAP Capture File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Wireshark PCAP Capture (*.pcap)", "pcap"));
        int ret = sipinspector.Utils.FileChooserUtils.showOpenDialog(this, chooser);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            String base = selectedFile.getName();
            int dot = base.lastIndexOf('.');
            if (dot != -1) {
                base = base.substring(0, dot);
            }
            
            String outScenarioName = base + "_imported.txt";
            String outValuesName = base + "_imported_values.txt";
            
            try {
                Map<String, String> conversion = PcapConverter.convert(selectedFile, null, base + "_imported");
                
                File scOut = new File("scenarios" + File.separator + outScenarioName);
                try (PrintWriter pw = new PrintWriter(new FileWriter(scOut))) {
                    pw.write(conversion.get("scenario"));
                }
                
                File valOut = new File("values" + File.separator + outValuesName);
                try (PrintWriter pw = new PrintWriter(new FileWriter(valOut))) {
                    pw.write(conversion.get("values"));
                }
                
                JOptionPane.showMessageDialog(this,
                    "Successfully converted PCAP!\n" +
                    "Extracted Call-ID: " + conversion.get("call_id") + "\n" +
                    "Extracted Packets: " + conversion.get("packet_count") + "\n\n" +
                    "Scenario saved as: " + outScenarioName + "\n" +
                    "Values saved as: " + outValuesName,
                    "PCAP Convert Success",
                    JOptionPane.INFORMATION_MESSAGE);
                
                refreshDropdowns();
                
                for (int i = 0; i < scenarioDropdown.getItemCount(); i++) {
                    if (outScenarioName.equals(scenarioDropdown.getItemAt(i))) {
                        scenarioDropdown.setSelectedIndex(i);
                        break;
                    }
                }
                
                for (int i = 0; i < valuesDropdown.getItemCount(); i++) {
                    if (outValuesName.equals(valuesDropdown.getItemAt(i))) {
                        valuesDropdown.setSelectedIndex(i);
                        break;
                    }
                }
                
                onLoadScenario();
                onLoadValues();
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to parse and convert PCAP:\n" + ex.getMessage(),
                    "PCAP Convert Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
