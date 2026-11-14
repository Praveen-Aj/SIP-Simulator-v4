/*
 * Tone Synthesizer
 * Generates standard telephony tones (dial, busy, reorder, end-of-call beep)
 * as G.711 A-law RTP-in-.pcap files, playable through the existing
 * cmd_PlayPcap / RTPSenderThread / PurePcapRtpPlayer pipeline - the same
 * mechanism already used for the recorded RBT/Hold/Guide/Conversation tones.
 *
 * Generation is idempotent: each file is created once under pcap/ and reused
 * on subsequent runs.
 */
package sipinspector.Media;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class ToneSynthesizer {

    private static final int SAMPLE_RATE = 8000;
    private static final int SAMPLES_PER_FRAME = 160; // 20ms @ 8kHz
    private static final int RTP_PAYLOAD_TYPE_PCMA = 8;

    public static final String DIAL_TONE = "pcap/DialTone_generated.pcap";
    public static final String BUSY_TONE = "pcap/BusyTone_generated.pcap";
    public static final String REORDER_TONE = "pcap/ReorderTone_generated.pcap";
    public static final String END_TONE = "pcap/EndTone_generated.pcap";

    /** Generate any missing built-in synthesized tones. Safe to call repeatedly. */
    public static void ensureGenerated() {
        generateIfMissing(DIAL_TONE, dualTone(350, 440, 2000, 0, 0, 2000));
        generateIfMissing(BUSY_TONE, dualTone(480, 620, 500, 500, 0, 4000));
        generateIfMissing(REORDER_TONE, dualTone(480, 620, 250, 250, 0, 4000));
        generateIfMissing(END_TONE, dualTone(600, 0, 350, 0, 0, 350));
    }

    private static void generateIfMissing(String path, byte[] pcmBigEndian) {
        File f = new File(path);
        if (f.exists()) {
            return;
        }
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        try {
            writeRtpPcap(f, pcmBigEndian);
        } catch (IOException e) {
            System.err.println("ToneSynthesizer: failed to generate " + path + ": " + e.getMessage());
        }
    }

    /**
     * Build big-endian 16-bit PCM samples for a dual-frequency tone with an
     * on/off cadence, repeated to fill totalMs. onMs=continuous tone if
     * offMs is 0. freq2=0 produces a single-frequency tone (used for the
     * end-of-call beep).
     */
    private static byte[] dualTone(int freq1, int freq2, int onMs, int offMs, int fadeMs, int totalMs) {
        int totalSamples = SAMPLE_RATE * totalMs / 1000;
        int onSamples = SAMPLE_RATE * onMs / 1000;
        int offSamples = SAMPLE_RATE * offMs / 1000;
        int cycle = Math.max(1, onSamples + offSamples);

        byte[] pcm = new byte[totalSamples * 2];
        double amplitude = 0.35 * Short.MAX_VALUE;
        for (int i = 0; i < totalSamples; i++) {
            int posInCycle = offSamples > 0 ? (i % cycle) : 0;
            boolean toneOn = offSamples == 0 || posInCycle < onSamples;
            double sample = 0;
            if (toneOn) {
                double t = i / (double) SAMPLE_RATE;
                sample = Math.sin(2 * Math.PI * freq1 * t);
                if (freq2 > 0) {
                    sample = 0.5 * sample + 0.5 * Math.sin(2 * Math.PI * freq2 * t);
                }
            }
            short s = (short) Math.round(sample * amplitude);
            pcm[i * 2] = (byte) (s >> 8);
            pcm[i * 2 + 1] = (byte) s;
        }
        return pcm;
    }

    private static void writeRtpPcap(File file, byte[] pcmBigEndian) throws IOException {
        byte[] alaw = AudioFormatUtils.pcmToAlawArray(pcmBigEndian, pcmBigEndian.length);

        Random rnd = new Random();
        int ssrc = rnd.nextInt(Integer.MAX_VALUE);
        int seq = rnd.nextInt(0xFFFF);
        int timestamp = rnd.nextInt(Integer.MAX_VALUE);

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            // pcap global header
            out.writeInt(0xa1b2c3d4);
            out.writeShort(2);
            out.writeShort(4);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(65535);
            out.writeInt(1); // DLT_EN10MB

            long frameMillis = 0;
            for (int off = 0; off < alaw.length; off += SAMPLES_PER_FRAME) {
                int len = Math.min(SAMPLES_PER_FRAME, alaw.length - off);
                byte[] rtpPacket = buildRtpPacket(alaw, off, len, seq, timestamp, ssrc);
                byte[] frame = buildEthernetIpUdpFrame(rtpPacket);

                out.writeInt((int) (frameMillis / 1000L));
                out.writeInt((int) ((frameMillis % 1000L) * 1000L));
                out.writeInt(frame.length);
                out.writeInt(frame.length);
                out.write(frame);

                seq = (seq + 1) & 0xFFFF;
                timestamp += SAMPLES_PER_FRAME;
                frameMillis += 20;
            }
        }
    }

    private static byte[] buildRtpPacket(byte[] alaw, int offset, int len, int seq, int timestamp, int ssrc) {
        byte[] pkt = new byte[12 + len];
        pkt[0] = (byte) 0x80; // V=2
        pkt[1] = (byte) RTP_PAYLOAD_TYPE_PCMA;
        pkt[2] = (byte) (seq >> 8);
        pkt[3] = (byte) seq;
        pkt[4] = (byte) (timestamp >> 24);
        pkt[5] = (byte) (timestamp >> 16);
        pkt[6] = (byte) (timestamp >> 8);
        pkt[7] = (byte) timestamp;
        pkt[8] = (byte) (ssrc >> 24);
        pkt[9] = (byte) (ssrc >> 16);
        pkt[10] = (byte) (ssrc >> 8);
        pkt[11] = (byte) ssrc;
        System.arraycopy(alaw, offset, pkt, 12, len);
        return pkt;
    }

    private static byte[] buildEthernetIpUdpFrame(byte[] rtpPayload) throws IOException {
        int udpLen = 8 + rtpPayload.length;
        int ipLen = 20 + udpLen;

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);

        d.write(new byte[12]); // dummy MACs
        d.writeShort(0x0800);  // EtherType IPv4

        byte[] ip = new byte[20];
        ip[0] = 0x45;
        ip[2] = (byte) (ipLen >> 8);
        ip[3] = (byte) ipLen;
        ip[6] = 0x40; // Don't Fragment
        ip[8] = 64;   // TTL
        ip[9] = 17;   // UDP
        ip[12] = 127; ip[13] = 0; ip[14] = 0; ip[15] = 1;
        ip[16] = 127; ip[17] = 0; ip[18] = 0; ip[19] = 2;
        int cs = checksum(ip, 20);
        ip[10] = (byte) (cs >> 8);
        ip[11] = (byte) cs;
        d.write(ip);

        d.writeShort(6000);
        d.writeShort(6000);
        d.writeShort(udpLen);
        d.writeShort(0); // checksum not computed

        d.write(rtpPayload);
        d.flush();
        return b.toByteArray();
    }

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
