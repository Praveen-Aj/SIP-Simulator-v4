/*
 * File Chooser Utils
 * Wraps JFileChooser with a top "Path:" address bar so a full path can be
 * typed or pasted and jumped to directly (Enter / Go), matching the native
 * Windows Explorer address-bar workflow instead of manual folder-by-folder
 * navigation.
 */
package sipinspector.Utils;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class FileChooserUtils {

    private FileChooserUtils() {
    }

    /** Drop-in replacement for {@code chooser.showOpenDialog(parent)} with a path bar. */
    public static int showOpenDialog(Component parent, JFileChooser chooser) {
        return show(parent, chooser, JFileChooser.OPEN_DIALOG);
    }

    /** Drop-in replacement for {@code chooser.showSaveDialog(parent)} with a path bar. */
    public static int showSaveDialog(Component parent, JFileChooser chooser) {
        return show(parent, chooser, JFileChooser.SAVE_DIALOG);
    }

    private static int show(Component parent, JFileChooser chooser, int dialogType) {
        chooser.setDialogType(dialogType);

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        String title = chooser.getDialogTitle();
        JDialog dialog = new JDialog(owner, title != null ? title : "Select File",
                Dialog.ModalityType.APPLICATION_MODAL);

        final int[] result = { JFileChooser.CANCEL_OPTION };

        JPanel pathBar = new JPanel(new BorderLayout(6, 0));
        pathBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        JLabel pathLabel = new JLabel("Path:");
        JTextField pathField = new JTextField();
        File startDir = chooser.getCurrentDirectory();
        if (startDir != null) {
            pathField.setText(startDir.getAbsolutePath());
        }
        JButton goButton = new JButton("Go");

        ActionListener navigate = e -> navigateTo(chooser, pathField.getText());
        pathField.addActionListener(navigate);
        goButton.addActionListener(navigate);

        pathBar.add(pathLabel, BorderLayout.WEST);
        pathBar.add(pathField, BorderLayout.CENTER);
        pathBar.add(goButton, BorderLayout.EAST);

        // Keep the path bar in sync when the user navigates via the chooser itself.
        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, evt -> {
            File dir = chooser.getCurrentDirectory();
            if (dir != null) {
                pathField.setText(dir.getAbsolutePath());
            }
        });

        chooser.addActionListener(e -> {
            result[0] = JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())
                    ? JFileChooser.APPROVE_OPTION
                    : JFileChooser.CANCEL_OPTION;
            dialog.dispose();
        });

        dialog.setLayout(new BorderLayout());
        dialog.add(pathBar, BorderLayout.NORTH);
        dialog.add(chooser, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // blocks until the chooser fires approve/cancel above

        return result[0];
    }

    private static void navigateTo(JFileChooser chooser, String pathText) {
        if (pathText == null || pathText.trim().isEmpty()) {
            return;
        }
        File f = new File(pathText.trim());
        if (f.isDirectory()) {
            chooser.setCurrentDirectory(f);
            return;
        }
        if (f.isFile()) {
            chooser.setCurrentDirectory(f.getParentFile());
            chooser.setSelectedFile(f);
            return;
        }
        // Non-existent file path (common for Save dialogs): jump to its parent
        // folder if that exists, and pre-fill the requested file name.
        File parentDir = f.getParentFile();
        if (parentDir != null && parentDir.isDirectory()) {
            chooser.setCurrentDirectory(parentDir);
            chooser.setSelectedFile(f);
        } else {
            JOptionPane.showMessageDialog(chooser, "Path not found:\n" + pathText,
                    "Invalid Path", JOptionPane.WARNING_MESSAGE);
        }
    }
}
