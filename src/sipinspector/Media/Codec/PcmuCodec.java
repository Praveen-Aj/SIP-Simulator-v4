/*
 * SIP Simulator v4 - Phase 9
 * PcmuCodec: G.711 mu-law (PT 0) implementation
 */
package sipinspector.Media.Codec;

import sipinspector.Media.AudioFormatUtils;

public class PcmuCodec implements AudioCodec {

    @Override
    public int getPayloadType() {
        return 0;
    }

    @Override
    public String getEncodingName() {
        return "PCMU";
    }

    @Override
    public int getClockRate() {
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
        return 160;
    }

    @Override
    public String getRtpmapAttribute() {
        return "a=rtpmap:0 PCMU/8000";
    }

    @Override
    public String getFmtpAttribute() {
        return null;
    }

    @Override
    public byte[] encode(byte[] pcmBigEndian, int length) {
        return AudioFormatUtils.pcmToUlawArray(pcmBigEndian, length);
    }

    @Override
    public byte[] decode(byte[] payload, int length) {
        return AudioFormatUtils.ulawToPcmArray(payload, length);
    }
}
