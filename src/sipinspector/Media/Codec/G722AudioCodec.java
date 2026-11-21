/*
 * SIP Simulator v4 - Phase 9
 * G722AudioCodec: ITU-T G.722 16 kHz Wideband Audio (PT 9)
 */
package sipinspector.Media.Codec;

import sipinspector.Media.G722Codec;

public class G722AudioCodec implements AudioCodec {

    private final G722Codec internalCodec = new G722Codec();

    @Override
    public int getPayloadType() {
        return 9;
    }

    @Override
    public String getEncodingName() {
        return "G722";
    }

    @Override
    public int getClockRate() {
        // RFC 3551 specifies 8000 Hz RTP clock rate for G722 due to historical reasons,
        // even though audio sampling rate is 16000 Hz.
        return 8000;
    }

    @Override
    public int getChannels() {
        return 1;
    }

    @Override
    public int getFrameDurationMs() {
        return 20;
    }

    @Override
    public int getFrameSamples() {
        // 20 ms @ 16 kHz = 320 samples
        return 320;
    }

    @Override
    public String getRtpmapAttribute() {
        return "a=rtpmap:9 G722/8000";
    }

    @Override
    public String getFmtpAttribute() {
        return null;
    }

    @Override
    public synchronized byte[] encode(byte[] pcmBigEndian, int length) {
        return internalCodec.encode(pcmBigEndian, length);
    }

    @Override
    public synchronized byte[] decode(byte[] payload, int length) {
        return internalCodec.decode(payload, length);
    }
}
