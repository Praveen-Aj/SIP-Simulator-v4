/*
 * Keyboard Shortcuts Reference Dialog
 * Shows all available keyboard shortcuts in SIP Inspector
 */
package sipinspector.UI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;

import net.miginfocom.swing.MigLayout;

/**
 * Dialog showing all keyboard shortcuts
 * 
 * @author SIP Support Team
 */
public class KeyboardShortcutsDialog extends JDialog {

    public KeyboardShortcutsDialog(Frame parent) {
        super(parent, "Keyboard Shortcuts Reference", true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 20", "[grow]", "[][grow][]"));

        // Title
        JLabel titleLabel = new JLabel("Keyboard Shortcuts");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        add(titleLabel, "align center, wrap 15");

        // Create tabbed pane for categories
        JTabbedPane tabbedPane = new JTabbedPane();

        // File Operations (implemented)
        tabbedPane.addTab("File", createShortcutsPanel(new String[][] {
                { "Ctrl+N", "New Scenario", "Create a new SIP scenario" },
                { "Ctrl+O", "Open Scenario", "Load an existing scenario file" },
                { "Ctrl+S", "Save Scenario", "Save current scenario" },
                { "Ctrl+Shift+S", "Save As", "Save scenario with new name" },
                { "Ctrl+Q", "Quit", "Exit application" }
        }));

        // Edit Operations
        tabbedPane.addTab("Edit", createShortcutsPanel(new String[][] {
                { "Ctrl+F", "Find/Replace", "Search and replace in messages" }
        }));

        // Options
        tabbedPane.addTab("Options", createShortcutsPanel(new String[][] {
                { "Ctrl+L", "Toggle SIP Logging", "Enable/disable SIP logging" }
        }));

        // Help
        tabbedPane.addTab("Help", createShortcutsPanel(new String[][] {
                { "F1", "Keyboard Shortcuts", "Open this reference" }
        }));

        add(tabbedPane, "grow, wrap 15");

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton printButton = new JButton("Print");
        printButton.setToolTipText("Print this reference");
        printButton.addActionListener(e -> printShortcuts());
        buttonPanel.add(printButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        add(buttonPanel, "align center");

        // Dialog settings
        setSize(700, 600);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private JPanel createShortcutsPanel(String[][] shortcuts) {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[150!][200!][grow]", ""));

        // Header
        JLabel keyHeader = new JLabel("Shortcut");
        keyHeader.setFont(keyHeader.getFont().deriveFont(Font.BOLD));
        panel.add(keyHeader);

        JLabel actionHeader = new JLabel("Action");
        actionHeader.setFont(actionHeader.getFont().deriveFont(Font.BOLD));
        panel.add(actionHeader);

        JLabel descHeader = new JLabel("Description");
        descHeader.setFont(descHeader.getFont().deriveFont(Font.BOLD));
        panel.add(descHeader, "wrap");

        panel.add(new JSeparator(), "span 3, grow, wrap 5");

        // Add shortcuts
        for (String[] shortcut : shortcuts) {
            // Shortcut key (with background)
            JLabel keyLabel = new JLabel(shortcut[0]);
            keyLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
            keyLabel.setOpaque(true);
            keyLabel.setBackground(new Color(230, 230, 230));
            keyLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.GRAY),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            panel.add(keyLabel);

            // Action name
            JLabel actionLabel = new JLabel(shortcut[1]);
            actionLabel.setFont(actionLabel.getFont().deriveFont(Font.BOLD));
            panel.add(actionLabel);

            // Description
            JLabel descLabel = new JLabel(shortcut[2]);
            descLabel.setForeground(Color.GRAY);
            panel.add(descLabel, "wrap 3");
        }

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);

        return wrapper;
    }

    private void printShortcuts() {
        JOptionPane.showMessageDialog(this,
                "Print functionality will be available in next update.\n" +
                        "For now, you can take a screenshot (Win+Shift+S)",
                "Print Shortcuts",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
