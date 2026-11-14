/*
 *  SIP Inspector - Live Audio Support
 *
 *  Codec abstraction for live two-way audio. Each instance is stateful and
 *  used for ONE direction (a sender's encoder or a receiver's decoder), because
 *  G.722 carries adaptive predictor state per stream.
 *
 *  Supported payload types:
 *    PT 0  PCMU  - G.711 mu-law,  8 kHz line, 160 bytes / 20 ms
 *    PT 8  PCMA  - G.711 A-law,   8 kHz line, 160 bytes / 20 ms
 *    PT 9  G722  - sub-band ADPCM, 16 kHz line, 160 bytes / 20 ms (RTP clock 8 kHz)
 *
 *  The RTP timestamp increment per 20 ms frame is 160 for all three (G.722 uses
 *  the historical 8 kHz RTP clock even though it samples at 16 kHz).
 */
package sipinspector.Media;

public abstract class LiveAudioCodec {

    public abstract int payloadType();
    public abstract String rtpmapName();

    /** Audio line sample rate (8000 for G.711, 16000 for G.722). */
    public abstract int sampleRateHz();

    /** PCM samples captured/played per 20 ms frame at {@link #sampleRateHz()}. */
    public abstract int frameSamples();

    /** RTP timestamp units to advance per 20 ms frame (160 for all three). */
    public int tsIncrementPerFrame() {
        return 160;
    }

    /** PCM bytes (16-bit) per 20 ms frame captured from the mic. */
    public int frameBytesPcm() {
        return frameSamples() * 2;
    }

    /** Encode big-endian PCM16 to the RTP payload. */
    public abstract byte[] encode(byte[] pcmBE, int len);

    /** Decode an RTP payload to big-endian PCM16. */
    public abstract byte[] decode(byte[] payload, int len);

    public static boolean isSupported(int pt) {
        return pt == AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMU
            || pt == AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA
            || pt == AudioFormatUtils.RTP_PAYLOAD_TYPE_G722;
    }

    /** Create a fresh stateful codec for a payload type, or null if unsupported. */
    public static LiveAudioCodec create(int pt) {
        switch (pt) {
            case AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMU: return new Pcmu();
            case AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA: return new Pcma();
            case AudioFormatUtils.RTP_PAYLOAD_TYPE_G722: return new G722();
            default: return null;
        }
    }

    // ------------------------------------------------------------------
    private static final class Pcma extends LiveAudioCodec {
        public int payloadType() { return AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA; }
        public String rtpmapName() { return "PCMA"; }
        public int sampleRateHz() { return 8000; }
        public int frameSamples() { return 160; }
        public byte[] encode(byte[] pcmBE, int len) { return AudioFormatUtils.pcmToAlawArray(pcmBE, len); }
        public byte[] decode(byte[] p, int len) { return AudioFormatUtils.alawToPcmArray(p, len); }
    }

    private static final class Pcmu extends LiveAudioCodec {
        public int payloadType() { return AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMU; }
        public String rtpmapName() { return "PCMU"; }
        public int sampleRateHz() { return 8000; }
        public int frameSamples() { return 160; }
        public byte[] encode(byte[] pcmBE, int len) { return AudioFormatUtils.pcmToUlawArray(pcmBE, len); }
        public byte[] decode(byte[] p, int len) { return AudioFormatUtils.ulawToPcmArray(p, len); }
    }

    private static final class G722 extends LiveAudioCodec {
        private final G722Codec codec = new G722Codec();
        public int payloadType() { return AudioFormatUtils.RTP_PAYLOAD_TYPE_G722; }
        public String rtpmapName() { return "G722"; }
        public int sampleRateHz() { return 16000; }
        public int frameSamples() { return 320; } // 20 ms @ 16 kHz
        public byte[] encode(byte[] pcmBE, int len) { return codec.encode(pcmBE, len); }
        public byte[] decode(byte[] p, int len) { return codec.decode(p, len); }
    }
}
