SIP Simulator v4.1 - User Guide
================================

1. Purpose
----------
SIP Simulator is a desktop tool for generating SIP traffic and testing SIP
servers, PBXs, and SBCs (registrars, proxies, application servers, and
Alcatel-Lucent OXE in particular). It places and receives SIP calls using
scenario scripts, supports live audio and RTP tones, captures traffic for
Wireshark, and can run automated batches of test cases.

This document is for END USERS who install and run the application.


2. System Requirements
-----------------------
- Windows 10 or later (64-bit recommended)
- A standard (non-administrator) user account is enough to install and run
  the app itself
- Node.js is bundled - the Web Dashboard works without installing anything
  separately
- **Npcap** (with "WinPcap API-compatible mode" enabled) is required if you
  want to play RBT/Hold/Guide/DTMF tones or replay a recorded .pcap during a
  scenario. It is NOT required for the new packet-CAPTURE feature (Tools ->
  Start Packet Capture), which uses its own built-in capture engine.

The installer includes a bundled Java runtime, Node.js runtime, and an
Npcap installer, so you normally do not need to install any of these
yourself.


3. Installation
----------------
1. Run `SIP_Simulator_Setup_v4.1.exe`.
2. On **Select Additional Tasks**, choose:
   - `Create a desktop shortcut` (recommended)
   - `Install Npcap` - keep this checked if you plan to use tone/PCAP
     playback (see Section 2). It can be safely unchecked if you only need
     basic call signaling and the new packet-capture feature.
3. If you install Npcap, when its wizard appears:
   - Accept the license.
   - Enable **"WinPcap API-compatible mode"**.
   - Complete the installation (Windows may ask for admin approval and, on
     some systems, a restart).
4. Click **Finish** to close the SIP Simulator installer.

After installation you can start the app from:
- Desktop shortcut: **SIP Simulator**
- Start Menu: **Start -> SIP Simulator -> SIP Simulator**


4. First Run - Basic Setup
----------------------------
When SIP Simulator starts:

1. **Local IP and Port**
   - In the **Local Info** section, pick the local IP address to bind to.
   - Set the SIP port (default `5060`).

2. **Connection Type**
   - Choose **UDP**, **TCP**, or **TLS** under *IP Connection Type*.

3. **Remote Server (optional)**
   - Tick **Remote Server** if you want SIP Simulator to connect to a SIP
     server/PBX.
   - Enter the **Remote IP address** and **Remote Server Port**.

4. Click **Next** to go to the main scenario view.


5. Tour of the Main Window
----------------------------
The main window is organized into tabs:

- **Scenario Editor** - build or edit a scenario message-by-message. The
  right-hand panel has its own sub-tabs: **SIP** (add a request/response),
  **Condition**, **Commands** (pause, play a .pcap, set remote target),
  **Variables**, **SDP**, and **Tones** (see Section 8).
- **Scenario & Values** - pick a scenario file and its matching values file
  from dropdowns; selecting a scenario auto-selects the matching values.
- **Edit Values** - edit the field values used by the loaded scenario.
  **Apply** updates the current session only; **Save** / **Save As...**
  write the changes back to a values file (see Section 9).
- **Logs** - browse SIP/TLS/UDP/TCP log files, view a color-coded visual
  call-flow ladder diagram, and export it as PNG or PDF (Section 10).
- **Campaigns** - configure and run batches of test cases automatically
  (Section 11).
- **Simulation** - inject network latency, packet loss, or malformed
  headers for negative testing (Section 12).
- **RTP Inspector** - live RTP/media diagnostics: codec, payload type,
  packet/jitter stats, DTMF received, and TLS/SRTP security details.
- **Message Search** - search and filter the SIP messages in the loaded
  scenario by text, message type, or response code.

The **Tools** menu adds: SDP Calculator, **Message Templates** (a library
of 19 ready-made SIP messages you can copy into a scenario), **Compare SIP
Messages** (header-by-header diff of two messages), **Export Statistics**,
**Open Logs Folder**, **Launch Web Dashboard**, and **Start/Stop Packet
Capture** (Section 7).


6. Running a Sample Scenario
------------------------------
1. From the **Scenario & Values** tab, load a sample scenario (for example
   `Outgoing_basic_call.txt` or `Incoming_basic_call_with_RBT_tone.txt`).
2. Verify or edit values using **Edit Values** if needed.
3. Press **Run** to start the call flow.
4. Watch the progress dialog and message logs to confirm requests and
   responses behave as expected.


7. Packet Capture (Wireshark)
--------------------------------
To capture SIP signaling and RTP media for analysis in Wireshark:
1. Go to **Tools -> Start Packet Capture...** and choose where to save the
   `.pcap` file.
2. Run your scenario as normal. All SIP messages (UDP/TCP/TLS - TLS is
   captured decrypted) and any tone/PCAP or Live Audio RTP are written to
   the file as they occur.
3. Go to **Tools -> Stop Packet Capture** when done, then open the file in
   Wireshark.

This feature does not require Npcap - it uses its own capture engine.


8. Sending Tones (RBT, Hold, Greeting, DTMF...)
--------------------------------------------------
The **Tones** tab (next to **SDP** in the Scenario Editor) inserts a tone
at whatever point you place it in the script - the same way you add a SIP
message - so it can play before 180 Ringing, between 180 and 200 OK, after
the call connects, or anywhere else.

1. Open the **Tones** tab.
2. Pick a preset (Ring Back Tone, Hold, Welcome/Greeting Guide, Background
   Conversation, Dial/Busy/Reorder tone, a DTMF digit, or an End Tone) or
   choose **Custom .pcap File** to browse for your own recording.
3. Tick **Continuous** to loop the tone until the next scenario step, or
   leave it unticked to play once.
4. Position the cursor where you want the tone in the message tree and
   click **<< Add**, just as you would for a SIP message.

Note: for a scenario acting as the CALLED party, the tone can be sent as
soon as the incoming INVITE (with its SDP) is received - i.e. before you
send any response. For a scenario acting as the CALLING party, no tone can
be sent until the far end provides an SDP answer (via early media or the
200 OK), since the destination for the audio is not known before that.

Tone playback uses the same engine as regular .pcap playback, so it needs
Npcap unless the app has been configured to use the built-in pure-Java
engine instead (an advanced setting).


9. Editing and Saving Values
--------------------------------
1. Load a scenario and its values file (Section 6).
2. Go to **Edit Values** and change any field.
3. **Apply** - uses the new values for the rest of this session only.
4. **Save** - applies the changes AND writes them back to the values file
   you loaded (the file's original comments are preserved).
5. **Save As...** - writes the changes to a new or different values file.

If **Values Protection** is enabled (Settings tab), Save/Save As will
refuse to overwrite an existing file and tell you to use Save As or turn
protection off first.


10. Logs, Call-Flow Diagrams, and Export
--------------------------------------------
- The **Logs** tab lists log files by transport (UDP/TCP/TLS/SIP). Selecting
  one shows both the raw text and a visual sequence ladder diagram.
- Click any arrow in the ladder to see that message's full headers below.
- Use **Export PNG** / **Export PDF** above the diagram to save it as an
  image for a report or ticket.
- RTP/audio events (Live Audio start/stop, DTMF digits, tone start/stop)
  are written into the same log, alongside the SIP messages.


11. Batch Campaign Testing
------------------------------
The **Campaigns** tab runs a list of test cases automatically:

1. Click **+ Add Test Case** and choose a name, a caller scenario+values
   pair, and a callee scenario+values pair.
2. For an attended-transfer style test, tick **3-way Transfer Test** and
   also choose a target scenario+values pair.
3. Click **Execute Campaign Batch**.

For each test case, SIP Simulator automatically starts a background
instance to act as the other side of the call (and a third instance for
transfer tests), each registering under the identity defined in its own
values file. Both sides register with, and call each other through, the
SIP server/PBX configured in the app - so a campaign run needs a reachable,
working registrar/PBX to actually connect the calls, the same as it would
for two real phones.

Results (Pass/Fail) require the call to have actually connected (an
INVITE answered with 200 OK); an unanswered or failed call is reported as
FAIL. Export results with **Export HTML Report** or **Export CSV**.


12. Simulation Tab (Network Emulation)
-------------------------------------------
Use this tab to test how your scenario behaves under imperfect network
conditions:
- **Packet Loss %** - randomly drops that percentage of packets in each
  direction. A dropped packet is still recorded in the debug log as a
  "[SIMULATION] Dropped..." line, in case a message appears to be missing.
- **Delay (ms)** - holds each packet for that many milliseconds before it
  is actually sent/received, simulating network latency.
- **Malformed** - deliberately corrupts a specific header (CSeq or Expires)
  on outgoing messages, to test how the remote server handles bad input.

Remember to reset these to 0/None when you are done, so ordinary testing
is not affected by leftover simulation settings.


13. Web Dashboard
--------------------
Go to **Tools -> Launch Web Dashboard** to open a browser-based view of the
same scenarios, values, and diagnostics - useful for viewing from another
device, or for a simpler screen-share-friendly view. The dashboard starts
its own local web server automatically; no separate installation is
needed.


14. Themes & Settings
--------------------------
The **Settings** tab lets you choose a color theme (applies immediately and
is remembered on restart), set default transport/ports, configure log
retention, and enable Scenario/Values Protection (blocks accidental
overwrites of files with the same name).


15. Logs and Troubleshooting
---------------------------------
- SIP and transport logs are written under a folder named
  **"SIP Simulator logs"** on your desktop.
- Use these logs when troubleshooting call flows or sharing details with
  the support/engineering team.

Common issues:
- **Cannot bind local port** - another process is using the port; choose a
  different local port.
- **No SIP messages received** - verify remote IP/port, firewall rules, and
  that the SIP server is reachable from the selected local IP.
- **A response seems to be missing from the ladder diagram** - check the
  Simulation tab's Packet Loss % is not set above 0, and check Raw Log Text
  for a "[SIMULATION] Dropped..." line.
- **Tone/PCAP playback errors** - confirm Npcap is installed with "WinPcap
  API-compatible mode" enabled (Section 2); if just installed, restart
  Windows once and try again.
- **RTP Inspector shows "No Active Audio Stream"** - this is expected
  whenever the running scenario has not started Live Audio or a tone/PCAP;
  it does not necessarily mean anything is wrong.


16. Uninstalling
--------------------
To remove SIP Simulator:
1. Open **Settings -> Apps -> Installed apps** (or **Control Panel ->
   Programs and Features** on older Windows versions).
2. Find **SIP Simulator** and choose **Uninstall**.

Npcap is a separate system component. If you remove Npcap, tone/PCAP
playback in SIP Simulator will no longer work (packet capture is
unaffected).


17. Getting Help
---------------------
If you encounter issues that you cannot resolve using this guide:
- Collect the log files from **"SIP Simulator logs"** on your desktop.
- If relevant, include a Wireshark capture (Section 7) and/or an exported
  call-flow diagram (Section 10).
- Note the scenario used and a short description of the problem.
- Share this information with the SIP Support / engineering team for
  further analysis.
