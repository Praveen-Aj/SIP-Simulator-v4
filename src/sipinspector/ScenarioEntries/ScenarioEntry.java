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
abstract public class ScenarioEntry
{
    public enum TYPE
    {
        NONE,
        REQUEST,
        RESPONSE,
        COMMAND_PAUSE,
        COMMAND_PLAYPCAP,
        VARIABLE,
        LABEL,
        GOTO,
        CONDITION,
        SET_REMOTE_TARGET,
        NEW_DIALOG,
        ASSERT_DTMF,
        ASSERT_PT
    };

    public enum DIRECTION
    {
        NONE,
        IN,
        OUT

    };

    public ScenarioEntry()
    {
        type = TYPE.NONE;
        direction = DIRECTION.NONE;
        resetCounts();
        index = -1;
    }

    public ScenarioEntry(TYPE type, DIRECTION dir)
    {
        this.type = type;
        direction = dir;
        resetCounts();
        index = -1;
    }

    static public ScenarioEntry parse(String content)
    {
        String str;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try
        {
            while ((str = reader.readLine()) != null)
            {
                if (str.startsWith("---------->") == true ||
                        str.startsWith("<----------"))
                {
                    return new SIPMessageEntry(content);
                }
                else if (str.startsWith("cmd_Pause=") == true)
                {
                    return new CommandPauseEntry(content);
                }
                else if (str.startsWith("cmd_PlayPcap=") == true)
                {
                    return new CommandPlayPcapEntry(content);
                }
                else if (str.startsWith("set_Variable=") == true)
                {
                    return new VariableEntry(content);
                }
                else if (str.startsWith("label=") == true)
                {
                    return new LabelEntry(content);
                }
                else if (str.startsWith("goto=") == true)
                {
                    return new GotoEntry(content);
                }
                else if (str.startsWith("condition=") == true)
                {
                    return new ConditionEntry(content);
                }
                else if (str.startsWith("cmd_setRemote=") == true)
                {
                    return new SetRemoteTargetEntry(content);
                }
                else if (str.startsWith("cmd_NewDialog") == true)
                {
                    return new NewDialog(content);
                }
                else if (str.startsWith("cmd_AssertDtmf=") == true || str.startsWith("assert_dtmf=") == true || str.startsWith("assert_dtmf:") == true)
                {
                    return new AssertDtmfEntry(content);
                }
                else if (str.startsWith("cmd_AssertPt=") == true || str.startsWith("assert_pt=") == true || str.startsWith("assert_pt:") == true)
                {
                    return new AssertPtEntry(content);
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

        return null;
    }


    abstract public boolean processEntry(SIPCall call,
            ScenarioProgressDialog dialog) throws ScenarioException;

    abstract public String getTreeText();

    abstract public String getScenarioScreenText();

    abstract public String getShortCode();

    abstract public String getScenarioProgressScreenText();
    
    public void stop()
    {
        return;
    }

    public ScenarioEntry.DIRECTION getDirection()
    {
        return direction;
    }

    public void setDirection(ScenarioEntry.DIRECTION dir)
    {
        direction = dir;
    }

    public ScenarioEntry.TYPE getType()
    {
        return type;
    }

    public void setType(ScenarioEntry.TYPE msgType)
    {
        this.type = msgType;
    }

    public boolean getOptionalFlag()
    {
        return false;
    }

    public void increaseExpectedCnt()
    {
        expectedCnt++;
    }

    public int getExpectedCnt()
    {
        return expectedCnt;
    }

    public void increaseUnexpectedCnt()
    {
        unexpectedCnt++;
    }

    public int getUnexpectedCnt()
    {
        return unexpectedCnt;
    }

    public void resetCounts()
    {
        expectedCnt = 0;
        unexpectedCnt = 0;
    }

    public void setIndex(int value)
    {
        index = value;
    }

    public int getIndex()
    {
        return index;
    }


    private TYPE type;

    private DIRECTION direction;

    private int expectedCnt;

    private int unexpectedCnt;

    private int index;

}
