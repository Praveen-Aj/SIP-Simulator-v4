/*
 * Edit Values Tab
 * Allows editing values for the current session (Apply), or writing the
 * edited values back to a file (Save / Save As).
 */
package sipinspector.OperationTabs;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.EmojiIcon;
import sipinspector.Utils.FileChooserUtils;

/**
 * Tab for editing scenario values. "Apply" updates the current session only;
 * "Save"/"Save As..." write the edited values back to a values file, subject
 * to the Values Protection setting (Settings tab).
 *
 * @author SIP Support Team
 */
public class EditValuesTab extends JPanel {

    private JPanel fieldsPanel;
    private Map<String, JTextField> fieldEditors;
    private Map<String, String> originalValues;
    private JButton applyButton;
    private JButton resetButton;
    private JButton clearAllButton;
    private JButton saveButton;
    private JButton saveAsButton;
    private JLabel warningLabel;
    private boolean valuesLoaded = false;
    private SIPInspectorMainFrame mainFrame;
    private boolean dirty = false;
    private boolean suppressDirtyEvents = false;
    private String currentValuesFileName = null;
    private java.util.Map<String, java.util.Map<String, String>> editedValuesCache = new java.util.HashMap<>();

    public EditValuesTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.fieldEditors = new HashMap<>();
        this.originalValues = new HashMap<>();
        initComponents();
        setupListeners();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 5", "[70!][70!][70!][110!][130!][grow]", "[][grow]"));
        setBorder(BorderFactory.createTitledBorder("Edit Values"));

        // Action buttons on same row as title
        applyButton = new JButton("Apply");
        applyButton.setToolTipText("Apply changes to current session");
        applyButton.setEnabled(false);
        add(applyButton, "w 70!");

        resetButton = new JButton("Reset");
        resetButton.setToolTipText("Reset to original values");
        resetButton.setEnabled(false);
        add(resetButton, "w 70!");

        clearAllButton = new JButton("Clear");
        clearAllButton.setToolTipText("Clear all fields");
        clearAllButton.setEnabled(false);
        add(clearAllButton, "w 70!");

        saveButton = new JButton("Save", EmojiIcon.getSaveIcon());
        saveButton.setToolTipText("Apply changes and save them back to the loaded values file");
        saveButton.setEnabled(false);
        add(saveButton, "w 110!");

        saveAsButton = new JButton("Save As...", EmojiIcon.getSaveIcon());
        saveAsButton.setToolTipText("Apply changes and save them to a new/different values file");
        saveAsButton.setEnabled(false);
        add(saveAsButton, "w 130!");

        // Warning section
        warningLabel = new JLabel(
                "Load scenario and values first! Edit below, then Apply (session) or Save (writes to file).");
        warningLabel.setForeground(new Color(200, 100, 0));
        add(warningLabel, "grow, wrap");

        // Fields panel with scroll
        fieldsPanel = new JPanel(new MigLayout("fillx, insets 5", "[grow][grow]", "[]"));
        JScrollPane scrollPane = new JScrollPane(fieldsPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Value Fields"));
        add(scrollPane, "grow, span, height 150:220:350");
    }

    private void setupListeners() {
        applyButton.addActionListener(e -> onApply());
        resetButton.addActionListener(e -> onReset());
        clearAllButton.addActionListener(e -> onClearAll());
        saveButton.addActionListener(e -> onSave(false));
        saveAsButton.addActionListener(e -> onSave(true));
    }

    public void populateFields(String valuesFileName, Map<String, String> values) {
        this.currentValuesFileName = valuesFileName;
        
        if (values == null || values.isEmpty()) {
            showWarning("No values loaded. Please load values first.");
            valuesLoaded = false;
            return;
        }

        // Check if we have cached edits for this values file
        Map<String, String> valuesToUse = values;
        if (editedValuesCache.containsKey(valuesFileName)) {
            valuesToUse = editedValuesCache.get(valuesFileName);
            // Also apply these cached values to main frame's ValuesLoader
            mainFrame.getValuesLoader().updateCurrentRowValues(valuesToUse);
        }

        // Clear existing fields
        suppressDirtyEvents = true;
        fieldsPanel.removeAll();
        fieldEditors.clear();
        originalValues.clear();

        // Create text fields for each value with descriptions
        int columnToggle = 0;
        for (Map.Entry<String, String> entry : valuesToUse.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            String description = getFieldDescription(fieldName);

            JLabel label = new JLabel(fieldName + " " + description);
            label.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));

            JTextField textField = new JTextField(fieldValue);
            textField.setToolTipText("Edit " + fieldName + " - " + description);
            attachChangeTracking(textField);

            String constraints = (columnToggle % 2 == 0) ? "growx" : "growx, wrap";
            fieldsPanel.add(label, "gapx 10, split 2, span 1");
            fieldsPanel.add(textField, constraints);

            fieldEditors.put(fieldName, textField);
            originalValues.put(fieldName, fieldValue);
            columnToggle++;
        }
        suppressDirtyEvents = false;
        dirty = false;
        updateWarningLabel();

        // Enable buttons
        applyButton.setEnabled(true);
        resetButton.setEnabled(true);
        clearAllButton.setEnabled(true);
        saveButton.setEnabled(true);
        saveAsButton.setEnabled(true);
        valuesLoaded = true;

        // Update warning
        warningLabel.setText("Values loaded. Edit below and click Apply to update session values.");
        warningLabel.setForeground(new Color(0, 150, 0));

        fieldsPanel.revalidate();
        fieldsPanel.repaint();
    }

    private void onApply() {
        applyChanges(true);
    }

    private void onReset() {
        resetFields(true);
    }

    private void onClearAll() {
        if (!valuesLoaded) {
            showWarning("No values loaded. Please load values first.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Clear all fields?\n(This cannot be undone)",
                "Confirm Clear", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (JTextField field : fieldEditors.values()) {
                field.setText("");
            }
            markDirty();
        }
    }

    public boolean confirmPendingEdits(java.awt.Component parent) {
        if (!dirty) {
            return true;
        }

        Object[] options = { "Apply Now", "Discard", "Cancel" };
        int choice = JOptionPane.showOptionDialog(parent,
                "You have unapplied value edits. Apply them before leaving?",
                "Pending Value Edits",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
            return false;
        }

        if (choice == 0) {
            return applyChanges(false);
        }

        // Discard edits by resetting to last applied values without dialog
        resetFields(false);
        return true;
    }

    private String getFieldDescription(String fieldName) {
        switch (fieldName) {
            case "field0":
                return "(Caller user - From/Contact)";
            case "field1":
                return "(Callee user - Request-URI/To)";
            case "field2":
                return "(SDP audio media port)";
            case "field3":
                return "(Custom field 3)";
            case "field4":
                return "(Custom field 4)";
            case "field5":
                return "(Custom field 5)";
            default:
                return "(Custom value)";
        }
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    public void clearFields() {
        suppressDirtyEvents = true;
        fieldsPanel.removeAll();
        fieldEditors.clear();
        originalValues.clear();
        currentValuesFileName = null;
        applyButton.setEnabled(false);
        resetButton.setEnabled(false);
        clearAllButton.setEnabled(false);
        saveButton.setEnabled(false);
        saveAsButton.setEnabled(false);
        valuesLoaded = false;
        dirty = false;
        warningLabel.setText(
                "WARNING: Load scenario and values first! Edit below, then Apply (session) or Save (writes to file).");
        warningLabel.setForeground(new Color(200, 100, 0));
        suppressDirtyEvents = false;
    }

    private void attachChangeTracking(JTextField textField) {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        });
    }

    private void markDirty() {
        if (suppressDirtyEvents || !valuesLoaded) {
            return;
        }
        dirty = true;
        updateWarningLabel();
    }

    private void updateWarningLabel() {
        if (!valuesLoaded) {
            warningLabel.setText(
                    "WARNING: Load scenario and values first! Edit below, then Apply (session) or Save (writes to file).");
            warningLabel.setForeground(new Color(200, 100, 0));
            return;
        }

        if (dirty) {
            warningLabel.setText("Pending edits not yet applied. Click Apply to use them.");
            warningLabel.setForeground(new Color(200, 100, 0));
        } else {
            warningLabel.setText("Values loaded. Edit below and click Apply to update session values.");
            warningLabel.setForeground(new Color(0, 150, 0));
        }
    }

    private boolean applyChanges(boolean showInfoDialog) {
        if (!valuesLoaded) {
            showWarning("No values loaded. Please load values first.");
            return false;
        }

        Map<String, String> newValues = new HashMap<>();
        StringBuilder debugLog = new StringBuilder("=== EDIT VALUES APPLIED ===\n");
        for (Map.Entry<String, JTextField> entry : fieldEditors.entrySet()) {
            String fieldValue = entry.getValue().getText();
            newValues.put(entry.getKey(), fieldValue);
            GlobalSettings.setSetting(entry.getKey(), fieldValue);
            debugLog.append(entry.getKey()).append(" = ").append(fieldValue).append("\n");
        }
        debugLog.append("=== END EDIT VALUES ===\n");

        mainFrame.getValuesLoader().updateCurrentRowValues(newValues);

        // Cache the newly applied values
        if (currentValuesFileName != null) {
            editedValuesCache.put(currentValuesFileName, newValues);
        }

        if (sipinspector.Utils.TLSDebugLogger.isEnabled()) {
            sipinspector.Utils.TLSDebugLogger.log(debugLog.toString());
        }

        if (showInfoDialog) {
            JOptionPane.showMessageDialog(this,
                    "Changes applied to current session.\n(Not saved to file)\n\nRun the scenario to use updated values.",
                    "Changes Applied", JOptionPane.INFORMATION_MESSAGE);
        }

        originalValues.clear();
        originalValues.putAll(newValues);
        dirty = false;
        updateWarningLabel();
        return true;
    }

    private void resetFields(boolean showDialog) {
        if (!valuesLoaded) {
            showWarning("No values loaded. Please load values first.");
            return;
        }

        suppressDirtyEvents = true;
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            JTextField field = fieldEditors.get(entry.getKey());
            if (field != null) {
                field.setText(entry.getValue());
            }
        }
        suppressDirtyEvents = false;
        dirty = false;
        updateWarningLabel();

        if (showDialog) {
            JOptionPane.showMessageDialog(this, "Fields reset to original values.", "Reset Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Apply pending edits, then write the values back to disk - either the
     * currently loaded file (Save) or a chosen file (Save As).
     */
    private void onSave(boolean saveAs) {
        if (!valuesLoaded) {
            showWarning("No values loaded. Please load values first.");
            return;
        }
        if (!applyChanges(false)) {
            return;
        }

        File target = resolveSaveTarget(saveAs);
        if (target == null) {
            return; // chooser cancelled
        }

        if (target.exists() && GlobalSettings.isValuesProtected) {
            JOptionPane.showMessageDialog(this,
                    "\"" + target.getName() + "\" already exists and Values Protection is enabled "
                            + "(Settings tab).\nUse \"Save As...\" to choose a different file, or disable "
                            + "Values Protection to overwrite.",
                    "Values Protected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (target.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Overwrite \"" + target.getName() + "\" with the current values?",
                    "Save Values", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            writeValuesFile(target);
            currentValuesFileName = target.getName();
            GlobalSettings.setSetting("valuesPath", "values" + File.separator + target.getName());
            JOptionPane.showMessageDialog(this,
                    "Values saved to:\n" + target.getAbsolutePath(),
                    "Save Values", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save values: " + ex.getMessage(),
                    "Save Values", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File resolveSaveTarget(boolean saveAs) {
        if (!saveAs && currentValuesFileName != null) {
            return new File("values", currentValuesFileName);
        }
        File valuesDir = new File("values");
        JFileChooser chooser = new JFileChooser(valuesDir.exists() ? valuesDir : new File(System.getProperty("user.dir")));
        chooser.setDialogTitle("Save Values As");
        String suggested = currentValuesFileName != null ? currentValuesFileName : "values.txt";
        chooser.setSelectedFile(new File(suggested));
        if (FileChooserUtils.showSaveDialog(this, chooser) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".txt")) {
            f = new File(f.getParentFile(), f.getName() + ".txt");
        }
        return f;
    }

    /**
     * Write the ValuesLoader's current row state to {@code target},
     * preserving any leading "//"/"#" comment/documentation lines already
     * present in that file (if it exists).
     */
    private void writeValuesFile(File target) throws IOException {
        List<String> headerLines = new ArrayList<>();
        if (target.exists()) {
            for (String line : Files.readAllLines(target.toPath())) {
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                    headerLines.add(line);
                } else if (!trimmed.isEmpty()) {
                    break; // stop at the first real data line
                }
            }
        }

        List<String> rows = mainFrame.getValuesLoader().getAllRowsAsLines();

        try (PrintWriter pw = new PrintWriter(target, "UTF-8")) {
            for (String header : headerLines) {
                pw.println(header);
            }
            for (String row : rows) {
                pw.println(row);
            }
        }
    }
}
