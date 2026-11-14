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

import sipinspector.ScenarioEntries.ScenarioEntry;
import java.util.Vector;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class SIPScenario
{

    public SIPScenario()
    {
        entries = new Vector<ScenarioEntry>(0, 1);
    }

    public static ScenarioEntry getScenarioPos(int index)
    {
        return entries.elementAt(index);
    }

    static public int getEntriesCount()
    {
        return entries.size();
    }

    public void appendNewEntry(ScenarioEntry entry, JTree tree)
    {
        int list[] = tree.isSelectionEmpty() ? null : tree.getSelectionRows();
        if (list != null)
        {
            entries.insertElementAt(entry, list[0]);
        }
        else
        {
            entries.add(entry);
        }
    }

    public void replaceEntryAtPosition(ScenarioEntry entry,
        int index)
    {
        int size = entries.size();
        if (index < size)
        {
            entries.remove(index);
            entries.insertElementAt(entry, index);
        }
    }

    static public void initializeStaticCounters()
    {
        int i;
        int cnt = entries.size();

        for (i = 0; i < cnt; i++)
        {
            entries.get(i).resetCounts();
        }
    }

    public void setEntriesIndices()
    {
        int i;
        int cnt = entries.size();

        for (i = 0; i < cnt; i++)
        {
            entries.get(i).setIndex(i);
        }
    }

    public void updateTreeFromScenario(JTree msgTree)
    {
        int list[] = msgTree.isSelectionEmpty() ? null : 
            msgTree.getSelectionRows();
        DefaultMutableTreeNode root =
            (DefaultMutableTreeNode)msgTree.getModel().getRoot();
        root.removeAllChildren();

        DefaultTreeModel model = (DefaultTreeModel)msgTree.getModel();
        DefaultMutableTreeNode node;

        int totalCount = getEntriesCount();
        int i;
        for (i = 0; i < totalCount; i++)
        {
            MsgJTreeEntry entry =
                new MsgJTreeEntry(getScenarioPos(i).getTreeText());

            node = new DefaultMutableTreeNode(entry);
            model.insertNodeInto(node, root, model.getChildCount(root));
        }
        model.nodeStructureChanged((TreeNode)root);
        
        if (list != null)
        {
            msgTree.setSelectionRow(list[0]);
        }

    }

    public void removeEntry(int index)
    {
        if (index >= 0 && index < entries.size())
        {
            entries.remove(index);
        }
    }

    public void moveEntryUp(int index)
    {
        if (index >= 0 && index < entries.size())
        {
            ScenarioEntry entry = entries.elementAt(index);
            entries.remove(index);
            entries.insertElementAt(entry, index - 1);
        }
    }

    public void moveEntryDown(int index)
    {
        if (index >= 0 && index < entries.size())
        {
            ScenarioEntry entry = entries.elementAt(index);
            entries.remove(index);
            entries.insertElementAt(entry, index + 1);
        }
    }

    public void RemoveAllScenarioEntries(JTree MsgTree)
    {
        getEntries().removeAllElements();
        updateTreeFromScenario(MsgTree);
        setEntriesIndices();
        MsgTree.clearSelection();
    }

    public Vector<ScenarioEntry> getEntries()
    {
        return entries;
    }

    private static Vector<ScenarioEntry> entries;

}



