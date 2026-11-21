/*
 * SIP Simulator v4 - Phase 9
 * CodecRegistry: Centralized registry for standard and wideband audio codecs
 */
package sipinspector.Media.Codec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CodecRegistry {

    private static final Map<Integer, AudioCodec> codecsByPt = new ConcurrentHashMap<>();
    private static final Map<String, AudioCodec> codecsByName = new ConcurrentHashMap<>();

    static {
        registerCodec(new PcmaCodec());
        registerCodec(new PcmuCodec());
        registerCodec(new G722AudioCodec());
        registerCodec(new OpusAudioCodec());
    }

    public static void registerCodec(AudioCodec codec) {
        if (codec == null) return;
        codecsByPt.put(codec.getPayloadType(), codec);
        codecsByName.put(codec.getEncodingName().toUpperCase(), codec);
    }

    public static AudioCodec getCodec(int payloadType) {
        return codecsByPt.get(payloadType);
    }

    public static AudioCodec getCodecByName(String encodingName) {
        if (encodingName == null) return null;
        return codecsByName.get(encodingName.trim().toUpperCase());
    }

    public static boolean isSupported(int payloadType) {
        return codecsByPt.containsKey(payloadType);
    }

    public static Collection<AudioCodec> getAllCodecs() {
        return Collections.unmodifiableCollection(codecsByPt.values());
    }

    public static List<Integer> getSupportedPayloadTypes() {
        return new ArrayList<>(codecsByPt.keySet());
    }
}
