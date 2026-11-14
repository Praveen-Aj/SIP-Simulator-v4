/*
 * Tones Panel
 * Inserts a cmd_PlayPcap scenario entry (RBT, Hold, Guide, Conversation,
 * synthesized Dial/Busy/Reorder/End tones, or a DTMF digit) at the current
 * position in the scenario tree - just like any SIP message. Because it
 * plugs into the same "<< Add" mechanism as the SIP/Commands tabs, a tone
 * can be placed anywhere in the script: before 180 Ringing, between 180 and
 * 200 OK, after 200 OK, etc.
 */
package sipinspector.ControlTabs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JTree;
import net.miginfocom.swing.MigLayout;
import sipinspector.Media.ToneSynthesizer;
import sipinspector.SIPScenario;
import sipinspector.ScenarioEntries.CommandPlayPcapEntry;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.Utils.FileChooserUtils;

public class TonesPanel extends EntryTab {

    private JRadioButton presetButton;
    private JRadioButton customButton;
    private JComboBox<ToneOption> presetCombo;
    private JCheckBox continuousCheckbox;
    private JTextField customPathField;
    private JButton customBrowseButton;

    private static class ToneOption {
        final String label;
        final String path;
        final boolean defaultContinuous;

        ToneOption(String label, String path, boolean defaultContinuous) {
            this.label = label;
            this.path = path;
            this.defaultContinuous = defaultContinuous;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public TonesPanel() {
        ToneSynthesizer.ensureGenerated();
        initComponents();
    }

    @Override
    public JTree addMsgTreeEntry(JTree tree, SIPScenario sipScenario) {
        String path;
        boolean continuous;

        if (customButton.isSelected()) {
            path = customPathField.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a .pcap file",
                        "Tone validation", JOptionPane.ERROR_MESSAGE);
                return tree;
            }
            continuous = continuousCheckbox.isSelected();
        } else {
            ToneOption selected = (ToneOption) presetCombo.getSelectedItem();
            if (selected == null) {
                return tree;
            }
            path = selected.path;
            continuous = continuousCheckbox.isSelected();
        }

        if (!new File(path).exists()) {
            JOptionPane.showMessageDialog(this, "Tone file not found:\n" + path,
                    "Tone validation", JOptionPane.ERROR_MESSAGE);
            return tree;
        }

        String cmdString = "cmd_PlayPcap=" + path + ";continuous=" + continuous;
        ScenarioEntry newMsg = new CommandPlayPcapEntry(cmdString);
        sipScenario.appendNewEntry(newMsg, tree);
        sipScenario.updateTreeFromScenario(tree);
        return tree;
    }

    private void initComponents() {
        setName("Tones");
        setLayout(new MigLayout("insets 10", "[][grow]", "[][][][][]"));

        JLabel title = new JLabel("Insert a tone at the current scenario position");
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(title, "span, wrap");

        ButtonGroup group = new ButtonGroup();

        presetButton = new JRadioButton("Preset Tone");
        presetButton.setSelected(true);
        group.add(presetButton);
        presetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                updateEnabledState();
            }
        });
        add(presetButton);

        presetCombo = new JComboBox<>(buildPresetOptions());
        presetCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ToneOption sel = (ToneOption) presetCombo.getSelectedItem();
                if (sel != null) {
                    continuousCheckbox.setSelected(sel.defaultContinuous);
                }
            }
        });
        add(presetCombo, "growx, wrap");

        customButton = new JRadioButton("Custom .pcap File");
        group.add(customButton);
        customButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                updateEnabledState();
            }
        });
        add(customButton);

        customPathField = new JTextField();
        customPathField.setEnabled(false);
        add(customPathField, "growx, split 2");

        customBrowseButton = new JButton("...");
        customBrowseButton.setEnabled(false);
        customBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                browseForPcap();
            }
        });
        add(customBrowseButton, "w 30!, wrap");

        continuousCheckbox = new JCheckBox("Continuous (loop until the next tone/pause/message)");
        continuousCheckbox.setSelected(true);
        add(continuousCheckbox, "span, wrap");

        JLabel hint = new JLabel("<html><i>Tip: place this entry anywhere - e.g. right after receiving the "
                + "INVITE and before sending 180/200 OK - to play Ring Back or a greeting "
                + "before the call is answered.</i></html>");
        add(hint, "span, growx");
    }

    private ToneOption[] buildPresetOptions() {
        java.util.List<ToneOption> options = new java.util.ArrayList<>();
        options.add(new ToneOption("Ring Back Tone (RBT)", "pcap/RBT_tone.pcap", true));
        options.add(new ToneOption("Hold Tone", "pcap/Hold_tone.pcap", true));
        options.add(new ToneOption("Welcome / Greeting Guide", "pcap/Guide_tone.pcap", false));
        options.add(new ToneOption("Background Conversation", "pcap/Conversation_tone.pcap", true));
        options.add(new ToneOption("Dial Tone", ToneSynthesizer.DIAL_TONE, true));
        options.add(new ToneOption("Busy Tone", ToneSynthesizer.BUSY_TONE, true));
        options.add(new ToneOption("Reorder (Fast Busy) Tone", ToneSynthesizer.REORDER_TONE, true));
        options.add(new ToneOption("End Tone", ToneSynthesizer.END_TONE, false));
        String[] digits = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "star", "pound"};
        String[] labels = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#"};
        for (int i = 0; i < digits.length; i++) {
            options.add(new ToneOption("DTMF Digit " + labels[i], "pcap/dtmf_" + digits[i] + ".pcap", false));
        }
        return options.toArray(new ToneOption[0]);
    }

    private void updateEnabledState() {
        boolean custom = customButton.isSelected();
        presetCombo.setEnabled(!custom);
        customPathField.setEnabled(custom);
        customBrowseButton.setEnabled(custom);
    }

    private void browseForPcap() {
        JFileChooser fc = new JFileChooser(new File("pcap").exists() ? "pcap" : System.getProperty("user.dir"));
        fc.setDialogTitle("Select a tone .pcap file");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Wireshark PCAP Capture (*.pcap)", "pcap"));
        if (FileChooserUtils.showOpenDialog(this, fc) == JFileChooser.APPROVE_OPTION) {
            customPathField.setText(fc.getSelectedFile().getPath());
        }
    }
}
