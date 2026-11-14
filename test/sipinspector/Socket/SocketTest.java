/*
 * Simple test for SIP Inspector Socket implementations
 */
import sipinspector.Socket.*;
import java.net.*;

public class SocketTest {

    public static void main(String[] args) {
        System.out.println("Testing SIP Inspector Socket Implementations");

        try {
            // Test UDP Socket creation
            System.out.println("\n=== Testing UDP Socket Creation ===");
            UDPClientSocket udpSocket = new UDPClientSocket(InetAddress.getByName("127.0.0.1"), 5060);
            System.out.println("UDP Socket created successfully");
            System.out.println("Transport type: " + udpSocket.getTransportType());
            System.out.println("Is UDP: " + udpSocket.isTransportUDP());
            udpSocket.close();

            // Test TCP Socket creation
            System.out.println("\n=== Testing TCP Socket Creation ===");
            TCPClientSocket tcpSocket = new TCPClientSocket(InetAddress.getByName("127.0.0.1"), 5060);
            System.out.println("TCP Socket created successfully");
            System.out.println("Transport type: " + tcpSocket.getTransportType());
            System.out.println("Is UDP: " + tcpSocket.isTransportUDP());
            tcpSocket.close();

            // Test TLS Socket creation
            System.out.println("\n=== Testing TLS Socket Creation ===");
            TLSClientSocket tlsSocket = new TLSClientSocket(InetAddress.getByName("127.0.0.1"), 5060);
            System.out.println("TLS Socket created successfully");
            System.out.println("Transport type: " + tlsSocket.getTransportType());
            System.out.println("Is UDP: " + tlsSocket.isTransportUDP());
            System.out.println("Is TLS enabled: " + tlsSocket.isTLSEnabled());
            tlsSocket.close();

            System.out.println("\n=== All socket implementations created successfully! ===");

        } catch (Exception e) {
            System.err.println("Error testing sockets: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
