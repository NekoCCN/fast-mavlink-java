package com.chulise.mavlink.netty;

import com.chulise.mavlink.core.MavlinkDialect;
import com.chulise.mavlink.core.MavlinkParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.ByteBuffer;
import java.util.List;

public final class MavlinkPacketDecoder extends ByteToMessageDecoder
{
    private final MavlinkParser parser;
    private final MavlinkDialect dialect;

    public MavlinkPacketDecoder()
    {
        this(new MavlinkParser(), null);
    }

    public MavlinkPacketDecoder(MavlinkParser parser)
    {
        this(parser, null);
    }

    public MavlinkPacketDecoder(MavlinkParser.Options options)
    {
        this(new MavlinkParser(options), null);
    }

    public MavlinkPacketDecoder(MavlinkParser parser, MavlinkDialect dialect)
    {
        if (parser == null)
        {
            throw new IllegalArgumentException("parser is null");
        }
        this.parser = parser;
        this.dialect = dialect;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
    {
        for (;;)
        {
            int readable = in.readableBytes();
            if (readable <= 0)
            {
                return;
            }

            ByteBuffer nio = in.nioBuffer(in.readerIndex(), readable);
            MavlinkParser.ParseResult result = parser.next(nio, 0, dialect);
            if (result == null)
            {
                return;
            }

            int discard = result.startOffset();
            if (discard > 0)
            {
                if (discard > in.readableBytes())
                {
                    return;
                }
                in.skipBytes(discard);
            }

            int length = result.length();
            if (length <= 0 || length > in.readableBytes())
            {
                return;
            }

            ByteBuf frame = in.readRetainedSlice(length);
            out.add(new MavlinkPacket(frame));
        }
    }
}
