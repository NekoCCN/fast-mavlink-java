package com.chulise.mavlink.netty;

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MavlinkByteBufWriter
{
    public static final int MAX_PACKET_LEN_V1 = 6 + 255 + 2;
    public static final int MAX_PACKET_LEN_V2 = 10 + 255 + 2 + 13;

    private MavlinkByteBufWriter()
    {
    }

    @FunctionalInterface
    public interface PacketWriter
    {
        int write(ByteBuffer buffer, int offset);
    }

    public static int write(ByteBuf out, PacketWriter writer)
    {
        return write(out, MAX_PACKET_LEN_V2, writer);
    }

    public static int write(ByteBuf out, int maxLen, PacketWriter writer)
    {
        if (out == null)
        {
            throw new IllegalArgumentException("out is null");
        }
        if (writer == null)
        {
            throw new IllegalArgumentException("writer is null");
        }
        if (maxLen <= 0)
        {
            throw new IllegalArgumentException("maxLen must be > 0");
        }

        int index = out.writerIndex();
        out.ensureWritable(maxLen);

        ByteBuffer nio = out.nioBuffer(index, maxLen);
        if (nio.order() != ByteOrder.LITTLE_ENDIAN)
        {
            nio.order(ByteOrder.LITTLE_ENDIAN);
        }
        int written = writer.write(nio, nio.position());
        if (written < 0 || written > maxLen)
        {
            throw new IllegalArgumentException("writer returned invalid length: " + written);
        }

        out.writerIndex(index + written);
        return written;
    }
}
