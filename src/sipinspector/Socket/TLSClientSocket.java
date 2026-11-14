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
import javax.net.ssl.*;
import javax.swing.JOptionPane;

/**
 * TLS/SSL Socket implementation extending TCP functionality
 * @author SIP Simulator Team
 */
public class TLSClientSocket extends TCPClientSocket
{
    private SSLSocket sslSocket;
    private SSLContext sslContext;
    private SSLSocketFactory sslSocketFactory;
    private boolean tlsEnabled = false;

    public TLSClientSocket()
    {
        super();
    }

    public TLSClientSocket(
        InetAddress localIP,
        int local_Port)
    {
        super(localIP, local_Port);
    }

    /**
     * Initialize SSL context with default trust manager
     */
    private void initializeSSLContext() throws Exception
    {
        sslContext = SSLContext.getInstance("TLS");

        // Create trust manager that accepts all certificates (for testing)
        // In production, this should be replaced with proper certificate validation
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };

        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        sslSocketFactory = sslContext.getSocketFactory();
    }

    @Override
    public boolean open(InetAddress remoteAddress, int remotePort)
    {
        try
        {
            // Initialize SSL context first
            initializeSSLContext();

            // For TLS, we need to use blocking mode
            // Create a new socket channel in blocking mode
            initialChannel = SocketChannel.open();
            initialChannel.configureBlocking(true); // TLS requires blocking mode
            
            // Enable socket reuse and set socket options BEFORE binding
            initialChannel.socket().setReuseAddress(true);
            initialChannel.socket().setSoLinger(false, 0); // Don't wait on close
            
            // Bind to local address/port (use port 0 for auto-assignment if localPort is 0)
            if (localPort == 0) {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, 0));
            } else {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));
            }
            
            // Set connection timeout to prevent freeze
            initialChannel.socket().connect(new InetSocketAddress(remoteAddress, remotePort), 5000); // 5 second timeout
            
            // Wrap the connected socket with SSL
            Socket tcpSocket = initialChannel.socket();
            
            // Set socket timeout for handshake to prevent freeze
            tcpSocket.setSoTimeout(10000); // 10 second timeout for handshake
            
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                tcpSocket, 
                remoteAddress.getHostAddress(), 
                remotePort, 
                true
            );

            // Configure SSL socket with timeout
            sslSocket.setSoTimeout(10000); // 10 second timeout
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

            // Start SSL handshake (with timeout protection)
            sslSocket.startHandshake();

            tlsEnabled = true;
            
            // Register with selector if available (for compatibility)
            if (selector != null)
            {
                // Switch back to non-blocking for selector registration
                initialChannel.configureBlocking(false);
                SelectionKey key = initialChannel.register(selector, initialChannel.validOps());
                ByteBuffer rcvdBytes = ByteBuffer.allocateDirect(4096);
                key.attach(rcvdBytes);
            }
            
            return true;
        }
        catch (java.net.SocketTimeoutException timeout)
        {
            showErrorMessage(
                "TLS connection timeout - Server may not support TLS on port " + remotePort + "\n" +
                "Check if server is configured for TLS and port is correct.",
                "TLS Timeout");
            tlsEnabled = false;
            close(); // Clean up
            return false;
        }
        catch (Exception ioexcp)
        {
            showErrorMessage(
                "TLS handshake failed: " + ioexcp.getMessage() + "\n" +
                "Server may not support TLS or requires different configuration.",
                "TLS Error");
            tlsEnabled = false;
            close(); // Clean up
            return false;
        }
    }

    private static void showErrorMessage(String message, String title)
    {
        if (java.awt.GraphicsEnvironment.isHeadless() || Boolean.getBoolean("sip.headless") || Boolean.getBoolean("tls.autoAcceptUntrusted"))
        {
            System.err.println("[" + title + "] " + message);
        }
        else
        {
            try
            {
                JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
            }
            catch (Throwable t)
            {
                System.err.println("[" + title + "] " + message);
            }
        }
    }

    @Override
    public boolean isOpen()
    {
        return super.isOpen() && tlsEnabled && sslSocket != null && sslSocket.isConnected() && !sslSocket.isClosed();
    }

    @Override
    public SocketChannel getSocket()
    {
        // Return the underlying SocketChannel, not the SSLSocket
        // SSLSocket wraps the channel but we need to return SocketChannel for compatibility
        return super.getSocket();
    }
    
    /**
     * Get the SSL socket for TLS-specific operations
     */
    public SSLSocket getSSLSocket()
    {
        return sslSocket;
    }

    @Override
    public void close()
    {
        try {
            if (sslSocket != null) {
                sslSocket.close();
            }
        } catch (IOException e) {
            // Ignore close exceptions
        }
        tlsEnabled = false;
        sslSocket = null;

        // Call parent close to handle channel cleanup
        super.close();
    }

    @Override
    public String getTransportType()
    {
        return "TLS";
    }

    @Override
    public boolean isTransportUDP()
    {
        return false;
    }

    /**
     * Check if TLS is enabled and active
     */
    public boolean isTLSEnabled()
    {
        return tlsEnabled;
    }
    
    /**
     * Override send to use SSL socket streams
     */
    @Override
    public void send(MyPacket sipPacket) throws IOException
    {
        if (sslSocket == null || !sslSocket.isConnected())
        {
            throw new IOException("TLS socket not connected");
        }

        // Apply simulation packet corruption
        sipPacket = SocketSimulationHelper.corruptPacketIfNeeded(sipPacket);

        // Apply outgoing simulation (delay & drop)
        if (SocketSimulationHelper.simulateOutgoing(sipPacket.getAddress(), sipPacket.getPort(), "TLS")) {
            return;
        }

        // Write directly to SSL socket output stream
        sslSocket.getOutputStream().write(sipPacket.getBytes(), 0, sipPacket.getLength());
        sslSocket.getOutputStream().flush();
        
        // Log the outgoing message
        logOutgoingMsg(sipPacket);
    }
    
    /**
     * Override receive to use SSL socket streams
     */
    @Override
    public MyPacket receive() throws IOException
    {
        if (sslSocket == null || !sslSocket.isConnected())
        {
            throw new IOException("TLS socket not connected");
        }

        MyPacket myPacket = new MyPacket();
        byte[] buffer = new byte[4096];
        int numRcvdBytes = 0;

        // Read data from SSL socket input stream
        while (myPacket.isComplete() == false)
        {
            numRcvdBytes = sslSocket.getInputStream().read(buffer);
            
            if (numRcvdBytes <= 0)
            {
                // No data available or connection closed
                return null;
            }

            // Create a properly sized byte array for parsing
            byte[] actualBytes = new byte[numRcvdBytes];
            System.arraycopy(buffer, 0, actualBytes, 0, numRcvdBytes);
            
            // Parse the received bytes into SIP message
            myPacket.parseBytesIntoSIPMsg(actualBytes);
        }

        // Set remote address and port
        myPacket.setAddress(sslSocket.getInetAddress());
        myPacket.setPort(sslSocket.getPort());
        
        // Log the incoming message
        logIncomingMsg(myPacket);
        
        // Apply incoming simulation
        if (SocketSimulationHelper.simulateIncoming(myPacket.getAddress(), myPacket.getPort(), "TLS")) {
            return null;
        }

        return myPacket;
    }
}
