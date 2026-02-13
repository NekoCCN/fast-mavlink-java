package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkView;
import com.chulise.mavlink.core.MavlinkPacketView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

final class PendingRequest
{
    final int messageId;
    final long expireAt;
    final int expectedSysId;
    final int expectedCompId;
    final int expectedLinkId;
    final MavlinkResponseMatcher matcher;
    final CompletableFuture<?> future;
    final Class<? extends MavlinkView> responseType;

    PendingRequest(int messageId,
                   long expireAt,
                   int expectedSysId,
                   int expectedCompId,
                   int expectedLinkId,
                   MavlinkResponseMatcher matcher,
                   CompletableFuture<?> future,
                   Class<? extends MavlinkView> responseType)
    {
        this.messageId = messageId;
        this.expireAt = expireAt;
        this.expectedSysId = expectedSysId;
        this.expectedCompId = expectedCompId;
        this.expectedLinkId = expectedLinkId;
        this.matcher = matcher;
        this.future = future;
        this.responseType = responseType;
    }

    boolean matches(MavlinkPacketView packet)
    {
        if (messageId != packet.getMessageId())
        {
            return false;
        }
        if (matcher != null)
        {
            return matcher.matches(packet, this);
        }
        return defaultMatch(packet);
    }

    private boolean defaultMatch(MavlinkPacketView packet)
    {
        if (expectedSysId >= 0 && packet.getSysId() != expectedSysId)
        {
            return false;
        }
        if (expectedCompId >= 0 && packet.getCompId() != expectedCompId)
        {
            return false;
        }
        if (expectedLinkId >= 0)
        {
            int linkId = packet.hasSignature() ? packet.getSignatureLinkId() : -1;
            if (linkId != expectedLinkId)
            {
                return false;
            }
        }
        return true;
    }

    void completeWithCopy(MavlinkPacketView packet)
    {
        if (future.isDone())
        {
            return;
        }

        ByteBuffer copy = copyPacket(packet);
        try
        {
            MavlinkView view = responseType.getDeclaredConstructor().newInstance();
            MavlinkPacketView viewPacket = new MavlinkPacketView();
            viewPacket.wrap(copy, 0);
            view.wrap(viewPacket);
            @SuppressWarnings("unchecked")
            CompletableFuture<MavlinkView> f = (CompletableFuture<MavlinkView>) future;
            f.complete(view);
        } catch (Exception e)
        {
            future.completeExceptionally(e);
        }
    }

    private static ByteBuffer copyPacket(MavlinkPacketView packet)
    {
        int headerLen = packet.isV2() ? MavlinkPacketView.HEADER_LEN_V2 : MavlinkPacketView.HEADER_LEN_V1;
        int total = headerLen + packet.getPayloadLength() + 2 + packet.getSignatureLength();
        int start = packet.getPayloadOffset() - headerLen;

        ByteBuffer src = packet.getBuffer();
        ByteBuffer dst = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < total; i++)
        {
            dst.put(src.get(start + i));
        }
        dst.flip();
        return dst;
    }
}
