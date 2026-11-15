/*
 *  SIP Inspector - Live Audio Support v3
 *  RTP/SRTP Reception and Speaker Playback Thread
 *
 *  Codec: G.711 A-law (PCMA), PT=8
 */
package sipinspector.Media;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;
import sipinspector.GlobalSettings;
import sipinspector.Utils.TLSDebugLogger;

/**
 * Receives G.711 A-law (PCMA) RTP packets and plays audio through speakers.
 * If SAVP is enabled, expects SRTP (AES_CM_128_HMAC_SHA1_80, SDES) and fails closed on auth mismatch.
 * Includes basic jitter buffer for packet loss tolerance.
 * Runs in a separate thread to avoid blocking UI.
 * 
 * @author SIP Simulator v3
 */
public class LiveAudioReceiverThread extends Thread {
    
    private final int localPort;
    private final int ssrcFilter; // If set, only accept packets with this SSRC
    private final DatagramSocket socket;
    private final SrtpContext srtp;
    private final boolean useSrtp;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    
    private SourceDataLine speakerLine = null;
    private volatile boolean speakerMuted = false;
    private volatile RtpWavRecorder recorder = null; // Optional: records received audio to WAV
    
    private static final int RTP_HEADER_SIZE = 12;
    // Negotiated codec for this call (PCMU/PCMA/G722), resolved at run() start.
    private LiveAudioCodec codec = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);
    private int negotiatedPt = AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA;
    private static final int JITTER_BUFFER_SIZE = 10; // 10 packets = ~200ms
    private static final int RTP_BUFFER_SIZE = 4096;
    private static final long LOG_INTERVAL_MS = 1000L;
    
    // Jitter buffer (sorted by sequence number)
    private LinkedList<RTPPacketData> jitterBuffer = new LinkedList<>();
    
    // Statistics
    private long packetsReceived = 0;
    private long bytesReceived = 0;
    private long packetsDropped = 0;
    private long authFailures = 0;
    private long malformedPackets = 0;
    private long startTimeMs;
    private long lastRxLogMs = 0;
    private int observedSsrc = -1;
    private int lastRxSeq = -1;
    private int lastRxTs = Integer.MIN_VALUE;

    // SSRC latching: lock onto one media source; re-latch only if it goes idle.
    private int latchedSsrc = 0;
    private boolean latched = false;
    private long lastLatchedArrivalMs = 0;
    private static final long SSRC_RELATCH_MS = 2000; // re-latch if latched source silent >2s

    // RTP Quality & Sequence Discontinuity tracking (RFC 3550)
    private long lostPackets = 0;
    private long duplicatePackets = 0;
    private long outOfOrderPackets = 0;
    private long sequenceDiscontinuities = 0;
    
    // RTP details
    private double jitter = 0.0;
    private long lastArrivalMs = -1;
    private int lastTimestamp = -1;
    private long firstArrivalMs = -1;
    private int firstTimestamp = -1;
    private long timestampDriftMs = 0;
    
    // RFC 2833 / payload tracking
    private final Set<Integer> observedPayloadTypes = Collections.synchronizedSet(new HashSet<>());
    private volatile String lastReceivedDtmf = "";
    private int lastDtmfTimestamp = -1;
    private int lastDtmfEvent = -1;

    public String getLastReceivedDtmf() {
        return lastReceivedDtmf;
    }

    public synchronized void consumeDtmf(String digits) {
        if (digits == null || digits.isEmpty()) return;
        for (int i = 0; i < digits.length(); i++) {
            char d = digits.charAt(i);
            int idx = lastReceivedDtmf.indexOf(d);
            if (idx >= 0) {
                lastReceivedDtmf = lastReceivedDtmf.substring(0, idx) + lastReceivedDtmf.substring(idx + 1);
            }
        }
    }

    public Set<Integer> getObservedPayloadTypes() {
        synchronized (observedPayloadTypes) {
            return new HashSet<>(observedPayloadTypes);
        }
    }

    /**
     * RTP packet data wrapper
     */
    private static class RTPPacketData {
        int sequence;
        byte[] payload;
        long receivedTimeMs;
        
        RTPPacketData(int sequence, byte[] payload) {
            this.sequence = sequence;
            this.payload = payload;
            this.receivedTimeMs = System.currentTimeMillis();
        }
    }
    
    /**
     * Create a live audio receiver thread
     * 
     * @param localPort Port to receive RTP on
     * @param ssrcFilter SSRC to filter for (-1 to accept all)
     * @param socket Shared UDP socket bound to media port
     * @param srtp SRTP context (nullable when SAVP disabled)
     */
    private RtcpMonitor rtcpMonitor = null;

    public LiveAudioReceiverThread(int localPort, int ssrcFilter, DatagramSocket socket, SrtpContext srtp) {
        this(localPort, ssrcFilter, socket, srtp, null);
    }

    public LiveAudioReceiverThread(int localPort, int ssrcFilter, DatagramSocket socket, SrtpContext srtp, RtcpMonitor rtcpMonitor) {
        this.localPort = localPort;
        this.ssrcFilter = ssrcFilter;
        this.socket = socket;
        this.srtp = srtp;
        this.useSrtp = (srtp != null);
        this.rtcpMonitor = rtcpMonitor;
        
        setName("LiveAudioReceiver-:" + localPort);
        setDaemon(true);
    }
    
    @Override
    public void run() {
        try {
            running = true;
            startTimeMs = System.currentTimeMillis();
            LiveAudioRuntimeDiagnostics.onReceiverStarted();

            // Resolve the negotiated codec and open the speaker at its rate
            // (16kHz for G.722, 8kHz for G.711).
            resolveCodec();

            // Open speaker output
            try {
                speakerLine = AudioDeviceManager.openSpeakerOutput(codec.sampleRateHz());
                if (speakerLine != null) {
                    speakerLine.start();
                    LiveAudioRuntimeDiagnostics.onSpeakerLineOpened();
                }
            } catch (Exception se) {
                System.err.println("LiveAudioReceiverThread: could not open speaker (" + se.getMessage()
                        + ") - audio playback disabled, RTP reception and recording continues.");
                speakerLine = null;
            }
            
            byte[] buffer = new byte[RTP_BUFFER_SIZE];
            int lastPlayedSequence = -1;
            
            while (running) {
                if (paused) {
                    Thread.sleep(50);
                    continue;
                }
                
                try {
                    // Receive RTP packet (non-blocking with timeout)
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.setSoTimeout(100); // 100ms timeout
                    socket.receive(packet);
                    
                    // Parse RTP header
                    if (packet.getLength() < RTP_HEADER_SIZE) {
                        continue; // Invalid packet
                    }
                    
                    byte[] data = packet.getData();
                    int len = packet.getLength();
                    byte[] plainRtp = null;
                    if (useSrtp && GlobalSettings.isSdpUseSavp()) {
                        plainRtp = srtp.unprotectRtp(data, len);
                        if (plainRtp == null) {
                            // Fail closed on auth mismatch
                            authFailures++;
                            packetsDropped++;
                            maybeLogDrop("auth_fail", -1, -1, -1);
                            continue;
                        }
                        data = plainRtp;
                        len = plainRtp.length;
                    }
                    
                    // Validate RTP version (must be 2)
                    int version = (data[0] & 0xC0) >> 6;
                    if (version != 2) {
                        malformedPackets++;
                        packetsDropped++;
                        maybeLogDrop("bad_rtp_version", -1, -1, -1);
                        continue;
                    }

                    // Mirror cleartext RTP into an active .pcap capture (post-SRTP-unprotect)
                    try {
                        sipinspector.Utils.PcapCapture cap = sipinspector.Utils.PcapCapture.getInstance();
                        if (cap.isCapturing()) {
                            cap.writePacket(packet.getAddress(), packet.getPort(),
                                    socket.getLocalAddress(), socket.getLocalPort(),
                                    sipinspector.Utils.PcapCapture.UDP, data, 0, len);
                        }
                    } catch (Exception ignore) {
                    }
                    
                    // Extract sequence number, timestamp, ssrc
                    int sequence = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    int timestamp = ((data[4] & 0xFF) << 24)
                                  | ((data[5] & 0xFF) << 16)
                                  | ((data[6] & 0xFF) << 8)
                                  | (data[7] & 0xFF);
                    int ssrc = ((data[8] & 0xFF) << 24)
                             | ((data[9] & 0xFF) << 16)
                             | ((data[10] & 0xFF) << 8)
                             | (data[11] & 0xFF);
                    
                    // Extract payload type
                    int payloadType = data[1] & 0x7F;
                    observedPayloadTypes.add(payloadType);
                    
                    // Explicit SSRC filter (if the caller pinned one).
                    if (ssrcFilter >= 0) {
                        if (ssrc != ssrcFilter) continue; // Not our SSRC
                    }

                    // SSRC LATCHING. A media port can legitimately receive RTP
                    // from more than one synchronization source at once (e.g. an
                    // OXE media gateway that keeps relaying an old stream while a
                    // new one starts, or an announcement/MOH source). Without
                    // latching, packets from two SSRCs - each with its own
                    // sequence/timestamp space and possibly a different ptime
                    // (20ms/160B vs 30ms/240B) - get interleaved into one jitter
                    // buffer, so ordering collapses and audio breaks up after a
                    // few seconds (observed: constant "SSRC changed / non-monotonic
                    // seq" churn, dropped count climbing). Lock onto the FIRST
                    // active source and ignore the others; re-latch only if the
                    // current source goes quiet for a while (real re-anchor).
                    long nowMsLatch = System.currentTimeMillis();
                    if (latchedSsrc == 0 && !latched) {
                        latchedSsrc = ssrc;
                        latched = true;
                        lastLatchedArrivalMs = nowMsLatch;
                        if (LiveAudioRuntimeDiagnostics.enabled()) {
                            TLSDebugLogger.logf("[RTP RX] latched onto SSRC=0x%08X", ssrc);
                        }
                    } else if (ssrc != latchedSsrc) {
                        // A different source. If our latched source is still
                        // active (heard within the grace window), drop this one.
                        if (nowMsLatch - lastLatchedArrivalMs < SSRC_RELATCH_MS) {
                            packetsDropped++;
                            maybeLogDrop("foreign_ssrc", -1, -1, payloadType);
                            continue;
                        }
                        // Latched source went quiet -> re-latch to this new one.
                        if (LiveAudioRuntimeDiagnostics.enabled()) {
                            TLSDebugLogger.logf("[RTP RX] re-latched SSRC 0x%08X -> 0x%08X (previous source idle)",
                                    latchedSsrc, ssrc);
                        }
                        latchedSsrc = ssrc;
                        lastLatchedArrivalMs = nowMsLatch;
                        // Reset per-stream tracking so stats/jitter restart cleanly.
                        jitterBuffer.clear();
                        lastRxSeq = -1;
                        lastRxTs = Integer.MIN_VALUE;
                        lastTimestamp = -1;
                        lastArrivalMs = -1;
                    } else {
                        lastLatchedArrivalMs = nowMsLatch;
                    }


                    // Extract payload
                    int payloadLength = len - RTP_HEADER_SIZE;
                    byte[] payload = new byte[payloadLength];
                    System.arraycopy(data, RTP_HEADER_SIZE, payload, 0, payloadLength);
                    
                    // DTMF Event check (RFC 2833)
                    if (payloadType == 101) {
                        if (payloadLength >= 1) {
                            int eventId = payload[0] & 0xFF;
                            int dtmfTimestamp = timestamp;
                            if (dtmfTimestamp != lastDtmfTimestamp || eventId != lastDtmfEvent) {
                                char digit = '?';
                                if (eventId >= 0 && eventId <= 9) {
                                    digit = (char)('0' + eventId);
                                } else if (eventId == 10) {
                                    digit = '*';
                                } else if (eventId == 11) {
                                    digit = '#';
                                } else if (eventId >= 12 && eventId <= 15) {
                                    digit = (char)('A' + eventId - 12);
                                }
                                if (digit != '?') {
                                    lastReceivedDtmf += digit;
                                    if (lastReceivedDtmf.length() > 100) {
                                        lastReceivedDtmf = lastReceivedDtmf.substring(lastReceivedDtmf.length() - 100);
                                    }
                                    if (TLSDebugLogger.isEnabled()) {
                                        TLSDebugLogger.log("[DTMF RX] Detected digit: " + digit + " (eventId=" + eventId + ", timestamp=" + dtmfTimestamp + ")");
                                    }
                                    sipinspector.Utils.LogManager.logRtpEvent("DTMF digit received: '" + digit + "'");
                                }
                                lastDtmfTimestamp = dtmfTimestamp;
                                lastDtmfEvent = eventId;
                            }
                        }
                        continue; // Do not play telephone-event to speaker
                    }
                    
                    if (payloadType != negotiatedPt) {
                        packetsDropped++;
                        maybeLogDrop("unexpected_pt", -1, -1, payloadType);
                        continue; // Not the negotiated codec
                    }

                    
                    packetsReceived++;
                    bytesReceived += len;
                    if (rtcpMonitor != null) {
                        try {
                            rtcpMonitor.processRtpPacket(sequence, timestamp, 8000);
                        } catch (Exception ignored) {}
                    }
                    
                    // Record raw PCMA/PCMU/G722 payload BEFORE jitter buffer (captures all packets)
                    if (recorder != null) {
                        try {
                            recorder.feedPayload(payload, payloadType);
                        } catch (Exception recEx) {
                            // Don't let recording errors affect reception
                        }
                    }
                    
                    // Add to jitter buffer
                    addToJitterBuffer(new RTPPacketData(sequence, payload));
                    validateRxRtp(sequence, timestamp, payloadType, ssrc);
                    maybeLogRx(sequence, timestamp, payloadType, ssrc, payloadLength);
                    
                    // Try to play buffered packets
                    playFromJitterBuffer(speakerLine);
                    
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout - try to play from buffer anyway
                    playFromJitterBuffer(speakerLine);
                } catch (java.net.SocketException se) {
                    if (running) {
                        System.err.println("LiveAudioReceiverThread socket error: " + se.getMessage());
                    }
                    break;
                } catch (Exception packetEx) {
                    malformedPackets++;
                    packetsDropped++;
                    maybeLogDrop("malformed_packet", -1, -1, -1);
                }
            }
            
        } catch (Exception e) {
            System.err.println("LiveAudioReceiverThread error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }
    
    /**
     * Add packet to jitter buffer (maintains sequence order)
     */
    private synchronized void addToJitterBuffer(RTPPacketData packet) {
        // Insert sorted by RTP sequence number (16-bit wraparound aware) so
        // out-of-order packets are played back in sender order. Buffer is
        // small (<= JITTER_BUFFER_SIZE) so a linear scan from the tail is cheap.
        int idx = jitterBuffer.size();
        while (idx > 0 && seqBefore(packet.sequence, jitterBuffer.get(idx - 1).sequence)) {
            idx--;
        }
        jitterBuffer.add(idx, packet);

        // Trim if buffer gets too large (drop oldest in sequence order)
        if (jitterBuffer.size() > JITTER_BUFFER_SIZE) {
            jitterBuffer.poll();
            packetsDropped++;
        }
    }

    /** True if sequence a comes before b in RTP order, accounting for 16-bit wraparound (RFC 3550). */
    private static boolean seqBefore(int a, int b) {
        return a != b && ((b - a) & 0xFFFF) < 0x8000;
    }
    
    /**
     * Play packets from jitter buffer to speaker
     */
    private synchronized void playFromJitterBuffer(SourceDataLine speakerLine) {
        if (speakerLine == null) {
            jitterBuffer.clear();
            return;
        }
        while (!jitterBuffer.isEmpty() && speakerLine.available() > 160) {
            RTPPacketData packet = jitterBuffer.poll();
            
            if (packet != null) {
                // Decode the negotiated codec (PCMA/PCMU/G722) to PCM16
                byte[] pcmData = codec.decode(packet.payload, packet.payload.length);

                // Write to speaker if not muted
                if (!speakerMuted) {
                    speakerLine.write(pcmData, 0, pcmData.length);
                }
            }
        }
    }
    
    /**
     * Start playing audio
     */
    public void startAudio() {
        paused = false;
    }
    
    /**
     * Pause audio playback
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
     * Set an optional recorder to capture received PCMA audio to WAV.
     * Call this before starting the thread, or while running.
     */
    public void setRecorder(RtpWavRecorder recorder) {
        this.recorder = recorder;
    }

    public RtpWavRecorder getRecorder() {
        return recorder;
    }

    public void setSpeakerMuted(boolean muted) {
        this.speakerMuted = muted;
    }

    public boolean isSpeakerMuted() {
        return speakerMuted;
    }
    
    /**
     * Check if currently playing
     */
    public boolean isRunning() {
        return running && !paused;
    }
    
    /**
     * Get packets received
     */
    public long getPacketsReceived() {
        return packetsReceived;
    }
    
    /**
     * Get bytes received
     */
    public long getBytesReceived() {
        return bytesReceived;
    }
    
    /**
     * Get packets dropped due to buffer overflow
     */
    public long getPacketsDropped() {
        return packetsDropped;
    }

    public long getAuthFailures() {
        return authFailures;
    }

    public long getMalformedPackets() {
        return malformedPackets;
    }
    
    public int getObservedSsrc() {
        return observedSsrc;
    }
    
    public int getPayloadType() {
        return negotiatedPt;
    }

    public String getCodecName() {
        return codec != null ? codec.rtpmapName() : "PCMA";
    }

    /**
     * Resolve the negotiated codec from GlobalSettings "RTP_Remote_PT". Falls
     * back to PCMA if unset/unsupported.
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
        negotiatedPt = pt;
        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[RTP RX] using codec %s (PT=%d, %dHz)",
                    codec.rtpmapName(), negotiatedPt, codec.sampleRateHz());
        }
    }

    public double getJitterMs() {
        return jitter / 8.0;
    }
    
    public long getTimestampDriftMs() {
        return timestampDriftMs;
    }

    public long getLostPackets() {
        return lostPackets;
    }

    public long getDuplicatePackets() {
        return duplicatePackets;
    }

    public long getOutOfOrderPackets() {
        return outOfOrderPackets;
    }

    public long getSequenceDiscontinuities() {
        return sequenceDiscontinuities;
    }
    
    /**
     * Get current jitter buffer size
     */
    public int getJitterBufferSize() {
        return jitterBuffer.size();
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
            if (speakerLine != null) {
                speakerLine.stop();
                speakerLine.close();
                LiveAudioRuntimeDiagnostics.onSpeakerLineClosed();
                speakerLine = null;
            }
        } catch (Exception e) {
            // Ignore
        }
        
        running = false;
        LiveAudioRuntimeDiagnostics.onReceiverStopped();
    }

    private void maybeLogRx(int seq, int ts, int pt, int ssrc, int payloadLen) {
        if (!TLSDebugLogger.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRxLogMs < LOG_INTERVAL_MS) {
            return;
        }
        lastRxLogMs = now;
        String mode = (useSrtp && GlobalSettings.isSdpUseSavp()) ? "SRTP RX" : "RTP RX";
        TLSDebugLogger.log(String.format(
            "[%s] seq=%d ts=%d pt=%d ssrc=0x%08X len=%d auth=%s dropped=%d jitterDepth=%d totalRx=%d",
            mode,
            seq & 0xFFFF,
            ts,
            pt,
            ssrc,
            payloadLen,
            (useSrtp && GlobalSettings.isSdpUseSavp()) ? "OK" : "N/A",
            packetsDropped,
            getJitterBufferSize(),
            packetsReceived
        ));
    }

    private void maybeLogDrop(String reason, Integer seq, Integer ts, Integer pt) {
        if (!TLSDebugLogger.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRxLogMs < LOG_INTERVAL_MS) {
            return;
        }
        lastRxLogMs = now;
        TLSDebugLogger.log(String.format(
            "[SRTP RX] drop reason=%s seq=%s ts=%s pt=%s authFailures=%d dropped=%d jitterDepth=%d",
            reason,
            seq >= 0 ? String.valueOf(seq) : "n/a",
            ts >= 0 ? String.valueOf(ts) : "n/a",
            pt >= 0 ? String.valueOf(pt) : "n/a",
            authFailures,
            packetsDropped,
            getJitterBufferSize()
        ));
    }

    private void validateRxRtp(int seq, int ts, int pt, int ssrc) {
        long arrivalMs = System.currentTimeMillis();
        
        // Jitter Calculation (RFC 3550)
        if (lastArrivalMs >= 0 && lastTimestamp != -1) {
            // All codecs use 8 kHz RTP clock (PCMU/PCMA @ 8 kHz, G.722 @ 16 kHz audio but 8 kHz RTP clock per RFC 3550)
            long arrivalDiffTs = (arrivalMs - lastArrivalMs) * 8;
            long senderDiffTs = ts - lastTimestamp;
            double d = Math.abs(arrivalDiffTs - senderDiffTs);
            jitter = jitter + (d - jitter) / 16.0;
        }
        lastArrivalMs = arrivalMs;
        lastTimestamp = ts;
        
        // Timestamp Drift Calculation
        if (firstArrivalMs == -1) {
            firstArrivalMs = arrivalMs;
            firstTimestamp = ts;
        } else {
            long localElapsed = arrivalMs - firstArrivalMs;
            long rtpElapsed = (long)((ts - firstTimestamp) / 8.0);
            timestampDriftMs = localElapsed - rtpElapsed;
        }

        // Track the observed receive SSRC UNCONDITIONALLY - it is surfaced in
        // the RTP Inspector on every call, not just when diagnostics logging is
        // enabled. Previously this lived below the enabled() gate, so with debug
        // off (the default) observedSsrc stayed -1 and the inspector rendered a
        // permanent 0xFFFFFFFF. Only the SSRC-change WARNING stays gated.
        if (observedSsrc < 0) {
            observedSsrc = ssrc;
        } else if (observedSsrc != ssrc) {
            if (LiveAudioRuntimeDiagnostics.enabled()) {
                TLSDebugLogger.log(String.format("[RTP WARN RX] SSRC changed old=0x%08X new=0x%08X", observedSsrc, ssrc));
            }
            observedSsrc = ssrc;
        }

        // RFC 3550 Sequence arithmetic & packet loss tracking
        if (lastRxSeq >= 0) {
            int seqDiff = (seq - lastRxSeq) & 0xFFFF;
            if (seqDiff == 0) {
                duplicatePackets++;
            } else if (seqDiff > 0x8000) {
                // Sequence is older than lastRxSeq -> out-of-order packet
                outOfOrderPackets++;
            } else if (seqDiff > 1) {
                if (seqDiff < 3000) {
                    lostPackets += (seqDiff - 1);
                    sequenceDiscontinuities++;
                }
            }
        }

        if (!LiveAudioRuntimeDiagnostics.enabled()) {
            return;
        }
        if (pt != 8) {
            TLSDebugLogger.log("[RTP WARN RX] unexpected PT=" + pt + " expected=8");
        }
        if (lastRxSeq >= 0) {
            int expected = (lastRxSeq + 1) & 0xFFFF;
            if ((seq & 0xFFFF) != expected && (seq & 0xFFFF) != (lastRxSeq & 0xFFFF)) {
                TLSDebugLogger.log("[RTP WARN RX] non-monotonic seq prev=" + (lastRxSeq & 0xFFFF) + " now=" + (seq & 0xFFFF));
            }
        }
        if (lastRxTs != Integer.MIN_VALUE) {
            int delta = ts - lastRxTs;
            if (delta != 160 && delta != 0) {
                TLSDebugLogger.log("[RTP WARN RX] timestamp delta=" + delta + " expected=160");
            }
        }
        lastRxSeq = seq;
        lastRxTs = ts;
    }
}
