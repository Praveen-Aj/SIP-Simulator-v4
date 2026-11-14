package sipinspector.ControlTabs;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;

import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPScenario;

public class SDPPanel extends EntryTab {
    private JCheckBox g711CheckBox;

    private JCheckBox g729CheckBox;

    private JCheckBox g722CheckBox;

    private JCheckBox opusCheckBox;

    private JCheckBox savpCheckBox;

    private JComboBox<String> answerCodecComboBox;

    private JTextArea sdpTextArea;

    private JScrollPane scrollPane;

    private JButton applyButton;

    public SDPPanel() {
        initComponents();
    }

    private void initComponents() {
        setName("SDP");
        setLayout(new MigLayout("fill", "[grow]", "[][][grow]"));

        g711CheckBox = new JCheckBox("G.711 (PCMA)");
        g729CheckBox = new JCheckBox("G.729");
        g722CheckBox = new JCheckBox("G.722");
        opusCheckBox = new JCheckBox("Opus");

        // Keep label and codec checkboxes together on one row
        add(new JLabel("Offer codecs:"), "split 5");
        add(g711CheckBox);
        add(g729CheckBox);
        add(g722CheckBox);
        add(opusCheckBox, "wrap");

        savpCheckBox = new JCheckBox("Use SRTP (RTP/SAVP)");
        add(savpCheckBox, "split 4, gapright 30");

        add(new JLabel("Preferred answer codec:"));
        answerCodecComboBox = new JComboBox<String>();
        answerCodecComboBox.addItem("G.711");
        answerCodecComboBox.addItem("G.729");
        answerCodecComboBox.addItem("G.722");
        answerCodecComboBox.addItem("Opus");
        add(answerCodecComboBox);

        applyButton = new JButton("Apply SDP settings");
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });
        add(applyButton, "wrap");

        sdpTextArea = new JTextArea(10, 40);
        sdpTextArea.setFont(new java.awt.Font("Courier New", 0, 12));
        sdpTextArea.setEditable(false);
        scrollPane = new JScrollPane(sdpTextArea);
        add(scrollPane, "span, grow, pushy");

        g711CheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateAnswerCodecEnabledState();
                updatePreview();
            }
        });
        g729CheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateAnswerCodecEnabledState();
                updatePreview();
            }
        });
        g722CheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateAnswerCodecEnabledState();
                updatePreview();
            }
        });
        opusCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateAnswerCodecEnabledState();
                updatePreview();
            }
        });
        savpCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updatePreview();
            }
        });
        answerCodecComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updatePreview();
            }
        });

        loadSettings();
    }

    private void loadSettings() {
        g711CheckBox.setSelected(GlobalSettings.isSdpG711());
        g729CheckBox.setSelected(GlobalSettings.isSdpG729());
        g722CheckBox.setSelected(GlobalSettings.isSdpG722());
        opusCheckBox.setSelected(GlobalSettings.isSdpOpus());

        savpCheckBox.setSelected(GlobalSettings.isSdpUseSavp());

        String answerCodec = GlobalSettings.getSdpAnswerCodecType();
        if ("G729".equals(answerCodec)) {
            answerCodecComboBox.setSelectedItem("G.729");
        } else if ("G722".equals(answerCodec)) {
            answerCodecComboBox.setSelectedItem("G.722");
        } else if ("OPUS".equals(answerCodec)) {
            answerCodecComboBox.setSelectedItem("Opus");
        } else {
            answerCodecComboBox.setSelectedItem("G.711");
        }

        updateAnswerCodecEnabledState();
        updatePreview();
    }

    private void updateAnswerCodecEnabledState() {
        // Ensure the preferred answer codec is among enabled offer codecs
        if (!g711CheckBox.isSelected() && "G.711".equals(answerCodecComboBox.getSelectedItem())) {
            selectFirstEnabledCodec();
        } else if (!g729CheckBox.isSelected() && "G.729".equals(answerCodecComboBox.getSelectedItem())) {
            selectFirstEnabledCodec();
        } else if (!g722CheckBox.isSelected() && "G.722".equals(answerCodecComboBox.getSelectedItem())) {
            selectFirstEnabledCodec();
        } else if (!opusCheckBox.isSelected() && "Opus".equals(answerCodecComboBox.getSelectedItem())) {
            selectFirstEnabledCodec();
        }
    }

    private void selectFirstEnabledCodec() {
        if (g711CheckBox.isSelected()) {
            answerCodecComboBox.setSelectedItem("G.711");
        } else if (g729CheckBox.isSelected()) {
            answerCodecComboBox.setSelectedItem("G.729");
        } else if (g722CheckBox.isSelected()) {
            answerCodecComboBox.setSelectedItem("G.722");
        } else if (opusCheckBox.isSelected()) {
            answerCodecComboBox.setSelectedItem("Opus");
        }
    }

    private void updatePreview() {
        StringBuilder sb = new StringBuilder(256);
        boolean useSavp = savpCheckBox.isSelected();

        // Build payloads based on selected codecs (fallback to G.711 if none)
        boolean g711 = g711CheckBox.isSelected();
        boolean g729 = g729CheckBox.isSelected();
        boolean g722 = g722CheckBox.isSelected();
        boolean opus = opusCheckBox.isSelected();

        boolean anyCodec = g711 || g729 || g722 || opus;
        StringBuilder payloads = new StringBuilder();
        boolean first = true;
        if (!anyCodec || g711) {
            payloads.append("8");
            first = false;
        }
        if (g729) {
            if (!first) {
                payloads.append(" ");
            }
            payloads.append("18");
            first = false;
        }
        if (g722) {
            if (!first) {
                payloads.append(" ");
            }
            payloads.append("9");
            first = false;
        }
        if (opus) {
            if (!first) {
                payloads.append(" ");
            }
            payloads.append("111");
            first = false;
        }
        if (!first) {
            payloads.append(" ");
        }
        payloads.append("101");

        // Offer lines
        sb.append("Offer:  m=audio [media_port] RTP/AVP ")
                .append(payloads)
                .append("\n");

        if (useSavp) {
            sb.append("Offer:  m=audio [media_port] RTP/SAVP ")
                    .append(payloads)
                    .append("\n");
            String crypto = GlobalSettings.getSrtpCryptoLine();
            if (crypto == null || crypto.trim().length() == 0) {
                crypto = "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:(generated-at-runtime)|2^31";
            }
            sb.append("SRTP:  ").append(crypto).append("\n");
        }

        // Answer line
        String answer = (String) answerCodecComboBox.getSelectedItem();
        String answerPayload = "8";
        if ("G.729".equals(answer)) {
            answerPayload = "18";
        } else if ("G.722".equals(answer)) {
            answerPayload = "9";
        } else if ("Opus".equals(answer)) {
            answerPayload = "111";
        }

        sb.append("Answer: m=audio [media_port] ")
                .append(useSavp ? "RTP/SAVP" : "RTP/AVP")
                .append(" ")
                .append(answerPayload)
                .append(" 101\n");

        sdpTextArea.setText(sb.toString());
    }

    private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {
        GlobalSettings.setSdpG711(g711CheckBox.isSelected());
        GlobalSettings.setSdpG729(g729CheckBox.isSelected());
        GlobalSettings.setSdpG722(g722CheckBox.isSelected());
        GlobalSettings.setSdpOpus(opusCheckBox.isSelected());

        String answer = (String) answerCodecComboBox.getSelectedItem();
        if ("G.729".equals(answer)) {
            GlobalSettings.setSdpAnswerCodecType("G729");
        } else if ("G.722".equals(answer)) {
            GlobalSettings.setSdpAnswerCodecType("G722");
        } else if ("Opus".equals(answer)) {
            GlobalSettings.setSdpAnswerCodecType("OPUS");
        } else {
            GlobalSettings.setSdpAnswerCodecType("G711");
        }

        GlobalSettings.setSdpUseSavp(savpCheckBox.isSelected());

        javax.swing.JOptionPane.showMessageDialog(this,
                "SDP settings updated. New INVITEs and SDP responses created via SIP tab will use these codecs.",
                "SDP settings",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    public JTree addMsgTreeEntry(JTree tree, SIPScenario sipScenario) {
        javax.swing.JOptionPane.showMessageDialog(this,
                "SDP tab configures default SDP codecs and RTP/AVP vs RTP/SAVP. Use the SIP tab to add messages.",
                "SDP panel",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        return tree;
    }
}
