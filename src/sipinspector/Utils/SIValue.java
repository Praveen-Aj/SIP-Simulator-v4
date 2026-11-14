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
package sipinspector.Utils;

/**
 *
 * @author Zarko Coklin
 */
public class SIValue
{
    public SIValue(String expression)
    {
        valueExpression = expression;
        resetCurrentValue();
    }

    public final void resetCurrentValue()
    {
        int pos;

        if ((pos = findOperationSign(valueExpression)) == -1)
        {
            currentValue = valueExpression;
        }
        else
        {
            currentValue = valueExpression.substring(0, pos);
        }
    }

    public static int findOperationSign(String keyword)
    {
        int pos = -1;

        for (pos = 0; pos < keyword.length(); pos++)
        {
            switch (keyword.charAt(pos))
            {
                case '+':
                case '-':
                    return pos;
                default:
                    break;
            }
        }
        return -1;
    }

    public static int getIntValue(String word)
    {
        int cnt;
        int wordLen;
        StringBuilder number = new StringBuilder(16);

        wordLen = word.length();
        for (cnt = 0; cnt < wordLen; cnt++)
        {
            if (Character.isDigit(word.charAt(cnt)) == true)
            {
                number.append(word.charAt(cnt));
            }
        }
        if (number.length() == 0)
        {
            return 0;
        }

        return Integer.parseInt(number.toString());
    }

    public void action()
    {
        int pos;

        if ((pos = findOperationSign(valueExpression)) == -1)
        {
            return;
        }

        try
        {
            if (valueExpression.charAt(pos) == '+')
            {
                currentValue = String.valueOf(Long.parseLong(currentValue)
                    + getIntValue(valueExpression.substring(pos + 1)));
            }
            else
            {
                currentValue = String.valueOf(Long.parseLong(currentValue)
                    - getIntValue(valueExpression.substring(pos + 1)));
            }
        }
        catch (NumberFormatException e)
        {
            return;
        }

    }

    public String getValue()
    {
        return currentValue;
    }

    private String valueExpression;

    private String currentValue;
}
