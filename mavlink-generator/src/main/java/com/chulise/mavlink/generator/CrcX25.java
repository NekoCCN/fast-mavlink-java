package com.chulise.mavlink.generator;

import java.nio.charset.StandardCharsets;

public class CrcX25
{
    private int crc = 0xFFFF;

    public void update(String str)
    {
        for (byte b : str.getBytes(StandardCharsets.US_ASCII))
        {
            update(b);
        }
    }

    public void update(int data)
    {
        int tmp = (data & 0xFF) ^ (crc & 0xFF);
        tmp ^= (tmp << 4) & 0xFF;

        crc = ((crc >> 8) & 0xFF) ^ (tmp << 8) ^ (tmp << 3) ^ ((tmp >> 4) & 0xFF);
    }

    public int get()
    {
        return crc & 0xFFFF;
    }
}