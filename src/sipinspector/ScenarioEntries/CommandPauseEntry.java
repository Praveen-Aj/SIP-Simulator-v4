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
public class CommandPauseEntry extends ScenarioEntry
{

    public CommandPauseEntry(String content)
    {
        super(TYPE.COMMAND_PAUSE, DIRECTION.NONE);
        String str;
        pause_ms = 0;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try
        {
            while ((str = reader.readLine()) != null)
            {
                if (str.startsWith("//") == true)
                {
                    continue;
                }
                if (str.startsWith("cmd_Pause=") == true)
                {
                    setPauseValue(str);
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

    private void setPauseValue(String command)
    {
        int pos = command.indexOf('=');

        if (pos == -1)
        {
            pause_ms = 0;
        }
        else
        {
            try
            {
                pause_ms = Integer.parseInt(command.substring(pos + 1));
            }
            catch (NumberFormatException exc)
            {
                pause_ms = 0;
            }
        }
    }

    @Override
    public boolean processEntry(SIPCall call,
        ScenarioProgressDialog dialog) throws ScenarioException
    {
        /* Make sure pause only once started, otherwise stats
         * will display wrong number for a pause
         */
        if (call.getPauseActivated() == false)
        {
            call.setPauseActivated();
            call.setPauseDuration(pause_ms);
            super.increaseExpectedCnt();
            dialog.updateStats(this);
        }
        return false;
    }

    @Override
    public String getTreeText()
    {
        return (" ---- Pause[ms]=" + Integer.toString(pause_ms));
    }

    @Override
    public String getScenarioScreenText()
    {
        String txt = new String(System.getProperty("line.separator") +
            "----------" + System.getProperty("line.separator") +
            "cmd_Pause=" + Integer.toString(pause_ms) +
            System.getProperty("line.separator"));
        return txt;
    }

    @Override
    public String getShortCode()
    {
        return "Pause[ms]=" + String.valueOf(pause_ms);
    }

    @Override
    public String getScenarioProgressScreenText()
    {
        return new String(" ---- " + getShortCode());
    }

    private int pause_ms;
}
