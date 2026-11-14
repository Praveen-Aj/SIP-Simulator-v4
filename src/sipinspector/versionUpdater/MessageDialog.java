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

import javax.swing.JFrame;
import net.miginfocom.swing.MigLayout;
import sipinspector.SIPInspectorMainFrame;

/**
 *
 * @author Zarko Coklin
 */
public class MessageDialog extends javax.swing.JFrame
{
    /** Creates new form UpdateInfoDialog */
    public MessageDialog(JFrame mainFrame)
    {
        this.mainFrame = mainFrame;
        this.setLocationRelativeTo(null);
        SIPInspectorMainFrame.setIcon(this);
        initComponents();
        if (CheckUpdate.getNewMessage() != null)
        {
            messageArea.setText(CheckUpdate.getNewMessage());
            this.setVisible(true);
        }
        else
        {
            dispose();
            mainFrame.setVisible(true);
        }
    }

    private void initComponents() {

        OKButton = new javax.swing.JToggleButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        messageArea = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Message to users....");
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });

        OKButton.setText("OK");
        OKButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OKButtonActionPerformed(evt);
            }
        });

        messageArea.setBackground(new java.awt.Color(212, 208, 200));
        messageArea.setEditable(false);
        messageArea.setFont(new java.awt.Font("Arial", 0, 12)); // NOI18N
        messageArea.setForeground(new java.awt.Color(51, 51, 255));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        jScrollPane1.setViewportView(messageArea);

        setLayout(new MigLayout(""));

        add(jScrollPane1, "w 400!, h 200!, wrap");
        add(OKButton, "center");

        pack();
    }

    private void OKButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_OKButtonActionPerformed
    {//GEN-HEADEREND:event_OKButtonActionPerformed
        dispose();
        mainFrame.setVisible(true);
    }//GEN-LAST:event_OKButtonActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowClosed
    {//GEN-HEADEREND:event_formWindowClosed
        mainFrame.setVisible(true);
    }//GEN-LAST:event_formWindowClosed

    private javax.swing.JToggleButton OKButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea messageArea;
    private JFrame mainFrame;
}
