package sipinspector.Media;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Map;
import sipinspector.GlobalSettings;
import sipinspector.Utils.TLSDebugLogger;

/**
 * Runtime soak validation utility for Live Audio lifecycle and RTP/SRTP paths.
 */
public class LiveAudioSoakValidationMain {

    public static void main(String[] args) throws Exception {
        TLSDebugLogger.enable("UDP");
        System.setProperty("liveaudio.validation", "true");
        new GlobalSettings();

        int cycles = 20;
        int mediaPort = 41000;
        if (args != null && args.length > 0) {
            try {
                cycles = Integer.parseInt(args[0]);
            } catch (Exception ignore) {}
        }
        if (args != null && args.length > 1) {
            try {
                mediaPort = Integer.parseInt(args[1]);
            } catch (Exception ignore) {}
        }
        int startFailures = 0;
        int runtimeExceptions = 0;

        configureLoopback(false, mediaPort);
        for (int i = 1; i <= cycles; i++) {
            LiveAudioController c = new LiveAudioController();
            boolean started = c.startAudio();
            if (!started) {
                startFailures++;
                System.out.println("Cycle " + i + " failed to start: " + c.getLastError());
                c.stopAudio();
                continue;
            }
            // Alternate between rapid and active-stream stop.
            if ((i % 2) == 0) {
                Thread.sleep(100);
            } else {
                Thread.sleep(800);
            }
            try {
                c.stopAudio();
            } catch (Exception ex) {
                runtimeExceptions++;
            }
            Thread.sleep(120);
        }

        // SRTP cycle run.
        configureLoopback(true, mediaPort);
        LiveAudioController srtpController = new LiveAudioController();
        boolean srtpStarted = srtpController.startAudio();
        Map<String, Object> srtpStatsBeforeStop = null;
        if (srtpStarted) {
            Thread.sleep(300);
            injectSrtpNegativePackets();
            Thread.sleep(1500);
            srtpStatsBeforeStop = srtpController.getStats();
            srtpController.stopAudio();
        }

        int liveAudioThreads = countLiveAudioThreads();
        Map<String, Object> finalStats = srtpController.getStats();

        System.out.println("=== LiveAudio Soak Validation Summary ===");
        System.out.println("cycles=" + cycles);
        System.out.println("start_failures=" + startFailures);
        System.out.println("runtime_exceptions=" + runtimeExceptions);
        System.out.println("srtp_started=" + srtpStarted);
        System.out.println("remaining_liveaudio_threads=" + liveAudioThreads);
        System.out.println("diag_snapshot=" + LiveAudioRuntimeDiagnostics.snapshotString());
        System.out.println("srtp_stats_before_stop=" + srtpStatsBeforeStop);
        System.out.println("final_stats=" + finalStats);
        System.out.println(
            "RESULT cycles=" + cycles
            + " start_failures=" + startFailures
            + " runtime_exceptions=" + runtimeExceptions
            + " srtp_started=" + srtpStarted
            + " remaining_liveaudio_threads=" + liveAudioThreads
            + " diag_open_sockets=" + LiveAudioRuntimeDiagnostics.getOpenSockets()
            + " diag_active_senders=" + LiveAudioRuntimeDiagnostics.getActiveSenders()
            + " diag_active_receivers=" + LiveAudioRuntimeDiagnostics.getActiveReceivers()
            + " diag_open_mic_lines=" + LiveAudioRuntimeDiagnostics.getOpenMicLines()
            + " diag_open_speaker_lines=" + LiveAudioRuntimeDiagnostics.getOpenSpeakerLines()
        );

        TLSDebugLogger.disable();
    }

    private static void configureLoopback(boolean savp, int mediaPort) {
        GlobalSettings.setLocalIP("127.0.0.1");
        GlobalSettings.setRemoteIPAddr("127.0.0.1");
        GlobalSettings.setMediaPort(String.valueOf(mediaPort));
        GlobalSettings.setRemotePort(String.valueOf(mediaPort));
        GlobalSettings.setSetting("RTP_Remote_Address", "127.0.0.1");
        GlobalSettings.setSetting("RTP_Remote_Port", String.valueOf(mediaPort));
        GlobalSettings.setSdpUseSavp(savp);
        if (savp) {
            byte[] keySalt = new byte[30];
            for (int i = 0; i < keySalt.length; i++) {
                keySalt[i] = (byte)(i + 1);
            }
            String inline = Base64.getEncoder().encodeToString(keySalt);
            GlobalSettings.setSrtpCryptoLine("a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:" + inline);
        } else {
            GlobalSettings.setSrtpCryptoLine(null);
        }
    }

    private static int countLiveAudioThreads() {
        int count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String n = t.getName();
            if (n != null && (n.startsWith("LiveAudioSender-") || n.startsWith("LiveAudioReceiver-"))) {
                if (t.isAlive()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void injectSrtpNegativePackets() {
        try (DatagramSocket sock = new DatagramSocket()) {
            InetAddress ip = InetAddress.getByName("127.0.0.1");
            int port = Integer.parseInt(GlobalSettings.getMediaPort());
            String cryptoLine = GlobalSettings.getSrtpCryptoLine();
            SrtpContext ctx = SrtpContext.fromSdesCryptoLine(cryptoLine);
            byte[] payload = new byte[160];
            byte[] rtp = buildRtp(1000, 32000, 0x11112222, payload);
            byte[] goodSrtp = ctx.protectRtp(rtp, rtp.length);

            byte[] badAuth = goodSrtp.clone();
            badAuth[badAuth.length - 1] ^= 0x01;
            sock.send(new DatagramPacket(badAuth, badAuth.length, ip, port));

            byte[] truncated = new byte[8];
            System.arraycopy(goodSrtp, 0, truncated, 0, 8);
            sock.send(new DatagramPacket(truncated, truncated.length, ip, port));

            byte[] badVersion = goodSrtp.clone();
            badVersion[0] = 0x00;
            sock.send(new DatagramPacket(badVersion, badVersion.length, ip, port));
        } catch (Exception e) {
            System.out.println("inject_srtp_negative_packets_failed=" + e.getMessage());
        }
    }

    private static byte[] buildRtp(int seq, int ts, int ssrc, byte[] payload) {
        byte[] r = new byte[12 + payload.length];
        r[0] = (byte)0x80;
        r[1] = (byte)8;
        r[2] = (byte)((seq >> 8) & 0xFF);
        r[3] = (byte)(seq & 0xFF);
        r[4] = (byte)((ts >> 24) & 0xFF);
        r[5] = (byte)((ts >> 16) & 0xFF);
        r[6] = (byte)((ts >> 8) & 0xFF);
        r[7] = (byte)(ts & 0xFF);
        r[8] = (byte)((ssrc >> 24) & 0xFF);
        r[9] = (byte)((ssrc >> 16) & 0xFF);
        r[10] = (byte)((ssrc >> 8) & 0xFF);
        r[11] = (byte)(ssrc & 0xFF);
        System.arraycopy(payload, 0, r, 12, payload.length);
        return r;
    }
}
