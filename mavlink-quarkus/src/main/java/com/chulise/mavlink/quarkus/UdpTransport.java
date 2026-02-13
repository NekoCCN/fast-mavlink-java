package com.chulise.mavlink.quarkus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.function.Consumer;

final class UdpTransport implements MavlinkTransport
{
    private final InetSocketAddress bind;
    private final InetSocketAddress remote;
    private final int bufferSize;
    private volatile boolean running;
    private DatagramChannel channel;
    private Thread thread;
    private volatile SocketAddress lastRemote;

    UdpTransport(InetSocketAddress bind, InetSocketAddress remote, int bufferSize)
    {
        this.bind = bind;
        this.remote = remote;
        this.bufferSize = Math.max(512, bufferSize);
    }

    @Override
    public boolean isStream()
    {
        return false;
    }

    @Override
    public void start(Consumer<ByteBuffer> onPacket)
    {
        if (running)
        {
            return;
        }
        running = true;
        thread = new Thread(() -> runLoop(onPacket), "mavlink-udp");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop(Consumer<ByteBuffer> onPacket)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        try (DatagramChannel ch = DatagramChannel.open())
        {
            this.channel = ch;
            if (bind != null)
            {
                ch.bind(bind);
            }
            while (running)
            {
                buffer.clear();
                SocketAddress src = ch.receive(buffer);
                if (src != null)
                {
                    lastRemote = src;
                    buffer.flip();
                    if (buffer.hasRemaining())
                    {
                        onPacket.accept(buffer);
                    }
                }
            }
        } catch (IOException e)
        {
            throw new IllegalStateException("UDP transport error", e);
        }
    }

    @Override
    public void send(ByteBuffer buffer, int offset, int length)
    {
        DatagramChannel ch = this.channel;
        if (ch == null)
        {
            throw new IllegalStateException("UDP transport not started");
        }
        InetSocketAddress dst = this.remote != null ? this.remote : (InetSocketAddress) lastRemote;
        if (dst == null)
        {
            throw new IllegalStateException("UDP remote is not set");
        }

        ByteBuffer dup = buffer.duplicate();
        dup.position(offset);
        dup.limit(offset + length);
        try
        {
            ch.send(dup, dst);
        } catch (IOException e)
        {
            throw new IllegalStateException("UDP send failed", e);
        }
    }

    @Override
    public void close()
    {
        running = false;
        if (thread != null)
        {
            thread.interrupt();
        }
        if (channel != null)
        {
            try
            {
                channel.close();
            } catch (IOException ignore)
            {
            }
        }
    }
}
