package com.chulise.mavlink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MavlinkPacketView
{
    public static final int MAGIC_V1 = 0xFE;
    public static final int MAGIC_V2 = 0xFD;
    public static final int INCOMPAT_FLAG_SIGNED = 0x01;
    public static final int KNOWN_INCOMPAT_FLAGS = INCOMPAT_FLAG_SIGNED;

    public static final int HEADER_LEN_V1 = 6;
    public static final int HEADER_LEN_V2 = 10;
    public static final int SIGNATURE_LEN = 13;

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

    public boolean isV2()
    {
        return isV2;
    }

    public boolean hasSignature()
    {
        return isV2 && (getIncompatFlags() & INCOMPAT_FLAG_SIGNED) != 0;
    }

    public int getSignatureLength()
    {
        return hasSignature() ? SIGNATURE_LEN : 0;
    }

    public int getSignatureOffset()
    {
        return getPayloadOffset() + getPayloadLength() + 2;
    }

    public int getSignatureLinkId()
    {
        if (!hasSignature())
        {
            return -1;
        }
        return buffer.get(getSignatureOffset()) & 0xFF;
    }

    public long getSignatureTimestamp()
    {
        if (!hasSignature())
        {
            return -1;
        }

        int off = getSignatureOffset() + 1;
        long ts = 0;
        for (int i = 0; i < 6; i++)
        {
            ts |= ((long) buffer.get(off + i) & 0xFF) << (8 * i);
        }
        return ts;
    }

    public boolean validateSignature(byte[] secretKey)
    {
        if (!hasSignature() || secretKey == null || secretKey.length == 0)
        {
            return false;
        }

        int headerLen = isV2 ? HEADER_LEN_V2 : HEADER_LEN_V1;
        int payloadLen = getPayloadLength();
        int packetLengthWithCrc = headerLen + payloadLen + 2;
        int signatureOffset = getSignatureOffset();
        if (signatureOffset + SIGNATURE_LEN > buffer.limit())
        {
            return false;
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secretKey);

            ByteBuffer dup = buffer.duplicate();
            dup.position(startOffset);
            dup.limit(startOffset + packetLengthWithCrc);
            digest.update(dup);

            byte[] sigHeader = new byte[7];
            for (int i = 0; i < sigHeader.length; i++)
            {
                sigHeader[i] = buffer.get(signatureOffset + i);
            }
            digest.update(sigHeader);

            byte[] hash = digest.digest();
            for (int i = 0; i < 6; i++)
            {
                if (hash[i] != buffer.get(signatureOffset + 7 + i))
                {
                    return false;
                }
            }
            return true;
        } catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
