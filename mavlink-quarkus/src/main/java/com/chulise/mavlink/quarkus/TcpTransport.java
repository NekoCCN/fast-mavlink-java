package com.chulise.mavlink.quarkus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

final class TcpTransport implements MavlinkTransport
{
    private static final long RECONNECT_DELAY_MS = 1000L;
    private static final long MAX_READ_IDLE_MS = 5000L;

    private final InetSocketAddress bind;
    private final InetSocketAddress remote;
    private final int bufferSize;
    private volatile boolean running;
    private volatile SocketChannel channel;
    private volatile long lastReadAtMillis;
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
        while (running)
        {
            SocketChannel ch = null;
            try
            {
                ch = SocketChannel.open();
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
                this.channel = ch;
                this.lastReadAtMillis = System.currentTimeMillis();

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
                        this.lastReadAtMillis = System.currentTimeMillis();
                        buffer.flip();
                        onPacket.accept(buffer);
                    }
                }
            } catch (IOException e)
            {
                // Reconnect loop handles transient transport failures.
            } finally
            {
                this.channel = null;
                if (ch != null)
                {
                    try
                    {
                        ch.close();
                    } catch (IOException ignore)
                    {
                    }
                }
            }

            if (running)
            {
                sleepBeforeReconnect();
            }
        }
    }

    private void sleepBeforeReconnect()
    {
        try
        {
            Thread.sleep(RECONNECT_DELAY_MS);
        } catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
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
        long idle = System.currentTimeMillis() - lastReadAtMillis;
        if (idle > MAX_READ_IDLE_MS)
        {
            closeChannelQuietly(ch);
            this.channel = null;
            throw new IllegalStateException("TCP transport read idle timeout, reconnecting");
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
            closeChannelQuietly(ch);
            this.channel = null;
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
            closeChannelQuietly(channel);
        }
    }

    private static void closeChannelQuietly(SocketChannel ch)
    {
        if (ch == null)
        {
            return;
        }
        try
        {
            ch.close();
        } catch (IOException ignore)
        {
        }
    }
}
