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
package sipinspector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;

import sipinspector.ScenarioEntries.LabelEntry;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.ScenarioEntries.ScenarioEntry.TYPE;
import sipinspector.ScenarioEntries.ScenarioException;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Utils.MD5Digest;
import sipinspector.Utils.RemoteClient;
import sipinspector.Utils.SIPParser;
import sipinspector.Utils.SIValue;
import sipinspector.Utils.TLSDebugLogger;

/**
 *
 * @author Zarko Coklin
 */
public class SIPCall {

    public SIPCall(ScenarioProgressDialog dialog) {
        entryPos = 0;
        cseqValue = 1;
        callNumber = globalCallNumber++;
        VariablesMap = new HashMap<String, String>(10);
        callIDList = new ArrayList(2);
        callIDList.add(Integer.toString(callNumber));
        this.socket = dialog.getMainFrame().getSocket();
        key = socket.getInitialKey();
        remoteAddress = dialog.getMainFrame().getRemoteAddress();
        remotePort = dialog.getMainFrame().getRemotePort();
        callRewinded = false;
        this.dialog = dialog;
        pauseDuration = 0; // put some default value for pause
        pauseActivated = false;
        rowOfValues = dialog.getMainFrame().getValuesLoader().getRowOfValues();
        if (TLSDebugLogger.isEnabled()) {
            int fields = (rowOfValues != null ? rowOfValues.length : 0);
            TLSDebugLogger.logf("SIPCALL INIT: rowOfValues=%s, fields=%d",
                    (rowOfValues != null ? "non-null" : "null"), fields);
        }
        try {
            if (GlobalSettings.getSetting("RTP_Remote_Address") != null &&
                    GlobalSettings.getSetting("RTP_Remote_Port") != null) {
                remoteRTPClient = new RemoteClient(
                        InetAddress.getByName(GlobalSettings.getSetting("RTP_Remote_Address")),
                        Integer.parseInt(GlobalSettings.getSetting("RTP_Remote_Port")));
            } else {
                remoteRTPClient = new RemoteClient(null, 0);
            }
        } catch (UnknownHostException ex) {
            remoteRTPClient = new RemoteClient(null, 0);
        }
    }

    public SIPCall(ScenarioProgressDialog dialog,
            MyPacket packet) {
        entryPos = 0;
        cseqValue = 1;
        callIDList = new ArrayList(2);
        callIDList.add(SIPParser.getCallID(packet.getMessageTxt()));
        VariablesMap = new HashMap<String, String>(10);
        this.socket = dialog.getMainFrame().getSocket();
        remoteAddress = packet.getAddress();
        remotePort = packet.getPort();
        this.dialog = dialog;
        pauseDuration = 0;
        pauseActivated = false;
        rowOfValues = dialog.getMainFrame().getValuesLoader().getRowOfValues();
        remoteRTPClient = new RemoteClient(null, 0);
    }

    public void forwardToTheBeginningOfDialog() {
        /*
         * In some cases scenario could go like this:
         * ---> REGISTER
         * <--- 200 OK
         * New Dialog
         * <--- INVITE
         * ....
         * New Dialog
         * <--- OPTIONS
         * ---> 200 OK
         * New Dialog
         * <--- INFO
         * ---> 200 OK
         * 
         * This scenario has basically 4 dialogs. Every time a new dialog is
         * about to be created this functions finds where possibly a new dialog
         * starts. The start of a dialog is determined if:
         * a) Incoming message matches the expected message
         * b) the previous command is "New Dialog" - indication a new dialog
         * may and is allowed to start.
         * 
         * In this particular example a new Dialog will be started if either
         * INVITE, OPTIONS or INFO SIP requests are received.
         */
        int totalNum = SIPScenario.getEntriesCount();
        int cnt = 0;
        int newDialogPos = -2;
        ScenarioEntry entry;

        while (cnt < totalNum - 1) {
            /* find mandatory outgoing message or play pcap request */
            entry = SIPScenario.getScenarioPos(cnt);
            if (entry.getType() == TYPE.NEW_DIALOG) {
                newDialogPos = cnt++;
                continue;
            }
            if (entry.getDirection() == ScenarioEntry.DIRECTION.IN
                    && entry.getType() == TYPE.REQUEST
                    && newDialogPos == cnt - 1) {
                entryPos = cnt;
                break;
            }
            cnt++;
        }
    }

    // This
    public void rewindClientScenario() {
        int totalNum = SIPScenario.getEntriesCount();
        ScenarioEntry entry;

        while (entryPos < totalNum - 1) {
            /* find mandatory outgoing message or play pcap request */
            entry = SIPScenario.getScenarioPos(entryPos);
            if ((entry.getDirection() == ScenarioEntry.DIRECTION.OUT &&
                    entry.getOptionalFlag() == false) ||
                    entry.getType() == ScenarioEntry.TYPE.COMMAND_PLAYPCAP ||
                    entry.getType() == ScenarioEntry.TYPE.SET_REMOTE_TARGET) {
                break;
            } else {
                nextEntry();
                callRewinded = true; // this will help to determine end of call
            }
        }
    }

    public ScenarioEntry getScenarioPos() {
        return SIPScenario.getScenarioPos(entryPos);
    }

    public void increaseGlobalCallNumber() {
        globalCallNumber++;
    }

    static public int getGlobalCallCounter() {
        return globalCallNumber;
    }

    public ScenarioEntry getScenarioPos(int pos) {
        return SIPScenario.getScenarioPos(pos);
    }

    public void setScenarioPos(int value) {
        entryPos = value;
    }

    public int getCurrentScenarioPos() {
        return entryPos;
    }

    public void resetScenarioPos() {
        entryPos = 0;
    }

    public String getLastRcvdExpectedMsg() {
        return lastRcvdExpectedMsg;
    }

    public boolean processIncomingMsg(String rcvdMsg) throws ScenarioException {
        boolean rc = true;

        if (receivedExpectedMessage(rcvdMsg) == false) {
            rc = false;
            getScenarioPos().increaseUnexpectedCnt();
            dialog.updateStats(getScenarioPos());
        } else {
            lastRcvdExpectedMsg = rcvdMsg;
            setRemoteRTPClient(lastRcvdExpectedMsg);
            getScenarioPos().increaseExpectedCnt();
            dialog.updateStats(getScenarioPos());
            nextEntry();
        }

        return rc;
    }

    public boolean isExpectedMessage(String msg) {
        return receivedExpectedMessage(msg);
    }

    private boolean receivedExpectedMessage(String msg) {
        String received;
        int totalNum = SIPScenario.getEntriesCount();
        received = SIPParser.getMsgReqRespString(msg);
        int start = entryPos;

        while (true) {
            ScenarioEntry entry = getScenarioPos();

            switch (entry.getType()) {
                case REQUEST:
                case RESPONSE:
                    // received message is handled only when scenario says so
                    if (entry.getDirection() != ScenarioEntry.DIRECTION.IN
                            && entry.getOptionalFlag() == false) {
                        return false;
                    }
                    if (entry.getShortCode().equals(received) == true) {
                        return true;
                    }
                    // if message is not optional, then it has to match
                    if (entry.getOptionalFlag() == false) {
                        return false;
                    }
                    break;
                case SET_REMOTE_TARGET: {
                    return false;
                }
                default:
                    break;
            }

            /*
             * If the last entry is optional entry, make sure you do
             * not exceed boundary
             */
            if (entryPos < totalNum - 1) {
                nextEntry();
            } else {
                /*
                 * The last entry is optional but already reached the end
                 * of the scenario. Return false
                 */
                break;
            }
        }
        entryPos = start;
        return false;
    }

    public String procesOutgoingMsg(
            String msg) {
        msg = replaceKeyWords(msg);
        msg = replaceAuthentication(msg);
        msg = updateContentLen(msg);
        return msg;
    }

    public LabelEntry gotoLabel(
            String labelName) {
        int totalNum = SIPScenario.getEntriesCount();
        int cnt = 0;

        while (cnt < totalNum) {
            ScenarioEntry entry = getScenarioPos(cnt);
            // if message is not optional, then it has to match
            if (entry.getType() == ScenarioEntry.TYPE.LABEL) {
                LabelEntry labelEntry = (LabelEntry) entry;
                if (labelEntry.getName().equals(labelName) == true) {
                    entryPos = cnt;
                    return labelEntry;
                }
            }
            cnt++;
        }

        return null;
    }

    public String getAndStoreValue(
            String word) {
        int pos;
        String value;

        String oldValue = "null";
        String keyword;

        if ((pos = SIValue.findOperationSign(word)) == -1) {
            return VariablesMap.get(word);
        }

        keyword = word.substring(0, pos) + "]";
        oldValue = VariablesMap.get(keyword);
        if (oldValue != null) {
            try {
                if (word.charAt(pos) == '+') {
                    value = String.valueOf(Integer.parseInt(oldValue) + SIValue.getIntValue(word.substring(pos + 1)));
                } else {
                    value = String.valueOf(Integer.parseInt(oldValue) - SIValue.getIntValue(word.substring(pos + 1)));
                }

            } catch (NumberFormatException e) {
                return oldValue;
            }

            VariablesMap.put(keyword, value);
        }

        return oldValue;
    }

    public String replaceKeyWords(String msg) {
        int start = 0;
        int end = 0;
        int marker = 0;
        String keyword;

        String value;

        StringBuilder newMessage = new StringBuilder(2048);

        while (true) {
            start = msg.indexOf('[', end);
            if (start == -1) {
                break;
            }

            newMessage.append(msg.substring(end, start));
            end = msg.indexOf(']', start + 1);
            if (end == -1) {
                break;
            }

            marker = ++end;
            keyword = msg.substring(start, end);

            if (keyword.startsWith("[last_") == true) {
                String tmpStr = keyword.substring(6, keyword.length() - 1);
                newMessage.append(replaceLastKeywords(tmpStr));
            } else if (keyword.startsWith("[field") == true) {
                String fieldValue = dialog.getMainFrame().getValuesLoader().getFieldValue(rowOfValues, keyword);
                TLSDebugLogger.logf("FIELD SUBSTITUTE: keyword=%s, value=%s",
                        keyword, fieldValue);
                newMessage.append(fieldValue);
            } else if (keyword.startsWith("[val_last_") == true) {
                String tmpStr = keyword.substring(10, keyword.length() - 1);
                newMessage.append(replaceValLastKeywords(tmpStr));
            } else if (keyword.equals("[call_number]") == true) {
                newMessage.append(getCallID());
            } else if (keyword.equals("[branch]") == true) {
                newMessage.append(replaceBranchKeyword());
            } else if (keyword.equals("[peer_tag_param]") == true) {
                newMessage.append(replacePeerTagParam());
            } else if (keyword.equals("[remote_ip]") == true) {
                newMessage.append(getRemoteAddress());
            } else if (keyword.equals("[remote_port]") == true) {
                newMessage.append(getRemotePort());
            } else if (keyword.startsWith("[cseq") == true) {
                if (keyword.charAt(5) == '+') {
                    cseqValue += SIValue.getIntValue(keyword.substring(6));

                } else if (keyword.charAt(5) == '-') {
                    cseqValue -= SIValue.getIntValue(keyword.substring(6));
                }

                newMessage.append(String.valueOf(cseqValue));

            } else if (keyword.charAt(1) == '$') {
                // at this point keyword is like [$var]
                newMessage.append(getAndStoreValue(keyword));
            } else if (keyword.equals("[ver]") == true) {
                newMessage.append(SIPInspectorMainFrame.getSIPInspectorVersion());
            } else if (keyword.startsWith("[authentication username=")) {
                /*
                 * in this case do nothing just reset end to start + 1 cause
                 * it could be like [authentication username=[field0]; password=[field1];]
                 */
                newMessage.append("[authentication username=");
                end = start + 25; // "[authentication username="
                marker = end;
            } else if ((value = GlobalSettings.KeyWordsMap.get(keyword)) != null) {
                newMessage.append(value);
            } else {
                newMessage.append(keyword);
            }

        }
        newMessage.append(msg.substring(marker));
        return newMessage.toString();
    }

    public String replaceLastKeywords(
            String keyword) {
        return SIPParser.grepString(lastRcvdExpectedMsg, keyword);
    }

    public String replaceValLastKeywords(
            String keyword) {
        return SIPParser.getHeaderValue(lastRcvdExpectedMsg, keyword);
    }

    private String replacePeerTagParam() {
        String header;

        if (lastRcvdExpectedMsg == null) {
            return "";
        }

        if (SIPParser.isMsgRequest(lastRcvdExpectedMsg) == false) {
            header = SIPParser.getHeader(lastRcvdExpectedMsg, "To", "t");
            return SIPParser.getParam(header, "tag");
        } else {
            header = SIPParser.getHeader(lastRcvdExpectedMsg, "From", "f");
            return SIPParser.getParam(header, "tag");
        }

    }

    private String replaceAuthentication(String msg) {
        int start;
        int end;
        String rc;

        String username;

        String password;

        String method;

        String authHdr;

        String ruri;

        String response;

        StringBuilder proxyAuthHdr = new StringBuilder(256);
        String old;

        start = msg.indexOf("[authentication ");
        if (start == -1) {
            return msg;
        }

        end = msg.indexOf(']', start);
        if (end == -1) {
            return msg;
        }

        end++;

        if (lastRcvdExpectedMsg != null && SIPParser.isMsgRequest(lastRcvdExpectedMsg) == false) {
            rc = SIPParser.getResponseCode(lastRcvdExpectedMsg);
            if (rc.equals("401") || rc.equals("407")) {
                old = msg.substring(start, end);
                username = SIPParser.getParamValue(old, "username");
                password = SIPParser.getParamValue(old, "password");
                method = SIPParser.getRequestMethod(msg);
                authHdr = SIPParser.grepString(lastRcvdExpectedMsg, "-Authenticate");
                ruri = SIPParser.getRURI(msg);
                MD5Digest md5 = new MD5Digest(authHdr, method, username,
                        password, ruri);
                response = md5.calculateMD5DigestResponse();
                proxyAuthHdr.append("Digest " + "username=\"").append(username).append("\",realm=\"")
                        .append(md5.getRealm()).append("\"," + "nonce=\"").append(md5.getNonce())
                        .append("\"," + "uri=\"").append(ruri).append("\"," + "response=\"").append(response)
                        .append("\"");

                String qop = md5.getQop();
                if (qop != null && qop.contains("auth") == true) {
                    proxyAuthHdr.append(",qop=auth");
                    proxyAuthHdr.append(",cnonce=\"").append(md5.getCnonce()).append("\",nc=00000001");
                }

                proxyAuthHdr.append(",algorithm=MD5");

                String opaque = md5.getOpaque();
                if (opaque != null && opaque.equals("") == false) {
                    proxyAuthHdr.append(",opaque=\"").append(md5.getOpaque()).append("\"");
                }

                msg = msg.replace(old, proxyAuthHdr);
            }

        }
        return msg;
    }

    private String replaceBranchKeyword() {
        return "z9hG4bK" + String.valueOf(globalCallNumber) + String.valueOf(entryPos);
    }

    public static String updateContentLen(
            String msg) {
        int totLen = msg.length();
        int contLen;
        int posStart = msg.indexOf("[len]");

        // Check long version first
        if (posStart == -1) {
            return msg;
        }

        // Then find the start of SDP body. It starts after "\r\n\r\n"
        // To save some processing start from the end of Content-Length header
        posStart = msg.indexOf("\r\n\r\n", posStart);
        if (posStart == -1) {
            contLen = 0;
        } else {
            contLen = totLen - (posStart + 4);
        }

        msg = msg.replace("[len]", String.valueOf(contLen));
        return msg;
    }

    public void nextEntry() {
        entryPos++;

    }

    public boolean isEndReached() {
        int cnt = entryPos;
        boolean optionalFlag = true;

        if (entryPos == 0) {
            return false;
        }
        // End is reached when the last entry in the scenario is processed or
        // when a New Dialog command is encountered
        else if (entryPos >= SIPScenario.getEntriesCount() ||
                getScenarioPos().getType() == ScenarioEntry.TYPE.NEW_DIALOG) {
            dialog.getRTPThread().delRemoteClient(remoteRTPClient);
            return true;
        }
        // End is also reached
        else if (getScenarioPos(cnt).getType() == ScenarioEntry.TYPE.NEW_DIALOG) {
            dialog.getRTPThread().delRemoteClient(remoteRTPClient);
            return true;
        }
        // The end is reached when the last entry is processed or
        // - when the starting sequence of optional messages is followed by
        // an incoming request
        // - when the starting sequence of optional messages are processed
        // <----- SUBSCRIBE*
        // -----> 200 OK* - this is considered as the end
        // <----- REGISTER*
        // -----> 200 OK* - this is considered as the end too
        // <----- INVITE
        // This way scenario can handle unsuspected, out-of-dialog requests
        else if (callRewinded == false && getScenarioPos(cnt).getType() == ScenarioEntry.TYPE.REQUEST) {
            // Check that all previous entries in the sceario were optional
            while (cnt > 0 && optionalFlag == true) {
                optionalFlag &= getScenarioPos(--cnt).getOptionalFlag();
            }

            return optionalFlag;
        }

        return false;
    }

    public void storeVariableValue(String variable,
            String value) {
        VariablesMap.put("[$" + variable + "]", value);
    }

    public void sendMsg(String msg) throws IOException {
        String outgoingMsg = procesOutgoingMsg(msg);
        String callId = SIPParser.getCallID(outgoingMsg);
        if (dialog.getCallDB().containsKey(callId) == false) {
            dialog.getCallDB().put(callId, this);
            setCallId(callId);
        }
        justSendMsg(outgoingMsg);
    }

    public void justSendMsg(String msg) throws IOException {
        lastMsgOut = new MyPacket(msg);
        lastMsgOut.setAddress(remoteAddress);
        lastMsgOut.setPort(remotePort);
        socket.send(lastMsgOut);

        // Record the media port THIS endpoint just advertised in its own SDP.
        // In SDP, the m=audio port you SEND is the port you expect to RECEIVE
        // RTP on - so live audio must bind its receiver to exactly this port.
        // Scenarios source the m=audio port from different tokens ([media_port],
        // [field1], [field2], ...); without capturing the actually-sent value,
        // LiveAudioController would bind GlobalSettings.getMediaPort() and could
        // silently listen on a different port than we advertised, so the peer's
        // RTP lands nowhere and no audio is heard.
        int localSdpPort = getRTPPort(msg);
        if (localSdpPort > 0) {
            GlobalSettings.setSetting("RTP_Local_Port", Integer.toString(localSdpPort));
            // Also record the audio codec list WE advertised, so codec
            // negotiation can pick a payload type both ends actually offered.
            String localCodecs = getAudioFormatList(msg);
            if (localCodecs != null && localCodecs.length() > 0) {
                GlobalSettings.setSetting("RTP_Local_Codecs", localCodecs);
            }
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[RTP LOCAL] advertised media port=%d codecs=[%s]",
                        localSdpPort, localCodecs);
            }
        }
    }

    /**
     * Return the space-separated payload-type list from an SDP m=audio line,
     * e.g. "8 0 9 101", or "" if none.
     */
    private String getAudioFormatList(String msg) {
        String line = SIPParser.grepString(msg, "m=audio ");
        if (line == null || line.trim().length() == 0) {
            return "";
        }
        String[] parts = line.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public void recvMsg() throws IOException {
        key.channel().register(socket.getSelector(), SelectionKey.OP_READ);
    }

    public void setPauseDuration(int newValue) {
        if (pauseActivated == false) {
            return;
        }

        pauseDuration = newValue;
        if (pauseDuration <= 0) {
            pauseActivated = false;
        }

        return;
    }

    public boolean getPauseActivated() {
        return pauseActivated;
    }

    public void setPauseActivated() {
        pauseActivated = true;
    }

    public int getPauseDuration() {
        return pauseDuration;
    }

    public String getCallID() {
        if (callIDList != null && callIDList.size() >= 1) {
            return callIDList.get(0);
        }

        return "null";
    }

    public ArrayList<String> getCallIDList() {
        return callIDList;
    }

    public SelectionKey getChannelKey() {
        return key;
    }

    private void setRemoteRTPClient(String msg) {
        InetAddress remAddress = getRTPAddress(msg);
        int remPort = getRTPPort(msg);
        int remPt = getRTPPayloadType(msg);

        if (remPort == 0) {
            return;
        }

        if (remAddress == null) {
            // SDP had no c= line. Reuse existing RTP target if present,
            // otherwise fall back to the SIP peer address.
            remAddress = remoteRTPClient.getRemAddress();
            if (remAddress == null) {
                remAddress = remoteAddress;
            }
        }

        if (remAddress != null) {
            InetAddress oldAddress = remoteRTPClient.getRemAddress();
            int oldPort = remoteRTPClient.getRemPort();
            boolean changed = (oldAddress == null || !oldAddress.equals(remAddress) || oldPort != remPort);

            if (changed) {
                remoteRTPClient.setRemAddress(remAddress);
                remoteRTPClient.setRemPort(remPort);
                GlobalSettings.setSetting("RTP_Remote_Address", remAddress.getHostAddress());
                GlobalSettings.setSetting("RTP_Remote_Port", Integer.toString(remPort));
                if (remPt > 0) {
                    GlobalSettings.setSetting("RTP_Remote_PT", Integer.toString(remPt));
                }

                if (TLSDebugLogger.isEnabled()) {
                    String callId = getCallID();
                    String msgType = SIPParser.getMsgReqRespString(msg);
                    String oldIp = (oldAddress != null ? oldAddress.getHostAddress() : "null");
                    TLSDebugLogger.logf(
                            "RTP TARGET UPDATE: callId=%s, msg=%s, %s:%d -> %s:%d",
                            callId,
                            msgType,
                            oldIp,
                            oldPort,
                            remAddress.getHostAddress(),
                            remPort);
                    TLSDebugLogger.logf(
                            "[RTP TARGET] ip=%s port=%d source=SDP",
                            remAddress.getHostAddress(),
                            remPort);
                    if (remPt > 0) {
                        TLSDebugLogger.logf("[RTP MAP] negotiated_pt=%d", remPt);
                    }
                }
            }
        }
    }

    public RemoteClient getRemoteClient() {
        return remoteRTPClient;
    }

    private InetAddress getRTPAddress(String msg) {
        if (msg == null) return null;
        int index = msg.toLowerCase().indexOf("c=in ip4 ");
        if (index == -1) return null;
        int start = index + 9;
        int end = msg.indexOf('\n', start);
        if (end == -1) end = msg.length();
        String line = msg.substring(start, end).trim();
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1).trim();
        }
        int pos = line.lastIndexOf('/');
        if (pos > 0) {
            line = line.substring(0, pos).trim();
        }
        try {
            return InetAddress.getByName(line);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private int getRTPPort(String msg) {
        if (msg == null) return 0;
        int index = msg.toLowerCase().indexOf("m=audio ");
        if (index == -1) return 0;
        int start = index + 8;
        int end = msg.indexOf('\n', start);
        if (end == -1) end = msg.length();
        String line = msg.substring(start, end).trim();
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1).trim();
        }
        String[] parts = line.split("\\s+");
        if (parts.length > 0) {
            try {
                return Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private int getRTPPayloadType(String msg) {
        String line = SIPParser.grepString(msg, "m=audio ");
        if (line == null || line.trim().length() == 0) {
            return 0;
        }
        // m=audio <port> RTP/AVP <fmt> <fmt> ... — the format (payload-type)
        // list starts at index 3.
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 4) {
            return 0;
        }
        // Negotiate the payload type both ends will actually use. Walk the
        // peer's format list IN PREFERENCE ORDER and pick the first codec that
        // (a) this tool's live-audio engine can handle (PCMU/PCMA/G722) and
        // (b) we ourselves advertised in our own SDP (RTP_Local_Codecs), so we
        // never claim a codec the far end wasn't offered. Honoring the peer's
        // order means an OXE that prefers G722 gets G722, PCMU gets PCMU, etc.
        String localCodecs = GlobalSettings.getSetting("RTP_Local_Codecs");
        int firstEngineSupported = -1;
        int firstOffered = -1;
        for (int i = 3; i < parts.length; i++) {
            int candidate;
            try {
                candidate = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                continue; // skip non-numeric token
            }
            if (firstOffered < 0) {
                firstOffered = candidate;
            }
            if (!sipinspector.Media.LiveAudioCodec.isSupported(candidate)) {
                continue;
            }
            if (firstEngineSupported < 0) {
                firstEngineSupported = candidate;
            }
            // Prefer a codec we also advertised (true mutual agreement).
            if (localCodecs == null || localCodecs.trim().length() == 0
                    || codecListContains(localCodecs, candidate)) {
                return candidate;
            }
        }
        // No mutually-advertised codec. Fall back to the first engine-supported
        // codec the peer offered; if the peer offered none we can do, keep its
        // first codec but warn (audio would be silent).
        if (firstEngineSupported > 0) {
            return firstEngineSupported;
        }
        if (firstOffered > 0 && TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf(
                    "[RTP WARN] peer offered no codec this tool supports (PCMU/PCMA/G722); "
                    + "audio will be silent. Peer media line: %s", line.trim());
        }
        return firstOffered > 0 ? firstOffered : 0;
    }

    private boolean codecListContains(String list, int pt) {
        for (String tok : list.trim().split("\\s+")) {
            try {
                if (Integer.parseInt(tok) == pt) return true;
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        return false;
    }

    public void setCallId(String callId) {
        this.callIDList.add(callId);
    }

    public MyPacket getLastMsgOut() {
        return lastMsgOut;
    }

    public void setRemoteAddress(InetAddress address) {
        remoteAddress = address;
    }

    public String getRemoteAddress() {
        return remoteAddress.getHostAddress();
    }

    public void setRemotePort(int port) {
        remotePort = port;
    }

    public String getRemotePort() {
        return Integer.toString(remotePort);
    }

    public void storeCallID(String callId) {

    }

    private int entryPos;

    private static int globalCallNumber = 1;

    private int cseqValue;

    private String lastRcvdExpectedMsg;

    private HashMap<String, String> VariablesMap;

    private ArrayList<String> callIDList;

    private MySocketAbstract socket;

    private SelectionKey key;

    private InetAddress remoteAddress;

    private int remotePort;

    private ScenarioProgressDialog dialog;

    private boolean callRewinded;

    private int pauseDuration;

    private boolean pauseActivated;

    private int callNumber;

    private SIValue[] rowOfValues;

    private RemoteClient remoteRTPClient;

    private MyPacket lastMsgOut;
}
