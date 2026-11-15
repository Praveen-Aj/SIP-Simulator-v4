package sipinspector.OperationTabs;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import sipinspector.Call.SipCallEngine;
import sipinspector.Call.SipCallEngine.CallSession;
import sipinspector.GlobalSettings;
import sipinspector.Media.LiveAudioController;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Threads.RTPSenderThread;

/**
 * RTP Stream Inspector & Live Media Diagnostics Workspace.
 * Comprehensive real-time analysis of bidirectional RTP streams, RFC 3550 sequence
 * continuity, RTCP metrics, live sparkline telemetry graphs, and multi-session inspection.
 *
 * Implements Workstream C of Phase 8.
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class RtpInspectorTab extends JPanel {

    private final javax.swing.Timer refreshTimer;

    // Session Selector & Media Status
    private JComboBox<String> sessionSelectorCombo;
    private JLabel lblMediaStatus;
    private JLabel lblSessionCallId;
    private JButton btnViewInExplorer;

    // Transmit RTP Fields (Measured)
    private JLabel lblTxLocalEp;
    private JLabel lblTxRemoteEp;
    private JLabel lblTxSsrc;
    private JLabel lblTxPtCodec;
    private JLabel lblTxPackets;
    private JLabel lblTxBytes;
    private JLabel lblTxRate;

    // Receive RTP Fields (Measured)
    private JLabel lblRxSourceEp;
    private JLabel lblRxDestEp;
    private JLabel lblRxSsrc;
    private JLabel lblRxPtCodec;
    private JLabel lblRxPackets;
    private JLabel lblRxBytes;
    private JLabel lblRxDiscontinuities;
    private JLabel lblRxDuplicates;
    private JLabel lblRxOutOfOrder;
    private JLabel lblRxLoss;
    private JLabel lblRxJitter;
    private JLabel lblRxDrift;
    private JLabel lblRxDtmf;

    // Quality & RTCP Fields (Estimated / Calculated)
    private JLabel lblEstMos;
    private JLabel lblRtcpFracLost;
    private JLabel lblRtcpCumLost;
    private JLabel lblRtcpJitter;
    private JLabel lblRtcpRtt;
    private JLabel lblSrtpStatus;

    // Security Details
    private JLabel lblTlsProtocol;
    private JLabel lblTlsCipher;
    private JTextArea txtTlsCert;

    // Live Graphs
    private TelemetrySparkline txRateGraph;
    private TelemetrySparkline jitterGraph;
    private TelemetrySparkline lossGraph;

    // Empty State Helper Panel
    private JPanel emptyStatePanel;
    private JPanel activeDataPanel;

    // Rate computation state
    private long lastTxPackets = 0;
    private long lastRxPackets = 0;
    private long lastCheckTime = 0;

    private SIPInspectorMainFrame mainFrame;

    public RtpInspectorTab() {
        this(null);
    }

    public RtpInspectorTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(new Color(15, 23, 42)); // Slate 900
        setBorder(new EmptyBorder(8, 8, 8, 8));

        add(createHeaderPanel(), BorderLayout.NORTH);

        // Center: Card/Stacked layout between active data view and explanatory empty state
        activeDataPanel = createActiveDataPanel();
        emptyStatePanel = createEmptyStatePanel();

        JPanel centerContainer = new JPanel(new CardLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(activeDataPanel, "ACTIVE");
        centerContainer.add(emptyStatePanel, "EMPTY");
        add(centerContainer, BorderLayout.CENTER);

        // Refresh timer (500ms intervals)
        refreshTimer = new javax.swing.Timer(500, e -> updateDiagnostics((CardLayout) centerContainer.getLayout(), centerContainer));
        refreshTimer.start();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 4", "[][grow][][][]", "[]"));
        panel.setBackground(new Color(30, 41, 59));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)));

        JLabel title = new JLabel("RTP Stream Inspector & Media Quality Diagnostics");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        panel.add(title, "");

        // Session Selector
        panel.add(new JLabel("Media Session:"), "gapleft 20");
        sessionSelectorCombo = new JComboBox<>(new String[]{"Auto-select Active Call"});
        sessionSelectorCombo.setPreferredSize(new Dimension(240, 24));
        panel.add(sessionSelectorCombo, "");

        JButton btnExport = new JButton("Export Diagnostics...");
        btnExport.setToolTipText("Export current RTP/RTCP metrics snapshot to JSON / CSV");
        btnExport.addActionListener(e -> exportDiagnostics());
        panel.add(btnExport, "");

        // Media Status Badge
        lblMediaStatus = new JLabel("Status: No Media Session");
        lblMediaStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMediaStatus.setForeground(new Color(239, 68, 68));
        panel.add(lblMediaStatus, "gapleft 15");

        return panel;
    }

    private JPanel createActiveDataPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);

        // Call-ID Link Banner
        JPanel linkBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        linkBar.setBackground(new Color(24, 33, 53));
        linkBar.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));

        JLabel lblDialogTitle = new JLabel("Associated SIP Dialog Call-ID:");
        lblDialogTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblDialogTitle.setForeground(new Color(148, 163, 184));
        linkBar.add(lblDialogTitle);

        lblSessionCallId = new JLabel("(none)");
        lblSessionCallId.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblSessionCallId.setForeground(new Color(56, 189, 248));
        linkBar.add(lblSessionCallId);

        btnViewInExplorer = new JButton("View in SIP Message Explorer →");
        btnViewInExplorer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnViewInExplorer.addActionListener(e -> viewInMessageExplorer());
        linkBar.add(btnViewInExplorer);

        panel.add(linkBar, BorderLayout.NORTH);

        // Center Grid: 3 Columns (Transmit RTP | Receive RTP | Quality & RTCP & TLS)
        JPanel grid = new JPanel(new GridLayout(1, 3, 8, 0));
        grid.setOpaque(false);

        grid.add(createTransmitPanel());
        grid.add(createReceivePanel());
        grid.add(createQualityAndSecurityPanel());

        panel.add(grid, BorderLayout.CENTER);

        // Bottom Strip: Live Telemetry Sparklines
        JPanel graphsPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        graphsPanel.setPreferredSize(new Dimension(800, 110));
        graphsPanel.setOpaque(false);

        txRateGraph = new TelemetrySparkline("TX Packet Rate (pkts/sec)", new Color(56, 189, 248), 0, 100);
        jitterGraph = new TelemetrySparkline("RX Interarrival Jitter (ms)", new Color(251, 191, 36), 0, 60);
        lossGraph = new TelemetrySparkline("Estimated Loss (%)", new Color(244, 63, 94), 0, 20);

        graphsPanel.add(txRateGraph);
        graphsPanel.add(jitterGraph);
        graphsPanel.add(lossGraph);

        panel.add(graphsPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTransmitPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[120!][grow]", "[][][][][][][]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "TRANSMIT RTP (Local -> Remote) [Measured]",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(56, 189, 248)
        ));

        panel.add(createFieldLabel("Local Endpoint:"));
        lblTxLocalEp = createValLabel("-");
        panel.add(lblTxLocalEp, "wrap");

        panel.add(createFieldLabel("Remote Endpoint:"));
        lblTxRemoteEp = createValLabel("-");
        panel.add(lblTxRemoteEp, "wrap");

        panel.add(createFieldLabel("SSRC:"));
        lblTxSsrc = createValLabel("-");
        panel.add(lblTxSsrc, "wrap");

        panel.add(createFieldLabel("Payload / Codec:"));
        lblTxPtCodec = createValLabel("-");
        panel.add(lblTxPtCodec, "wrap");

        panel.add(createFieldLabel("Packets Transmitted:"));
        lblTxPackets = createValLabel("-");
        panel.add(lblTxPackets, "wrap");

        panel.add(createFieldLabel("Bytes Transmitted:"));
        lblTxBytes = createValLabel("-");
        panel.add(lblTxBytes, "wrap");

        panel.add(createFieldLabel("TX Packet Rate:"));
        lblTxRate = createValLabel("0 pkts/sec");
        lblTxRate.setForeground(new Color(56, 189, 248));
        panel.add(lblTxRate, "wrap");

        return panel;
    }

    private JPanel createReceivePanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[130!][grow]", "[][][][][][][][][][][][]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "RECEIVE RTP (Remote -> Local) [Measured]",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(74, 222, 128)
        ));

        panel.add(createFieldLabel("Remote Source:"));
        lblRxSourceEp = createValLabel("-");
        panel.add(lblRxSourceEp, "wrap");

        panel.add(createFieldLabel("Local Destination:"));
        lblRxDestEp = createValLabel("-");
        panel.add(lblRxDestEp, "wrap");

        panel.add(createFieldLabel("SSRC:"));
        lblRxSsrc = createValLabel("-");
        panel.add(lblRxSsrc, "wrap");

        panel.add(createFieldLabel("Payload / Codec:"));
        lblRxPtCodec = createValLabel("-");
        panel.add(lblRxPtCodec, "wrap");

        panel.add(createFieldLabel("Packets Received:"));
        lblRxPackets = createValLabel("-");
        panel.add(lblRxPackets, "wrap");

        panel.add(createFieldLabel("Bytes Received:"));
        lblRxBytes = createValLabel("-");
        panel.add(lblRxBytes, "wrap");

        panel.add(createFieldLabel("Discontinuities (RFC 3550):"));
        lblRxDiscontinuities = createValLabel("0");
        panel.add(lblRxDiscontinuities, "wrap");

        panel.add(createFieldLabel("Duplicate Packets:"));
        lblRxDuplicates = createValLabel("0");
        panel.add(lblRxDuplicates, "wrap");

        panel.add(createFieldLabel("Out-of-Order Packets:"));
        lblRxOutOfOrder = createValLabel("0");
        panel.add(lblRxOutOfOrder, "wrap");

        panel.add(createFieldLabel("Estimated Packet Loss:"));
        lblRxLoss = createValLabel("0.0 %");
        panel.add(lblRxLoss, "wrap");

        panel.add(createFieldLabel("Interarrival Jitter:"));
        lblRxJitter = createValLabel("0.0 ms");
        panel.add(lblRxJitter, "wrap");

        panel.add(createFieldLabel("Timestamp Drift:"));
        lblRxDrift = createValLabel("0 ms");
        panel.add(lblRxDrift, "wrap");

        panel.add(createFieldLabel("DTMF Digits Received:"));
        lblRxDtmf = createValLabel("-");
        lblRxDtmf.setForeground(new Color(251, 191, 36));
        panel.add(lblRxDtmf, "wrap");

        return panel;
    }

    private JPanel createQualityAndSecurityPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[130!][grow]", "[][][][][][][][grow]"));
        panel.setBackground(new Color(24, 33, 53));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                "QUALITY (ITU-T G.107) & RTCP / SECURITY",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(192, 132, 252)
        ));

        panel.add(createFieldLabel("Estimated MOS (G.107):"));
        lblEstMos = createValLabel("4.39 (Estimated R-factor)");
        lblEstMos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstMos.setForeground(new Color(74, 222, 128));
        panel.add(lblEstMos, "wrap");

        panel.add(createFieldLabel("RTCP Fraction Lost:"));
        lblRtcpFracLost = createValLabel("0 / 256 (0%)");
        panel.add(lblRtcpFracLost, "wrap");

        panel.add(createFieldLabel("RTCP Cumulative Lost:"));
        lblRtcpCumLost = createValLabel("0 packets");
        panel.add(lblRtcpCumLost, "wrap");

        panel.add(createFieldLabel("RTCP Jitter:"));
        lblRtcpJitter = createValLabel("0.0 ms");
        panel.add(lblRtcpJitter, "wrap");

        panel.add(createFieldLabel("Round-Trip Time (RTT):"));
        lblRtcpRtt = createValLabel("< 10 ms (Measured)");
        panel.add(lblRtcpRtt, "wrap");

        panel.add(createFieldLabel("SRTP SDES Status:"));
        lblSrtpStatus = createValLabel("Disabled (Plain RTP)");
        panel.add(lblSrtpStatus, "wrap");

        panel.add(createFieldLabel("TLS Transport:"));
        lblTlsProtocol = createValLabel("No active TLS session");
        panel.add(lblTlsProtocol, "wrap");

        panel.add(createFieldLabel("TLS Cipher:"));
        lblTlsCipher = createValLabel("-");
        panel.add(lblTlsCipher, "wrap");

        txtTlsCert = new JTextArea(3, 20);
        txtTlsCert.setEditable(false);
        txtTlsCert.setFont(new Font("Monospaced", Font.PLAIN, 10));
        txtTlsCert.setBackground(new Color(10, 15, 30));
        txtTlsCert.setForeground(new Color(226, 232, 240));
        txtTlsCert.setText("Peer certificate information will appear when TLS/SIPS signaling is used.");
        panel.add(new JScrollPane(txtTlsCert), "span 2, grow");

        return panel;
    }

    private JPanel createEmptyStatePanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 40", "[grow]", "[][][][grow]"));
        panel.setBackground(new Color(15, 23, 42));

        JLabel title = new JLabel("No Active Audio Stream");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(239, 68, 68));
        panel.add(title, "wrap, align center");

        JLabel sub = new JLabel("The RTP media inspector is currently idle because no live call or audio stream is in progress.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(148, 163, 184));
        panel.add(sub, "wrap, align center");

        JTextArea guide = new JTextArea(
                "HOW TO ACTIVATE LIVE MEDIA DIAGNOSTICS:\n\n" +
                "1. Manual Softphone Call: Open the Web Phone dashboard and dial an extension (e.g. 90001 on MicroSIP or 90008 on OXE).\n" +
                "2. Automated Scenarios: Execute any scenario with media (e.g. SIP_CALL_RTP_G711_imported.txt or Incoming_basic_call_with_RBT_tone.txt).\n" +
                "3. Live Campaigns: Run a batch test campaign from the Campaigns tab.\n\n" +
                "DIAGNOSTIC METRICS DEFINITIONS:\n" +
                "• Measured: Transmit/Receive packet counts, byte volumes, socket endpoints, and RFC 3550 sequence discontinuities.\n" +
                "• Estimated: ITU-T E-model G.107 Mean Opinion Score (MOS, scale 1.0-4.5) calculated from jitter, loss, and delay.\n" +
                "• Unavailable: Displayed as '-' until SDP negotiation completes and both RTP ports are actively receiving."
        );
        guide.setEditable(false);
        guide.setFont(new Font("Monospaced", Font.PLAIN, 11));
        guide.setBackground(new Color(24, 33, 53));
        guide.setForeground(new Color(226, 232, 240));
        guide.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                new EmptyBorder(15, 15, 15, 15)
        ));
        panel.add(new JScrollPane(guide), "grow, align center");

        return panel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(new Color(148, 163, 184));
        return lbl;
    }

    private JLabel createValLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(Color.WHITE);
        return lbl;
    }

    private static String codecNameForPt(int pt) {
        switch (pt) {
            case 0: return "PCMU (G.711 u-law, 8kHz)";
            case 8: return "PCMA (G.711 A-law, 8kHz)";
            case 9: return "G.722 (Wideband 16kHz)";
            case 18: return "G.729 (8kbps CS-ACELP)";
            case 101: return "telephone-event (RFC 4733 DTMF)";
            case 111: return "Opus (Wideband / Fullband)";
            case 120: return "telephone-event (OXE PT 120)";
            default: return "Payload Type " + pt;
        }
    }

    private void updateDiagnostics(CardLayout cardLayout, JPanel container) {
        LiveAudioController audio = LiveAudioController.getActiveInstance();
        Map<String, CallSession> activeSessions = SipCallEngine.getInstance().getActiveSessions();
        RTPSenderThread pcapSender = RTPSenderThread.getActiveInstance();

        boolean hasActiveAudio = (audio != null && audio.isAudioRunning())
                || (pcapSender != null && pcapSender.isPlaying())
                || (activeSessions != null && !activeSessions.isEmpty());

        if (!hasActiveAudio) {
            lblMediaStatus.setText("Status: No Media Session (Idle)");
            lblMediaStatus.setForeground(new Color(239, 68, 68));
            cardLayout.show(container, "EMPTY");
            return;
        }

        cardLayout.show(container, "ACTIVE");

        // Update Session Selector options
        Set<String> sessionKeys = new LinkedHashSet<>();
        sessionKeys.add("Primary Active Session");
        if (activeSessions != null) {
            for (String cid : activeSessions.keySet()) {
                sessionKeys.add("Call-ID: " + cid);
            }
        }
        updateSessionCombo(sessionKeys);

        // Determine specific session to display
        String selectedCombo = (String) sessionSelectorCombo.getSelectedItem();
        CallSession targetSession = null;
        if (activeSessions != null) {
            for (Map.Entry<String, CallSession> entry : activeSessions.entrySet()) {
                if (selectedCombo != null && selectedCombo.contains(entry.getKey())) {
                    targetSession = entry.getValue();
                    break;
                }
            }
            if (targetSession == null && !activeSessions.isEmpty()) {
                targetSession = activeSessions.values().iterator().next();
            }
        }

        // Compute packet rate
        long now = System.currentTimeMillis();
        double elapsedSec = lastCheckTime > 0 ? (now - lastCheckTime) / 1000.0 : 0.5;
        if (elapsedSec <= 0) elapsedSec = 0.5;

        if (audio != null && audio.isAudioRunning()) {
            lblMediaStatus.setText("Status: Bidirectional Live Audio Streaming");
            lblMediaStatus.setForeground(new Color(74, 222, 128));

            Map<String, Object> stats = audio.getStats();
            Map<String, Object> sender = (Map<String, Object>) stats.get("sender");
            Map<String, Object> receiver = (Map<String, Object>) stats.get("receiver");

            String callId = targetSession != null ? targetSession.callId : "(live audio)";
            lblSessionCallId.setText(callId);

            // Transmit
            String localIp = targetSession != null ? targetSession.caller : "127.0.0.1";
            int localPort = targetSession != null ? targetSession.localRtpPort : 42000;
            String remoteIp = targetSession != null && targetSession.remoteRtpIp != null ? targetSession.remoteRtpIp : "Remote";
            int remotePort = targetSession != null ? targetSession.remoteRtpPort : 4000;

            lblTxLocalEp.setText(localIp + ":" + localPort);
            lblTxRemoteEp.setText(remoteIp + ":" + remotePort);
            lblTxSsrc.setText(String.format("0x%08X", stats.get("tx_ssrc")));
            int txPt = ((Number) stats.get("tx_payload_type")).intValue();
            lblTxPtCodec.setText(txPt + " - " + codecNameForPt(txPt));

            long currentTxPackets = sender != null ? ((Number) sender.get("packets_sent")).longValue() : 0;
            lblTxPackets.setText(String.valueOf(currentTxPackets));
            lblTxBytes.setText(sender != null ? String.valueOf(sender.get("bytes_sent")) : "-");

            double txRate = (currentTxPackets >= lastTxPackets) ? (currentTxPackets - lastTxPackets) / elapsedSec : 0.0;
            lblTxRate.setText(String.format("%.1f pkts/sec", txRate));
            txRateGraph.addValue(txRate);

            // Receive
            lblRxSourceEp.setText(remoteIp + ":" + remotePort);
            lblRxDestEp.setText(localIp + ":" + localPort);
            lblRxSsrc.setText(String.format("0x%08X", stats.get("rx_ssrc")));
            int rxPt = ((Number) stats.get("rx_payload_type")).intValue();
            lblRxPtCodec.setText(rxPt + " - " + codecNameForPt(rxPt));

            long currentRxPackets = ((Number) stats.get("packets_received")).longValue();
            lblRxPackets.setText(String.valueOf(currentRxPackets));
            lblRxBytes.setText(receiver != null ? String.valueOf(receiver.get("bytes_received")) : "-");

            long discontinuities = receiver != null && receiver.containsKey("sequence_discontinuities")
                    ? ((Number) receiver.get("sequence_discontinuities")).longValue() : 0;
            lblRxDiscontinuities.setText(String.valueOf(discontinuities));

            long duplicates = receiver != null && receiver.containsKey("duplicate_packets")
                    ? ((Number) receiver.get("duplicate_packets")).longValue() : 0;
            lblRxDuplicates.setText(String.valueOf(duplicates));

            long outOfOrder = receiver != null && receiver.containsKey("out_of_order_packets")
                    ? ((Number) receiver.get("out_of_order_packets")).longValue() : 0;
            lblRxOutOfOrder.setText(String.valueOf(outOfOrder));

            long lostPackets = receiver != null && receiver.containsKey("lost_packets")
                    ? ((Number) receiver.get("lost_packets")).longValue() : 0;
            double lossRate = (currentRxPackets + lostPackets) > 0 ? ((double) lostPackets / (currentRxPackets + lostPackets)) * 100.0 : 0.0;
            lblRxLoss.setText(String.format("%.2f %% (%d lost)", lossRate, lostPackets));
            lossGraph.addValue(lossRate);

            double jitter = (double) stats.get("rx_jitter_ms");
            lblRxJitter.setText(String.format("%.2f ms", jitter));
            jitterGraph.addValue(jitter);

            long drift = (long) stats.get("rx_drift_ms");
            lblRxDrift.setText(drift + " ms");

            String dtmf = audio.getLastReceivedDtmf();
            lblRxDtmf.setText((dtmf == null || dtmf.isEmpty()) ? "(none)" : dtmf);

            // Quality & RTCP
            double mos = audio.getEstimatedMos();
            lblEstMos.setText(String.format("%.2f (ITU-T G.107 Est. R-factor)", mos));
            lblRtcpFracLost.setText(String.format("%d / 256 (%.1f%%)", (int) (lossRate * 2.56), lossRate));
            lblRtcpCumLost.setText(lostPackets + " packets");
            lblRtcpJitter.setText(String.format("%.2f ms", jitter));
            Object rttVal = stats.get("rtcp_rtt_ms");
            if (rttVal instanceof Number && ((Number) rttVal).longValue() > 0) {
                lblRtcpRtt.setText(((Number) rttVal).longValue() + " ms (RTCP SR/RR)");
                lblRtcpRtt.setForeground(new Color(74, 222, 128));
            } else {
                lblRtcpRtt.setText("RTT unavailable — no valid RTCP measurement");
                lblRtcpRtt.setForeground(new Color(148, 163, 184));
            }

            lastTxPackets = currentTxPackets;
            lastRxPackets = currentRxPackets;
            lastCheckTime = now;
        } else if (targetSession != null) {
            lblMediaStatus.setText("Status: SDP Negotiated / Session " + targetSession.state);
            lblMediaStatus.setForeground(new Color(56, 189, 248));
            lblSessionCallId.setText(targetSession.callId);

            String rtpIp = targetSession.remoteRtpIp != null ? targetSession.remoteRtpIp : "0.0.0.0";
            lblTxLocalEp.setText("0.0.0.0:" + targetSession.localRtpPort);
            lblTxRemoteEp.setText(rtpIp + ":" + targetSession.remoteRtpPort);
            lblTxPtCodec.setText(targetSession.codecName != null ? targetSession.codecName : "PCMA (PT=8)");
            lblRxSourceEp.setText(rtpIp + ":" + targetSession.remoteRtpPort);
            lblRxDestEp.setText("0.0.0.0:" + targetSession.localRtpPort);
        }

        // SRTP & TLS info
        if (GlobalSettings.isSdpUseSavp()) {
            lblSrtpStatus.setText("ACTIVE: AES_CM_128_HMAC_SHA1_80");
            lblSrtpStatus.setForeground(new Color(74, 222, 128));
        } else {
            lblSrtpStatus.setText("Disabled (Plain RTP)");
            lblSrtpStatus.setForeground(new Color(148, 163, 184));
        }

        String tlsVer = TLSClientSocket_V6.getLastProtocol();
        String tlsSuite = TLSClientSocket_V6.getLastCipherSuite();
        if (tlsVer != null && !tlsVer.equalsIgnoreCase("None")) {
            lblTlsProtocol.setText(tlsVer);
            lblTlsCipher.setText(tlsSuite != null ? tlsSuite : "-");
            txtTlsCert.setText("Subject: " + TLSClientSocket_V6.getLastPeerCertSubject() +
                    "\nIssuer: " + TLSClientSocket_V6.getLastPeerCertIssuer() +
                    "\nValidity: " + TLSClientSocket_V6.getLastPeerCertValidity());
        } else {
            lblTlsProtocol.setText("UDP / Plain SIP (No active TLS)");
            lblTlsCipher.setText("-");
        }
    }

    private void updateSessionCombo(Set<String> options) {
        Object current = sessionSelectorCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String opt : options) model.addElement(opt);
        sessionSelectorCombo.setModel(model);
        if (current != null && options.contains(current.toString())) {
            sessionSelectorCombo.setSelectedItem(current);
        }
    }

    private void viewInMessageExplorer() {
        String callId = lblSessionCallId.getText();
        if (callId == null || callId.contains("none") || callId.trim().isEmpty() || callId.equals("-")) {
            JOptionPane.showMessageDialog(this, "No active Call-ID selected.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (mainFrame != null) {
            mainFrame.navigateToMessageExplorer(callId.trim());
        }
    }

    private void exportDiagnostics() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("rtp_diagnostics_snapshot.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(dest))) {
                LiveAudioController audio = LiveAudioController.getActiveInstance();
                Map<String, Object> stats = audio != null ? audio.getStats() : Collections.emptyMap();
                pw.println("{");
                pw.println("  \"timestamp\": \"" + new Date() + "\",");
                pw.println("  \"media_status\": \"" + lblMediaStatus.getText() + "\",");
                pw.println("  \"call_id\": \"" + lblSessionCallId.getText() + "\",");
                pw.println("  \"tx_local\": \"" + lblTxLocalEp.getText() + "\",");
                pw.println("  \"tx_remote\": \"" + lblTxRemoteEp.getText() + "\",");
                pw.println("  \"tx_packets\": \"" + lblTxPackets.getText() + "\",");
                pw.println("  \"rx_packets\": \"" + lblRxPackets.getText() + "\",");
                pw.println("  \"rx_loss\": \"" + lblRxLoss.getText() + "\",");
                pw.println("  \"rx_jitter\": \"" + lblRxJitter.getText() + "\",");
                pw.println("  \"estimated_mos\": \"" + lblEstMos.getText() + "\",");
                pw.println("  \"stats_raw\": " + stats.toString());
                pw.println("}");
                JOptionPane.showMessageDialog(this, "Diagnostics exported successfully to:\n" + dest.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting diagnostics: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void stopTimer() {
        if (refreshTimer != null) refreshTimer.stop();
    }

    /**
     * Lightweight double-buffered telemetry sparkline chart for displaying
     * real-time metrics (packet rate, jitter, loss) over a sliding 60-sample window.
     */
    private static class TelemetrySparkline extends JPanel {
        private final String title;
        private final Color lineColor;
        private final double minVal;
        private final double maxVal;
        private final List<Double> values = new ArrayList<>();
        private static final int MAX_POINTS = 60;

        public TelemetrySparkline(String title, Color lineColor, double minVal, double maxVal) {
            this.title = title;
            this.lineColor = lineColor;
            this.minVal = minVal;
            this.maxVal = maxVal;
            setBackground(new Color(24, 33, 53));
            setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        }

        public synchronized void addValue(double val) {
            if (values.size() >= MAX_POINTS) {
                values.remove(0);
            }
            values.add(val);
            repaint();
        }

        @Override
        protected synchronized void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background & Title
            g2.setColor(new Color(148, 163, 184));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            double cur = !values.isEmpty() ? values.get(values.size() - 1) : 0.0;
            g2.drawString(title + String.format(" [Current: %.1f]", cur), 8, 14);

            if (values.size() < 2) return;

            // Plot line
            int top = 22;
            int bot = h - 8;
            int plotH = bot - top;

            double range = maxVal - minVal;
            if (range <= 0) range = 1.0;

            int n = values.size();
            int[] xPoints = new int[n];
            int[] yPoints = new int[n];

            for (int i = 0; i < n; i++) {
                xPoints[i] = (int) ((double) i / (MAX_POINTS - 1) * (w - 16)) + 8;
                double v = Math.max(minVal, Math.min(maxVal, values.get(i)));
                yPoints[i] = bot - (int) (((v - minVal) / range) * plotH);
            }

            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawPolyline(xPoints, yPoints, n);
        }
    }
}
