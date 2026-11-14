/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and 
 *  troubleshoot SIP signaling. 
 * 
 *  Copyright (C) 2008  Zarko Coklin (zarko_coklin at yahoo dot com)
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sipinspector.ScenarioEntries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.SecureRandom;
import java.util.Base64;

import javax.swing.JOptionPane;

import sipinspector.GlobalSettings;
import sipinspector.SIPCall;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Utils.SIPParser;

/**
 *
 * @author Zarko Coklin
 */
public class SIPMessageEntry extends ScenarioEntry {

    private String getRURI() {
        return "[request] sip:" + GlobalSettings.getUsername()
                + "@[remote_ip]:[remote_port];transport=udp SIP/2.0\r\n";
    }

    private String getVia() {
        return "Via: SIP/2.0/[transport] [local_ip]:[local_port];branch=[branch]\r\n";
    }

    private String getFrom() {
        return "From: <sip:" + GlobalSettings.getUsername() + "@[local_ip]:[local_port]>;tag=24417923471779367133\r\n";
    }

    private String getTo() {
        // Standard To header with tag for non-INVITE requests
        return "To: <sip:" + GlobalSettings.getUsername() + "@[remote_ip]:[remote_port]>;[peer_tag_param]\r\n";
    }

    private String getToForINVITE() {
        // INVITE tag logic: Remove To tag from initial INVITE (RFC 3261)
        // Initial INVITE should NOT have To tag
        // Re-INVITE should have To tag (call already established)
        String toHeader = "To: <sip:" + GlobalSettings.getUsername() + "@[remote_ip]:[remote_port]>";

        // Check if this is an initial INVITE or re-INVITE
        if (isInitialINVITE()) {
            // Initial INVITE: No tag in To header
            toHeader += "\r\n";
            sipinspector.Utils.TLSDebugLogger
                    .log("DEBUG: INVITE TAG LOGIC - Initial INVITE detected, To header without tag");
        } else {
            // Re-INVITE or other requests: Include tag
            toHeader += ";[peer_tag_param]\r\n";
            sipinspector.Utils.TLSDebugLogger
                    .log("DEBUG: INVITE TAG LOGIC - Re-INVITE or established dialog, To header with tag");
        }

        return toHeader;
    }

    private boolean isInitialINVITE() {
        // Detect if this is an initial INVITE based on dialog state
        // Check if we have a peer tag (indicates established dialog)
        // If no peer tag exists, this is an initial INVITE
        String peerTag = GlobalSettings.getSetting("[peer_tag_param]");

        // Also check GlobalSettings flag for explicit tracking
        boolean isInitial = GlobalSettings.isInitialInvite;

        // If peer tag is null or empty, it's an initial INVITE
        if ((peerTag == null || peerTag.isEmpty() || peerTag.equals("")) && !isInitial) {
            return true;
        }

        // If explicitly marked as initial, return true
        if (isInitial) {
            return true;
        }

        return false;
    }

    private String getCallID() {
        return "Call-ID: [call_number]@[local_ip]\r\n";
    }

    private String getCSeq() {
        return "CSeq: [cseq] [request]\r\n";
    }

    private String getIncrementCSeq() {
        return "CSeq: [cseq+1] [request]\r\n";
    }

    private String getMaxForwards() {
        return "Max-Forwards: 70\r\n";
    }

    private String getContact() {
        return "Contact: <sip:" + GlobalSettings.getUsername() + "@[local_ip]:[local_port];transport=[transport]>\r\n";
    }

    private String getUserAgent() {
        return "User-Agent: SIP Simulator v" + SIPInspectorMainFrame.getSIPInspectorVersion() + "\r\n";

    }

    private String getContentType() {
        return "Content-Type: application/sdp\r\n";
    }

    private String getContentLen() {
        return "Content-Length: [len]\r\n\r\n";
    }

    private String getContentLenZero() {
        return "Content-Length: 0\r\n\r\n";
    }

    private String getRAck() {
        return "RAck: [val_last_RSeq:] [val_last_CSeq:]\r\n";
    }

    private String getOfferSDP() {
        String custom = GlobalSettings.getCustomSDP();
        if (custom != null && custom.length() > 0) {
            return custom;
        }

        boolean useG711 = GlobalSettings.isSdpG711();
        boolean useG729 = GlobalSettings.isSdpG729();
        boolean useG722 = GlobalSettings.isSdpG722();
        boolean useOpus = GlobalSettings.isSdpOpus();

        if (useG711 == false && useG729 == false && useG722 == false && useOpus == false) {
            // Fallback to G.711 if nothing is selected
            useG711 = true;
        }

        StringBuilder sdp = new StringBuilder(256);
        sdp.append("v=0\r\n");
        sdp.append("o=111 843670094 843670094 IN IP4 [local_ip]\r\n");
        sdp.append("s=-\r\n");
        sdp.append("c=IN IP4 [local_ip]\r\n");
        sdp.append("t=0 0\r\n");

        boolean useSavp = GlobalSettings.isSdpUseSavp();

        // Build payload list based on enabled codecs
        StringBuilder payloads = new StringBuilder();
        boolean firstPayload = true;
        if (useG711) {
            payloads.append("8");
            firstPayload = false;
        }
        if (useG729) {
            if (!firstPayload) {
                payloads.append(" ");
            }
            payloads.append("18");
            firstPayload = false;
        }
        if (useG722) {
            if (!firstPayload) {
                payloads.append(" ");
            }
            payloads.append("9");
            firstPayload = false;
        }
        if (useOpus) {
            if (!firstPayload) {
                payloads.append(" ");
            }
            payloads.append("111");
            firstPayload = false;
        }

        // Always advertise telephone-event as last payload
        if (!firstPayload) {
            payloads.append(" ");
        }
        payloads.append("101");

        // Build rtpmap lines for enabled codecs + telephone-event
        StringBuilder rtpmap = new StringBuilder();
        if (useG711) {
            rtpmap.append("a=rtpmap:8 PCMA/8000\r\n");
        }
        if (useG729) {
            rtpmap.append("a=rtpmap:18 G729/8000\r\n");
        }
        if (useG722) {
            rtpmap.append("a=rtpmap:9 G722/8000\r\n");
        }
        if (useOpus) {
            rtpmap.append("a=rtpmap:111 opus/48000/2\r\n");
        }
        rtpmap.append("a=rtpmap:101 telephone-event/8000\r\n");

        if (useSavp) {
            // Canonical SRTP offer structure: AVP and SAVP m-lines with rtcp, silenceSupp,
            // ptime/maxptime and crypto. Codecs are still driven by SDP panel selections.
            sdp.append("m=audio ")
                    .append(GlobalSettings.getMediaPort())
                    .append(" RTP/AVP ")
                    .append(payloads)
                    .append("\r\n");
            sdp.append("a=rtcp:[media_port+1]\r\n");
            sdp.append(rtpmap);
            sdp.append("a=fmtp:101 0-15\r\n");
            sdp.append("a=silenceSupp:off - - - -\r\n");
            sdp.append("a=sendrecv\r\n");
            sdp.append("a=ptime:20\r\n");
            sdp.append("a=maxptime:30\r\n");

            sdp.append("m=audio ")
                    .append(GlobalSettings.getMediaPort())
                    .append(" RTP/SAVP ")
                    .append(payloads)
                    .append("\r\n");
            sdp.append("a=rtcp:[media_port+1]\r\n");
            sdp.append(rtpmap);
            sdp.append("a=fmtp:101 0-15\r\n");
            sdp.append("a=silenceSupp:off - - - -\r\n");
            sdp.append("a=sendrecv\r\n");
            sdp.append("a=ptime:20\r\n");
            sdp.append("a=maxptime:30\r\n");
            // SRTP crypto must be sourced from actual SDP text and must not be hardcoded.
            sdp.append(generateSdesCryptoLine())
                    .append("\r\n");

            String sdpText = sdp.toString();
            persistSrtpCryptoFromSdp(sdpText);
            return sdpText;
        }

        // Non-SRTP offer: original multi-codec RTP/AVP behavior
        sdp.append("a=sendrecv\r\n");
        sdp.append("m=audio ")
                .append(GlobalSettings.getMediaPort())
                .append(" RTP/AVP ")
                .append(payloads)
                .append("\r\n");

        sdp.append(rtpmap);
        sdp.append("a=fmtp:101 0-15\r\n");
        sdp.append("a=ptime:20\r\n");

        String sdpText = sdp.toString();
        persistSrtpCryptoFromSdp(sdpText);
        return sdpText;
    }

    private String getAnswerSDP() {
        String custom = GlobalSettings.getCustomSDP();
        if (custom != null && custom.length() > 0) {
            persistSrtpCryptoFromSdp(custom);
            return custom;
        }

        String answerType = GlobalSettings.getSdpAnswerCodecType();
        if (answerType == null) {
            answerType = "G711";
        }

        String payload = "8";
        if ("G729".equals(answerType)) {
            payload = "18";
        } else if ("G722".equals(answerType)) {
            payload = "9";
        } else if ("OPUS".equals(answerType)) {
            payload = "111";
        }

        StringBuilder sdp = new StringBuilder(256);
        sdp.append("v=0\r\n");
        sdp.append("o=111 843670094 843670094 IN IP4 [local_ip]\r\n");
        sdp.append("s=-\r\n");
        sdp.append("c=IN IP4 [local_ip]\r\n");
        sdp.append("t=0 0\r\n");
        sdp.append("a=sendrecv\r\n");

        String transportToken = GlobalSettings.isSdpUseSavp() ? "RTP/SAVP" : "RTP/AVP";

        sdp.append("m=audio ")
                .append(GlobalSettings.getMediaPort())
                .append(" ")
                .append(transportToken)
                .append(" ")
                .append(payload)
                .append(" 101\r\n");

        if ("G729".equals(answerType)) {
            sdp.append("a=rtpmap:18 G729/8000\r\n");
        } else if ("G722".equals(answerType)) {
            sdp.append("a=rtpmap:9 G722/8000\r\n");
        } else if ("OPUS".equals(answerType)) {
            sdp.append("a=rtpmap:111 opus/48000/2\r\n");
        } else {
            sdp.append("a=rtpmap:8 PCMA/8000\r\n");
        }

        sdp.append("a=rtpmap:101 telephone-event/8000\r\n");
        sdp.append("a=fmtp:101 0-15\r\n");
        sdp.append("a=ptime:20\r\n");

        String sdpText = sdp.toString();
        persistSrtpCryptoFromSdp(sdpText);
        return sdpText;
    }

    private static void persistSrtpCryptoFromSdp(String sdpText) {
        if (sdpText == null) {
            return;
        }
        if (!GlobalSettings.isSdpUseSavp()) {
            return;
        }
        String crypto = extractFirstCryptoLine(sdpText);
        if (crypto != null) {
            GlobalSettings.setSrtpCryptoLine(crypto);
            GlobalSettings.saveSettings();
        }
    }

    private static String extractFirstCryptoLine(String sdpText) {
        int idx = sdpText.indexOf("a=crypto:");
        if (idx < 0) {
            return null;
        }
        int end = sdpText.indexOf("\r\n", idx);
        if (end < 0) {
            end = sdpText.indexOf("\n", idx);
        }
        if (end < 0) {
            end = sdpText.length();
        }
        String line = sdpText.substring(idx, end).trim();
        if (line.length() == 0) {
            return null;
        }
        return line;
    }

    private static String generateSdesCryptoLine() {
        // If a crypto line already exists (from earlier generated SDP), reuse it for consistency.
        String existing = GlobalSettings.getSrtpCryptoLine();
        if (existing != null && existing.trim().startsWith("a=crypto:")) {
            return existing.trim();
        }

        // 16-byte master key + 14-byte master salt (30 bytes) base64-encoded per SDES inline format.
        byte[] keySalt = new byte[30];
        new SecureRandom().nextBytes(keySalt);
        String b64 = Base64.getEncoder().encodeToString(keySalt);

        // Minimal required suite per finalized decisions.
        String line = "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:" + b64 + "|2^31";
        GlobalSettings.setSrtpCryptoLine(line);
        GlobalSettings.saveSettings();
        return line;
    }

    private String getREGISTER() {
        return getRURI()
                + getVia()
                + "From: <sip:" + GlobalSettings.getUsername()
                + "@[remote_ip]:[remote_port]>;tag=24417923471779367133\r\n"
                + "To: <sip:" + GlobalSettings.getUsername() + "@[remote_ip]:[remote_port]>\r\n"
                + getCallID()
                + getIncrementCSeq()
                + getMaxForwards()
                + "Contact: <sip:" + GlobalSettings.getUsername()
                + "@[local_ip]:[local_port];transport=[transport]>\r\n"
                + getUserAgent()
                + getContentLen();
    }

    private String getINVITE() {
        return getRURI()
                + getVia()
                + getFrom()
                + getToForINVITE()
                + getCallID()
                + getIncrementCSeq()
                + getMaxForwards()
                + getContact()
                + getUserAgent()
                + getContentType()
                + getContentLen()
                + getOfferSDP();
    }

    private String getCANCEL_ACK() {
        return getRURI()
                + getVia()
                + getFrom()
                + getTo()
                + getCallID()
                + getCSeq()
                + getMaxForwards()
                + getContact()
                + getUserAgent()
                + getContentLenZero();
    }

    private String getPRACK() {
        return getRURI()
                + getVia()
                + getFrom()
                + "To: <sip:" + GlobalSettings.getUsername() + "@[remote_ip]:[remote_port]>\r\n"
                + getCallID()
                + getIncrementCSeq()
                + getRAck()
                + getMaxForwards()
                + getContact()
                + getUserAgent()
                + getContentLenZero();
    }

    private String getGenericRequest() {
        return getRURI()
                + getVia()
                + getFrom()
                + getTo()
                + getCallID()
                + getIncrementCSeq()
                + getMaxForwards()
                + getContact()
                + getUserAgent()
                + getContentLenZero();
    }

    private String getRESPONSE() {
        return "SIP/2.0 [request]\r\n"
                + "[last_Via:]\r\n"
                + "[last_From:]\r\n"
                + "[last_To:];tag=[peer_tag_param]\r\n"
                + "[last_Call-ID:]\r\n"
                + "[last_CSeq:]\r\n"
                + "User-Agent: SIPInspector_v_" + SIPInspectorMainFrame.getSIPInspectorVersion() + "\r\n"
                + "Contact: <sip:[local_ip]:[local_port];transport=[transport]>\r\n"
                + "Content-Length: 0\r\n\r\n";
    }

    private String getRESPONSE(String request) {
        boolean includeSDP = false;
        String statusLine = request;

        if (request != null) {
            if (request.startsWith("180 Ringing")) {
                if (request.contains("SDP")) {
                    includeSDP = true;
                }
                statusLine = "180 Ringing";
            } else if (request.startsWith("183 Session Progress")) {
                if (request.contains("SDP")) {
                    includeSDP = true;
                }
                statusLine = "183 Session Progress";
            } else if (request.startsWith("200 OK")) {
                includeSDP = true;
                statusLine = "200 OK";
            }
        }

        StringBuilder msg = new StringBuilder(256);
        msg.append("SIP/2.0 ").append(statusLine).append("\r\n");
        msg.append("[last_Via:]\r\n");
        msg.append("[last_From:]\r\n");
        msg.append("[last_To:];tag=[peer_tag_param]\r\n");
        msg.append("[last_Call-ID:]\r\n");
        msg.append("[last_CSeq:]\r\n");
        msg.append("User-Agent: SIPInspector_v_")
                .append(SIPInspectorMainFrame.getSIPInspectorVersion())
                .append("\r\n");
        msg.append("Contact: <sip:[local_ip]:[local_port];transport=[transport]>\r\n");

        if (includeSDP) {
            msg.append("Content-Type: application/sdp\r\n");
            msg.append("Content-Length: [len]\r\n\r\n");
            msg.append(getAnswerSDP());
        } else {
            msg.append("Content-Length: 0\r\n\r\n");
        }

        return msg.toString();
    }

    public SIPMessageEntry(String content) {
        String str;
        boolean startFlag = true;
        ScenarioEntry.DIRECTION direction = ScenarioEntry.DIRECTION.NONE;
        StringBuilder tmpStr = new StringBuilder(1024);
        boolean emptyLine = false;

        optionalFlag = false;
        BufferedReader reader = new BufferedReader(new StringReader(content));
        try {
            while ((str = reader.readLine()) != null) {
                if (str.startsWith("//") == true) {
                    continue;
                }
                /*
                 * First line in passed in string should always be
                 * "---------->" or "<----------", possibly followed by a "*"
                 */
                if (direction == ScenarioEntry.DIRECTION.NONE) {
                    if (str.charAt(0) == '<') {
                        direction = ScenarioEntry.DIRECTION.IN;
                        setDirection(direction);
                    } else {
                        direction = ScenarioEntry.DIRECTION.OUT;
                        setDirection(direction);
                    }
                    if (str.contains("*") == true) {
                        optionalFlag = true;
                    }
                    continue;
                }
                if (startFlag == true) {
                    if (SIPParser.isMsgRequest(str) == false) {
                        setType(ScenarioEntry.TYPE.RESPONSE);
                    } else {
                        setType(ScenarioEntry.TYPE.REQUEST);
                    }
                    startFlag = false;
                }
                if (direction == ScenarioEntry.DIRECTION.IN) {
                    SIPMsg = str;
                    return;
                }

                tmpStr.append(str.replace("«", "")).append("\r\n");
                emptyLine = str.equals("");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    e,
                    "Update Messages",
                    JOptionPane.ERROR_MESSAGE);
        }

        if (emptyLine == true) {
            tmpStr.setLength(tmpStr.length() - 2);
        }
        // Ensure outgoing messages without body end with a double CRLF (\r\n\r\n)
        // to comply with standard SIP and prevent "Message is incomplete" errors.
        if (direction == ScenarioEntry.DIRECTION.OUT) {
            String msgStr = tmpStr.toString();
            if (msgStr.toLowerCase().contains("content-length:") && !msgStr.endsWith("\r\n\r\n")) {
                if (msgStr.endsWith("\r\n")) {
                    tmpStr.append("\r\n");
                } else {
                    tmpStr.append("\r\n\r\n");
                }
            }
        }
        /* Now eliminate all unwanted empty lines at the end of SDP part */
        SIPMsg = tmpStr.toString();
    }

    public SIPMessageEntry(ScenarioEntry.TYPE type,
            ScenarioEntry.DIRECTION direction,
            boolean flag,
            String arg) {
        super(type, direction);
        optionalFlag = flag;
        SIPMsg = createSipString(arg, type, direction);
    }

    public String getSIPMsg() {
        return SIPMsg;
    }

    public void setSIPMsg(String msg) {
        SIPMsg = msg;
    }

    @Override
    public boolean getOptionalFlag() {
        return optionalFlag;
    }

    public void setOptionalFlag(boolean flag) {
        optionalFlag = flag;
    }

    private String createSipString(String request,
            ScenarioEntry.TYPE type,
            ScenarioEntry.DIRECTION direction) {
        String msg;

        if (direction == ScenarioEntry.DIRECTION.OUT) {
            if (type == ScenarioEntry.TYPE.REQUEST) {
                if (request.equals("INVITE") == true) {
                    msg = getINVITE();
                } else if (request.equals("REGISTER") == true) {
                    msg = getREGISTER();
                } else if (request.equals("ACK") == true
                        || request.equals("CANCEL") == true) {
                    msg = getCANCEL_ACK();
                } else if (request.equals("PRACK") == true) {
                    msg = getPRACK();
                } else {
                    msg = getGenericRequest();
                }
                msg = msg.replace("[request]", request);
            } else {
                msg = getRESPONSE(request);
            }
        } else {
            msg = request;
        }
        return msg;
    }

    @Override
    synchronized public boolean processEntry(SIPCall call,
            ScenarioProgressDialog dialog) throws ScenarioException {
        try {
            if (getDirection() == DIRECTION.OUT) {
                // Before the message goes out, check if it is an optional
                // message. Optional messages are sent only if the previous
                // message in the scenario tree was optional.
                if (getOptionalFlag() == false || sendOptionalMsg(call) == true) {
                    this.increaseExpectedCnt();
                    dialog.updateStats(call.getScenarioPos());
                    call.sendMsg(SIPMsg);
                }
                call.nextEntry();
                return true;
            }

            return false;

        } catch (IOException exc) {
            throw new ScenarioException();
        }
    }

    @Override
    public String getTreeText() {
        return getScenarioProgressScreenText();
    }

    private boolean sendOptionalMsg(SIPCall call) {
        int currentPos = call.getCurrentScenarioPos();

        /* Inspect previos SIP requests or response if they were optional */
        while (--currentPos >= 0) {
            switch (call.getScenarioPos(currentPos).getType()) {
                case REQUEST:
                case RESPONSE:
                    if (call.getScenarioPos(currentPos).getOptionalFlag() == true) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    @Override
    public String getScenarioScreenText() {
        StringBuilder txt = new StringBuilder(2048);
        String displayMsg = SIPMsg.replace("\r\n", "«\r\n");
        if (getDirection() == ScenarioEntry.DIRECTION.IN) {
            txt.append(System.getProperty("line.separator")).append("<----------");
            if (getOptionalFlag() == true) {
                txt.append("*");
            }
            txt.append(System.getProperty("line.separator")).append(displayMsg)
                    .append(System.getProperty("line.separator"));
        } else {
            txt.append(System.getProperty("line.separator")).append("---------->");
            if (getOptionalFlag() == true) {
                txt.append("*");
            }
            txt.append(System.getProperty("line.separator")).append(displayMsg);
        }

        return txt.toString();
    }

    @Override
    public String getShortCode() {
        return SIPParser.getMsgReqRespString(SIPMsg);
    }

    @Override
    public String getScenarioProgressScreenText() {
        StringBuilder txt = new StringBuilder(128);

        if (getDirection() == ScenarioEntry.DIRECTION.IN) {
            txt.append(" <--- ").append(getShortCode());

        } else {
            txt.append(" ---> ").append(getShortCode());
        }
        if (getOptionalFlag() == true) {
            txt.append("*");
        }

        return txt.toString();
    }

    private String SIPMsg;

    private boolean optionalFlag;
}
