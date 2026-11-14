/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and 
 *  troubleshoot SIP signaling. 
 * 
 *  Copyright (C) 2011  Zarko Coklin (zarko_coklin at yahoo dot com)
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
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.swing.JOptionPane;

import sipinspector.SIPCall;
import sipinspector.ScenarioProgressDialog;

public class SetRemoteTargetEntry extends ScenarioEntry
{
    public SetRemoteTargetEntry(String content)
    {
        super(TYPE.SET_REMOTE_TARGET, DIRECTION.NONE);
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
                if (str.startsWith("cmd_setRemote=") == true)
                {
                    setRemoteTarget(str);
                    break;
                }
            }
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(null, e, "Update Messages",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setRemoteTarget(String command)
    {
        int pos = command.indexOf('=');

        if (pos == -1)
        {
            return;
        }
        else
        {
            String[] args = command.substring(pos + 1).split(":");

            if (args != null)
            {
                if (args.length > 0)
                {
                    remoteTargetAddress = args[0];
                }

                if (args.length > 1)
                {
                    remoteTargetPort = args[1];
                }
            }
        }
    }

    @Override
    public boolean processEntry(SIPCall call, ScenarioProgressDialog dialog)
        throws ScenarioException
    {
        try
        {
            call.setRemoteAddress(InetAddress.getByName(call.replaceKeyWords(remoteTargetAddress)));
            call.setRemotePort(Integer.parseInt(call.replaceKeyWords(remoteTargetPort)));
        }
        catch (UnknownHostException exc)
        {
            throw new ScenarioException("Failed to set remote target");
        }
        super.increaseExpectedCnt();
        dialog.updateStats(this);
        call.nextEntry();
        return true;
    }

    @Override
    public String getTreeText()
    {
        return (" ---- Remote=" + remoteTargetAddress + ":" + remoteTargetPort);
    }

    @Override
    public String getScenarioScreenText()
    {
        String txt = new String(System.getProperty("line.separator")
            + "----------" + System.getProperty("line.separator")
            + "cmd_setRemote=" + remoteTargetAddress + ":"
            + remoteTargetPort + System.getProperty("line.separator"));
        return txt;
    }

    @Override
    public String getShortCode()
    {
        return (" ---- Remote=" + remoteTargetAddress + ":" + remoteTargetPort);
    }

    @Override
    public String getScenarioProgressScreenText()
    {
        return (" ---- Remote=" + remoteTargetAddress + ":" + remoteTargetPort);
    }

    private String remoteTargetAddress = "";

    private String remoteTargetPort = "5060";
}
