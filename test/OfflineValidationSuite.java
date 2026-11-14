package test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import sipinspector.Media.AudioFormatUtils;
import sipinspector.Media.G722Codec;
import sipinspector.Media.LiveAudioCodec;
import sipinspector.Media.SrtpContext;
import sipinspector.Media.ToneSynthesizer;
import sipinspector.Utils.SIPParser;
import sipinspector.Utils.ValuesLoader;

public class OfflineValidationSuite {

    private static int passed = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("=== Running Offline Validation Suite ===");

        test("G.711 A-law & u-law Codec Roundtrip", () -> {
            LiveAudioCodec pcma = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);
            LiveAudioCodec pcmu = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMU);
            byte[] pcm = new byte[320]; // 20ms @ 8kHz (160 samples, 16-bit = 320 bytes)
            for (int i = 0; i < pcm.length; i += 2) {
                short sample = (short) (Math.sin(i * 0.1) * 10000);
                pcm[i] = (byte) (sample & 0xFF);
                pcm[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            byte[] encPcma = pcma.encode(pcm, pcm.length);
            if (encPcma.length != 160) throw new Exception("PCMA encoded length should be 160 bytes, got " + encPcma.length);
            byte[] decPcma = pcma.decode(encPcma, encPcma.length);
            if (decPcma.length != 320) throw new Exception("PCMA decoded length should be 320 bytes, got " + decPcma.length);

            byte[] encPcmu = pcmu.encode(pcm, pcm.length);
            if (encPcmu.length != 160) throw new Exception("PCMU encoded length should be 160 bytes, got " + encPcmu.length);
            byte[] decPcmu = pcmu.decode(encPcmu, encPcmu.length);
            if (decPcmu.length != 320) throw new Exception("PCMU decoded length should be 320 bytes, got " + decPcmu.length);
        });

        test("G.722 Wideband Codec Roundtrip", () -> {
            LiveAudioCodec g722 = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_G722);
            byte[] pcm16k = new byte[640]; // 20ms @ 16kHz (320 samples, 16-bit = 640 bytes)
            for (int i = 0; i < pcm16k.length; i += 2) {
                short sample = (short) (Math.sin(i * 0.05) * 8000);
                pcm16k[i] = (byte) (sample & 0xFF);
                pcm16k[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            byte[] encG722 = g722.encode(pcm16k, pcm16k.length);
            if (encG722.length != 160) throw new Exception("G.722 encoded length should be 160 bytes, got " + encG722.length);
            byte[] decG722 = g722.decode(encG722, encG722.length);
            if (decG722.length != 640) throw new Exception("G.722 decoded length should be 640 bytes, got " + decG722.length);
        });

        test("SRTP Encryption and Authentication Roundtrip", () -> {
            byte[] masterKey = new byte[16];
            byte[] masterSalt = new byte[14];
            Arrays.fill(masterKey, (byte) 0x5A);
            Arrays.fill(masterSalt, (byte) 0x3C);
            SrtpContext senderSrtp = new SrtpContext(masterKey, masterSalt);
            SrtpContext receiverSrtp = new SrtpContext(masterKey, masterSalt);

            byte[] rtpPacket = new byte[12 + 160];
            rtpPacket[0] = (byte) 0x80;
            rtpPacket[1] = (byte) 8;
            rtpPacket[2] = 0x00; rtpPacket[3] = 0x01; // Seq = 1
            rtpPacket[4] = 0; rtpPacket[5] = 0; rtpPacket[6] = 0x03; rtpPacket[7] = 0x20; // TS = 800
            rtpPacket[8] = 0x12; rtpPacket[9] = 0x34; rtpPacket[10] = 0x56; rtpPacket[11] = 0x78; // SSRC = 0x12345678
            for (int i = 12; i < rtpPacket.length; i++) rtpPacket[i] = (byte) (i & 0xFF);

            byte[] protectedPacket = senderSrtp.protectRtp(rtpPacket, rtpPacket.length);
            if (protectedPacket == null || protectedPacket.length != rtpPacket.length + SrtpContext.AUTH_TAG_LEN) {
                throw new Exception("SRTP protect failed or tag length mismatch");
            }

            byte[] unprotectPacket = receiverSrtp.unprotectRtp(protectedPacket, protectedPacket.length);
            if (unprotectPacket == null || unprotectPacket.length != rtpPacket.length) {
                throw new Exception("SRTP unprotect failed or length mismatch");
            }

            for (int i = 0; i < rtpPacket.length; i++) {
                if (unprotectPacket[i] != rtpPacket[i]) {
                    throw new Exception("Decrypted SRTP byte at index " + i + " mismatch: expected " + rtpPacket[i] + " got " + unprotectPacket[i]);
                }
            }
        });

        test("SIPParser Re-INVITE vs Initial INVITE & To-tag Extraction", () -> {
            String initialInvite = "INVITE sip:70201@192.168.0.70 SIP/2.0\r\n" +
                                   "Via: SIP/2.0/UDP 192.168.0.10:5060;branch=z9hG4bK-init1\r\n" +
                                   "From: <sip:70200@192.168.0.10>;tag=tag123\r\n" +
                                   "To: <sip:70201@192.168.0.70>\r\n" +
                                   "Call-ID: call-abc-123\r\n" +
                                   "CSeq: 1 INVITE\r\n" +
                                   "Content-Length: 0\r\n\r\n";

            String reInvite = "INVITE sip:70201@192.168.0.70 SIP/2.0\r\n" +
                              "Via: SIP/2.0/UDP 192.168.0.10:5060;branch=z9hG4bK-reinv2\r\n" +
                              "From: <sip:70200@192.168.0.10>;tag=tag123\r\n" +
                              "To: <sip:70201@192.168.0.70>;tag=remote-tag-999\r\n" +
                              "Call-ID: call-abc-123\r\n" +
                              "CSeq: 2 INVITE\r\n" +
                              "Content-Length: 0\r\n\r\n";

            if (!SIPParser.isInitialInvite(initialInvite)) throw new Exception("Expected initialInvite to be true");
            if (SIPParser.isInitialInvite(reInvite)) throw new Exception("Expected reInvite isInitialInvite to be false");
            if (!SIPParser.hasToTag(reInvite)) throw new Exception("Expected reInvite to have To-tag");
            if (SIPParser.hasToTag(initialInvite)) throw new Exception("Expected initialInvite to NOT have To-tag");
            String toTag = SIPParser.getToTag(reInvite);
            if (!"remote-tag-999".equals(toTag)) throw new Exception("Expected To-tag 'remote-tag-999', got: " + toTag);
        });

        test("ToneSynthesizer Generation & File Verification", () -> {
            ToneSynthesizer.ensureGenerated();
            File dial = new File(ToneSynthesizer.DIAL_TONE);
            File busy = new File(ToneSynthesizer.BUSY_TONE);
            File reorder = new File(ToneSynthesizer.REORDER_TONE);
            File end = new File(ToneSynthesizer.END_TONE);

            if (!dial.exists() || dial.length() == 0) throw new Exception("Dial tone PCAP was not created");
            if (!busy.exists() || busy.length() == 0) throw new Exception("Busy tone PCAP was not created");
            if (!reorder.exists() || reorder.length() == 0) throw new Exception("Reorder tone PCAP was not created");
            if (!end.exists() || end.length() == 0) throw new Exception("End tone PCAP was not created");
        });

        test("ValuesLoader CSV and Semicolon Token Mapping", () -> {
            ValuesLoader loader = new ValuesLoader();
            File tempVal = new File(sipinspector.Utils.AppPaths.getValuesDir(), "temp_test_values_loader.txt");
            try (java.io.FileWriter fw = new java.io.FileWriter(tempVal)) {
                fw.write("// Test values\n");
                fw.write("70200;192.168.0.70;5060\n");
            }
            try {
                loader.loadValuesFromFile(tempVal.getPath());
                sipinspector.Utils.SIValue[] row = loader.getRowOfValues();
                if (row == null || row.length < 3) throw new Exception("ValuesLoader failed to load row of values");
                if (!"70200".equals(loader.getFieldValue(row, "[field0]"))) throw new Exception("field0 mismatch");
                if (!"192.168.0.70".equals(loader.getFieldValue(row, "[field1]"))) throw new Exception("field1 mismatch");
                if (!"5060".equals(loader.getFieldValue(row, "[field2]"))) throw new Exception("field2 mismatch");
            } finally {
                if (tempVal.exists()) tempVal.delete();
            }
        });

        System.out.println("=========================================");
        System.out.println("OFFLINE VALIDATION SUMMARY: " + passed + " / " + total + " PASSED");
        System.out.println("=========================================");
        if (passed != total) System.exit(1);
    }

    private static void test(String name, RunnableTest runnable) {
        total++;
        System.out.print("Testing: " + name + "... ");
        try {
            runnable.run();
            passed++;
            System.out.println("PASS");
        } catch (Throwable t) {
            System.out.println("FAIL");
            System.err.println("  Error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    interface RunnableTest {
        void run() throws Exception;
    }
}
