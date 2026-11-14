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
package sipinspector.versionUpdater;

import sipinspector.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 *
 * @author Zarko Coklin
 */
public class CheckUpdate
{

    /**
     * Sends an HTTP GET request to a url, and check the version
     *
     * @param endpoint - The URL of the server. (Example: " http://www.yahoo.com/search")
     * @param requestParameters - all the request parameters (Example: "param1=val1&param2=val2"). Note: This method will add the question mark (?) to the request - DO NOT add it yourself
     * @return - The response from the end point
     */
    public static boolean newerVersionExists()
    {
        float newVersion = 0;
        float oldVersion = Float.parseFloat(SIPInspectorMainFrame.getSIPInspectorVersion());
        boolean flag = false;
        int pos;

        newMessage = null;
        try
        {
            // Send data
            String urlStr = "https://sites.google.com/site/sipinspectorsite/current_version";
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection();

            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null)
            {
                // Web page should have line like this:
                // [SI_version]0.90[/SI_version]
                if ((pos = line.indexOf("[SI_version]")) != -1)
                {
                    newVersionStr = line.substring(pos + 12, line.indexOf("[/SI_version]"));
                    newVersion = Float.parseFloat(newVersionStr);
                    if (newVersion > oldVersion)
                    {
                        //pop up a new window to inform there is a new version
                        flag = true;
                    }
                }
                else if ((pos = line.indexOf("[SI_URL]")) != -1)
                // Web page should have line like this:
                // [SI_URL]http://something[/SI_URL]
                {
                    newVersionURL = line.substring(pos + 8, line.indexOf("[/SI_URL]"));
                }
                else if ((pos = line.indexOf("[SI_MESSAGE]")) != -1)
                // Web page should have line like this:
                // [SI_MESSAGE]http://something[/SI_MESSAGE]
                {
                    newMessage = line.substring(pos + 12, line.indexOf("[/SI_MESSAGE]"));
                    if (newMessage.equals("") == true)
                    {
                        newMessage = null;
                    }
                    rd.close();
                    break;
                }
            }
            if (flag == false)
            {
                rd.close();
            }
        }
        catch (Exception e)
        {
            return flag;
        }

        return flag;
    }

    public static String getNewVersionURL()
    {
        return newVersionURL;
    }

    public static String getNewMessage()
    {
        return newMessage;
    }

    public static String newVersionStr;

    public static String newVersionURL;

    public static String newMessage;
}
