/*
 * SIP Simulator v4 - Phase 9 Validation
 * OxeTelecomInteroperabilityTest: Validates UUI, Diversion, History-Info, Privacy, PAI, Blind & Attended Transfer
 */
package test;

import sipinspector.Call.SipCallEngine;

import java.util.LinkedHashMap;
import java.util.Map;

public class OxeTelecomInteroperabilityTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: OXE TELECOM INTEROPERABILITY & SPECIAL HEADERS VALIDATION");
        System.out.println("================================================================================");

        testTelecomCustomHeadersFormatting();
        testBlindTransferFormatting();
        testAttendedTransferWithReplacesFormatting();

        System.out.println(">>> ALL OXE TELECOM INTEROPERABILITY TESTS PASSED!");
    }

    private static void testTelecomCustomHeadersFormatting() {
        System.out.print("[TEST 1/3] Custom Telecom Headers (UUI, Diversion, History-Info, Privacy)... ");
        SipCallEngine engine = SipCallEngine.getInstance();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-to-User", "04C301020304;encoding=hex");
        headers.put("Diversion", "<sip:90002@192.168.0.20>;reason=no-answer;screen=no;privacy=off");
        headers.put("History-Info", "<sip:90001@192.168.0.20>;index=1, <sip:90002@192.168.0.20>;index=1.1");
        headers.put("Privacy", "id");

        try {
            String targetHost = engine.getLocalIp();
            // Initiate call with dummy destination to inspect constructed INVITE
            SipCallEngine.CallSession session = engine.makeCall("90008", "90001", 8, targetHost, 5098, headers);
            if (session == null || session.rawInviteMsg == null) {
                throw new RuntimeException("Call session or rawInviteMsg is null");
            }

            String invite = session.rawInviteMsg;
            if (!invite.contains("User-to-User: 04C301020304;encoding=hex")) {
                throw new RuntimeException("Missing User-to-User header in INVITE:\n" + invite);
            }
            if (!invite.contains("Diversion: <sip:90002@192.168.0.20>;reason=no-answer;screen=no;privacy=off")) {
                throw new RuntimeException("Missing Diversion header in INVITE:\n" + invite);
            }
            if (!invite.contains("History-Info: <sip:90001@192.168.0.20>;index=1, <sip:90002@192.168.0.20>;index=1.1")) {
                throw new RuntimeException("Missing History-Info header in INVITE:\n" + invite);
            }
            if (!invite.contains("Privacy: id")) {
                throw new RuntimeException("Missing Privacy header in INVITE:\n" + invite);
            }
            if (!invite.contains("P-Asserted-Identity: \"90008\" <sip:90008@" + targetHost + ">")) {
                throw new RuntimeException("Missing P-Asserted-Identity header in INVITE:\n" + invite);
            }

            // Cleanup session
            engine.hangupCall(session.callId);

            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("Custom headers formatting test failed: " + e.getMessage(), e);
        }
    }

    private static void testBlindTransferFormatting() {
        System.out.print("[TEST 2/3] Blind Transfer (REFER with Refer-To)... ");
        SipCallEngine engine = SipCallEngine.getInstance();
        try {
            String targetHost = engine.getLocalIp();
            SipCallEngine.CallSession session = engine.makeCall("90008", "90001", 8, targetHost, 5098);
            session.setState(sipinspector.Media.CallState.ESTABLISHED);
            boolean xfer = engine.transferCall(session.callId, "90002", null);
            if (!xfer) {
                throw new RuntimeException("Blind transfer returned false");
            }
            engine.hangupCall(session.callId);
            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("Blind transfer test failed: " + e.getMessage(), e);
        }
    }

    private static void testAttendedTransferWithReplacesFormatting() {
        System.out.print("[TEST 3/3] Attended Transfer (REFER with Refer-To + Replaces)... ");
        SipCallEngine engine = SipCallEngine.getInstance();
        try {
            String targetHost = engine.getLocalIp();
            SipCallEngine.CallSession s1 = engine.makeCall("90008", "90001", 8, targetHost, 5098);
            SipCallEngine.CallSession s2 = engine.makeCall("90008", "90002", 8, targetHost, 5098);
            s1.setState(sipinspector.Media.CallState.ESTABLISHED);
            s2.setState(sipinspector.Media.CallState.ESTABLISHED);

            boolean xfer = engine.transferCall(s1.callId, "90002", s2.callId);
            if (!xfer) {
                throw new RuntimeException("Attended transfer returned false");
            }

            engine.hangupCall(s1.callId);
            engine.hangupCall(s2.callId);
            System.out.println("PASSED");
        } catch (Exception e) {
            throw new RuntimeException("Attended transfer test failed: " + e.getMessage(), e);
        }
    }
}
