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
 *
 * Created on May 14, 2010, 4:30:32 PM
 */
package sipinspector;

import javax.swing.JButton;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import sipinspector.Utils.IPAddressValidator;

/**
 *
 * @author Zarko Coklin
 */
public class RemoteRTP extends javax.swing.JDialog
{

    /** Creates new form RemoteRTP */
    public RemoteRTP(SIPInspectorMainFrame owner)
    {
        super(owner, true);
        mf = owner;
        initComponents();
    }

    private void initComponents()
    {

        setTitle("Remote RTP port...");
        setMinimumSize(new java.awt.Dimension(220, 130));
        setResizable(false);
        setLayout(new MigLayout("", "[left] [grow]", ""));

        add(new JLabel("Remote RTP Address:"));
        RTP_Remote_Address = new javax.swing.JTextField();
        if (GlobalSettings.getSetting("RTP_Remote_Address") != null)
        {
            RTP_Remote_Address.setText(GlobalSettings.getSetting("RTP_Remote_Address"));
        }
        RTP_Remote_Address.setInputVerifier(new IPAddressValidator());

        add(RTP_Remote_Address, "grow, wrap");

        add(new JLabel("Remote RTP Port:"));
        RTP_Remote_Port = new javax.swing.JTextField();
        if (GlobalSettings.getSetting("RTP_Remote_Port") != null)
        {
            RTP_Remote_Port.setText(GlobalSettings.getSetting("RTP_Remote_Port"));
        }

        add(RTP_Remote_Port, "grow,wrap");

        add(new JLabel("Group RTP packets:"));
        NumRTPPackets = new javax.swing.JTextField(GlobalSettings.getSetting("numRTPPackets"));
        add(NumRTPPackets, "grow,wrap");

        OKButton = new JButton("OK");
        add(OKButton, "align center, gaptop 10");
        OKButton.addActionListener(new java.awt.event.ActionListener()
        {

            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                OKButtonActionPerformed(evt);
            }
        });

        CloseButton = new JButton("Close");
        add(CloseButton, "align center");
        CloseButton.addActionListener(new java.awt.event.ActionListener()
        {

            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CloseButtonActionPerformed(evt);
            }
        });
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        pack();
    }

    private void OKButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        GlobalSettings.setSetting("RTP_Remote_Address", RTP_Remote_Address.getText());
        GlobalSettings.setSetting("RTP_Remote_Port", RTP_Remote_Port.getText());
        GlobalSettings.setSetting("numRTPPackets", NumRTPPackets.getText());
        dispose();
    }

    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        dispose();
    }

    private javax.swing.JTextField RTP_Remote_Port;

    private javax.swing.JTextField RTP_Remote_Address;

    private javax.swing.JTextField NumRTPPackets;

    private JButton OKButton;

    private JButton CloseButton;

    private SIPInspectorMainFrame mf;
}
