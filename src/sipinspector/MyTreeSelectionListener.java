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

import sipinspector.ScenarioEntries.ScenarioEntry;
import java.util.Arrays;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

/**
 *
 * @author Zarko Coklin
 */
public class MyTreeSelectionListener implements TreeSelectionListener
{
    MyTreeSelectionListener(SIPInspectorMainFrame mFrame)
    {
        this.tree = mFrame.getTree();
        this.textArea = mFrame.getMessageTextArea();
    }

    public void valueChanged(TreeSelectionEvent e)
    {
        ScenarioEntry entry;
        String screen = new String();
        StringBuilder tmpScreen = new StringBuilder(4096);
        int cnt = tree.getSelectionCount();
        if (cnt == 0)
        {
            textArea.setText("");
            return;
        }
        int i;
        int list[] = tree.getSelectionRows();
        Arrays.sort(list);

        // Clear old text Area
        textArea.setText("");
        for (i = 0; i < cnt; i++)
        {
            if (list[i] == 0)
            {
                continue;
            }
            entry = SIPScenario.getScenarioPos(list[i] - 1);
            tmpScreen.append(entry.getScenarioScreenText());
        }
        if (System.getProperty("line.separator").equals("\r\n") == true)
        {
            screen = tmpScreen.toString().replaceAll("\r\n", "\n");
        }
        else
        {
            screen = tmpScreen.toString().replaceAll("\r\n",
                    System.getProperty("line.separator"));
        }
        textArea.setText(screen);
    }

    private JTree tree;

    private JTextArea textArea;
}
