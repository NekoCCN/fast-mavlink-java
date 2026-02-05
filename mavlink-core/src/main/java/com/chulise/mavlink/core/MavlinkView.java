package com.chulise.mavlink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class MavlinkView
{
    protected ByteBuffer buffer;
    protected int offset;

    private static final ThreadLocal<ByteBuffer> SHADOW_BUF = ThreadLocal.withInitial(() ->
            ByteBuffer.allocate(280).order(ByteOrder.LITTLE_ENDIAN)
    );

    public void wrap(MavlinkPacketView packet)
    {
        int receivedLen = packet.getPayloadLength();
        int definedLen = getLengthV2();

        if (receivedLen < definedLen)
        {
            ByteBuffer shadow = SHADOW_BUF.get();
            shadow.clear();

            ByteBuffer src = packet.getBuffer();
            int payloadStart = packet.getPayloadOffset();

            for (int i = 0; i < receivedLen; i++)
            {
                shadow.put(src.get(payloadStart + i));
            }

            int zeroLen = definedLen - receivedLen;
            for (int i = 0; i < zeroLen; i++)
            {
                shadow.put((byte) 0);
            }

            this.buffer = shadow;
            this.offset = 0;

        } else
        {
            this.buffer = packet.getBuffer();
            this.offset = packet.getPayloadOffset();
        }

        if (this.buffer.order() != ByteOrder.LITTLE_ENDIAN)
        {
            this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    public abstract int getMessageId();

    public abstract int getCrcExtra();

    public abstract int getLengthV1();

    public abstract int getLengthV2();

    @Override
    public String toString()
    {
        return "MavlinkMsg(ID=" + getMessageId() + ")";
    }
}