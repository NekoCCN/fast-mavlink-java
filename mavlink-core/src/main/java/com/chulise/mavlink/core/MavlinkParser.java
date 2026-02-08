package com.chulise.mavlink.core;

import java.nio.ByteBuffer;

public class MavlinkParser
{
    private final MavlinkPacketView packetView = new MavlinkPacketView();

    public ParseResult next(ByteBuffer buffer, int startOffset)
    {
        int limit = buffer.limit();
        int cursor = startOffset;

        while (cursor < limit)
        {
            int magic = buffer.get(cursor) & 0xFF;
            if (magic != MavlinkPacketView.MAGIC_V1 && magic != MavlinkPacketView.MAGIC_V2)
            {
                cursor++;
                continue;
            }

            int headerLen = (magic == MavlinkPacketView.MAGIC_V2) ? 10 : 6;
            if (cursor + headerLen > limit)
            {
                return null;
            }

            int payloadLen = buffer.get(cursor + 1) & 0xFF;

            int crcLen = 2;
            int signatureLen = 0;

            if (magic == MavlinkPacketView.MAGIC_V2)
            {
                int incompatFlags = buffer.get(cursor + 2);
                if ((incompatFlags & 0x01) != 0)
                {
                    signatureLen = 13;
                }
            }

            int totalPacketLen = headerLen + payloadLen + crcLen + signatureLen;

            if (cursor + totalPacketLen > limit)
            {
                return null;
            }

            packetView.wrap(buffer, cursor);

            return new ParseResult(packetView, totalPacketLen, cursor);
        }

        return null;
    }

    public record ParseResult(MavlinkPacketView view, int length, int startOffset)
    {
    }
}