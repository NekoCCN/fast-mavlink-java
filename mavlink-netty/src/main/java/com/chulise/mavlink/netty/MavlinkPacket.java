package com.chulise.mavlink.netty;

import com.chulise.mavlink.core.MavlinkPacketView;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import java.nio.ByteBuffer;

public final class MavlinkPacket implements ReferenceCounted
{
    private final ByteBuf content;
    private final MavlinkPacketView view;

    public MavlinkPacket(ByteBuf content)
    {
        if (content == null)
        {
            throw new IllegalArgumentException("content is null");
        }
        this.content = content;
        this.view = new MavlinkPacketView();
        ByteBuffer nio = content.nioBuffer();
        this.view.wrap(nio, nio.position());
    }

    public ByteBuf content()
    {
        return content;
    }

    public MavlinkPacketView view()
    {
        return view;
    }

    @Override
    public int refCnt()
    {
        return content.refCnt();
    }

    @Override
    public MavlinkPacket retain()
    {
        content.retain();
        return this;
    }

    @Override
    public MavlinkPacket retain(int increment)
    {
        content.retain(increment);
        return this;
    }

    @Override
    public MavlinkPacket touch()
    {
        content.touch();
        return this;
    }

    @Override
    public MavlinkPacket touch(Object hint)
    {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release()
    {
        return content.release();
    }

    @Override
    public boolean release(int decrement)
    {
        return content.release(decrement);
    }
}
