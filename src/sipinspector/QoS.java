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


import java.awt.event.KeyEvent;
import java.math.BigInteger;
import java.text.ParseException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.text.MaskFormatter;

import net.miginfocom.swing.MigLayout;

public class QoS extends JDialog
{
    public QoS(SIPInspectorMainFrame owner)
    {
        super(owner, true);
        initComponents();
    }
    
    private void initComponents()
    {
        String qosString;
        String tmpBinVal;
        
        setTitle("Expedited Forwarding");
        setResizable(false);
        JPanel ExpFwd = new javax.swing.JPanel(new MigLayout("fill"));

        ExpFwd.add(new JLabel("QoS DSCP in Hex:"));
        try
        {
            if ((qosString = GlobalSettings.getSetting("QoS")) == null)
            {

                dscpVal = new JFormattedTextField(new MaskFormatter("HH"));

            }
            else
            {
                dscpVal = new JFormattedTextField(new MaskFormatter("HH"));
                dscpVal.setText(qosString);
            }
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }
        
        dscpVal.setColumns(2);
        dscpVal.setEnabled(true);
        dscpVal.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                dscpValKeyReleased(evt);
            }
        });
        ExpFwd.add(dscpVal, "growx");
        BigInteger bi = new BigInteger(dscpVal.getText(), 16);
        tmpBinVal = bi.toString(2);
        while (tmpBinVal.length() < 8)
        {
            tmpBinVal = "0" + tmpBinVal;
        }
        binValue = new JLabel(tmpBinVal);
        binValue.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        ExpFwd.add(binValue, "growx, wrap");   

        OKButton = new JButton("OK");
        ExpFwd.add(OKButton, "center, spanx, split 2, gaptop 10");
        OKButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                OKButtonActionPerformed(evt);
            }
        });

        CloseButton = new javax.swing.JButton("Close");
        ExpFwd.add(CloseButton, "align center, gaptop 10");
        CloseButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CloseButtonActionPerformed(evt);
            }
        });
        
        add(ExpFwd);
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        pack();

    }
    
    private void OKButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        String val = dscpVal.getText().trim();
        while (val.length() < 2)
        {
            val = "0" + val;
        }
        dscpVal.setText(val);
        GlobalSettings.setSetting("QoS", val);
        dispose();
    }
    
    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
       dispose();
    }
    
    private void dscpValKeyReleased(KeyEvent evt)
    {
        if ((evt.getKeyCode() >= KeyEvent.VK_0 && evt.getKeyCode() <= KeyEvent.VK_9)
            || (evt.getKeyCode() >= KeyEvent.VK_A && evt.getKeyCode() <= KeyEvent.VK_V))
        {
            String tmpVal = dscpVal.getText().trim();
            BigInteger bi = new BigInteger(tmpVal, 16);
            String tmpBinVal = bi.toString(2);
            while (tmpBinVal.length() < 8)
            {
                tmpBinVal = "0" + tmpBinVal;
            }
            binValue.setText(tmpBinVal);
        }
    }
    
        
    private JFormattedTextField dscpVal;
    
    private JLabel binValue;
    
    private JButton OKButton;
    
    private JButton CloseButton;


}
