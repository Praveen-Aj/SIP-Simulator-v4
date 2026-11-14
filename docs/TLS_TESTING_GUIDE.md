# TLS Testing Guide - Send SIP Messages

## ✅ TLS Handshake Successful!

Your server logs confirm TLS handshake completed:
```
[CTransportTls::acceptConnection] Remote=(10.94.161.179 5061) CNX created OK
Recv <<< TLS 1.2  [length 0005]    # Client Hello
Send >>> TLS 1.2  [length 009b]    # Server Hello
```

**TLS connection is ESTABLISHED and WORKING!** 🎉

---

## 📋 Why No Messages Are Sent

After clicking "Next", SIP Inspector:
1. ✅ Establishes TLS connection (DONE)
2. ⏸️ Waits for you to send a SIP message (PENDING)

You need to **load a scenario** or **create a SIP message** to send.

---

## 🚀 How to Send SIP Messages Over TLS

### Option 1: Load Existing Scenario

1. **File → Load Scenario**
2. **Select a scenario** (e.g., `scenarios/Reg_client.txt`)
3. **Click "Run"** or send the first message
4. **Check server logs** for encrypted SIP message

### Option 2: Create Simple REGISTER Message

1. After clicking "Next", go to **"Scenario building screen"**
2. In **SIP tab**, create an outgoing message:
   - **Direction**: OUT (--->) 
   - **Message Type**: REGISTER
   - **Message content**:
   ```
   REGISTER sip:[remote_ip] SIP/2.0
   Via: SIP/2.0/TLS [local_ip]:[local_port];branch=z9hG4bK[branch]
   From: <sip:[fromUsername]@[remote_ip]>;tag=[local_tag]
   To: <sip:[fromUsername]@[remote_ip]>
   Call-ID: [call_id]
   CSeq: 1 REGISTER
   Contact: <sip:[fromUsername]@[local_ip]:[local_port];transport=tls>
   Max-Forwards: 70
   Expires: 3600
   Content-Length: 0
   ```
3. **Click "Run"** to send

### Option 3: Quick Test with Existing Scenario

The simplest way:
```
1. File → Load Scenario → "Reg_client.txt"
2. Set Account Info:
   - From: 1000
   - To: 1000
3. Click "Run"
4. Watch server logs for encrypted REGISTER
```

---

## 🔍 What Should Happen

### On SIP Inspector:
1. TLS connection established ✅ (already done)
2. Send SIP REGISTER
3. Receive encrypted response from server
4. Display response in UI

### On Server (@[TerminalName: powershell, ProcessId: 17544]):
```
[CConnectionTls::receiveMsg] Receiving encrypted data
[CIOCom::onReceiveMessage] Decrypting TLS message
RECEIVE MESSAGE FROM NETWORK (10.94.161.179:5061 [TLS])
----------------------utf8-----------------------
REGISTER sip:10.94.161.179 SIP/2.0
Via: SIP/2.0/TLS ...
...
```

---

## 🎯 Verification Steps

1. **Load scenario**: `File → Load Scenario → Reg_client.txt`
2. **Configure**:
   - Remote IP: 10.94.161.179
   - Remote Port: 5061
   - Transport: TLS
   - From/To: 1000
3. **Click "Next"** (TLS connects - already working)
4. **Click "Run"** or send first message
5. **Check server logs** - should see decrypted SIP REGISTER

---

## 📊 Current Status

| Component | Status |
|-----------|--------|
| TLS Connection | ✅ Working |
| TLS Handshake | ✅ Complete |
| Encryption | ✅ Active |
| Send SIP Message | ⏸️ Waiting for scenario |

---

## 🔧 Troubleshooting

### If message still doesn't send:

1. **Check if scenario is loaded**
   - Look for entries in "Scenario entries" panel
   
2. **Check if "Run" button is enabled**
   - If disabled, scenario might not be valid

3. **Try manual message**
   - Create a simple OUT message in SIP tab
   - Add to scenario
   - Click "Run"

4. **Check SIP Inspector logs**
   - Tools → Enable logging
   - Check for send errors

---

## ✅ Next Step

**Load a scenario and click "Run"** - the TLS connection is ready and waiting for you to send SIP messages! 🚀

The hard part (TLS handshake) is DONE. Now just send a SIP message through the established encrypted channel!
