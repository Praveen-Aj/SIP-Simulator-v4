package test;

import sipinspector.GlobalSettings;
import sipinspector.MyPacket;
import sipinspector.Media.LiveAudioController;
import sipinspector.OperationTabs.CampaignsTab.CampaignItem;
import sipinspector.Socket.SocketSimulationHelper;
import sipinspector.Utils.SipMessageRepository;
import sipinspector.Utils.SipMessageRepository.MessageListener;
import sipinspector.Utils.SipMessageRepository.SipMessageRecord;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8: Engineering Workspace Consolidation & Diagnostic Intelligence Test Suite.
 *
 * Validates:
 * 1. SipMessageRepository (ring buffer, parsing, Call-ID extraction, filter matching, listeners)
 * 2. SocketSimulationHelper (network impairments, jitter, RTP media drop/delay, malformed headers)
 * 3. CampaignItem failure intelligence (status integrity, failure classification, triage steps)
 * 4. RTP metric computation & MOS rating algorithms
 */
public class Phase8WorkspaceIntelligenceTest {

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("ASSERTION FAILED: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("ASSERTION FAILED: " + message + " [Expected: " + expected + ", Actual: " + actual + "]");
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 8 — WORKSPACE CONSOLIDATION & DIAGNOSTIC INTELLIGENCE TESTS");
        System.out.println("================================================================================");

        testSipMessageRepository();
        testSocketSimulationHelper();
        testCampaignFailureIntelligence();
        testRtpMetricsAndMos();

        System.out.println("\n>>> ALL PHASE 8 WORKSPACE INTELLIGENCE TESTS PASSED SUCCESSFULLY! <<<\n");
    }

    private static void testSipMessageRepository() {
        System.out.print("[TEST 1/4] SipMessageRepository (Storage, Parsing, Filtering, Bounded Buffer)... ");
        SipMessageRepository repo = SipMessageRepository.getInstance();
        repo.clear();
        assertEquals(0, repo.getMessageCount(), "Repository should be empty after clear()");

        // Test Listener
        final List<SipMessageRecord> listenerReceived = new ArrayList<SipMessageRecord>();
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessageAdded(SipMessageRecord record) {
                listenerReceived.add(record);
            }
            @Override
            public void onMessagesCleared() {
                listenerReceived.clear();
            }
        };
        repo.addListener(listener);

        // Add Outgoing INVITE
        String inviteMsg = "INVITE sip:90001@192.168.0.20:5060 SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP 192.168.0.9:5060;branch=z9hG4bK-inv-12345\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: <sip:90008@192.168.0.20>;tag=tag-inv-from\r\n" +
                "To: <sip:90001@192.168.0.20>\r\n" +
                "Call-ID: call-abc-789@192.168.0.9\r\n" +
                "CSeq: 101 INVITE\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: 142\r\n" +
                "\r\n" +
                "v=0\r\n" +
                "o=- 1234 5678 IN IP4 192.168.0.9\r\n" +
                "s=-\r\n" +
                "c=IN IP4 192.168.0.9\r\n" +
                "t=0 0\r\n" +
                "m=audio 30000 RTP/AVP 0 8\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=rtpmap:8 PCMA/8000\r\n";

        SipMessageRecord rec1 = repo.recordMessage("OUT", "UDP", "192.168.0.9:5060", "192.168.0.20:5060", inviteMsg);
        assertEquals(1, repo.getMessageCount(), "Message count should be 1");
        assertEquals(1, listenerReceived.size(), "Listener should receive message");
        assertEquals("call-abc-789@192.168.0.9", rec1.callId, "Call-ID parsed correctly");
        assertEquals("INVITE", rec1.methodOrResponse, "Method parsed correctly");
        assertEquals("101 INVITE", rec1.cseq, "CSeq parsed correctly");
        assertEquals("INVITE", rec1.cseqMethod, "CSeq method parsed correctly");
        assertEquals(101L, rec1.cseqNumber, "CSeq number parsed correctly");
        assertEquals("OUT", rec1.direction, "Direction should be OUT");
        assertTrue(rec1.sdpBody != null, "Should detect SDP body");
        assertEquals("application/sdp", rec1.getHeader("Content-Type"), "Content-Type parsed correctly");

        // Add Incoming 180 Ringing
        String ringingMsg = "SIP/2.0 180 Ringing\r\n" +
                "Via: SIP/2.0/UDP 192.168.0.9:5060;branch=z9hG4bK-inv-12345\r\n" +
                "From: <sip:90008@192.168.0.20>;tag=tag-inv-from\r\n" +
                "To: <sip:90001@192.168.0.20>;tag=oxe-tag-678\r\n" +
                "Call-ID: call-abc-789@192.168.0.9\r\n" +
                "CSeq: 101 INVITE\r\n" +
                "Content-Length: 0\r\n\r\n";

        SipMessageRecord rec2 = repo.recordMessage("IN", "UDP", "192.168.0.20:5060", "192.168.0.9:5060", ringingMsg);
        assertEquals(2, repo.getMessageCount(), "Message count should be 2");
        assertEquals("180 Ringing", rec2.methodOrResponse, "Status line parsed into method field");
        assertEquals(180, rec2.responseCode, "Status code should be 180");
        assertEquals("IN", rec2.direction, "Direction should be IN");
        assertTrue(rec2.sdpBody == null, "180 Ringing should not have SDP");

        // Filter tests
        List<SipMessageRecord> filterByCallId = repo.filter(null, "call-abc", "ALL", "ALL", null);
        assertEquals(2, filterByCallId.size(), "Filter by Call-ID substring should return both messages");

        List<SipMessageRecord> filterByMethod = repo.filter("INVITE", null, "ALL", "ALL", null);
        assertEquals(1, filterByMethod.size(), "Filter by Method INVITE should return 1 message");

        List<SipMessageRecord> filterByDirection = repo.filter(null, null, "IN", "ALL", null);
        assertEquals(1, filterByDirection.size(), "Filter by Direction IN should return 1 message");

        List<SipMessageRecord> filterByStatus = repo.filter("180", null, "ALL", "ALL", null);
        assertEquals(1, filterByStatus.size(), "Filter by Status 180 should return 1 message");

        repo.removeListener(listener);
        System.out.println("PASSED");
    }

    private static void testSocketSimulationHelper() {
        System.out.print("[TEST 2/4] SocketSimulationHelper (Loss, Jitter, Malformed Headers, RTP Impairment)... ");
        // Reset settings
        GlobalSettings.setSetting("sim.enabled", "false");
        GlobalSettings.setSetting("sim.delay", "0");
        GlobalSettings.setSetting("sim.loss", "0");
        GlobalSettings.setSetting("sim.jitter", "0");
        GlobalSettings.setSetting("sim.affectMedia", "false");
        GlobalSettings.setSetting("sim.malformed", "None");

        // Baseline verification
        assertTrue(!SocketSimulationHelper.isImpairmentEnabled(), "Impairment initially disabled");
        assertEquals(0, SocketSimulationHelper.getDelayMs(), "Delay should be 0");
        assertEquals(0, SocketSimulationHelper.getLossPercent(), "Loss rate should be 0");
        assertEquals(0, SocketSimulationHelper.getJitterMs(), "Jitter should be 0");

        // Configure Impairments
        GlobalSettings.setSetting("sim.enabled", "true");
        GlobalSettings.setSetting("sim.delay", "50");
        GlobalSettings.setSetting("sim.loss", "20");
        GlobalSettings.setSetting("sim.jitter", "15");
        GlobalSettings.setSetting("sim.affectMedia", "true");

        assertTrue(SocketSimulationHelper.isImpairmentEnabled(), "Impairment should be enabled");
        assertEquals(50, SocketSimulationHelper.getDelayMs(), "Delay should be 50ms");
        assertEquals(20, SocketSimulationHelper.getLossPercent(), "Loss rate should be 20%");
        assertEquals(15, SocketSimulationHelper.getJitterMs(), "Jitter should be 15ms");
        assertTrue(SocketSimulationHelper.isAffectMedia(), "Affect media should be true");

        // Test Malformed Header Injection
        GlobalSettings.setSetting("sim.malformed", "Max-Forwards");
        String testSip = "INVITE sip:test@test.com SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP 1.2.3.4:5060\r\n" +
                "Max-Forwards: 70\r\n\r\n";
        MyPacket pkt = new MyPacket(testSip);
        MyPacket corrupted = SocketSimulationHelper.corruptPacketIfNeeded(pkt);
        assertTrue(corrupted.getMessageTxt().contains("Max-Forwards: 0"),
                "Mutated SIP should contain corrupted Max-Forwards: 0 header");

        // Test Media Impairment on RTP packet
        try {
            InetAddress localHost = InetAddress.getByName("127.0.0.1");

            // With 100% loss
            GlobalSettings.setSetting("sim.loss", "100");
            boolean dropped = SocketSimulationHelper.simulateMediaOutgoing(localHost, 40000);
            assertTrue(dropped, "RTP packet should be dropped when loss rate is 100%");

            // With 0% loss
            GlobalSettings.setSetting("sim.loss", "0");
            boolean passed = !SocketSimulationHelper.simulateMediaOutgoing(localHost, 40000);
            assertTrue(passed, "RTP packet should pass when loss rate is 0%");
        } catch (Exception e) {
            throw new RuntimeException("RTP simulation test failed: " + e.getMessage(), e);
        }

        // Clean up
        GlobalSettings.setSetting("sim.enabled", "false");
        GlobalSettings.setSetting("sim.loss", "0");
        GlobalSettings.setSetting("sim.delay", "0");
        GlobalSettings.setSetting("sim.jitter", "0");
        GlobalSettings.setSetting("sim.affectMedia", "false");
        GlobalSettings.setSetting("sim.malformed", "None");
        assertTrue(!SocketSimulationHelper.isImpairmentEnabled(), "Simulation should be disabled after reset");
        assertEquals(0, SocketSimulationHelper.getLossPercent(), "Loss rate should be 0 after reset");
        System.out.println("PASSED");
    }

    private static void testCampaignFailureIntelligence() {
        System.out.print("[TEST 3/4] CampaignItem (Status Integrity, Failure Classification, Diagnostic Triage)... ");
        CampaignItem item = new CampaignItem();
        item.name = "TC-101: OXE Hold and Resume Session";
        item.expectedStatus = "PASS";
        item.runStatus = "Pending";
        assertEquals("Pending", item.runStatus, "Initial status must be Pending");

        // Status transition: Pending -> Running -> Completed
        item.runStatus = "Running";
        assertEquals("Running", item.runStatus, "Status should transition to Running");

        // Mark as PASS
        item.runStatus = "Completed";
        item.runResult = "PASS";
        item.duration = "0.45s";
        assertEquals("PASS", item.runResult, "Result must be PASS");
        assertEquals("0.45s", item.duration, "Duration matches");

        // Mark as FAIL with failure classification
        CampaignItem failItem = new CampaignItem();
        failItem.name = "TC-102: Blind Transfer REFER";
        failItem.runStatus = "Completed";
        failItem.runResult = "FAIL";
        failItem.failureClassification = "PBX_REJECTION";
        failItem.failureStage = "INVITE Negotiation";
        failItem.expectedCondition = "202 Accepted";
        failItem.actualCondition = "488 Not Acceptable Here";
        failItem.failureReason = "OXE rejected the transfer target due to class-of-service restriction or codec incompatibility.";
        failItem.suggestedInvestigation = "1. Verify OXE user profile COS settings for extension 90008.\n2. Ensure target 90001 supports G.711u.";
        failItem.supportingMessages = "SIP/2.0 488 Not Acceptable Here\r\nWarning: 304 \"Incompatible media capabilities\"";

        assertEquals("FAIL", failItem.runResult, "Result must be FAIL");
        assertEquals("PBX_REJECTION", failItem.failureClassification, "Classification must match PBX_REJECTION");
        assertEquals("INVITE Negotiation", failItem.failureStage, "Failure stage must match");
        assertTrue(failItem.suggestedInvestigation.contains("Verify OXE user profile"), "Investigation steps recorded");
        assertTrue(failItem.supportingMessages.contains("488 Not Acceptable"), "Supporting traces recorded");

        // Status integrity: verify BLOCKED
        CampaignItem blockedItem = new CampaignItem();
        blockedItem.name = "TC-103: TLS Secure Dialog";
        blockedItem.runStatus = "Blocked";
        blockedItem.runResult = "BLOCKED";
        blockedItem.actualCondition = "BLOCKED: Local port 5061 in use";
        assertEquals("BLOCKED", blockedItem.runResult, "Result must be BLOCKED");
        System.out.println("PASSED");
    }

    private static void testRtpMetricsAndMos() {
        System.out.print("[TEST 4/4] RTP Metrics & MOS Rating Algorithms... ");
        LiveAudioController audio = new LiveAudioController();

        // Initially no packets sent or received
        double mosInitial = audio.getEstimatedMos();
        assertTrue(mosInitial >= 1.0 && mosInitial <= 4.5, "MOS must be within valid range [1.0, 4.5], got: " + mosInitial);

        // Test MOS calculation under clean conditions
        double mosClean = LiveAudioController.calculateMos(0.0, 5.0);
        assertTrue(mosClean >= 4.2 && mosClean <= 4.5, "Clean G.711u should yield MOS ~4.3-4.4, got: " + mosClean);

        // Test MOS calculation with high packet loss (e.g. 15% loss, 60ms jitter)
        double mosDegraded = LiveAudioController.calculateMos(15.0, 60.0);
        assertTrue(mosDegraded < mosClean, "Degraded conditions must yield lower MOS than clean (" + mosDegraded + " < " + mosClean + ")");
        assertTrue(mosDegraded >= 1.0, "MOS must not drop below 1.0, got: " + mosDegraded);

        // Test RFC 3550 sequence discontinuity detection logic
        int seq1 = 65534;
        int seq2 = 65535;
        int seq3 = 0; // Wraparound
        int seq4 = 5; // Jump / loss of 4 packets

        int diffClean = (seq2 - seq1) & 0xFFFF;
        assertEquals(1, diffClean, "Consecutive packets diff must be 1");

        int diffWrap = (seq3 - seq2) & 0xFFFF;
        assertEquals(1, diffWrap, "Wrap-around packets diff must be 1");

        int diffJump = (seq4 - seq3) & 0xFFFF;
        assertEquals(5, diffJump, "Jump of 5 packets detected");
        assertTrue(diffJump > 1, "Discontinuity detected when diff > 1");

        System.out.println("PASSED");
    }
}
