package sipinspector.Dialogs;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.EmojiIcon;

/**
 * Structured Settings Dialog for SIP Simulator v4.
 *
 * Implements Workstream F of Phase 8 & Phase 8.2 (DEF-08).
 * Provides clean, categorized configuration tabs for:
 * 1. Network
 * 2. SIP
 * 3. Media
 * 4. Diagnostics & Simulation
 * 5. Appearance
 *
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class StructuredSettingsDialog extends JDialog {

    private final Frame parentFrame;

    // --- Tab 1: Network ---
    private JTextField txtLocalIp;
    private JTextField txtLocalPort;
    private JTextField txtPbxIp;
    private JTextField txtPbxPort;
    private JComboBox<String> comboTransport;

    // --- Tab 2: SIP ---
    private JTextField txtRegExpires;
    private JTextField txtExtension;
    private JTextField txtUserAgent;
    private JComboBox<String> comboDtmfMode;

    // --- Tab 3: Media ---
    private JComboBox<String> comboDefaultCodec;
    private JTextField txtRtpPortStart;
    private JTextField txtRtpPortEnd;
    private JComboBox<String> comboMediaSecurity;
    private JCheckBox chkLiveAudio;

    // --- Tab 4: Diagnostics ---
    private JCheckBox chkLogsEnabled;
    private JCheckBox chkTlsDebug;
    private JComboBox<String> comboPcapEngine;
    private JSpinner spinDelay;
    private JSpinner spinLoss;
    private JSpinner spinJitter;

    // --- Tab 5: Appearance ---
    private JComboBox<String> comboTheme;
    private JCheckBox chkSaveGeometry;

    public StructuredSettingsDialog(Frame parent) {
        super(parent, "Preferences / Settings", true);
        this.parentFrame = parent;
        initComponents();
        loadCurrentSettings();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setSize(580, 480);
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(parentFrame);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        tabbedPane.addTab("Network", createNetworkPanel());
        tabbedPane.addTab("SIP", createSipPanel());
        tabbedPane.addTab("Media", createMediaPanel());
        tabbedPane.addTab("Diagnostics", createDiagnosticsPanel());
        tabbedPane.addTab("Appearance", createAppearancePanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(203, 213, 225)));

        JButton btnDefaults = new JButton("Defaults");
        btnDefaults.setToolTipText("Reset all settings to recommended defaults");
        btnDefaults.addActionListener(e -> populateDefaults());

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnApply = new JButton("Apply & Save");
        btnApply.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnApply.addActionListener(e -> applyAndSave());

        bottomBar.add(btnDefaults);
        bottomBar.add(btnCancel);
        bottomBar.add(btnApply);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private JPanel createNetworkPanel() {
        JPanel p = new JPanel(new MigLayout("fillx, insets 16", "[150!][grow]", "[][][][][]"));
        p.add(new JLabel("Local Interface IP:"), "");
        txtLocalIp = new JTextField();
        txtLocalIp.setToolTipText("Leave empty for automatic dynamic interface resolution towards PBX");
        p.add(txtLocalIp, "growx, wrap");

        p.add(new JLabel("Local SIP Port:"), "");
        txtLocalPort = new JTextField("5060");
        txtLocalPort.setToolTipText("Default local SIP listening port (1 - 65535)");
        p.add(txtLocalPort, "growx, wrap");

        p.add(new JLabel("Target PBX IP Address:"), "");
        txtPbxIp = new JTextField("192.168.0.20");
        txtPbxIp.setToolTipText("IP address or hostname of the target PBX (e.g. Alcatel-Lucent OXE)");
        p.add(txtPbxIp, "growx, wrap");

        p.add(new JLabel("Target PBX SIP Port:"), "");
        txtPbxPort = new JTextField("5060");
        txtPbxPort.setToolTipText("Target PBX SIP signaling port (5060 for UDP/TCP, 5061 for TLS)");
        p.add(txtPbxPort, "growx, wrap");

        p.add(new JLabel("Default Transport:"), "");
        comboTransport = new JComboBox<>(new String[]{"UDP", "TCP", "TLS"});
        p.add(comboTransport, "growx, wrap");
        return p;
    }

    private JPanel createSipPanel() {
        JPanel p = new JPanel(new MigLayout("fillx, insets 16", "[160!][grow]", "[][][][]"));
        p.add(new JLabel("Registration Expiry (s):"), "");
        txtRegExpires = new JTextField("1800");
        txtRegExpires.setToolTipText("SIP Expires header value in seconds (minimum 60s, OXE requires 1800s)");
        p.add(txtRegExpires, "growx, wrap");

        p.add(new JLabel("Default Extension / User:"), "");
        txtExtension = new JTextField("90008");
        txtExtension.setToolTipText("Default subscriber extension number on PBX");
        p.add(txtExtension, "growx, wrap");

        p.add(new JLabel("SIP User-Agent Header:"), "");
        txtUserAgent = new JTextField("SIP Simulator v4");
        p.add(txtUserAgent, "growx, wrap");

        p.add(new JLabel("In-Call DTMF Mode:"), "");
        comboDtmfMode = new JComboBox<>(new String[]{
                "RFC 2833 / 4733 (RTP Event)",
                "SIP INFO (application/dtmf-relay)",
                "In-Band PCM Audio"
        });
        p.add(comboDtmfMode, "growx, wrap");
        return p;
    }

    private JPanel createMediaPanel() {
        JPanel p = new JPanel(new MigLayout("fillx, insets 16", "[160!][grow]", "[][][][][]"));
        p.add(new JLabel("Default Audio Codec:"), "");
        comboDefaultCodec = new JComboBox<>(new String[]{
                "PCMU / G.711 u-law (PT=0)",
                "PCMA / G.711 A-law (PT=8)",
                "G.722 Wideband (PT=9)"
        });
        p.add(comboDefaultCodec, "growx, wrap");

        p.add(new JLabel("RTP Port Range Start:"), "");
        txtRtpPortStart = new JTextField("42000");
        txtRtpPortStart.setToolTipText("Starting even-numbered UDP port for RTP media streams");
        p.add(txtRtpPortStart, "growx, wrap");

        p.add(new JLabel("RTP Port Range End:"), "");
        txtRtpPortEnd = new JTextField("50000");
        txtRtpPortEnd.setToolTipText("Ending UDP port for RTP media streams");
        p.add(txtRtpPortEnd, "growx, wrap");

        p.add(new JLabel("Media Security (SAVP):"), "");
        comboMediaSecurity = new JComboBox<>(new String[]{
                "Plain RTP (RTP/AVP)",
                "SRTP SDES (RTP/SAVP, AES-CM-128-HMAC-SHA1-80)"
        });
        p.add(comboMediaSecurity, "growx, wrap");

        p.add(new JLabel("Live Audio Lines:"), "");
        chkLiveAudio = new JCheckBox("Capture and play audio through sound card (Microphone/Speaker)");
        p.add(chkLiveAudio, "growx, wrap");
        return p;
    }

    private JPanel createDiagnosticsPanel() {
        JPanel p = new JPanel(new MigLayout("fillx, insets 16", "[160!][grow]", "[][][][][][]"));
        p.add(new JLabel("File Logging:"), "");
        chkLogsEnabled = new JCheckBox("Write SIP signaling and campaign traces to logs/ folder", true);
        p.add(chkLogsEnabled, "growx, wrap");

        p.add(new JLabel("TLS Debug Logging:"), "");
        chkTlsDebug = new JCheckBox("Log detailed TLS handshake, certificates, and SSL engine state");
        p.add(chkTlsDebug, "growx, wrap");

        p.add(new JLabel("PCAP Capture Engine:"), "");
        comboPcapEngine = new JComboBox<>(new String[]{"Pure Java PCAP", "Jpcap / Npcap Driver"});
        p.add(comboPcapEngine, "growx, wrap");

        p.add(new JLabel("Default Loss Rate (%):"), "");
        spinLoss = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        p.add(spinLoss, "w 80!, wrap");

        p.add(new JLabel("Default Delay (ms):"), "");
        spinDelay = new JSpinner(new SpinnerNumberModel(0, 0, 2000, 10));
        p.add(spinDelay, "w 80!, wrap");

        p.add(new JLabel("Default Jitter (ms):"), "");
        spinJitter = new JSpinner(new SpinnerNumberModel(0, 0, 500, 5));
        p.add(spinJitter, "w 80!, wrap");
        return p;
    }

    private JPanel createAppearancePanel() {
        JPanel p = new JPanel(new MigLayout("fillx, insets 16", "[160!][grow]", "[][]"));
        p.add(new JLabel("UI Theme:"), "");
        comboTheme = new JComboBox<>(new String[]{"Ocean Blue", "Dark", "FlatLaf Light", "FlatLaf Dark"});
        p.add(comboTheme, "growx, wrap");

        p.add(new JLabel("Window Geometry:"), "");
        chkSaveGeometry = new JCheckBox("Remember window position and maximized state across sessions", true);
        p.add(chkSaveGeometry, "growx, wrap");
        return p;
    }

    private void loadCurrentSettings() {
        // Network
        String localIp = GlobalSettings.getSetting("sip.localIp");
        txtLocalIp.setText(localIp != null ? localIp : "");
        String localPort = GlobalSettings.getSetting("sip.localPort");
        txtLocalPort.setText(localPort != null && !localPort.isEmpty() ? localPort : "5060");
        String pbxIp = GlobalSettings.getSetting("sip.pbxIp");
        txtPbxIp.setText(pbxIp != null && !pbxIp.isEmpty() ? pbxIp : "192.168.0.20");
        String pbxPort = GlobalSettings.getSetting("sip.pbxPort");
        txtPbxPort.setText(pbxPort != null && !pbxPort.isEmpty() ? pbxPort : "5060");
        String trans = GlobalSettings.getSetting("sip.transport");
        if (trans != null) {
            for (int i = 0; i < comboTransport.getItemCount(); i++) {
                if (comboTransport.getItemAt(i).equalsIgnoreCase(trans)) {
                    comboTransport.setSelectedIndex(i);
                    break;
                }
            }
        }

        // SIP
        String expires = GlobalSettings.getSetting("sip.expires");
        txtRegExpires.setText(expires != null && !expires.isEmpty() ? expires : "1800");
        String ext = GlobalSettings.getUsername();
        txtExtension.setText(ext != null && !ext.isEmpty() ? ext : "90008");
        String ua = GlobalSettings.getSetting("sip.userAgent");
        txtUserAgent.setText(ua != null && !ua.isEmpty() ? ua : "SIP Simulator v4");

        // Media
        String rtpStart = GlobalSettings.getSetting("rtp.portRangeStart");
        txtRtpPortStart.setText(rtpStart != null && !rtpStart.isEmpty() ? rtpStart : "42000");
        String rtpEnd = GlobalSettings.getSetting("rtp.portRangeEnd");
        txtRtpPortEnd.setText(rtpEnd != null && !rtpEnd.isEmpty() ? rtpEnd : "50000");
        comboMediaSecurity.setSelectedIndex(GlobalSettings.isSdpUseSavp() ? 1 : 0);
        chkLiveAudio.setSelected(GlobalSettings.isLiveAudioEnabled());

        // Diagnostics
        chkLogsEnabled.setSelected(GlobalSettings.isLogsEnabled());
        chkTlsDebug.setSelected("true".equalsIgnoreCase(GlobalSettings.getSetting("tls.debug")));
        try {
            int loss = Integer.parseInt(GlobalSettings.getSetting("sim.loss"));
            spinLoss.setValue(Math.max(0, Math.min(100, loss)));
        } catch (Exception ignore) {}
        try {
            int delay = Integer.parseInt(GlobalSettings.getSetting("sim.delay"));
            spinDelay.setValue(Math.max(0, Math.min(2000, delay)));
        } catch (Exception ignore) {}
        try {
            int jitter = Integer.parseInt(GlobalSettings.getSetting("sim.jitter"));
            spinJitter.setValue(Math.max(0, Math.min(500, jitter)));
        } catch (Exception ignore) {}

        // Appearance
        String theme = GlobalSettings.getSetting("defaultTheme");
        if (theme != null) {
            for (int i = 0; i < comboTheme.getItemCount(); i++) {
                if (comboTheme.getItemAt(i).equalsIgnoreCase(theme)) {
                    comboTheme.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void populateDefaults() {
        txtLocalIp.setText("");
        txtLocalPort.setText("5060");
        txtPbxIp.setText("192.168.0.20");
        txtPbxPort.setText("5060");
        comboTransport.setSelectedItem("UDP");
        txtRegExpires.setText("1800");
        txtExtension.setText("90008");
        txtUserAgent.setText("SIP Simulator v4");
        comboDtmfMode.setSelectedIndex(0);
        comboDefaultCodec.setSelectedIndex(0);
        txtRtpPortStart.setText("42000");
        txtRtpPortEnd.setText("50000");
        comboMediaSecurity.setSelectedIndex(0);
        chkLiveAudio.setSelected(false);
        chkLogsEnabled.setSelected(true);
        chkTlsDebug.setSelected(false);
        spinLoss.setValue(0);
        spinDelay.setValue(0);
        spinJitter.setValue(0);
        comboTheme.setSelectedItem("Ocean Blue");
        chkSaveGeometry.setSelected(true);
    }

    /**
     * Pure validation logic for settings, decoupled from UI controls for testability.
     */
    public static String validateSettings(String localIp, String localPort, String pbxIp, String pbxPort,
                                          String rtpStart, String rtpEnd, String regExpires) {
        // Validate local IP if provided
        if (localIp != null && !localIp.trim().isEmpty() && !localIp.equalsIgnoreCase("auto")) {
            if (!isValidIpv4(localIp.trim())) {
                return "Local IP must be a valid IPv4 address or blank for auto-resolution.";
            }
        }

        // Validate PBX IP
        if (pbxIp == null || pbxIp.trim().isEmpty()) {
            return "Target PBX IP address cannot be empty.";
        }
        if (!isValidIpv4(pbxIp.trim()) && !isValidHostname(pbxIp.trim())) {
            return "Target PBX IP must be a valid IPv4 address or hostname.";
        }

        // Validate local SIP port
        int lp = parsePort(localPort);
        if (lp <= 0 || lp > 65535) {
            return "Local SIP port must be an integer between 1 and 65535.";
        }

        // Validate PBX SIP port
        int pp = parsePort(pbxPort);
        if (pp <= 0 || pp > 65535) {
            return "PBX SIP port must be an integer between 1 and 65535.";
        }

        // Validate RTP port range
        int rs = parsePort(rtpStart);
        int re = parsePort(rtpEnd);
        if (rs < 1024 || rs > 65534 || rs % 2 != 0) {
            return "RTP Port Range Start must be an even integer between 1024 and 65534.";
        }
        if (re <= rs || re > 65535) {
            return "RTP Port Range End must be greater than Start port and <= 65535.";
        }

        // Validate Registration Expiry
        try {
            int exp = Integer.parseInt(regExpires.trim());
            if (exp < 60) {
                return "Registration expiration must be at least 60 seconds.";
            }
        } catch (Exception e) {
            return "Registration expiration must be a positive integer.";
        }

        return null; // Valid
    }

    private static boolean isValidIpv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                int n = Integer.parseInt(p);
                if (n < 0 || n > 255) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidHostname(String host) {
        return host.matches("^[a-zA-Z0-9.-]+$");
    }

    private static int parsePort(String str) {
        try {
            return Integer.parseInt(str != null ? str.trim() : "");
        } catch (Exception e) {
            return -1;
        }
    }

    private void applyAndSave() {
        String localIp = txtLocalIp.getText().trim();
        String localPort = txtLocalPort.getText().trim();
        String pbxIp = txtPbxIp.getText().trim();
        String pbxPort = txtPbxPort.getText().trim();
        String rtpStart = txtRtpPortStart.getText().trim();
        String rtpEnd = txtRtpPortEnd.getText().trim();
        String regExpires = txtRegExpires.getText().trim();

        String validationError = validateSettings(localIp, localPort, pbxIp, pbxPort, rtpStart, rtpEnd, regExpires);
        if (validationError != null) {
            JOptionPane.showMessageDialog(this, validationError, "Invalid Setting", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Apply settings into GlobalSettings
        GlobalSettings.setSetting("sip.localIp", localIp);
        GlobalSettings.setSetting("sip.localPort", localPort);
        GlobalSettings.setSetting("sip.pbxIp", pbxIp);
        GlobalSettings.setSetting("sip.pbxPort", pbxPort);
        GlobalSettings.setSetting("sip.transport", (String) comboTransport.getSelectedItem());
        GlobalSettings.setSetting("sip.expires", regExpires);
        GlobalSettings.setUsername(txtExtension.getText().trim());
        GlobalSettings.setSetting("sip.userAgent", txtUserAgent.getText().trim());
        GlobalSettings.setSetting("rtp.portRangeStart", rtpStart);
        GlobalSettings.setSetting("rtp.portRangeEnd", rtpEnd);
        GlobalSettings.setSdpUseSavp(comboMediaSecurity.getSelectedIndex() == 1);
        GlobalSettings.setLiveAudioEnabled(chkLiveAudio.isSelected());
        GlobalSettings.setLogsEnabled(chkLogsEnabled.isSelected());
        GlobalSettings.setSetting("tls.debug", String.valueOf(chkTlsDebug.isSelected()));
        GlobalSettings.setSetting("sim.loss", String.valueOf(spinLoss.getValue()));
        GlobalSettings.setSetting("sim.delay", String.valueOf(spinDelay.getValue()));
        GlobalSettings.setSetting("sim.jitter", String.valueOf(spinJitter.getValue()));
        GlobalSettings.setSetting("defaultTheme", (String) comboTheme.getSelectedItem());

        // Save persistent properties
        GlobalSettings.saveSettings();

        JOptionPane.showMessageDialog(this, "Settings applied and saved successfully.", "Preferences Saved", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
