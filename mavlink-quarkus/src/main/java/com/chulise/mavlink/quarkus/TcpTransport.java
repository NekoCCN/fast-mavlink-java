package com.chulise.mavlink.quarkus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

final class TcpTransport implements MavlinkTransport
{
    private final InetSocketAddress bind;
    private final InetSocketAddress remote;
    private final int bufferSize;
    private volatile boolean running;
    private SocketChannel channel;
    private Thread thread;

    TcpTransport(InetSocketAddress bind, InetSocketAddress remote, int bufferSize)
    {
        this.bind = bind;
        this.remote = remote;
        this.bufferSize = Math.max(1024, bufferSize);
    }

    @Override
    public boolean isStream()
    {
        return true;
    }

    @Override
    public void start(Consumer<ByteBuffer> onPacket)
    {
        if (running)
        {
            return;
        }
        running = true;
        thread = new Thread(() -> runLoop(onPacket), "mavlink-tcp");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop(Consumer<ByteBuffer> onPacket)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        try (SocketChannel ch = SocketChannel.open())
        {
            this.channel = ch;
            if (bind != null)
            {
                ch.bind(bind);
            }
            if (remote == null)
            {
                throw new IllegalStateException("TCP remote is required");
            }
            ch.connect(remote);
            ch.configureBlocking(true);

            while (running)
            {
                buffer.clear();
                int n = ch.read(buffer);
                if (n < 0)
                {
                    break;
                }
                if (n > 0)
                {
                    buffer.flip();
                    onPacket.accept(buffer);
                }
            }
        } catch (IOException e)
        {
            throw new IllegalStateException("TCP transport error", e);
        }
    }

    @Override
    public void send(ByteBuffer buffer, int offset, int length)
    {
        SocketChannel ch = this.channel;
        if (ch == null)
        {
            throw new IllegalStateException("TCP transport not started");
        }
        ByteBuffer dup = buffer.duplicate();
        dup.position(offset);
        dup.limit(offset + length);
        try
        {
            while (dup.hasRemaining())
            {
                ch.write(dup);
            }
        } catch (IOException e)
        {
            throw new IllegalStateException("TCP send failed", e);
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
