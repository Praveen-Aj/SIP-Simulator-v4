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
public class ConditionEntry extends ScenarioEntry
{

    public enum RELATION
    {

        LESS,
        LESS_OR_EQUAL,
        EQUAL,
        NOT_EQUAL,
        GREATER_OR_EQUAL,
        GREATER,
        HAS
    };

    public ConditionEntry(String content)
    {
        super(TYPE.CONDITION, DIRECTION.NONE);
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
                if (str.startsWith("condition=") == true)
                {
                    pos = str.indexOf('=');
                    if (pos == -1)
                    {
                        return;
                    }
                    parseCondition(str.substring(pos + 1));
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

    private void parseCondition(String expression)
    {
        String[] strArray = new String[6];
        strArray = expression.split(" ", 6);
        variable = strArray[1].substring(1);
        relationStr = strArray[2];
        relation = getRelation(strArray[2]);
        variableValue = strArray[3].substring(0, strArray[3].length() - 1);
        label = strArray[5];
    }

    private RELATION getRelation(String relationStr)
    {
        if (relationStr.equals("<") == true)
        {
            return RELATION.LESS;
        }
        else if (relationStr.equals("<=") == true)
        {
            return RELATION.LESS_OR_EQUAL;
        }
        else if (relationStr.equals("==") == true)
        {
            return RELATION.EQUAL;
        }
        else if (relationStr.equals("!=") == true)
        {
            return RELATION.NOT_EQUAL;
        }
        else if (relationStr.equals(">=") == true)
        {
            return RELATION.GREATER_OR_EQUAL;
        }
        else if (relationStr.equals(">") == true)
        {
            return RELATION.GREATER;
        }
        else
        {
            return RELATION.HAS;
        }
    }

    @Override
    public boolean processEntry(SIPCall call,
        ScenarioProgressDialog dialog) throws ScenarioException
    {
        super.increaseExpectedCnt();
        dialog.updateStats(call.getScenarioPos());
        String keyword = new String("[$" + variable + "]");
        String storedValue = call.getAndStoreValue(keyword);
        if (storedValue == null)
        {
            throw new ScenarioException();
        }
        boolean conditionMatched = false;
        int result;
        if (storedValue.length() < variableValue.length())
        {
            result = -1;
        }
        else if (storedValue.length() > variableValue.length())
        {
            result = 1;
        }
        else
        {
            result = storedValue.compareTo(variableValue);
        }
        
        switch (relation)
        {

            case LESS:
                if (result < 0)
                {
                    conditionMatched = true;
                }
                break;
            case LESS_OR_EQUAL:
                if (result <= 0)
                {
                    conditionMatched = true;
                }
                break;
            case EQUAL:
                if (result == 0)
                {
                    conditionMatched = true;
                }
                break;
            case NOT_EQUAL:
                if (result != 0)
                {
                    conditionMatched = true;
                }
                break;
            case GREATER_OR_EQUAL:
                if (result >= 0)
                {
                    conditionMatched = true;
                }
                break;
            case GREATER:
                if (result > 0)
                {
                    conditionMatched = true;
                }
                break;
            case HAS:
                if (storedValue.contains(variableValue) == true)
                {
                    conditionMatched = true;
                }
        }

        if (conditionMatched == true)
        {
            // goto to label, otherwise move to next scenario
            call.gotoLabel(label);
        }
        else
        {
            call.nextEntry();
        }
        return true;

    }

    @Override
    public String getTreeText()
    {
        return (" ---- " + getShortCode());
    }

    @Override
    public String getScenarioScreenText()
    {
        String txt = new String(System.getProperty("line.separator") +
            "----------" + System.getProperty("line.separator") +
            "condition=if (" + variable + " " + relationStr + " " +
            variableValue + ") goto " + label +
            System.getProperty("line.separator"));
        return txt;
    }

    @Override
    public String getShortCode()
    {
        return ("if (" + variable + " " + relationStr + " " +
            variableValue + ") goto " + label);
    }

    @Override
    public String getScenarioProgressScreenText()
    {
        return getTreeText();
    }

    private RELATION relation;

    private String relationStr;

    private String variable;

    private String variableValue;

    private String label;
}
