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

import sipinspector.Utils.SIPParser;
import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 *
 * @author Zarko Coklin
 */
public class MyPacket
{

    public MyPacket(String msgText)
    {
        messageTxt = msgText;
        bytes = msgText.getBytes();
        len = msgText.length();
    }

    public MyPacket()
    {
        messageTxt = new String("");
        bytes = messageTxt.getBytes();
        len = 0;
        remoteIP = null;
        remotePort = 0;
        complete = false;

    }

    public MyPacket(DatagramPacket packet)
    {
        messageTxt = new String(packet.getData(), 0, packet.getLength());
        bytes = packet.getData();
        len = packet.getLength();
        setAddress(packet.getAddress());
        setPort(packet.getPort());
        complete = true;
    }

    public String getMessageTxt()
    {
        return messageTxt;
    }

    public int length()
    {
        return len;
    }

    public byte[] getBytes()
    {
        return bytes;
    }

    public InetAddress getAddress()
    {
        return remoteIP;
    }

    public void setAddress(InetAddress address)
    {
        remoteIP = address;
    }

    public int getPort()
    {
        return remotePort;
    }

    public void setPort(int port)
    {
        remotePort = port;
    }

    /*
     * Function tries to make SIP-sense from received bytes through TCP socket.
     * Once a SIP message end is detected, len is initialized to zero and 
     * byte array is updated so it has ONLY not-yet-parsed bytes.
     * Function returns number of read bytes from the stream.
     */
    public int parseBytesIntoSIPMsg(byte[] rcvdBytes)
    {
        String tmpByteHolder = messageTxt + new String(rcvdBytes);
        int totalLength = tmpByteHolder.length();
        int contLenInt = 0;

        int pos = tmpByteHolder.indexOf("\r\n\r\n");
        if (pos == -1)
        {
            messageTxt = tmpByteHolder;
            bytes = messageTxt.getBytes();
            len = bytes.length;
            complete = true;
            return rcvdBytes.length;
        }
        else
        {
            String contLenValue =
                SIPParser.getHeaderValue(tmpByteHolder, "Content-Length", "l");
            try
            {
                contLenInt = Integer.parseInt(contLenValue);
            }
            catch (NumberFormatException exc)
            {
                contLenInt = 0;
            }
            if (contLenInt + pos + 4 > totalLength)
            {
                messageTxt = tmpByteHolder;
                bytes = messageTxt.getBytes();
                len = bytes.length;
                complete = false;
                return rcvdBytes.length;
            }
            messageTxt = tmpByteHolder.substring(0, pos + 4 + contLenInt);
            bytes = messageTxt.getBytes();
            int bytesProcessed = pos + 4 + contLenInt - len;
            len = bytes.length;
            complete = true;
            return bytesProcessed;
        }
    }

    public int getLength()
    {
        return len;
    }

    public boolean isComplete()
    {
        return complete;
    }

    private int len;
    /* In case of TCP stream socket, byes is a placeholder for received
     * byes. len keeps track until wha point in bytes array SIP content was
     * parsed
     */

    private byte[] bytes;

    private String messageTxt;

    InetAddress remoteIP;

    int remotePort;

    boolean complete;
}
