package test;

import sipinspector.MyPacket;
import sipinspector.Socket.TCPClientSocket;
import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Socket.UDPClientSocket;
import sipinspector.Utils.TLSDebugLogger;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Live integration and protocol validation against Alcatel-Lucent OmniPCX Enterprise (OXE) PBX.
 * Target: 192.168.0.20:5060 (UDP/TCP), 192.168.0.20:5061 (TLS).
 */
public class OxeLiveIntegrationTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final int SIP_PORT = 5060;
    private static final int TLS_PORT = 5061;

    public static void main(String[] args) {
        System.setProperty("sip.headless", "true");
        System.setProperty("tls.autoAcceptUntrusted", "true");
        TLSDebugLogger.enable("ALL");

        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — Alcatel-Lucent OXE Live Integration Validation");
        System.out.println("   Target PBX: " + PBX_IP + " | Local Host: 192.168.0.9");
        System.out.println("================================================================================");

        try {
            InetAddress localAddr = InetAddress.getByName("192.168.0.9");
            InetAddress pbxAddr = InetAddress.getByName(PBX_IP);

            // Phase 1: UDP SIP OPTIONS Ping
            testUdpOptions(localAddr, pbxAddr);

            // Phase 2: TCP SIP OPTIONS Ping & Stream Framing
            testTcpOptions(localAddr, pbxAddr);

            // Phase 3: TLS 5061 Handshake & Certificate Inspection
            testTlsHandshake(localAddr, pbxAddr);

            // Phase 4: Live SIP Call Signaling & Routing
            testCallSignaling(localAddr, pbxAddr);

            // Phase 5: Clean Deregistration Teardown
            testDeregister(localAddr, pbxAddr);

            System.out.println("\n================================================================================");
            System.out.println("   OXE Live Integration Validation Complete");
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("OXE Live Test Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testUdpOptions(InetAddress localAddr, InetAddress pbxAddr) {
        System.out.println("\n--- [STEP 1: UDP SIP OPTIONS] ---");
        try {
            UDPClientSocket udpSocket = new UDPClientSocket(localAddr, 5060);
            if (!udpSocket.open(pbxAddr, SIP_PORT)) {
                System.out.println("UDP socket open failed.");
                return;
            }

            String callId = UUID.randomUUID().toString() + "@192.168.0.9";
            String optionsMsg =
                    "OPTIONS sip:" + PBX_IP + " SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 192.168.0.9;branch=z9hG4bKudp" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:sipinspector@192.168.0.9>;tag=sim" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sip:" + PBX_IP + ">\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 OPTIONS\r\n" +
                    "Contact: <sip:sipinspector@192.168.0.9>\r\n" +
                    "Accept: application/sdp\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE Integration Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            System.out.println("Sending SIP OPTIONS request over UDP to " + PBX_IP + ":" + SIP_PORT + "...");
            MyPacket udpPkt = new MyPacket(optionsMsg);
            udpPkt.setAddress(pbxAddr);
            udpPkt.setPort(SIP_PORT);
            udpSocket.send(udpPkt);

            // Wait for response with timeout
            long start = System.currentTimeMillis();
            MyPacket resp = null;
            while (System.currentTimeMillis() - start < 3000) {
                resp = udpSocket.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED UDP RESPONSE FROM OXE PBX (" + resp.length() + " bytes):");
                System.out.println(resp.getMessageTxt());
            } else {
                System.out.println("No UDP response received within 3000ms (PBX may require registration or drop unsolicited OPTIONS over UDP).");
            }

            // Step 1B: Send REGISTER over UDP
            System.out.println("\nSending SIP REGISTER for user '1000' over UDP to OXE PBX...");
            String udpRegCallId = "reg-udp-" + UUID.randomUUID().toString() + "@192.168.0.9";
            String udpRegMsg =
                    "REGISTER sip:" + PBX_IP + ":5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 192.168.0.9:5060;branch=z9hG4bKudpreg" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:1000@" + PBX_IP + ":5060>;tag=udpreg" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sip:1000@" + PBX_IP + ":5060>\r\n" +
                    "Call-ID: " + udpRegCallId + "\r\n" +
                    "CSeq: 1 REGISTER\r\n" +
                    "Contact: <sip:1000@192.168.0.9:5060;transport=udp>\r\n" +
                    "Expires: 3600\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE UDP Register Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            MyPacket udpRegPkt = new MyPacket(udpRegMsg);
            udpRegPkt.setAddress(pbxAddr);
            udpRegPkt.setPort(SIP_PORT);
            udpSocket.send(udpRegPkt);

            start = System.currentTimeMillis();
            MyPacket udpRegResp = null;
            while (System.currentTimeMillis() - start < 3000) {
                udpRegResp = udpSocket.receive();
                if (udpRegResp != null && udpRegResp.getMessageTxt() != null && udpRegResp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (udpRegResp != null && udpRegResp.getMessageTxt() != null && udpRegResp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED UDP REGISTER RESPONSE FROM OXE PBX (" + udpRegResp.length() + " bytes):");
                System.out.println(udpRegResp.getMessageTxt());
            } else {
                System.out.println("No response to UDP REGISTER within 3000ms.");
            }

            udpSocket.close();
        } catch (Exception e) {
            System.out.println("UDP test exception: " + e.getMessage());
        }
    }

    private static void testTcpOptions(InetAddress localAddr, InetAddress pbxAddr) {
        System.out.println("\n--- [STEP 2: TCP SIP OPTIONS & STREAM FRAMING] ---");
        try {
            TCPClientSocket tcpSocket = new TCPClientSocket(localAddr, 0); // Dynamic local port for TCP client
            System.out.println("Connecting to " + PBX_IP + ":" + SIP_PORT + " via TCP...");
            boolean connected = tcpSocket.open(pbxAddr, SIP_PORT);
            if (!connected) {
                System.out.println("TCP connection to OXE PBX failed.");
                return;
            }
            System.out.println("TCP Connection Established successfully to " + PBX_IP + ":" + SIP_PORT);

            String callId = UUID.randomUUID().toString() + "@192.168.0.9";
            String optionsMsg =
                    "OPTIONS sip:" + PBX_IP + ":5060;transport=tcp SIP/2.0\r\n" +
                    "Via: SIP/2.0/TCP 192.168.0.9;branch=z9hG4bKtcp" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:sipinspector@192.168.0.9>;tag=tcp" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sip:" + PBX_IP + ":5060>\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 101 OPTIONS\r\n" +
                    "Contact: <sip:sipinspector@192.168.0.9;transport=tcp>\r\n" +
                    "Accept: application/sdp\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE Integration Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            System.out.println("Sending SIP OPTIONS request over TCP...");
            MyPacket tcpPkt = new MyPacket(optionsMsg);
            tcpPkt.setAddress(pbxAddr);
            tcpPkt.setPort(SIP_PORT);
            tcpSocket.send(tcpPkt);

            // Wait for response via SipStreamFramer
            long start = System.currentTimeMillis();
            MyPacket resp = null;
            while (System.currentTimeMillis() - start < 4000) {
                resp = tcpSocket.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED TCP RESPONSE FROM OXE PBX (Framed by SipStreamFramer, " + resp.length() + " bytes):");
                System.out.println(resp.getMessageTxt());
            } else {
                System.out.println("TCP connected but no response to OPTIONS received within 4000ms.");
            }

            // Step 2B: Send REGISTER over TCP
            System.out.println("\nSending SIP REGISTER for user '1000' over TCP to OXE PBX...");
            String regCallId = "reg-" + UUID.randomUUID().toString() + "@192.168.0.9";
            String regMsg =
                    "REGISTER sip:" + PBX_IP + ":5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/TCP 192.168.0.9:5060;branch=z9hG4bKreg" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:1000@" + PBX_IP + ":5060>;tag=reg" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sip:1000@" + PBX_IP + ":5060>\r\n" +
                    "Call-ID: " + regCallId + "\r\n" +
                    "CSeq: 1 REGISTER\r\n" +
                    "Contact: <sip:1000@192.168.0.9:5060;transport=tcp>\r\n" +
                    "Expires: 3600\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE Register Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            MyPacket regPkt = new MyPacket(regMsg);
            regPkt.setAddress(pbxAddr);
            regPkt.setPort(SIP_PORT);
            tcpSocket.send(regPkt);

            start = System.currentTimeMillis();
            MyPacket regResp = null;
            while (System.currentTimeMillis() - start < 4000) {
                regResp = tcpSocket.receive();
                if (regResp != null && regResp.getMessageTxt() != null && regResp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (regResp != null && regResp.getMessageTxt() != null && regResp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED REGISTER RESPONSE FROM OXE PBX (Framed by SipStreamFramer, " + regResp.length() + " bytes):");
                System.out.println(regResp.getMessageTxt());
            } else {
                System.out.println("No response to REGISTER within 4000ms.");
            }

            tcpSocket.close();
        } catch (Exception e) {
            System.out.println("TCP test exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testTlsHandshake(InetAddress localAddr, InetAddress pbxAddr) {
        System.out.println("\n--- [STEP 3: TLS 5061 HANDSHAKE & CERTIFICATE INSPECTION] ---");
        try {
            TLSClientSocket_V6 tlsSocket = new TLSClientSocket_V6(localAddr, 0);
            System.out.println("Initiating TLS handshake to " + PBX_IP + ":" + TLS_PORT + "...");
            boolean tlsOk = tlsSocket.open(pbxAddr, TLS_PORT);
            if (!tlsOk) {
                System.out.println("TLS handshake to OXE PBX failed (or rejected).");
                return;
            }

            System.out.println(">>> TLS HANDSHAKE SUCCESSFUL!");
            System.out.println("Cipher Suite: " + TLSClientSocket_V6.getLastCipherSuite());
            System.out.println("TLS Protocol: " + TLSClientSocket_V6.getLastProtocol());
            System.out.println("Peer Subject: " + TLSClientSocket_V6.getLastPeerCertSubject());
            System.out.println("Peer Issuer:  " + TLSClientSocket_V6.getLastPeerCertIssuer());
            System.out.println("Peer Validity:" + TLSClientSocket_V6.getLastPeerCertValidity());

            String callId = UUID.randomUUID().toString() + "@192.168.0.9";
            String optionsMsg =
                    "OPTIONS sips:" + PBX_IP + ":5061;transport=tls SIP/2.0\r\n" +
                    "Via: SIP/2.0/TLS 192.168.0.9:5061;branch=z9hG4bKtls" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sips:sipinspector@192.168.0.9>;tag=tls" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sips:" + PBX_IP + ":5061>\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 201 OPTIONS\r\n" +
                    "Contact: <sips:sipinspector@192.168.0.9:5061;transport=tls>\r\n" +
                    "Accept: application/sdp\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE TLS Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            System.out.println("Sending SIP OPTIONS request over TLS...");
            MyPacket tlsPkt = new MyPacket(optionsMsg);
            tlsPkt.setAddress(pbxAddr);
            tlsPkt.setPort(TLS_PORT);
            tlsSocket.send(tlsPkt);

            long start = System.currentTimeMillis();
            MyPacket resp = null;
            while (System.currentTimeMillis() - start < 4000) {
                resp = tlsSocket.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED TLS RESPONSE FROM OXE PBX (" + resp.length() + " bytes):");
                System.out.println(resp.getMessageTxt());
            } else {
                System.out.println("TLS connected, no response to SIPS OPTIONS within 4000ms.");
            }

            // Step 3B: Send SIPS REGISTER over TLS
            System.out.println("\nSending SIPS REGISTER for user '1000' over TLS to OXE PBX...");
            String tlsRegCallId = "reg-tls-" + UUID.randomUUID().toString() + "@192.168.0.9";
            String tlsRegMsg =
                    "REGISTER sips:" + PBX_IP + ":5061 SIP/2.0\r\n" +
                    "Via: SIP/2.0/TLS 192.168.0.9:5061;branch=z9hG4bKtlsreg" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sips:1000@" + PBX_IP + ":5061>;tag=tlsreg" + (int)(Math.random() * 100000) + "\r\n" +
                    "To: <sips:1000@" + PBX_IP + ":5061>\r\n" +
                    "Call-ID: " + tlsRegCallId + "\r\n" +
                    "CSeq: 1 REGISTER\r\n" +
                    "Contact: <sips:1000@192.168.0.9:5061;transport=tls>\r\n" +
                    "Expires: 3600\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE TLS Register Test)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            MyPacket tlsRegPkt = new MyPacket(tlsRegMsg);
            tlsRegPkt.setAddress(pbxAddr);
            tlsRegPkt.setPort(TLS_PORT);
            tlsSocket.send(tlsRegPkt);

            start = System.currentTimeMillis();
            MyPacket tlsRegResp = null;
            while (System.currentTimeMillis() - start < 4000) {
                tlsRegResp = tlsSocket.receive();
                if (tlsRegResp != null && tlsRegResp.getMessageTxt() != null && tlsRegResp.getMessageTxt().length() > 0) {
                    break;
                }
                Thread.sleep(50);
            }

            if (tlsRegResp != null && tlsRegResp.getMessageTxt() != null && tlsRegResp.getMessageTxt().length() > 0) {
                System.out.println(">>> RECEIVED TLS REGISTER RESPONSE FROM OXE PBX (" + tlsRegResp.length() + " bytes):");
                System.out.println(tlsRegResp.getMessageTxt());
            } else {
                System.out.println("No response to TLS REGISTER within 4000ms.");
            }

            tlsSocket.close();
        } catch (Exception e) {
            System.out.println("TLS test exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testCallSignaling(InetAddress localAddr, InetAddress pbxAddr) {
        System.out.println("\n--- [STEP 4: LIVE OXE CALL SIGNALING & 100 TRYING VALIDATION] ---");
        try {
            UDPClientSocket udp = new UDPClientSocket(localAddr, 5060);
            if (!udp.open(pbxAddr, SIP_PORT)) {
                System.out.println("UDP socket open failed.");
                return;
            }

            String sdp =
                    "v=0\r\n" +
                    "o=OXE 1675390460 1675390460 IN IP4 192.168.0.9\r\n" +
                    "s=abs\r\n" +
                    "c=IN IP4 192.168.0.9\r\n" +
                    "t=0 0\r\n" +
                    "m=audio 42000 RTP/AVP 8 0 9 101\r\n" +
                    "a=sendrecv\r\n" +
                    "a=rtpmap:8 PCMA/8000\r\n" +
                    "a=rtpmap:0 PCMU/8000\r\n" +
                    "a=rtpmap:9 G722/8000\r\n" +
                    "a=rtpmap:101 telephone-event/8000\r\n";

            String callId = "live-call-" + UUID.randomUUID().toString() + "@192.168.0.9";
            String inviteMsg =
                    "INVITE sip:1001@" + PBX_IP + ":" + SIP_PORT + " SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 192.168.0.9:5060;branch=z9hG4bKinv" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:1000@" + PBX_IP + ":" + SIP_PORT + ">;tag=call" + System.currentTimeMillis() + "\r\n" +
                    "To: <sip:1001@" + PBX_IP + ":" + SIP_PORT + ">\r\n" +
                    "Call-ID: " + callId + "\r\n" +
                    "CSeq: 1 INVITE\r\n" +
                    "Contact: <sip:1000@192.168.0.9:5060;transport=udp>\r\n" +
                    "User-Agent: SIP Simulator v4 (OXE Call Test)\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: " + sdp.getBytes().length + "\r\n\r\n" +
                    sdp;

            System.out.println("Sending INVITE to sip:1001@" + PBX_IP + ":" + SIP_PORT + "...");
            MyPacket invitePkt = new MyPacket(inviteMsg);
            invitePkt.setAddress(pbxAddr);
            invitePkt.setPort(SIP_PORT);
            udp.send(invitePkt);

            long start = System.currentTimeMillis();
            boolean gotTrying = false;
            while (System.currentTimeMillis() - start < 3000) {
                MyPacket resp = udp.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                    System.out.println(">>> RECEIVED CALL RESPONSE (" + resp.length() + " bytes):");
                    System.out.println(resp.getMessageTxt());
                    if (resp.getMessageTxt().contains("100 Trying")) {
                        gotTrying = true;
                    }
                }
                Thread.sleep(50);
            }

            if (gotTrying) {
                System.out.println(">>> 100 Trying transaction response confirmed from OXE PBX!");
            }
            udp.close();
        } catch (Exception e) {
            System.out.println("Call signaling exception: " + e.getMessage());
        }
    }

    private static void testDeregister(InetAddress localAddr, InetAddress pbxAddr) {
        System.out.println("\n--- [STEP 5: CLEAN DEREGISTRATION TEARDOWN] ---");
        try {
            UDPClientSocket udp = new UDPClientSocket(localAddr, 5060);
            if (!udp.open(pbxAddr, SIP_PORT)) return;

            String deregMsg =
                    "REGISTER sip:" + PBX_IP + ":5060 SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP 192.168.0.9:5060;branch=z9hG4bKdereg" + System.currentTimeMillis() + ";rport\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: <sip:1000@" + PBX_IP + ":5060>;tag=dereg1\r\n" +
                    "To: <sip:1000@" + PBX_IP + ":5060>\r\n" +
                    "Call-ID: dereg-" + UUID.randomUUID() + "@192.168.0.9\r\n" +
                    "CSeq: 1 REGISTER\r\n" +
                    "Contact: *\r\n" +
                    "Expires: 0\r\n" +
                    "User-Agent: SIP Simulator v4 (Deregister)\r\n" +
                    "Content-Length: 0\r\n\r\n";

            MyPacket pkt = new MyPacket(deregMsg);
            pkt.setAddress(pbxAddr);
            pkt.setPort(SIP_PORT);
            udp.send(pkt);

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 2000) {
                MyPacket resp = udp.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().contains("200 OK")) {
                    System.out.println(">>> Clean deregistration confirmed: \n" + resp.getMessageTxt());
                    break;
                }
                Thread.sleep(50);
            }
            udp.close();
        } catch (Exception e) {
            System.out.println("Deregister exception: " + e.getMessage());
        }
    }
}
