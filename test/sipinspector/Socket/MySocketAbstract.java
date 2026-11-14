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
package sipinspector.Socket;

import java.io.FileOutputStream;
import sipinspector.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 *
 * @author Zarko Coklin
 */
public abstract class MySocketAbstract
{

    public abstract boolean open(InetAddress remoteAddress,
        int remotePort);

    public abstract boolean isOpen();

    public abstract Object getSocket();

    public abstract Selector getSelector();

    public abstract void close();

    public abstract InetAddress getLocalAddress();

    public abstract int getLocalPort();

    public abstract MyPacket receive() throws IOException;

    public abstract void send(MyPacket packet) throws IOException;

    public abstract String getTransportType();

    public abstract boolean isTransportUDP();

    public abstract SelectionKey getInitialKey();

    public abstract boolean isDaemon();

    public void logOutgoingMsg(MyPacket packet)
    {
        FileOutputStream fos = SIPInspectorMainFrame.getFos();
        if (fos == null)
        {
            return;
        }

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
        String time = sdf.format(cal.getTime());

        String txt = new String("From") + getLocalAddress().getHostAddress() +
            ":" + Integer.toString(getLocalPort()) +
            " - To:" + packet.getAddress().getHostAddress() +
            ":" + Integer.toString(packet.getPort()) +
            "    " + time + System.getProperty("line.separator") +
            "---------->" + System.getProperty("line.separator");
        try
        {
            synchronized (fos)
            {
                fos.write(txt.getBytes());
                fos.write(packet.getBytes(), 0, packet.getLength());
                fos.write(System.getProperty("line.separator").getBytes());
            }
        }
        catch (IOException excp)
        {
            try
            {
                fos.close();
            }
            catch (IOException ex)
            {
            }
        }
    }

    public void logIncomingMsg(MyPacket packet)
    {

        FileOutputStream fos = SIPInspectorMainFrame.getFos();
        if (fos == null)
        {
            return;
        }

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
        String time = sdf.format(cal.getTime());

        String txt = new String("From:" +
            packet.getAddress().getHostAddress() + ":" +
            Integer.toString(packet.getPort()) + " - To:" +
            getLocalAddress().getHostAddress() + ":" +
            Integer.toString(getLocalPort()) + "    " + time +
            System.getProperty("line.separator") +
            "<----------" + System.getProperty("line.separator"));
        try
        {
            synchronized (fos)
            {
                fos.write(txt.getBytes());
                fos.write(packet.getBytes(), 0, packet.length());
                fos.write(System.getProperty("line.separator").getBytes());
            }
        }
        catch (IOException excp)
        {
        }
    }
    
    public abstract void setQos();

}
