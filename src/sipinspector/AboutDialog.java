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
package sipinspector;

import java.awt.Cursor;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Zarko Coklin
 */
public class AboutDialog extends javax.swing.JDialog {

    /** Creates new form AboutDialog */
    public AboutDialog(java.awt.Frame parent) {
        super(parent, true);
        initComponents();
        this.setLocationRelativeTo(parent);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     */
    private void initComponents() {

        setTitle("About...");
        setMinimumSize(new java.awt.Dimension());
        setResizable(false);
        setLayout(new MigLayout("fill", "[left] [left] [grow]", ""));

        add(new JLabel("SIP Simulator:"));
        add(new JLabel("(version: "
                + SIPInspectorMainFrame.getSIPInspectorVersion() + ")"), "grow, wrap");

        add(new JLabel("Maintained by:"));
        add(new JLabel("SIP Support Team"), "grow, wrap");

        add(new JLabel("Original author:"));
        add(new JLabel("Žarko Coklin"), "grow, wrap");

        add(new JLabel("Contact:"));
        ContactValueLabel = new JLabel("Internal tool - see SIP Simulator documentation");
        ContactValueLabel.setCursor(Cursor.getDefaultCursor());
        add(ContactValueLabel, "grow, wrap");

        add(new JLabel("Web site:"));
        WebsiteValueLabel = new JLabel("Not applicable (internal distribution)");
        WebsiteValueLabel.setCursor(Cursor.getDefaultCursor());
        add(WebsiteValueLabel, "grow, wrap");

        add(new JLabel("Mailing list:"));
        MailListValueLabel = new JLabel("Not applicable");
        MailListValueLabel.setCursor(Cursor.getDefaultCursor());
        add(MailListValueLabel, "grow, wrap");

        add(new JLabel("Project:"));
        add(new JLabel("SIP Simulator v4 (open-source fork of SIP Inspector)"), "grow, wrap");

        add(new JLabel("License:"));
        add(new JLabel("GNU General Public License v3 (or later)"), "grow, wrap");

        CloseButton = new JButton("Close");
        add(CloseButton, "spanx, center, gaptop 10");
        CloseButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CloseButtonActionPerformed(evt);
            }
        });

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        pack();
    }

    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt) {
        dispose();
    }

    private void WebsiteValueLabelMousePressed(java.awt.event.MouseEvent evt) {
        try {
            java.awt.Desktop.getDesktop().browse(new URI("http://sites.google.com/site/sipinspectorsite/"));
        } catch (URISyntaxException ex) {
        } catch (IOException ex) {
        }
    }

    private void MailListValueLabelMousePressed(java.awt.event.MouseEvent evt) {
        try {
            java.awt.Desktop.getDesktop().browse(new URI("http://groups.google.com/group/sip-inspector"));
        } catch (URISyntaxException ex) {
        } catch (IOException ex) {
        }
    }

    private void ContactValueLabelMousePressed(java.awt.event.MouseEvent evt) {
        try {
            java.awt.Desktop.getDesktop().mail(new URI("mailto:sip-inspector@googlegroups.com"));
        } catch (URISyntaxException ex) {
        } catch (IOException ex) {
        }
    }

    private javax.swing.JButton CloseButton;
    private javax.swing.JLabel ContactValueLabel;
    private javax.swing.JLabel MailListValueLabel;
    private javax.swing.JLabel WebsiteValueLabel;
}
