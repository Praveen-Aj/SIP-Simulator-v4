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
import sipinspector.ScenarioProgressDialog;

/**
 *
 * @author Zarko Coklin
 */
public class LabelEntry extends ScenarioEntry
{
    public LabelEntry(String content)
    {
        super(TYPE.LABEL, DIRECTION.NONE);
        String str;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try
        {
            while ((str = reader.readLine()) != null)
            {
                if (str.startsWith("//") == true)
                {
                    continue;
                }
                if (str.startsWith("label=") == true)
                {
                    setLabelName(str);
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

    public void setLabelName(String arg)
    {
        int pos = arg.indexOf('=');

        if (pos == -1)
        {
            name = new String("");
        }
        else
        {
            name = arg.substring(pos + 1);
        }
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean processEntry(SIPCall call, ScenarioProgressDialog dialog)
        throws ScenarioException
    {
        super.increaseExpectedCnt();
        dialog.updateStats(this);
        call.nextEntry();
        return true;
    }

    @Override
    public String getTreeText()
    {
        return getShortCode();
    }

    @Override
    public String getScenarioScreenText()
    {
        String txt = new String(System.getProperty("line.separator") +
            "----------" + System.getProperty("line.separator") +
            "label=" + name + System.getProperty("line.separator"));
        return txt;
    }

    @Override
    public String getShortCode()
    {
        return (" " + name + ":");
    }

    @Override
    public String getScenarioProgressScreenText()
    {
        return getShortCode();
    }

    private String name;
}
