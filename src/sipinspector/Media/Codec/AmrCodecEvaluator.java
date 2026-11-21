/*
 * SIP Simulator v4 - Phase 9
 * AMR / AMR-WB Codec Architectural & Patent Evaluation
 *
 * Provides comprehensive technical and intellectual property analysis for
 * AMR (Adaptive Multi-Rate, RFC 4867) and AMR-WB (G.722.2) codecs.
 */
package sipinspector.Media.Codec;

import java.util.*;

public class AmrCodecEvaluator {

    public static class EvaluationReport {
        public final String codecName;
        public final String rfcSpec;
        public final boolean patentBlocked;
        public final String patentLicensingEntity;
        public final String legalRiskAssessment;
        public final String recommendedStrategy;
        public final List<String> requiredSdpAttributes;

        public EvaluationReport(String codecName, String rfcSpec, boolean patentBlocked,
                                String patentLicensingEntity, String legalRiskAssessment,
                                String recommendedStrategy, List<String> requiredSdpAttributes) {
            this.codecName = codecName;
            this.rfcSpec = rfcSpec;
            this.patentBlocked = patentBlocked;
            this.patentLicensingEntity = patentLicensingEntity;
            this.legalRiskAssessment = legalRiskAssessment;
            this.recommendedStrategy = recommendedStrategy;
            this.requiredSdpAttributes = requiredSdpAttributes;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("codecName", codecName);
            map.put("rfcSpec", rfcSpec);
            map.put("patentBlocked", patentBlocked);
            map.put("licensingEntity", patentLicensingEntity);
            map.put("legalRiskAssessment", legalRiskAssessment);
            map.put("recommendedStrategy", recommendedStrategy);
            map.put("requiredSdpAttributes", requiredSdpAttributes);
            return map;
        }
    }

    /**
     * Evaluates the legal and technical viability of embedding AMR-NB / AMR-WB in SIP Simulator v4.
     */
    public static EvaluationReport evaluateAmrNb() {
        List<String> sdp = Arrays.asList(
                "a=rtpmap:96 AMR/8000/1",
                "a=fmtp:96 octet-align=1;mode-set=0,2,4,7"
        );
        return new EvaluationReport(
                "AMR-NB (Adaptive Multi-Rate Narrowband)",
                "RFC 4867 / 3GPP TS 26.071",
                true,
                "VoiceAge Corporation / Via Licensing Patent Pool (Ericsson, Qualcomm, Nokia, NTT)",
                "High Legal Liability: Distribution of compiled AMR-NB C/Java codecs without paying per-unit royalties violates standard essential patent (SEP) licensing agreements.",
                "Pure-Java Clean-Room Architecture: Support RFC 4867 SDP negotiation and framing in SIP Simulator v4, but require end-users to supply external licensed shared libraries (e.g., libopencore-amrnb) rather than bundling proprietary binaries.",
                sdp
        );
    }

    public static EvaluationReport evaluateAmrWb() {
        List<String> sdp = Arrays.asList(
                "a=rtpmap:97 AMR-WB/16000/1",
                "a=fmtp:97 octet-align=1;mode-set=0,1,2"
        );
        return new EvaluationReport(
                "AMR-WB (Adaptive Multi-Rate Wideband / ITU-T G.722.2)",
                "RFC 4867 / 3GPP TS 26.171 / ITU-T G.722.2",
                true,
                "VoiceAge Licensing LLC Patent Pool",
                "High Legal Liability: G.722.2/AMR-WB patents remain active in multiple jurisdictions with strict per-channel royalty requirements.",
                "Use Open Standards (Opus RFC 7587 and G.722) for wideband telephony in SIP Simulator v4; provide modular SDP pass-through for AMR-WB interoperability testing.",
                sdp
        );
    }
}
