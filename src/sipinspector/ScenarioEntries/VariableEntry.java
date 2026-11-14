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
package sipinspector.ScenarioEntries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import javax.swing.JOptionPane;
import sipinspector.SIPCall;
import sipinspector.Utils.SIPParser;
import sipinspector.ScenarioProgressDialog;

/**
 *
 * @author Zarko Coklin
 */
public class VariableEntry extends ScenarioEntry
{
    public VariableEntry(String content)
    {
        super(TYPE.VARIABLE, DIRECTION.NONE);
        String str;
        int pos = 0;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try
        {
            while ((str = reader.readLine()) != null)
            {
                if (str.startsWith("//") == true)
                {
                    continue;
                }
                if (str.startsWith("set_Variable=") == true)
                {
                    pos = str.indexOf('=');
                    if (pos == -1)
                    {
                        return;
                    }
                    parseVariableCommand(str.substring(pos + 1));
                    break;
                }
            }
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(null,
                    e,
                    "Update Messages",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void parseVariableCommand(String cmd)
    {
        String[] strArray = new String[3];
        strArray = cmd.split(" ", 3);
        variableName = strArray[0];

        /* Passed in headername and expression have quotes. So, they have to
         * be removed at this point.
         */
        headerName = stripOffQuotes(strArray[1]);

        expression = stripOffQuotes(strArray[2]);
    }

    private String stripOffQuotes(String txt)
    {
        if (txt.charAt(0) == '"' && txt.charAt(txt.length() - 1) == '"')
        {
            return txt.substring(1, txt.length() - 1);
        }
        return txt;
    }

    @Override
    public boolean processEntry(SIPCall call,
            ScenarioProgressDialog dialog) throws ScenarioException
    {
        String value;
        String lastRcvdMsg = call.getLastRcvdExpectedMsg();
        String srcTxt;
        int pos = 0;

        pos = expression.indexOf('=');
        if (pos == 0)
        {
            call.storeVariableValue(variableName, expression.substring(pos + 1));
        }
        else
        {
            pos = expression.indexOf('*');
            if (pos == -1)
            {
                JOptionPane.showMessageDialog(dialog,
                        "Expected to find '*' somewhere in expression",
                        "Variable Entry",
                        JOptionPane.ERROR_MESSAGE);
                throw new ScenarioException();
            }

            if (headerName != null && headerName.equals("-") == false)
            {
                if (headerName.equals("startline") == true)
                {
                    srcTxt = SIPParser.getStartline(lastRcvdMsg);
                }
                else
                {
                    /* To cover cases when people simply put "From"
                     * and not "From:"
                     */
                    if (headerName.contains(":") == false)
                    {
                        headerName += ":";
                    }
                    srcTxt = SIPParser.getHeaderValue(lastRcvdMsg, headerName);
                }
            }
            else
            {
                srcTxt = call.getLastRcvdExpectedMsg();
            }
            value = SIPParser.getStarExpression(srcTxt, expression);
            call.storeVariableValue(variableName, value);
        }

        super.increaseExpectedCnt();
        dialog.updateStats(this);
        call.nextEntry();
        return true;

    }

    @Override
    public String getTreeText()
    {
        return (" ---- set " + variableName + "=(\"" + headerName +
                "\",\"" + expression + "\")");
    }

    @Override
    public String getScenarioScreenText()
    {
        String txt = new String(System.getProperty("line.separator") +
            "----------" + System.getProperty("line.separator") +
            "set_Variable=" + variableName + " \"" +
            headerName + "\" \"" + expression + "\"" +
            System.getProperty("line.separator"));
        return txt;
    }

    @Override
    public String getShortCode()
    {
        return (variableName + "=(\"" + headerName + "\",\"" +
                expression + "\")");
    }

    @Override
    public String getScenarioProgressScreenText()
    {
        return new String(" ---- " + getShortCode());
    }

    public String getName()
    {
        return variableName;
    }

    private String variableName;

    private String headerName;

    private String expression;
}
