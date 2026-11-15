/*
 * SIP Message Comparison / Diff Dialog
 * Compares two SIP messages header-by-header to pinpoint interop differences
 * (e.g. a sent INVITE vs the OXE response, or two scenario messages).
 */
package sipinspector.UI;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import net.miginfocom.swing.MigLayout;
import sipinspector.Utils.EmojiIcon;

/**
 * Side-by-side SIP message diff. Paste two messages, click Compare, and the
 * result table shows each header with a SAME / DIFFERENT / ONLY-IN-A /
 * ONLY-IN-B verdict, plus start-line and body comparison.
 *
 * @author SIP Support Team
 */
public class MessageDiffDialog extends JDialog {

    private JTextArea messageAArea;
    private JTextArea messageBArea;
    private JTable resultTable;
    private DefaultTableModel resultModel;
    private JLabel summaryLabel;

    private static final String SAME = "SAME";
    private static final String DIFFERENT = "DIFFERENT";
    private static final String ONLY_A = "ONLY IN A";
    private static final String ONLY_B = "ONLY IN B";

    public MessageDiffDialog(Frame parent) {
        super(parent, "Compare SIP Messages", false);
        initComponents();
    }

    public MessageDiffDialog(Frame parent, String messageA, String messageB) {
        this(parent);
        if (messageA != null) setMessageA(messageA);
        if (messageB != null) setMessageB(messageB);
        if (messageA != null && messageB != null && !messageA.isEmpty() && !messageB.isEmpty()) {
            compare();
        }
    }

    public void setMessageA(String text) {
        if (messageAArea != null) {
            messageAArea.setText(text != null ? text : "");
            messageAArea.setCaretPosition(0);
        }
    }

    public void setMessageB(String text) {
        if (messageBArea != null) {
            messageBArea.setText(text != null ? text : "");
            messageBArea.setCaretPosition(0);
        }
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 12", "[grow][grow]", "[grow 40][][grow 60][]"));

        messageAArea = buildInputArea();
        messageBArea = buildInputArea();

        JScrollPane scrollA = new JScrollPane(messageAArea);
        scrollA.setBorder(BorderFactory.createTitledBorder("Message A (e.g. sent INVITE)"));
        add(scrollA, "grow");

        JScrollPane scrollB = new JScrollPane(messageBArea);
        scrollB.setBorder(BorderFactory.createTitledBorder("Message B (e.g. received response / other trace)"));
        add(scrollB, "grow, wrap");

        JButton compareBtn = new JButton("Compare", EmojiIcon.getCompareIcon());
        compareBtn.setToolTipText("Compare the two messages header-by-header");
        compareBtn.addActionListener(e -> compare());
        JButton swapBtn = new JButton("Swap", EmojiIcon.getSwapIcon());
        swapBtn.setToolTipText("Swap message A and B");
        swapBtn.addActionListener(e -> {
            String a = messageAArea.getText();
            messageAArea.setText(messageBArea.getText());
            messageBArea.setText(a);
        });
        JButton clearBtn = new JButton("Clear", EmojiIcon.getCloseIcon());
        clearBtn.addActionListener(e -> {
            messageAArea.setText("");
            messageBArea.setText("");
            resultModel.setRowCount(0);
            summaryLabel.setText(" ");
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(compareBtn);
        btnPanel.add(swapBtn);
        btnPanel.add(clearBtn);
        add(btnPanel, "span, growx, wrap");

        resultModel = new DefaultTableModel(new Object[]{"Field", "Message A", "Message B", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        resultTable = new JTable(resultModel);
        resultTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultTable.setRowHeight(20);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        resultTable.setDefaultRenderer(Object.class, new DiffCellRenderer());
        JScrollPane resultScroll = new JScrollPane(resultTable);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Comparison Result"));
        add(resultScroll, "span, grow, wrap");

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD));
        add(summaryLabel, "span, growx");

        sipinspector.UI.UIUtils.sizeAndCenterDialog(this, 1000, 700, 750, 500, getParent());
    }

    private JTextArea buildInputArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setLineWrap(false);
        return area;
    }

    private void compare() {
        resultModel.setRowCount(0);
        ParsedMessage a = parse(messageAArea.getText());
        ParsedMessage b = parse(messageBArea.getText());

        int differences = 0;

        // Start line
        String status = valueStatus(a.startLine, b.startLine);
        if (!SAME.equals(status)) {
            differences++;
        }
        resultModel.addRow(new Object[]{"<Start-Line>", nz(a.startLine), nz(b.startLine), status});

        // Header union, order of first appearance (A first, then B-only)
        List<String> order = new ArrayList<>(a.headers.keySet());
        for (String key : b.headers.keySet()) {
            if (!order.contains(key)) {
                order.add(key);
            }
        }

        for (String key : order) {
            List<String> va = a.headers.get(key);
            List<String> vb = b.headers.get(key);
            String displayName = key;
            if (va != null && !va.isEmpty()) {
                displayName = a.displayNames.get(key);
            } else if (vb != null) {
                displayName = b.displayNames.get(key);
            }
            int rows = Math.max(va == null ? 0 : va.size(), vb == null ? 0 : vb.size());
            for (int i = 0; i < rows; i++) {
                String sa = (va != null && i < va.size()) ? va.get(i) : null;
                String sb = (vb != null && i < vb.size()) ? vb.get(i) : null;
                String st = valueStatus(sa, sb);
                if (!SAME.equals(st)) {
                    differences++;
                }
                resultModel.addRow(new Object[]{displayName, nz(sa), nz(sb), st});
            }
        }

        // Body
        String bodyStatus = valueStatus(a.body, b.body);
        if (!SAME.equals(bodyStatus)) {
            differences++;
        }
        if ((a.body != null && !a.body.isEmpty()) || (b.body != null && !b.body.isEmpty())) {
            resultModel.addRow(new Object[]{"<Body>",
                summarize(a.body), summarize(b.body), bodyStatus});
        }

        if (differences == 0) {
            summaryLabel.setText("Messages are identical (" + resultModel.getRowCount() + " fields compared).");
            summaryLabel.setForeground(new Color(0, 150, 0));
        } else {
            summaryLabel.setText(differences + " difference(s) found across "
                    + resultModel.getRowCount() + " fields.");
            summaryLabel.setForeground(new Color(200, 0, 0));
        }
    }

    private static String valueStatus(String a, String b) {
        boolean hasA = a != null;
        boolean hasB = b != null;
        if (hasA && hasB) {
            return a.trim().equals(b.trim()) ? SAME : DIFFERENT;
        }
        if (hasA) {
            return ONLY_A;
        }
        if (hasB) {
            return ONLY_B;
        }
        return SAME;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String summarize(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String oneLine = body.replace("\r", "").replace("\n", " \\n ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    /** Parsed SIP message: start line + ordered headers (folded lines unfolded) + body. */
    private static class ParsedMessage {
        String startLine;
        // key = lower-case header name; value = list of values in order
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        String body;
    }

    private static ParsedMessage parse(String text) {
        ParsedMessage msg = new ParsedMessage();
        if (text == null) {
            return msg;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        int i = 0;
        // Skip leading blank lines
        while (i < lines.length && lines[i].trim().isEmpty()) {
            i++;
        }
        if (i < lines.length) {
            msg.startLine = lines[i].trim();
            i++;
        }

        StringBuilder pending = null;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                i++;
                break; // headers finished; rest is body
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && pending != null) {
                pending.append(' ').append(line.trim()); // folded continuation
                continue;
            }
            if (pending != null) {
                addHeader(msg, pending.toString());
            }
            pending = new StringBuilder(line);
        }
        if (pending != null) {
            addHeader(msg, pending.toString());
        }

        if (i < lines.length) {
            StringBuilder body = new StringBuilder();
            for (; i < lines.length; i++) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(lines[i]);
            }
            String b = body.toString();
            msg.body = b.trim().isEmpty() ? null : b;
        }
        return msg;
    }

    private static void addHeader(ParsedMessage msg, String headerLine) {
        int colon = headerLine.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String name = headerLine.substring(0, colon).trim();
        String value = headerLine.substring(colon + 1).trim();
        String key = name.toLowerCase();
        msg.headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        msg.displayNames.putIfAbsent(key, name);
    }

    /** Colors DIFFERENT rows red-ish, ONLY-IN rows amber, SAME rows default. */
    private class DiffCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                String status = String.valueOf(table.getValueAt(row, 3));
                if (DIFFERENT.equals(status)) {
                    c.setBackground(new Color(255, 220, 220));
                    c.setForeground(Color.BLACK);
                } else if (ONLY_A.equals(status) || ONLY_B.equals(status)) {
                    c.setBackground(new Color(255, 243, 205));
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                }
            }
            return c;
        }
    }
}
