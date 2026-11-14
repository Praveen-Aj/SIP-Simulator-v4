# TLS Certificate Setup Guide

## Understanding the "TLS handshake failed: null" Error

This error occurs because:
1. Your server requires proper TLS certificates
2. The client (SIP Inspector) doesn't trust the server's certificate
3. Or the server rejects the connection due to certificate validation

## How TLS Works in SIP

### Server Side (Your SIP Server)
Your server needs:
- **Server Certificate** (`server.crt` or `cacert.pem`)
- **Server Private Key** (`server.key` or `cakey.pem`)

### Client Side (SIP Inspector)
The client needs to **trust** the server's certificate by either:
- Having the server's certificate in its trust store, OR
- Trusting the Certificate Authority (CA) that signed the server's certificate, OR
- Using "accept all certificates" mode (insecure, for testing only)

---

## Step 1: Check Your Server's TLS Configuration

### Find Server Certificate Files
On your SIP server, locate:
```bash
# Common locations:
/etc/ssl/certs/
/usr/local/etc/ssl/
/opt/sip/certs/
```

Look for files like:
- `server.crt`, `server.pem`, `cacert.pem` (certificate)
- `server.key`, `cakey.pem` (private key)

### Verify Server is Using TLS
Check server logs or configuration to confirm:
- TLS is enabled on port 5060
- Certificate files are loaded
- TLS version (1.2, 1.3) is configured

---

## Step 2: Get Server Certificate

### Option A: Export from Server
```bash
# On your server, copy the certificate
scp user@10.94.161.179:/path/to/server.crt ./server.crt
```

### Option B: Retrieve via OpenSSL
```bash
# Get certificate from running server
openssl s_client -connect 10.94.161.179:5060 -showcerts < /dev/null 2>/dev/null | \
  openssl x509 -outform PEM > server.crt
```

---

## Step 3: Create Certificate Directory in SIP Inspector

Create a `security` folder:
```
SIPInspector/trunk/
├── security/
│   ├── server.crt          # Server's certificate
│   ├── truststore.jks      # Java trust store (optional)
│   └── README.txt          # Certificate info
```

---

## Step 4: Two Approaches to Fix TLS

### Approach 1: Trust Specific Server Certificate (Recommended)

Import server certificate into Java trust store:
```bash
cd security/

# Create trust store and import server certificate
keytool -import -alias sipserver \
  -file server.crt \
  -keystore truststore.jks \
  -storepass changeit

# Verify
keytool -list -keystore truststore.jks -storepass changeit
```

Then update `TLSClientSocket.java` to use this trust store.

### Approach 2: Accept All Certificates (Current - Testing Only)

Current implementation accepts ANY certificate (insecure):
```java
// This is what we have now - accepts all certs
TrustManager[] trustAllCerts = new TrustManager[]{
    new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }
        public void checkClientTrusted(...) { }
        public void checkServerTrusted(...) { }
    }
};
```

**Problem:** If this doesn't work, your server might be:
1. Requiring client certificates (mutual TLS)
2. Using a specific TLS version
3. Requiring specific cipher suites

---

## Step 5: Debug TLS Connection

### Test with OpenSSL
```bash
# Test TLS connection to your server
openssl s_client -connect 10.94.161.179:5060 -tls1_2 -showcerts

# If successful, you'll see:
# - Certificate chain
# - SSL handshake details
# - "Verify return code: 0 (ok)"

# If it fails, you'll see the error
```

### Common Issues

#### Issue: "SSL_ERROR_WANT_READ"
**Cause:** Server expecting client certificate

**Solution:** Server requires mutual TLS. Need to:
1. Generate client certificate
2. Send to server for signing
3. Configure SIP Inspector to use client cert

#### Issue: "Certificate verify failed"
**Cause:** Server certificate not trusted

**Solution:** Import server certificate to trust store (Approach 1 above)

#### Issue: "Protocol version mismatch"
**Cause:** Server uses TLS 1.0/1.1, we use 1.2/1.3

**Solution:** Update `TLSClientSocket.java`:
```java
sslSocket.setEnabledProtocols(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"});
```

---

## Step 6: Check if Server Requires Mutual TLS

### What is Mutual TLS?
- **Normal TLS:** Only server has certificate, client trusts it
- **Mutual TLS (mTLS):** Both server AND client have certificates

### Check Server Configuration
Look for settings like:
```
tls_verify_client = yes
tls_require_client_cert = true
```

If enabled, you need to:
1. Generate client certificate
2. Get it signed by server's CA
3. Configure SIP Inspector to send it

---

## Next Steps

1. **Run OpenSSL test** to see exact error:
   ```bash
   openssl s_client -connect 10.94.161.179:5060 -tls1_2
   ```

2. **Check server logs** when SIP Inspector tries to connect

3. **Share the output** of both, and I'll help fix the specific issue

4. **Verify server TLS config:**
   - Is TLS enabled on port 5060?
   - Does it require client certificates?
   - What TLS versions are supported?

---

## Quick Test Commands

```bash
# Test if server accepts TLS on port 5060
openssl s_client -connect 10.94.161.179:5060 -tls1_2

# Get server certificate
echo | openssl s_client -connect 10.94.161.179:5060 2>/dev/null | \
  openssl x509 -text

# Test with specific TLS version
openssl s_client -connect 10.94.161.179:5060 -tls1
openssl s_client -connect 10.94.161.179:5060 -tls1_1
openssl s_client -connect 10.94.161.179:5060 -tls1_2
openssl s_client -connect 10.94.161.179:5060 -tls1_3
```

The output will tell us exactly what's wrong and how to fix it!
