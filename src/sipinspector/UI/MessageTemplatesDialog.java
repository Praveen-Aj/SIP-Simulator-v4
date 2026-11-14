/*
 * Message Templates Dialog
 * Provides quick access to common SIP message templates
 */
package sipinspector.UI;

import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import net.miginfocom.swing.MigLayout;
import sipinspector.Utils.EmojiIcon;

/**
 * Dialog for browsing and copying pre-built SIP message templates.
 * Select a template to preview it, then click Insert to copy it to the
 * clipboard (the copied text can be pasted into the scenario editor).
 *
 * @author SIP Support Team
 */
public class MessageTemplatesDialog extends JDialog {

    private JList<String> templateList;
    private JTextArea previewArea;
    private JButton insertButton;
    private JButton closeButton;
    private String selectedTemplate = null;

    private static final String[] TEMPLATE_NAMES = {
        "INVITE (Basic)",
        "INVITE (with SDP)",
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
        "PRACK",
        "200 OK (INVITE)",
        "200 OK (REGISTER)",
        "180 Ringing",
        "100 Trying",
        "401 Unauthorized",
        "407 Proxy Auth Required"
    };

    public MessageTemplatesDialog(Frame parent) {
        super(parent, "SIP Message Templates", true);
        initComponents();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 15", "[220!][grow]", "[grow][]"));

        // Template list
        templateList = new JList<>(TEMPLATE_NAMES);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    showTemplatePreview();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(templateList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Templates"));
        add(listScroll, "grow");

        // Preview area
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Preview"));
        add(previewScroll, "grow, wrap");

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        insertButton = new JButton("Insert (Copy)", EmojiIcon.getCheckIcon());
        insertButton.setToolTipText("Copy the selected template to the clipboard");
        insertButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertTemplate();
            }
        });
        buttonPanel.add(insertButton);

        closeButton = new JButton("Close", EmojiIcon.getCloseIcon());
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(closeButton);

        add(buttonPanel, "span, right");

        // Dialog settings
        sipinspector.UI.UIUtils.sizeAndCenterDialog(this, 800, 600, 600, 450, getParent());

        // Select first template so the preview is populated on open
        if (TEMPLATE_NAMES.length > 0) {
            templateList.setSelectedIndex(0);
        }
    }

    private void showTemplatePreview() {
        String selected = templateList.getSelectedValue();
        if (selected == null) {
            return;
        }
        previewArea.setText(getTemplate(selected));
        previewArea.setCaretPosition(0);
    }

    private void insertTemplate() {
        selectedTemplate = previewArea.getText();
        if (selectedTemplate != null && !selectedTemplate.isEmpty()) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(selectedTemplate), null);
            } catch (Exception ex) {
                // Clipboard may be unavailable in some environments; ignore.
            }
            JOptionPane.showMessageDialog(this,
                "Template copied to clipboard.\nYou can now paste it into the scenario editor.",
                "Template Copied",
                JOptionPane.INFORMATION_MESSAGE);
        }
        dispose();
    }

    /**
     * @return the last template the user chose via Insert, or null if none.
     */
    public String getSelectedTemplate() {
        return selectedTemplate;
    }

    private String getTemplate(String name) {
        switch (name) {
            case "INVITE (Basic)":
                return "INVITE sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Contact: <sip:caller@192.168.1.100:5060>\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "INVITE (with SDP)":
                return "INVITE sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Contact: <sip:caller@192.168.1.100:5060>\r\n" +
                       "Content-Type: application/sdp\r\n" +
                       "Content-Length: 142\r\n\r\n" +
                       "v=0\r\n" +
                       "o=user1 53655765 2353687637 IN IP4 192.168.1.100\r\n" +
                       "s=-\r\n" +
                       "c=IN IP4 192.168.1.100\r\n" +
                       "t=0 0\r\n" +
                       "m=audio 6000 RTP/AVP 8\r\n" +
                       "a=rtpmap:8 PCMA/8000\r\n";

            case "REGISTER":
                return "REGISTER sip:domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:user@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 REGISTER\r\n" +
                       "Contact: <sip:user@192.168.1.100:5060>\r\n" +
                       "Expires: 3600\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "BYE":
                return "BYE sip:user@192.168.1.200:5060 SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 231 BYE\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "ACK":
                return "ACK sip:user@192.168.1.200:5060 SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 ACK\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "CANCEL":
                return "CANCEL sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 CANCEL\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "OPTIONS":
                return "OPTIONS sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 OPTIONS\r\n" +
                       "Accept: application/sdp\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "SUBSCRIBE":
                return "SUBSCRIBE sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 SUBSCRIBE\r\n" +
                       "Event: message-summary\r\n" +
                       "Accept: application/simple-message-summary\r\n" +
                       "Contact: <sip:caller@192.168.1.100:5060>\r\n" +
                       "Expires: 3600\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "NOTIFY":
                return "NOTIFY sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 2 NOTIFY\r\n" +
                       "Event: message-summary\r\n" +
                       "Subscription-State: active;expires=3600\r\n" +
                       "Content-Type: application/simple-message-summary\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "REFER":
                return "REFER sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 3 REFER\r\n" +
                       "Refer-To: <sip:transfer-target@domain.com>\r\n" +
                       "Referred-By: <sip:caller@domain.com>\r\n" +
                       "Contact: <sip:caller@192.168.1.100:5060>\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "MESSAGE":
                return "MESSAGE sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 MESSAGE\r\n" +
                       "Content-Type: text/plain\r\n" +
                       "Content-Length: 12\r\n\r\n" +
                       "Hello World!";

            case "UPDATE":
                return "UPDATE sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 2 UPDATE\r\n" +
                       "Contact: <sip:caller@192.168.1.100:5060>\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "PRACK":
                return "PRACK sip:user@domain.com SIP/2.0\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "Max-Forwards: 70\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 2 PRACK\r\n" +
                       "RAck: 1 314159 INVITE\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "200 OK (INVITE)":
                return "SIP/2.0 200 OK\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Contact: <sip:user@192.168.1.200:5060>\r\n" +
                       "Content-Type: application/sdp\r\n" +
                       "Content-Length: 142\r\n\r\n" +
                       "v=0\r\n" +
                       "o=user2 2890844526 2890844526 IN IP4 192.168.1.200\r\n" +
                       "s=-\r\n" +
                       "c=IN IP4 192.168.1.200\r\n" +
                       "t=0 0\r\n" +
                       "m=audio 6000 RTP/AVP 8\r\n" +
                       "a=rtpmap:8 PCMA/8000\r\n";

            case "200 OK (REGISTER)":
                return "SIP/2.0 200 OK\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:user@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 REGISTER\r\n" +
                       "Contact: <sip:user@192.168.1.100:5060>;expires=3600\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "180 Ringing":
                return "SIP/2.0 180 Ringing\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Contact: <sip:user@192.168.1.200:5060>\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "100 Trying":
                return "SIP/2.0 100 Trying\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "401 Unauthorized":
                return "SIP/2.0 401 Unauthorized\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:user@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 1 REGISTER\r\n" +
                       "WWW-Authenticate: Digest realm=\"domain.com\", " +
                       "nonce=\"84a4cc6f3082121f32b42a2187831a9e\", algorithm=MD5, qop=\"auth\"\r\n" +
                       "Content-Length: 0\r\n\r\n";

            case "407 Proxy Auth Required":
                return "SIP/2.0 407 Proxy Authentication Required\r\n" +
                       "Via: SIP/2.0/UDP 192.168.1.100:5060;branch=z9hG4bK776asdhds\r\n" +
                       "To: <sip:user@domain.com>;tag=a6c85cf\r\n" +
                       "From: <sip:caller@domain.com>;tag=1928301774\r\n" +
                       "Call-ID: a84b4c76e66710@pc33.domain.com\r\n" +
                       "CSeq: 314159 INVITE\r\n" +
                       "Proxy-Authenticate: Digest realm=\"domain.com\", " +
                       "nonce=\"84a4cc6f3082121f32b42a2187831a9e\", algorithm=MD5, qop=\"auth\"\r\n" +
                       "Content-Length: 0\r\n\r\n";

            default:
                return "Template not yet implemented.\r\n" +
                       "This is a placeholder for: " + name;
        }
    }
}
