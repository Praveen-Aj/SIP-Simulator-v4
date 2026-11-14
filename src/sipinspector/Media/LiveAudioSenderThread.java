/*
 *  SIP Inspector - Live Audio Support v3
 *  Microphone Capture and RTP (or SRTP) Transmission Thread
 *
 *  Codec: G.711 A-law (PCMA), PT=8, 20ms frames (160 samples @ 8kHz)
 */
package sipinspector.Media;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import sipinspector.GlobalSettings;
import sipinspector.Utils.TLSDebugLogger;

/**
 * Captures audio from microphone and sends as G.711 A-law (PCMA) RTP packets.
 * If SAVP is enabled, protects RTP using SDES-SRTP (AES_CM_128_HMAC_SHA1_80).
 * Runs in a separate thread to avoid blocking UI.
 * 
 * RTP Header format:
 *  - SSRC: Source identifier (unique per sender)
 *  - Sequence: Increments per packet
 *  - Timestamp: RTP timestamp (8kHz clock)
 *  - Payload Type: 8 for G.711 A-law (PCMA)
 * 
 * @author SIP Simulator v3
 */
public class LiveAudioSenderThread extends Thread {
    
    private volatile InetAddress remoteAddress;
    private volatile int remotePort;
    private final DatagramSocket socket;
    private final SrtpContext srtp;
    private final boolean useSrtp;
    
    private volatile boolean running = false;
    private volatile boolean paused = false;
    
    private TargetDataLine micLine = null;
    
    private int ssrc;
    private int sequence;
    private int timestamp;
    private static final int RTP_HEADER_SIZE = 12;
    // Negotiated codec for this call (PCMU/PCMA/G722), resolved at run() start
    // from GlobalSettings "RTP_Remote_PT". Defaults to PCMA.
    private LiveAudioCodec codec = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);
    private int payloadType = AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA;
    private static final long FRAME_INTERVAL_NANOS = 20_000_000L; // 20ms
    private static final long LOG_INTERVAL_MS = 1000L;
    
    // Statistics
    private long packetsSent = 0;
    private long bytesSent = 0;
    private long startTimeMs;
    private long lastLogMs = 0;
    private long lastTargetRefreshMs = 0;
    private int lastSentSeq = -1;
    private int lastSentTs = Integer.MIN_VALUE;
    private long lastSendNanos = -1L;
    
    /**
     * Create a live audio sender thread
     * 
     * @param remoteAddress Remote IP address to send RTP to
     * @param remotePort Remote RTP port
     * @param socket Shared UDP socket bound to media port
     * @param srtp SRTP context (nullable when SAVP disabled)
     */
    public LiveAudioSenderThread(InetAddress remoteAddress, int remotePort, DatagramSocket socket, SrtpContext srtp) {
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.socket = socket;
        this.srtp = srtp;
        this.useSrtp = (srtp != null);
        
        this.ssrc = new Random().nextInt(0x7FFFFFFF);
        this.sequence = new Random().nextInt(0xFFFF);
        this.timestamp = new Random().nextInt();
        setName("LiveAudioSender-" + remoteAddress.getHostAddress() + ":" + remotePort);
        setDaemon(true);
    }
    
    @Override
    public void run() {
        try {
            running = true;
            startTimeMs = System.currentTimeMillis();
            LiveAudioRuntimeDiagnostics.onSenderStarted();

            // Resolve the negotiated codec for this call.
            resolveCodec();
            final int frameBytes = codec.frameBytesPcm();

            // Open microphone at the codec's sample rate (16kHz for G.722).
            micLine = AudioDeviceManager.openMicrophoneInput(codec.sampleRateHz());
            micLine.start();
            LiveAudioRuntimeDiagnostics.onMicLineOpened();

            // Audio buffer for one 20ms frame
            byte[] pcmBuffer = new byte[frameBytes];
            byte[] encodedBuffer;

            long frameCount = 0;
            long nextFrameNanos = System.nanoTime();
            
            while (running) {
                if (paused) {
                    Thread.sleep(100);
                    nextFrameNanos = System.nanoTime();
                    continue;
                }
                
                // Read PCM data from microphone (blocking)
                int bytesRead = micLine.read(pcmBuffer, 0, frameBytes);
                refreshRtpTargetFromNegotiatedSettings();

                if (bytesRead > 0) {
                    // Encode PCM to the negotiated codec (PCMA/PCMU/G722)
                    encodedBuffer = codec.encode(pcmBuffer, bytesRead);

                    // Update timestamp and sequence
                    sequence = (sequence + 1) & 0xFFFF;
                    timestamp += codec.tsIncrementPerFrame(); // 160 (8kHz RTP clock)

                    // Build RTP packet
                    byte[] rtpPacket = buildRTPPacket(encodedBuffer, encodedBuffer.length);
                    int outLen = rtpPacket.length;
                    byte[] outPacket = rtpPacket;
                    if (useSrtp && GlobalSettings.isSdpUseSavp()) {
                        outPacket = srtp.protectRtp(rtpPacket, outLen);
                        outLen = outPacket.length;
                    }
                    
                    // Send RTP packet
                    DatagramPacket packet = new DatagramPacket(
                        outPacket, 
                        outLen, 
                        remoteAddress, 
                        remotePort
                    );
                    socket.send(packet);

                    // Mirror cleartext RTP into an active .pcap capture (pre-SRTP so Wireshark can decode)
                    try {
                        sipinspector.Utils.PcapCapture cap = sipinspector.Utils.PcapCapture.getInstance();
                        if (cap.isCapturing()) {
                            cap.writePacket(socket.getLocalAddress(), socket.getLocalPort(),
                                    remoteAddress, remotePort,
                                    sipinspector.Utils.PcapCapture.UDP, rtpPacket, 0, rtpPacket.length);
                        }
                    } catch (Exception ignore) {
                    }

                    packetsSent++;
                    bytesSent += outLen;
                    frameCount++;

                    validateTxRtp(sequence, timestamp);
                    maybeLogTx(sequence, timestamp, payloadType, encodedBuffer.length);

                    nextFrameNanos += FRAME_INTERVAL_NANOS;
                    long now = System.nanoTime();
                    long sleepNanos = nextFrameNanos - now;
                    if (sleepNanos > 0) {
                        Thread.sleep(sleepNanos / 1_000_000L, (int)(sleepNanos % 1_000_000L));
                    } else if (sleepNanos < -200_000_000L) {
                        // If scheduler delay is large, resync to avoid cumulative drift.
                        nextFrameNanos = now;
                    }
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SocketException se) {
            if (running && (socket == null || !socket.isClosed())) {
                System.err.println("LiveAudioSenderThread socket error: " + se.getMessage());
            }
        } catch (LineUnavailableException le) {
            // Expected/common on a machine with no microphone, or one already in
            // use by another app - not a real crash, so don't spam a stack trace.
            // The rest of the call (signaling, RTP receive) is unaffected; we
            // simply won't be sending outbound live audio for this call.
            System.err.println("LiveAudioSenderThread: no microphone available (" + le.getMessage()
                    + ") - outbound live audio disabled for this call, everything else continues normally.");
        } catch (Exception e) {
            System.err.println("LiveAudioSenderThread error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }
    
    /**
     * Build an RTP packet with the given payload
     * 
     * RTP Header (12 bytes):
     *  V(2) | P(1) | X(1) | CC(4) | M(1) | PT(7) | Sequence (16)
     *  Timestamp (32)
     *  SSRC (32)
     * 
     * @param payload G.711 A-law (PCMA) audio data
     * @param payloadLength Length of payload
     * @return Complete RTP packet
     */
    private byte[] buildRTPPacket(byte[] payload, int payloadLength) {
        byte[] packet = new byte[RTP_HEADER_SIZE + payloadLength];
        int index = 0;
        
        // Byte 0: V(2), P(1), X(1), CC(4)
        // Version=2, Padding=0, Extension=0, CC=0
        packet[index++] = (byte)0x80;
        
        // Byte 1: M(1), PT(7)
        // Marker=0, PayloadType = negotiated codec (PCMU=0/PCMA=8/G722=9)
        packet[index++] = (byte)(payloadType & 0x7F);
        
        // Bytes 2-3: Sequence number (16-bit big-endian)
        packet[index++] = (byte)((sequence >> 8) & 0xFF);
        packet[index++] = (byte)(sequence & 0xFF);
        
        // Bytes 4-7: Timestamp (32-bit big-endian)
        packet[index++] = (byte)((timestamp >> 24) & 0xFF);
        packet[index++] = (byte)((timestamp >> 16) & 0xFF);
        packet[index++] = (byte)((timestamp >> 8) & 0xFF);
        packet[index++] = (byte)(timestamp & 0xFF);
        
        // Bytes 8-11: SSRC (32-bit big-endian)
        packet[index++] = (byte)((ssrc >> 24) & 0xFF);
        packet[index++] = (byte)((ssrc >> 16) & 0xFF);
        packet[index++] = (byte)((ssrc >> 8) & 0xFF);
        packet[index++] = (byte)(ssrc & 0xFF);
        
        // Payload
        System.arraycopy(payload, 0, packet, index, payloadLength);
        
        return packet;
    }
    
    /**
     * Start sending audio
     */
    public void startAudio() {
        paused = false;
    }
    
    /**
     * Pause audio sending
     */
    public void pauseAudio() {
        paused = true;
    }
    
    /**
     * Stop the thread
     */
    public void stopAudio() {
        running = false;
        interrupt();
    }
    
    /**
     * Check if currently sending
     */
    public boolean isRunning() {
        return running && !paused;
    }
    
    /**
     * Get packets sent
     */
    public long getPacketsSent() {
        return packetsSent;
    }

    public int getSsrc() {
        return ssrc;
    }

    public int getPayloadType() {
        return payloadType;
    }

    public String getCodecName() {
        return codec != null ? codec.rtpmapName() : "PCMA";
    }

    /**
     * Resolve the negotiated codec from GlobalSettings "RTP_Remote_PT" (set by
     * SIPCall during SDP negotiation). Falls back to PCMA if unset/unsupported.
     */
    private void resolveCodec() {
        int pt = AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA;
        try {
            String s = GlobalSettings.getSetting("RTP_Remote_PT");
            if (s != null && s.trim().length() > 0) {
                int parsed = Integer.parseInt(s.trim());
                if (LiveAudioCodec.isSupported(parsed)) {
                    pt = parsed;
                }
            }
        } catch (Exception ignore) {
            // keep PCMA default
        }
        codec = LiveAudioCodec.create(pt);
        payloadType = pt;
        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[RTP TX] using codec %s (PT=%d, %dHz)",
                    codec.rtpmapName(), payloadType, codec.sampleRateHz());
        }
    }

    /**
     * Get bytes sent
     */
    public long getBytesSent() {
        return bytesSent;
    }
    
    /**
     * Get elapsed time in milliseconds
     */
    public long getElapsedTimeMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
    
    /**
     * Cleanup resources
     */
    private void cleanup() {
        try {
            if (micLine != null) {
                micLine.stop();
                micLine.close();
                LiveAudioRuntimeDiagnostics.onMicLineClosed();
                micLine = null;
            }
        } catch (Exception e) {
            // Ignore
        }
        
        running = false;
        LiveAudioRuntimeDiagnostics.onSenderStopped();
    }

    private void maybeLogTx(int seq, int ts, int pt, int payloadLen) {
        if (!TLSDebugLogger.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLogMs < LOG_INTERVAL_MS) {
            return;
        }
        lastLogMs = now;
        String mode = (useSrtp && GlobalSettings.isSdpUseSavp()) ? "SRTP TX" : "RTP TX";
        TLSDebugLogger.log(String.format(
            "[%s] seq=%d ts=%d pt=%d ssrc=0x%08X len=%d dst=%s:%d totalSent=%d",
            mode,
            seq & 0xFFFF,
            ts,
            pt,
            ssrc,
            payloadLen,
            remoteAddress.getHostAddress(),
            remotePort,
            packetsSent
        ));
    }

    private void refreshRtpTargetFromNegotiatedSettings() {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastTargetRefreshMs < 500) {
            return;
        }
        lastTargetRefreshMs = nowMs;
        try {
            String ip = GlobalSettings.getSetting("RTP_Remote_Address");
            String portStr = GlobalSettings.getSetting("RTP_Remote_Port");
            if (ip == null || ip.trim().isEmpty() || portStr == null || portStr.trim().isEmpty()) {
                return;
            }
            InetAddress negotiatedAddr = InetAddress.getByName(ip.trim());
            int negotiatedPort = Integer.parseInt(portStr.trim());
            if (!negotiatedAddr.equals(remoteAddress) || negotiatedPort != remotePort) {
                remoteAddress = negotiatedAddr;
                remotePort = negotiatedPort;
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[RTP TARGET] ip=%s port=%d source=SDP dynamic-update",
                            remoteAddress.getHostAddress(),
                            remotePort);
                }
            }
        } catch (Exception ignore) {
            // Keep last known valid target.
        }
    }

    private void validateTxRtp(int seq, int ts) {
        if (!LiveAudioRuntimeDiagnostics.enabled()) {
            return;
        }
        if (!LiveAudioCodec.isSupported(payloadType)) {
            TLSDebugLogger.log("[RTP WARN TX] unsupported PT=" + payloadType);
        }
        String negotiatedPt = GlobalSettings.getSetting("RTP_Remote_PT");
        if (negotiatedPt != null && negotiatedPt.trim().length() > 0) {
            try {
                int npt = Integer.parseInt(negotiatedPt.trim());
                if (npt != payloadType) {
                    TLSDebugLogger.log("[RTP WARN TX] negotiated_pt=" + npt + " but tx_pt=" + payloadType);
                }
            } catch (Exception ignore) {
                // Ignore malformed negotiated PT.
            }
        }
        if (lastSentSeq >= 0) {
            int expectedSeq = (lastSentSeq + 1) & 0xFFFF;
            if ((seq & 0xFFFF) != expectedSeq) {
                TLSDebugLogger.log("[RTP WARN TX] sequence jump prev=" + (lastSentSeq & 0xFFFF) + " now=" + (seq & 0xFFFF));
            }
        }
        if (lastSentTs != Integer.MIN_VALUE) {
            int delta = ts - lastSentTs;
            if (delta != 160) {
                TLSDebugLogger.log("[RTP WARN TX] timestamp delta=" + delta + " expected=160");
            }
        }
        long now = System.nanoTime();
        if (lastSendNanos > 0) {
            long deltaMs = (now - lastSendNanos) / 1_000_000L;
            if (deltaMs < 10 || deltaMs > 80) {
                TLSDebugLogger.log("[RTP WARN TX] pacing_delta_ms=" + deltaMs + " expected~20");
            }
        }
        lastSendNanos = now;
        lastSentSeq = seq;
        lastSentTs = ts;
    }
}
