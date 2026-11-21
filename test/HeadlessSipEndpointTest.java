/*
 * SIP Simulator v4 - Phase 9 Validation
 * HeadlessSipEndpointTest: Validates native headless UAS / UAC without MicroSIP
 */
package test;

import sipinspector.Endpoint.HeadlessSipEndpoint;
import sipinspector.MyPacket;
import sipinspector.Socket.UDPClientSocket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeadlessSipEndpointTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: NATIVE HEADLESS SIP ENDPOINT (LIM-03) VALIDATION");
        System.out.println("================================================================================");

        testEndpointLifecycle();
        testAutoAnswerCallFlow();
        testDtmfReception();
        testOptionsHandling();

        System.out.println(">>> ALL HEADLESS SIP ENDPOINT TESTS PASSED!");
    }

    private static void testEndpointLifecycle() {
        System.out.print("[TEST 1/4] Endpoint Startup and Shutdown... ");
        HeadlessSipEndpoint ep = new HeadlessSipEndpoint("127.0.0.1", 5070, "90001");
        boolean started = ep.start();
        if (!started || !ep.isRunning()) {
            throw new RuntimeException("Failed to start HeadlessSipEndpoint on 127.0.0.1:5070");
        }
        if (ep.getState() != HeadlessSipEndpoint.EndpointState.LISTENING) {
            throw new RuntimeException("Expected LISTENING state, got: " + ep.getState());
        }
        ep.stop();
        if (ep.isRunning() || ep.getState() != HeadlessSipEndpoint.EndpointState.STOPPED) {
            throw new RuntimeException("Failed to stop HeadlessSipEndpoint cleanly");
        }
        System.out.println("PASSED");
    }

    private static void testAutoAnswerCallFlow() {
        System.out.print("[TEST 2/4] Auto-Answer Call Flow (INVITE -> 100 -> 180 -> 200 OK -> ACK -> BYE)... ");
        HeadlessSipEndpoint ep = new HeadlessSipEndpoint("127.0.0.1", 5072, "90001");
        ep.setAutoAnswer(true);
        ep.setRingDelayMs(50);
        ep.start();

        final CountDownLatch answeredLatch = new CountDownLatch(1);
        final CountDownLatch endedLatch = new CountDownLatch(1);

        ep.addListener(new HeadlessSipEndpoint.EndpointEventListener() {
            @Override
            public void onCallReceived(String callId, String caller, String sdp) {}

            @Override
            public void onCallAnswered(String callId, int localRtpPort) {
                answeredLatch.countDown();
            }

            @Override
            public void onCallEnded(String callId, String reason) {
                endedLatch.countDown();
            }

            @Override
            public void onDtmfReceived(String callId, char digit) {}

            @Override
            public void onRegistrationStatus(boolean successful, String message) {}
        });

        DatagramSocket client = null;
        try {
            client = new DatagramSocket(5074);
            client.setSoTimeout(3000);

            String callId = "test-call-123@127.0.0.1";
            String invite = "INVITE sip:90001@127.0.0.1:5072 SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP 127.0.0.1:5074;branch=z9hG4bKinv01\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:90008@127.0.0.1>;tag=caller01\r\n"
                    + "To: <sip:90001@127.0.0.1>\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: 1 INVITE\r\n"
                    + "Contact: <sip:90008@127.0.0.1:5074>\r\n"
                    + "Content-Type: application/sdp\r\n"
                    + "Content-Length: 120\r\n\r\n"
                    + "v=0\r\no=Tester 1 1 IN IP4 127.0.0.1\r\ns=Test\r\nc=IN IP4 127.0.0.1\r\nt=0 0\r\nm=audio 40000 RTP/AVP 8 101\r\n";

            byte[] invBytes = invite.getBytes(StandardCharsets.UTF_8);
            client.send(new DatagramPacket(invBytes, invBytes.length, InetAddress.getByName("127.0.0.1"), 5072));

            // Receive responses (100 Trying, 180 Ringing, 200 OK)
            boolean got100 = false;
            boolean got180 = false;
            boolean got200 = false;
            byte[] buf = new byte[4096];

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && (!got100 || !got180 || !got200)) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    client.receive(p);
                    String resp = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    if (resp.contains("SIP/2.0 100 Trying")) got100 = true;
                    if (resp.contains("SIP/2.0 180 Ringing")) got180 = true;
                    if (resp.contains("SIP/2.0 200 OK") && resp.contains("m=audio")) got200 = true;
                } catch (Exception ignored) {}
            }

            if (!got100) throw new RuntimeException("Did not receive 100 Trying");
            if (!got180) throw new RuntimeException("Did not receive 180 Ringing");
            if (!got200) throw new RuntimeException("Did not receive 200 OK with SDP");

            boolean answered = answeredLatch.await(2, TimeUnit.SECONDS);
            if (!answered) throw new RuntimeException("Callback onCallAnswered was not triggered");

            // Send ACK
            String ack = "ACK sip:90001@127.0.0.1:5072 SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP 127.0.0.1:5074;branch=z9hG4bKack01\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:90008@127.0.0.1>;tag=caller01\r\n"
                    + "To: <sip:90001@127.0.0.1>\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: 1 ACK\r\n"
                    + "Content-Length: 0\r\n\r\n";
            byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
            client.send(new DatagramPacket(ackBytes, ackBytes.length, InetAddress.getByName("127.0.0.1"), 5072));
            Thread.sleep(100);

            // Send BYE
            String bye = "BYE sip:90001@127.0.0.1:5072 SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP 127.0.0.1:5074;branch=z9hG4bKbye01\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:90008@127.0.0.1>;tag=caller01\r\n"
                    + "To: <sip:90001@127.0.0.1>\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: 2 BYE\r\n"
                    + "Content-Length: 0\r\n\r\n";
            byte[] byeBytes = bye.getBytes(StandardCharsets.UTF_8);
            client.send(new DatagramPacket(byeBytes, byeBytes.length, InetAddress.getByName("127.0.0.1"), 5072));

            // Wait for 200 OK to BYE
            DatagramPacket byeResp = new DatagramPacket(buf, buf.length);
            client.receive(byeResp);
            String byeRespStr = new String(byeResp.getData(), 0, byeResp.getLength(), StandardCharsets.UTF_8);
            if (!byeRespStr.contains("SIP/2.0 200 OK")) {
                throw new RuntimeException("Expected 200 OK to BYE, got:\n" + byeRespStr);
            }

            boolean ended = endedLatch.await(2, TimeUnit.SECONDS);
            if (!ended) throw new RuntimeException("Callback onCallEnded was not triggered");

            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("Auto-answer call flow test failed: " + e.getMessage(), e);
        } finally {
            if (client != null && !client.isClosed()) client.close();
            ep.stop();
        }
    }

    private static void testDtmfReception() {
        System.out.print("[TEST 3/4] In-Dialog DTMF Signal Processing (SIP INFO)... ");
        HeadlessSipEndpoint ep = new HeadlessSipEndpoint("127.0.0.1", 5076, "90001");
        ep.start();

        final CountDownLatch dtmfLatch = new CountDownLatch(1);
        final StringBuilder receivedChar = new StringBuilder();

        ep.addListener(new HeadlessSipEndpoint.EndpointEventListener() {
            @Override
            public void onCallReceived(String callId, String caller, String sdp) {}
            @Override
            public void onCallAnswered(String callId, int localRtpPort) {}
            @Override
            public void onCallEnded(String callId, String reason) {}
            @Override
            public void onDtmfReceived(String callId, char digit) {
                receivedChar.append(digit);
                dtmfLatch.countDown();
            }
            @Override
            public void onRegistrationStatus(boolean successful, String message) {}
        });

        DatagramSocket client = null;
        try {
            client = new DatagramSocket(5078);
            client.setSoTimeout(3000);

            String info = "INFO sip:90001@127.0.0.1:5076 SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP 127.0.0.1:5078;branch=z9hG4bKinfo01\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:90008@127.0.0.1>;tag=dtmf01\r\n"
                    + "To: <sip:90001@127.0.0.1>\r\n"
                    + "Call-ID: dtmf-test@127.0.0.1\r\n"
                    + "CSeq: 10 INFO\r\n"
                    + "Content-Type: application/dtmf-relay\r\n"
                    + "Content-Length: 22\r\n\r\n"
                    + "Signal=5\r\nDuration=160\r\n";

            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
            client.send(new DatagramPacket(infoBytes, infoBytes.length, InetAddress.getByName("127.0.0.1"), 5076));

            byte[] buf = new byte[2048];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            client.receive(resp);
            String respStr = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);
            if (!respStr.contains("SIP/2.0 200 OK")) {
                throw new RuntimeException("Expected 200 OK to INFO, got:\n" + respStr);
            }

            boolean gotDtmf = dtmfLatch.await(2, TimeUnit.SECONDS);
            if (!gotDtmf || !"5".equals(receivedChar.toString())) {
                throw new RuntimeException("Failed to process DTMF digit 5, received: '" + receivedChar + "'");
            }

            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("DTMF test failed: " + e.getMessage(), e);
        } finally {
            if (client != null && !client.isClosed()) client.close();
            ep.stop();
        }
    }

    private static void testOptionsHandling() {
        System.out.print("[TEST 4/4] RFC 3261 OPTIONS Healthcheck & Capabilities... ");
        HeadlessSipEndpoint ep = new HeadlessSipEndpoint("127.0.0.1", 5080, "90001");
        ep.start();

        DatagramSocket client = null;
        try {
            client = new DatagramSocket(5082);
            client.setSoTimeout(3000);

            String options = "OPTIONS sip:90001@127.0.0.1:5080 SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP 127.0.0.1:5082;branch=z9hG4bKopt01\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:healthcheck@127.0.0.1>;tag=opt01\r\n"
                    + "To: <sip:90001@127.0.0.1>\r\n"
                    + "Call-ID: opt-test@127.0.0.1\r\n"
                    + "CSeq: 1 OPTIONS\r\n"
                    + "Content-Length: 0\r\n\r\n";

            byte[] optBytes = options.getBytes(StandardCharsets.UTF_8);
            client.send(new DatagramPacket(optBytes, optBytes.length, InetAddress.getByName("127.0.0.1"), 5080));

            byte[] buf = new byte[2048];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            client.receive(resp);
            String respStr = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8);

            if (!respStr.contains("SIP/2.0 200 OK") || !respStr.contains("Allow:") || !respStr.contains("INVITE")) {
                throw new RuntimeException("Invalid OPTIONS response:\n" + respStr);
            }

            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("OPTIONS test failed: " + e.getMessage(), e);
        } finally {
            if (client != null && !client.isClosed()) client.close();
            ep.stop();
        }
    }
}
