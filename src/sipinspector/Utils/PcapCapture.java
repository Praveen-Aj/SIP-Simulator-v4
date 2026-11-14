/*
 * PcapCapture - live packet capture to a Wireshark-readable .pcap file.
 *
 * Zero external dependencies: synthesizes libpcap global/record headers and
 * Ethernet + IPv4/IPv6 + UDP/TCP framing around each captured SIP payload.
 * Captured SIP is the application-level message (so TLS traffic is written in
 * cleartext, which is exactly what you want for interop debugging).
 *
 * Thread-safe singleton: multiple socket threads call writePacket concurrently.
 */
package sipinspector.Utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class PcapCapture {

    private static final PcapCapture INSTANCE = new PcapCapture();

    public static PcapCapture getInstance() {
        return INSTANCE;
    }

    private static final int LINKTYPE_ETHERNET = 1;

    public static final boolean UDP = false;
    public static final boolean TCP = true;

    private DataOutputStream out;
    private boolean capturing = false;
    private String currentFile = null;
    private int packetCount = 0;
    // Per-flow monotonic TCP sequence numbers (key = "src:port>dst:port").
    private final Map<String, Long> tcpSeq = new HashMap<>();

    private PcapCapture() {
    }

    public synchronized boolean isCapturing() {
        return capturing;
    }

    public synchronized String getCurrentFile() {
        return currentFile;
    }

    public synchronized int getPacketCount() {
        return packetCount;
    }

    /** Begin capture, writing the libpcap global header. Replaces any active capture. */
    public synchronized void start(File file) throws IOException {
        if (capturing) {
            stop();
        }
        DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        d.writeInt(0xa1b2c3d4);        // magic number
        d.writeShort(2);               // version major
        d.writeShort(4);               // version minor
        d.writeInt(0);                 // thiszone (GMT)
        d.writeInt(0);                 // sigfigs
        d.writeInt(65535);             // snaplen
        d.writeInt(LINKTYPE_ETHERNET); // network (DLT_EN10MB)
        d.flush();
        this.out = d;
        this.capturing = true;
        this.currentFile = file.getAbsolutePath();
        this.packetCount = 0;
        this.tcpSeq.clear();
    }

    /** Flush and close the current capture (safe to call when not capturing). */
    public synchronized void stop() {
        if (!capturing) {
            return;
        }
        try {
            out.flush();
            out.close();
        } catch (IOException ignore) {
        }
        out = null;
        capturing = false;
    }

    /**
     * Write one packet: {@code payload[0..payloadLen)} sent from src to dst.
     *
     * @param tcp true for TCP framing (also used for TLS), false for UDP.
     */
    public synchronized void writePacket(InetAddress src, int srcPort, InetAddress dst, int dstPort,
                                         boolean tcp, byte[] payload, int payloadLen) {
        writePacket(src, srcPort, dst, dstPort, tcp, payload, 0, payloadLen);
    }

    /** Offset-aware variant for callers whose payload sits inside a larger buffer (e.g. RTP in a pcap frame). */
    public synchronized void writePacket(InetAddress src, int srcPort, InetAddress dst, int dstPort,
                                         boolean tcp, byte[] payload, int offset, int payloadLen) {
        if (!capturing || out == null || src == null || dst == null || payload == null
                || offset < 0 || payloadLen < 0 || offset + payloadLen > payload.length) {
            return;
        }
        if (offset != 0) {
            byte[] copy = new byte[payloadLen];
            System.arraycopy(payload, offset, copy, 0, payloadLen);
            payload = copy;
        }
        try {
            byte[] frame;
            boolean v6 = src.getAddress().length == 16 && dst.getAddress().length == 16;
            if (v6) {
                frame = buildV6(src, srcPort, dst, dstPort, tcp, payload, payloadLen);
            } else {
                frame = buildV4(src, srcPort, dst, dstPort, tcp, payload, payloadLen);
            }
            long now = System.currentTimeMillis();
            out.writeInt((int) (now / 1000L));           // ts_sec
            out.writeInt((int) ((now % 1000L) * 1000L)); // ts_usec
            out.writeInt(frame.length);                  // incl_len
            out.writeInt(frame.length);                  // orig_len
            out.write(frame);
            out.flush();
            packetCount++;
        } catch (IOException ignore) {
        }
    }

    private byte[] buildV4(InetAddress src, int srcPort, InetAddress dst, int dstPort,
                           boolean tcp, byte[] payload, int payloadLen) throws IOException {
        int l4 = tcp ? 20 : 8;
        int ipTotal = 20 + l4 + payloadLen;

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);

        // Ethernet header (dummy MACs, EtherType IPv4)
        d.write(new byte[12]);
        d.writeShort(0x0800);

        // IPv4 header (assembled into a buffer so we can checksum it)
        byte[] ip = new byte[20];
        ip[0] = 0x45;                       // version 4, IHL 5
        ip[1] = 0;                          // DSCP/ECN
        ip[2] = (byte) (ipTotal >> 8);
        ip[3] = (byte) ipTotal;
        ip[4] = 0; ip[5] = 0;              // identification
        ip[6] = 0x40; ip[7] = 0;          // flags: Don't Fragment
        ip[8] = 64;                        // TTL
        ip[9] = (byte) (tcp ? 6 : 17);     // protocol
        ip[10] = 0; ip[11] = 0;           // header checksum (filled below)
        byte[] sa = src.getAddress();
        byte[] da = dst.getAddress();
        System.arraycopy(sa, 0, ip, 12, 4);
        System.arraycopy(da, 0, ip, 16, 4);
        int cs = checksum(ip, 20);
        ip[10] = (byte) (cs >> 8);
        ip[11] = (byte) cs;
        d.write(ip);

        if (tcp) {
            writeTcp(d, src, srcPort, dst, dstPort, payloadLen);
        } else {
            writeUdp(d, srcPort, dstPort, payloadLen);
        }
        d.write(payload, 0, payloadLen);
        d.flush();
        return b.toByteArray();
    }

    private byte[] buildV6(InetAddress src, int srcPort, InetAddress dst, int dstPort,
                           boolean tcp, byte[] payload, int payloadLen) throws IOException {
        int l4 = tcp ? 20 : 8;
        int payLen = l4 + payloadLen;

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);

        // Ethernet header (EtherType IPv6)
        d.write(new byte[12]);
        d.writeShort(0x86DD);

        // IPv6 header (40 bytes, no checksum)
        d.writeInt(0x60000000);            // version 6, traffic class 0, flow label 0
        d.writeShort(payLen);              // payload length
        d.writeByte(tcp ? 6 : 17);         // next header
        d.writeByte(64);                   // hop limit
        d.write(src.getAddress());         // 16 bytes
        d.write(dst.getAddress());         // 16 bytes

        if (tcp) {
            writeTcp(d, src, srcPort, dst, dstPort, payloadLen);
        } else {
            writeUdp(d, srcPort, dstPort, payloadLen);
        }
        d.write(payload, 0, payloadLen);
        d.flush();
        return b.toByteArray();
    }

    private void writeUdp(DataOutputStream d, int srcPort, int dstPort, int payloadLen) throws IOException {
        d.writeShort(srcPort & 0xFFFF);
        d.writeShort(dstPort & 0xFFFF);
        d.writeShort((8 + payloadLen) & 0xFFFF);
        d.writeShort(0); // checksum (0 = not computed)
    }

    private void writeTcp(DataOutputStream d, InetAddress src, int srcPort, InetAddress dst, int dstPort,
                          int payloadLen) throws IOException {
        String key = src.getHostAddress() + ":" + srcPort + ">" + dst.getHostAddress() + ":" + dstPort;
        Long prev = tcpSeq.get(key);
        long seq = (prev == null) ? 1L : prev;
        tcpSeq.put(key, seq + Math.max(1, payloadLen));

        d.writeShort(srcPort & 0xFFFF);
        d.writeShort(dstPort & 0xFFFF);
        d.writeInt((int) seq);             // sequence number
        d.writeInt(0);                     // acknowledgement number
        d.writeShort((5 << 12) | 0x018);   // data offset 5 words, flags PSH+ACK
        d.writeShort(0xFFFF);              // window size
        d.writeShort(0);                   // checksum (0 = not computed)
        d.writeShort(0);                   // urgent pointer
    }

    /** Standard internet 16-bit one's-complement checksum over buf[0..len). */
    private static int checksum(byte[] buf, int len) {
        int sum = 0;
        for (int i = 0; i < len; i += 2) {
            int hi = buf[i] & 0xFF;
            int lo = (i + 1 < len) ? (buf[i + 1] & 0xFF) : 0;
            sum += (hi << 8) | lo;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (~sum) & 0xFFFF;
    }
}
