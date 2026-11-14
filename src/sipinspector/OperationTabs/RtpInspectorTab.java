package sipinspector.OperationTabs;

import java.awt.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import sipinspector.Media.LiveAudioController;
import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Threads.RTPSenderThread;

/**
 * Desktop panel displaying real-time RTP media diagnostics (SSRC, packet loss,
 * jitter, drift) and TLS/SRTP security handshake details.
 * 
 * @author SIP Support Team
 */
public class RtpInspectorTab extends JPanel {

    private final Timer refreshTimer;
    
    // RTP UI Fields
    private JLabel lblStatus;
    private JLabel lblTxSsrc, lblTxPt, lblTxCodec, lblTxPackets, lblTxBytes;
    private JLabel lblRxSsrc, lblRxPt, lblRxCodec, lblRxPackets, lblRxBytes, lblRxDropped, lblRxJitter, lblRxDrift, lblDtmf;
    
    // Security UI Fields
    private JLabel lblTlsVersion, lblCipherSuite;
    private JTextArea txtCertDetails;
    private JLabel lblSrtpCipher;

    public RtpInspectorTab() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(15, 23, 42)); // Slate 900
        
        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        mainPanel.setOpaque(false);
        
        mainPanel.add(createRtpPanel());
        mainPanel.add(createSecurityPanel());
        
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        
        // Timer to poll diagnostics every 500ms
        refreshTimer = new Timer(500, e -> updateDiagnostics());
        refreshTimer.start();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        JLabel title = new JLabel("RTP Stream Inspector & Security Diagnostics");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        
        lblStatus = new JLabel("Status: No Active Audio Stream");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setForeground(new Color(239, 68, 68)); // red-500
        
        panel.add(title, BorderLayout.WEST);
        panel.add(lblStatus, BorderLayout.EAST);
        return panel;
    }

    private JPanel createRtpPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 10));
        panel.setOpaque(false);
        
        // Transmit Panel
        JPanel txPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        txPanel.setOpaque(false);
        txPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)), // slate-700
                "Transmit RTP (Local -> Remote)", 
                TitledBorder.DEFAULT_JUSTIFICATION, 
                TitledBorder.DEFAULT_POSITION, 
                new Font("Segoe UI", Font.BOLD, 12), 
                new Color(56, 189, 248) // cyan-400
        ));
        
        txPanel.add(createFieldLabel("SSRC:"));
        lblTxSsrc = createValLabel("-");
        txPanel.add(lblTxSsrc);
        
        txPanel.add(createFieldLabel("Payload Type:"));
        lblTxPt = createValLabel("-");
        txPanel.add(lblTxPt);

        txPanel.add(createFieldLabel("Codec:"));
        lblTxCodec = createValLabel("-");
        txPanel.add(lblTxCodec);

        txPanel.add(createFieldLabel("Packets Sent:"));
        lblTxPackets = createValLabel("-");
        txPanel.add(lblTxPackets);
        
        txPanel.add(createFieldLabel("Bytes Sent:"));
        lblTxBytes = createValLabel("-");
        txPanel.add(lblTxBytes);

        // Receive Panel
        JPanel rxPanel = new JPanel(new GridLayout(9, 2, 5, 5));
        rxPanel.setOpaque(false);
        rxPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "Receive RTP (Remote -> Local)", 
                TitledBorder.DEFAULT_JUSTIFICATION, 
                TitledBorder.DEFAULT_POSITION, 
                new Font("Segoe UI", Font.BOLD, 12), 
                new Color(74, 222, 128) // green-400
        ));
        
        rxPanel.add(createFieldLabel("SSRC:"));
        lblRxSsrc = createValLabel("-");
        rxPanel.add(lblRxSsrc);
        
        rxPanel.add(createFieldLabel("Payload Type:"));
        lblRxPt = createValLabel("-");
        rxPanel.add(lblRxPt);

        rxPanel.add(createFieldLabel("Codec:"));
        lblRxCodec = createValLabel("-");
        rxPanel.add(lblRxCodec);

        rxPanel.add(createFieldLabel("Packets Received:"));
        lblRxPackets = createValLabel("-");
        rxPanel.add(lblRxPackets);
        
        rxPanel.add(createFieldLabel("Bytes Received:"));
        lblRxBytes = createValLabel("-");
        rxPanel.add(lblRxBytes);
        
        rxPanel.add(createFieldLabel("Packets Lost/Dropped:"));
        lblRxDropped = createValLabel("-");
        rxPanel.add(lblRxDropped);
        
        rxPanel.add(createFieldLabel("Jitter (ms):"));
        lblRxJitter = createValLabel("-");
        rxPanel.add(lblRxJitter);
        
        rxPanel.add(createFieldLabel("Timestamp Drift (ms):"));
        lblRxDrift = createValLabel("-");
        rxPanel.add(lblRxDrift);

        rxPanel.add(createFieldLabel("DTMF Received:"));
        lblDtmf = createValLabel("-");
        rxPanel.add(lblDtmf);

        panel.add(txPanel);
        panel.add(rxPanel);
        return panel;
    }

    private JPanel createSecurityPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "SIP Security & SRTP Details", 
                TitledBorder.DEFAULT_JUSTIFICATION, 
                TitledBorder.DEFAULT_POSITION, 
                new Font("Segoe UI", Font.BOLD, 12), 
                new Color(244, 63, 94) // rose-500
        ));
        
        JPanel grid = new JPanel(new GridLayout(3, 2, 5, 5));
        grid.setOpaque(false);
        
        grid.add(createFieldLabel("TLS Version:"));
        lblTlsVersion = createValLabel("-");
        grid.add(lblTlsVersion);
        
        grid.add(createFieldLabel("Negotiated Cipher:"));
        lblCipherSuite = createValLabel("-");
        grid.add(lblCipherSuite);
        
        grid.add(createFieldLabel("SRTP SDES Suite:"));
        lblSrtpCipher = createValLabel("-");
        grid.add(lblSrtpCipher);
        
        panel.add(grid, BorderLayout.NORTH);
        
        JPanel certPanel = new JPanel(new BorderLayout());
        certPanel.setOpaque(false);
        certPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        JLabel certTitle = createFieldLabel("Server SSL Certificate details:");
        txtCertDetails = new JTextArea();
        txtCertDetails.setEditable(false);
        txtCertDetails.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtCertDetails.setBackground(new Color(10, 15, 30));
        txtCertDetails.setForeground(new Color(226, 232, 240));
        txtCertDetails.setLineWrap(true);
        txtCertDetails.setWrapStyleWord(true);
        
        JScrollPane scroll = new JScrollPane(txtCertDetails);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        
        certPanel.add(certTitle, BorderLayout.NORTH);
        certPanel.add(scroll, BorderLayout.CENTER);
        
        panel.add(certPanel, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(148, 163, 184)); // slate-400
        return lbl;
    }

    private JLabel createValLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(Color.WHITE);
        return lbl;
    }

    /** Maps a negotiated RTP payload type number to its codec name (RFC 3551 statics + this app's dynamic offers). */
    private static String codecNameForPt(int pt) {
        switch (pt) {
            case 0: return "PCMU (G.711 u-law)";
            case 8: return "PCMA (G.711 A-law)";
            case 9: return "G722";
            case 18: return "G729";
            case 101: return "telephone-event (DTMF)";
            case 111: return "Opus";
            default: return "PT " + pt + " (unknown)";
        }
    }

    private void updateDiagnostics() {
        LiveAudioController audio = LiveAudioController.getActiveInstance();
        
        // Read last TLS connection details
        String tlsVer = TLSClientSocket_V6.getLastProtocol();
        String tlsSuite = TLSClientSocket_V6.getLastCipherSuite();
        String certSubj = TLSClientSocket_V6.getLastPeerCertSubject();
        String certIss = TLSClientSocket_V6.getLastPeerCertIssuer();
        String certVal = TLSClientSocket_V6.getLastPeerCertValidity();
        
        if (tlsVer != null && !tlsVer.equalsIgnoreCase("None")) {
            lblTlsVersion.setText(tlsVer);
            lblCipherSuite.setText(tlsSuite);
            txtCertDetails.setText("Subject: " + certSubj + "\nIssuer: " + certIss + "\nValidity: " + certVal);
        } else {
            lblTlsVersion.setText("No active TLS connection");
            lblCipherSuite.setText("-");
            txtCertDetails.setText("SSL details will be populated when a TLS call scenario executes.");
        }

        if (audio != null && audio.isAudioRunning()) {
            lblStatus.setText("Status: Live Audio Streaming");
            lblStatus.setForeground(new Color(74, 222, 128)); // green-400
            
            Map<String, Object> stats = audio.getStats();
            
            // Transmit RTP values
            lblTxSsrc.setText(String.format("0x%08X", stats.get("tx_ssrc")));
            int txPt = ((Number) stats.get("tx_payload_type")).intValue();
            lblTxPt.setText(String.valueOf(txPt));
            lblTxCodec.setText(codecNameForPt(txPt));

            Map<String, Object> sender = (Map<String, Object>) stats.get("sender");
            if (sender != null) {
                lblTxPackets.setText(String.valueOf(sender.get("packets_sent")));
                lblTxBytes.setText(String.valueOf(sender.get("bytes_sent")));
            }
            
            // Receive RTP values
            lblRxSsrc.setText(String.format("0x%08X", stats.get("rx_ssrc")));
            int rxPt = ((Number) stats.get("rx_payload_type")).intValue();
            lblRxPt.setText(String.valueOf(rxPt));
            lblRxCodec.setText(codecNameForPt(rxPt));
            lblRxPackets.setText(String.valueOf(stats.get("packets_received")));
            
            Map<String, Object> receiver = (Map<String, Object>) stats.get("receiver");
            if (receiver != null) {
                lblRxBytes.setText(String.valueOf(receiver.get("bytes_received")));
            }
            lblRxDropped.setText(String.valueOf(stats.get("dropped_packets")));
            
            double jitterMs = (double) stats.get("rx_jitter_ms");
            lblRxJitter.setText(String.format("%.2f ms", jitterMs));
            
            long driftMs = (long) stats.get("rx_drift_ms");
            lblRxDrift.setText(driftMs + " ms");

            String dtmf = audio.getLastReceivedDtmf();
            lblDtmf.setText((dtmf == null || dtmf.isEmpty()) ? "(none yet)" : dtmf);

            if (sipinspector.GlobalSettings.isSdpUseSavp()) {
                lblSrtpCipher.setText("AES_CM_128_HMAC_SHA1_80 (SDES)");
                lblSrtpCipher.setForeground(new Color(74, 222, 128));
            } else {
                lblSrtpCipher.setText("Disabled (Plain RTP)");
                lblSrtpCipher.setForeground(new Color(148, 163, 184));
            }
            
        } else {
            RTPSenderThread rtpSender = RTPSenderThread.getActiveInstance();
            if (rtpSender != null && rtpSender.isPlaying()) {
                lblStatus.setText("Status: Playing PCAP Stream");
                lblStatus.setForeground(new Color(74, 222, 128)); // green-400
                
                lblTxSsrc.setText(String.format("0x%08X", rtpSender.getSsrc()));
                int pcapPt = rtpSender.getPayloadType();
                lblTxPt.setText(String.valueOf(pcapPt));
                lblTxCodec.setText(codecNameForPt(pcapPt));
                lblTxPackets.setText(String.valueOf(rtpSender.getPacketsSent()));
                lblTxBytes.setText(String.valueOf(rtpSender.getBytesSent()));

                lblRxSsrc.setText("-");
                lblRxPt.setText("-");
                lblRxCodec.setText("-");
                lblRxPackets.setText("-");
                lblRxBytes.setText("-");
                lblRxDropped.setText("-");
                lblRxJitter.setText("-");
                lblRxDrift.setText("-");
                lblDtmf.setText("-");
                lblSrtpCipher.setText("Disabled (Plain RTP)");
                lblSrtpCipher.setForeground(new Color(148, 163, 184));
            } else {
                lblStatus.setText("Status: No Active Audio Stream (idle until a scenario plays a tone/PCAP or Live Audio is started)");
                lblStatus.setForeground(new Color(239, 68, 68)); // red-500

                lblTxSsrc.setText("-");
                lblTxPt.setText("-");
                lblTxCodec.setText("-");
                lblTxPackets.setText("-");
                lblTxBytes.setText("-");

                lblRxSsrc.setText("-");
                lblRxPt.setText("-");
                lblRxCodec.setText("-");
                lblRxPackets.setText("-");
                lblRxBytes.setText("-");
                lblRxDropped.setText("-");
                lblRxJitter.setText("-");
                lblRxDrift.setText("-");
                lblDtmf.setText("-");
                lblSrtpCipher.setText("-");
            }
        }
    }

    public void stopTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
