package com.chulise.mavlink.quarkus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class TcpServerTransport implements MavlinkTransport
{
    private final InetSocketAddress bind;
    private final int bufferSize;
    private final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private ServerSocketChannel server;
    private Thread acceptThread;
    private volatile SocketChannel lastActive;

    TcpServerTransport(InetSocketAddress bind, int bufferSize)
    {
        this.bind = bind;
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
        if (bind == null)
        {
            throw new IllegalStateException("TCP server bind is required");
        }
        running = true;
        acceptThread = new Thread(() -> acceptLoop(onPacket), "mavlink-tcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop(Consumer<ByteBuffer> onPacket)
    {
        try (ServerSocketChannel srv = ServerSocketChannel.open())
        {
            this.server = srv;
            srv.bind(bind);
            while (running)
            {
                SocketChannel ch = srv.accept();
                if (ch == null)
                {
                    continue;
                }
                ch.configureBlocking(true);
                clients.add(ch);
                Thread t = new Thread(() -> readLoop(ch, onPacket), "mavlink-tcp-client");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e)
        {
            throw new IllegalStateException("TCP server transport error", e);
        }
    }

    private void readLoop(SocketChannel ch, Consumer<ByteBuffer> onPacket)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        try
        {
            while (running && ch.isOpen())
            {
                buffer.clear();
                int n = ch.read(buffer);
                if (n < 0)
                {
                    break;
                }
                if (n > 0)
                {
                    lastActive = ch;
                    buffer.flip();
                    onPacket.accept(buffer);
                }
            }
        } catch (IOException e)
        {
            // ignore
        } finally
        {
            clients.remove(ch);
            try
            {
                ch.close();
            } catch (IOException ignore)
            {
            }
        }
    }

    @Override
    public void send(ByteBuffer buffer, int offset, int length)
    {
        SocketChannel dst = selectTarget();
        if (dst == null)
        {
            throw new IllegalStateException("TCP server has no active client");
        }
        ByteBuffer dup = buffer.duplicate();
        dup.position(offset);
        dup.limit(offset + length);
        try
        {
            while (dup.hasRemaining())
            {
                dst.write(dup);
            }
        } catch (IOException e)
        {
            throw new IllegalStateException("TCP server send failed", e);
        }
    }

    private SocketChannel selectTarget()
    {
        SocketChannel last = lastActive;
        if (last != null && last.isOpen())
        {
            return last;
        }
        if (clients.size() == 1)
        {
            SocketChannel only = clients.get(0);
            if (only.isOpen())
            {
                return only;
            }
        }
        return null;
    }

    @Override
    public void close()
    {
        running = false;
        if (acceptThread != null)
        {
            acceptThread.interrupt();
        }
        if (server != null)
        {
            try
            {
                server.close();
            } catch (IOException ignore)
            {
            }
        }
        for (SocketChannel ch : clients)
        {
            try
            {
                ch.close();
            } catch (IOException ignore)
            {
            }
        }
        clients.clear();
    }
}
