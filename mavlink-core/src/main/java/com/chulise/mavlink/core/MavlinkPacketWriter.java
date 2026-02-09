package com.chulise.mavlink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MavlinkPacketWriter
{
    private MavlinkPacketWriter()
    {
    }

    public static int writeV1(ByteBuffer out,
                              int offset,
                              int sequence,
                              int sysId,
                              int compId,
                              int messageId,
                              int crcExtra,
                              ByteBuffer payload,
                              int payloadOffset,
                              int payloadLength)
    {
        if (payloadLength < 0 || payloadLength > 255)
        {
            throw new IllegalArgumentException("payloadLength out of range: " + payloadLength);
        }
        if (messageId < 0 || messageId > 255)
        {
            throw new IllegalArgumentException("messageId out of range for V1: " + messageId);
        }

        copyPayload(out, offset + MavlinkPacketView.HEADER_LEN_V1, payload, payloadOffset, payloadLength);
        return writeV1InPlace(out, offset, sequence, sysId, compId, messageId, crcExtra, payloadLength);
    }

    public static int writeV2(ByteBuffer out,
                              int offset,
                              int sequence,
                              int sysId,
                              int compId,
                              int messageId,
                              int crcExtra,
                              ByteBuffer payload,
                              int payloadOffset,
                              int payloadLength,
                              int minPayloadLength,
                              boolean trimExtensionZeros,
                              int compatFlags,
                              int incompatFlags,
                              byte[] secretKey,
                              int linkId,
                              long timestamp)
    {
        if (payloadLength < 0 || payloadLength > 255)
        {
            throw new IllegalArgumentException("payloadLength out of range: " + payloadLength);
        }
        if (minPayloadLength < 0 || minPayloadLength > payloadLength)
        {
            throw new IllegalArgumentException("minPayloadLength out of range: " + minPayloadLength);
        }

        int effectivePayloadLen = payloadLength;
        if (trimExtensionZeros)
        {
            effectivePayloadLen = Math.max(minPayloadLength,
                    trimTrailingZeros(payload, payloadOffset, payloadLength));
        }

        copyPayload(out, offset + MavlinkPacketView.HEADER_LEN_V2, payload, payloadOffset, effectivePayloadLen);

        return writeV2InPlace(out,
                offset,
                sequence,
                sysId,
                compId,
                messageId,
                crcExtra,
                effectivePayloadLen,
                effectivePayloadLen,
                false,
                compatFlags,
                incompatFlags,
                secretKey,
                linkId,
                timestamp);
    }

    public static int writeV1InPlace(ByteBuffer out,
                                     int offset,
                                     int sequence,
                                     int sysId,
                                     int compId,
                                     int messageId,
                                     int crcExtra,
                                     int payloadLength)
    {
        if (payloadLength < 0 || payloadLength > 255)
        {
            throw new IllegalArgumentException("payloadLength out of range: " + payloadLength);
        }
        if (messageId < 0 || messageId > 255)
        {
            throw new IllegalArgumentException("messageId out of range for V1: " + messageId);
        }

        int totalLen = MavlinkPacketView.HEADER_LEN_V1 + payloadLength + 2;
        ensureCapacity(out, offset, totalLen);
        out.order(ByteOrder.LITTLE_ENDIAN);

        out.put(offset, (byte) MavlinkPacketView.MAGIC_V1);
        out.put(offset + 1, (byte) payloadLength);
        out.put(offset + 2, (byte) sequence);
        out.put(offset + 3, (byte) sysId);
        out.put(offset + 4, (byte) compId);
        out.put(offset + 5, (byte) messageId);

        int crc = MavlinkCrc.calculateCrc(out, offset + 1,
                (MavlinkPacketView.HEADER_LEN_V1 - 1) + payloadLength, crcExtra);
        out.putShort(offset + MavlinkPacketView.HEADER_LEN_V1 + payloadLength, (short) crc);

        return totalLen;
    }

    public static int writeV2InPlace(ByteBuffer out,
                                     int offset,
                                     int sequence,
                                     int sysId,
                                     int compId,
                                     int messageId,
                                     int crcExtra,
                                     int payloadLength,
                                     int minPayloadLength,
                                     boolean trimExtensionZeros,
                                     int compatFlags,
                                     int incompatFlags,
                                     byte[] secretKey,
                                     int linkId,
                                     long timestamp)
    {
        if (payloadLength < 0 || payloadLength > 255)
        {
            throw new IllegalArgumentException("payloadLength out of range: " + payloadLength);
        }
        if (minPayloadLength < 0 || minPayloadLength > payloadLength)
        {
            throw new IllegalArgumentException("minPayloadLength out of range: " + minPayloadLength);
        }

        int effectivePayloadLen = payloadLength;
        if (trimExtensionZeros)
        {
            int payloadOffset = offset + MavlinkPacketView.HEADER_LEN_V2;
            effectivePayloadLen = Math.max(minPayloadLength,
                    trimTrailingZeros(out, payloadOffset, payloadLength));
        }

        boolean sign = secretKey != null && secretKey.length > 0;
        int signedFlag = sign ? MavlinkPacketView.INCOMPAT_FLAG_SIGNED : 0;
        if (!sign && (incompatFlags & MavlinkPacketView.INCOMPAT_FLAG_SIGNED) != 0)
        {
            throw new IllegalArgumentException("signed flag set without secretKey");
        }

        int headerLen = MavlinkPacketView.HEADER_LEN_V2;
        int signatureLen = sign ? MavlinkPacketView.SIGNATURE_LEN : 0;
        int totalLen = headerLen + effectivePayloadLen + 2 + signatureLen;
        ensureCapacity(out, offset, totalLen);
        out.order(ByteOrder.LITTLE_ENDIAN);

        out.put(offset, (byte) MavlinkPacketView.MAGIC_V2);
        out.put(offset + 1, (byte) effectivePayloadLen);
        out.put(offset + 2, (byte) (incompatFlags | signedFlag));
        out.put(offset + 3, (byte) compatFlags);
        out.put(offset + 4, (byte) sequence);
        out.put(offset + 5, (byte) sysId);
        out.put(offset + 6, (byte) compId);
        putInt24(out, offset + 7, messageId);

        int crc = MavlinkCrc.calculateCrc(out, offset + 1, (headerLen - 1) + effectivePayloadLen, crcExtra);
        out.putShort(offset + headerLen + effectivePayloadLen, (short) crc);

        if (sign)
        {
            int sigOffset = offset + headerLen + effectivePayloadLen + 2;
            writeSignature(out, sigOffset, linkId, timestamp, secretKey,
                    offset, headerLen + effectivePayloadLen + 2);
        }

        return totalLen;
    }

    private static void copyPayload(ByteBuffer out, int outOffset, ByteBuffer payload, int payloadOffset, int payloadLength)
    {
        ByteBuffer src = payload.duplicate();
        src.position(payloadOffset);
        src.limit(payloadOffset + payloadLength);
        ByteBuffer dst = out.duplicate();
        dst.position(outOffset);
        dst.put(src);
    }

    private static int trimTrailingZeros(ByteBuffer payload, int payloadOffset, int payloadLength)
    {
        for (int i = payloadLength - 1; i >= 0; i--)
        {
            if (payload.get(payloadOffset + i) != 0)
            {
                return i + 1;
            }
        }
        return 0;
    }

    private static void putInt24(ByteBuffer out, int offset, int value)
    {
        out.put(offset, (byte) (value & 0xFF));
        out.put(offset + 1, (byte) ((value >> 8) & 0xFF));
        out.put(offset + 2, (byte) ((value >> 16) & 0xFF));
    }

    private static void writeSignature(ByteBuffer out,
                                       int sigOffset,
                                       int linkId,
                                       long timestamp,
                                       byte[] secretKey,
                                       int packetOffset,
                                       int packetLengthWithCrc)
    {
        out.put(sigOffset, (byte) linkId);
        for (int i = 0; i < 6; i++)
        {
            out.put(sigOffset + 1 + i, (byte) ((timestamp >> (8 * i)) & 0xFF));
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secretKey);

            ByteBuffer dup = out.duplicate();
            dup.position(packetOffset);
            dup.limit(packetOffset + packetLengthWithCrc);
            digest.update(dup);

            byte[] sigHeader = new byte[7];
            for (int i = 0; i < sigHeader.length; i++)
            {
                sigHeader[i] = out.get(sigOffset + i);
            }
            digest.update(sigHeader);

            byte[] hash = digest.digest();
            for (int i = 0; i < 6; i++)
            {
                out.put(sigOffset + 7 + i, hash[i]);
            }
        } catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void ensureCapacity(ByteBuffer out, int offset, int length)
    {
        if (offset < 0 || length < 0 || offset + length > out.limit())
        {
            throw new IllegalArgumentException("output buffer too small");
        }
    }
}
