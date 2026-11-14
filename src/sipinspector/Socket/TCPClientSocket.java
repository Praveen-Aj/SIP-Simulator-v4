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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import javax.swing.JOptionPane;

import sipinspector.MyPacket;
import sipinspector.Utils.TLSDebugLogger;

/**
 *
 * @author Zarko Coklin
 */
public class TCPClientSocket extends MySocketAbstract {

    public TCPClientSocket() {
    }

    public TCPClientSocket(
            InetAddress localIP,
            int local_Port) {
        localAddress = localIP;
        localPort = local_Port;
        try {
            selector = Selector.open();
        } catch (IOException ioexcp) {
            JOptionPane.showMessageDialog(null,
                    ioexcp,
                    "Socket status",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean open(InetAddress remoteAddress,
            int remotePort) {
        // Check if TCP debug logging is enabled
        String tcpDebug = System.getProperty("tcp.debug");
        if ("true".equalsIgnoreCase(tcpDebug)) {
            TLSDebugLogger.enable("TCP");
        }

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("=== TCP OPEN DEBUG ===");
            TLSDebugLogger.log("Remote: " + remoteAddress + ":" + remotePort);
            TLSDebugLogger.log("Local: " + localAddress + ":" + localPort);
        }

        try {
            initialChannel = SocketChannel.open();
            initialChannel.configureBlocking(false);

            // Enable socket reuse and set socket options BEFORE binding
            initialChannel.socket().setReuseAddress(true);
            initialChannel.socket().setSoLinger(false, 0); // Don't wait on close

            // Bind to local address/port (use port 0 for auto-assignment if localPort is 0)
            if (localPort == 0) {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, 0));
            } else {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));
            }

            // Use channel's connect() for non-blocking mode, not socket().connect()
            initialChannel.connect(new InetSocketAddress(remoteAddress, remotePort));

            // Wait for connection to complete (non-blocking)
            while (!initialChannel.finishConnect()) {
                // Connection in progress, keep trying
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return false;
                }
            }

            if (selector != null) {
                SelectionKey key = initialChannel.register(selector,
                        initialChannel.validOps());
                ByteBuffer rcvdBytes = ByteBuffer.allocateDirect(4096);
                key.attach(rcvdBytes);
            }

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("TCP connection established");
                TLSDebugLogger.log("=== TCP OPEN COMPLETE ===");
                TLSDebugLogger.log("");
            }
        } catch (IOException ioexcp) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logException("TCP OPEN FAILED:", ioexcp);
            }
            close();
            return false;
        }
        return true;
    }

    public boolean isOpen() {
        if (selector == null) {
            return false;
        }

        return selector.isOpen();
    }

    public SocketChannel getSocket() {
        return initialChannel;
    }

    public void close() {
        if (selector == null || selector.isOpen() == false) {
            return;
        }
        try {
            selector.close();
        } catch (IOException exc) {
        }
        selector = null;
        streamFramer.reset();
    }

    public InetAddress getLocalAddress() {
        return localAddress;
    }

    public int getLocalPort() {
        return localPort;
    }

    public InetAddress getRemoteAddress(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();

        if (sc == null) {
            return null;
        }
        return sc.socket().getInetAddress();
    }

    public int getRemotePort(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();

        if (sc == null) {
            return 0;
        }
        return sc.socket().getPort();
    }

    protected final sipinspector.Utils.SipStreamFramer streamFramer = new sipinspector.Utils.SipStreamFramer();

    public MyPacket receive() throws IOException {
        if (initialChannel == null || !initialChannel.isConnected()) {
            throw new IOException("TCP socket not connected");
        }

        // 1. If we already have a complete framed message buffered from a previous multi-message read, return it immediately
        byte[] readyMsg = streamFramer.pollNextMessage();
        if (readyMsg != null) {
            return buildAndLogPacket(readyMsg);
        }

        ByteBuffer buffer = ByteBuffer.allocate(4096);
        byte[] byteArray = new byte[4096];

        // 2. Read from channel until at least one complete message is framed
        while (!streamFramer.hasNextMessage()) {
            ((java.nio.Buffer) buffer).clear();
            int numRcvdBytes = initialChannel.read(buffer);

            if (numRcvdBytes < 0) {
                // Remote peer cleanly closed connection (EOF)
                return null;
            }
            if (numRcvdBytes == 0) {
                // Non-blocking channel has no data available at this instant
                return null;
            }

            ((java.nio.Buffer) buffer).flip();
            buffer.get(byteArray, 0, numRcvdBytes);
            streamFramer.feed(byteArray, 0, numRcvdBytes);
        }

        byte[] assembled = streamFramer.pollNextMessage();
        if (assembled == null) {
            return null;
        }

        return buildAndLogPacket(assembled);
    }

    private MyPacket buildAndLogPacket(byte[] msgBytes) {
        MyPacket myPacket = new MyPacket(msgBytes, msgBytes.length);
        myPacket.setAddress(initialChannel.socket().getInetAddress());
        myPacket.setPort(initialChannel.socket().getPort());

        if (TLSDebugLogger.isEnabled()) {
            String receivedMessage = myPacket.getMessageTxt();
            TLSDebugLogger.log("=== TCP RECV DEBUG ===");
            TLSDebugLogger.log(
                    "Socket: " + initialChannel.socket().getInetAddress() + ":" + initialChannel.socket().getPort());
            TLSDebugLogger.log("Received " + myPacket.getLength() + " bytes");
            TLSDebugLogger.log("--- SIP MESSAGE START ---");
            TLSDebugLogger.log(receivedMessage);
            TLSDebugLogger.log("--- SIP MESSAGE END ---");
        }

        // Log the incoming message
        logIncomingMsg(myPacket);

        // Apply incoming simulation
        if (SocketSimulationHelper.simulateIncoming(myPacket.getAddress(), myPacket.getPort(), "TCP")) {
            return null;
        }

        return myPacket;
    }

    public void send(MyPacket sipPacket) throws IOException {
        if (initialChannel == null || !initialChannel.isConnected()) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("ERROR: TCP socket not connected!");
            }
            throw new IOException("TCP socket not connected");
        }

        // Apply simulation packet corruption
        sipPacket = SocketSimulationHelper.corruptPacketIfNeeded(sipPacket);

        // Apply outgoing simulation (delay & drop)
        if (SocketSimulationHelper.simulateOutgoing(sipPacket.getAddress(), sipPacket.getPort(), "TCP")) {
            return;
        }

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("=== TCP SEND DEBUG ===");
            TLSDebugLogger.log("Socket: " + initialChannel.getRemoteAddress());
            TLSDebugLogger.log("Packet length: " + sipPacket.getLength() + " bytes");
            String sipMessage = new String(sipPacket.getBytes(), 0, sipPacket.getLength());
            TLSDebugLogger.log("--- SIP MESSAGE START ---");
            TLSDebugLogger.log(sipMessage);
            TLSDebugLogger.log("--- SIP MESSAGE END ---");
        }

        // Wrap the SIP message bytes into a ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(sipPacket.getBytes(), 0, sipPacket.getLength());

        // Write all bytes to the channel
        while (buffer.hasRemaining()) {
            initialChannel.write(buffer);
        }

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("TCP SEND SUCCESS!");
            TLSDebugLogger.log("=== TCP SEND COMPLETE ===");
            TLSDebugLogger.log("");
        }

        // Log the outgoing message
        logOutgoingMsg(sipPacket);
    }

    public void send(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        sc.write((ByteBuffer) key.attachment());
    }

    public String getTransportType() {
        return "TCP";
    }

    public boolean isTransportUDP() {
        return false;
    }

    public boolean isDaemon() {
        return false;
    }

    public Selector getSelector() {
        return selector;
    }

    public SelectionKey getInitialKey() {
        return initialKey;
    }

    public void setQos() {
        return;
    }

    protected Selector selector;

    protected InetAddress localAddress;

    protected int localPort;

    protected SelectionKey initialKey;

    protected SocketChannel initialChannel;
}
