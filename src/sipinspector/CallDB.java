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

import sipinspector.Utils.SIPParser;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author Zarko Coklin
 */
public class CallDB implements Map<String, SIPCall>
{
    public CallDB(int initCapacity, int maxAllowed, int maxConcurrent)
    {
        internalMap = Collections.synchronizedMap(new HashMap(initCapacity));
        totalCallsNumber = 0;
        maxAllowedCalls = maxAllowed;
        maxConcurrentCalls = maxConcurrent;
    }

    public boolean addCall(SIPCall call, String callId, boolean totCallsFlag)
    {
        if (callId != null && callId.equals("") == false)
        {
            put(callId, call);
            if (totCallsFlag == true)
            {
                totalCallsNumber++;
            }
            return true;
        }

        return false;
    }

    public boolean deleteCall(SIPCall call)
    {
        String[] list;

        if (call.getCallIDList() == null)
        {
            return false;
        }
        list = call.getCallIDList().toArray(new String[0]);
        for (String callId : list)
        {
            remove(callId);
        }

        return true;
    }

    public void resetCallCounter()
    {
        totalCallsNumber = 0;
    }

    public SIPCall getCall(String callID)
    {
        return (SIPCall) get(callID);
    }

    public SIPCall findCallForMsg(String msg)
    {
        return (SIPCall) get(SIPParser.getCallID(msg));
    }

    public int getTotalCallsNumber()
    {
        return totalCallsNumber;
    }

    public int getConcurrentCallsNum()
    {
        return this.size();
    }

    public boolean isMaxAllowedReached()
    {
        if (maxAllowedCalls != 0 && totalCallsNumber >= maxAllowedCalls)
        {
            return true;
        }
        return false;
    }

    public int size()
    {
        return internalMap.size();
    }

    public boolean isEmpty()
    {
        return internalMap.isEmpty();
    }

    public boolean containsKey(Object key)
    {
        return internalMap.containsKey((String) key);
    }

    public boolean containsValue(Object value)
    {
        return internalMap.containsValue((SIPCall) value);
    }

    public SIPCall get(Object key)
    {
        return internalMap.get((String) key);
    }

    public SIPCall put(String key, SIPCall value)
    {
        return internalMap.put(key, value);

    }

    public SIPCall remove(Object key)
    {
        return internalMap.remove((String) key);
    }

    public void putAll(Map<? extends String, ? extends SIPCall> m)
    {
        internalMap.putAll(m);
    }

    public void clear()
    {
        internalMap.clear();
    }

    public Set<String> keySet()
    {
        return internalMap.keySet();
    }

    public Collection<SIPCall> values()
    {
        return internalMap.values();
    }

    public Set<Entry<String, SIPCall>> entrySet()
    {
        return internalMap.entrySet();
    }

    public int getMaxConcurrCalls()
    {
        return maxConcurrentCalls;
    }

    public int getConcurrCalls()
    {
        return internalMap.size();
    }

    private int totalCallsNumber;

    private int maxAllowedCalls;

    private int maxConcurrentCalls;

    private Map<String, SIPCall> internalMap;
}
