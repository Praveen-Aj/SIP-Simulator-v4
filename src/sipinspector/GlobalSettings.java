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
package sipinspector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author Zarko Coklin
 */
public class GlobalSettings {

    public GlobalSettings() {
        KeyWordsMap = new HashMap<String, String>();
        setSetting("QoS", "00");
    }

    static public void setUsername(String number) {
        Username = number;
        KeyWordsMap.put("[service]", Username);
    }

    static public String getUsername() {
        return Username;
    }

    static public void setAuthUsername(String username) {
        AuthUsername = username;
        KeyWordsMap.put("[username]", AuthUsername);
    }

    static public String getAuthUsername() {
        return AuthUsername;
    }

    static public void setAuthPassword(String password) {
        AuthPassword = password;
        KeyWordsMap.put("[password]", AuthPassword);
    }

    static public String getAuthPassword() {
        return AuthPassword;
    }

    static public void setLocalIP(String address) {
        LocalIPAddr = address;
        KeyWordsMap.put("[local_ip]", address);
    }

    static public String getLocalIP() {
        return LocalIPAddr;
    }

    static public void setLocalPort(String port) {
        LocalPort = port;
        KeyWordsMap.put("[local_port]", port);
    }

    static public String getLocalPort() {
        return LocalPort;
    }

    static public void setRemoteIPAddr(String address) {
        RemoteIPAddr = address;
        KeyWordsMap.put("[remote_ip]", address);
    }

    static public String getRemoteIPAddr() {
        return RemoteIPAddr;
    }

    static public void setRemotePort(String port) {
        RemotePort = port;
        KeyWordsMap.put("[remote_port]", port);
    }

    static public String getRemotePort() {
        return RemotePort;
    }

    static public void setTransport(String trans) {
        transport = trans;
        KeyWordsMap.put("[transport]", transport);
    }

    static public String getTransport() {
        return transport;
    }

    static public void setMediaPort(String mp) {
        mediaPort = mp;
        KeyWordsMap.put("[media_port]", mediaPort);
    }

    static public String getMediaPort() {
        return mediaPort;
    }

    static public void setSdpProfile(String profile) {
        sdpProfile = profile;
    }

    static public String getSdpProfile() {
        return sdpProfile;
    }

    static public void setCustomSDP(String sdp) {
        customSDP = sdp;
    }

    static public String getCustomSDP() {
        return customSDP;
    }

    static public void setSdpG711(boolean enabled) {
        sdpG711 = enabled;
    }

    static public boolean isSdpG711() {
        return sdpG711;
    }

    static public void setSdpG729(boolean enabled) {
        sdpG729 = enabled;
    }

    static public boolean isSdpG729() {
        return sdpG729;
    }

    static public void setSdpG722(boolean enabled) {
        sdpG722 = enabled;
    }

    static public boolean isSdpG722() {
        return sdpG722;
    }

    static public void setSdpOpus(boolean enabled) {
        sdpOpus = enabled;
    }

    static public boolean isSdpOpus() {
        return sdpOpus;
    }

    static public void setSdpAnswerCodecType(String codec) {
        sdpAnswerCodecType = codec;
    }

    static public String getSdpAnswerCodecType() {
        return sdpAnswerCodecType;
    }

    static public void setSdpUseSavp(boolean useSavp) {
        sdpUseSavp = useSavp;
    }

    static public boolean isSdpUseSavp() {
        return sdpUseSavp;
    }

    // ---- Live Audio + SRTP (V3) ----
    static public void setLiveAudioEnabled(boolean enabled) {
        liveAudioEnabled = enabled;
    }

    static public boolean isLiveAudioEnabled() {
        return liveAudioEnabled;
    }

    /**
     * SRTP SDES crypto line sourced from actual SDP text (a=crypto:...).
     * Stored exactly as parsed (without trailing CRLF).
     */
    static public void setSrtpCryptoLine(String cryptoLine) {
        srtpCryptoLine = cryptoLine;
    }

    static public String getSrtpCryptoLine() {
        return srtpCryptoLine;
    }

    static public void setSelectedMicDevice(String deviceName) {
        selectedMicDevice = deviceName;
    }

    static public String getSelectedMicDevice() {
        return selectedMicDevice;
    }

    static public void setSelectedSpeakerDevice(String deviceName) {
        selectedSpeakerDevice = deviceName;
    }

    static public String getSelectedSpeakerDevice() {
        return selectedSpeakerDevice;
    }

    static public void setSetting(String key, String value) {
        if (KeyWordsMap == null) {
            KeyWordsMap = new HashMap<String, String>();
        }
        KeyWordsMap.put(key, value);
    }

    static public String getSetting(String key) {
        if (KeyWordsMap == null) {
            return null;
        }
        return KeyWordsMap.get(key);
    }

    static public void setPcapEngine(String engine) {
        if (engine == null || engine.trim().length() == 0) {
            pcapEngine = "jpcap";
        } else {
            pcapEngine = engine;
        }
        setSetting("pcap.engine", pcapEngine);
    }

    static public String getPcapEngine() {
        return pcapEngine;
    }

    static private String Username = null;

    static private String AuthUsername = null;

    static private String AuthPassword = null;

    static HashMap<String, String> KeyWordsMap = new HashMap<String, String>();

    static private String LocalIPAddr = null;

    static private String LocalPort = null;

    static private String RemoteIPAddr = null;

    static private String RemotePort = null;

    static private String transport = null;

    static private String mediaPort = null;

    static private String sdpProfile = "G711";

    static private String customSDP = null;

    static private boolean sdpG711 = true;

    static private boolean sdpG729 = false;

    static private boolean sdpG722 = false;

    static private boolean sdpOpus = false;

    static private String sdpAnswerCodecType = "G711";

    static private boolean sdpUseSavp = false;

    // Live Audio settings (persisted)
    static private boolean liveAudioEnabled = false;
    static private String srtpCryptoLine = null;
    static private String selectedMicDevice = null;
    static private String selectedSpeakerDevice = null;

    // Phase 3: File Protection and Backup settings
    static public boolean isScenarioProtected = false;

    static public boolean isValuesProtected = false;

    static public boolean logsEnabled = true;

    public static boolean isLogsEnabled() {
        return logsEnabled;
    }

    public static void setLogsEnabled(boolean enabled) {
        logsEnabled = enabled;
    }

    static private String pcapEngine = "jpcap";

    // Phase 4: INVITE Tag Logic
    static public String currentInviteType = "None";

    static public boolean isInitialInvite = false;

    // Settings persistence (centrally and portably resolved via AppPaths)
    private static File getSettingsFile() {
        return sipinspector.Utils.AppPaths.getConfigFile("settings.properties");
    }

    /**
     * Save settings to persistent storage (file)
     */
    static public void saveSettings() {
        try {
            File settingsFile = getSettingsFile();
            File configDir = settingsFile.getParentFile();
            if (configDir != null && !configDir.exists()) {
                configDir.mkdirs();
            }

            Properties props = new Properties();
            props.setProperty("isScenarioProtected", String.valueOf(isScenarioProtected));
            props.setProperty("isValuesProtected", String.valueOf(isValuesProtected));
            props.setProperty("logsEnabled", String.valueOf(logsEnabled));
            props.setProperty("pcap.engine", pcapEngine);
            props.setProperty("defaultTheme", getSetting("defaultTheme") == null ? "Ocean Blue" : getSetting("defaultTheme"));

            props.setProperty("liveAudioEnabled", String.valueOf(liveAudioEnabled));
            if (srtpCryptoLine != null) {
                props.setProperty("srtpCryptoLine", srtpCryptoLine);
            }
            if (selectedMicDevice != null) {
                props.setProperty("selectedMicDevice", selectedMicDevice);
            }
            if (selectedSpeakerDevice != null) {
                props.setProperty("selectedSpeakerDevice", selectedSpeakerDevice);
            }

            // Save simulation settings
            props.setProperty("sim.delay", getSetting("sim.delay") == null ? "0" : getSetting("sim.delay"));
            props.setProperty("sim.loss", getSetting("sim.loss") == null ? "0" : getSetting("sim.loss"));
            props.setProperty("sim.jitter", getSetting("sim.jitter") == null ? "0" : getSetting("sim.jitter"));
            props.setProperty("sim.affectMedia", getSetting("sim.affectMedia") == null ? "false" : getSetting("sim.affectMedia"));
            props.setProperty("sim.malformed", getSetting("sim.malformed") == null ? "None" : getSetting("sim.malformed"));

            // Save SIP & Media network configuration
            if (getSetting("sip.localIp") != null) props.setProperty("sip.localIp", getSetting("sip.localIp"));
            if (getSetting("sip.localPort") != null) props.setProperty("sip.localPort", getSetting("sip.localPort"));
            if (getSetting("sip.pbxIp") != null) props.setProperty("sip.pbxIp", getSetting("sip.pbxIp"));
            if (getSetting("sip.pbxPort") != null) props.setProperty("sip.pbxPort", getSetting("sip.pbxPort"));
            if (getSetting("sip.transport") != null) props.setProperty("sip.transport", getSetting("sip.transport"));
            if (getSetting("sip.expires") != null) props.setProperty("sip.expires", getSetting("sip.expires"));
            if (getSetting("sip.userAgent") != null) props.setProperty("sip.userAgent", getSetting("sip.userAgent"));
            if (getSetting("rtp.portRangeStart") != null) props.setProperty("rtp.portRangeStart", getSetting("rtp.portRangeStart"));
            if (getSetting("rtp.portRangeEnd") != null) props.setProperty("rtp.portRangeEnd", getSetting("rtp.portRangeEnd"));
            if (getSetting("tls.debug") != null) props.setProperty("tls.debug", getSetting("tls.debug"));

            // Save window geometry settings
            if (getSetting("window.width") != null) props.setProperty("window.width", getSetting("window.width"));
            if (getSetting("window.height") != null) props.setProperty("window.height", getSetting("window.height"));
            if (getSetting("window.x") != null) props.setProperty("window.x", getSetting("window.x"));
            if (getSetting("window.y") != null) props.setProperty("window.y", getSetting("window.y"));
            if (getSetting("window.maximized") != null) props.setProperty("window.maximized", getSetting("window.maximized"));

            FileOutputStream fos = new FileOutputStream(settingsFile);
            props.store(fos, "SIP Simulator Settings");
            fos.close();
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    /**
     * Load settings from persistent storage (file)
     */
    static public void loadSettings() {
        try {
            File settingsFile = getSettingsFile();
            if (!settingsFile.exists()) {
                return; // No saved settings yet
            }

            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(settingsFile);
            props.load(fis);
            fis.close();

            // Load all generic properties dynamically into KeyWordsMap
            for (String propName : props.stringPropertyNames()) {
                setSetting(propName, props.getProperty(propName));
            }

            isScenarioProtected = "true".equals(props.getProperty("isScenarioProtected", "false"));
            isValuesProtected = "true".equals(props.getProperty("isValuesProtected", "false"));
            logsEnabled = "true".equals(props.getProperty("logsEnabled", "true"));
            pcapEngine = props.getProperty("pcap.engine", "jpcap");
            setSetting("defaultTheme", props.getProperty("defaultTheme", "Ocean Blue"));

            liveAudioEnabled = "true".equals(props.getProperty("liveAudioEnabled", "false"));
            srtpCryptoLine = props.getProperty("srtpCryptoLine", null);
            selectedMicDevice = props.getProperty("selectedMicDevice", null);
            selectedSpeakerDevice = props.getProperty("selectedSpeakerDevice", null);

            setSetting("isScenarioProtected", Boolean.toString(isScenarioProtected));
            setSetting("isValuesProtected", Boolean.toString(isValuesProtected));
            setSetting("logsEnabled", Boolean.toString(logsEnabled));
            setSetting("pcap.engine", pcapEngine);

            setSetting("liveAudioEnabled", Boolean.toString(liveAudioEnabled));
            if (srtpCryptoLine != null) {
                setSetting("srtpCryptoLine", srtpCryptoLine);
            }
            if (selectedMicDevice != null) {
                setSetting("selectedMicDevice", selectedMicDevice);
            }
            if (selectedSpeakerDevice != null) {
                setSetting("selectedSpeakerDevice", selectedSpeakerDevice);
            }

            // Load simulation settings
            setSetting("sim.delay", props.getProperty("sim.delay", "0"));
            setSetting("sim.loss", props.getProperty("sim.loss", "0"));
            setSetting("sim.malformed", props.getProperty("sim.malformed", "None"));

            // Load window geometry settings
            if (props.containsKey("window.width")) setSetting("window.width", props.getProperty("window.width"));
            if (props.containsKey("window.height")) setSetting("window.height", props.getProperty("window.height"));
            if (props.containsKey("window.x")) setSetting("window.x", props.getProperty("window.x"));
            if (props.containsKey("window.y")) setSetting("window.y", props.getProperty("window.y"));
            if (props.containsKey("window.maximized")) setSetting("window.maximized", props.getProperty("window.maximized"));
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }
}
