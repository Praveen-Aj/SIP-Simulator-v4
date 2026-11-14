package sipinspector.Utils;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.Set;
import sipinspector.GlobalSettings;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.ScenarioEntries.AssertDtmfEntry;
import sipinspector.ScenarioEntries.AssertPtEntry;
import sipinspector.OperationTabs.ScenarioValuesPanel;
import java.lang.reflect.Method;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.MyPacket;
import sipinspector.CallDB;

public class AssertionUnitTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("       SIP SIMULATOR MIDDLE-TIER UNIT TESTS       ");
        System.out.println("=================================================");

        // Initialize GlobalSettings to prevent NullPointerExceptions in Swing components
        new GlobalSettings();

        runTest("Test PcapConverter Arrow Mapping (Mock PCAP)", AssertionUnitTest::testPcapConverterArrows);
        runTest("Test Scenario Entry Parser for Assertions", AssertionUnitTest::testAssertionParsing);
        runTest("Test Scenario Validation with Standard and Short Arrows", AssertionUnitTest::testScenarioValidationAlignment);
        runTest("Test Dual-Format Log Parsing & Timestamp Stripping", AssertionUnitTest::testLogParsing);
        runTest("Test Account Username Substitution & Fallback", AssertionUnitTest::testAccountUsernameFallback);
        runTest("Test Unexpected NOTIFY Auto 200 OK Response", AssertionUnitTest::testUnexpectedNotifyAutoResponse);
        runTest("Test Socket Simulation Helper (Loss/Drop/Corruption)", AssertionUnitTest::testSocketSimulationHelper);
        runTest("Test RTPSenderThread Metrics Collection (PCAP RTP)", AssertionUnitTest::testRtpSenderMetrics);

        System.out.println("=================================================");
        System.out.println("TEST RUN SUMMARY: " + testsPassed + " / " + testsRun + " passed.");
        System.out.println("=================================================");

        if (testsPassed == testsRun) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    private static void runTest(String name, RunnableTest test) {
        testsRun++;
        System.out.print("Running: " + name + "... ");
        try {
            test.run();
            testsPassed++;
            System.out.println("PASS");
        } catch (Throwable e) {
            System.out.println("FAIL");
            System.err.println("  Reason: " + e.getMessage());
            e.printStackTrace();
        }
    }

    interface RunnableTest {
        void run() throws Exception;
    }

    private static void testPcapConverterArrows() throws Exception {
        File mockPcapFile = new File("pcap/temp_mock_unit_test.pcap");
        
        String sipMsg = "REGISTER sip:192.168.1.100 SIP/2.0\r\n" +
                        "Via: SIP/2.0/UDP 192.168.1.10:5060;branch=z9hG4bK-abc\r\n" +
                        "From: <sip:70200@192.168.1.100>;tag=123\r\n" +
                        "To: <sip:70200@192.168.1.100>\r\n" +
                        "Call-ID: test-call-id-12345@192.168.1.10\r\n" +
                        "CSeq: 1 REGISTER\r\n\r\n";
                        
        createMockPcap(mockPcapFile, sipMsg);

        try {
            // Run conversion
            Map<String, String> results = PcapConverter.convert(mockPcapFile, null, "test_output");
            String scenarioContent = results.get("scenario");

            if (scenarioContent == null || scenarioContent.trim().isEmpty()) {
                throw new Exception("Generated scenario is empty!");
            }

            // Verify arrow outputs in generated scenario are standard
            if (scenarioContent.contains("u->") || scenarioContent.contains("u<-")) {
                throw new Exception("Generated scenario contains deprecated u-> or u<- short arrows! Expected standard arrows.");
            }

            if (!scenarioContent.contains("---------->") && !scenarioContent.contains("<----------")) {
                throw new Exception("Generated scenario does not contain standard direction arrows (----------> or <----------)!");
            }
        } finally {
            if (mockPcapFile.exists()) {
                mockPcapFile.delete();
            }
        }
    }

    private static void createMockPcap(File file, String sipMsg) throws Exception {
        byte[] sipBytes = sipMsg.getBytes("UTF-8");
        
        java.io.DataOutputStream dos = new java.io.DataOutputStream(new java.io.FileOutputStream(file));
        
        // 1. Global Header (24 bytes)
        dos.writeInt(0xa1b2c3d4); // Magic number
        dos.writeShort(2);        // Major version
        dos.writeShort(4);        // Minor version
        dos.writeInt(0);          // Timezone
        dos.writeInt(0);          // Sigfigs
        dos.writeInt(65535);      // Snaplen
        dos.writeInt(1);          // LinkType (Ethernet)
        
        // 2. Packet Record Header (16 bytes)
        int ipLen = 20 + 8 + sipBytes.length;
        int packetLen = 14 + ipLen;
        
        dos.writeInt((int)(System.currentTimeMillis() / 1000)); // ts_sec
        dos.writeInt(0); // ts_usec
        dos.writeInt(packetLen); // incl_len
        dos.writeInt(packetLen); // orig_len
        
        // 3. Ethernet Header (14 bytes)
        dos.write(new byte[12]); // Dummy MACs
        dos.writeShort(0x0800);  // EtherType IPv4
        
        // 4. IPv4 Header (20 bytes)
        dos.writeByte(0x45); // Version & IHL
        dos.writeByte(0);    // DSCP/ECN
        dos.writeShort(ipLen); // Total length
        dos.writeShort(0);     // Identification
        dos.writeShort(0);     // Flags & Fragment Offset
        dos.writeByte(64);     // TTL
        dos.writeByte(17);     // Protocol (UDP)
        dos.writeShort(0);     // Checksum
        dos.write(new byte[]{127, 0, 0, 1}); // Source IP
        dos.write(new byte[]{127, 0, 0, 2}); // Dest IP
        
        // 5. UDP Header (8 bytes)
        dos.writeShort(5060); // Source Port
        dos.writeShort(5060); // Dest Port
        dos.writeShort(8 + sipBytes.length); // Length
        dos.writeShort(0);    // Checksum
        
        // 6. SIP Payload
        dos.write(sipBytes);
        
        dos.close();
    }

    private static void testAssertionParsing() throws Exception {
        // Test parsing cmd_AssertDtmf=5
        String dtmfCmd1 = "cmd_AssertDtmf=5";
        ScenarioEntry entry1 = ScenarioEntry.parse(dtmfCmd1);
        if (entry1 == null || entry1.getType() != ScenarioEntry.TYPE.ASSERT_DTMF) {
            throw new Exception("Failed to parse command line: " + dtmfCmd1);
        }
        if (!entry1.getTreeText().contains("Assert DTMF=5")) {
            throw new Exception("AssertDtmfEntry has wrong tree text: " + entry1.getTreeText());
        }

        // Test alternative syntax assert_dtmf: 9
        String dtmfCmd2 = "assert_dtmf: 9";
        ScenarioEntry entry2 = ScenarioEntry.parse(dtmfCmd2);
        if (entry2 == null || entry2.getType() != ScenarioEntry.TYPE.ASSERT_DTMF) {
            throw new Exception("Failed to parse command line: " + dtmfCmd2);
        }

        // Test parsing cmd_AssertPt=8
        String ptCmd1 = "cmd_AssertPt=8";
        ScenarioEntry entry3 = ScenarioEntry.parse(ptCmd1);
        if (entry3 == null || entry3.getType() != ScenarioEntry.TYPE.ASSERT_PT) {
            throw new Exception("Failed to parse command line: " + ptCmd1);
        }
        if (!entry3.getTreeText().contains("Assert PT=8")) {
            throw new Exception("AssertPtEntry has wrong tree text: " + entry3.getTreeText());
        }
    }

    @SuppressWarnings("unchecked")
    private static void testScenarioValidationAlignment() throws Exception {
        // Construct a dummy scenario using standard arrows
        String scenarioText = 
            "// Standard scenario\n" +
            "---------->\n" +
            "INVITE sip:[remote_ip] SIP/2.0\n" +
            "Call-ID: [call_id]\n" +
            "\n" +
            "<----------*\n" +
            "180 Ringing\n" +
            "\n" +
            "<----------\n" +
            "200 OK\n\n";

        // Invoke private validateScenario method using reflection
        ScenarioValuesPanel panel = new ScenarioValuesPanel(null, null);
        Method validateMethod = ScenarioValuesPanel.class.getDeclaredMethod("validateScenario", String.class);
        validateMethod.setAccessible(true);
        
        List<String> warnings = (List<String>) validateMethod.invoke(panel, scenarioText);
        if (warnings != null && !warnings.isEmpty()) {
            throw new Exception("Standard scenario triggered validation warnings: " + warnings);
        }

        // Construct a dummy scenario using u->/u<- arrows to check compatibility
        String shortArrowText =
            "u->\n" +
            "INVITE sip:[remote_ip] SIP/2.0\n" +
            "Call-ID: [call_id]\n" +
            "\n" +
            "u<-\n" +
            "200 OK\n\n";

        List<String> shortWarnings = (List<String>) validateMethod.invoke(panel, shortArrowText);
        if (shortWarnings != null && !shortWarnings.isEmpty()) {
            throw new Exception("Short arrow scenario triggered validation warnings: " + shortWarnings);
        }
    }

    private static void testLogParsing() throws Exception {
        // Set IP settings programmatically for deterministic normalization
        GlobalSettings.setLocalIP("192.168.1.100");
        GlobalSettings.setRemoteIPAddr("192.168.0.70");

        // Test standard clean SIP log format
        String cleanLog = 
            "From:192.168.1.100:5060 - To:192.168.0.70:5060\n" +
            "---------->\n" +
            "REGISTER sip:192.168.0.70 SIP/2.0\n" +
            "Call-ID: clean-call-id-123\n" +
            "CSeq: 1 REGISTER\n" +
            "\n" +
            "----------\n";
            
        File cleanLogFile = new File(AppPaths.getLogsDir(), "temp_clean_unit_test.log");
        try (java.io.FileWriter fw = new java.io.FileWriter(cleanLogFile)) {
            fw.write(cleanLog);
        }
        
        try {
            List<sipinspector.UI.SipLadderPanel.SipMessage> msgs = sipinspector.UI.SipLadderPanel.parseLog(cleanLogFile);
            if (msgs.size() != 1) {
                throw new Exception("Expected 1 parsed message in clean log, got " + msgs.size());
            }
            sipinspector.UI.SipLadderPanel.SipMessage msg = msgs.get(0);
            if (!msg.source.equals("SIP Simulator(LOCAL)") || !msg.target.equals("192.168.0.70(Remote)")) {
                throw new Exception("Clean log parsed wrong source/target: source=" + msg.source + ", target=" + msg.target);
            }
            if (!msg.direction.equals("out")) {
                throw new Exception("Clean log parsed wrong direction: " + msg.direction);
            }
            if (!msg.callId.equals("clean-call-id-123") || !msg.cseq.equals("1 REGISTER")) {
                throw new Exception("Clean log parsed wrong call-id or cseq");
            }
        } finally {
            if (cleanLogFile.exists()) cleanLogFile.delete();
        }

        // Test debug log format with timestamps
        String debugLog = 
            "2026-06-21 08:54:57.190 === UDP SEND DEBUG ===\n" +
            "2026-06-21 08:54:57.190 To: /192.168.0.70:5060\n" +
            "2026-06-21 08:54:57.190 Packet length: 947 bytes\n" +
            "2026-06-21 08:54:57.190 --- SIP MESSAGE START ---\n" +
            "2026-06-21 08:54:57.190 REGISTER sip:192.168.0.70 SIP/2.0\n" +
            "2026-06-21 08:54:57.190 Call-ID: debug-call-id-456\n" +
            "2026-06-21 08:54:57.190 CSeq: 2 REGISTER\n" +
            "2026-06-21 08:54:57.190 \n" +
            "2026-06-21 08:54:57.190 --- SIP MESSAGE END ---\n";

        File debugLogFile = new File(AppPaths.getLogsDir(), "temp_debug_unit_test.log");
        try (java.io.FileWriter fw = new java.io.FileWriter(debugLogFile)) {
            fw.write(debugLog);
        }
        
        try {
            List<sipinspector.UI.SipLadderPanel.SipMessage> msgs = sipinspector.UI.SipLadderPanel.parseLog(debugLogFile);
            if (msgs.size() != 1) {
                throw new Exception("Expected 1 parsed message in debug log, got " + msgs.size());
            }
            sipinspector.UI.SipLadderPanel.SipMessage msg = msgs.get(0);
            if (!msg.source.equals("SIP Simulator(LOCAL)") || !msg.target.equals("192.168.0.70(Remote)")) {
                throw new Exception("Debug log parsed wrong source/target: source=" + msg.source + ", target=" + msg.target);
            }
            if (!msg.direction.equals("out")) {
                throw new Exception("Debug log parsed wrong direction: " + msg.direction);
            }
            if (!msg.callId.equals("debug-call-id-456") || !msg.cseq.equals("2 REGISTER")) {
                throw new Exception("Debug log parsed wrong call-id or cseq: callId=" + msg.callId + ", cseq=" + msg.cseq);
            }
            // Check that the timestamp is stripped from headers raw text
            if (msg.rawText.contains("2026-06-21")) {
                throw new Exception("Debug log raw text still contains timestamps:\n" + msg.rawText);
            }
        } finally {
            if (debugLogFile.exists()) debugLogFile.delete();
        }
    }

    private static void testAccountUsernameFallback() throws Exception {
        new GlobalSettings();
        GlobalSettings.setUsername("70201");
        
        ValuesLoader loader = new ValuesLoader();
        
        // Test field0 with default '1000' -> should override with '70201'
        SIValue[] rowWithDefault = new SIValue[]{ new SIValue("1000"), new SIValue("192.168.1.100") };
        String res1 = loader.getFieldValue(rowWithDefault, "[field0]");
        if (!res1.equals("70201")) {
            throw new Exception("Expected fallback to account username '70201' for field0 default '1000', got: " + res1);
        }
        
        // Test field0 with custom '3000' in single-run scenario (list is null/empty) -> should override with '70201'
        SIValue[] rowWithCustomSingle = new SIValue[]{ new SIValue("3000"), new SIValue("192.168.1.100") };
        String res2 = loader.getFieldValue(rowWithCustomSingle, "[field0]");
        if (!res2.equals("70201")) {
            throw new Exception("Expected fallback to account username '70201' for field0 custom value in single-run scenario, got: " + res2);
        }
        
        // Test field1 (should not override)
        String res3 = loader.getFieldValue(rowWithDefault, "[field1]");
        if (!res3.equals("192.168.1.100")) {
            throw new Exception("Expected field1 value to remain '192.168.1.100', got: " + res3);
        }

        // Test field0 with custom edit '70202' -> should preserve the edited value and NOT fallback to '70201'
        java.util.Map<String, String> fieldUpdates = new java.util.HashMap<>();
        fieldUpdates.put("field0", "70202");
        loader.updateCurrentRowValues(fieldUpdates);
        SIValue[] rowWithEdited = new SIValue[]{ new SIValue("70202"), new SIValue("192.168.1.100") };
        String resEdited = loader.getFieldValue(rowWithEdited, "[field0]");
        if (!resEdited.equals("70202")) {
            throw new Exception("Expected manually edited field0 value '70202' to be preserved, got: " + resEdited);
        }
    }

    static class TestSocket extends MySocketAbstract {
        MyPacket sentPacket = null;
        @Override public boolean open(java.net.InetAddress addr, int p) { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public Object getSocket() { return null; }
        @Override public java.nio.channels.Selector getSelector() { return null; }
        @Override public void close() {}
        @Override public java.net.InetAddress getLocalAddress() {
            try { return java.net.InetAddress.getByName("192.168.0.10"); } catch(Exception e) { return null; }
        }
        @Override public int getLocalPort() { return 5060; }
        @Override public MyPacket receive() { return null; }
        @Override public void send(MyPacket packet) {
            sentPacket = packet;
        }
        @Override public String getTransportType() { return "UDP"; }
        @Override public boolean isTransportUDP() { return true; }
        @Override public java.nio.channels.SelectionKey getInitialKey() { return null; }
        @Override public boolean isDaemon() { return false; }
        @Override public void setQos() {}
    }

    private static void testUnexpectedNotifyAutoResponse() throws Exception {
        new GlobalSettings();
        
        TestSocket testSocket = new TestSocket();
        CallDB callDB = new CallDB(20, 10, 0);
        
        // Bypassing GUI dialog with test constructor
        java.lang.reflect.Constructor<sipinspector.Threads.ReceiverThread> testCtor = 
            sipinspector.Threads.ReceiverThread.class.getDeclaredConstructor(CallDB.class, MySocketAbstract.class);
        testCtor.setAccessible(true);
        sipinspector.Threads.ReceiverThread thread = testCtor.newInstance(callDB, testSocket);
        
        // Mock incoming NOTIFY request
        String notifyMsg = 
            "NOTIFY sip:70201@192.168.0.10:5060;transport=UDP SIP/2.0\r\n" +
            "Via: SIP/2.0/UDP 192.168.0.70;branch=z9hG4bK43a1707d73ada6229f8d43e16719b358\r\n" +
            "From: sip:70201@telnet70:5060;tag=5ea78025e3a9966462c9ee6ffe8ed071\r\n" +
            "To: sip:70201@telnet70:5060\r\n" +
            "Call-ID: 251ada59a83159627234f473a1386c34@192.168.0.70\r\n" +
            "CSeq: 161634749 NOTIFY\r\n" +
            "Content-Length: 0\r\n\r\n";
            
        MyPacket incomingPacket = new MyPacket(notifyMsg);
        incomingPacket.setAddress(java.net.InetAddress.getByName("192.168.0.70"));
        incomingPacket.setPort(5060);
        
        // Invoke handleIncomingMsgs via reflection
        java.lang.reflect.Method handleMethod = 
            sipinspector.Threads.ReceiverThread.class.getDeclaredMethod("handleIncomingMsgs", MyPacket.class);
        handleMethod.setAccessible(true);
        Object callResult = handleMethod.invoke(thread, incomingPacket);
        
        // Verify that the callResult is null (meaning it was intercepted and auto-responded)
        if (callResult != null) {
            throw new Exception("Expected handleIncomingMsgs to return null for unexpected NOTIFY, but got a Call object!");
        }
        
        // Verify that the mock socket received the 200 OK
        if (testSocket.sentPacket == null) {
            throw new Exception("No automatic response was sent through the socket!");
        }
        
        String sentMsg = testSocket.sentPacket.getMessageTxt();
        if (!sentMsg.startsWith("SIP/2.0 200 OK\r\n")) {
            throw new Exception("Expected sent packet to be 200 OK, but got: " + sentMsg);
        }
        
        // Verify the response copies headers correctly
        if (!sentMsg.contains("Call-ID: 251ada59a83159627234f473a1386c34@192.168.0.70\r\n")) {
            throw new Exception("Auto 200 OK lacks matching Call-ID!");
        }
        if (!sentMsg.contains("CSeq: 161634749 NOTIFY\r\n")) {
            throw new Exception("Auto 200 OK lacks matching CSeq!");
        }
        if (!sentMsg.contains("Via: SIP/2.0/UDP 192.168.0.70;branch=z9hG4bK43a1707d73ada6229f8d43e16719b358\r\n")) {
            throw new Exception("Auto 200 OK lacks matching Via!");
        }
        if (!sentMsg.contains("tag=")) {
            throw new Exception("Auto 200 OK To header lacks generated tag!");
        }
    }

    private static void testSocketSimulationHelper() throws Exception {
        new GlobalSettings();
        
        // 1. Test corruption
        GlobalSettings.setSetting("sim.malformed", "CSeq");
        MyPacket pkt = new MyPacket("REGISTER sip:test SIP/2.0\r\nCSeq: 42 REGISTER\r\n\r\n");
        pkt.setAddress(java.net.InetAddress.getByName("127.0.0.1"));
        pkt.setPort(5060);
        MyPacket corrupted = sipinspector.Socket.SocketSimulationHelper.corruptPacketIfNeeded(pkt);
        String corruptedText = corrupted.getMessageTxt();
        if (!corruptedText.contains("CSeq: -9999")) {
            throw new Exception("Expected CSeq to be corrupted to -9999, but got:\n" + corruptedText);
        }

        GlobalSettings.setSetting("sim.malformed", "Expires");
        pkt = new MyPacket("REGISTER sip:test SIP/2.0\r\nExpires: 3600\r\n\r\n");
        pkt.setAddress(java.net.InetAddress.getByName("127.0.0.1"));
        pkt.setPort(5060);
        corrupted = sipinspector.Socket.SocketSimulationHelper.corruptPacketIfNeeded(pkt);
        corruptedText = corrupted.getMessageTxt();
        if (!corruptedText.contains("Expires: invalid_number")) {
            throw new Exception("Expected Expires to be corrupted, but got:\n" + corruptedText);
        }

        // 2. Test drop
        GlobalSettings.setSetting("sim.loss", "100");
        boolean dropped = sipinspector.Socket.SocketSimulationHelper.simulateIncoming(java.net.InetAddress.getByName("127.0.0.1"), 5060, "UDP");
        if (!dropped) {
            throw new Exception("Expected packet to be dropped under 100% loss setting!");
        }

        GlobalSettings.setSetting("sim.loss", "0");
        dropped = sipinspector.Socket.SocketSimulationHelper.simulateIncoming(java.net.InetAddress.getByName("127.0.0.1"), 5060, "UDP");
        if (dropped) {
            throw new Exception("Expected packet NOT to be dropped under 0% loss setting!");
        }

        // Clean up settings
        GlobalSettings.setSetting("sim.malformed", "None");
        GlobalSettings.setSetting("sim.loss", "0");
        GlobalSettings.setSetting("sim.delay", "0");
    }

    private static void testRtpSenderMetrics() throws Exception {
        sipinspector.Threads.RTPSenderThread rtpSender = new sipinspector.Threads.RTPSenderThread();
        
        // Mock a 12-byte RTP header
        byte[] rtpHeader = new byte[12];
        rtpHeader[0] = (byte) 0x80;
        rtpHeader[1] = (byte) 0x08; // PT 8
        rtpHeader[2] = 0x04;
        rtpHeader[3] = (byte) 0xD2; // Seq 1234
        // Timestamp
        rtpHeader[4] = 0; rtpHeader[5] = 0; rtpHeader[6] = 0x16; rtpHeader[7] = 0x2E;
        // SSRC
        rtpHeader[8] = 0x12;
        rtpHeader[9] = 0x34;
        rtpHeader[10] = 0x56;
        rtpHeader[11] = 0x78;

        rtpSender.recordSentPacket(rtpHeader, 0, rtpHeader.length);

        if (rtpSender.getPacketsSent() != 1) {
            throw new Exception("Expected packets sent to be 1, got " + rtpSender.getPacketsSent());
        }
        if (rtpSender.getBytesSent() != 12) {
            throw new Exception("Expected bytes sent to be 12, got " + rtpSender.getBytesSent());
        }
        if (rtpSender.getPayloadType() != 8) {
            throw new Exception("Expected payload type to be 8, got " + rtpSender.getPayloadType());
        }
        if (rtpSender.getSsrc() != 0x12345678) {
            throw new Exception("Expected SSRC to be 0x12345678, got " + String.format("0x%08X", rtpSender.getSsrc()));
        }
    }
}
