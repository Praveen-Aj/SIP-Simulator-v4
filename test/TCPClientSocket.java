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
import java.net.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import javax.swing.JOptionPane;

/**
 *
 * @author Zarko Coklin
 */
public class TCPClientSocket extends MySocketAbstract
{

    public TCPClientSocket()
    {
    }

    public TCPClientSocket(
        InetAddress localIP,
        int local_Port)
    {
        localAddress = localIP;
        localPort = local_Port;
        try
        {
            selector = Selector.open();
        }
        catch (IOException ioexcp)
        {
            JOptionPane.showMessageDialog(null,
                ioexcp,
                "Socket status",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean open(InetAddress remoteAddress,
        int remotePort)
    {
        try
        {
            initialChannel = SocketChannel.open();
            initialChannel.configureBlocking(false);
            initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));
            initialChannel.socket().connect(new InetSocketAddress(remoteAddress, remotePort));
            if (initialChannel.finishConnect() == false)
            {
                return false;
            }
            if (selector != null)
            {
                SelectionKey key = initialChannel.register(selector,
                    initialChannel.validOps());
                ByteBuffer rcvdBytes = ByteBuffer.allocateDirect(4096);
                key.attach(rcvdBytes);
            }
        }
        catch (IOException ioexcp)
        {
            JOptionPane.showMessageDialog(null,
                ioexcp,
                "Socket status",
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    public boolean isOpen()
    {
        if (selector == null)
        {
            return false;
        }

        return selector.isOpen();
    }

    public SocketChannel getSocket()
    {
        return initialChannel;
    }

    public void close()
    {
        if (selector == null || selector.isOpen() == false)
        {
            return;
        }
        try
        {
            selector.close();
        }
        catch (IOException exc)
        {
        }
        selector = null;
    }

    public InetAddress getLocalAddress()
    {
        return localAddress;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public InetAddress getRemoteAddress(SelectionKey key)
    {
        SocketChannel sc = (SocketChannel)key.channel();

        if (sc == null)
        {
            return null;
        }
        return sc.socket().getInetAddress();
    }

    public int getRemotePort(SelectionKey key)
    {
        SocketChannel sc = (SocketChannel)key.channel();

        if (sc == null)
        {
            return 0;
        }
        return sc.socket().getPort();
    }

    public MyPacket receive() throws IOException
    {
        /*
        MyPacket myPacket = new MyPacket();
        int numRcvdBytes = 0;
        int numProcessedBytes = 0;
        SocketChannel sc = (SocketChannel)key.channel();
        ByteBuffer rcvdBytes;

        // Check if the socket channel is open and connected
        if (sc == null || sc.isOpen() == false || sc.isConnected() == false)
        {
            throw new IOException();
        }

        rcvdBytes = (ByteBuffer)key.attachment();
        rcvdBytes.clear();

        while (rcvdBytes != null && myPacket.isComplete() == false)
        {
            numRcvdBytes = sc.read(rcvdBytes);
            if (numRcvdBytes <= 0)
            {
                return null;
            }

            numProcessedBytes = myPacket.parseBytesIntoSIPMsg(rcvdBytes);
            if (numProcessedBytes < numRcvdBytes)
            {
                rcvdBytes.position(numProcessedBytes);
                break;
            }
            else
            {
                rcvdBytes.clear(); // reset buffer
            }
        }

        myPacket.setAddress(sc.socket().getInetAddress());
        myPacket.setPort(sc.socket().getPort());
        logIncomingMsg(key, myPacket);
        return myPacket;
         */

        return null;
    }

    public void send(MyPacket sipPacket) throws IOException
    {
        /*
        ByteBuffer rcvdBytes = (ByteBuffer)key.attachment();
        SocketChannel sc = (SocketChannel)key.channel();

        if (rcvdBytes == null || sc == null)
        {
        throw new IOException();
        }

        rcvdBytes = ByteBuffer.wrap(sipPacket.getBytes());
        key.attach(rcvdBytes);
        sc.register(selector, SelectionKey.OP_WRITE);
         */
    }

    public void send(SelectionKey key) throws IOException
    {
        SocketChannel sc = (SocketChannel)key.channel();
        sc.write((ByteBuffer)key.attachment());
    }

    public String getTransportType()
    {
        return "TCP";
    }

    public boolean isTransportUDP()
    {
        return false;
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
    
    public void setQos()
    {
        return;
    }

    protected Selector selector;

    protected InetAddress localAddress;

    protected int localPort;

    protected SelectionKey initialKey;

    private SocketChannel initialChannel;
}
