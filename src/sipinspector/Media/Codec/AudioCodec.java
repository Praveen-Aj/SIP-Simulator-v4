/*
 * SIP Simulator v4 - Phase 9 Codec Architecture
 * AudioCodec Interface
 */
package sipinspector.Media.Codec;

public interface AudioCodec {

    /** RTP Payload Type number (0=PCMU, 8=PCMA, 9=G722, 111/dynamic=Opus, etc.) */
    int getPayloadType();

    /** Standard MIME / SDP encoding name (e.g. "PCMA", "PCMU", "G722", "opus") */
    String getEncodingName();

    /** RTP clock rate in Hz (e.g. 8000 for PCMA/PCMU/G722, 48000 for Opus) */
    int getClockRate();

    /** Audio channels (1 = mono, 2 = stereo) */
    int getChannels();

    /** Standard nominal frame duration in milliseconds (e.g. 20 ms) */
    int getFrameDurationMs();

    /** Frame size in big-endian PCM16 samples */
    int getFrameSamples();

    /** Generate SDP rtpmap attribute line without trailing CRLF */
    String getRtpmapAttribute();

    /** Generate SDP fmtp attribute line without trailing CRLF (or null if none) */
    String getFmtpAttribute();

    /** Encode raw 16-bit linear PCM (big-endian) to RTP payload bytes */
    byte[] encode(byte[] pcmBigEndian, int length);

    /** Decode RTP payload bytes to 16-bit linear PCM (big-endian) */
    byte[] decode(byte[] payload, int length);
}
