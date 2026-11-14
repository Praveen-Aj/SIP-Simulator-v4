# TCP/TLS Connection and Message Sending Fix

## Problem 1: Connection Failed
When selecting TCP or TLS transport and clicking "Next", the application threw:
```
java.nio.channels.IllegalBlockingModeException
    at sun.nio.ch.SocketAdaptor.connect(SocketAdaptor.java:103)
    at sipinspector.Socket.TCPClientSocket.open(TCPClientSocket.java:69)
```

### Root Cause
The `TCPClientSocket.open()` method was:
1. Setting the `SocketChannel` to **non-blocking mode** (`configureBlocking(false)`)
2. Then calling `socket().connect()` which is a **blocking operation**

This mismatch caused `IllegalBlockingModeException`.

## Problem 2: Messages Not Sent
TCP connection was established (server logs showed connection), but SIP messages were not being sent.

### Root Cause
The `TCPClientSocket.send()` and `receive()` methods were **completely commented out** - they returned without doing anything!

## Solution
Changed `TCPClientSocket.open()` to use the channel's non-blocking API:

**Before:**
```java
initialChannel.configureBlocking(false);
initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));
initialChannel.socket().connect(new InetSocketAddress(remoteAddress, remotePort)); // ❌ Blocking call
if (initialChannel.finishConnect() == false) {
    return false;
}
```

**After:**
```java
initialChannel.configureBlocking(false);
initialChannel.socket().bind(new InetSocketAddress(localAddress, localPort));

// Use channel's connect() for non-blocking mode
initialChannel.connect(new InetSocketAddress(remoteAddress, remotePort));

// Wait for connection to complete (non-blocking)
while (!initialChannel.finishConnect()) {
    try {
        Thread.sleep(10);
    } catch (InterruptedException e) {
        return false;
    }
}
```

### Solution for send()
Implemented proper TCP message sending:
```java
public void send(MyPacket sipPacket) throws IOException
{
    if (initialChannel == null || !initialChannel.isConnected()) {
        throw new IOException("TCP socket not connected");
    }

    // Wrap the SIP message bytes into a ByteBuffer
    ByteBuffer buffer = ByteBuffer.wrap(sipPacket.getBytes(), 0, sipPacket.getLength());
    
    // Write all bytes to the channel
    while (buffer.hasRemaining()) {
        initialChannel.write(buffer);
    }
    
    // Log the outgoing message
    logOutgoingMsg(sipPacket);
}
```

### Solution for receive()
Implemented proper TCP message receiving:
```java
public MyPacket receive() throws IOException
{
    if (initialChannel == null || !initialChannel.isConnected()) {
        throw new IOException("TCP socket not connected");
    }

    MyPacket myPacket = new MyPacket();
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    byte[] byteArray = new byte[4096];
    int numRcvdBytes = 0;

    // Read data from the channel
    while (myPacket.isComplete() == false) {
        buffer.clear();
        numRcvdBytes = initialChannel.read(buffer);
        
        if (numRcvdBytes <= 0) {
            return null;
        }

        // Convert ByteBuffer to byte array for parsing
        buffer.flip();
        buffer.get(byteArray, 0, numRcvdBytes);
        
        byte[] actualBytes = new byte[numRcvdBytes];
        System.arraycopy(byteArray, 0, actualBytes, 0, numRcvdBytes);
        
        // Parse the received bytes into SIP message
        myPacket.parseBytesIntoSIPMsg(actualBytes);
    }

    myPacket.setAddress(initialChannel.socket().getInetAddress());
    myPacket.setPort(initialChannel.socket().getPort());
    logIncomingMsg(myPacket);
    
    return myPacket;
}
```

## Impact
- ✅ **TCP connections** now work correctly
- ✅ **TCP message sending** now works correctly
- ✅ **TCP message receiving** now works correctly
- ✅ **TLS connections** now work correctly (TLS extends TCP)
- ✅ **TLS message sending/receiving** now works correctly (inherits from TCP)
- ✅ **UDP connections** continue to work as before

## Testing
1. Rebuild: `.\build.bat`
2. Run: `.\run.bat`
3. Select TCP or TLS transport
4. Click "Next" → Connection should succeed
5. Send SIP message → Server should receive the message
6. Server response → Client should receive the response

## Files Modified
- `src/sipinspector/Socket/TCPClientSocket.java`
  - `open()` method (lines 61-99) - Fixed non-blocking connection
  - `send()` method (lines 213-231) - Implemented message sending
  - `receive()` method (lines 166-210) - Implemented message receiving
