/*
 *  SIP Inspector - RTCP Monitor
 *  RFC 3550 Real-time Transport Protocol / RTCP Quality Monitoring
 */
package sipinspector.Media;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import sipinspector.Utils.TLSDebugLogger;

/**
 * RtcpMonitor runs on (RTP port + 1) to provide quality of service (QoS)
 * reporting, jitter calculation, packet loss tracking, and RFC 3550 Sender/Receiver
 * reports.
 */
public class RtcpMonitor {

    public static final int RTCP_PT_SR   = 200; // Sender Report
    public static final int RTCP_PT_RR   = 201; // Receiver Report
    public static final int RTCP_PT_SDES = 202; // Source Description
    public static final int RTCP_PT_BYE  = 203; // Goodbye
    public static final int RTCP_PT_APP  = 204; // Application defined

    private final InetAddress localAddress;
    private final int localRtcpPort;
    private final InetAddress remoteAddress;
    private final int remoteRtcpPort;
    private final long localSsrc;

    private volatile boolean running = false;
    private DatagramSocket socket = null;
    private Thread workerThread = null;

    // Metrics tracking
    private volatile long remoteSsrc = 0;
    private volatile long packetsReceived = 0;
    private volatile long octetsReceived = 0;
    private volatile long packetsSent = 0;
    private volatile long octetsSent = 0;

    // RFC 3550 Jitter & Loss calculations
    private long lastRtpTransit = 0;
    private double jitter = 0.0; // in timestamp units
    private int maxSeqReceived = -1;
    private int cycleCount = 0;
    private int baseSeq = -1;
    private long expectedPrior = 0;
    private long receivedPrior = 0;
    private int fractionLost = 0;
    private long cumulativeLost = 0;

    // Round-Trip Time tracking
    private long lastSrNtpTimestamp = 0;
    private long lastSrReceiptTimeMs = 0;
    private long roundTripTimeMs = 0;

    public static class RtcpStats {
        public long localSsrc;
        public long remoteSsrc;
        public long packetsSent;
        public long packetsReceived;
        public double jitterMs;
        public int fractionLost;
        public long cumulativeLost;
        public long roundTripTimeMs;

        @Override
        public String toString() {
            return String.format("RTCP [Remote SSRC=0x%08X, Jitter=%.2f ms, Loss=%d/256 (Cum=%d), RTT=%d ms, Rx=%d, Tx=%d]",
                    remoteSsrc, jitterMs, fractionLost, cumulativeLost, roundTripTimeMs, packetsReceived, packetsSent);
        }
    }

    public RtcpMonitor(InetAddress localAddress, int localRtpPort, InetAddress remoteAddress, int remoteRtpPort) {
        this.localAddress = localAddress;
        this.localRtcpPort = (localRtpPort % 2 == 0) ? (localRtpPort + 1) : localRtpPort;
        this.remoteAddress = remoteAddress;
        this.remoteRtcpPort = (remoteRtpPort % 2 == 0) ? (remoteRtpPort + 1) : remoteRtpPort;
        this.localSsrc = (long) (Math.random() * 0xFFFFFFFFL);
    }

    public synchronized void start() throws SocketException {
        if (running) return;
        socket = new DatagramSocket(localRtcpPort, localAddress);
        socket.setSoTimeout(500);
        running = true;

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                long lastReportSent = System.currentTimeMillis();

                while (running) {
                    try {
                        socket.receive(packet);
                        parseIncomingRtcp(packet.getData(), packet.getOffset(), packet.getLength());
                    } catch (SocketTimeoutException ste) {
                        // Expected timeout
                    } catch (IOException ioe) {
                        if (!running) break;
                    }

                    // Send periodic Receiver Report every 5 seconds
                    long now = System.currentTimeMillis();
                    if (now - lastReportSent >= 5000) {
                        sendReceiverReport();
                        lastReportSent = now;
                    }
                }
            }
        }, "RTCP-Monitor-" + localRtcpPort);

        workerThread.setDaemon(true);
        workerThread.start();
        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[RTCP START] Bound port=%d, remote=%s:%d, localSSRC=0x%08X",
                    localRtcpPort, remoteAddress != null ? remoteAddress.getHostAddress() : "null", remoteRtcpPort, localSsrc);
        }
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (workerThread != null) {
            try {
                workerThread.join(1000);
            } catch (InterruptedException ignored) {}
            workerThread = null;
        }
    }

    /**
     * Process an incoming RTP packet sequence and timestamp to compute
     * RFC 3550 Appendix A.8 interarrival jitter and packet loss.
     */
    public synchronized void processRtpPacket(int sequenceNumber, long rtpTimestamp, int clockRateHz) {
        if (clockRateHz <= 0) clockRateHz = 8000;

        if (baseSeq < 0) {
            baseSeq = sequenceNumber;
            maxSeqReceived = sequenceNumber;
        } else {
            // Sequence wrap detection
            int delta = sequenceNumber - maxSeqReceived;
            if (delta > 0 && delta < 32768) {
                maxSeqReceived = sequenceNumber;
            } else if (delta < -32768) {
                cycleCount++;
                maxSeqReceived = sequenceNumber;
            }
        }

        packetsReceived++;

        // Jitter Calculation RFC 3550 Appendix A.8
        long arrivalTimestamp = (System.currentTimeMillis() * clockRateHz) / 1000;
        long transit = arrivalTimestamp - rtpTimestamp;
        if (lastRtpTransit != 0) {
            long d = transit - lastRtpTransit;
            if (d < 0) d = -d;
            jitter += (d - jitter) / 16.0;
        }
        lastRtpTransit = transit;
    }

    /**
     * Builds and transmits an RFC 3550 Receiver Report (RR) to the remote peer.
     */
    public synchronized void sendReceiverReport() {
        if (socket == null || socket.isClosed() || remoteAddress == null || remoteRtcpPort <= 0) return;

        try {
            byte[] rrPacket = buildReceiverReportPacket();
            DatagramPacket p = new DatagramPacket(rrPacket, rrPacket.length, remoteAddress, remoteRtcpPort);
            socket.send(p);
            packetsSent++;
            octetsSent += rrPacket.length;
        } catch (Exception e) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("[RTCP SEND ERROR] " + e.getMessage());
            }
        }
    }

    public byte[] buildReceiverReportPacket() {
        byte[] data = new byte[32]; // Header (8 bytes) + 1 Report Block (24 bytes)

        // Header: V=2, P=0, RC=1 (one report block), PT=201 (RR), Length=7 (32 bytes / 4 - 1 = 7)
        data[0] = (byte) 0x81; // V=2, P=0, RC=1
        data[1] = (byte) RTCP_PT_RR;
        data[2] = 0x00;
        data[3] = 0x07; // length in 32-bit words minus one = 7

        // Sender SSRC
        writeInt32(data, 4, (int) localSsrc);

        // Calculate loss stats
        long extendedMaxSeq = ((long) cycleCount << 16) | (maxSeqReceived & 0xFFFF);
        long expected = extendedMaxSeq - (baseSeq >= 0 ? baseSeq : 0) + 1;
        long expectedInterval = expected - expectedPrior;
        expectedPrior = expected;
        long receivedInterval = packetsReceived - receivedPrior;
        receivedPrior = packetsReceived;
        long lostInterval = expectedInterval - receivedInterval;
        if (expectedInterval == 0 || lostInterval <= 0) {
            fractionLost = 0;
        } else {
            fractionLost = (int) ((lostInterval << 8) / expectedInterval);
            if (fractionLost > 255) fractionLost = 255;
        }
        cumulativeLost = expected - packetsReceived;
        if (cumulativeLost < 0) cumulativeLost = 0;

        // Report Block: SSRC of source
        writeInt32(data, 8, (int) remoteSsrc);

        // Fraction lost (8 bits) + Cumulative lost (24 bits)
        data[12] = (byte) (fractionLost & 0xFF);
        data[13] = (byte) ((cumulativeLost >> 16) & 0xFF);
        data[14] = (byte) ((cumulativeLost >> 8) & 0xFF);
        data[15] = (byte) (cumulativeLost & 0xFF);

        // Extended highest sequence number
        writeInt32(data, 16, (int) extendedMaxSeq);

        // Interarrival jitter
        writeInt32(data, 20, (int) jitter);

        // Last SR timestamp (LSR)
        writeInt32(data, 24, (int) ((lastSrNtpTimestamp >> 16) & 0xFFFFFFFFL));

        // Delay since last SR (DLSR)
        long dlsr = 0;
        if (lastSrReceiptTimeMs > 0) {
            dlsr = ((System.currentTimeMillis() - lastSrReceiptTimeMs) * 65536) / 1000;
        }
        writeInt32(data, 28, (int) dlsr);

        return data;
    }

    public synchronized void parseIncomingRtcp(byte[] data, int offset, int length) {
        if (length < 8) return;

        int version = (data[offset] >> 6) & 0x03;
        if (version != 2) return;

        int pt = data[offset + 1] & 0xFF;
        int count = data[offset] & 0x1F;

        if (pt == RTCP_PT_SR && length >= 28) {
            long ssrc = readInt32(data, offset + 4);
            this.remoteSsrc = ssrc;

            long ntpSec = readInt32(data, offset + 8);
            long ntpFrac = readInt32(data, offset + 12);
            this.lastSrNtpTimestamp = ((ntpSec & 0xFFFF) << 16) | ((ntpFrac >> 16) & 0xFFFF);
            this.lastSrReceiptTimeMs = System.currentTimeMillis();

            long rtpTs = readInt32(data, offset + 16);
            long senderPackets = readInt32(data, offset + 20);
            long senderOctets = readInt32(data, offset + 24);

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[RTCP SR RECV] RemoteSSRC=0x%08X, Pkts=%d, Octets=%d", ssrc, senderPackets, senderOctets);
            }
        } else if (pt == RTCP_PT_RR && length >= 8) {
            long ssrc = readInt32(data, offset + 4);
            if (this.remoteSsrc == 0) this.remoteSsrc = ssrc;

            if (count > 0 && length >= 32) {
                int rFractionLost = data[offset + 12] & 0xFF;
                long rJitter = readInt32(data, offset + 20);
                long lsr = readInt32(data, offset + 24);
                long dlsr = readInt32(data, offset + 28);

                // Round-Trip Time computation RFC 3550 Section 6.4.1:
                // RTT = A - LSR - DLSR
                if (lsr != 0) {
                    long nowNtp = (System.currentTimeMillis() * 65536) / 1000;
                    long rttUnits = nowNtp - lsr - dlsr;
                    if (rttUnits > 0) {
                        this.roundTripTimeMs = (rttUnits * 1000) / 65536;
                    }
                }

                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[RTCP RR RECV] RemoteSSRC=0x%08X, FractionLost=%d, Jitter=%d, RTT=%d ms",
                            ssrc, rFractionLost, rJitter, roundTripTimeMs);
                }
            }
        }
    }

    public synchronized RtcpStats getStats(int clockRateHz) {
        if (clockRateHz <= 0) clockRateHz = 8000;
        RtcpStats s = new RtcpStats();
        s.localSsrc = localSsrc;
        s.remoteSsrc = remoteSsrc;
        s.packetsSent = packetsSent;
        s.packetsReceived = packetsReceived;
        s.jitterMs = (jitter * 1000.0) / clockRateHz;
        s.fractionLost = fractionLost;
        s.cumulativeLost = cumulativeLost;
        s.roundTripTimeMs = roundTripTimeMs;
        return s;
    }

    private static void writeInt32(byte[] b, int offset, int val) {
        b[offset]     = (byte) ((val >> 24) & 0xFF);
        b[offset + 1] = (byte) ((val >> 16) & 0xFF);
        b[offset + 2] = (byte) ((val >> 8) & 0xFF);
        b[offset + 3] = (byte) (val & 0xFF);
    }

    private static long readInt32(byte[] b, int offset) {
        return (((long)(b[offset] & 0xFF) << 24) |
                ((long)(b[offset + 1] & 0xFF) << 16) |
                ((long)(b[offset + 2] & 0xFF) << 8) |
                ((long)(b[offset + 3] & 0xFF))) & 0xFFFFFFFFL;
    }
}
