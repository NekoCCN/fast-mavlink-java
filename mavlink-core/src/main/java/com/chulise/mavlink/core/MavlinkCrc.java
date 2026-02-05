package com.chulise.mavlink.core;

import java.nio.ByteBuffer;

public final class MavlinkCrc
{
    private static final int CRC_INIT = 0xFFFF;

    public static int calculateCrc(ByteBuffer buffer, int offset, int length, int crcExtra)
    {
        int crc = CRC_INIT;
        for (int i = 0; i < length; i++)
        {
            int b = buffer.get(offset + i) & 0xFF;
            crc = accumulate(crc, b);
        }
        return accumulate(crc, crcExtra) & 0xFFFF;
    }

    private static int accumulate(int crc, int data)
    {
        int tmp = data ^ (crc & 0xFF);
        tmp ^= (tmp << 4) & 0xFF;
        return (crc >> 8) ^ (tmp << 8) ^ (tmp << 3) ^ (tmp >> 4);
    }
}