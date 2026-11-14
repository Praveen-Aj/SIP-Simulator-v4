/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and
 *  troubleshoot SIP signaling.
 *
 *  Copyright (C) 2010  Zarko Coklin (zarko_coklin at yahoo dot com)
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
package sipinspector.Utils;

// DOM classes.
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

//JAXP 1.1
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;

/**
 *
 * @author Zarko Coklin
 */
public class WriteSettings {

    public WriteSettings(String name) {
        fileName = name;
    }

    public void writeToFile() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xmldoc = builder.newDocument();
            // Document.
            // Root element.
            Element root = xmldoc.createElement("SIPInspector");
            xmldoc.appendChild(root);
            Element element = null;
            Node n;
            element = xmldoc.createElement("version");
            element.appendChild(xmldoc.createTextNode(SIPInspectorMainFrame.getSIPInspectorVersion()));
            root.appendChild(element);

            Element servers = xmldoc.createElement("serversInfo");
            root.appendChild(servers);

            element = xmldoc.createElement("localIPAddress");
            element.appendChild(xmldoc.createTextNode(GlobalSettings.getLocalIP()));
            servers.appendChild(element);

            element = xmldoc.createElement("localPort");
            element.appendChild(xmldoc.createTextNode(GlobalSettings.getLocalPort()));
            servers.appendChild(element);

            String setting = GlobalSettings.getSetting("remoteServerFlag");
            if (setting != null && setting.equals("enabled")) {
                element = xmldoc.createElement("remoteServerFlag");
                element.appendChild(xmldoc.createTextNode("enabled"));
                servers.appendChild(element);
            }

            setting = GlobalSettings.getRemoteIPAddr();
            if (setting != null) {
                element = xmldoc.createElement("remoteIPAddress");
                element.appendChild(xmldoc.createTextNode(setting));
                servers.appendChild(element);
            }

            setting = GlobalSettings.getRemotePort();
            if (setting != null) {
                element = xmldoc.createElement("remotePort");
                element.appendChild(xmldoc.createTextNode(setting));
                servers.appendChild(element);
            }

            Element account = xmldoc.createElement("account");
            root.appendChild(account);

            setting = GlobalSettings.getUsername();
            if (setting != null) {
                element = xmldoc.createElement("username");
                element.appendChild(xmldoc.createTextNode(setting));
                account.appendChild(element);
            }

            setting = GlobalSettings.getAuthUsername();
            if (setting != null) {
                element = xmldoc.createElement("authUsername");
                element.appendChild(xmldoc.createTextNode(setting));
                account.appendChild(element);
            }

            setting = GlobalSettings.getAuthPassword();
            if (setting != null) {
                element = xmldoc.createElement("authPassword");
                element.appendChild(xmldoc.createTextNode(setting));
                account.appendChild(element);
            }

            setting = GlobalSettings.getMediaPort();
            if (setting != null) {
                element = xmldoc.createElement("RTPPort");
                element.appendChild(xmldoc.createTextNode(setting));
                account.appendChild(element);
            }

            Element callgeneration = xmldoc.createElement("callGeneration");
            root.appendChild(callgeneration);

            setting = GlobalSettings.getSetting("callsPerSec");
            if (setting != null) {
                element = xmldoc.createElement("callsPerSec");
                element.appendChild(xmldoc.createTextNode(setting));
                callgeneration.appendChild(element);
            }

            setting = GlobalSettings.getSetting("totalCalls");
            if (setting != null) {
                element = xmldoc.createElement("totalCalls");
                element.appendChild(xmldoc.createTextNode(setting));
                callgeneration.appendChild(element);
            }

            setting = GlobalSettings.getSetting("maxConcurrCalls");
            if (setting != null) {
                element = xmldoc.createElement("maxConcurrCalls");
                element.appendChild(xmldoc.createTextNode(setting));
                callgeneration.appendChild(element);
            }

            setting = GlobalSettings.getSetting("scenarioPath");
            if (setting != null) {
                element = xmldoc.createElement("scenarioPath");
                element.appendChild(xmldoc.createTextNode(setting));
                root.appendChild(element);
            }

            setting = GlobalSettings.getSetting("valuesPath");
            if (setting != null) {
                element = xmldoc.createElement("valuesPath");
                element.appendChild(xmldoc.createTextNode(setting));
                root.appendChild(element);
            }

            // Settings tab options
            Element settings = xmldoc.createElement("settings");
            root.appendChild(settings);

            setting = GlobalSettings.getSetting("defaultTheme");
            if (setting != null) {
                element = xmldoc.createElement("defaultTheme");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("defaultTransport");
            if (setting != null) {
                element = xmldoc.createElement("defaultTransport");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("defaultUdpTcpLocalPort");
            if (setting != null) {
                element = xmldoc.createElement("defaultUdpTcpLocalPort");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("defaultTlsLocalPort");
            if (setting != null) {
                element = xmldoc.createElement("defaultTlsLocalPort");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("defaultUdpTcpRemotePort");
            if (setting != null) {
                element = xmldoc.createElement("defaultUdpTcpRemotePort");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("defaultTlsRemotePort");
            if (setting != null) {
                element = xmldoc.createElement("defaultTlsRemotePort");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("autoEnableDebug");
            if (setting != null) {
                element = xmldoc.createElement("autoEnableDebug");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("autoDebugTransport");
            if (setting != null) {
                element = xmldoc.createElement("autoDebugTransport");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("maxLogFiles");
            if (setting != null) {
                element = xmldoc.createElement("maxLogFiles");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            setting = GlobalSettings.getSetting("logRetentionDays");
            if (setting != null) {
                element = xmldoc.createElement("logRetentionDays");
                element.appendChild(xmldoc.createTextNode(setting));
                settings.appendChild(element);
            }

            // Serialisation through Tranform.
            DOMSource domSource = new DOMSource(xmldoc);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(fileName, false);

            } catch (FileNotFoundException exc) {
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            StreamResult streamResult = new StreamResult(fos);
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(domSource, streamResult);
        } catch (TransformerException ex) {
        } catch (ParserConfigurationException ex) {
        }

    }

    public String fileName;
}
