/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and
 *  troubleshoot SIP signaling.
 *
 *  Copyright (C) 2010  Zarko Coklin (zarko_coklin at yahoo dot com)
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Zarko Coklin
 */
public class SDPBodyCalc extends JDialog
{

    public SDPBodyCalc(SIPInspectorMainFrame owner)
    {
        super(owner, true);
        initComponents();
        caretPos = 0;
    }

    private void initComponents()
    {

        setTitle("SDP body calculator...");
        setMinimumSize(new java.awt.Dimension(400, 350));
        setResizable(false);
        setLayout(new MigLayout("fill", "", "[grow] []"));

        myTextArea = new JTextArea("");
        myTextArea.addKeyListener(new java.awt.event.KeyAdapter()
        {

            @Override
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                bodyKeyReleased(evt);
            }

            @Override
            public void keyTyped(java.awt.event.KeyEvent evt)
            {
                bodyKeyTyped(evt);
            }
        });

        myTextArea.setEditable(true);

        add(new JScrollPane(myTextArea), "grow,wrap");

        bodySize = new JLabel("Content-Length:");

        add(bodySize, "center");

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        pack();
    }

    private void bodyKeyReleased(KeyEvent evt)
    {
        String bodyString;
        String finalBody;

        if (evt.isControlDown() == true &&
                (evt.getKeyCode() != KeyEvent.VK_X &&
                evt.getKeyCode() != KeyEvent.VK_V))
        {
            return;
        }
        switch (evt.getKeyCode())
        {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_PAGE_UP:
            case KeyEvent.VK_PAGE_DOWN:
            case KeyEvent.VK_END:
            case KeyEvent.VK_HOME:
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_CONTROL:
                return;
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
                caretPos--;
                break;
            default:
                break;
        }
        bodyString = myTextArea.getText();
        bodyString = bodyString.replace("«" + System.getProperty("line.separator"), "^^");
        bodyString = bodyString.replace("«\r", "");
        bodyString = bodyString.replace("«\n", "");
        bodyString = bodyString.replace("«", "");
        bodyString = bodyString.replace("\n", "^^");
        bodySize.setText("Content-Length: " + String.valueOf(bodyString.length()) +
                "    ['«' represents '\\r\\n']");

        bodyString = bodyString.replace("^^", "«" + System.getProperty("line.separator"));

        myTextArea.setText(bodyString);
        if (evt.getKeyCode() == KeyEvent.VK_ENTER)
        {
            myTextArea.setCaretPosition(caretPos + System.getProperty("line.separator").length());
        }
        else
        {
            if (caretPos + 1 > bodyString.length())
            {
                myTextArea.setCaretPosition(bodyString.length());
            }
            else
            {
                myTextArea.setCaretPosition(caretPos + 1);
            }
        }
    }

    private void bodyKeyTyped(KeyEvent evt)
    {
        caretPos = myTextArea.getCaretPosition();
    }

    private JTextArea myTextArea;

    private JLabel bodySize;

    private int caretPos;
}
