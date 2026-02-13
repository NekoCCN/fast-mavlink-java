package com.chulise.mavlink.quarkus;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

interface MavlinkTransport extends AutoCloseable
{
    boolean isStream();

    void start(Consumer<ByteBuffer> onPacket);

    void send(ByteBuffer buffer, int offset, int length);

    @Override
    void close();
}
