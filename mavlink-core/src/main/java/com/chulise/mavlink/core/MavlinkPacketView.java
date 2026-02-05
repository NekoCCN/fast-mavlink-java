package com.chulise.mavlink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MavlinkPacketView
{
    public static final int MAGIC_V1 = 0xFE;
    public static final int MAGIC_V2 = 0xFD;

    public static final int HEADER_LEN_V1 = 6;
    public static final int HEADER_LEN_V2 = 10;

    private ByteBuffer buffer;
    private int startOffset;
    private boolean isV2;

    public void wrap(ByteBuffer buffer, int startOffset)
    {
        this.buffer = buffer;
        this.startOffset = startOffset;
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN)
        {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        int magic = buffer.get(startOffset) & 0xFF;
        this.isV2 = (magic == MAGIC_V2);
    }

    public int getMagic()
    {
        return buffer.get(startOffset) & 0xFF;
    }

    public int getPayloadLength()
    {
        return buffer.get(startOffset + 1) & 0xFF;
    }

    public int getIncompatFlags()
    {
        return isV2 ? (buffer.get(startOffset + 2) & 0xFF) : 0;
    }

    public int getCompatFlags()
    {
        return isV2 ? (buffer.get(startOffset + 3) & 0xFF) : 0;
    }

    public int getSequence()
    {
        int pos = isV2 ? 4 : 2;
        return buffer.get(startOffset + pos) & 0xFF;
    }

    public int getSysId()
    {
        int pos = isV2 ? 5 : 3;
        return buffer.get(startOffset + pos) & 0xFF;
    }

    public int getCompId()
    {
        int pos = isV2 ? 6 : 4;
        return buffer.get(startOffset + pos) & 0xFF;
    }

    public int getMessageId()
    {
        if (isV2)
        {
            int b1 = buffer.get(startOffset + 7) & 0xFF;
            int b2 = buffer.get(startOffset + 8) & 0xFF;
            int b3 = buffer.get(startOffset + 9) & 0xFF;
            return b1 | (b2 << 8) | (b3 << 16);
        } else
        {
            return buffer.get(startOffset + 5) & 0xFF;
        }
    }

    public ByteBuffer getBuffer()
    {
        return buffer;
    }

    public int getPayloadOffset()
    {
        return startOffset + (isV2 ? HEADER_LEN_V2 : HEADER_LEN_V1);
    }

    public boolean validateCrc(int crcExtra)
    {
        int payloadLen = getPayloadLength();
        int headerLen = isV2 ? HEADER_LEN_V2 : HEADER_LEN_V1;

        int crcCalcLength = (headerLen - 1) + payloadLen;

        int calculated = MavlinkCrc.calculateCrc(buffer, startOffset + 1, crcCalcLength, crcExtra);

        int crcPosition = startOffset + headerLen + payloadLen;
        int received = Short.toUnsignedInt(buffer.getShort(crcPosition));

        return calculated == received;
    }
}