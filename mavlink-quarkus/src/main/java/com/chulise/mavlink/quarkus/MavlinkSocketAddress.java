package com.chulise.mavlink.quarkus;

import java.net.InetSocketAddress;

final class MavlinkSocketAddress
{
    private MavlinkSocketAddress()
    {
    }

    static InetSocketAddress parse(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        String v = value.trim();
        int idx = v.lastIndexOf(':');
        if (idx <= 0 || idx == v.length() - 1)
        {
            throw new IllegalArgumentException("invalid address: " + value);
        }

        String host = v.substring(0, idx).trim();
        String portStr = v.substring(idx + 1).trim();
        int port = Integer.parseInt(portStr);
        return new InetSocketAddress(host, port);
    }
}
