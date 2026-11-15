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
    private static final Map<String, LiveAudioController> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.Timer fileWriteTimer = null;
    
    public static LiveAudioController getActiveInstance() {
        return activeInstance;
    }

    public static LiveAudioController getSession(String callId) {
        if (callId == null) return activeInstance;
        LiveAudioController c = activeSessions.get(callId);
        return c != null ? c : activeInstance;
    }

    public static void registerSession(String callId, LiveAudioController controller) {
        if (callId != null && controller != null) {
            activeSessions.put(callId, controller);
        }
        activeInstance = controller;
    }

    public static void removeSession(String callId) {
        if (callId != null) {
            activeSessions.remove(callId);
        }
    }

    public static Map<String, LiveAudioController> getAllSessions() {
        return new HashMap<>(activeSessions);
    }
    
    private LiveAudioSenderThread senderThread = null;
    private LiveAudioReceiverThread receiverThread = null;
    private RtcpMonitor rtcpMonitor = null;
    private volatile RtpWavRecorder txRecorder = null;
    private volatile RtpWavRecorder rxRecorder = null;
    private String currentCallId = null;

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
     * Explicit per-call initialization for multi-call and API orchestration.
     */
    public void initializeForCall(String callId, String remoteIp, int remoteRtpPort, String localIp, int localRtpPort, int payloadType, boolean savp, String cryptoLine) {
        try {
            this.currentCallId = callId;
            this.remoteAddress = InetAddress.getByName(remoteIp.trim());
            this.remotePort = remoteRtpPort;
            this.localAddress = InetAddress.getByName(localIp.trim());
            this.localMediaPort = localRtpPort;
            
            AudioDeviceManager.initializeFromGlobalSettings();
            
            if (savp && cryptoLine != null && !cryptoLine.trim().isEmpty()) {
                this.srtp = SrtpContext.fromSdesCryptoLine(cryptoLine);
            } else {
                this.srtp = null;
            }
            
            registerSession(callId, this);
            initialized = true;
        } catch (Exception e) {
            initialized = false;
            throw new IllegalStateException("Failed to initialize LiveAudioController for call " + callId + ": " + e.getMessage(), e);
        }
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
            if (txRecorder != null) {
                senderThread.setTxRecorder(txRecorder);
            }
            senderThread.start();
            
            // Start RTCP Monitor on (localMediaPort + 1)
            try {
                rtcpMonitor = new RtcpMonitor(localAddress, localMediaPort, remoteAddress, remotePort);
                rtcpMonitor.start();
            } catch (Exception rtcpEx) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[RTCP INIT WARN] Failed to start RTCP monitor: " + rtcpEx.getMessage());
                }
                rtcpMonitor = null;
            }

            // Create receiver thread (remote → speakers)
            receiverThread = new LiveAudioReceiverThread(
                localMediaPort,
                -1, // Accept any SSRC
                mediaSocket,
                srtp,
                rtcpMonitor
            );
            if (rxRecorder != null) {
                receiverThread.setRecorder(rxRecorder);
            }
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
        stopRecording();
        if (currentCallId != null) {
            removeSession(currentCallId);
        }
        
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
     * Start dual WAV recording for this session.
     */
    public synchronized boolean startRecording(String callId) {
        try {
            if (callId == null || callId.trim().isEmpty()) {
                callId = "call_" + System.currentTimeMillis();
            }
            this.currentCallId = callId;
            java.io.File recDir = sipinspector.Utils.AppPaths.getRecordingsDir();
            if (!recDir.exists()) {
                recDir.mkdirs();
            }
            String safeId = sanitizeFilename(callId);
            java.io.File txFile = new java.io.File(recDir, "tx_" + safeId + ".wav");
            java.io.File rxFile = new java.io.File(recDir, "rx_" + safeId + ".wav");

            txRecorder = new RtpWavRecorder(txFile.getAbsolutePath());
            rxRecorder = new RtpWavRecorder(rxFile.getAbsolutePath());

            if (senderThread != null) {
                senderThread.setTxRecorder(txRecorder);
            }
            if (receiverThread != null) {
                receiverThread.setRecorder(rxRecorder);
            }
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[AUDIO RECORD] Started dual recording for call %s: TX=%s, RX=%s",
                        callId, txFile.getName(), rxFile.getName());
            }
            return true;
        } catch (Exception e) {
            System.err.println("LiveAudioController: failed to start recording: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stop dual WAV recording and flush headers.
     */
    public synchronized void stopRecording() {
        if (txRecorder != null) {
            try {
                txRecorder.close();
            } catch (Exception ignore) {}
        }
        if (rxRecorder != null) {
            try {
                rxRecorder.close();
            } catch (Exception ignore) {}
        }
    }

    private static String sanitizeFilename(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public void setMicrophoneMuted(boolean muted) {
        if (senderThread != null) {
            senderThread.setMuted(muted);
        }
    }

    public boolean isMicrophoneMuted() {
        return senderThread != null && senderThread.isMuted();
    }

    public void setSpeakerMuted(boolean muted) {
        if (receiverThread != null) {
            receiverThread.setSpeakerMuted(muted);
        }
    }

    public boolean isSpeakerMuted() {
        return receiverThread != null && receiverThread.isSpeakerMuted();
    }

    public void setAudioSourceMode(LiveAudioSenderThread.AudioSourceMode mode) {
        if (senderThread != null) {
            senderThread.setAudioSourceMode(mode);
        }
    }

    public RtpWavRecorder getTxRecorder() {
        return txRecorder;
    }

    public RtpWavRecorder getRxRecorder() {
        return rxRecorder;
    }

    public Map<String, Object> getMediaHealthSummary() {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> stats = getStats();
        summary.putAll(stats);
        
        long txPkts = senderThread != null ? senderThread.getPacketsSent() : lastPacketsSent;
        long rxPkts = receiverThread != null ? receiverThread.getPacketsReceived() : lastPacketsReceived;
        
        boolean bidirectional = txPkts > 5 && rxPkts > 5;
        boolean oneWay = (txPkts > 5 && rxPkts == 0) || (rxPkts > 5 && txPkts == 0);
        boolean dead = txPkts == 0 && rxPkts == 0 && audioRunning;
        
        summary.put("is_bidirectional", bidirectional);
        summary.put("is_one_way", oneWay);
        summary.put("is_dead_media", dead);
        
        if (rxRecorder != null) {
            summary.put("rx_wav_rms", rxRecorder.getRmsEnergy());
            summary.put("rx_wav_peak", rxRecorder.getPeakAmplitude());
            summary.put("rx_wav_duration_sec", rxRecorder.getDurationSeconds());
            summary.put("rx_wav_path", rxRecorder.getWavPath());
        }
        if (txRecorder != null) {
            summary.put("tx_wav_rms", txRecorder.getRmsEnergy());
            summary.put("tx_wav_peak", txRecorder.getPeakAmplitude());
            summary.put("tx_wav_duration_sec", txRecorder.getDurationSeconds());
            summary.put("tx_wav_path", txRecorder.getWavPath());
        }
        if (rtcpMonitor != null) {
            RtcpMonitor.RtcpStats qos = rtcpMonitor.getStats(8000);
            if (qos != null) {
                summary.put("rtcp_qos", qos.toString());
                summary.put("rtcp_jitter_ms", qos.jitterMs);
                summary.put("rtcp_rtt_ms", qos.roundTripTimeMs);
                summary.put("rtcp_fraction_lost", qos.fractionLost);
                summary.put("rtcp_cumulative_lost", qos.cumulativeLost);
            }
        }
        return summary;
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
            stats.put("lost_packets", receiverThread.getLostPackets());
            stats.put("duplicate_packets", receiverThread.getDuplicatePackets());
            stats.put("out_of_order_packets", receiverThread.getOutOfOrderPackets());
            stats.put("sequence_discontinuities", receiverThread.getSequenceDiscontinuities());
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

        long lost = receiver.containsKey("lost_packets") ? ((Number) receiver.get("lost_packets")).longValue() : 0L;
        long rxPkts = receiver.containsKey("packets_received") ? ((Number) receiver.get("packets_received")).longValue() : 0L;
        long txPkts = sender.containsKey("packets_sent") ? ((Number) sender.get("packets_sent")).longValue() : 0L;
        double lossRate = (rxPkts + lost) > 0 ? ((double) lost / (rxPkts + lost)) * 100.0 : 0.0;
        double jitterMs = receiver.containsKey("jitter_ms") ? ((Number) receiver.get("jitter_ms")).doubleValue() : 0.0;

        stats.put("lost_packets", lost);
        stats.put("duplicate_packets", receiver.containsKey("duplicate_packets") ? receiver.get("duplicate_packets") : 0L);
        stats.put("out_of_order_packets", receiver.containsKey("out_of_order_packets") ? receiver.get("out_of_order_packets") : 0L);
        stats.put("sequence_discontinuities", receiver.containsKey("sequence_discontinuities") ? receiver.get("sequence_discontinuities") : 0L);
        stats.put("loss_rate_percent", Math.round(lossRate * 100.0) / 100.0);

        boolean oneWay = (txPkts > 50 && rxPkts == 0) || (rxPkts > 50 && txPkts == 0);
        stats.put("one_way_audio", oneWay);

        double mos = calculateMos(lossRate, jitterMs);
        stats.put("mos_score", Math.round(mos * 100.0) / 100.0);

        stats.put("diag_active_sender_threads", LiveAudioRuntimeDiagnostics.getActiveSenders());
        stats.put("diag_active_receiver_threads", LiveAudioRuntimeDiagnostics.getActiveReceivers());
        stats.put("diag_open_sockets", LiveAudioRuntimeDiagnostics.getOpenSockets());
        stats.put("diag_open_mic_lines", LiveAudioRuntimeDiagnostics.getOpenMicLines());
        stats.put("diag_open_speaker_lines", LiveAudioRuntimeDiagnostics.getOpenSpeakerLines());
        stats.put("diag_controller_starts", LiveAudioRuntimeDiagnostics.getControllerStartCount());
        stats.put("diag_controller_stops", LiveAudioRuntimeDiagnostics.getControllerStopCount());
        return stats;
    }

    /**
     * ITU-T E-model G.107 MOS calculation.
     * Computes R-factor based on delay (derived from 10ms propagation + 20ms packetization + 2*jitter)
     * and packet loss % with Bpl=19.0 (G.711 with Appendix I PLC),
     * then transforms R into MOS on a 1.0 to 4.5 scale.
     */
    public static double calculateMos(double packetLossPercent, double jitterMs) {
        return calculateMos(packetLossPercent, jitterMs, -1.0);
    }

    /**
     * Overloaded ITU-T E-model G.107 MOS calculation with explicit one-way delay (ms).
     * If oneWayDelayMs < 0, delay is estimated as: 10ms (propagation) + 20ms (packetization) + 2 * jitterMs.
     */
    public static double calculateMos(double packetLossPercent, double jitterMs, double oneWayDelayMs) {
        if (packetLossPercent < 0) packetLossPercent = 0;
        if (packetLossPercent > 100) packetLossPercent = 100;
        if (jitterMs < 0) jitterMs = 0;

        // Base R-factor for G.711 / standard narrowband
        double r0 = 93.2;

        // One-way delay: propagation (10ms) + packetization (20ms) + jitter buffer delay (2 * jitter)
        double delay;
        if (oneWayDelayMs >= 0) {
            delay = oneWayDelayMs;
        } else {
            delay = 10.0 + 20.0 + (2.0 * jitterMs);
        }

        // Delay impairment Id
        double id = 0.0;
        if (delay > 177.3) {
            id = 0.024 * delay + 0.11 * (delay - 177.3);
        } else {
            id = 0.024 * delay;
        }

        // Equipment impairment Ie: standard G.711 with packet loss
        // Bpl (packet loss robustness factor) = 19.0 for G.711 with ITU-T Appendix I PLC
        double bpl = 19.0;
        double ie = 0.0;
        double ieEff = ie + (95.0 - ie) * (packetLossPercent / (packetLossPercent + bpl));

        // Transmission rating factor R
        double r = r0 - id - ieEff;
        if (r < 0) r = 0;
        if (r > 100) r = 100;

        // Convert R to MOS (1.0 - 4.5) per ITU-T G.107
        double mos;
        if (r <= 0) {
            mos = 1.0;
        } else if (r >= 100) {
            mos = 4.5;
        } else {
            mos = 1.0 + 0.035 * r + r * (r - 60.0) * (100.0 - r) * 0.000007;
        }

        if (mos < 1.0) mos = 1.0;
        if (mos > 4.5) mos = 4.5;
        return mos;
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
        if (rtcpMonitor != null) {
            try {
                rtcpMonitor.stop();
            } catch (Exception ignored) {}
            rtcpMonitor = null;
        }
        stopDiagnosticsFileWriter();
        activeInstance = null;
        senderThread = null;
        receiverThread = null;
        closeMediaSocket();
    }

    public RtcpMonitor getRtcpMonitor() {
        return rtcpMonitor;
    }

    public RtcpMonitor.RtcpStats getRtcpStats() {
        return rtcpMonitor != null ? rtcpMonitor.getStats(8000) : null;
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
