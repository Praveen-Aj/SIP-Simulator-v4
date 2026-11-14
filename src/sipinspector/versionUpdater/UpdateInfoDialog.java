/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and
 *  troubleshoot SIP signaling.
 *
 *  Copyright (C) 2008  Zarko Coklin (zarko_coklin at yahoo dot com)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * UpdateInfoDialog.java
 *
 * Created on Apr 13, 2009, 2:41:04 PM
 */
package sipinspector.versionUpdater;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import sipinspector.SIPInspectorMainFrame;

/**
 *
 * @author Zarko Coklin
 */
public class UpdateInfoDialog extends javax.swing.JFrame {
    /** Creates new form UpdateInfoDialog */
    public UpdateInfoDialog(JFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.setLocationRelativeTo(null);
        SIPInspectorMainFrame.setIcon(this);
        initComponents();

        pack();
        this.setVisible(true);
    }

    private void initComponents() {
        setLayout(new MigLayout(""));
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Version checker");
        setResizable(false);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        versionPanel = new JPanel(new MigLayout(""));
        add(versionPanel, "wrap");

        versionInfoLabel = new javax.swing.JLabel("A new version ("
                + CheckUpdate.newVersionStr + ") of SIP Simulator is available.");
        versionInfoLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        versionPanel.add(versionInfoLabel, "center, wrap");

        upgradeQuestion = new javax.swing.JLabel("Would you like to upgrade now?");
        upgradeQuestion.setFont(new java.awt.Font("Tahoma", 0, 12));
        versionPanel.add(upgradeQuestion, "center, wrap");

        NoteLabel = new javax.swing.JLabel("p.s. If you upgrade, make sure to unzip and run a new version ;-)");
        NoteLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        versionPanel.add(NoteLabel, "wrap");

        JPanel lowerPanel = new JPanel(new MigLayout("fillx"));
        NoButton = new javax.swing.JToggleButton("No");
        NoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NoButtonActionPerformed(evt);
            }
        });
        lowerPanel.add(NoButton);

        YesButton = new javax.swing.JToggleButton("Yes");
        YesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                YesButtonActionPerformed(evt);
            }
        });
        lowerPanel.add(YesButton, "right");
        add(lowerPanel, "grow");

        pack();
    }

    private void YesButtonActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_YesButtonActionPerformed
    {// GEN-HEADEREND:event_YesButtonActionPerformed
        final JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        fc.setDialogTitle("Save new version");
        File file = new File("SIP_Simulator_"
                + CheckUpdate.newVersionStr + ".zip");
        fc.setSelectedFile(file);
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, fc) == JFileChooser.APPROVE_OPTION) {
            file = fc.getSelectedFile();
            if (file.exists() == true) {
                Object[] options = {
                        "Yes", "No"
                };
                int result = JOptionPane.showOptionDialog(this,
                        "Do you really want to overwrite an existing scenario file?", "Save File",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                if (result != 0) {
                    dispose();
                    mainFrame.setVisible(true);
                    return;
                }
            }
            DownloaderThread myThread = new DownloaderThread(mainFrame,
                    this, fc);
            myThread.start();
        }

    }// GEN-LAST:event_YesButtonActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt)// GEN-FIRST:event_formWindowClosed
    {// GEN-HEADEREND:event_formWindowClosed
        mainFrame.setVisible(true);
    }// GEN-LAST:event_formWindowClosed

    private void NoButtonActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_NoButtonActionPerformed
    {// GEN-HEADEREND:event_NoButtonActionPerformed
        dispose();
        mainFrame.setVisible(true);
    }// GEN-LAST:event_NoButtonActionPerformed

    private javax.swing.JToggleButton NoButton;

    private javax.swing.JLabel NoteLabel;

    private javax.swing.JToggleButton YesButton;

    private javax.swing.JLabel upgradeQuestion;

    private javax.swing.JLabel versionInfoLabel;

    private javax.swing.JPanel versionPanel;

    private JFrame mainFrame;
}
