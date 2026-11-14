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

import java.net.InetAddress;

/**
 *
 * @author Zarko Coklin
 */
public class RemoteClient
{
    public RemoteClient(InetAddress address, int port)
    {
        remAddress = address;
        remPort = port;
    }

    public InetAddress getRemAddress()
    {
        return remAddress;
    }

    public int getRemPort()
    {
        return remPort;
    }

    public void setRemAddress(InetAddress address)
    {
        remAddress = address;
    }

    public void setRemPort(int port)
    {
        remPort = port;
    }

    private InetAddress remAddress;

    private int remPort;
}
