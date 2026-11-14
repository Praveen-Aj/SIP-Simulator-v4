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

import java.net.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import javax.swing.JOptionPane;
import java.nio.channels.spi.AbstractSelectableChannel;

/**
 *
 * @author Zarko Coklin
 */
public class TCPServerSocket extends TCPClientSocket
{

    public TCPServerSocket(
        InetAddress localIP,
        int localPort)
    {
        super(localIP, localPort);
    }

    @Override
    public boolean open(InetAddress remoteAddr,
        int remotePort)
    {
        try
        {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(super.localAddress, super.localPort));
            selector = Selector.open();

            if (selector != null)
            {
                SelectionKey key = server.register(selector, SelectionKey.OP_ACCEPT);
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

    @Override
    public SocketChannel getSocket()
    {
        try
        {
            return server.accept();
        }
        catch (IOException exc)
        {
            return null;
        }
    }

    @Override
    public boolean isDaemon()
    {
        return true;
    }
    public AbstractSelectableChannel getDefaultChannel()
    {
        return server;
    }

    private ServerSocketChannel server;
}
