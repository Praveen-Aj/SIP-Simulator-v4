package sipinspector.Media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal SRTP context for:
 * - AES_CM_128_HMAC_SHA1_80
 * - SDES keying (a=crypto:... inline:...)
 * - RTP protection only (no SRTCP)
 *
 * Fail-closed: authentication mismatch causes unprotect() to return null.
 */
public final class SrtpContext {

    public static final int AUTH_TAG_LEN = 10; // 80-bit

    private final byte[] masterKey;  // 16 bytes
    private final byte[] masterSalt; // 14 bytes

    private final byte[] sessionEncKey;  // 16 bytes
    private final byte[] sessionAuthKey; // 20 bytes
    private final byte[] sessionSalt;    // 14 bytes

    private final SecretKeySpec encKeySpec;
    private final SecretKeySpec authKeySpec;

    private final Map<Integer, SsrcState> ssrcStates = new HashMap<>();

    private static final class SsrcState {
        int ssrc;
        int roc;
        int lastSeq = -1;

        SsrcState(int ssrc) {
            this.ssrc = ssrc;
            this.roc = 0;
        }
    }

    public SrtpContext(byte[] masterKey, byte[] masterSalt) throws GeneralSecurityException {
        if (masterKey == null || masterKey.length != 16) {
            throw new GeneralSecurityException("SRTP master key must be 16 bytes");
        }
        if (masterSalt == null || masterSalt.length != 14) {
            throw new GeneralSecurityException("SRTP master salt must be 14 bytes");
        }
        this.masterKey = masterKey.clone();
        this.masterSalt = masterSalt.clone();

        // KDF labels per RFC3711: 0x00 enc, 0x01 auth, 0x02 salt
        this.sessionEncKey = deriveKey((byte) 0x00, 16);
        this.sessionAuthKey = deriveKey((byte) 0x01, 20);
        this.sessionSalt = deriveKey((byte) 0x02, 14);

        this.encKeySpec = new SecretKeySpec(sessionEncKey, "AES");
        this.authKeySpec = new SecretKeySpec(sessionAuthKey, "HmacSHA1");
    }

    /**
     * Parse SDES crypto line. Only supports AES_CM_128_HMAC_SHA1_80.
     * Example:
     * a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:BASE64KEYSALT|2^31
     */
    public static SrtpContext fromSdesCryptoLine(String cryptoLine) throws GeneralSecurityException {
        if (cryptoLine == null) {
            throw new GeneralSecurityException("Missing SRTP crypto line");
        }
        String line = cryptoLine.trim();
        if (line.startsWith("a=")) {
            line = line.substring(2);
        }
        if (!line.startsWith("crypto:")) {
            throw new GeneralSecurityException("Invalid crypto line: " + cryptoLine);
        }
        if (!cryptoLine.contains("AES_CM_128_HMAC_SHA1_80")) {
            throw new GeneralSecurityException("Unsupported SRTP suite (expected AES_CM_128_HMAC_SHA1_80)");
        }

        int inlineIdx = cryptoLine.indexOf("inline:");
        if (inlineIdx < 0) {
            throw new GeneralSecurityException("Crypto line missing inline key/salt");
        }
        String afterInline = cryptoLine.substring(inlineIdx + "inline:".length()).trim();
        String b64 = afterInline;
        int pipe = afterInline.indexOf('|');
        if (pipe >= 0) {
            b64 = afterInline.substring(0, pipe);
        } else {
            int sp = afterInline.indexOf(' ');
            if (sp >= 0) {
                b64 = afterInline.substring(0, sp);
            }
        }
        byte[] keySalt = Base64.getDecoder().decode(b64);
        if (keySalt.length < 30) {
            throw new GeneralSecurityException("Invalid inline key/salt length: " + keySalt.length);
        }
        byte[] mk = new byte[16];
        byte[] ms = new byte[14];
        System.arraycopy(keySalt, 0, mk, 0, 16);
        System.arraycopy(keySalt, 16, ms, 0, 14);
        return new SrtpContext(mk, ms);
    }

    /**
     * Protect RTP packet in-place: encrypt payload and append auth tag.
     * @param rtp Plain RTP packet (header+payload). Must not already include auth tag.
     * @return SRTP packet (header + encrypted payload + auth tag)
     */
    public synchronized byte[] protectRtp(byte[] rtp, int rtpLen) throws GeneralSecurityException {
        if (rtpLen < 12) {
            throw new GeneralSecurityException("RTP too short");
        }
        int cc = rtp[0] & 0x0F;
        boolean hasExt = (rtp[0] & 0x10) != 0;
        int headerLen = 12 + (cc * 4);
        if (rtpLen < headerLen) {
            throw new GeneralSecurityException("RTP header truncated");
        }
        if (hasExt) {
            if (rtpLen < headerLen + 4) {
                throw new GeneralSecurityException("RTP extension truncated");
            }
            int extLenWords = ((rtp[headerLen + 2] & 0xFF) << 8) | (rtp[headerLen + 3] & 0xFF);
            headerLen += 4 + (extLenWords * 4);
            if (rtpLen < headerLen) {
                throw new GeneralSecurityException("RTP extension truncated");
            }
        }

        int seq = ((rtp[2] & 0xFF) << 8) | (rtp[3] & 0xFF);
        int ssrc = ((rtp[8] & 0xFF) << 24) | ((rtp[9] & 0xFF) << 16) | ((rtp[10] & 0xFF) << 8) | (rtp[11] & 0xFF);
        SsrcState st = getOrCreateState(ssrc);
        int roc = updateRocOnSend(st, seq);
        long index = (toUnsignedInt(roc) << 16) | (seq & 0xFFFFL);

        // Encrypt payload (header is not encrypted)
        int payloadLen = rtpLen - headerLen;
        byte[] out = new byte[rtpLen + AUTH_TAG_LEN];
        System.arraycopy(rtp, 0, out, 0, rtpLen);
        aesCmXor(out, headerLen, payloadLen, ssrc, index);

        // Auth tag = HMAC-SHA1( packet || ROC ) truncated to 80 bits
        byte[] tag = computeAuthTag(out, rtpLen, roc);
        System.arraycopy(tag, 0, out, rtpLen, AUTH_TAG_LEN);
        return out;
    }

    /**
     * Unprotect SRTP packet: verify auth tag, decrypt payload, return plain RTP (without tag).
     * Fail-closed: returns null if auth fails or packet invalid.
     */
    public synchronized byte[] unprotectRtp(byte[] srtp, int srtpLen) throws GeneralSecurityException {
        if (srtpLen < 12 + AUTH_TAG_LEN) {
            return null;
        }
        int version = (srtp[0] >> 6) & 0x03;
        if (version != 2) {
            return null;
        }
        int rtpLen = srtpLen - AUTH_TAG_LEN;
        int seq = ((srtp[2] & 0xFF) << 8) | (srtp[3] & 0xFF);
        int ssrc = ((srtp[8] & 0xFF) << 24) | ((srtp[9] & 0xFF) << 16) | ((srtp[10] & 0xFF) << 8) | (srtp[11] & 0xFF);

        SsrcState st = getOrCreateState(ssrc);
        int rocGuess = guessRocOnRecv(st, seq);

        // Verify auth before decrypt (fail closed)
        byte[] expected = computeAuthTag(srtp, rtpLen, rocGuess);
        int tagOffset = rtpLen;
        int diff = 0;
        for (int i = 0; i < AUTH_TAG_LEN; i++) {
            diff |= (srtp[tagOffset + i] ^ expected[i]);
        }
        if (diff != 0) {
            return null;
        }

        // Parse header length to locate payload
        int cc = srtp[0] & 0x0F;
        boolean hasExt = (srtp[0] & 0x10) != 0;
        int headerLen = 12 + (cc * 4);
        if (rtpLen < headerLen) {
            return null;
        }
        if (hasExt) {
            if (rtpLen < headerLen + 4) {
                return null;
            }
            int extLenWords = ((srtp[headerLen + 2] & 0xFF) << 8) | (srtp[headerLen + 3] & 0xFF);
            headerLen += 4 + (extLenWords * 4);
            if (rtpLen < headerLen) {
                return null;
            }
        }

        long index = (toUnsignedInt(rocGuess) << 16) | (seq & 0xFFFFL);
        byte[] out = new byte[rtpLen];
        System.arraycopy(srtp, 0, out, 0, rtpLen);
        aesCmXor(out, headerLen, rtpLen - headerLen, ssrc, index);

        // Update receive state after successful auth/decrypt
        st.roc = rocGuess;
        st.lastSeq = seq;

        return out;
    }

    private SsrcState getOrCreateState(int ssrc) {
        SsrcState st = ssrcStates.get(ssrc);
        if (st == null) {
            st = new SsrcState(ssrc);
            ssrcStates.put(ssrc, st);
        }
        return st;
    }

    private static int updateRocOnSend(SsrcState st, int seq) {
        if (st.lastSeq >= 0) {
            int last = st.lastSeq & 0xFFFF;
            if (seq < last && (last - seq) > 0x8000) {
                st.roc++;
            }
        }
        st.lastSeq = seq;
        return st.roc;
    }

    private static int guessRocOnRecv(SsrcState st, int seq) {
        if (st.lastSeq < 0) {
            return st.roc;
        }
        int last = st.lastSeq & 0xFFFF;
        int roc = st.roc;
        if (seq < last && (last - seq) > 0x8000) {
            return roc + 1;
        }
        if (seq > last && (seq - last) > 0x8000 && roc > 0) {
            return roc - 1;
        }
        return roc;
    }

    private byte[] computeAuthTag(byte[] packet, int rtpLen, int roc) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(authKeySpec);
        mac.update(packet, 0, rtpLen);
        byte[] rocBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(roc).array();
        mac.update(rocBytes);
        byte[] full = mac.doFinal();
        byte[] tag = new byte[AUTH_TAG_LEN];
        System.arraycopy(full, 0, tag, 0, AUTH_TAG_LEN);
        return tag;
    }

    private void aesCmXor(byte[] buf, int off, int len, int ssrc, long packetIndex) throws GeneralSecurityException {
        if (len <= 0) {
            return;
        }
        byte[] iv = buildIv(ssrc, packetIndex);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encKeySpec, new IvParameterSpec(iv));
        byte[] keystreamXor = cipher.doFinal(buf, off, len);
        System.arraycopy(keystreamXor, 0, buf, off, len);
    }

    /**
     * SRTP IV for AES-CM:
     * IV = (session_salt << 16) XOR (ssrc << 64) XOR (packetIndex << 16)
     */
    private byte[] buildIv(int ssrc, long packetIndex) {
        byte[] iv = new byte[16];
        // sessionSalt is 14 bytes; left shift by 16 bits => append two 0x00
        System.arraycopy(sessionSalt, 0, iv, 0, 14);
        iv[14] = 0x00;
        iv[15] = 0x00;

        // XOR SSRC into bytes 4..7 (big-endian)
        iv[4] ^= (byte) ((ssrc >> 24) & 0xFF);
        iv[5] ^= (byte) ((ssrc >> 16) & 0xFF);
        iv[6] ^= (byte) ((ssrc >> 8) & 0xFF);
        iv[7] ^= (byte) (ssrc & 0xFF);

        // packetIndex is 48-bit; XOR into bytes 8..13, then <<16 => lower 2 bytes are zero
        long idx48 = packetIndex & 0xFFFFFFFFFFFFL;
        iv[8] ^= (byte) ((idx48 >> 40) & 0xFF);
        iv[9] ^= (byte) ((idx48 >> 32) & 0xFF);
        iv[10] ^= (byte) ((idx48 >> 24) & 0xFF);
        iv[11] ^= (byte) ((idx48 >> 16) & 0xFF);
        iv[12] ^= (byte) ((idx48 >> 8) & 0xFF);
        iv[13] ^= (byte) (idx48 & 0xFF);
        return iv;
    }

    /**
     * RFC3711 KDF using AES-ECB with masterKey.
     * Key_id = (masterSalt << 16) XOR (label << 48).
     * The derived material is the AES output stream for counter blocks.
     */
    private byte[] deriveKey(byte label, int outLen) throws GeneralSecurityException {
        byte[] keyId = new byte[16];
        System.arraycopy(masterSalt, 0, keyId, 0, 14);
        keyId[14] = 0x00;
        keyId[15] = 0x00;

        // XOR label into byte 7 (bits 48..55)
        keyId[7] ^= label;

        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"));

        byte[] out = new byte[outLen];
        int produced = 0;
        int counter = 0;
        byte[] blockIn = keyId.clone();
        while (produced < outLen) {
            // Set last 2 bytes as counter (big-endian). This matches common SRTP KDF practice.
            blockIn[14] = (byte) ((counter >> 8) & 0xFF);
            blockIn[15] = (byte) (counter & 0xFF);
            byte[] block = ecb.doFinal(blockIn);
            int take = Math.min(16, outLen - produced);
            System.arraycopy(block, 0, out, produced, take);
            produced += take;
            counter++;
        }
        return out;
    }

    private static long toUnsignedInt(int v) {
        return v & 0xFFFFFFFFL;
    }
}

