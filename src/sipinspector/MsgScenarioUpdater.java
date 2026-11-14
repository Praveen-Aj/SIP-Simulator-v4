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

/**
 *
 * @author Zarko Coklin
 */
import sipinspector.ScenarioEntries.ScenarioEntry;
import java.io.*;
import java.util.Arrays;
import javax.swing.*;


public class MsgScenarioUpdater extends SwingWorker<SIPScenario, Object>
{
    MsgScenarioUpdater(SIPInspectorMainFrame mFrame,
            String text)
    {
        mySipScenario = mFrame.getScenario();
        myMsgTree = mFrame.getTree();
        myPositions = myMsgTree.isSelectionEmpty() ? null : myMsgTree.getSelectionRows();
        if (myPositions != null)
        {
            Arrays.sort(myPositions);
        }
        content = text;
        textArea = mFrame.getMessageTextArea();
        carretPos = textArea.getCaretPosition();
    }

    @Override
    protected SIPScenario doInBackground() throws Exception
    {
        // At this point read messages from Text area and update
        // selected SIP messages
        String str;
        StringBuilder tmpStr = new StringBuilder(4096);
        ScenarioEntry newEntry;
        int posEntry = 0;
        int listCount = 0;
        boolean processFlag = false;

        if (myPositions != null)
        {
            if (myPositions[posEntry] == 0)
            {
                posEntry++;
            }
            listCount = myPositions.length;
        }
        /* Find chunks in between "---------" and ("---------" of the end)
         * and feed ScenarioEntry parse function. It is important to pass
         * starting "---------" sequence cause it tells the direction.
         */
        BufferedReader reader = new BufferedReader(new StringReader(content));
        try
        {
            while ((str = reader.readLine()) != null)
            {
                /* Skip all lines prior first message*/
                //if (processFlag == false && str.equals("") == true)
                if (processFlag == false && str.contains("---------") == false)
                {
                    continue;
                }
                if (str.startsWith("---------", 1) == true)
                {
                    if (processFlag == true)
                    {
                        newEntry = ScenarioEntry.parse(tmpStr.toString());

                        if (posEntry < listCount)
                        {
                            mySipScenario.replaceEntryAtPosition(newEntry,
                                    myPositions[posEntry] - 1);
                            posEntry++;
                        }
                        else
                        {
                            mySipScenario.appendNewEntry(newEntry, myMsgTree);
                        }
                        tmpStr.setLength(0);
                        tmpStr.append(str).append("\r\n");
                        continue;
                    }
                    else
                    {
                        processFlag = true;
                    }
                }
                tmpStr.append(str).append("\r\n");
            }
            // The end of scenario text may not have a "----------" mark
            if (processFlag == true)
            {
                newEntry = ScenarioEntry.parse(tmpStr.toString());

                if (posEntry < listCount)
                {
                    mySipScenario.replaceEntryAtPosition(newEntry,
                            myPositions[posEntry] - 1);
                    posEntry++;
                }
                else
                {
                    mySipScenario.appendNewEntry(newEntry, myMsgTree);
                }
                tmpStr.setLength(0);
                tmpStr.append(str).append("\r\n");
            }
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(null,
                    e,
                    "Update Messages",
                    JOptionPane.ERROR_MESSAGE);
        }
        return mySipScenario;
    }

    @Override
    protected void done()
    {
        mySipScenario.updateTreeFromScenario(myMsgTree);
        mySipScenario.setEntriesIndices();
        myMsgTree.setSelectionRows(myPositions);
        textArea.setText(content);
        textArea.setCaretPosition(carretPos);
    }
    private SIPScenario mySipScenario;

    private JTree myMsgTree;

    private String content;

    private int[] myPositions;

    private JTextArea textArea;

    private int carretPos;

};

