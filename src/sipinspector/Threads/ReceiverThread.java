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
package sipinspector.Threads;

import java.io.IOException;

import sipinspector.CallDB;
import sipinspector.MyPacket;
import sipinspector.SIPCall;
import sipinspector.ScenarioProgressDialog;
import sipinspector.ScenarioEntries.ScenarioException;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Utils.SIPParser;
import sipinspector.Utils.TLSDebugLogger;
import sipinspector.SIPInspectorMainFrame;

/**
 *
 * @author Zarko Coklin
 */
public class ReceiverThread extends Thread {

    public ReceiverThread(ScenarioProgressDialog scenarioProgressDialog,
            MySocketAbstract socket) {
        progressDialog = scenarioProgressDialog;
        callDB = scenarioProgressDialog.getCallDB();
        this.socket = socket;
        threadStopped = false;
    }

    ReceiverThread(CallDB callDB, MySocketAbstract socket) {
        this.progressDialog = null;
        this.callDB = callDB;
        this.socket = socket;
        this.threadStopped = false;
    }

    @Override
    /*
     * This method will make sure that calls are generated at a required pace
     * (calls/second) rate, no maximum number of concurrent calls value is
     * exceeded and that each new call is "fairly treated". A "fair treatment"
     * is a concept where if a call scenario requires 5 messages to go out,
     * the logic will make sure the 1st msg is sent out for all the calls, then
     * 2nd message is sent for all the calls, and so on...
     */
    public void run() {
        MyPacket packet;

        try {
            while (true) {
                if (threadStopped == true) {
                    return;
                }
                /*
                 * Make sure the maximum number of allowed concurrent calls never
                 * goes beyond an allowed limit
                 */
                // Time to accept incoming messages
                packet = socket.receive();
                if (packet != null) {
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.log("RECEIVER: packet received, len=" +
                                packet.length() + ", from=" + packet.getAddress() + ":" + packet.getPort());
                    }
                    handleIncomingMsgs(packet);
                }
            }
        } catch (IOException exc) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logException("RECEIVER: IOException in receive loop", exc);
            }
        }
    }

    private SIPCall handleIncomingMsgs(MyPacket packet) {
        boolean newCallFlag = false;

        boolean isNotify = false;
        try {
            String msgTxt = packet.getMessageTxt();
            if (msgTxt != null && SIPParser.isMsgRequest(msgTxt)) {
                String method = SIPParser.getRequestMethod(msgTxt);
                if (method != null && method.equalsIgnoreCase("NOTIFY")) {
                    isNotify = true;
                }
            }
        } catch (Exception ignore) {}

        SIPCall currCall = callDB.findCallForMsg(packet.getMessageTxt());

        if (isNotify) {
            boolean expected = false;
            if (currCall != null) {
                if (currCall.isExpectedMessage(packet.getMessageTxt())) {
                    expected = true;
                }
            } else {
                try {
                    SIPCall tempCall = new SIPCall(progressDialog, packet);
                    tempCall.forwardToTheBeginningOfDialog();
                    if (tempCall.isExpectedMessage(packet.getMessageTxt())) {
                        expected = true;
                    }
                } catch (Exception ignore) {}
            }

            if (!expected) {
                sendAuto200OK(packet);
                return null;
            }
        }

        if (currCall == null) {
            // Check if received message is equal to the first
            // expected incoming message. If so, then, everything is
            // legitimate and create a new call. Otherwise, drop the
            // message
            currCall = new SIPCall(progressDialog, packet);
            currCall.forwardToTheBeginningOfDialog();
            newCallFlag = true;
        }
        synchronized (currCall) {
            try {
                if (currCall.isEndReached() == true) {
                    return null;
                }
                if (currCall.processIncomingMsg(packet.getMessageTxt()) == true) {
                    if (currCall.isEndReached() == true) {
                        callDB.deleteCall(currCall);
                    }
                    /*
                     * if in incoming SIP message is a request store srcIP and
                     * src Port. Next outgoing message is going there.
                     */
                    if (SIPParser.isMsgRequest(packet.getMessageTxt()) == true) {
                        currCall.setRemoteAddress(packet.getAddress());
                        currCall.setRemotePort(packet.getPort());
                    }
                    progressDialog.getCallsInProgress().add(currCall);
                    if (newCallFlag == true) {
                        callDB.addCall(currCall, currCall.getCallID(), true);
                    }
                }

            } catch (ScenarioException ex) {
                currCall.nextEntry();
            }
            return currCall;
        }
    }

    public void stopThread() {
        threadStopped = true;
        interrupt();
    }

    private void sendAuto200OK(MyPacket requestPacket) {
        try {
            String reqMsg = requestPacket.getMessageTxt();
            
            // Extract required headers from incoming NOTIFY
            java.util.Vector viaList = SIPParser.getHeadersList(reqMsg, "Via", "v");
            StringBuilder vias = new StringBuilder();
            for (Object via : viaList) {
                String v = ((String) via).trim();
                vias.append(v).append("\r\n");
            }
            
            String fromHeader = SIPParser.getHeader(reqMsg, "From", "f");
            if (fromHeader != null) fromHeader = fromHeader.trim() + "\r\n";
            else fromHeader = "From: unknown\r\n";
            
            String toHeader = SIPParser.getHeader(reqMsg, "To", "t");
            if (toHeader != null) {
                toHeader = toHeader.trim();
                if (!toHeader.contains("tag=")) {
                    toHeader = toHeader + ";tag=" + (int)(Math.random() * 100000000);
                }
                toHeader = toHeader + "\r\n";
            } else {
                toHeader = "To: unknown\r\n";
            }
            
            String callIdHeader = SIPParser.getHeader(reqMsg, "Call-ID", "i");
            if (callIdHeader != null) callIdHeader = callIdHeader.trim() + "\r\n";
            else callIdHeader = "Call-ID: unknown\r\n";
            
            String cseqHeader = SIPParser.getHeader(reqMsg, "CSeq", null);
            if (cseqHeader != null) cseqHeader = cseqHeader.trim() + "\r\n";
            else cseqHeader = "CSeq: 1 NOTIFY\r\n";
            
            // Build the response message
            StringBuilder resp = new StringBuilder();
            resp.append("SIP/2.0 200 OK\r\n");
            resp.append(vias);
            resp.append(fromHeader);
            resp.append(toHeader);
            resp.append(callIdHeader);
            resp.append(cseqHeader);
            resp.append("User-Agent: SIPSimulator_v_").append(SIPInspectorMainFrame.getSIPInspectorVersion()).append("\r\n");
            resp.append("Content-Length: 0\r\n\r\n");
            
            String respText = resp.toString();
            
            MyPacket respPacket = new MyPacket(respText);
            respPacket.setAddress(requestPacket.getAddress());
            respPacket.setPort(requestPacket.getPort());
            
            // Log outgoing message to visual ladder and console log file
            socket.logOutgoingMsg(respPacket);
            
            // Send to network
            socket.send(respPacket);
            
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("RECEIVER: Sent automatic 200 OK for unexpected NOTIFY to " +
                        requestPacket.getAddress() + ":" + requestPacket.getPort());
            }
        } catch (Exception e) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logException("RECEIVER: Error sending auto 200 OK", e);
            }
        }
    }

    private ScenarioProgressDialog progressDialog;

    private CallDB callDB;

    private MySocketAbstract socket;

    private boolean threadStopped;
}
