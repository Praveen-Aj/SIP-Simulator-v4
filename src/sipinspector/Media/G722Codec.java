/*
 *  SIP Inspector - Live Audio Support
 *
 *  G.722 (64 kbit/s) sub-band ADPCM codec.
 *
 *  This is a Java port of the ITU-T G.722 reference algorithm (the same
 *  arithmetic used by the widely-known public-domain implementations). G.722
 *  samples audio at 16 kHz; two input samples are encoded into one output byte,
 *  so a 20 ms frame = 320 input samples (16 kHz) = 160 encoded bytes on the
 *  wire. Note the RTP clock rate for G.722 is 8000 by historical convention
 *  even though the audio is 16 kHz.
 *
 *  A separate instance must be used for each direction (encode vs decode) since
 *  each carries its own adaptive predictor / QMF state.
 */
package sipinspector.Media;

public class G722Codec {

    // ---- Static tables (ITU-T G.722) ----
    private static final int[] WL   = {-60, -30, 58, 172, 334, 538, 1198, 3042};
    private static final int[] RL42 = {0, 7, 6, 5, 4, 3, 2, 1, 7, 6, 5, 4, 3, 2, 1, 0};
    private static final int[] ILB  = {
        2048, 2093, 2139, 2186, 2233, 2282, 2332, 2383,
        2435, 2489, 2543, 2599, 2656, 2714, 2774, 2834,
        2896, 2960, 3025, 3091, 3158, 3228, 3298, 3371,
        3444, 3520, 3597, 3676, 3756, 3838, 3922, 4008
    };
    private static final int[] WH  = {0, -214, 798};
    private static final int[] RH2 = {2, 1, 2, 1};
    private static final int[] QM2 = {-7408, -1616, 7408, 1616};
    private static final int[] QM4 = {
        0, -20456, -12896, -8968, -6288, -4240, -2584, -1200,
        20456, 12896, 8968, 6288, 4240, 2584, 1200, 0
    };
    private static final int[] QM5 = {
        -280, -280, -23352, -17560, -14120, -11664, -9752, -8184,
        -6864, -5712, -4696, -3784, -2960, -2208, -1520, -880,
        23352, 17560, 14120, 11664, 9752, 8184, 6864, 5712,
        4696, 3784, 2960, 2208, 1520, 880, 280, -280
    };
    private static final int[] QM6 = {
        -136, -136, -136, -136, -24808, -21904, -19008, -16704,
        -14984, -13512, -12280, -11192, -10232, -9360, -8576, -7856,
        -7192, -6576, -6000, -5456, -4944, -4464, -4008, -3576,
        -3168, -2776, -2400, -2032, -1688, -1360, -1040, -728,
        24808, 21904, 19008, 16704, 14984, 13512, 12280, 11192,
        10232, 9360, 8576, 7856, 7192, 6576, 6000, 5456,
        4944, 4464, 4008, 3576, 3168, 2776, 2400, 2032,
        1688, 1360, 1040, 728, 432, 136, -432, -136
    };
    private static final int[] Q6 = {
        0, 35, 72, 110, 150, 190, 233, 276,
        323, 370, 422, 473, 530, 587, 650, 714,
        786, 858, 940, 1023, 1121, 1219, 1339, 1458,
        1612, 1765, 1980, 2195, 2557, 2919, 0
    };
    private static final int[] ILN = {
        0, 63, 62, 31, 30, 29, 28, 27,
        26, 25, 24, 23, 22, 21, 20, 19,
        18, 17, 16, 15, 14, 13, 12, 11,
        10, 9, 8, 7, 6, 5, 4, 0
    };
    private static final int[] ILP = {
        0, 61, 60, 59, 58, 57, 56, 55,
        54, 53, 52, 51, 50, 49, 48, 47,
        46, 45, 44, 43, 42, 41, 40, 39,
        38, 37, 36, 35, 34, 33, 32, 0
    };
    private static final int[] IHN = {0, 1, 0};
    private static final int[] IHP = {0, 3, 2};

    // QMF filter coefficients (12 taps).
    private static final int[] QMF_COEFFS = {
        3, -11, 12, 32, -210, 951, 3876, -805, 362, -156, 53, -11
    };

    // ---- Per-band adaptive state ----
    private static final class Band {
        int s;
        int sp;
        int sz;
        int[] r  = new int[3];
        int[] a  = new int[3];
        int[] ap = new int[3];
        int[] p  = new int[3];
        int[] d  = new int[7];
        int[] b  = new int[7];
        int[] bp = new int[7];
        int[] sg = new int[7];
        int nb;
        int det = 32;
    }

    private final Band[] band = { new Band(), new Band() };
    private final int[] x = new int[24]; // QMF history

    private static int saturate(int amp) {
        if (amp > 32767) return 32767;
        if (amp < -32768) return -32768;
        return amp;
    }

    private void block4(int b, int d) {
        Band bs = band[b];
        int wd1, wd2, wd3, i;

        bs.d[0] = d;
        bs.r[0] = saturate(bs.s + d);
        bs.p[0] = saturate(bs.sz + d);

        // UPPOL2
        for (i = 0; i < 3; i++) bs.sg[i] = bs.p[i] >> 15;
        wd1 = saturate(bs.a[1] << 2);
        wd2 = (bs.sg[0] == bs.sg[1]) ? -wd1 : wd1;
        if (wd2 > 32767) wd2 = 32767;
        wd3 = ((bs.sg[0] == bs.sg[2]) ? 128 : -128) + (wd2 >> 7) + ((bs.a[2] * 32512) >> 15);
        if (wd3 > 12288) wd3 = 12288; else if (wd3 < -12288) wd3 = -12288;
        bs.ap[2] = wd3;

        // UPPOL1
        bs.sg[0] = bs.p[0] >> 15;
        bs.sg[1] = bs.p[1] >> 15;
        wd1 = (bs.sg[0] == bs.sg[1]) ? 192 : -192;
        wd2 = (bs.a[1] * 32640) >> 15;
        bs.ap[1] = saturate(wd1 + wd2);
        wd3 = saturate(15360 - bs.ap[2]);
        if (bs.ap[1] > wd3) bs.ap[1] = wd3; else if (bs.ap[1] < -wd3) bs.ap[1] = -wd3;

        // UPZERO
        wd1 = (d == 0) ? 0 : 128;
        bs.sg[0] = d >> 15;
        for (i = 1; i < 7; i++) {
            bs.sg[i] = bs.d[i] >> 15;
            wd2 = (bs.sg[i] == bs.sg[0]) ? wd1 : -wd1;
            wd3 = (bs.b[i] * 32640) >> 15;
            bs.bp[i] = saturate(wd2 + wd3);
        }

        // DELAYA
        for (i = 6; i > 0; i--) {
            bs.d[i] = bs.d[i - 1];
            bs.b[i] = bs.bp[i];
        }
        for (i = 2; i > 0; i--) {
            bs.r[i] = bs.r[i - 1];
            bs.p[i] = bs.p[i - 1];
            bs.a[i] = bs.ap[i];
        }

        // FILTEP
        wd1 = saturate(bs.r[1] + bs.r[1]);
        wd1 = (bs.a[1] * wd1) >> 15;
        wd2 = saturate(bs.r[2] + bs.r[2]);
        wd2 = (bs.a[2] * wd2) >> 15;
        bs.sp = saturate(wd1 + wd2);

        // FILTEZ
        bs.sz = 0;
        for (i = 6; i > 0; i--) {
            wd1 = saturate(bs.d[i] + bs.d[i]);
            bs.sz += (bs.b[i] * wd1) >> 15;
        }
        bs.sz = saturate(bs.sz);

        // PREDIC
        bs.s = saturate(bs.sp + bs.sz);
    }

    /**
     * Encode 16 kHz PCM16 (big-endian bytes) into G.722. Input length must be a
     * multiple of 4 bytes (2 samples per output byte).
     *
     * @param pcmBE big-endian PCM16 samples at 16 kHz
     * @param len   number of valid bytes in pcmBE
     * @return encoded G.722 bytes (len/4)
     */
    public byte[] encode(byte[] pcmBE, int len) {
        int samples = len / 2;
        byte[] out = new byte[samples / 2];
        int outIdx = 0;

        for (int j = 0; j + 1 < samples; j += 2) {
            int s0 = (short) (((pcmBE[2 * j] & 0xFF) << 8) | (pcmBE[2 * j + 1] & 0xFF));
            int s1 = (short) (((pcmBE[2 * (j + 1)] & 0xFF) << 8) | (pcmBE[2 * (j + 1) + 1] & 0xFF));

            // Transmit QMF: shuffle buffer down by 2 and insert the two samples.
            for (int i = 0; i < 22; i++) x[i] = x[i + 2];
            x[22] = s0;
            x[23] = s1;
            int sumodd = 0, sumeven = 0;
            for (int i = 0; i < 12; i++) {
                sumodd  += x[2 * i]     * QMF_COEFFS[i];
                sumeven += x[2 * i + 1] * QMF_COEFFS[11 - i];
            }
            int xlow  = (sumeven + sumodd) >> 14;
            int xhigh = (sumeven - sumodd) >> 14;
            if (xlow > 32767) xlow = 32767; else if (xlow < -32768) xlow = -32768;
            if (xhigh > 32767) xhigh = 32767; else if (xhigh < -32768) xhigh = -32768;

            // ---- Low band ADPCM (6-bit) ----
            int el = saturate(xlow - band[0].s);
            int wd = (el >= 0) ? el : -(el + 1);
            int i;
            for (i = 1; i < 30; i++) {
                int wd1 = (Q6[i] * band[0].det) >> 12;
                if (wd < wd1) break;
            }
            int ilow = (el < 0) ? ILN[i] : ILP[i];

            int ril = ilow >> 2;
            int dlow = (band[0].det * QM4[ril]) >> 15;
            block4(0, dlow);

            // Low-band scale factor update
            int il4 = RL42[ril];
            wd = (band[0].nb * 127) >> 7;
            band[0].nb = wd + WL[il4];
            if (band[0].nb < 0) band[0].nb = 0; else if (band[0].nb > 18432) band[0].nb = 18432;
            int wd1 = (band[0].nb >> 6) & 31;
            int wd2 = 8 - (band[0].nb >> 11);
            int wd3 = (wd2 < 0) ? (ILB[wd1] << -wd2) : (ILB[wd1] >> wd2);
            band[0].det = wd3 << 2;

            // ---- High band ADPCM (2-bit) ----
            int eh = saturate(xhigh - band[1].s);
            int whd = (eh >= 0) ? eh : -(eh + 1);
            int wd11 = (564 * band[1].det) >> 12;
            int mih = (whd >= wd11) ? 2 : 1;
            int ihigh = (eh < 0) ? IHN[mih] : IHP[mih];

            int dhigh = (band[1].det * QM2[ihigh]) >> 15;
            block4(1, dhigh);

            int ih2 = RH2[ihigh];
            wd = (band[1].nb * 127) >> 7;
            band[1].nb = wd + WH[ih2];
            if (band[1].nb < 0) band[1].nb = 0; else if (band[1].nb > 22528) band[1].nb = 22528;
            wd1 = (band[1].nb >> 6) & 31;
            wd2 = 10 - (band[1].nb >> 11);
            wd3 = (wd2 < 0) ? (ILB[wd1] << -wd2) : (ILB[wd1] >> wd2);
            band[1].det = wd3 << 2;

            out[outIdx++] = (byte) (((ihigh << 6) | ilow) & 0xFF);
        }
        return out;
    }

    /**
     * Decode G.722 into 16 kHz PCM16 (big-endian bytes). Each input byte yields
     * two output samples (4 bytes).
     *
     * @param g722 encoded bytes
     * @param len  number of valid bytes in g722
     * @return big-endian PCM16 at 16 kHz (len*4 bytes)
     */
    public byte[] decode(byte[] g722, int len) {
        byte[] out = new byte[len * 4];
        int outIdx = 0;

        for (int j = 0; j < len; j++) {
            int code = g722[j] & 0xFF;
            int ilow = code & 0x3F;
            int ihigh = (code >> 6) & 0x03;

            // ---- Low band ----
            int wd2 = QM6[ilow];
            wd2 = (band[0].det * wd2) >> 15;
            int rlow = band[0].s + wd2;
            if (rlow > 16383) rlow = 16383; else if (rlow < -16384) rlow = -16384;

            int ril = ilow >> 2;
            int dlowt = (band[0].det * QM4[ril]) >> 15;
            block4(0, dlowt);

            int il4 = RL42[ril];
            int wd = (band[0].nb * 127) >> 7;
            band[0].nb = wd + WL[il4];
            if (band[0].nb < 0) band[0].nb = 0; else if (band[0].nb > 18432) band[0].nb = 18432;
            int wd1 = (band[0].nb >> 6) & 31;
            wd2 = 8 - (band[0].nb >> 11);
            int wd3 = (wd2 < 0) ? (ILB[wd1] << -wd2) : (ILB[wd1] >> wd2);
            band[0].det = wd3 << 2;

            // ---- High band ----
            wd2 = QM2[ihigh];
            wd2 = (band[1].det * wd2) >> 15;
            int rhigh = wd2 + band[1].s;
            if (rhigh > 16383) rhigh = 16383; else if (rhigh < -16384) rhigh = -16384;

            block4(1, wd2);

            int ih2 = RH2[ihigh];
            wd = (band[1].nb * 127) >> 7;
            band[1].nb = wd + WH[ih2];
            if (band[1].nb < 0) band[1].nb = 0; else if (band[1].nb > 22528) band[1].nb = 22528;
            wd1 = (band[1].nb >> 6) & 31;
            wd2 = 10 - (band[1].nb >> 11);
            wd3 = (wd2 < 0) ? (ILB[wd1] << -wd2) : (ILB[wd1] >> wd2);
            band[1].det = wd3 << 2;

            // ---- Receive QMF: reconstruct two 16 kHz samples ----
            for (int i = 0; i < 22; i++) x[i] = x[i + 2];
            x[22] = rlow + rhigh;
            x[23] = rlow - rhigh;

            int sumodd = 0, sumeven = 0;
            for (int i = 0; i < 12; i++) {
                sumodd  += x[2 * i]     * QMF_COEFFS[i];
                sumeven += x[2 * i + 1] * QMF_COEFFS[11 - i];
            }
            int out0 = saturate(sumeven >> 11);
            int out1 = saturate(sumodd >> 11);

            out[outIdx++] = (byte) (out0 >> 8);
            out[outIdx++] = (byte) out0;
            out[outIdx++] = (byte) (out1 >> 8);
            out[outIdx++] = (byte) out1;
        }
        return out;
    }
}
