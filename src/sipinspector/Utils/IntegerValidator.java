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
package sipinspector.Utils;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/**
 *
 * @author Zarko Coklin
 */
public class IntegerValidator extends InputVerifier
{

    @Override
    public boolean verify(JComponent input)
    {
        JTextField txtField = (JTextField)input;

        if (txtField.isVisible() == true)
        {
            try
            {
                Integer.parseInt(txtField.getText());
            }
            catch (NumberFormatException exc)
            {
                JOptionPane.showMessageDialog(input,
                    "The value must be an integer value",
                    "Value validation",
                    JOptionPane.ERROR_MESSAGE);
                return false;

            }
        }
        return true;
    }
}
