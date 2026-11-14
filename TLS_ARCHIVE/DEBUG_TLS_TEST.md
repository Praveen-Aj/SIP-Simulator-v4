# TLS Debug Testing Guide

## ✅ Debug Logging Added

Comprehensive debug logging has been added to trace every step of TLS operation:

### What Will Be Logged:

#### 1. TLS Connection (when clicking "Next"):
```
=== TLS OPEN DEBUG ===
Remote: 10.94.161.179:5061
Local: 10.94.161.179:0
Initializing SSL context...
SSL context initialized
Starting TLS handshake...
TLS handshake completed successfully!
Cipher suite: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
Protocol: TLSv1.2
TLS enabled = true
=== TLS OPEN COMPLETE - SUCCESS ===
```

#### 2. TLS Send (when sending SIP message):
```
=== TLS SEND DEBUG ===
sslSocket: SSLSocket[...]
sslSocket.isConnected(): true
sslSocket.isClosed(): false
Packet length: 456
Packet content preview: REGISTER sip:10.94.161.179 SIP/2.0
Via: SIP/2.0/TLS...
Writing 456 bytes to SSL socket...
Flushing SSL socket output stream...
TLS SEND SUCCESS!
=== TLS SEND COMPLETE ===
```

---

## 🧪 How to Test with Debug Output

### Step 1: Run from Command Line (to see console output)
```cmd
cd "<project-root>"
java -jar SIPInspector.jar
```

### Step 2: Configure TLS Connection
```
Remote IP: 10.94.161.179
Remote Port: 5061
Local Port: 0 (auto)
Transport: TLS
From: 1000
To: 1000
```

### Step 3: Click "Next"
Watch console for:
```
=== TLS OPEN DEBUG ===
...
=== TLS OPEN COMPLETE - SUCCESS ===
```

### Step 4: Load Scenario and Send
```
File → Load Scenario → Reg_client.txt
Click "Run"
```

Watch console for:
```
=== TLS SEND DEBUG ===
...
=== TLS SEND COMPLETE ===
```

### Step 5: Check Server Logs
Server should show:
```
[CConnectionTls::receiveMsg] Receiving encrypted data
RECEIVE MESSAGE FROM NETWORK (10.94.161.179:5061 [TLS])
REGISTER sip:10.94.161.179 SIP/2.0
...
```

---

## 🔍 What to Look For

### If TLS Open Succeeds:
- ✅ "TLS handshake completed successfully!"
- ✅ "TLS enabled = true"
- ✅ "=== TLS OPEN COMPLETE - SUCCESS ==="

### If TLS Send is Called:
- ✅ "=== TLS SEND DEBUG ==="
- ✅ "Packet length: XXX"
- ✅ "Writing XXX bytes to SSL socket..."
- ✅ "TLS SEND SUCCESS!"

### If TLS Send is NOT Called:
- ❌ No "=== TLS SEND DEBUG ===" message
- **Reason**: No scenario loaded or no message to send

---

## 📋 Possible Issues and Debug Output

### Issue 1: Send Method Never Called
**Debug output:**
```
=== TLS OPEN COMPLETE - SUCCESS ===
(nothing else)
```
**Cause:** No scenario loaded or no message configured
**Solution:** Load a scenario and click "Run"

### Issue 2: Socket Not Connected
**Debug output:**
```
=== TLS SEND DEBUG ===
sslSocket.isConnected(): false
ERROR: TLS socket not connected!
```
**Cause:** TLS handshake failed or connection closed
**Solution:** Check TLS open logs for errors

### Issue 3: Send Fails with Exception
**Debug output:**
```
=== TLS SEND DEBUG ===
Writing XXX bytes to SSL socket...
ERROR during TLS send: [error message]
```
**Cause:** Network error or socket closed
**Solution:** Check exception details

---

## 🎯 Expected Full Flow

### Console Output:
```
=== TLS OPEN DEBUG ===
Remote: 10.94.161.179:5061
Local: 10.94.161.179:12345
Initializing SSL context...
SSL context initialized
Starting TLS handshake...
TLS handshake completed successfully!
Cipher suite: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
Protocol: TLSv1.2
TLS enabled = true
=== TLS OPEN COMPLETE - SUCCESS ===

(user loads scenario and clicks Run)

=== TLS SEND DEBUG ===
sslSocket: SSLSocket[addr=/10.94.161.179,port=5061,localport=12345]
sslSocket.isConnected(): true
sslSocket.isClosed(): false
Packet length: 456
Packet content preview: REGISTER sip:10.94.161.179 SIP/2.0
Via: SIP/2.0/TLS 10.94.161.179:12345;branch=z9hG4bK...
Writing 456 bytes to SSL socket...
Flushing SSL socket output stream...
TLS SEND SUCCESS!
=== TLS SEND COMPLETE ===
```

### Server Logs:
```
[CConnectionTls::receiveMsg] Receiving encrypted data
RECEIVE MESSAGE FROM NETWORK (10.94.161.179:5061 [TLS])
REGISTER sip:10.94.161.179 SIP/2.0
Via: SIP/2.0/TLS 10.94.161.179:12345;branch=z9hG4bK...
From: <sip:1000@10.94.161.179>;tag=...
To: <sip:1000@10.94.161.179>
...
```

---

## ✅ Action Items

1. **Run from command line** (not double-click) to see console output
2. **Configure TLS** (port 5061)
3. **Click "Next"** and watch for "TLS OPEN COMPLETE - SUCCESS"
4. **Load scenario** (Reg_client.txt)
5. **Click "Run"** and watch for "TLS SEND DEBUG"
6. **Share console output** if issues occur

The debug logs will show exactly what's happening at each step! 🔍
