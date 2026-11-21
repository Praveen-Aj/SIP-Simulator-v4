/*
 * SIP Simulator v4 - Phase 9
 * PcmaCodec: G.711 A-law (PT 8) implementation
 */
package sipinspector.Media.Codec;

import sipinspector.Media.AudioFormatUtils;

public class PcmaCodec implements AudioCodec {

    @Override
    public int getPayloadType() {
        return 8;
    }

    @Override
    public String getEncodingName() {
        return "PCMA";
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
        return "a=rtpmap:8 PCMA/8000";
    }

    @Override
    public String getFmtpAttribute() {
        return null;
    }

    @Override
    public byte[] encode(byte[] pcmBigEndian, int length) {
        return AudioFormatUtils.pcmToAlawArray(pcmBigEndian, length);
    }

    @Override
    public byte[] decode(byte[] payload, int length) {
        return AudioFormatUtils.alawToPcmArray(payload, length);
    }
}
