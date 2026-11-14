package sipinspector.Utils;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import sipinspector.Media.LiveAudioController;
import sipinspector.Threads.RTPSenderThread;

/**
 * Helper to collect RTP statistics from active media threads (either
 * LiveAudioController or RTPSenderThread) and write them to config/rtp_diagnostics.json
 * so that both the Swing UI and the Web React dashboard can view them.
 * 
 * @author SIP Support Team
 */
public class RtpDiagnosticsWriter {

    @SuppressWarnings("unchecked")
    public static void writeUnifiedRtpDiagnostics() {
        try {
            boolean active = false;
            long packetsSent = 0;
            long packetsReceived = 0;
            long authFailures = 0;
            long droppedPackets = 0;
            int jitterDepth = 0;
            long rxSsrc = -1;
            int rxPt = -1;
            double rxJitter = 0.0;
            long rxDrift = 0;
            long txSsrc = -1;
            int txPt = -1;
            long txBytes = 0;
            long rxBytes = 0;

            LiveAudioController audio = LiveAudioController.getActiveInstance();
            RTPSenderThread rtpSender = RTPSenderThread.getActiveInstance();

            if (audio != null && audio.isAudioRunning()) {
                active = true;
                Map<String, Object> stats = audio.getStats();
                packetsSent = ((Number) stats.getOrDefault("packets_sent", 0L)).longValue();
                packetsReceived = ((Number) stats.getOrDefault("packets_received", 0L)).longValue();
                authFailures = ((Number) stats.getOrDefault("auth_failures", 0L)).longValue();
                droppedPackets = ((Number) stats.getOrDefault("dropped_packets", 0L)).longValue();
                jitterDepth = ((Number) stats.getOrDefault("current_jitter_buffer_depth", 0)).intValue();
                rxSsrc = ((Number) stats.getOrDefault("rx_ssrc", -1L)).longValue();
                rxPt = ((Number) stats.getOrDefault("rx_payload_type", -1)).intValue();
                rxJitter = ((Number) stats.getOrDefault("rx_jitter_ms", 0.0)).doubleValue();
                rxDrift = ((Number) stats.getOrDefault("rx_drift_ms", 0L)).longValue();
                txSsrc = ((Number) stats.getOrDefault("tx_ssrc", -1L)).longValue();
                txPt = ((Number) stats.getOrDefault("tx_payload_type", -1)).intValue();

                Map<String, Object> sender = (Map<String, Object>) stats.get("sender");
                if (sender != null) {
                    txBytes = ((Number) sender.getOrDefault("bytes_sent", 0L)).longValue();
                }
                Map<String, Object> receiver = (Map<String, Object>) stats.get("receiver");
                if (receiver != null) {
                    rxBytes = ((Number) receiver.getOrDefault("bytes_received", 0L)).longValue();
                }
            } else if (rtpSender != null && rtpSender.isPlaying()) {
                active = true;
                packetsSent = rtpSender.getPacketsSent();
                txBytes = rtpSender.getBytesSent();
                txSsrc = rtpSender.getSsrc();
                txPt = rtpSender.getPayloadType();
            }

            File dir = new File("config");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, "rtp_diagnostics.json");

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"audio_running\":").append(active).append(",");
            sb.append("\"packets_sent\":").append(packetsSent).append(",");
            sb.append("\"packets_received\":").append(packetsReceived).append(",");
            sb.append("\"auth_failures\":").append(authFailures).append(",");
            sb.append("\"dropped_packets\":").append(droppedPackets).append(",");
            sb.append("\"current_jitter_buffer_depth\":").append(jitterDepth).append(",");
            sb.append("\"uptime_seconds\":").append(0).append(",");
            sb.append("\"rx_ssrc\":").append(rxSsrc).append(",");
            sb.append("\"rx_payload_type\":").append(rxPt).append(",");
            sb.append("\"rx_jitter_ms\":").append(rxJitter).append(",");
            sb.append("\"rx_drift_ms\":").append(rxDrift).append(",");
            sb.append("\"tx_ssrc\":").append(txSsrc).append(",");
            sb.append("\"tx_payload_type\":").append(txPt).append(",");

            sb.append("\"diag_active_sender_threads\":").append(active ? 1 : 0).append(",");
            sb.append("\"diag_active_receiver_threads\":").append(audio != null && audio.isAudioRunning() ? 1 : 0).append(",");
            sb.append("\"diag_open_sockets\":").append(active ? 1 : 0).append(",");
            sb.append("\"diag_open_mic_lines\":").append(audio != null && audio.isAudioRunning() ? 1 : 0).append(",");
            sb.append("\"diag_open_speaker_lines\":").append(audio != null && audio.isAudioRunning() ? 1 : 0).append(",");
            sb.append("\"diag_controller_starts\":1,");
            sb.append("\"diag_controller_stops\":0,");

            sb.append("\"sender\":{");
            sb.append("\"packets_sent\":").append(packetsSent).append(",");
            sb.append("\"bytes_sent\":").append(txBytes).append(",");
            sb.append("\"elapsed_ms\":").append(0).append(",");
            sb.append("\"running\":").append(active).append(",");
            sb.append("\"ssrc\":").append(txSsrc).append(",");
            sb.append("\"payload_type\":").append(txPt);
            sb.append("},");

            sb.append("\"receiver\":{");
            sb.append("\"packets_received\":").append(packetsReceived).append(",");
            sb.append("\"bytes_received\":").append(rxBytes).append(",");
            sb.append("\"packets_dropped\":").append(droppedPackets).append(",");
            sb.append("\"auth_failures\":").append(authFailures).append(",");
            sb.append("\"malformed_packets\":").append(0).append(",");
            sb.append("\"jitter_buffer_size\":").append(jitterDepth).append(",");
            sb.append("\"elapsed_ms\":").append(0).append(",");
            sb.append("\"running\":").append(audio != null && audio.isAudioRunning()).append(",");
            sb.append("\"ssrc\":").append(rxSsrc).append(",");
            sb.append("\"payload_type\":").append(rxPt).append(",");
            sb.append("\"jitter_ms\":").append(rxJitter).append(",");
            sb.append("\"drift_ms\":").append(rxDrift);
            sb.append("}");

            sb.append("}");

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(sb.toString());
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public static void writeStoppedDiagnostics() {
        try {
            File dir = new File("config");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, "rtp_diagnostics.json");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("{\"audio_running\":false,\"packets_sent\":0,\"packets_received\":0,\"auth_failures\":0,\"dropped_packets\":0,\"current_jitter_buffer_depth\":0,\"uptime_seconds\":0,\"rx_ssrc\":-1,\"rx_payload_type\":-1,\"rx_jitter_ms\":0.0,\"rx_drift_ms\":0,\"tx_ssrc\":-1,\"tx_payload_type\":-1}");
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
