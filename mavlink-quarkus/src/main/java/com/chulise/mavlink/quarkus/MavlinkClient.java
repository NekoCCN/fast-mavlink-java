package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketWriter;
import com.chulise.mavlink.core.MavlinkView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

public final class MavlinkClient
{
    private static final int MAX_PACKET_LEN_V2 = 10 + 255 + 2 + 13;

    private final String id;
    private final MavlinkTransport transport;
    private final MavlinkPacketWriter.Encoder encoder;
    private final MavlinkRequestManager requestManager;
    private final ThreadLocal<ByteBuffer> writeBuffer;
    private final MavlinkRequestOptions defaultRequestOptions;

    MavlinkClient(String id,
                  MavlinkTransport transport,
                  MavlinkPacketWriter.Encoder encoder,
                  MavlinkRequestManager requestManager,
                  MavlinkRequestOptions defaultRequestOptions)
    {
        this.id = id;
        this.transport = transport;
        this.encoder = encoder;
        this.requestManager = requestManager;
        this.defaultRequestOptions = defaultRequestOptions;
        this.writeBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(MAX_PACKET_LEN_V2)
                .order(ByteOrder.LITTLE_ENDIAN));
    }

    public String id()
    {
        return id;
    }

    public MavlinkPacketWriter.Encoder encoder()
    {
        return encoder;
    }

    @FunctionalInterface
    public interface PacketWriter
    {
        int write(ByteBuffer buffer, int offset);
    }

    public int send(PacketWriter writer)
    {
        ByteBuffer buffer = writeBuffer.get();
        buffer.clear();
        int written = writer.write(buffer, 0);
        if (written <= 0)
        {
            throw new IllegalArgumentException("writer returned invalid length: " + written);
        }
        transport.send(buffer, 0, written);
        return written;
    }

    public <T extends MavlinkView> CompletableFuture<T> request(Class<T> responseType,
                                                                int responseMessageId,
                                                                PacketWriter writer)
    {
        if (requestManager == null)
        {
            throw new IllegalStateException("request manager not available");
        }
        return request(responseType, responseMessageId, defaultRequestOptions, writer);
    }

    public <T extends MavlinkView> CompletableFuture<T> request(Class<T> responseType,
                                                                int responseMessageId,
                                                                MavlinkRequestOptions options,
                                                                PacketWriter writer)
    {
        if (requestManager == null)
        {
            throw new IllegalStateException("request manager not available");
        }
        CompletableFuture<T> future = requestManager.register(responseMessageId, responseType, options);
        send(writer);
        return future;
    }
}
