package sipinspector.Utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import sipinspector.MyPacket;

/**
 * Tracks SIP registration status (expiry countdown) and active call dialog counts.
 * 
 * @author SIP Support Team
 */
public class SIPStateTracker {

    private static volatile String regState = "Unregistered";
    private static volatile long regExpiryTime = 0; // Epoch milliseconds
    
    // Set of active Call-IDs
    private static final Set<String> activeDialogs = ConcurrentHashMap.newKeySet();

    public static synchronized void reset() {
        regState = "Unregistered";
        regExpiryTime = 0;
        activeDialogs.clear();
    }

    public static synchronized void updateState(MyPacket packet, boolean isIncoming) {
        String msg = packet.getMessageTxt();
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }

        try {
            boolean isRequest = SIPParser.isMsgRequest(msg);
            String callId = SIPParser.getCallID(msg);

            if (isRequest) {
                String method = SIPParser.getRequestMethod(msg);
                if (method != null) {
                    method = method.toUpperCase();
                    if (method.equals("REGISTER")) {
                        if (!isIncoming) { // Outgoing REGISTER client attempt
                            regState = "Registering...";
                        }
                    } else if (method.equals("INVITE")) {
                        if (callId != null) {
                            activeDialogs.add(callId);
                        }
                    } else if (method.equals("BYE")) {
                        if (callId != null) {
                            activeDialogs.remove(callId);
                        }
                    }
                }
            } else {
                // Response
                String code = SIPParser.getResponseCode(msg);
                String cseq = SIPParser.getHeaderValue(msg, "CSeq");
                String method = "";
                if (cseq != null) {
                    cseq = cseq.trim();
                    int space = cseq.lastIndexOf(' ');
                    if (space != -1) {
                        method = cseq.substring(space + 1).trim().toUpperCase();
                    }
                }

                if (method.equals("REGISTER")) {
                    if (code.equals("200")) {
                        // Registration success
                        String expiresHeader = SIPParser.getHeaderValue(msg, "Expires");
                        int expiresVal = 3600; // default standard
                        if (expiresHeader != null) {
                            try {
                                expiresVal = Integer.parseInt(expiresHeader.trim());
                            } catch (NumberFormatException e) {}
                        } else {
                            // Try parsing expires parameter in Contact header
                            String contact = SIPParser.getHeaderValue(msg, "Contact");
                            if (contact != null && contact.contains("expires=")) {
                                String val = SIPParser.getParamValue(contact, "expires");
                                if (val != null) {
                                    try {
                                        expiresVal = Integer.parseInt(val.trim());
                                    } catch (NumberFormatException e) {}
                                }
                            }
                        }

                        if (expiresVal > 0) {
                            regExpiryTime = System.currentTimeMillis() + (long) expiresVal * 1000;
                            regState = "Registered";
                        } else {
                            regExpiryTime = 0;
                            regState = "Unregistered";
                        }
                    } else if (code.startsWith("4") || code.startsWith("5") || code.startsWith("6")) {
                        // Registration failed
                        regExpiryTime = 0;
                        regState = "Failed (" + code + ")";
                    }
                } else if (method.equals("INVITE")) {
                    if (code.startsWith("3") || code.startsWith("4") || code.startsWith("5") || code.startsWith("6")) {
                        // INVITE rejected, remove dialog
                        if (callId != null) {
                            activeDialogs.remove(callId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback
        }
    }

    public static synchronized String getRegistrationState() {
        if (regState.equals("Registered")) {
            long remaining = (regExpiryTime - System.currentTimeMillis()) / 1000;
            if (remaining > 0) {
                return "Registered (" + remaining + "s)";
            } else {
                regState = "Expired";
                return "Expired";
            }
        }
        return regState;
    }

    public static synchronized int getActiveDialogCount() {
        return activeDialogs.size();
    }
}
