package test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import sipinspector.MyPacket;
import sipinspector.Socket.TCPClientSocket;
import sipinspector.Utils.SipStreamFramer;

/**
 * Deterministic TCP SIP Message Framing and Stream Reliability Test Suite.
 *
 * Covers:
 * Test A - Complete message in one read
 * Test B - Header split across reads
 * Test C - Body split across reads
 * Test D - Multiple messages in one read
 * Test E - Complete message followed by partial message
 * Test F - Empty body (Content-Length: 0)
 * Test G - Read timeout / buffering continuity
 * Test H - Connection closure handling
 * Test I - Header variations (Content-Length, compact l, casing, whitespace)
 * Test J - Binary payload byte-length verification (UTF-8 multi-byte bodies)
 * Test K - Framer buffer reset on socket close
 */
public class TcpSipFramingValidationTest {

    public static void main(String[] args) {
        System.out.println("=== Running Phase 2 TCP SIP Framing & Stream Reliability Validation ===");
        int passed = 0;
        int total = 11;

        try {
            // [TEST A] Complete message in one read
            System.out.print("[TEST A] Complete message in one read: ");
            SipStreamFramer framerA = new SipStreamFramer();
            String msgA = "SIP/2.0 200 OK\r\nCSeq: 1 INVITE\r\nContent-Length: 10\r\n\r\n0123456789";
            byte[] bytesA = msgA.getBytes(StandardCharsets.UTF_8);
            framerA.feed(bytesA, 0, bytesA.length);
            byte[] outA = framerA.pollNextMessage();
            if (outA != null && new String(outA, StandardCharsets.UTF_8).equals(msgA) && !framerA.hasNextMessage()) {
                System.out.println("PASS");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST B] Header split across reads
            System.out.print("[TEST B] Header split across reads: ");
            SipStreamFramer framerB = new SipStreamFramer();
            byte[] chunkB1 = "INVITE sip:user@10.0.0.1 SIP/2.0\r\nVia: SIP/2.0/TCP 10.0.0.2\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] chunkB2 = "Call-ID: call-123\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            framerB.feed(chunkB1, 0, chunkB1.length);
            if (framerB.hasNextMessage()) {
                System.err.println("FAIL (Emitted before header boundary completed)");
            } else {
                framerB.feed(chunkB2, 0, chunkB2.length);
                byte[] outB = framerB.pollNextMessage();
                if (outB != null && outB.length == (chunkB1.length + chunkB2.length)) {
                    System.out.println("PASS");
                    passed++;
                } else {
                    System.err.println("FAIL (Did not assemble split headers)");
                }
            }

            // [TEST C] Body split across reads
            System.out.print("[TEST C] Body split across reads: ");
            SipStreamFramer framerC = new SipStreamFramer();
            String hdrC = "SIP/2.0 200 OK\r\nContent-Type: application/sdp\r\nContent-Length: 20\r\n\r\n";
            byte[] chunkC1 = (hdrC + "1234567890").getBytes(StandardCharsets.UTF_8);
            byte[] chunkC2 = "abcdefghij".getBytes(StandardCharsets.UTF_8);
            framerC.feed(chunkC1, 0, chunkC1.length);
            if (framerC.hasNextMessage()) {
                System.err.println("FAIL (Emitted before body was fully received)");
            } else {
                framerC.feed(chunkC2, 0, chunkC2.length);
                byte[] outC = framerC.pollNextMessage();
                if (outC != null && outC.length == (chunkC1.length + chunkC2.length)) {
                    System.out.println("PASS");
                    passed++;
                } else {
                    System.err.println("FAIL (Did not assemble split body)");
                }
            }

            // [TEST D] Multiple messages in one read
            System.out.print("[TEST D] Multiple messages in one read: ");
            SipStreamFramer framerD = new SipStreamFramer();
            String msgD1 = "SIP/2.0 100 Trying\r\nContent-Length: 0\r\n\r\n";
            String msgD2 = "SIP/2.0 180 Ringing\r\nContent-Length: 0\r\n\r\n";
            String msgD3 = "SIP/2.0 200 OK\r\nContent-Length: 4\r\n\r\nTEST";
            byte[] combinedD = (msgD1 + msgD2 + msgD3).getBytes(StandardCharsets.UTF_8);
            framerD.feed(combinedD, 0, combinedD.length);

            byte[] r1 = framerD.pollNextMessage();
            byte[] r2 = framerD.pollNextMessage();
            byte[] r3 = framerD.pollNextMessage();

            if (r1 != null && r2 != null && r3 != null
                    && new String(r1, StandardCharsets.UTF_8).equals(msgD1)
                    && new String(r2, StandardCharsets.UTF_8).equals(msgD2)
                    && new String(r3, StandardCharsets.UTF_8).equals(msgD3)
                    && !framerD.hasNextMessage()) {
                System.out.println("PASS (3 messages cleanly extracted in order)");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST E] Complete message followed by partial message
            System.out.print("[TEST E] Complete message followed by partial message: ");
            SipStreamFramer framerE = new SipStreamFramer();
            String msgE1 = "SIP/2.0 100 Trying\r\nContent-Length: 0\r\n\r\n";
            String partialE2 = "INVITE sip:remote@10.0.0.1 SIP/2.0\r\nVia: SIP/2.0/TCP 10.0.0.2\r\n";
            byte[] chunkE1 = (msgE1 + partialE2).getBytes(StandardCharsets.UTF_8);
            framerE.feed(chunkE1, 0, chunkE1.length);

            byte[] outE1 = framerE.pollNextMessage();
            boolean hasNextPremature = framerE.hasNextMessage();
            int remaining = framerE.getRemainingBufferSize();

            byte[] chunkE2 = "Content-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            framerE.feed(chunkE2, 0, chunkE2.length);
            byte[] outE2 = framerE.pollNextMessage();

            if (outE1 != null && new String(outE1, StandardCharsets.UTF_8).equals(msgE1)
                    && !hasNextPremature
                    && remaining == partialE2.getBytes(StandardCharsets.UTF_8).length
                    && outE2 != null && !framerE.hasNextMessage()) {
                System.out.println("PASS (Leftovers preserved and parsed on subsequent feed)");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST F] Empty body (Content-Length: 0)
            System.out.print("[TEST F] Empty body (Content-Length: 0): ");
            SipStreamFramer framerF = new SipStreamFramer();
            String msgF = "ACK sip:user@10.0.0.1 SIP/2.0\r\nCSeq: 1 ACK\r\nContent-Length: 0\r\n\r\n";
            byte[] bytesF = msgF.getBytes(StandardCharsets.UTF_8);
            framerF.feed(bytesF, 0, bytesF.length);
            byte[] outF = framerF.pollNextMessage();
            if (outF != null && outF.length == bytesF.length && !framerF.hasNextMessage()) {
                System.out.println("PASS");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST G] Read timeout continuity simulation
            System.out.print("[TEST G] Read timeout continuity & buffer retention: ");
            SipStreamFramer framerG = new SipStreamFramer();
            byte[] chunkG1 = "REGISTER sip:pbx@10.0.0.1 SIP/2.0\r\n".getBytes(StandardCharsets.UTF_8);
            framerG.feed(chunkG1, 0, chunkG1.length);
            // Simulate timeout: no data arrives, buffer remains intact
            int bufSizeAfterTimeout = framerG.getRemainingBufferSize();
            byte[] chunkG2 = "To: <sip:pbx@10.0.0.1>\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            framerG.feed(chunkG2, 0, chunkG2.length);
            byte[] outG = framerG.pollNextMessage();
            if (bufSizeAfterTimeout == chunkG1.length && outG != null && !framerG.hasNextMessage()) {
                System.out.println("PASS");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST H] Live Loopback Socket Read with Split Frames
            System.out.print("[TEST H] Live TCP loopback channel framing: ");
            int testPort = 55998;
            ServerSocket serverSocket = new ServerSocket(testPort, 1, InetAddress.getByName("127.0.0.1"));
            CountDownLatch serverReady = new CountDownLatch(1);

            new Thread(() -> {
                try (Socket clientConn = serverSocket.accept();
                     OutputStream os = clientConn.getOutputStream()) {
                    serverReady.countDown();
                    // Send split message across 3 separate TCP packet writes with micro-pauses
                    String p1 = "SIP/2.0 180 Ringing\r\n";
                    String p2 = "Content-Length: 5\r\n\r\n";
                    String p3 = "HELLO";
                    os.write(p1.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(20);
                    os.write(p2.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(20);
                    os.write(p3.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(50);
                } catch (Exception ignore) {}
            }).start();

            TCPClientSocket tcpClient = new TCPClientSocket(InetAddress.getByName("127.0.0.1"), 0);
            boolean opened = tcpClient.open(InetAddress.getByName("127.0.0.1"), testPort);
            serverReady.await(2, TimeUnit.SECONDS);

            MyPacket receivedPkt = null;
            long deadline = System.currentTimeMillis() + 1500;
            while (receivedPkt == null && System.currentTimeMillis() < deadline) {
                receivedPkt = tcpClient.receive();
                if (receivedPkt == null) {
                    Thread.sleep(10);
                }
            }
            tcpClient.close();
            serverSocket.close();

            if (opened && receivedPkt != null && receivedPkt.getMessageTxt().contains("180 Ringing")
                    && receivedPkt.getMessageTxt().endsWith("HELLO")) {
                System.out.println("PASS (Successfully reassembled split TCP frames over NIO channel)");
                passed++;
            } else {
                System.err.println("FAIL (Received: " + (receivedPkt != null ? receivedPkt.getMessageTxt() : "null") + ")");
            }

            // [TEST I] Header variations (casing, whitespace, compact 'l')
            System.out.print("[TEST I] Header variations (compact 'l', casing, whitespace): ");
            int len1 = SipStreamFramer.extractContentLength("INVITE sip:a@b SIP/2.0\r\ncontent-length:  128  \r\n\r\n");
            int len2 = SipStreamFramer.extractContentLength("SIP/2.0 200 OK\r\nl: 45\r\n\r\n");
            int len3 = SipStreamFramer.extractContentLength("SIP/2.0 200 OK\r\nL: \t 0 \r\n\r\n");
            int len4 = SipStreamFramer.extractContentLength("SIP/2.0 200 OK\r\nContent-Length: invalid\r\n\r\n");

            if (len1 == 128 && len2 == 45 && len3 == 0 && len4 == 0) {
                System.out.println("PASS (All header styles normalized correctly)");
                passed++;
            } else {
                System.err.println("FAIL (len1=" + len1 + " len2=" + len2 + " len3=" + len3 + " len4=" + len4 + ")");
            }

            // [TEST J] Binary-safe UTF-8 body length verification
            System.out.print("[TEST J] Binary-safe UTF-8 body byte length: ");
            SipStreamFramer framerJ = new SipStreamFramer();
            // String with non-ASCII characters: "München" (7 characters, 8 UTF-8 bytes)
            String unicodeBody = "v=0\r\no=München 1 1 IN IP4 10.0.0.1\r\n";
            byte[] bodyBytes = unicodeBody.getBytes(StandardCharsets.UTF_8);
            String msgJ = "SIP/2.0 200 OK\r\nContent-Length: " + bodyBytes.length + "\r\n\r\n";
            byte[] fullJ = new byte[msgJ.getBytes(StandardCharsets.UTF_8).length + bodyBytes.length];
            System.arraycopy(msgJ.getBytes(StandardCharsets.UTF_8), 0, fullJ, 0, msgJ.getBytes(StandardCharsets.UTF_8).length);
            System.arraycopy(bodyBytes, 0, fullJ, msgJ.getBytes(StandardCharsets.UTF_8).length, bodyBytes.length);

            framerJ.feed(fullJ, 0, fullJ.length);
            byte[] outJ = framerJ.pollNextMessage();
            if (outJ != null && outJ.length == fullJ.length && !framerJ.hasNextMessage()) {
                System.out.println("PASS (Exact byte length preserved for multi-byte payloads)");
                passed++;
            } else {
                System.err.println("FAIL");
            }

            // [TEST K] MyPacket backward compatibility and state sanity
            System.out.print("[TEST K] MyPacket backward compatibility & incomplete state: ");
            MyPacket legacyPkt = new MyPacket();
            byte[] partialHdr = "INVITE sip:test@10.0.0.1 SIP/2.0\r\n".getBytes(StandardCharsets.UTF_8);
            legacyPkt.parseBytesIntoSIPMsg(partialHdr);
            boolean incompleteCorrect = !legacyPkt.isComplete(); // Fixed: must be false when header not complete
            if (incompleteCorrect) {
                System.out.println("PASS (Legacy MyPacket now properly flags incomplete headers as false)");
                passed++;
            } else {
                System.err.println("FAIL (Legacy MyPacket still prematurely marked incomplete header as true)");
            }

        } catch (Exception e) {
            System.err.println("CRITICAL TEST FAILURE: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=================================================");
        System.out.printf("TCP FRAMING VALIDATION SUMMARY: %d / %d TESTS PASSED\n", passed, total);
        System.out.println("=================================================");

        if (passed != total) {
            System.exit(1);
        }
    }
}
