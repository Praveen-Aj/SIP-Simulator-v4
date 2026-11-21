/*
 * SIP Simulator v4 - Phase 9 Validation
 * CodecExpansionValidationTest: Validates CodecRegistry, Opus, G.722, PCMA/PCMU, and AMR Evaluation
 */
package test;

import sipinspector.Media.Codec.*;

import java.util.Arrays;

public class CodecExpansionValidationTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: CODEC EXPANSION & AMR EVALUATION (LIM-04) VALIDATION");
        System.out.println("================================================================================");

        testCodecRegistry();
        testOpusCodecAttributesAndPacketization();
        testG711AndG722Codecs();
        testAmrPatentAndLegalEvaluation();

        System.out.println(">>> ALL CODEC EXPANSION TESTS PASSED!");
    }

    private static void testCodecRegistry() {
        System.out.print("[TEST 1/4] CodecRegistry Resolution... ");
        AudioCodec pcma = CodecRegistry.getCodec(8);
        AudioCodec pcmu = CodecRegistry.getCodec(0);
        AudioCodec g722 = CodecRegistry.getCodec(9);
        AudioCodec opus = CodecRegistry.getCodec(111);

        if (pcma == null || !"PCMA".equalsIgnoreCase(pcma.getEncodingName())) {
            throw new RuntimeException("Failed to resolve PCMA (PT 8)");
        }
        if (pcmu == null || !"PCMU".equalsIgnoreCase(pcmu.getEncodingName())) {
            throw new RuntimeException("Failed to resolve PCMU (PT 0)");
        }
        if (g722 == null || !"G722".equalsIgnoreCase(g722.getEncodingName())) {
            throw new RuntimeException("Failed to resolve G722 (PT 9)");
        }
        if (opus == null || !"opus".equalsIgnoreCase(opus.getEncodingName())) {
            throw new RuntimeException("Failed to resolve Opus (PT 111)");
        }

        // Test lookup by name
        if (CodecRegistry.getCodecByName("opus") == null) {
            throw new RuntimeException("Failed to lookup Opus by name");
        }
        if (CodecRegistry.getCodecByName("PCMA") == null) {
            throw new RuntimeException("Failed to lookup PCMA by name");
        }

        System.out.println("PASSED");
    }

    private static void testOpusCodecAttributesAndPacketization() {
        System.out.print("[TEST 2/4] Opus RFC 7587 SDP Formatting & TOC Packetization... ");
        OpusAudioCodec opus = new OpusAudioCodec(111);

        // Verify clock rate is strictly 48000 Hz per RFC 7587 Section 6.1
        if (opus.getClockRate() != 48000) {
            throw new RuntimeException("Opus clock rate must be 48000 Hz, got: " + opus.getClockRate());
        }
        // Verify channels is 2 in SDP per RFC 7587 Section 6.1
        if (opus.getChannels() != 2) {
            throw new RuntimeException("Opus channels must be 2 in SDP, got: " + opus.getChannels());
        }

        String rtpmap = opus.getRtpmapAttribute();
        if (!"a=rtpmap:111 opus/48000/2".equals(rtpmap)) {
            throw new RuntimeException("Unexpected rtpmap attribute: " + rtpmap);
        }

        String fmtp = opus.getFmtpAttribute();
        if (fmtp == null || !fmtp.contains("minptime=20") || !fmtp.contains("useinbandfec=1")) {
            throw new RuntimeException("Unexpected fmtp attribute: " + fmtp);
        }

        // Test frame encoding / decoding
        byte[] syntheticPcm = new byte[1920]; // 20 ms @ 48 kHz stereo
        for (int i = 0; i < syntheticPcm.length; i++) {
            syntheticPcm[i] = (byte) (i % 128);
        }
        byte[] encoded = opus.encode(syntheticPcm, syntheticPcm.length);
        if (encoded == null || encoded.length == 0) {
            throw new RuntimeException("Opus encoding produced empty output");
        }

        // Validate TOC byte has Config 12 (bits 7..3 = 12)
        int toc = encoded[0] & 0xFF;
        int config = (toc >> 3) & 0x1F;
        if (config != 12) {
            throw new RuntimeException("Opus TOC byte config mismatch: expected 12, got " + config);
        }

        byte[] decoded = opus.decode(encoded, encoded.length);
        if (decoded == null || decoded.length == 0) {
            throw new RuntimeException("Opus decoding produced empty output");
        }

        System.out.println("PASSED");
    }

    private static void testG711AndG722Codecs() {
        System.out.print("[TEST 3/4] G.711 (PCMA/PCMU) & G.722 Encoding/Decoding... ");
        AudioCodec pcma = CodecRegistry.getCodec(8);
        AudioCodec pcmu = CodecRegistry.getCodec(0);
        AudioCodec g722 = CodecRegistry.getCodec(9);

        byte[] pcm8k = new byte[320]; // 160 16-bit samples = 20ms @ 8kHz
        Arrays.fill(pcm8k, (byte) 0x1A);

        byte[] pcmaEnc = pcma.encode(pcm8k, pcm8k.length);
        if (pcmaEnc.length != 160) throw new RuntimeException("Expected 160 bytes PCMA, got " + pcmaEnc.length);
        byte[] pcmaDec = pcma.decode(pcmaEnc, pcmaEnc.length);
        if (pcmaDec.length != 320) throw new RuntimeException("Expected 320 bytes decoded PCM, got " + pcmaDec.length);

        byte[] pcmuEnc = pcmu.encode(pcm8k, pcm8k.length);
        if (pcmuEnc.length != 160) throw new RuntimeException("Expected 160 bytes PCMU, got " + pcmuEnc.length);
        byte[] pcmuDec = pcmu.decode(pcmuEnc, pcmuEnc.length);
        if (pcmuDec.length != 320) throw new RuntimeException("Expected 320 bytes decoded PCM, got " + pcmuDec.length);

        // G.722 historical clock rate check
        if (g722.getClockRate() != 8000) {
            throw new RuntimeException("G.722 RTP clock rate must be 8000 Hz per RFC 3551");
        }
        if (g722.getFrameSamples() != 320) {
            throw new RuntimeException("G.722 20ms frame samples must be 320 (16 kHz audio line rate)");
        }

        System.out.println("PASSED");
    }

    private static void testAmrPatentAndLegalEvaluation() {
        System.out.print("[TEST 4/4] AMR-NB / AMR-WB Intellectual Property & Risk Evaluation... ");
        AmrCodecEvaluator.EvaluationReport amrNb = AmrCodecEvaluator.evaluateAmrNb();
        AmrCodecEvaluator.EvaluationReport amrWb = AmrCodecEvaluator.evaluateAmrWb();

        if (!amrNb.patentBlocked) {
            throw new RuntimeException("AMR-NB should be flagged as patent-blocked");
        }
        if (!amrNb.patentLicensingEntity.contains("VoiceAge") && !amrNb.patentLicensingEntity.contains("Via Licensing")) {
            throw new RuntimeException("AMR-NB licensing entity missing VoiceAge / Via Licensing");
        }
        if (!amrNb.requiredSdpAttributes.contains("a=rtpmap:96 AMR/8000/1")) {
            throw new RuntimeException("AMR-NB SDP attributes missing standard rtpmap");
        }

        if (!amrWb.patentBlocked) {
            throw new RuntimeException("AMR-WB should be flagged as patent-blocked");
        }
        if (!amrWb.requiredSdpAttributes.contains("a=rtpmap:97 AMR-WB/16000/1")) {
            throw new RuntimeException("AMR-WB SDP attributes missing standard rtpmap");
        }

        System.out.println("PASSED");
    }
}
