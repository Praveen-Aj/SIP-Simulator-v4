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
package sipinspector.Utils;

import java.security.NoSuchAlgorithmException;

import java.security.MessageDigest;
import java.util.Random;

/**
 *
 * @author Zarko Coklin
 */
public class MD5Digest
{
    public MD5Digest(String authHdr,
            String method,
            String username,
            String password,
            String uri)
    {
        int start;
        int end;

        this.username = username;
        this.password = password;
        this.method = method;
        this.uri = uri;
        Random rand = new Random();
        this.cnonce = Long.toHexString(rand.nextLong());

        start = authHdr.indexOf("realm=\"");
        if (start == -1)
        {
            realm = "";
        }
        else
        {
            start += 7;
            end = authHdr.indexOf('"', start);
            realm = authHdr.substring(start, end);
        }

        start = authHdr.indexOf("nonce=\"");
        if (start == -1)
        {
            nonce = "";
        }
        else
        {
            start += 7;
            end = authHdr.indexOf('"', start);
            nonce = authHdr.substring(start, end);
        }

        start = authHdr.indexOf("opaque=\"");
        if (start == -1)
        {
            opaque = "";
        }
        else
        {
            start += 8;
            end = authHdr.indexOf('"', start);
            opaque = authHdr.substring(start, end);
        }

        start = authHdr.indexOf("qop=");
        if (start == -1)
        {
            qop = "";
            return;
        }
        else
        {
            end = authHdr.indexOf('"', start + 5);
            if (end == -1)
            {
                end = authHdr.indexOf(' ', start + 5);
                if (end == -1)
                {
                    end = authHdr.indexOf(',', start + 5);
                    if (end == -1)
                    {
                        qop = authHdr.substring(start + 4);
                        qop = qop.replace("\"", "");
                        return;
                    }
                }
            }
            else
            {
                qop = authHdr.substring(start + 5, end);
            }
        }
    }

    public String calculateMD5DigestResponse()
    {
        String firstStr = new String(username + ":" + realm + ":" + password);
        String secondStr = new String(method + ":" + uri);

        String firstStrHashed = calculateMD5(firstStr);
        String secondStrHashed = calculateMD5(secondStr);

        if (qop.equals("auth") == true)
        {
            return calculateMD5(firstStrHashed + ":" + nonce + ":00000001:" +
                    getCnonce() + ":auth:" + secondStrHashed);
        }
        else
        {
            return calculateMD5(firstStrHashed + ":" + nonce +
                    ":" + secondStrHashed);

        }
    }

    public String getRealm()
    {
        return realm;
    }

    public String getNonce()
    {
        return nonce;
    }

    public String getOpaque()
    {
        return opaque;
    }

    public String getQop()
    {
        return qop;
    }

    public String getCnonce()
    {
        return cnonce;
    }

    private String calculateMD5(String value)
    {
        String finalString;
        try
        {
            MessageDigest md5Alg = MessageDigest.getInstance("MD5");
            md5Alg.reset();
            md5Alg.update(value.getBytes());
            byte messageDigest[] = md5Alg.digest();
            StringBuilder hexString = new StringBuilder(256);
            for (int i = 0; i < messageDigest.length; i++)
            {
                String hex = Integer.toHexString(0xFF & messageDigest[i]);
                if (hex.length() == 1)
                {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            finalString = hexString.toString();
        }
        catch (NoSuchAlgorithmException exc)
        {
            throw new RuntimeException("Hashing error happened:", exc);
        }
        return finalString;
    }

    private String username;

    private String password;

    private String realm;

    private String method;

    private String uri;

    private String nonce;

    private String cnonce;

    private String opaque;

    private String qop;
}
