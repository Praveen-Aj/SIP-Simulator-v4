/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and 
 *  troubleshoot SIP signaling. 
 * 
 *  Copyright (C) 2008   Zarko Coklin
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

import java.awt.Component;
import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Font;

import sipinspector.Utils.EmojiIcon;

/****

@author Zarko Coklin
 */
public class MyTreeCellRenderer extends DefaultTreeCellRenderer
{

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus)
    {
        JLabel resultComponent = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (tree.getModel().getRoot() == value)
        {
            if (tree.getModel().getChildCount(tree.getModel().getRoot()) == 0)
            {
                resultComponent.setIcon(EmojiIcon.getStatusWarningIcon());
            }
            else
            {
                resultComponent.setIcon(EmojiIcon.getStatusOkIcon());
            }
            tree.expandRow(0);
        }
        else
        {
            resultComponent.setIcon(null);
            resultComponent.setFont(new Font("Courier", Font.PLAIN, 12));
        }

        return resultComponent;
    }
}
