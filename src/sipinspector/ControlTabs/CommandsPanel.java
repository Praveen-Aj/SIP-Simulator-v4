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
package sipinspector.ControlTabs;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JTree;

import net.miginfocom.swing.MigLayout;
import sipinspector.SIPScenario;
import sipinspector.ScenarioEntries.CommandPauseEntry;
import sipinspector.ScenarioEntries.CommandPlayPcapEntry;
import sipinspector.ScenarioEntries.NewDialog;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.ScenarioEntries.SetRemoteTargetEntry;
import sipinspector.Utils.TLSDebugLogger;

/**
 * 
 * @author Zarko Coklin
 */
public class CommandsPanel extends EntryTab {
    /** Creates new form CommandsPanel */
    public CommandsPanel() {
        initComponents();
    }

    public JTree addMsgTreeEntry(JTree tree, SIPScenario sipScenario) {
        ScenarioEntry newMsg;

        if (PauseButton.isSelected() == true) {
            if (PauseField.getText().equals("") == true) {
                JOptionPane.showMessageDialog(this,
                        "You have to provide a value", "Command validation",
                        JOptionPane.ERROR_MESSAGE);
                return tree;
            }
            try {
                Integer.parseInt(PauseField.getText());
            } catch (NumberFormatException exc) {
                JOptionPane.showMessageDialog(this,
                        "Please provide an integer value",
                        "Command validation", JOptionPane.ERROR_MESSAGE);
                return tree;
            }
            newMsg = new CommandPauseEntry("cmd_Pause=" + PauseField.getText());
        } else if (PlayPcapButton.isSelected() == true) {
            if (filePath.getText().equals("") == true) {
                JOptionPane.showMessageDialog(this,
                        "Please provide a file path", "Command validation",
                        JOptionPane.ERROR_MESSAGE);
                return tree;
            }
            String cmdString;
            if (continuousRTPPlay.isSelected() == false) {
                cmdString = "cmd_PlayPcap=" + filePath.getText() + ";continuous=false";
                newMsg = new CommandPlayPcapEntry(cmdString);

            } else {
                cmdString = "cmd_PlayPcap=" + filePath.getText() + ";continuous=true";
                newMsg = new CommandPlayPcapEntry(cmdString);

            }

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP COMMAND ADDED ===");
                TLSDebugLogger.log("Source: CommandsPanel.addMsgTreeEntry");
                TLSDebugLogger.log("Command: " + cmdString);
                TLSDebugLogger.log("");
            }
        } else if (RemoteTarget.isSelected() == true) {
            newMsg = new SetRemoteTargetEntry("cmd_setRemote="
                    + RemoteTargetField.getText());
        } else {
            newMsg = new NewDialog("cmd_NewDialog");
        }

        sipScenario.appendNewEntry(newMsg, tree);
        sipScenario.updateTreeFromScenario(tree);
        return tree;
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        setName("Command");
        this.setLayout(new MigLayout(""));

        add(new JLabel("Command"), "gaptop 10");
        add(new JLabel("Value"), "wrap");

        // Create a button group and add all buttons to it
        buttonGroup = new javax.swing.ButtonGroup();

        // Pause button
        PauseButton = new javax.swing.JRadioButton("Pause");
        buttonGroup.add(PauseButton);
        PauseButton.setSelected(true);
        PauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PauseButtonActionPerformed(evt);
            }
        });
        add(PauseButton);

        PauseField = new javax.swing.JTextField(10);
        add(PauseField);
        add(new JLabel("[ms]"), "wrap");

        // PlayPcap button
        PlayPcapButton = new javax.swing.JRadioButton("Play pcap");
        buttonGroup.add(PlayPcapButton);
        PlayPcapButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PlayPcapButtonActionPerformed(evt);
            }
        });
        add(PlayPcapButton);

        // File path field
        filePath = new javax.swing.JTextField(10);
        filePath.setEnabled(false);
        add(filePath);

        // File selection button
        FileSelectionButton = new javax.swing.JButton("...");
        FileSelectionButton.setEnabled(false);
        FileSelectionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                FileSelectionButtonActionPerformed(evt);
            }
        });
        add(FileSelectionButton, "w 30!");

        // Continuous RTP Play checkbox
        continuousRTPPlay = new javax.swing.JCheckBox();
        continuousRTPPlay.setSelected(true);
        continuousRTPPlay.setText("Cont.");
        continuousRTPPlay.setToolTipText("Select to play RTP continuously");
        continuousRTPPlay.setEnabled(false);
        add(continuousRTPPlay, "wrap");

        // RemoteTarget
        RemoteTarget = new javax.swing.JRadioButton("Set Remote");
        buttonGroup.add(RemoteTarget);
        RemoteTarget.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SetRemoteButtonActionPerformed(evt);
            }
        });
        add(RemoteTarget);

        RemoteTargetField = new JTextField();
        RemoteTargetField.setEnabled(false);
        add(RemoteTargetField, "spanx, grow");

        // New Dialog
        NewDialogButton = new javax.swing.JRadioButton("New Dialog");
        buttonGroup.add(NewDialogButton);
        NewDialogButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SetNewDialogButtonActionPerformed(evt);
            }
        });
        add(NewDialogButton);

    }

    private void FileSelectionButtonActionPerformed(
            java.awt.event.ActionEvent evt) {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        fc.setDialogTitle("Select a valid cap/pcap file");
        if (sipinspector.Utils.FileChooserUtils.showOpenDialog(this, fc) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fc.getSelectedFile().getPath();
            filePath.setText(selectedPath);
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP FILE SELECTED ===");
                TLSDebugLogger.log("Source: CommandsPanel.FileSelectionButtonActionPerformed");
                TLSDebugLogger.log("Path: " + selectedPath);
                TLSDebugLogger.log("");
            }
        }
    }

    private void PauseButtonActionPerformed(java.awt.event.ActionEvent evt)// GEN-FIRST:event_PauseButtonActionPerformed
    {
        if (PauseButton.isSelected() == true) {
            PauseField.setEnabled(true);
            filePath.setEnabled(false);
            FileSelectionButton.setEnabled(false);
            continuousRTPPlay.setEnabled(false);
            RemoteTargetField.setEnabled(false);
        }
    }

    private void PlayPcapButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (PlayPcapButton.isSelected() == true) {
            PauseField.setEnabled(false);
            filePath.setEnabled(true);
            FileSelectionButton.setEnabled(true);
            continuousRTPPlay.setEnabled(true);
            RemoteTargetField.setEnabled(false);
        }
    }

    private void SetRemoteButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (RemoteTarget.isSelected() == true) {
            PauseField.setEnabled(false);
            filePath.setEnabled(false);
            FileSelectionButton.setEnabled(false);
            continuousRTPPlay.setEnabled(false);
            RemoteTargetField.setEnabled(true);
        }
    }

    private void SetNewDialogButtonActionPerformed(java.awt.event.ActionEvent evt) {
        if (NewDialogButton.isSelected() == true) {
            PauseField.setEnabled(false);
            filePath.setEnabled(false);
            FileSelectionButton.setEnabled(false);
            continuousRTPPlay.setEnabled(false);
            RemoteTargetField.setEnabled(true);
        }
    }

    private javax.swing.JButton FileSelectionButton;

    private javax.swing.JRadioButton PauseButton;

    private JTextField PauseField;

    private javax.swing.JRadioButton PlayPcapButton;

    private javax.swing.ButtonGroup buttonGroup;

    private javax.swing.JCheckBox continuousRTPPlay;

    private javax.swing.JTextField filePath;

    private javax.swing.JRadioButton RemoteTarget;

    private JTextField RemoteTargetField;

    private javax.swing.JRadioButton NewDialogButton;
}
