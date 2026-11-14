/*
 * Message Search and Filter Panel
 * Provides search and filtering capabilities for SIP messages
 */
package sipinspector.UI;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import net.miginfocom.swing.MigLayout;
import sipinspector.Utils.EmojiIcon;

/**
 * Panel for searching and filtering SIP messages in the tree
 * @author SIP Support Team
 */
public class MessageSearchPanel extends JPanel {

    private JTextField searchField;
    private JComboBox<String> messageTypeFilter;
    private JComboBox<String> responseCodeFilter;
    private JButton searchButton;
    private JButton clearButton;
    private JTree messageTree;
    private Runnable onSearchPerformed;

    public MessageSearchPanel(JTree tree) {
        this.messageTree = tree;
        initComponents();
    }

    /** Called after a non-empty search/filter is applied, e.g. to switch to the tab showing the tree. */
    public void setOnSearchPerformed(Runnable callback) {
        this.onSearchPerformed = callback;
    }

    private void initComponents() {
        // 6 components -> 6 column templates: label, growing search field,
        // two filter combos, two fixed-width buttons. (A mismatched column
        // count here previously pushed the Search/Clear buttons off-screen.)
        setLayout(new MigLayout("fillx, insets 10", "[][grow][170!][210!][90!][90!]", "[]"));

        // Search field
        searchField = new JTextField();
        searchField.setToolTipText("Search in messages (press Enter)");
        searchField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });
        JLabel searchLabel = new JLabel("Search:", EmojiIcon.getSearchIcon(), SwingConstants.LEFT);
        add(searchLabel, "");
        add(searchField, "growx");
        
        // Message type filter
        String[] messageTypes = {
            "All Types",
            "INVITE",
            "REGISTER",
            "BYE",
            "ACK",
            "CANCEL",
            "OPTIONS",
            "SUBSCRIBE",
            "NOTIFY",
            "REFER",
            "MESSAGE",
            "UPDATE",
            "PRACK"
        };
        messageTypeFilter = new JComboBox<>(messageTypes);
        messageTypeFilter.setToolTipText("Filter by message type");
        messageTypeFilter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });
        add(messageTypeFilter, "");
        
        // Response code filter
        String[] responseCodes = {
            "All Responses",
            "1xx - Provisional",
            "2xx - Success",
            "3xx - Redirection",
            "4xx - Client Error",
            "5xx - Server Error",
            "6xx - Global Failure",
            "---",
            "100 Trying",
            "180 Ringing",
            "200 OK",
            "401 Unauthorized",
            "403 Forbidden",
            "404 Not Found",
            "407 Proxy Auth Required",
            "408 Request Timeout",
            "480 Temporarily Unavailable",
            "486 Busy Here",
            "487 Request Terminated",
            "500 Server Internal Error",
            "503 Service Unavailable",
            "603 Decline"
        };
        responseCodeFilter = new JComboBox<>(responseCodes);
        responseCodeFilter.setToolTipText("Filter by response code");
        responseCodeFilter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });
        add(responseCodeFilter, "");
        
        // Search button
        searchButton = new JButton("Search");
        searchButton.setToolTipText("Search messages");
        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });
        add(searchButton, "");
        
        // Clear button
        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear filters");
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearFilters();
            }
        });
        add(clearButton, "");
    }
    
    private void performSearch() {
        String searchText = searchField.getText().toLowerCase();
        String messageType = (String) messageTypeFilter.getSelectedItem();
        String responseCode = (String) responseCodeFilter.getSelectedItem();
        
        // Get tree model
        if (messageTree == null || messageTree.getModel() == null) {
            return;
        }
        
        DefaultTreeModel model = (DefaultTreeModel) messageTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        
        // Expand all nodes for search
        if (!searchText.isEmpty() || !messageType.equals("All Types") || !responseCode.equals("All Responses")) {
            expandAll(messageTree, new TreePath(root), true);

            // Collect matching nodes and SELECT them (previously the match result
            // was computed and then discarded, so search did nothing but expand
            // the tree). Selecting highlights matches via the standard tree
            // selection painter and lets the user step through them.
            java.util.List<TreePath> matches = new java.util.ArrayList<>();
            collectMatches(root, new TreePath(root), searchText, messageType, responseCode, matches);

            messageTree.clearSelection();
            if (!matches.isEmpty()) {
                messageTree.setSelectionPaths(matches.toArray(new TreePath[0]));
                messageTree.scrollPathToVisible(matches.get(0));
            }

            if (onSearchPerformed != null) {
                onSearchPerformed.run();
            }
        }
    }

    private boolean nodeMatches(DefaultMutableTreeNode node, String searchText,
                                String messageType, String responseCode) {
        String nodeText = node.toString().toLowerCase();

        if (!searchText.isEmpty() && !nodeText.contains(searchText)) {
            return false;
        }
        if (!messageType.equals("All Types") && !nodeText.contains(messageType.toLowerCase())) {
            return false;
        }
        if (!responseCode.equals("All Responses") && !responseCode.startsWith("---")) {
            String code = responseCode.substring(0, 3);
            if (!nodeText.contains(code)) {
                return false;
            }
        }
        return true;
    }

    private void collectMatches(DefaultMutableTreeNode node, TreePath path, String searchText,
                                String messageType, String responseCode, java.util.List<TreePath> out) {
        // The root is a structural container, not a real message - never match it.
        if (node.getParent() != null && nodeMatches(node, searchText, messageType, responseCode)) {
            out.add(path);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            collectMatches(child, path.pathByAddingChild(child), searchText, messageType, responseCode, out);
        }
    }
    
    private void expandAll(JTree tree, TreePath parent, boolean expand) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                TreeNode n = node.getChildAt(i);
                TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path, expand);
            }
        }
        
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }
    
    private void clearFilters() {
        searchField.setText("");
        messageTypeFilter.setSelectedIndex(0);
        responseCodeFilter.setSelectedIndex(0);
        
        // Collapse all nodes
        if (messageTree != null && messageTree.getModel() != null) {
            DefaultTreeModel model = (DefaultTreeModel) messageTree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            expandAll(messageTree, new TreePath(root), false);
        }
    }
    
    public void setMessageTree(JTree tree) {
        this.messageTree = tree;
    }
}
