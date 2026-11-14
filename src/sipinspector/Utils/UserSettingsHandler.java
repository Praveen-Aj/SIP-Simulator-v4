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

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import sipinspector.GlobalSettings;

/**
 *
 * @author Zarko Coklin
 */
public class UserSettingsHandler extends DefaultHandler
{

    public UserSettingsHandler(GlobalSettings settings)
    {
        this.settings = settings;
    }

    public void characters(char[] ch, int start, int length) throws SAXException
    {
        tempVal = new String(ch, start, length);
    }

    @SuppressWarnings("static-access")
    public void endElement(String uri, String localName,
            String qName) throws SAXException
    {
        if (tempVal == null)
        {
            return;
        }
        tempVal = tempVal.trim();
        if (tempVal.equals(""))
        {
            return;
        }
        if (qName.equals("localIPAddress"))
        {
             settings.setLocalIP(tempVal);
        }
        else if (qName.equals("localPort"))
        {
            settings.setLocalPort(tempVal);
        }
        else if (qName.equals("remoteIPAddress"))
        {
            settings.setRemoteIPAddr(tempVal);
        }
        else if (qName.equals("remotePort"))
        {
            settings.setRemotePort(tempVal);
        }
        else if (qName.equals("username"))
        {
            settings.setUsername(tempVal);
        }
        else if (qName.equals("authUsername"))
        {
            settings.setAuthUsername(tempVal);
        }
        else if (qName.equals("authPassword"))
        {
            settings.setAuthPassword(tempVal);
        }
        else if (qName.equals("RTPPort"))
        {
            settings.setMediaPort(tempVal);
        }
        else if (qName.equals("scenarioPath"))
        {
            settings.setSetting("scenarioPath", tempVal);
        }
        else if (qName.equals("valuesPath"))
        {
            settings.setSetting("valuesPath", tempVal);
        }
        else if (qName.equals("remoteServerFlag"))
        {
            settings.setSetting("remoteServerFlag", tempVal);
        }
        else if (qName.equals("callsPerSec"))
        {
            settings.setSetting("callsPerSec", tempVal);
        }
        else if (qName.equals("totalCalls"))
        {
            settings.setSetting("totalCalls", tempVal);
        }
        else if (qName.equals("maxConcurrCalls"))
        {
            settings.setSetting("maxConcurrCalls", tempVal);
        }
    }

    private String tempVal;

    private GlobalSettings settings;
}
   
