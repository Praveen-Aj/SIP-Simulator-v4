# TLS Handshake and Socket Reuse Fix

## Problem 1: TLS Handshake Failure
TLS connections failed with "handshake null" error.

### Root Cause
The original TLS implementation tried to wrap a **non-blocking** `SocketChannel` with an `SSLSocket`, but SSL/TLS handshake requires **blocking I/O**.

### Solution
Modified `TLSClientSocket.open()` to:
1. Create socket channel in **blocking mode** for TLS
2. Establish TCP connection
3. Wrap with `SSLSocket` and perform handshake
4. Optionally switch to non-blocking mode for selector registration

```java
// Create socket in blocking mode for TLS
initialChannel = SocketChannel.open();
initialChannel.configureBlocking(true); // TLS requires blocking mode
initialChannel.socket().setReuseAddress(true);
initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));

// Connect to remote server
initialChannel.connect(new InetSocketAddress(remoteAddress, remotePort));

// Wrap with SSL and perform handshake
Socket tcpSocket = initialChannel.socket();
sslSocket = (SSLSocket) sslSocketFactory.createSocket(
    tcpSocket, 
    remoteAddress.getHostAddress(), 
    remotePort, 
    true
);

sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
sslSocket.startHandshake(); // ✅ Now works in blocking mode
```

### TLS Send/Receive
Implemented TLS-specific send/receive using SSL socket streams:

```java
@Override
public void send(MyPacket sipPacket) throws IOException
{
    // Write directly to SSL socket output stream
    sslSocket.getOutputStream().write(sipPacket.getBytes(), 0, sipPacket.getLength());
    sslSocket.getOutputStream().flush();
    logOutgoingMsg(sipPacket);
}

@Override
public MyPacket receive() throws IOException
{
    // Read from SSL socket input stream
    numRcvdBytes = sslSocket.getInputStream().read(buffer);
    // ... parse and return SIP message
}
```

---

## Problem 2: Address Already in Use
When restarting the application 2-3 times quickly, it showed "address already in use" error.

### Root Cause
TCP sockets enter a `TIME_WAIT` state after closing, preventing immediate reuse of the same local address/port combination.

### Solution
Enabled `SO_REUSEADDR` socket option in both `TCPClientSocket` and `TLSClientSocket`:

```java
// Enable socket reuse to avoid "address already in use" errors
initialChannel.socket().setReuseAddress(true);
```

This allows the socket to bind to an address that is in `TIME_WAIT` state, enabling immediate restart of the application.

---

## Impact
- ✅ **TLS handshake** - now completes successfully
- ✅ **TLS send/receive** - messages transmitted over encrypted connection
- ✅ **Socket reuse** - can restart app immediately without "address in use" errors
- ✅ **TCP** - continues to work correctly
- ✅ **UDP** - continues to work correctly

## Testing
1. **TLS Test:**
   - Select TLS transport
   - Click Next → TLS handshake should succeed
   - Send message → encrypted transmission to server
   - Receive response → decrypted response from server

2. **Socket Reuse Test:**
   - Start app with TCP/TLS
   - Close app
   - Immediately restart → should work without "address in use" error

## Files Modified
- `src/sipinspector/Socket/TLSClientSocket.java`
  - `open()` - Fixed to use blocking mode for handshake
  - `send()` - Implemented using SSL streams
  - `receive()` - Implemented using SSL streams
  
- `src/sipinspector/Socket/TCPClientSocket.java`
  - `open()` - Added `setReuseAddress(true)`
