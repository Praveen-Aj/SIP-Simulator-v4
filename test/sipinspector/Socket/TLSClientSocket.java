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
        // First establish TCP connection using parent class
        if (!super.open(remoteAddress, remotePort)) {
            return false;
        }

        try
        {
            // Initialize SSL context
            initializeSSLContext();

            // Convert the established TCP socket to SSL
            Socket tcpSocket = initialChannel.socket();
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(tcpSocket, tcpSocket.getInetAddress().getHostAddress(), tcpSocket.getPort(), true);

            // Configure SSL socket
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

            // Start SSL handshake
            sslSocket.startHandshake();

            tlsEnabled = true;
            return true;
        }
        catch (Exception ioexcp)
        {
            JOptionPane.showMessageDialog(null,
                "TLS handshake failed: " + ioexcp.getMessage(),
                "Socket status",
                JOptionPane.ERROR_MESSAGE);
            tlsEnabled = false;
            return false;
        }
    }

    @Override
    public boolean isOpen()
    {
        return super.isOpen() && tlsEnabled && sslSocket != null && sslSocket.isConnected() && !sslSocket.isClosed();
    }

    @Override
    public Object getSocket()
    {
        return sslSocket != null ? sslSocket : super.getSocket();
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
}
