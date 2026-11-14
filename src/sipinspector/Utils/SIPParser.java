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
package sipinspector.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Vector;

/**
 *
 * @author Zarko Coklin
 */
public class SIPParser {
    public SIPParser() {
    }

    static public String getHeader(String msg,
            String header,
            String shortHdr) {
        if (msg == null || header == null) {
            return null;
        }
        String lowerMsg = msg.toLowerCase();
        String lowerHdr = header.toLowerCase();
        if (!lowerHdr.endsWith(":")) {
            lowerHdr += ":";
        }

        int posHdr = lowerMsg.indexOf(lowerHdr);
        if (posHdr == -1) {
            if (shortHdr == null) {
                return null;
            } else {
                String lowerShort = shortHdr.toLowerCase();
                if (!lowerShort.endsWith(":")) {
                    lowerShort += ":";
                }
                posHdr = lowerMsg.indexOf(lowerShort);
                if (posHdr == -1) {
                    return null;
                }
            }
        }

        int posEnd = msg.indexOf("\r\n", posHdr);
        if (posEnd == -1) {
            posEnd = msg.indexOf("\n", posHdr);
        }
        if (posEnd == -1) {
            posEnd = msg.length();
        }
        String tmpStr = msg.substring(posHdr, posEnd) + "\r\n";
        return tmpStr;
    }

    /**
     * Returns SIP header value. For example, to get "2000 REGISTER" from the
     * following header: "CSeq: 2000 REGISTER"
     * passed in header argument should have value "CSeq:"
     */
    static public String getHeaderValue(String msg,
            String header) {
        int pos = 0;
        String line;
        String value = null;
        boolean found = false;
        String tmpHdr = header;
        int occuranceNum = 1; // default is to match the first occurance
        int occuranceCnt = 0;

        /*
         * Find if header number is given. Example From-3:. In this case
         * tmpHdr = "From" and occuranceNum = 3
         */
        pos = header.lastIndexOf('-');
        if (pos > 1 && pos < header.length() - 2) {
            try {
                occuranceNum = Integer.parseInt(header.substring((pos + 1),
                        header.length() - 1));
                tmpHdr = header.substring(0, pos) + ":";
            } catch (NumberFormatException exc) {
                occuranceNum = 1;
            }
        }

        String lowerTmpHdr = tmpHdr.toLowerCase();
        if (!lowerTmpHdr.endsWith(":")) {
            lowerTmpHdr += ":";
        }

        BufferedReader reader = new BufferedReader(new StringReader(msg));
        try {
            line = reader.readLine();
            while (line != null) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith(lowerTmpHdr) == true) {
                    occuranceCnt++;
                    if (occuranceCnt == occuranceNum) {
                        found = true;
                        value = line.substring(line.indexOf(':') + 1);
                    } else if (occuranceCnt > occuranceNum) {
                        break;
                    }
                } else {
                    if (found == true) {
                        if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                            value += line.trim();
                        } else {
                            break;
                        }
                    }
                }
                if (occuranceCnt == occuranceNum) {
                    break;
                }
                line = reader.readLine();
            }
        } catch (IOException exc) {
        }

        if (value != null) {
            value += "\r\n";
            value = value.trim();
        }

        return value;
    }

    /**
     * Returns SIP header value. For example for the following SIP header
     * "CSeq: 2000 REGISTER"
     * function returns back "2000 REGISTER"
     */
    static public String getHeaderValue(String msg,
            String header,
            String shortHdr) {
        if (msg == null || header == null) {
            return null;
        }
        String lowerMsg = msg.toLowerCase();
        String lowerHdr = header.toLowerCase();
        if (!lowerHdr.endsWith(":")) {
            lowerHdr += ":";
        }
        
        int posStart = lowerMsg.indexOf(lowerHdr);
        int len = lowerHdr.length();
        
        if (posStart == -1) {
            if (shortHdr == null) {
                return null;
            } else {
                String lowerShort = shortHdr.toLowerCase();
                if (!lowerShort.endsWith(":")) {
                    lowerShort += ":";
                }
                posStart = lowerMsg.indexOf(lowerShort);
                if (posStart == -1) {
                    return null;
                }
                len = lowerShort.length();
            }
        }
        
        // Skip spaces and tabs after the colon
        int valueStart = posStart + len;
        while (valueStart < msg.length() && (msg.charAt(valueStart) == ' ' || msg.charAt(valueStart) == '\t')) {
            valueStart++;
        }
        
        int posEnd = msg.indexOf("\r\n", valueStart);
        if (posEnd == -1) {
            posEnd = msg.indexOf("\n", valueStart);
        }
        if (posEnd == -1) {
            posEnd = msg.length();
        }
        String tmpStr = msg.substring(valueStart, posEnd) + "\r\n";
        tmpStr = tmpStr.trim();
        return tmpStr;
    }

    static public String getParam(String msg,
            String param) {
        int posParam = 0;
        int posEnd = 0;
        posParam = msg.indexOf(param);
        if (posParam == -1) {
            return null;
        }
        posEnd = msg.indexOf(";", posParam);
        if (posEnd == -1) {
            posEnd = msg.indexOf("\r\n", posParam);
            if (posEnd == -1) {
                return null;
            }
        }
        return msg.substring(posParam, posEnd);
    }

    static public String getParamValue(String msg,
            String param) {
        int posParam = 0;
        int posEnd = 0;
        posParam = msg.indexOf(param);
        if (posParam == -1) {
            return null;
        }
        posParam = msg.indexOf("=", posParam);
        posParam++;
        posEnd = msg.indexOf(";", posParam);
        if (posEnd == -1) {
            posEnd = msg.indexOf("\r\n", posParam);
            if (posEnd == -1) {
                return msg;
            }
        }
        return msg.substring(posParam, posEnd);
    }

    static public Vector getHeadersList(String msg,
            String longHdr,
            String shortHdr) {
        String str;
        Vector<String> vector = new Vector<String>();
        BufferedReader reader = new BufferedReader(new StringReader(msg));
        try {
            str = reader.readLine();
            while (str != null) {
                if (str.contains(longHdr + ":") || str.contains(shortHdr + ":")) {
                    vector.add(str);
                }
                str = reader.readLine();
            }
        } catch (IOException e) {
        }
        return vector;
    }

    /*
     * Will find all lines that contain given string pattern.
     * NOTE: The last line containing the pattern will not be terminated with
     * "\r\n". It is because of [last_*] headers
     */
    static public String grepString(String msg,
            String pattern) {
        String str;
        boolean firstPass = true;
        boolean hdrFound = false;

        if (msg == null) {
            return null;
        }
        StringBuilder newValue = new StringBuilder(128);
        BufferedReader reader = new BufferedReader(new StringReader(msg));
        try {
            str = reader.readLine();
            while (str != null) {
                if (str.contains(pattern) == true) {
                    hdrFound = true;
                    if (firstPass == true) {
                        newValue.append(str);
                        firstPass = false;
                    } else {
                        newValue.append("\r\n").append(str);
                    }
                } else if (hdrFound == true && str.isEmpty() == false &&
                        (str.charAt(0) == ' ' || str.charAt(0) == '\t')) {
                    newValue.append("\r\n").append(str);
                } else {
                    hdrFound = false;
                }
                str = reader.readLine();
            }
        } catch (IOException e) {
        }

        return newValue.toString();
    }

    static public boolean isMsgRequest(String msg) {
        int pos = 0;
        pos = msg.indexOf("SIP/2.0");
        if (pos == 0) {
            return false;
        }
        return true;
    }

    /*
     * Function first checks if msg is Request or Response
     * For Responses it returns numeric values. For example 180, 200, 404,...
     * For Requests it returns method names: INVITE, REGISTER, ACK,...
     */
    static public String getMsgReqRespString(String msg) {
        String value;
        if (isMsgRequest(msg) == false) {
            value = getResponseCode(msg);
        } else {
            value = getRequestMethod(msg);
        }
        return value;
    }

    static public String getRequestMethod(String msg) {
        int pos;
        pos = msg.indexOf(' ');
        if (pos == -1) {
            pos = msg.length();
        }
        return msg.substring(0, pos);
    }

    static public String getResponseCode(String msg) {
        return msg.substring(8, 11);
    }

    static public String getRURI(String msg) {
        int start;
        int end;

        start = msg.indexOf("sip:");
        if (start == -1) {
            return null;
        }
        end = msg.indexOf(' ', start);
        if (end == -1) {
            return null;
        }
        return msg.substring(start, end);
    }

    /*
     * From a srcTxt function finds a pattern and returns a string where '*' is.
     * For example,
     * srcTxt = "My name is Bond. James Bond - at jBond@hotmail.com"
     * Here are some examples what is returned for different expression values:
     * expression: value:
     * "* " "My"
     * " * is" "name"
     * " *@" "jBond"
     * ". *" "James Bond - at jBond@hotmail.com"
     * "" "null" // there is no '*'
     */
    static public String getStarExpression(String srcTxt, String expression) {
        String tmpValue;
        String secondPart;
        int pos = 0;
        int end = 0;
        int start = 0;

        if (srcTxt == null) {
            return null;
        }
        pos = expression.indexOf('*');
        if (pos == expression.length() - 1) {
            /* This means take the rest of the source string */
            tmpValue = expression.substring(0, pos);
            start = srcTxt.indexOf(tmpValue);
            if (start != -1) {
                return srcTxt.substring(start + pos);
            }
        } else if (pos == 0) {
            /* the beginning of the string */
            end = srcTxt.indexOf(expression.substring(pos + 1));
            if (end != -1) {
                return srcTxt.substring(0, end);
            }
        } else {
            tmpValue = expression.substring(0, pos);
            start = srcTxt.indexOf(tmpValue);
            if (start != -1) {
                secondPart = expression.substring(pos + 1);
                end = srcTxt.indexOf(secondPart, start + pos + 1);
                if (end != -1) {
                    return srcTxt.substring(start + pos, end);
                }
            }
        }
        return "";
    }

    static public String getStartline(String msg) {
        if (msg == null) {
            return null;
        }
        BufferedReader reader = new BufferedReader(new StringReader(msg));

        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    static public String getCallID(String msg) {
        String callIdHdrValue = getHeaderValue(msg, "Call-ID", "i");
        /*
         * Call-ID usually has value@host format. Only interested in value part.
         * The rason is that some SIP aware routers can change host part
         */
        String callID = getStarExpression(callIdHdrValue, "*@");
        if (callID == null || callID.equals("") == true) {
            callID = SIPParser.getStarExpression(callIdHdrValue, "*");
        }

        if (callID == null || callID.equals("") == true) {
            return null;
        }

        return callID;
    }

    /**
     * Determines if an INVITE message is an initial INVITE or a re-INVITE
     * 
     * Initial INVITE indicators:
     * - No To tag in the To header
     * - CSeq number is 1
     * - New Call-ID
     * 
     * Re-INVITE indicators:
     * - To tag present in To header
     * - CSeq number > 1
     * - Existing Call-ID
     * 
     * @param msg SIP message to analyze
     * @return true if initial INVITE, false if re-INVITE
     */
    static public boolean isInitialInvite(String msg) {
        // Check if this is an INVITE request
        String method = getRequestMethod(msg);
        if (method == null || !method.equals("INVITE")) {
            return false;
        }

        // Check if To header has a tag parameter
        String toHeader = getHeaderValue(msg, "To:", "t:");
        if (toHeader != null && toHeader.contains(";tag=")) {
            // Has To tag = re-INVITE
            return false;
        }

        // Check CSeq number
        int cseqNum = getCSeqNumber(msg);
        if (cseqNum > 1) {
            // CSeq > 1 = re-INVITE
            return false;
        }

        // No To tag and CSeq == 1 = initial INVITE
        return true;
    }

    /**
     * Extracts the CSeq number from a SIP message
     * 
     * @param msg SIP message
     * @return CSeq number, or 0 if not found
     */
    static public int getCSeqNumber(String msg) {
        String cseqHeader = getHeaderValue(msg, "CSeq:", "");
        if (cseqHeader == null || cseqHeader.isEmpty()) {
            return 0;
        }

        try {
            // CSeq format: "1 INVITE" or "2 REINVITE"
            String[] parts = cseqHeader.trim().split(" ");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (NumberFormatException e) {
            // Return 0 if parsing fails
        }

        return 0;
    }

    /**
     * Checks if a SIP message has a To tag
     * 
     * @param msg SIP message
     * @return true if To header contains tag parameter, false otherwise
     */
    static public boolean hasToTag(String msg) {
        String toHeader = getHeaderValue(msg, "To:", "t:");
        if (toHeader == null) {
            return false;
        }

        return toHeader.contains(";tag=");
    }

    /**
     * Extracts the To tag value from a SIP message
     * 
     * @param msg SIP message
     * @return To tag value, or null if not found
     */
    static public String getToTag(String msg) {
        String toHeader = getHeaderValue(msg, "To:", "t:");
        if (toHeader == null || !toHeader.contains(";tag=")) {
            return null;
        }

        int tagStart = toHeader.indexOf(";tag=") + 5;
        int tagEnd = toHeader.indexOf(";", tagStart);
        if (tagEnd == -1) {
            tagEnd = toHeader.indexOf("\r\n", tagStart);
        }
        if (tagEnd == -1) {
            tagEnd = toHeader.length();
        }

        return toHeader.substring(tagStart, tagEnd);
    }

    /**
     * Gets a human-readable description of the INVITE type
     * 
     * @param msg SIP message
     * @return "Initial INVITE" or "Re-INVITE"
     */
    static public String getInviteType(String msg) {
        if (isInitialInvite(msg)) {
            return "Initial INVITE";
        } else {
            return "Re-INVITE";
        }
    }
}
