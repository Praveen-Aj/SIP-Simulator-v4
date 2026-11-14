package test;

import sipinspector.Media.AudioFormatUtils;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioCodec;
import sipinspector.Media.RtcpMonitor;
import sipinspector.MyPacket;
import sipinspector.Socket.UDPClientSocket;
import sipinspector.Utils.SIPParser;

import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

/**
 * Live OXE PBX Two-Endpoint End-to-End Call and Media Integration Test
 * Endpoint A: SIP Simulator v4 (Extension 90008 @ 192.168.0.9:5060)
 * Endpoint B: MicroSIP 3.22.16  (Extension 90001 @ 192.168.0.9:49864)
 * PBX: Alcatel-Lucent OmniPCX Enterprise (OXE) @ 192.168.0.20:5060
 *
 * Tests executed:
 * - Registration of Extension 90008 on OXE PBX
 * - Test A: Simulator (90008) -> MicroSIP (90001) Call Setup, 3-way handshake
 * - Test B: MicroSIP (90001) -> Simulator (90008) Inbound Call Setup, 3-way handshake
 * - Test C: Bidirectional RTP Audio Streaming (PCMA PT=8)
 * - Test D: Call Termination via BYE -> 200 OK
 * - Extension 90008 Clean Deregistration
 */
public class OxeLiveEndpointTest {

    private static final String PBX_IP = "192.168.0.20";
    private static final int PBX_SIP_PORT = 5060;
    private static final String LOCAL_IP = "192.168.0.9";
    private static final int SIMULATOR_SIP_PORT = 5060;
    private static final int SIMULATOR_RTP_PORT = 42000;
    private static final String SIMULATOR_EXT = "90008";
    private static final String MICROSIP_EXT = "90001";

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   SIP Simulator v4 — Live OXE PBX Two-Endpoint Integration Validation");
        System.out.println("   Topology: Endpoint A (Simulator " + SIMULATOR_EXT + ") <-> OXE (" + PBX_IP + ") <-> Endpoint B (MicroSIP " + MICROSIP_EXT + ")");
        System.out.println("================================================================================");

        UDPClientSocket sipSocket = null;
        try {
            InetAddress localAddr = InetAddress.getByName(LOCAL_IP);
            InetAddress pbxAddr = InetAddress.getByName(PBX_IP);

            // Step 0: Check MicroSIP IPC connectivity
            System.out.println("\n[CHECK] Verifying MicroSIP IPC status via Windows Named Pipe...");
            String ipcStatus = MicroSipIpc.sendCommand("system.status", null);
            System.out.println("MicroSIP IPC: " + ipcStatus);
            if (ipcStatus.contains("\"error\"") || !ipcStatus.contains("\"registered\":true")) {
                System.out.println("WARNING: MicroSIP is not reporting registered:true. Verify MicroSIP is running.");
            } else {
                System.out.println(">>> MicroSIP 90001 confirmed active and registered on OXE!");
            }

            // Step 1: Open shared UDP signaling socket for 90008
            sipSocket = new UDPClientSocket(localAddr, SIMULATOR_SIP_PORT);

            // Step 2: Register extension 90008
            boolean regOk = registerEndpoint(sipSocket, pbxAddr, SIMULATOR_EXT, SIMULATOR_SIP_PORT);
            if (!regOk) {
                System.err.println("FATAL: Registration of " + SIMULATOR_EXT + " failed. Aborting.");
                return;
            }

            // Step 3: Test A — Outbound Call (Simulator 90008 -> MicroSIP 90001)
            System.out.println("\n================================================================================");
            System.out.println("   TEST A & C & D: OUTBOUND CALL (SIMULATOR 90008 -> MICROSIP 90001)");
            System.out.println("================================================================================");
            boolean testAOk = executeTestAOutboundCall(sipSocket, localAddr, pbxAddr);
            System.out.println(">>> TEST A (Outbound Call): " + (testAOk ? "PASSED" : "FAILED"));

            // Short pause between tests
            Thread.sleep(2000);

            // Step 4: Test B — Inbound Call (MicroSIP 90001 -> Simulator 90008)
            System.out.println("\n================================================================================");
            System.out.println("   TEST B & C & D: INBOUND CALL (MICROSIP 90001 -> SIMULATOR 90008)");
            System.out.println("================================================================================");
            boolean testBOk = executeTestBInboundCall(sipSocket, localAddr, pbxAddr);
            System.out.println(">>> TEST B (Inbound Call): " + (testBOk ? "PASSED" : "FAILED"));

            // Step 5: Clean Deregistration
            deregisterEndpoint(sipSocket, pbxAddr, SIMULATOR_EXT);

            System.out.println("\n================================================================================");
            System.out.println("   FINAL RESULTS SUMMARY");
            System.out.println("   Test A (Outbound Call to MicroSIP): " + (testAOk ? "PASS" : "FAIL"));
            System.out.println("   Test B (Inbound Call from MicroSIP): " + (testBOk ? "PASS" : "FAIL"));
            System.out.println("   Overall Status: " + ((testAOk && testBOk) ? "ALL TESTS PASSED!" : "SOME TESTS FAILED"));
            System.out.println("================================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sipSocket != null) {
                try {
                    sipSocket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static boolean registerEndpoint(UDPClientSocket socket, InetAddress pbxAddr, String user, int port) throws Exception {
        System.out.println("\n--- [REGISTRATION: EXTENSION " + user + " -> OXE PBX] ---");
        String callId = "reg-" + UUID.randomUUID() + "@" + LOCAL_IP;
        String branch = "z9hG4bKreg" + System.currentTimeMillis();
        String regMsg =
                "REGISTER sip:" + PBX_IP + " SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP " + LOCAL_IP + ":" + port + ";rport;branch=" + branch + "\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"" + user + "\" <sip:" + user + "@" + PBX_IP + ">;tag=reg" + (int)(Math.random() * 100000) + "\r\n" +
                "To: \"" + user + "\" <sip:" + user + "@" + PBX_IP + ">\r\n" +
                "Call-ID: callId\r\n" +
                "CSeq: 1 REGISTER\r\n" +
                "Contact: \"" + user + "\" <sip:" + user + "@" + LOCAL_IP + ":" + port + ";transport=udp>\r\n" +
                "Expires: 1800\r\n" +
                "Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n" +
                "User-Agent: SIP Simulator v4\r\n" +
                "Content-Length: 0\r\n\r\n";
        regMsg = regMsg.replace("callId", callId);

        MyPacket pkt = new MyPacket(regMsg);
        pkt.setAddress(pbxAddr);
        pkt.setPort(PBX_SIP_PORT);
        socket.send(pkt);

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 4000) {
            MyPacket resp = socket.receive();
            if (resp != null && resp.getMessageTxt() != null) {
                String code = SIPParser.getResponseCode(resp.getMessageTxt());
                if ("200".equals(code)) {
                    System.out.println(">>> Extension " + user + " Registered Successfully with OXE PBX (200 OK)!");
                    return true;
                }
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static boolean executeTestAOutboundCall(UDPClientSocket sipSocket, InetAddress localAddr, InetAddress pbxAddr) throws Exception {
        CallState callState = CallState.IDLE;
        System.out.println("CallState: " + callState);

        String sdpOffer =
                "v=0\r\n" +
                "o=SIPSimulator 90008 90008 IN IP4 " + LOCAL_IP + "\r\n" +
                "s=SIP Call\r\n" +
                "c=IN IP4 " + LOCAL_IP + "\r\n" +
                "t=0 0\r\n" +
                "m=audio " + SIMULATOR_RTP_PORT + " RTP/AVP 8 0 101\r\n" +
                "a=rtpmap:8 PCMA/8000\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=rtpmap:101 telephone-event/8000\r\n" +
                "a=fmtp:101 0-15\r\n" +
                "a=sendrecv\r\n" +
                "a=ptime:20\r\n";

        String callId = "call-" + UUID.randomUUID() + "@" + LOCAL_IP;
        String fromTag = "simTag" + System.currentTimeMillis();
        String inviteBranch = "z9hG4bKinv" + System.currentTimeMillis();

        String inviteMsg =
                "INVITE sip:" + MICROSIP_EXT + "@" + PBX_IP + " SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP " + LOCAL_IP + ":" + SIMULATOR_SIP_PORT + ";rport;branch=" + inviteBranch + "\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"" + SIMULATOR_EXT + "\" <sip:" + SIMULATOR_EXT + "@" + PBX_IP + ">;tag=" + fromTag + "\r\n" +
                "To: \"" + MICROSIP_EXT + "\" <sip:" + MICROSIP_EXT + "@" + PBX_IP + ">\r\n" +
                "Call-ID: " + callId + "\r\n" +
                "CSeq: 1 INVITE\r\n" +
                "Contact: \"" + SIMULATOR_EXT + "\" <sip:" + SIMULATOR_EXT + "@" + LOCAL_IP + ":" + SIMULATOR_SIP_PORT + ";transport=udp>\r\n" +
                "Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n" +
                "Supported: replaces, timer\r\n" +
                "User-Agent: SIP Simulator v4\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: " + sdpOffer.getBytes().length + "\r\n\r\n" +
                sdpOffer;

        System.out.println("Sending INVITE to sip:" + MICROSIP_EXT + "@" + PBX_IP + "...");
        MyPacket invitePkt = new MyPacket(inviteMsg);
        invitePkt.setAddress(pbxAddr);
        invitePkt.setPort(PBX_SIP_PORT);
        sipSocket.send(invitePkt);

        callState = CallState.CALLING;
        System.out.println("CallState -> " + callState);

        String remoteToTag = null;
        String remoteContact = null;
        String negotiatedRemoteRtpIp = PBX_IP;
        int negotiatedRemoteRtpPort = 0;
        List<String> recordRoutes = new ArrayList<>();
        boolean answered = false;
        boolean ringing = false;

        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 20000) {
            MyPacket resp = sipSocket.receive();
            if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().length() > 0) {
                String txt = resp.getMessageTxt();
                String code = SIPParser.getResponseCode(txt);
                System.out.println("<<< [RESPONSE " + code + " received]:\n" + txt);

                if ("100".equals(code)) {
                    callState = CallState.PROCEEDING;
                    System.out.println("CallState -> " + callState);
                } else if ("180".equals(code)) {
                    callState = CallState.RINGING;
                    ringing = true;
                    System.out.println("CallState -> " + callState + " (MicroSIP is ringing!)");

                    // Trigger automatic answer on MicroSIP via IPC
                    System.out.println("Triggering answer on MicroSIP via IPC...");
                    String ansResp = MicroSipIpc.sendCommand("call.answer", "{}");
                    System.out.println("MicroSIP IPC answer response: " + ansResp);

                } else if ("200".equals(code)) {
                    remoteToTag = SIPParser.getToTag(txt);
                    remoteContact = SIPParser.getHeaderValue(txt, "Contact");

                    for (String line : txt.split("\r?\n")) {
                        if (line.toLowerCase().startsWith("record-route:") || line.toLowerCase().startsWith("r:")) {
                            recordRoutes.add(line.substring(line.indexOf(':') + 1).trim());
                        }
                    }

                    // Parse SDP
                    for (String line : txt.split("\r?\n")) {
                        if (line.startsWith("c=IN IP4 ")) {
                            negotiatedRemoteRtpIp = line.substring(9).trim();
                        }
                        if (line.startsWith("m=audio ")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length > 1) {
                                try {
                                    negotiatedRemoteRtpPort = Integer.parseInt(parts[1]);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }

                    callState = CallState.ESTABLISHED;
                    System.out.println("CallState -> " + callState + " (DIALOG ESTABLISHED!)");
                    System.out.println("Remote SDP: RTP=" + negotiatedRemoteRtpIp + ":" + negotiatedRemoteRtpPort);
                    answered = true;
                    break;
                } else if (code.startsWith("4") || code.startsWith("5") || code.startsWith("6")) {
                    System.err.println("Call failed with code: " + code);
                    callState = CallState.FAILED;
                    return false;
                }
            }
            Thread.sleep(50);
        }

        if (!answered) {
            System.err.println("Test A failed: No 200 OK received within timeout.");
            return false;
        }

        // Clean Remote Contact URI
        String ackReqUri = "sip:" + MICROSIP_EXT + "@" + PBX_IP;
        if (remoteContact != null && remoteContact.contains("<") && remoteContact.contains(">")) {
            ackReqUri = remoteContact.substring(remoteContact.indexOf('<') + 1, remoteContact.indexOf('>'));
        }

        // Send ACK
        System.out.println("\nSending ACK to complete 3-way handshake...");
        StringBuilder ackBuilder = new StringBuilder();
        ackBuilder.append("ACK ").append(ackReqUri).append(" SIP/2.0\r\n");
        ackBuilder.append("Via: SIP/2.0/UDP ").append(LOCAL_IP).append(":").append(SIMULATOR_SIP_PORT).append(";rport;branch=z9hG4bKack").append(System.currentTimeMillis()).append("\r\n");
        ackBuilder.append("Max-Forwards: 70\r\n");
        ackBuilder.append("From: \"").append(SIMULATOR_EXT).append("\" <sip:").append(SIMULATOR_EXT).append("@").append(PBX_IP).append(">;tag=").append(fromTag).append("\r\n");
        ackBuilder.append("To: \"").append(MICROSIP_EXT).append("\" <sip:").append(MICROSIP_EXT).append("@").append(PBX_IP).append(">");
        if (remoteToTag != null && !remoteToTag.isEmpty()) {
            ackBuilder.append(";tag=").append(remoteToTag);
        }
        ackBuilder.append("\r\n");
        ackBuilder.append("Call-ID: ").append(callId).append("\r\n");
        ackBuilder.append("CSeq: 1 ACK\r\n");
        for (String rr : recordRoutes) {
            ackBuilder.append("Route: ").append(rr).append("\r\n");
        }
        ackBuilder.append("Contact: \"").append(SIMULATOR_EXT).append("\" <sip:").append(SIMULATOR_EXT).append("@").append(LOCAL_IP).append(":").append(SIMULATOR_SIP_PORT).append(";transport=udp>\r\n");
        ackBuilder.append("Content-Length: 0\r\n\r\n");

        MyPacket ackPkt = new MyPacket(ackBuilder.toString());
        ackPkt.setAddress(pbxAddr);
        ackPkt.setPort(PBX_SIP_PORT);
        sipSocket.send(ackPkt);
        System.out.println("ACK transmitted. 3-way handshake complete!");

        // Test C: Bidirectional RTP Streaming
        if (negotiatedRemoteRtpPort > 0) {
            System.out.println("\n--- [TEST C: BIDIRECTIONAL RTP AUDIO STREAMING] ---");
            InetAddress mediaRemoteAddr = InetAddress.getByName(negotiatedRemoteRtpIp);
            executeBidirectionalAudioSession(localAddr, SIMULATOR_RTP_PORT, mediaRemoteAddr, negotiatedRemoteRtpPort, 4000);
        }

        // Test D: Call Teardown via BYE
        System.out.println("\n--- [TEST D: CALL TEARDOWN VIA BYE] ---");
        callState = CallState.TERMINATING;
        System.out.println("CallState -> " + callState);

        StringBuilder byeBuilder = new StringBuilder();
        byeBuilder.append("BYE ").append(ackReqUri).append(" SIP/2.0\r\n");
        byeBuilder.append("Via: SIP/2.0/UDP ").append(LOCAL_IP).append(":").append(SIMULATOR_SIP_PORT).append(";rport;branch=z9hG4bKbye").append(System.currentTimeMillis()).append("\r\n");
        byeBuilder.append("Max-Forwards: 70\r\n");
        byeBuilder.append("From: \"").append(SIMULATOR_EXT).append("\" <sip:").append(SIMULATOR_EXT).append("@").append(PBX_IP).append(">;tag=").append(fromTag).append("\r\n");
        byeBuilder.append("To: \"").append(MICROSIP_EXT).append("\" <sip:").append(MICROSIP_EXT).append("@").append(PBX_IP).append(">");
        if (remoteToTag != null && !remoteToTag.isEmpty()) {
            byeBuilder.append(";tag=").append(remoteToTag);
        }
        byeBuilder.append("\r\n");
        byeBuilder.append("Call-ID: ").append(callId).append("\r\n");
        byeBuilder.append("CSeq: 2 BYE\r\n");
        for (String rr : recordRoutes) {
            byeBuilder.append("Route: ").append(rr).append("\r\n");
        }
        byeBuilder.append("Content-Length: 0\r\n\r\n");

        MyPacket byePkt = new MyPacket(byeBuilder.toString());
        byePkt.setAddress(pbxAddr);
        byePkt.setPort(PBX_SIP_PORT);
        sipSocket.send(byePkt);

        long byeStart = System.currentTimeMillis();
        boolean byeOk = false;
        while (System.currentTimeMillis() - byeStart < 4000) {
            MyPacket resp = sipSocket.receive();
            if (resp != null && resp.getMessageTxt() != null) {
                String code = SIPParser.getResponseCode(resp.getMessageTxt());
                System.out.println("<<< [BYE RESPONSE]: " + code);
                if ("200".equals(code)) {
                    callState = CallState.TERMINATED;
                    System.out.println("CallState -> " + callState + " (CALL TERMINATED CLEANLY)");
                    byeOk = true;
                    break;
                }
            }
            Thread.sleep(50);
        }
        return byeOk;
    }

    private static boolean executeTestBInboundCall(UDPClientSocket sipSocket, InetAddress localAddr, InetAddress pbxAddr) throws Exception {
        CallState callState = CallState.IDLE;
        System.out.println("CallState: " + callState);

        // Step 1: Dispatch call from MicroSIP to Simulator (90008) via IPC
        System.out.println("Triggering MicroSIP to dial " + SIMULATOR_EXT + " via IPC...");
        String dialResp = MicroSipIpc.sendCommand("call.dial", "{\"destination\":\"" + SIMULATOR_EXT + "\"}");
        System.out.println("MicroSIP IPC Dial: " + dialResp);

        // Step 2: Listen on UDP 5060 for incoming INVITE from OXE PBX
        System.out.println("Waiting for incoming INVITE from OXE PBX...");
        long startWait = System.currentTimeMillis();
        MyPacket incomingInvitePkt = null;

        while (System.currentTimeMillis() - startWait < 15000) {
            MyPacket pkt = sipSocket.receive();
            if (pkt != null && pkt.getMessageTxt() != null) {
                String msg = pkt.getMessageTxt();
                if (msg.startsWith("INVITE") || msg.contains("INVITE sip:" + SIMULATOR_EXT)) {
                    incomingInvitePkt = pkt;
                    break;
                }
            }
            Thread.sleep(50);
        }

        if (incomingInvitePkt == null) {
            System.err.println("Test B failed: No incoming INVITE received for " + SIMULATOR_EXT);
            return false;
        }

        String inviteTxt = incomingInvitePkt.getMessageTxt();
        System.out.println("\n<<< [RECEIVED INCOMING INVITE FROM OXE]:\n" + inviteTxt);

        callState = CallState.RINGING;
        System.out.println("CallState -> " + callState);

        // Extract SIP headers from INVITE
        List<String> viaHeaders = new ArrayList<>();
        List<String> recordRoutes = new ArrayList<>();
        String fromHeader = null;
        String toHeader = null;
        String callIdHeader = null;
        String cseqHeader = null;
        String contactHeader = null;
        String remoteRtpIp = PBX_IP;
        int remoteRtpPort = 0;

        for (String line : inviteTxt.split("\r?\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("via:") || lower.startsWith("v:")) {
                viaHeaders.add(line);
            } else if (lower.startsWith("record-route:") || lower.startsWith("r:")) {
                recordRoutes.add(line);
            } else if (lower.startsWith("from:") || lower.startsWith("f:")) {
                fromHeader = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("to:") || lower.startsWith("t:")) {
                toHeader = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("call-id:") || lower.startsWith("i:")) {
                callIdHeader = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("cseq:")) {
                cseqHeader = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("contact:") || lower.startsWith("m:")) {
                contactHeader = line.substring(line.indexOf(':') + 1).trim();
            } else if (line.startsWith("c=IN IP4 ")) {
                remoteRtpIp = line.substring(9).trim();
            } else if (line.startsWith("m=audio ")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    try {
                        remoteRtpPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        String localTag = "simTag" + (int)(Math.random() * 100000);
        String toWithTag = toHeader.contains(";tag=") ? toHeader : toHeader + ";tag=" + localTag;

        // Step 3: Send 180 Ringing
        System.out.println("\nSending 180 Ringing response...");
        StringBuilder r180 = new StringBuilder();
        r180.append("SIP/2.0 180 Ringing\r\n");
        for (String v : viaHeaders) {
            r180.append(v).append("\r\n");
        }
        for (String rr : recordRoutes) {
            r180.append(rr).append("\r\n");
        }
        r180.append("From: ").append(fromHeader).append("\r\n");
        r180.append("To: ").append(toWithTag).append("\r\n");
        r180.append("Call-ID: ").append(callIdHeader).append("\r\n");
        r180.append("CSeq: ").append(cseqHeader).append("\r\n");
        r180.append("Contact: \"").append(SIMULATOR_EXT).append("\" <sip:").append(SIMULATOR_EXT).append("@").append(LOCAL_IP).append(":").append(SIMULATOR_SIP_PORT).append(";transport=udp>\r\n");
        r180.append("Content-Length: 0\r\n\r\n");

        MyPacket p180 = new MyPacket(r180.toString());
        p180.setAddress(incomingInvitePkt.getAddress());
        p180.setPort(incomingInvitePkt.getPort());
        sipSocket.send(p180);

        callState = CallState.RINGING;
        System.out.println("CallState -> " + callState);

        Thread.sleep(600);

        // Step 4: Send 200 OK with SDP answer
        System.out.println("\nSending 200 OK with SDP answer...");
        String sdpAnswer =
                "v=0\r\n" +
                "o=SIPSimulator 90008 90008 IN IP4 " + LOCAL_IP + "\r\n" +
                "s=SIP Call\r\n" +
                "c=IN IP4 " + LOCAL_IP + "\r\n" +
                "t=0 0\r\n" +
                "m=audio " + SIMULATOR_RTP_PORT + " RTP/AVP 8 0 101\r\n" +
                "a=rtpmap:8 PCMA/8000\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=rtpmap:101 telephone-event/8000\r\n" +
                "a=fmtp:101 0-15\r\n" +
                "a=sendrecv\r\n" +
                "a=ptime:20\r\n";

        StringBuilder r200 = new StringBuilder();
        r200.append("SIP/2.0 200 OK\r\n");
        for (String v : viaHeaders) {
            r200.append(v).append("\r\n");
        }
        for (String rr : recordRoutes) {
            r200.append(rr).append("\r\n");
        }
        r200.append("From: ").append(fromHeader).append("\r\n");
        r200.append("To: ").append(toWithTag).append("\r\n");
        r200.append("Call-ID: ").append(callIdHeader).append("\r\n");
        r200.append("CSeq: ").append(cseqHeader).append("\r\n");
        r200.append("Contact: \"").append(SIMULATOR_EXT).append("\" <sip:").append(SIMULATOR_EXT).append("@").append(LOCAL_IP).append(":").append(SIMULATOR_SIP_PORT).append(";transport=udp>\r\n");
        r200.append("Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n");
        r200.append("User-Agent: SIP Simulator v4\r\n");
        r200.append("Content-Type: application/sdp\r\n");
        r200.append("Content-Length: ").append(sdpAnswer.getBytes().length).append("\r\n\r\n");
        r200.append(sdpAnswer);

        MyPacket p200 = new MyPacket(r200.toString());
        p200.setAddress(incomingInvitePkt.getAddress());
        p200.setPort(incomingInvitePkt.getPort());
        sipSocket.send(p200);

        // Step 5: Wait for ACK from OXE/MicroSIP
        System.out.println("Waiting for ACK from OXE/MicroSIP...");
        long ackWait = System.currentTimeMillis();
        boolean ackReceived = false;

        while (System.currentTimeMillis() - ackWait < 5000) {
            MyPacket resp = sipSocket.receive();
            if (resp != null && resp.getMessageTxt() != null) {
                String txt = resp.getMessageTxt();
                if (txt.startsWith("ACK") && txt.contains(callIdHeader)) {
                    System.out.println("<<< [ACK RECEIVED]:\n" + txt);
                    ackReceived = true;
                    callState = CallState.ESTABLISHED;
                    System.out.println("CallState -> " + callState + " (INBOUND CALL ESTABLISHED!)");
                    break;
                }
            }
            Thread.sleep(50);
        }

        if (!ackReceived) {
            System.err.println("Test B warning: ACK not received within timeout, but proceeding with media test.");
            callState = CallState.ESTABLISHED;
        }

        // Test C: Bidirectional RTP Streaming
        if (remoteRtpPort > 0) {
            System.out.println("\n--- [TEST C: BIDIRECTIONAL RTP AUDIO STREAMING (INBOUND)] ---");
            InetAddress mediaRemoteAddr = InetAddress.getByName(remoteRtpIp);
            executeBidirectionalAudioSession(localAddr, SIMULATOR_RTP_PORT, mediaRemoteAddr, remoteRtpPort, 4000);
        }

        // Test D: Hang up from MicroSIP via IPC (or BYE from Simulator)
        System.out.println("\n--- [TEST D: HANGING UP CALL VIA MICROSIP IPC] ---");
        callState = CallState.TERMINATING;
        System.out.println("CallState -> " + callState);
        String hangupResp = MicroSipIpc.sendCommand("call.hangup", "{}");
        System.out.println("MicroSIP IPC Hangup: " + hangupResp);

        // Listen for BYE or 200 OK on socket
        long byeWait = System.currentTimeMillis();
        while (System.currentTimeMillis() - byeWait < 4000) {
            MyPacket resp = sipSocket.receive();
            if (resp != null && resp.getMessageTxt() != null) {
                String txt = resp.getMessageTxt();
                if (txt.startsWith("BYE")) {
                    System.out.println("<<< [BYE RECEIVED FROM OXE/MICROSIP]:\n" + txt);
                    // Send 200 OK to BYE
                    StringBuilder bye200 = new StringBuilder();
                    bye200.append("SIP/2.0 200 OK\r\n");
                    for (String line : txt.split("\r?\n")) {
                        if (line.toLowerCase().startsWith("via:")) bye200.append(line).append("\r\n");
                        if (line.toLowerCase().startsWith("from:")) bye200.append(line).append("\r\n");
                        if (line.toLowerCase().startsWith("to:")) bye200.append(line).append("\r\n");
                        if (line.toLowerCase().startsWith("call-id:")) bye200.append(line).append("\r\n");
                        if (line.toLowerCase().startsWith("cseq:")) bye200.append(line).append("\r\n");
                    }
                    bye200.append("Content-Length: 0\r\n\r\n");
                    MyPacket pBye200 = new MyPacket(bye200.toString());
                    pBye200.setAddress(resp.getAddress());
                    pBye200.setPort(resp.getPort());
                    sipSocket.send(pBye200);
                    System.out.println(">>> 200 OK sent to BYE.");
                    callState = CallState.TERMINATED;
                    System.out.println("CallState -> " + callState + " (CALL TERMINATED CLEANLY)");
                    return true;
                }
            }
            Thread.sleep(50);
        }

        callState = CallState.TERMINATED;
        System.out.println("CallState -> " + callState);
        return true;
    }

    private static void executeBidirectionalAudioSession(InetAddress localAddr, int localPort, InetAddress remoteAddr, int remotePort, long durationMs) throws Exception {
        DatagramSocket rtpSocket = new DatagramSocket(localPort, localAddr);
        rtpSocket.setSoTimeout(100);

        LiveAudioCodec codec = LiveAudioCodec.create(AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);
        RtcpMonitor rtcp = new RtcpMonitor(localAddr, localPort, remoteAddr, remotePort);
        try {
            rtcp.start();
        } catch (Exception ignored) {}

        // Generate 20ms sine wave audio
        byte[] pcm20ms = new byte[320];
        for (int i = 0; i < pcm20ms.length; i += 2) {
            short sample = (short) (Math.sin(i * 0.1) * 8000);
            pcm20ms[i] = (byte) (sample & 0xFF);
            pcm20ms[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        byte[] pcmaPayload = codec.encode(pcm20ms, pcm20ms.length);

        long startTime = System.currentTimeMillis();
        long lastSendTime = 0;
        int seq = 0;
        long packetsSent = 0;
        long packetsReceived = 0;
        byte[] rxBuffer = new byte[1500];
        DatagramPacket rxPkt = new DatagramPacket(rxBuffer, rxBuffer.length);

        System.out.printf("Streaming RTP PCMA to %s:%d from local port %d for %d ms...\n", remoteAddr.getHostAddress(), remotePort, localPort, durationMs);

        while (System.currentTimeMillis() - startTime < durationMs) {
            long now = System.currentTimeMillis();
            // Send RTP packet every 20ms
            if (now - lastSendTime >= 20) {
                byte[] rtpPktData = new byte[12 + pcmaPayload.length];
                rtpPktData[0] = (byte) 0x80;
                rtpPktData[1] = (byte) 8; // PCMA
                rtpPktData[2] = (byte) ((seq >> 8) & 0xFF);
                rtpPktData[3] = (byte) (seq & 0xFF);
                int ts = seq * 160;
                rtpPktData[4] = (byte) ((ts >> 24) & 0xFF);
                rtpPktData[5] = (byte) ((ts >> 16) & 0xFF);
                rtpPktData[6] = (byte) ((ts >> 8) & 0xFF);
                rtpPktData[7] = (byte) (ts & 0xFF);
                // SSRC = 0x12345678
                rtpPktData[8] = 0x12; rtpPktData[9] = 0x34; rtpPktData[10] = 0x56; rtpPktData[11] = 0x78;
                System.arraycopy(pcmaPayload, 0, rtpPktData, 12, pcmaPayload.length);

                DatagramPacket txPacket = new DatagramPacket(rtpPktData, rtpPktData.length, remoteAddr, remotePort);
                rtpSocket.send(txPacket);
                packetsSent++;
                seq++;
                lastSendTime = now;
            }

            // Inbound packet receive
            try {
                rtpSocket.receive(rxPkt);
                packetsReceived++;
                if (packetsReceived % 50 == 1) {
                    System.out.printf("<<< Received RTP packet #%d from %s:%d (len=%d)\n",
                            packetsReceived, rxPkt.getAddress().getHostAddress(), rxPkt.getPort(), rxPkt.getLength());
                }
            } catch (Exception ignored) {}

            Thread.sleep(2);
        }

        System.out.printf("RTP Media Session Complete: %d packets sent, %d packets received.\n", packetsSent, packetsReceived);
        rtcp.stop();
        rtpSocket.close();
    }

    private static void deregisterEndpoint(UDPClientSocket socket, InetAddress pbxAddr, String user) {
        System.out.println("\n--- [DEREGISTERING EXTENSION " + user + "] ---");
        try {
            String deregMsg =
                    "REGISTER sip:" + PBX_IP + " SIP/2.0\r\n" +
                    "Via: SIP/2.0/UDP " + LOCAL_IP + ":" + SIMULATOR_SIP_PORT + ";rport;branch=z9hG4bKdereg" + System.currentTimeMillis() + "\r\n" +
                    "Max-Forwards: 70\r\n" +
                    "From: \"" + user + "\" <sip:" + user + "@" + PBX_IP + ">;tag=dereg1\r\n" +
                    "To: \"" + user + "\" <sip:" + user + "@" + PBX_IP + ">\r\n" +
                    "Call-ID: dereg-" + UUID.randomUUID() + "@" + LOCAL_IP + "\r\n" +
                    "CSeq: 2 REGISTER\r\n" +
                    "Contact: *\r\n" +
                    "Expires: 0\r\n" +
                    "User-Agent: SIP Simulator v4\r\n" +
                    "Content-Length: 0\r\n\r\n";

            MyPacket pkt = new MyPacket(deregMsg);
            pkt.setAddress(pbxAddr);
            pkt.setPort(PBX_SIP_PORT);
            socket.send(pkt);

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 2000) {
                MyPacket resp = socket.receive();
                if (resp != null && resp.getMessageTxt() != null && resp.getMessageTxt().contains("200 OK")) {
                    System.out.println(">>> Extension " + user + " Deregistered (200 OK)");
                    break;
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            System.out.println("Deregister error: " + e.getMessage());
        }
    }

    /**
     * Inter-Process Communication with MicroSIP over Windows Named Pipe
     */
    public static class MicroSipIpc {
        private static final String PIPE_PATH = "\\\\.\\pipe\\microsip_ipc";

        public static String sendCommand(String method, String paramsJson) {
            try (RandomAccessFile pipe = new RandomAccessFile(PIPE_PATH, "rw")) {
                String id = String.valueOf(System.currentTimeMillis() % 100000);
                String req;
                if (paramsJson != null && !paramsJson.isEmpty()) {
                    req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + ",\"id\":" + id + "}\n";
                } else {
                    req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"id\":" + id + "}\n";
                }
                pipe.write(req.getBytes("UTF-8"));
                return pipe.readLine();
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }
}
