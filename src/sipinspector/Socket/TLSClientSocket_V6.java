/*
 *  SIP Inspector - TLS Client Socket (Version 6 Implementation)
 *  Uses keystore/truststore for proper certificate management
 */
package sipinspector.Socket;

import sipinspector.*;
import sipinspector.Utils.SecurityManager;
import sipinspector.Utils.TLSDebugLogger;
import java.net.*;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.ByteBuffer;
import javax.net.ssl.*;
import javax.swing.JOptionPane;

/**
 * TLS/SSL Socket implementation using Version 6 keystore/truststore approach
 * @author SIP Simulator Team
 */
public class TLSClientSocket_V6 extends TCPClientSocket
{
    private SSLSocket sslSocket;
    private SecurityManager securityManager;
    private boolean tlsEnabled = false;

    private static String lastCipherSuite = "None";
    private static String lastProtocol = "None";
    private static String lastPeerCertSubject = "None";
    private static String lastPeerCertIssuer = "None";
    private static String lastPeerCertValidity = "None";

    public static String getLastCipherSuite() { return lastCipherSuite; }
    public static String getLastProtocol() { return lastProtocol; }
    public static String getLastPeerCertSubject() { return lastPeerCertSubject; }
    public static String getLastPeerCertIssuer() { return lastPeerCertIssuer; }
    public static String getLastPeerCertValidity() { return lastPeerCertValidity; }

    public TLSClientSocket_V6()
    {
        super();
        securityManager = new SecurityManager();
    }

    public TLSClientSocket_V6(InetAddress localIP, int local_Port)
    {
        super(localIP, local_Port);
        securityManager = new SecurityManager();
    }

    @Override
    public boolean open(InetAddress remoteAddress, int remotePort)
    {
        TLSDebugLogger.log("=== TLS OPEN DEBUG ===");
        TLSDebugLogger.logf("Remote: %s:%d", remoteAddress, remotePort);
        TLSDebugLogger.logf("Local: %s:%d", localAddress, localPort);
        
        try
        {
            // Initialize SSL context with Version 6 approach
            // Trust all self-signed certificates by default
            TLSDebugLogger.log("Initializing SSL context...");
            SSLContext sslContext = securityManager.initializeSSLContext(true);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            TLSDebugLogger.log("SSL context initialized");

            // Create socket channel in blocking mode for TLS
            initialChannel = SocketChannel.open();
            initialChannel.configureBlocking(true); // TLS requires blocking mode
            
            // Enable socket reuse and set socket options
            initialChannel.socket().setReuseAddress(true);
            initialChannel.socket().setSoLinger(false, 0);
            
            // Bind to local address/port
            if (localPort == 0) {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, 0));
            } else {
                initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));
            }
            
            // Set connection timeout to prevent freeze
            initialChannel.socket().connect(new InetSocketAddress(remoteAddress, remotePort), 5000);
            
            // Wrap the connected socket with SSL
            Socket tcpSocket = initialChannel.socket();
            tcpSocket.setSoTimeout(10000); // 10 second timeout for handshake
            
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                tcpSocket, 
                remoteAddress.getHostAddress(), 
                remotePort, 
                true
            );

            // Configure SSL socket
            sslSocket.setSoTimeout(10000);
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

            // Start SSL handshake
            TLSDebugLogger.log("Starting TLS handshake...");
            sslSocket.startHandshake();
            TLSDebugLogger.log("TLS handshake completed successfully!");
            SSLSession session = sslSocket.getSession();
            lastCipherSuite = session.getCipherSuite();
            lastProtocol = session.getProtocol();
            TLSDebugLogger.log("Cipher suite: " + lastCipherSuite);
            TLSDebugLogger.log("Protocol: " + lastProtocol);
            try {
                java.security.cert.Certificate[] certs = session.getPeerCertificates();
                if (certs.length > 0 && certs[0] instanceof java.security.cert.X509Certificate) {
                    java.security.cert.X509Certificate x509 = (java.security.cert.X509Certificate) certs[0];
                    lastPeerCertSubject = x509.getSubjectDN().getName();
                    lastPeerCertIssuer = x509.getIssuerDN().getName();
                    lastPeerCertValidity = x509.getNotBefore() + " to " + x509.getNotAfter();
                } else {
                    lastPeerCertSubject = "Unknown Certificate Type";
                    lastPeerCertIssuer = "Unknown";
                    lastPeerCertValidity = "Unknown";
                }
            } catch (SSLPeerUnverifiedException e) {
                lastPeerCertSubject = "Unverified Peer";
                lastPeerCertIssuer = "Unverified";
                lastPeerCertValidity = "Unverified";
            } catch (Exception e) {
                lastPeerCertSubject = "None / Error reading certificate";
                lastPeerCertIssuer = "None";
                lastPeerCertValidity = "None";
            }

            tlsEnabled = true;
            TLSDebugLogger.log("TLS enabled = true");
            
            // DON'T switch to non-blocking mode for TLS!
            // SSLSocket requires blocking mode for read/write operations
            // The selector is not used for TLS communication
            TLSDebugLogger.log("Keeping channel in blocking mode for TLS");
            
            writeTlsDiagnostics();
            TLSDebugLogger.log("=== TLS OPEN COMPLETE - SUCCESS ===");
            return true;
        }
        catch (java.net.SocketTimeoutException timeout)
        {
            TLSDebugLogger.log("=== TLS OPEN FAILED - TIMEOUT ===");
            JOptionPane.showMessageDialog(null,
                "TLS connection timeout - Server may not support TLS on port " + remotePort + "\n" +
                "Verify server TLS configuration and port.",
                "TLS Timeout",
                JOptionPane.ERROR_MESSAGE);
            tlsEnabled = false;
            close();
            return false;
        }
        catch (Exception ioexcp)
        {
            TLSDebugLogger.log("=== TLS OPEN FAILED - EXCEPTION ===");
            TLSDebugLogger.log("Exception: " + ioexcp.getClass().getName());
            TLSDebugLogger.log("Message: " + ioexcp.getMessage());
            TLSDebugLogger.logException("TLS Open Exception:", ioexcp);
            
            JOptionPane.showMessageDialog(null,
                "TLS handshake failed: " + ioexcp.getMessage() + "\n" +
                "Server may not support TLS or requires different configuration.\n" +
                "Check security/README.txt for certificate setup.",
                "TLS Error",
                JOptionPane.ERROR_MESSAGE);
            tlsEnabled = false;
            close();
            return false;
        }
    }

    @Override
    public boolean isOpen()
    {
        return super.isOpen() && tlsEnabled && sslSocket != null && 
               sslSocket.isConnected() && !sslSocket.isClosed();
    }

    @Override
    public SocketChannel getSocket()
    {
        return super.getSocket();
    }
    
    public SSLSocket getSSLSocket()
    {
        return sslSocket;
    }

    @Override
    public void close()
    {
        TLSDebugLogger.log("=== TLS CLOSE DEBUG ===");
        TLSDebugLogger.log("Closing TLS socket...");
        TLSDebugLogger.logException("Stack trace for close():", new Exception("Close called"));
        
        try {
            java.io.File file = new java.io.File("config/tls_diagnostics.json");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // ignore
        }
        
        try {
            if (sslSocket != null) {
                sslSocket.close();
                TLSDebugLogger.log("SSL socket closed");
            }
        } catch (IOException e) {
            TLSDebugLogger.log("Error closing SSL socket: " + e.getMessage());
        }
        tlsEnabled = false;
        sslSocket = null;
        super.close();
        TLSDebugLogger.log("=== TLS CLOSE COMPLETE ===");
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

    public boolean isTLSEnabled()
    {
        return tlsEnabled;
    }
    
    @Override
    public void send(MyPacket sipPacket) throws IOException
    {
        if (sslSocket == null || !sslSocket.isConnected())
        {
            TLSDebugLogger.log("ERROR: TLS socket not connected!");
            throw new IOException("TLS socket not connected");
        }

        // Apply simulation packet corruption
        sipPacket = SocketSimulationHelper.corruptPacketIfNeeded(sipPacket);

        // Apply outgoing simulation (delay & drop)
        if (SocketSimulationHelper.simulateOutgoing(sipPacket.getAddress(), sipPacket.getPort(), "TLS")) {
            return;
        }

        TLSDebugLogger.log("=== TLS SEND DEBUG ===");
        TLSDebugLogger.log("Socket: " + (sslSocket != null ? sslSocket.getInetAddress() + ":" + sslSocket.getPort() : "null"));
        TLSDebugLogger.log("Connected: " + (sslSocket != null ? sslSocket.isConnected() : "null"));
        TLSDebugLogger.log("Closed: " + (sslSocket != null ? sslSocket.isClosed() : "null"));
        TLSDebugLogger.log("Packet length: " + sipPacket.getLength() + " bytes");
        
        // Log full SIP message for analysis
        String sipMessage = new String(sipPacket.getBytes(), 0, sipPacket.getLength());
        TLSDebugLogger.log("--- SIP MESSAGE START ---");
        TLSDebugLogger.log(sipMessage);
        TLSDebugLogger.log("--- SIP MESSAGE END ---");

        try {
            // Write directly to SSL socket output stream
            TLSDebugLogger.log("Writing " + sipPacket.getLength() + " bytes to SSL socket...");
            sslSocket.getOutputStream().write(sipPacket.getBytes(), 0, sipPacket.getLength());
            sslSocket.getOutputStream().flush();
            TLSDebugLogger.log("TLS SEND SUCCESS!");
            
            logOutgoingMsg(sipPacket);
        } catch (Exception e) {
            TLSDebugLogger.logException("ERROR during TLS send:", e);
            throw e;
        }
        TLSDebugLogger.log("=== TLS SEND COMPLETE ===");
        TLSDebugLogger.log("");  // Blank line for readability
    }
    
    @Override
    public MyPacket receive() throws IOException
    {
        // Only log when we actually receive data (reduce redundant logs)
        if (sslSocket == null || !sslSocket.isConnected())
        {
            TLSDebugLogger.log("ERROR: TLS socket not connected for receive");
            throw new IOException("TLS socket not connected");
        }
        
        MyPacket myPacket = new MyPacket();
        byte[] buffer = new byte[4096];
        int numRcvdBytes = 0;

        try {
            // Set a read timeout to avoid blocking forever
            sslSocket.setSoTimeout(100); // 100ms timeout for non-blocking behavior
            
            // Read data from SSL socket input stream
            while (myPacket.isComplete() == false)
            {
                try {
                    numRcvdBytes = sslSocket.getInputStream().read(buffer);
                    
                    if (numRcvdBytes <= 0)
                    {
                        // Don't log - this happens frequently when no data
                        return null;
                    }

                    // Only log when we actually receive data
                    if (!TLSDebugLogger.isEnabled()) {
                        // Skip logging if debug not enabled
                    } else {
                        TLSDebugLogger.log("=== TLS RECEIVE ===");
                        TLSDebugLogger.log("Received " + numRcvdBytes + " bytes from " + 
                                          sslSocket.getInetAddress() + ":" + sslSocket.getPort());
                    }
                    
                    byte[] actualBytes = new byte[numRcvdBytes];
                    System.arraycopy(buffer, 0, actualBytes, 0, numRcvdBytes);
                    
                    myPacket.parseBytesIntoSIPMsg(actualBytes);
                } catch (java.net.SocketTimeoutException timeout) {
                    // Timeout is normal - no data available, don't log
                    return null;
                }
            }

            myPacket.setAddress(sslSocket.getInetAddress());
            myPacket.setPort(sslSocket.getPort());
            
            // Log received SIP message for analysis
            String receivedMessage = new String(myPacket.getBytes(), 0, myPacket.getLength());
            TLSDebugLogger.log("TLS RECEIVE SUCCESS - SIP message complete");
            TLSDebugLogger.log("Received " + myPacket.getLength() + " bytes from " + 
                              sslSocket.getInetAddress() + ":" + sslSocket.getPort());
            TLSDebugLogger.log("--- RECEIVED SIP MESSAGE START ---");
            TLSDebugLogger.log(receivedMessage);
            TLSDebugLogger.log("--- RECEIVED SIP MESSAGE END ---");
            TLSDebugLogger.log("");  // Blank line for readability
            
            logIncomingMsg(myPacket);
            
            // Apply incoming simulation
            if (SocketSimulationHelper.simulateIncoming(myPacket.getAddress(), myPacket.getPort(), "TLS")) {
                return null;
            }

            return myPacket;
        } catch (Exception e) {
            TLSDebugLogger.logException("ERROR during TLS receive:", e);
            throw e;
        }
    }

    private void writeTlsDiagnostics() {
        try {
            java.io.File dir = new java.io.File("config");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.io.File file = new java.io.File(dir, "tls_diagnostics.json");
            
            String subj = escapeJson(lastPeerCertSubject);
            String iss = escapeJson(lastPeerCertIssuer);
            String val = escapeJson(lastPeerCertValidity);
            
            String srtpCipher = GlobalSettings.isSdpUseSavp() ? "AES_CM_128_HMAC_SHA1_80 (SDES)" : "Disabled (Plain RTP)";
            
            String json = "{" +
                "\"protocol\":\"" + escapeJson(lastProtocol) + "\"," +
                "\"cipher_suite\":\"" + escapeJson(lastCipherSuite) + "\"," +
                "\"peer_cert_subject\":\"" + subj + "\"," +
                "\"peer_cert_issuer\":\"" + iss + "\"," +
                "\"peer_cert_validity\":\"" + val + "\"," +
                "\"srtp_cipher\":\"" + srtpCipher + "\"" +
                "}";
                
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(json);
            }
        } catch (Exception e) {
            TLSDebugLogger.log("Failed to write TLS diagnostics: " + e.getMessage());
        }
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
