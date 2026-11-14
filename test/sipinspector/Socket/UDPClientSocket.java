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

import sipinspector.*;

import java.math.BigInteger;
import java.net.*;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 *
 * @author Zarko Coklin
 */
public class UDPClientSocket extends MySocketAbstract
{

    public UDPClientSocket(
            InetAddress localIP,
            int localPort) throws IOException
    {
        this.localAddress = localIP;
        this.localPort = localPort;
        byteBuffer = new byte[4096];

        try
        {
            socket = new DatagramSocket(new InetSocketAddress(localAddress, localPort));
            socket.setReuseAddress(true);
            socket.setTrafficClass(144);
            socket.setSoTimeout(100);
        }
        catch (IOException ioexcp)
        {
            throw ioexcp;
        }
    }

    public boolean open(InetAddress remoteAddress,
            int remotePort)
    {
        return true;
    }
    
    public void setQos()
    {
        try
        {
            // For testing, use default QoS value
            BigInteger bi = new BigInteger("0", 16);
            socket.setTrafficClass(bi.intValue());
        }
        catch (Exception e)
        {
            return;
        }
    }

    public boolean isOpen()
    {
        if (socket.isBound() == false)
        {
            return false;
        }

        return true;
    }
    
    public void setSocket(DatagramSocket sckt)
    {
        //socket = sckt;
    }

    public DatagramSocket getSocket()
    {
        return socket;
    }

    public void close()
    {

        socket.close();
    }

    public InetAddress getLocalAddress()
    {
        return localAddress;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public MyPacket receive() throws IOException
    {

        MyPacket myPacket = null;

        DatagramPacket packet = new DatagramPacket(byteBuffer, byteBuffer.length);
        if (socket == null)
        {
            throw new IOException();
        }
        try
        {
            socket.receive(packet);
        }
        catch (SocketTimeoutException exc)
        {
            return null;
        }

        myPacket = new MyPacket(packet);
        logIncomingMsg(myPacket);
        return myPacket;
    }

    public void send(MyPacket sipPacket) throws IOException
    {

        DatagramPacket packet = new DatagramPacket(sipPacket.getBytes(),
                sipPacket.length(),
                sipPacket.getAddress(),
                sipPacket.getPort());
        socket.send(packet);
        logOutgoingMsg(sipPacket);
    }

    public String getTransportType()
    {
        return "UDP";
    }

    public boolean isTransportUDP()
    {
        return true;
    }

    public boolean isDaemon()
    {
        return false;
    }

    public Selector getSelector()
    {
        return selector;
    }

    public SelectionKey getInitialKey()
    {
        return initialKey;
    }

    byte[] byteBuffer;

    protected Selector selector;

    protected InetAddress localAddress;

    protected int localPort;

    protected SelectionKey initialKey;

    protected DatagramSocket socket;
}
