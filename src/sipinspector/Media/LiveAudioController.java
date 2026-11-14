/*
 *  SIP Inspector - Live Audio Support v3
 *  Live Audio Controller (Orchestration)
 */
package sipinspector.Media;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import sipinspector.GlobalSettings;
import sipinspector.Utils.TLSDebugLogger;

/**
 * High-level controller for live two-way audio.
 * Manages sender and receiver threads, provides start/stop controls,
 * and statistics collection.
 * 
 * @author SIP Simulator v3
 */
public class LiveAudioController {
    
    private static volatile LiveAudioController activeInstance = null;
    private java.util.Timer fileWriteTimer = null;
    
    public static LiveAudioController getActiveInstance() {
        return activeInstance;
    }
    
    private LiveAudioSenderThread senderThread = null;
    private LiveAudioReceiverThread receiverThread = null;

    private DatagramSocket mediaSocket = null;
    private InetAddress remoteAddress = null;
    private int remotePort = 0;
    private InetAddress localAddress = null;
    private int localMediaPort = 0;

    private SrtpContext srtp = null;
    
    private volatile boolean audioRunning = false;
    private volatile boolean initialized = false;
    private volatile String lastError = null;
    private long sessionStartMs = 0L;
    private long lastPacketsSent = 0L;
    private long lastPacketsReceived = 0L;
    private long lastAuthFailures = 0L;
    private long lastDroppedPackets = 0L;
    private int lastJitterDepth = 0;
    
    // Listeners for audio state changes
    private List<AudioStateListener> listeners = new ArrayList<>();
    
    /**
     * Interface for audio state change notifications
     */
    public interface AudioStateListener {
        void onAudioStarted();
        void onAudioStopped();
        void onAudioError(String error);
    }
    
    /**
     * Initialize from {@link GlobalSettings} (mandatory integration rule).
     * Sender target:
     * - GlobalSettings.getRemoteIPAddr()
     * - GlobalSettings.getRemotePort()
     * Receiver bind:
     * - GlobalSettings.getMediaPort()
     */
    public void initializeFromGlobalSettings() {
        try {
            String remoteIpStr = GlobalSettings.getRemoteIPAddr();
            String remotePortStr = GlobalSettings.getRemotePort();
            String negotiatedRtpIp = GlobalSettings.getSetting("RTP_Remote_Address");
            String negotiatedRtpPort = GlobalSettings.getSetting("RTP_Remote_Port");
            String localIpStr = GlobalSettings.getLocalIP();
            // Prefer the port we ACTUALLY advertised in our own SDP (captured by
            // SIPCall.justSendMsg as "RTP_Local_Port") over the static configured
            // media port. Scenarios advertise the m=audio port from various
            // tokens ([media_port], [field1], ...), so the configured
            // getMediaPort() is not always what the peer was told to send to.
            // Binding the advertised port is what makes inbound RTP actually
            // reach this receiver instead of landing on an unbound port.
            String mediaPortStr = GlobalSettings.getSetting("RTP_Local_Port");
            String mediaPortSource = "advertised-SDP";
            if (mediaPortStr == null || mediaPortStr.trim().isEmpty()) {
                mediaPortStr = GlobalSettings.getMediaPort();
                mediaPortSource = "configured-media-port";
            }

            if (remoteIpStr == null || remoteIpStr.trim().isEmpty()) {
                throw new IllegalStateException("Remote IP is not set");
            }
            if (remotePortStr == null || remotePortStr.trim().isEmpty()) {
                throw new IllegalStateException("Remote port is not set");
            }
            if (localIpStr == null || localIpStr.trim().isEmpty()) {
                throw new IllegalStateException("Local IP is not set");
            }
            if (mediaPortStr == null || mediaPortStr.trim().isEmpty()) {
                throw new IllegalStateException("Media port is not set");
            }

            String targetIp = remoteIpStr.trim();
            String targetPort = remotePortStr.trim();
            boolean usingNegotiatedTarget = false;
            if (negotiatedRtpIp != null && negotiatedRtpIp.trim().length() > 0
                    && negotiatedRtpPort != null && negotiatedRtpPort.trim().length() > 0) {
                targetIp = negotiatedRtpIp.trim();
                targetPort = negotiatedRtpPort.trim();
                usingNegotiatedTarget = true;
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[RTP TARGET] ip=%s port=%s source=SDP", targetIp, targetPort);
                }
            } else if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[RTP TARGET] ip=%s port=%s source=GlobalSettingsFallback", targetIp, targetPort);
            }

            // Safety: fail closed if no negotiated media target is known and fallback equals SIP signaling port.
            if (!usingNegotiatedTarget) {
                int sipPort = Integer.parseInt(remotePortStr.trim());
                int fallbackPort = Integer.parseInt(targetPort);
                if (fallbackPort == sipPort) {
                    throw new IllegalStateException(
                        "No negotiated SDP media target yet. Refusing Live Audio start to SIP signaling port " + sipPort
                    );
                }
            }

            this.remoteAddress = InetAddress.getByName(targetIp);
            this.remotePort = Integer.parseInt(targetPort);
            this.localAddress = InetAddress.getByName(localIpStr.trim());
            this.localMediaPort = Integer.parseInt(mediaPortStr.trim());
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[RTP LOCAL] live-audio receiver binding port=%d (source=%s)",
                        this.localMediaPort, mediaPortSource);
            }

            // Ensure audio devices are initialized and apply persisted device names if available.
            AudioDeviceManager.initializeFromGlobalSettings();

            // SRTP context is required when SAVP is enabled.
            if (GlobalSettings.isSdpUseSavp()) {
                String cryptoLine = getSrtpCryptoSourceOfTruth();
                if (cryptoLine == null || cryptoLine.trim().isEmpty()) {
                    throw new IllegalStateException("SRTP enabled (SAVP) but no SDP crypto line was found");
                }
                this.srtp = SrtpContext.fromSdesCryptoLine(cryptoLine);
            } else {
                this.srtp = null;
            }

            initialized = true;
        } catch (UnknownHostException e) {
            initialized = false;
            throw new IllegalStateException("Invalid IP address: " + e.getMessage(), e);
        } catch (NumberFormatException e) {
            initialized = false;
            throw new IllegalStateException("Invalid port: " + e.getMessage(), e);
        } catch (Exception e) {
            initialized = false;
            throw new IllegalStateException("Live audio init failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start live two-way audio
     * 
     * @return true if started successfully, false if error
     */
    public synchronized boolean startAudio() {
        try {
            if (!initialized) {
                initializeFromGlobalSettings();
            }
        } catch (Exception e) {
            lastError = "Audio init failed: " + e.getMessage();
            notifyError("Audio init failed: " + e.getMessage());
            return false;
        }
        
        if (audioRunning) {
            return true; // Already running
        }
        
        try {
            if (mediaSocket == null || mediaSocket.isClosed()) {
                mediaSocket = openMediaSocket(localAddress, localMediaPort);
                LiveAudioRuntimeDiagnostics.onSocketOpened();
            }

            // Create sender thread (mic → remote)
            senderThread = new LiveAudioSenderThread(
                remoteAddress,
                remotePort,
                mediaSocket,
                srtp
            );
            senderThread.start();
            
            // Create receiver thread (remote → speakers)
            receiverThread = new LiveAudioReceiverThread(
                localMediaPort,
                -1, // Accept any SSRC
                mediaSocket,
                srtp
            );
            receiverThread.start();
            
            audioRunning = true;
            activeInstance = this;
            sessionStartMs = System.currentTimeMillis();
            LiveAudioRuntimeDiagnostics.onControllerStart();
            GlobalSettings.setLiveAudioEnabled(true);
            GlobalSettings.saveSettings();
            sipinspector.Utils.LogManager.logRtpEvent("Live Audio session STARTED"
                    + System.lineSeparator() + "Remote: " + remoteAddress + ":" + remotePort
                    + System.lineSeparator() + "Local media port: " + localMediaPort
                    + System.lineSeparator() + "SRTP: " + (srtp != null ? "enabled" : "disabled")
                    + System.lineSeparator() + "Codec: PCMA (G.711 A-law), PT=8");
            notifyStarted();
            // startDiagnosticsFileWriter(); // Delegated to RtpDiagnosticsWriter
            
            return true;
            
        } catch (Exception e) {
            audioRunning = false;
            lastError = "Failed to start audio: " + e.getMessage();
            notifyError("Failed to start audio: " + e.getMessage());
            cleanup();
            return false;
        }
    }
    
    /**
     * Stop live two-way audio
     */
    public synchronized void stopAudio() {
        if (!audioRunning) {
            return;
        }
        
        audioRunning = false;
        
        try {
            LiveAudioSenderThread sender = senderThread;
            LiveAudioReceiverThread receiver = receiverThread;
            if (sender != null) {
                sender.stopAudio();
            }
            if (receiver != null) {
                receiver.stopAudio();
            }
            closeMediaSocket();
            joinSafely(sender, 1500L, "sender");
            joinSafely(receiver, 1500L, "receiver");
            if (sender != null) {
                lastPacketsSent = sender.getPacketsSent();
            }
            if (receiver != null) {
                lastPacketsReceived = receiver.getPacketsReceived();
                lastAuthFailures = receiver.getAuthFailures();
                lastDroppedPackets = receiver.getPacketsDropped();
                lastJitterDepth = receiver.getJitterBufferSize();
            }
            cleanup();
            LiveAudioRuntimeDiagnostics.onControllerStop();
            GlobalSettings.setLiveAudioEnabled(false);
            GlobalSettings.saveSettings();
            sipinspector.Utils.LogManager.logRtpEvent("Live Audio session STOPPED"
                    + System.lineSeparator() + "Packets sent: " + lastPacketsSent
                    + System.lineSeparator() + "Packets received: " + lastPacketsReceived
                    + System.lineSeparator() + "Packets dropped: " + lastDroppedPackets
                    + System.lineSeparator() + "SRTP auth failures: " + lastAuthFailures
                    + System.lineSeparator() + "Jitter buffer depth at stop: " + lastJitterDepth);
            notifyStopped();
            
        } catch (Exception e) {
            notifyError("Error stopping audio: " + e.getMessage());
        }
    }
    
    /**
     * Pause audio (stops sending/receiving, but keeps threads alive)
     */
    public void pauseAudio() {
        if (senderThread != null) {
            senderThread.pauseAudio();
        }
        if (receiverThread != null) {
            receiverThread.pauseAudio();
        }
    }
    
    /**
     * Resume audio after pause
     */
    public void resumeAudio() {
        if (senderThread != null) {
            senderThread.startAudio();
        }
        if (receiverThread != null) {
            receiverThread.startAudio();
        }
    }
    
    /**
     * Check if audio is currently running
     */
    public boolean isAudioRunning() {
        return audioRunning;
    }

    public String getLastError() {
        return lastError;
    }
    
    /**
     * Get sender statistics
     * 
     * @return Map of statistics
     */
    public Map<String, Object> getSenderStats() {
        Map<String, Object> stats = new HashMap<>();
        
        if (senderThread != null) {
            stats.put("packets_sent", senderThread.getPacketsSent());
            stats.put("bytes_sent", senderThread.getBytesSent());
            stats.put("elapsed_ms", senderThread.getElapsedTimeMs());
            stats.put("running", senderThread.isRunning());
            stats.put("ssrc", senderThread.getSsrc());
            stats.put("payload_type", senderThread.getPayloadType());
        }
        
        return stats;
    }
    
    /**
     * Get receiver statistics
     * 
     * @return Map of statistics
     */
    public Map<String, Object> getReceiverStats() {
        Map<String, Object> stats = new HashMap<>();
        
        if (receiverThread != null) {
            stats.put("packets_received", receiverThread.getPacketsReceived());
            stats.put("bytes_received", receiverThread.getBytesReceived());
            stats.put("packets_dropped", receiverThread.getPacketsDropped());
            stats.put("auth_failures", receiverThread.getAuthFailures());
            stats.put("malformed_packets", receiverThread.getMalformedPackets());
            stats.put("jitter_buffer_size", receiverThread.getJitterBufferSize());
            stats.put("elapsed_ms", receiverThread.getElapsedTimeMs());
            stats.put("running", receiverThread.isRunning());
            stats.put("ssrc", receiverThread.getObservedSsrc());
            stats.put("payload_type", receiverThread.getPayloadType());
            stats.put("jitter_ms", receiverThread.getJitterMs());
            stats.put("drift_ms", receiverThread.getTimestampDriftMs());
        }
        
        return stats;
    }
    
    /**
     * Get combined statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> sender = getSenderStats();
        Map<String, Object> receiver = getReceiverStats();
        stats.put("sender", sender);
        stats.put("receiver", receiver);
        stats.put("audio_running", audioRunning);
        stats.put("packets_sent", sender.containsKey("packets_sent") ? sender.get("packets_sent") : lastPacketsSent);
        stats.put("packets_received", receiver.containsKey("packets_received") ? receiver.get("packets_received") : lastPacketsReceived);
        stats.put("auth_failures", receiver.containsKey("auth_failures") ? receiver.get("auth_failures") : lastAuthFailures);
        stats.put("dropped_packets", receiver.containsKey("packets_dropped") ? receiver.get("packets_dropped") : lastDroppedPackets);
        stats.put("current_jitter_buffer_depth", receiver.containsKey("jitter_buffer_size") ? receiver.get("jitter_buffer_size") : lastJitterDepth);
        stats.put("uptime_seconds", sessionStartMs > 0 ? (System.currentTimeMillis() - sessionStartMs) / 1000L : 0L);
        
        // Advanced RTP metrics
        stats.put("rx_ssrc", receiver.containsKey("ssrc") ? receiver.get("ssrc") : -1);
        stats.put("rx_payload_type", receiver.containsKey("payload_type") ? receiver.get("payload_type") : -1);
        stats.put("rx_jitter_ms", receiver.containsKey("jitter_ms") ? receiver.get("jitter_ms") : 0.0);
        stats.put("rx_drift_ms", receiver.containsKey("drift_ms") ? receiver.get("drift_ms") : 0L);
        stats.put("tx_ssrc", sender.containsKey("ssrc") ? sender.get("ssrc") : -1);
        stats.put("tx_payload_type", sender.containsKey("payload_type") ? sender.get("payload_type") : -1);

        stats.put("diag_active_sender_threads", LiveAudioRuntimeDiagnostics.getActiveSenders());
        stats.put("diag_active_receiver_threads", LiveAudioRuntimeDiagnostics.getActiveReceivers());
        stats.put("diag_open_sockets", LiveAudioRuntimeDiagnostics.getOpenSockets());
        stats.put("diag_open_mic_lines", LiveAudioRuntimeDiagnostics.getOpenMicLines());
        stats.put("diag_open_speaker_lines", LiveAudioRuntimeDiagnostics.getOpenSpeakerLines());
        stats.put("diag_controller_starts", LiveAudioRuntimeDiagnostics.getControllerStartCount());
        stats.put("diag_controller_stops", LiveAudioRuntimeDiagnostics.getControllerStopCount());
        return stats;
    }

    public String getLastReceivedDtmf() {
        if (receiverThread != null) {
            return receiverThread.getLastReceivedDtmf();
        }
        return "";
    }

    public void consumeDtmf(String digits) {
        if (receiverThread != null) {
            receiverThread.consumeDtmf(digits);
        }
    }

    public Set<Integer> getObservedPayloadTypes() {
        if (receiverThread != null) {
            return receiverThread.getObservedPayloadTypes();
        }
        return new HashSet<>();
    }
    
    /**
     * Add listener for audio state changes
     */
    public void addListener(AudioStateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove listener
     */
    public void removeListener(AudioStateListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners that audio started
     */
    private void notifyStarted() {
        for (AudioStateListener listener : listeners) {
            try {
                listener.onAudioStarted();
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Notify all listeners that audio stopped
     */
    private void notifyStopped() {
        for (AudioStateListener listener : listeners) {
            try {
                listener.onAudioStopped();
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Notify all listeners of error
     */
    private void notifyError(String error) {
        for (AudioStateListener listener : listeners) {
            try {
                listener.onAudioError(error);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    private void cleanup() {
        stopDiagnosticsFileWriter();
        activeInstance = null;
        senderThread = null;
        receiverThread = null;
        closeMediaSocket();
    }

    private void startDiagnosticsFileWriter() {
        stopDiagnosticsFileWriter();
        fileWriteTimer = new java.util.Timer("RtpDiagnosticsWriter", true);
        fileWriteTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                writeDiagnosticsFile();
            }
        }, 0, 1000);
    }

    private void stopDiagnosticsFileWriter() {
        if (fileWriteTimer != null) {
            fileWriteTimer.cancel();
            fileWriteTimer = null;
        }
        try {
            java.io.File file = new java.io.File("config/rtp_diagnostics.json");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void writeDiagnosticsFile() {
        try {
            java.io.File dir = new java.io.File("config");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.io.File file = new java.io.File(dir, "rtp_diagnostics.json");
            String json = statsToJson(getStats());
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(json);
            }
        } catch (Exception e) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[RTP DIAG] Failed to write diagnostics file: %s", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String statsToJson(Map<String, Object> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        sb.append("\"audio_running\":").append(stats.get("audio_running")).append(",");
        sb.append("\"packets_sent\":").append(stats.get("packets_sent")).append(",");
        sb.append("\"packets_received\":").append(stats.get("packets_received")).append(",");
        sb.append("\"auth_failures\":").append(stats.get("auth_failures")).append(",");
        sb.append("\"dropped_packets\":").append(stats.get("dropped_packets")).append(",");
        sb.append("\"current_jitter_buffer_depth\":").append(stats.get("current_jitter_buffer_depth")).append(",");
        sb.append("\"uptime_seconds\":").append(stats.get("uptime_seconds")).append(",");
        sb.append("\"rx_ssrc\":").append(stats.get("rx_ssrc")).append(",");
        sb.append("\"rx_payload_type\":").append(stats.get("rx_payload_type")).append(",");
        sb.append("\"rx_jitter_ms\":").append(stats.get("rx_jitter_ms")).append(",");
        sb.append("\"rx_drift_ms\":").append(stats.get("rx_drift_ms")).append(",");
        sb.append("\"tx_ssrc\":").append(stats.get("tx_ssrc")).append(",");
        sb.append("\"tx_payload_type\":").append(stats.get("tx_payload_type")).append(",");
        
        sb.append("\"diag_active_sender_threads\":").append(stats.get("diag_active_sender_threads")).append(",");
        sb.append("\"diag_active_receiver_threads\":").append(stats.get("diag_active_receiver_threads")).append(",");
        sb.append("\"diag_open_sockets\":").append(stats.get("diag_open_sockets")).append(",");
        sb.append("\"diag_open_mic_lines\":").append(stats.get("diag_open_mic_lines")).append(",");
        sb.append("\"diag_open_speaker_lines\":").append(stats.get("diag_open_speaker_lines")).append(",");
        sb.append("\"diag_controller_starts\":").append(stats.get("diag_controller_starts")).append(",");
        sb.append("\"diag_controller_stops\":").append(stats.get("diag_controller_stops")).append(",");
        
        sb.append("\"sender\":{");
        Map<String, Object> sender = (Map<String, Object>) stats.get("sender");
        if (sender != null && !sender.isEmpty()) {
            sb.append("\"packets_sent\":").append(sender.get("packets_sent")).append(",");
            sb.append("\"bytes_sent\":").append(sender.get("bytes_sent")).append(",");
            sb.append("\"elapsed_ms\":").append(sender.get("elapsed_ms")).append(",");
            sb.append("\"running\":").append(sender.get("running")).append(",");
            sb.append("\"ssrc\":").append(sender.get("ssrc")).append(",");
            sb.append("\"payload_type\":").append(sender.get("payload_type"));
        } else {
            sb.append("\"packets_sent\":0,\"bytes_sent\":0,\"elapsed_ms\":0,\"running\":false,\"ssrc\":-1,\"payload_type\":-1");
        }
        sb.append("},");
        
        sb.append("\"receiver\":{");
        Map<String, Object> receiver = (Map<String, Object>) stats.get("receiver");
        if (receiver != null && !receiver.isEmpty()) {
            sb.append("\"packets_received\":").append(receiver.get("packets_received")).append(",");
            sb.append("\"bytes_received\":").append(receiver.get("bytes_received")).append(",");
            sb.append("\"packets_dropped\":").append(receiver.get("packets_dropped")).append(",");
            sb.append("\"auth_failures\":").append(receiver.get("auth_failures")).append(",");
            sb.append("\"malformed_packets\":").append(receiver.get("malformed_packets")).append(",");
            sb.append("\"jitter_buffer_size\":").append(receiver.get("jitter_buffer_size")).append(",");
            sb.append("\"elapsed_ms\":").append(receiver.get("elapsed_ms")).append(",");
            sb.append("\"running\":").append(receiver.get("running")).append(",");
            sb.append("\"ssrc\":").append(receiver.get("ssrc")).append(",");
            sb.append("\"payload_type\":").append(receiver.get("payload_type")).append(",");
            sb.append("\"jitter_ms\":").append(receiver.get("jitter_ms")).append(",");
            sb.append("\"drift_ms\":").append(receiver.get("drift_ms"));
        } else {
            sb.append("\"packets_received\":0,\"bytes_received\":0,\"packets_dropped\":0,\"auth_failures\":0,\"malformed_packets\":0,\"jitter_buffer_size\":0,\"elapsed_ms\":0,\"running\":false,\"ssrc\":-1,\"payload_type\":-1,\"jitter_ms\":0.0,\"drift_ms\":0");
        }
        sb.append("}");
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Set input device
     */
    public void setInputDevice(AudioDeviceManager.AudioDevice device) {
        AudioDeviceManager.setInputDevice(device);
    }
    
    /**
     * Set output device
     */
    public void setOutputDevice(AudioDeviceManager.AudioDevice device) {
        AudioDeviceManager.setOutputDevice(device);
    }
    
    /**
     * Get available input devices
     */
    public List<AudioDeviceManager.AudioDevice> getInputDevices() {
        return AudioDeviceManager.getInputDevices();
    }
    
    /**
     * Get available output devices
     */
    public List<AudioDeviceManager.AudioDevice> getOutputDevices() {
        return AudioDeviceManager.getOutputDevices();
    }

    private static DatagramSocket openMediaSocket(InetAddress localAddress, int localPort) throws SocketException {
        DatagramSocket s = new DatagramSocket(localPort, localAddress);
        s.setReuseAddress(true);
        return s;
    }

    private void closeMediaSocket() {
        if (mediaSocket != null) {
            try {
                mediaSocket.close();
            } catch (Exception e) {
                // ignore
            } finally {
                mediaSocket = null;
                LiveAudioRuntimeDiagnostics.onSocketClosed();
            }
        }
    }

    private static void joinSafely(Thread t, long timeoutMs, String name) {
        if (t == null) {
            return;
        }
        try {
            t.join(timeoutMs);
            if (t.isAlive() && TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("[LiveAudio] join timeout on " + name + " thread");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Crypto source of truth:
     * - Prefer GlobalSettings.getSrtpCryptoLine() populated by SIPMessageEntry-generated SDP
     * - Fallback to GlobalSettings.getCustomSDP()
     */
    private static String getSrtpCryptoSourceOfTruth() {
        String fromGenerated = GlobalSettings.getSrtpCryptoLine();
        if (fromGenerated != null && fromGenerated.trim().length() > 0) {
            return fromGenerated.trim();
        }
        String custom = GlobalSettings.getCustomSDP();
        if (custom != null && custom.trim().length() > 0) {
            int idx = custom.indexOf("a=crypto:");
            if (idx >= 0) {
                int end = custom.indexOf("\r\n", idx);
                if (end < 0) {
                    end = custom.indexOf("\n", idx);
                }
                if (end < 0) {
                    end = custom.length();
                }
                String line = custom.substring(idx, end).trim();
                if (line.length() > 0) {
                    return line;
                }
            }
        }
        return null;
    }
}
