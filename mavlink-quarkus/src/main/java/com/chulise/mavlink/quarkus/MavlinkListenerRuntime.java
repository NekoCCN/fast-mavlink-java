package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkDialect;
import com.chulise.mavlink.core.MavlinkParser;
import com.chulise.mavlink.core.MavlinkPacketWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MavlinkListenerRuntime implements AutoCloseable
{
    private static final int STREAM_BUFFER_SIZE = 8192;

    private final String id;
    private final MavlinkParser parser;
    private final MavlinkDispatcher dispatcher;
    private final MavlinkRequestManager requestManager;
    private final MavlinkTransport transport;
    private final MavlinkDialect dialect;
    private final MavlinkClient client;
    private ByteBuffer streamBuffer;
    private int streamWritePos;

    MavlinkListenerRuntime(String id,
                           MavlinkParser parser,
                           MavlinkDispatcher dispatcher,
                           MavlinkTransport transport,
                           MavlinkPacketWriter.Encoder encoder,
                           MavlinkRequestManager requestManager,
                           MavlinkRequestOptions defaultRequestOptions,
                           MavlinkDialect dialect)
    {
        this.id = id;
        this.parser = parser;
        this.dispatcher = dispatcher;
        this.requestManager = requestManager;
        this.transport = transport;
        this.dialect = dialect;
        this.client = new MavlinkClient(id, transport, encoder, requestManager, defaultRequestOptions);
    }

    String id()
    {
        return id;
    }

    MavlinkClient client()
    {
        return client;
    }

    MavlinkDispatcher dispatcher()
    {
        return dispatcher;
    }

    void start()
    {
        transport.start(this::onData);
    }

    private void onData(ByteBuffer data)
    {
        if (transport.isStream())
        {
            onStreamData(data);
        } else
        {
            onDatagram(data);
        }
    }

    private void onDatagram(ByteBuffer buffer)
    {
        int limit = buffer.limit();
        int cursor = 0;
        while (cursor < limit)
        {
            MavlinkParser.ParseResult res = parser.next(buffer, cursor, dialect);
            if (res == null)
            {
                return;
            }
            dispatcher.dispatch(res.view());
            cursor = res.startOffset() + res.length();
        }
    }

    private void onStreamData(ByteBuffer chunk)
    {
        if (streamBuffer == null)
        {
            streamBuffer = ByteBuffer.allocateDirect(STREAM_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            streamWritePos = 0;
        }

        int incoming = chunk.remaining();
        ensureStreamCapacity(incoming);
        for (int i = 0; i < incoming; i++)
        {
            streamBuffer.put(streamWritePos + i, chunk.get(chunk.position() + i));
        }
        streamWritePos += incoming;

        int limit = streamWritePos;
        int cursor = 0;
        while (cursor < limit)
        {
            MavlinkParser.ParseResult res = parser.next(streamBuffer, cursor, dialect);
            if (res == null)
            {
                break;
            }
            dispatcher.dispatch(res.view());
            cursor = res.startOffset() + res.length();
        }

        if (cursor > 0)
        {
            int remaining = limit - cursor;
            for (int i = 0; i < remaining; i++)
            {
                streamBuffer.put(i, streamBuffer.get(cursor + i));
            }
            streamWritePos = remaining;
        } else if (limit == streamBuffer.capacity())
        {
            streamWritePos = 0;
        }
    }

    private void ensureStreamCapacity(int incoming)
    {
        int required = streamWritePos + incoming;
        if (required <= streamBuffer.capacity())
        {
            return;
        }
        int newCap = Math.max(required, streamBuffer.capacity() * 2);
        ByteBuffer next = ByteBuffer.allocateDirect(newCap).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < streamWritePos; i++)
        {
            next.put(i, streamBuffer.get(i));
        }
        streamBuffer = next;
    }

    @Override
    public void close()
    {
        transport.close();
    }
}
