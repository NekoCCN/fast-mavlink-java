package com.chulise.mavlink.core;

import java.nio.ByteBuffer;

public class MavlinkParser
{
    private final MavlinkPacketView packetView = new MavlinkPacketView();

    public ParseResult next(ByteBuffer buffer, int startOffset)
    {
        return nextInternal(buffer, startOffset, null, false, null, false, false);
    }

    public ParseResult nextStrict(ByteBuffer buffer, int startOffset, MavlinkDialect dialect)
    {
        return nextInternal(buffer, startOffset, dialect, true, null, false, false);
    }

    public ParseResult nextStrict(ByteBuffer buffer, int startOffset, MavlinkDialect dialect, byte[] secretKey, boolean allowUnknown)
    {
        return nextInternal(buffer, startOffset, dialect, true, secretKey, allowUnknown, true);
    }

    private ParseResult nextInternal(ByteBuffer buffer,
                                     int startOffset,
                                     MavlinkDialect dialect,
                                     boolean strict,
                                     byte[] secretKey,
                                     boolean allowUnknown,
                                     boolean requireSignature)
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
                if ((incompatFlags & MavlinkPacketView.INCOMPAT_FLAG_SIGNED) != 0)
                {
                    signatureLen = MavlinkPacketView.SIGNATURE_LEN;
                }
            }

            int totalPacketLen = headerLen + payloadLen + crcLen + signatureLen;

            if (cursor + totalPacketLen > limit)
            {
                return null;
            }

            packetView.wrap(buffer, cursor);

            if (strict && !validateStrict(packetView, dialect, secretKey, allowUnknown, requireSignature))
            {
                cursor++;
                continue;
            }

            return new ParseResult(packetView, totalPacketLen, cursor);
        }

        return null;
    }

    public record ParseResult(MavlinkPacketView view, int length, int startOffset)
    {
    }

    private boolean validateStrict(MavlinkPacketView packet,
                                   MavlinkDialect dialect,
                                   byte[] secretKey,
                                   boolean allowUnknown,
                                   boolean requireSignature)
    {
        if (packet.isV2())
        {
            int unknown = packet.getIncompatFlags() & ~MavlinkPacketView.KNOWN_INCOMPAT_FLAGS;
            if (unknown != 0)
            {
                return false;
            }
        }

        if (packet.hasSignature() && requireSignature)
        {
            if (secretKey == null || secretKey.length == 0)
            {
                return false;
            }
            if (!packet.validateSignature(secretKey))
            {
                return false;
            }
        }

        if (dialect == null)
        {
            return true;
        }

        MavlinkView view = dialect.resolve(packet.getMessageId());
        if (view == null)
        {
            return allowUnknown;
        }

        int payloadLen = packet.getPayloadLength();
        int lenV1 = view.getLengthV1();
        int lenV2 = view.getLengthV2();

        if (packet.isV2())
        {
            if (payloadLen < lenV1 || payloadLen > lenV2)
            {
                return false;
            }
        } else
        {
            if (payloadLen != lenV1)
            {
                return false;
            }
        }

        return packet.validateCrc(view.getCrcExtra());
    }
}
