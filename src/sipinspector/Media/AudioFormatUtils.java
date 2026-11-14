/*
 *  SIP Inspector - Live Audio Support (v3)
 *
 *  Codec: G.711 A-law (PCMA), RTP Payload Type (PT)=8
 */
package sipinspector.Media;

/**
 * Audio format conversion utilities for live audio support.
 *
 * - PCM: 16-bit signed linear, 8kHz, mono
 * - G.711 A-law (PCMA): 8-bit samples, RTP PT=8
 */
public class AudioFormatUtils {

    public static final int RTP_PAYLOAD_TYPE_PCMA = 8;
    public static final int RTP_PAYLOAD_TYPE_PCMU = 0;
    public static final int RTP_PAYLOAD_TYPE_G722 = 9;
    public static final int SAMPLE_RATE_HZ = 8000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int FRAME_DURATION_MS = 20;
    public static final int SAMPLES_PER_FRAME = 160; // 8kHz * 20ms

    private static final int ALAW_MAX = 0x7FFF;

    private static final short[] SEG_END = {
        0x001F, 0x003F, 0x007F, 0x00FF,
        0x01FF, 0x03FF, 0x07FF, 0x0FFF
    };

    /**
     * Convert 16-bit signed PCM sample to G.711 A-law encoded byte (PCMA).
     */
    public static byte pcmToAlaw(short pcmSample) {
        // A-law companding operates on a 13-bit magnitude (segments cap at
        // 0xFFF). The 16-bit linear input must therefore be scaled down by 3
        // bits first (standard G.711 linear2alaw). Without this, any sample
        // louder than +/-4095 clipped to the top code while the DECODER still
        // produced full 16-bit output - an asymmetric gain that distorted all
        // transmitted (mic->A-law) audio above a quiet level.
        int pcm = pcmSample >> 3;
        int mask;
        int seg;
        int aval;

        if (pcm >= 0) {
            mask = 0xD5;
        } else {
            mask = 0x55;
            pcm = -pcm - 1;
            if (pcm < 0) {
                pcm = 0;
            }
        }

        if (pcm > ALAW_MAX) {
            pcm = ALAW_MAX;
        }

        seg = searchSegment(pcm);
        if (seg >= 8) {
            aval = 0x7F ^ mask;
            return (byte) aval;
        }

        aval = seg << 4;
        // Mantissa in the 13-bit domain (input already >>3), per standard
        // G.711 linear2alaw: >>1 for the two smallest segments, >>seg above.
        if (seg < 2) {
            aval |= (pcm >> 1) & 0x0F;
        } else {
            aval |= (pcm >> seg) & 0x0F;
        }

        aval ^= mask;
        return (byte) aval;
    }

    /**
     * Convert G.711 A-law encoded byte (PCMA) to 16-bit signed PCM sample.
     */
    public static short alawToPcm(byte alawByte) {
        int a = alawByte & 0xFF;
        a ^= 0x55;

        int t = (a & 0x0F) << 4;
        int seg = (a & 0x70) >> 4;

        switch (seg) {
            case 0:
                t += 8;
                break;
            case 1:
                t += 0x108;
                break;
            default:
                t += 0x108;
                t <<= (seg - 1);
                break;
        }

        return (short) (((a & 0x80) != 0) ? t : -t);
    }

    /**
     * Convert big-endian PCM16 bytes to G.711 A-law bytes (PCMA).
     */
    public static byte[] pcmToAlawArray(byte[] pcmBytes, int pcmLength) {
        int samples = pcmLength / 2;
        byte[] out = new byte[samples];
        for (int i = 0, j = 0; j < samples; i += 2, j++) {
            short sample = (short) (((pcmBytes[i] & 0xFF) << 8) | (pcmBytes[i + 1] & 0xFF));
            out[j] = pcmToAlaw(sample);
        }
        return out;
    }

    /**
     * Convert G.711 A-law bytes (PCMA) to big-endian PCM16 bytes.
     */
    public static byte[] alawToPcmArray(byte[] alawBytes, int alawLength) {
        byte[] out = new byte[alawLength * 2];
        for (int i = 0, j = 0; i < alawLength; i++, j += 2) {
            short sample = alawToPcm(alawBytes[i]);
            out[j] = (byte) (sample >> 8);
            out[j + 1] = (byte) sample;
        }
        return out;
    }
    
    // ---------------------------------------------------------------------
    // G.711 mu-law (PCMU, PT=0)
    // ---------------------------------------------------------------------

    private static final int ULAW_BIAS = 0x84;
    private static final int ULAW_CLIP = 32635;

    /**
     * Convert a 16-bit signed PCM sample to a G.711 mu-law encoded byte (PCMU).
     */
    public static byte pcmToUlaw(short pcmSample) {
        int sign = (pcmSample >> 8) & 0x80;
        int sample = pcmSample;
        if (sign != 0) {
            sample = -sample;
        }
        if (sample > ULAW_CLIP) {
            sample = ULAW_CLIP;
        }
        sample += ULAW_BIAS;

        int exponent = 7;
        for (int expMask = 0x4000; (sample & expMask) == 0 && exponent > 0; exponent--, expMask >>= 1) {
            // find exponent
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        int ulaw = ~(sign | (exponent << 4) | mantissa);
        return (byte) ulaw;
    }

    /**
     * Convert a G.711 mu-law encoded byte (PCMU) to a 16-bit signed PCM sample.
     */
    public static short ulawToPcm(byte ulawByte) {
        int u = ~ulawByte;
        int sign = u & 0x80;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int sample = ((mantissa << 3) + ULAW_BIAS) << exponent;
        sample -= ULAW_BIAS;
        return (short) (sign != 0 ? -sample : sample);
    }

    /**
     * Convert big-endian PCM16 bytes to G.711 mu-law bytes (PCMU).
     */
    public static byte[] pcmToUlawArray(byte[] pcmBytes, int pcmLength) {
        int samples = pcmLength / 2;
        byte[] out = new byte[samples];
        for (int i = 0, j = 0; j < samples; i += 2, j++) {
            short sample = (short) (((pcmBytes[i] & 0xFF) << 8) | (pcmBytes[i + 1] & 0xFF));
            out[j] = pcmToUlaw(sample);
        }
        return out;
    }

    /**
     * Convert G.711 mu-law bytes (PCMU) to big-endian PCM16 bytes.
     */
    public static byte[] ulawToPcmArray(byte[] ulawBytes, int ulawLength) {
        byte[] out = new byte[ulawLength * 2];
        for (int i = 0, j = 0; i < ulawLength; i++, j += 2) {
            short sample = ulawToPcm(ulawBytes[i]);
            out[j] = (byte) (sample >> 8);
            out[j + 1] = (byte) sample;
        }
        return out;
    }

    /**
     * Get frame size for a given sample rate and duration
     * Standard RTP frame: 20ms at 8kHz = 160 samples = 320 bytes (PCM) or 160 bytes (µ-law)
     * 
     * @param sampleRate Sample rate in Hz (8000 for G.711)
     * @param durationMs Frame duration in milliseconds
     * @return Frame size in samples
     */
    public static int getFrameSize(int sampleRate, int durationMs) {
        return (sampleRate * durationMs) / 1000;
    }
    
    /**
     * Get standard RTP frame size for G.711 (20ms @ 8kHz).
     */
    public static int getStandardFrameSize() {
        return SAMPLES_PER_FRAME;
    }
    
    /**
     * Validate that audio data is 8kHz mono 16-bit PCM
     * 
     * @param sampleRate Expected sample rate
     * @param channels Expected channels (1 for mono)
     * @param bitsPerSample Expected bits per sample (16 for PCM)
     * @return true if valid, false otherwise
     */
    public static boolean isValidAudioFormat(int sampleRate, int channels, int bitsPerSample) {
        return sampleRate == SAMPLE_RATE_HZ && channels == CHANNELS && bitsPerSample == BITS_PER_SAMPLE;
    }

    private static int searchSegment(int value) {
        for (int i = 0; i < SEG_END.length; i++) {
            if (value <= SEG_END[i]) {
                return i;
            }
        }
        return SEG_END.length;
    }
}
