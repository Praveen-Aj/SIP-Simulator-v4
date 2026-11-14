# SIP Simulator v3 Codebase Documentation

This document provides a comprehensive overview of the source files in the SIP Simulator v3 project, explaining the role and responsibility of each component.

---

## 1. Core Package (`sipinspector`)

*   **[AboutDialog.java](src/sipinspector/AboutDialog.java)**: Displays the application's "About" dialog box, including version details, credits, and licensing terms.
*   **[CallDB.java](src/sipinspector/CallDB.java)**: Manages a central database of active and completed calls, facilitating reporting and statistics.
*   **[FindReplaceDialog.java](src/sipinspector/FindReplaceDialog.java)**: Provides find-and-replace text functionality within the scenario and messages editor panels.
*   **[GlobalSettings.java](src/sipinspector/GlobalSettings.java)**: The central repository for all runtime settings, network configurations, paths, and values substitution variables.
*   **[IPListRenderer.java](src/sipinspector/IPListRenderer.java)**: Custom list cell renderer to format and display IP address selections inside configuration drop-downs.
*   **[Main.java](src/sipinspector/Main.java)**: The application entry point. Handles look-and-feel initialization and displays the main window (`SIPInspectorMainFrame`).
*   **[MsgJTreeEntry.java](src/sipinspector/MsgJTreeEntry.java)**: Represents a single node entry in the visual tree representation of a SIP scenario.
*   **[MsgScenarioUpdater.java](src/sipinspector/MsgScenarioUpdater.java)**: Dynamically updates the active SIP scenario model when a user edits messaging definitions in the GUI.
*   **[MyPacket.java](src/sipinspector/MyPacket.java)**: A utility class encapsulating packet data, including raw payloads, sequencing, and timing offsets for RTP playback.
*   **[MyTreeCellRenderer.java](src/sipinspector/MyTreeCellRenderer.java)**: Renders standard and customized icons (like arrows, labels, and pause indicators) for steps in the scenario tree.
*   **[MyTreeSelectionListener.java](src/sipinspector/MyTreeSelectionListener.java)**: Listens for selections in the scenario tree, updating panels with corresponding message details or parameter controls.
*   **[QoS.java](src/sipinspector/QoS.java)**: Manages network Quality of Service (QoS) parameters, such as DSCP/TOS bits, for signaling sockets.
*   **[RemoteRTP.java](src/sipinspector/RemoteRTP.java)**: Parses the media attribute lines in SDP (Session Description Protocol) payloads to identify remote RTP listening ports and addresses.
*   **[SDPBodyCalc.java](src/sipinspector/SDPBodyCalc.java)**: Utility class to rewrite IP and port values dynamically inside SDP text bodies to match current network parameters.
*   **[SIPCall.java](src/sipinspector/SIPCall.java)**: Models the lifecycle of an individual SIP call. Correlates messages (by Call-ID, To/From tags), processes the scenario state machine, and logs transaction flows.
*   **[SIPInspectorMainFrame.java](src/sipinspector/SIPInspectorMainFrame.java)**: The main GUI frame coordinating navigation, tab selections, file loading, status notifications, and network socket bindings.
*   **[SIPScenario.java](src/sipinspector/SIPScenario.java)**: Parses a text scenario file (e.g. `Reg_client.txt`) into sequential command entries, message blocks, jumps, and conditions.
*   **[ScenarioProgressDialog.java](src/sipinspector/ScenarioProgressDialog.java)**: The execution dialog showing active progress, message statistics, and providing control to manually stop scenario runs.
*   **[SimpleLauncher.java](src/sipinspector/SimpleLauncher.java)**: A lightweight command-line or programmatic launcher for quick script-driven execution.
*   **[SocketTest.java](src/sipinspector/SocketTest.java)**: Diagnostics utility to verify UDP socket creation on specific local network interfaces.

---

## 2. Media, Audio & SRTP Package (`sipinspector.Media`)

*   **[AudioDeviceManager.java](src/sipinspector/Media/AudioDeviceManager.java)**: Interfaces with Java Sound API to query and select input (microphone) and output (speaker) hardware lines.
*   **[AudioFormatUtils.java](src/sipinspector/Media/AudioFormatUtils.java)**: Implements conversions between standard 16-bit linear PCM and telephony compression codecs (G.711 A-law/PCMA and U-law/PCMU).
*   **[CliSipRegisterTest.java](src/sipinspector/Media/CliSipRegisterTest.java)**: Headless test runner executing the SIP registration sequence programmatically, bypassing Swing modal dialog blocks via direct Reflection field setting.
*   **[LiveAudioController.java](src/sipinspector/Media/LiveAudioController.java)**: Central manager for active voice calls. Coordinates starting and stopping sender/receiver threads and tracking audio health statistics.
*   **[LiveAudioReceiverThread.java](src/sipinspector/Media/LiveAudioReceiverThread.java)**: Listens for incoming RTP/SRTP media, decrypts/authenticates if SRTP is active, decodes G.711 audio samples, and writes them to the speaker playback line.
*   **[LiveAudioRuntimeDiagnostics.java](src/sipinspector/Media/LiveAudioRuntimeDiagnostics.java)**: Monitors live call performance indicators (packet count, jitter, drop counters, and active sound lines).
*   **[LiveAudioSenderThread.java](src/sipinspector/Media/LiveAudioSenderThread.java)**: Reads live mic inputs, compresses samples to G.711 format, packages them into RTP packets, secures them using SRTP if requested, and sends them to the remote media port.
*   **[LiveAudioSoakValidationMain.java](src/sipinspector/Media/LiveAudioSoakValidationMain.java)**: An automated stress-test validation harness for media threads, evaluating resource cleanup and resilience to malformed SRTP packets.
*   **[SrtpContext.java](src/sipinspector/Media/SrtpContext.java)**: A pure Java cryptographic implementation of SRTP/SRTCP (RFC 3711) using AES-128 counter mode and HMAC-SHA1-80 authentication.

---

## 3. Network Transport Sockets (`sipinspector.Socket`)

*   **[MySocketAbstract.java](src/sipinspector/Socket/MySocketAbstract.java)**: Unifying abstract class defining common send, receive, bind, and close operations for SIP transport.
*   **[TCPClientSocket.java](src/sipinspector/Socket/TCPClientSocket.java)**: Handles SIP message transport over TCP client connections.
*   **[TCPServerSocket.java](src/sipinspector/Socket/TCPServerSocket.java)**: Listens for and manages incoming TCP connections from remote SIP agents.
*   **[TLSClientSocket.java](src/sipinspector/Socket/TLSClientSocket.java)**: Implements secure SIP (SIPS) over IPv4 TLS connections.
*   **[TLSClientSocket_V6.java](src/sipinspector/Socket/TLSClientSocket_V6.java)**: Extends secure TLS transport with support for IPv6 network bindings.
*   **[UDPClientSocket.java](src/sipinspector/Socket/UDPClientSocket.java)**: Provides standard connectionless SIP transport over UDP.
*   **[UDPServerSocket.java](src/sipinspector/Socket/UDPServerSocket.java)**: Manages bindings for listening UDP server sockets.

---

## 4. Scenario Execution Threads (`sipinspector.Threads`)

*   **[CallGeneratorThread.java](src/sipinspector/Threads/CallGeneratorThread.java)**: Generates outgoing call scenarios at a configurable rate (Calls Per Second) to load-test target systems.
*   **[CallMaintainerThread.java](src/sipinspector/Threads/CallMaintainerThread.java)**: Periodically prunes and cleans up completed or timed-out SIP call sessions from active lists.
*   **[PauseMaintainerThread.java](src/sipinspector/Threads/PauseMaintainerThread.java)**: Implements pauses between sequential commands or messages defined inside scenario scripts.
*   **[PcapRtpPlayer.java](src/sipinspector/Threads/PcapRtpPlayer.java)**: Legacy wrapper for playing RTP streams from a PCAP file using native `JPcap` libraries.
*   **[PurePcapRtpPlayer.java](src/sipinspector/Threads/PurePcapRtpPlayer.java)**: A pure Java PCAP file parser and player. Streams RTP payloads over UDP without requiring native OS library dependencies.
*   **[RTPSenderThread.java](src/sipinspector/Threads/RTPSenderThread.java)**: Streams pre-recorded or static RTP audio frames to simulate call media.
*   **[ReceiverThread.java](src/sipinspector/Threads/ReceiverThread.java)**: The main background loop listening on the active SIP socket, dispatching incoming network messages to corresponding `SIPCall` sessions.

---

## 5. Scenario Entry Models (`sipinspector.ScenarioEntries`)

*   **[ScenarioEntry.java](src/sipinspector/ScenarioEntries/ScenarioEntry.java)**: Abstract base class representing a single step/command within a SIP scenario.
*   **[SIPMessageEntry.java](src/sipinspector/ScenarioEntries/SIPMessageEntry.java)**: Represents a specific SIP message to send (e.g. INVITE, REGISTER) or expect from the network.
*   **[CommandPauseEntry.java](src/sipinspector/ScenarioEntries/CommandPauseEntry.java)**: A scenario instruction specifying a delay duration.
*   **[CommandPlayPcapEntry.java](src/sipinspector/ScenarioEntries/CommandPlayPcapEntry.java)**: A scenario instruction triggers playback of a specific PCAP file's media stream.
*   **[ConditionEntry.java](src/sipinspector/ScenarioEntries/ConditionEntry.java)**: Defines branching logic based on headers, variables, or response status codes.
*   **[GotoEntry.java](src/sipinspector/ScenarioEntries/GotoEntry.java)**: Directs execution to jump to a specific scenario label.
*   **[LabelEntry.java](src/sipinspector/ScenarioEntries/LabelEntry.java)**: Defines a label target for jumps within scenarios.
*   **[VariableEntry.java](src/sipinspector/ScenarioEntries/VariableEntry.java)**: Manages variable assignments and increments within the call flow.
*   **[SetRemoteTargetEntry.java](src/sipinspector/ScenarioEntries/SetRemoteTargetEntry.java)**: Dynamically redirects subsequent signaling messages to a modified IP or port.
*   **[NewDialog.java](src/sipinspector/ScenarioEntries/NewDialog.java)**: Configures rules for handling new SIP dialog creation.
*   **[ScenarioException.java](src/sipinspector/ScenarioEntries/ScenarioException.java)**: Base exception thrown during scenario file loading, parsing, or structural validation errors.
*   **[OptionalMsgException.java](src/sipinspector/ScenarioEntries/OptionalMsgException.java)**: Thrown when an expected optional signaling message does not match state constraints.

---

## 6. Utilities & Helpers (`sipinspector.Utils`)

*   **[UserSettingsHandler.java](src/sipinspector/Utils/UserSettingsHandler.java)**: SAX handler to parse configuration details from `userSettings.xml`.
*   **[WriteSettings.java](src/sipinspector/Utils/WriteSettings.java)**: Serializes network bindings, file paths, and general settings back into `userSettings.xml`.
*   **[LoadScenario.java](src/sipinspector/Utils/LoadScenario.java)**: A SwingWorker task to read, parse, and initialize scenario and value lists in the background.
*   **[ValuesLoader.java](src/sipinspector/Utils/ValuesLoader.java)**: Reads CSV-formatted values files (e.g., `Reg_client_values.txt`) into active lists for variable substitutions.
*   **[SIPParser.java](src/sipinspector/Utils/SIPParser.java)**: Extracts SIP methods, response status codes, header values (e.g. Call-ID, To, From, Contact), and transaction parameters from raw text.
*   **[MD5Digest.java](src/sipinspector/Utils/MD5Digest.java)**: Computes MD5 hash digests for digest authentication headers (challenged by `401 Unauthorized` or `407 Proxy Authentication Required`).
*   **[SecurityManager.java](src/sipinspector/Utils/SecurityManager.java)**: Handles SSL/TLS contexts, key stores, trust stores, and certificates for secure SIPS socket setup.
*   **[LogManager.java](src/sipinspector/Utils/LogManager.java)**: Directs file logging targets, folder creations, and log rotations.
*   **[TLSDebugLogger.java](src/sipinspector/Utils/TLSDebugLogger.java)**: Main debug logger formatting SIP transport actions (UDP/TCP/TLS) to file streams.
*   **[TransportDebugLogger.java](src/sipinspector/Utils/TransportDebugLogger.java)**: Legacy/raw transport message logger.
*   **[BackupManager.java](src/sipinspector/Utils/BackupManager.java)**: Automates scheduled backups and restores of the scenarios and values resource directories.
*   **[CustomThemes.java](src/sipinspector/Utils/CustomThemes.java)**: Configures visual styling and Look-and-Feels (such as FlatLaf Dark/Light) for the interface.
*   **[EmojiIcon.java](src/sipinspector/Utils/EmojiIcon.java)**: Vector-renders icons and status symbols.
*   **[ExportUtils.java](src/sipinspector/Utils/ExportUtils.java)**: Exports running performance statistics to CSV reports.
*   **[IPAddressValidator.java](src/sipinspector/Utils/IPAddressValidator.java)**: Validates IPv4 and IPv6 syntax.
*   **[PortValidator.java](src/sipinspector/Utils/PortValidator.java)**: Validates socket port numbers (1-65535).
*   **[IntegerValidator.java](src/sipinspector/Utils/IntegerValidator.java)** & **[FloatValidator.java](src/sipinspector/Utils/FloatValidator.java)**: Validate syntax for numeric variables.
*   **[SIValue.java](src/sipinspector/Utils/SIValue.java)**: Holds substitution values mapped to scenario keys.
*   **[RemoteClient.java](src/sipinspector/Utils/RemoteClient.java)**: Inter-process utility for signaling remote instances.

---

## 7. GUI Tabs & Panels (`sipinspector.ControlTabs` & `sipinspector.OperationTabs` & `sipinspector.UI`)

*   **[SIPPanel.java](src/sipinspector/ControlTabs/SIPPanel.java)**: Interface panel to compose raw SIP text messages.
*   **[SDPPanel.java](src/sipinspector/ControlTabs/SDPPanel.java)**: Interface panel to customize SDP session lines.
*   **[CommandsPanel.java](src/sipinspector/ControlTabs/CommandsPanel.java)**: Panel listing pause, PCAP play, target redirection, and other commands.
*   **[ConditionsPanel.java](src/sipinspector/ControlTabs/ConditionsPanel.java)**: GUI to build branches and jumps based on variables.
*   **[VariablesPanel.java](src/sipinspector/ControlTabs/VariablesPanel.java)**: UI listing all custom evaluation variables.
*   **[SettingsTab.java](src/sipinspector/OperationTabs/SettingsTab.java)**: Renders application preferences (backup schedules, themes, file safety toggle, logging targets).
*   **[EditValuesTab.java](src/sipinspector/OperationTabs/EditValuesTab.java)**: Interactive grid to edit scenario substitution parameters for the current session only.
*   **[ScenarioValuesPanel.java](src/sipinspector/OperationTabs/ScenarioValuesPanel.java)**: Layout for loading specific scenario files and matching values lists.
*   **[LogsTab.java](src/sipinspector/OperationTabs/LogsTab.java)**: Panel to toggle, view, and read network logs directly.
*   **[AccountTab.java](src/sipinspector/OperationTabs/AccountTab.java)**: Tab to define SIP registration credentials, authentication pairs, and default RTP configurations.
*   **[StatisticsPanel.java](src/sipinspector/Statistics/StatisticsPanel.java)**: Renders total metrics, active session counts, success/failure percentages, and includes exporting features.
*   **[EnhancedStatusBar.java](src/sipinspector/UI/EnhancedStatusBar.java)**: Status indicators summarizing active scenario settings and debug logging flags.
*   **[KeyboardShortcutsDialog.java](src/sipinspector/UI/KeyboardShortcutsDialog.java)**: Help dialog displaying active keybinds.
*   **[MessageTemplatesDialog.java](src/sipinspector/UI/MessageTemplatesDialog.java)**: Catalog of pre-composed standard SIP messages.
*   **[MessageSearchPanel.java](src/sipinspector/UI/MessageSearchPanel.java)**: Search utility within logs.

---

## 8. Version Updater (`sipinspector.versionUpdater`)

*   **[CheckUpdate.java](src/sipinspector/versionUpdater/CheckUpdate.java)**: Contacts update servers to check for newer binary releases.
*   **[DownloaderThread.java](src/sipinspector/versionUpdater/DownloaderThread.java)**: Handles network downloads of updated package releases.
*   **[UpdateInfoDialog.java](src/sipinspector/versionUpdater/UpdateInfoDialog.java)** & **[MessageDialog.java](src/sipinspector/versionUpdater/MessageDialog.java)**: Dialog prompts presenting update logs and downloading status.
