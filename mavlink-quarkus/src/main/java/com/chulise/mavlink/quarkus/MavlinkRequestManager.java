package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class MavlinkRequestManager
{
    private final Map<Integer, List<PendingRequest>> pending = new ConcurrentHashMap<>();
    private final long defaultTimeoutMs;
    private final int maxPending;
    private final MavlinkResponseMatcher defaultMatcher;
    private volatile long lastSweepTime;

    MavlinkRequestManager(long defaultTimeoutMs, int maxPending, MavlinkResponseMatcher defaultMatcher)
    {
        this.defaultTimeoutMs = Math.max(0, defaultTimeoutMs);
        this.maxPending = Math.max(1, maxPending);
        this.defaultMatcher = defaultMatcher;
    }

    <T> CompletableFuture<T> register(int messageId,
                                      Class<? extends com.chulise.mavlink.core.MavlinkView> responseType,
                                      MavlinkRequestOptions options)
    {
        MavlinkRequestOptions opts = options == null ? MavlinkRequestOptions.builder().build() : options;
        long timeoutMs = opts.timeoutMs > 0 ? opts.timeoutMs : defaultTimeoutMs;
        long expireAt = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;

        MavlinkResponseMatcher matcher = opts.matcher != null ? opts.matcher : defaultMatcher;
        CompletableFuture<T> future = new CompletableFuture<>();

        PendingRequest req = new PendingRequest(messageId,
                expireAt,
                opts.expectedSysId,
                opts.expectedCompId,
                opts.expectedLinkId,
                matcher,
                future,
                responseType);

        List<PendingRequest> list = pending.computeIfAbsent(messageId, k -> new ArrayList<>());
        synchronized (list)
        {
            if (list.size() >= maxPending)
            {
                future.completeExceptionally(new IllegalStateException("too many pending requests"));
                return future;
            }
            list.add(req);
        }
        return future;
    }

    void onPacket(MavlinkPacketView packet)
    {
        List<PendingRequest> list = pending.get(packet.getMessageId());
        if (list == null)
        {
            maybeSweep();
            return;
        }

        PendingRequest matched = null;
        synchronized (list)
        {
            Iterator<PendingRequest> it = list.iterator();
            while (it.hasNext())
            {
                PendingRequest req = it.next();
                if (req.matches(packet))
                {
                    matched = req;
                    it.remove();
                    break;
                }
            }
        }

        if (matched != null)
        {
            matched.completeWithCopy(packet);
        }

        maybeSweep();
    }

    private void maybeSweep()
    {
        long now = System.currentTimeMillis();
        if (now - lastSweepTime < 500)
        {
            return;
        }
        lastSweepTime = now;
        sweepExpired(now);
    }

    private void sweepExpired(long now)
    {
        for (Map.Entry<Integer, List<PendingRequest>> entry : pending.entrySet())
        {
            List<PendingRequest> list = entry.getValue();
            synchronized (list)
            {
                Iterator<PendingRequest> it = list.iterator();
                while (it.hasNext())
                {
                    PendingRequest req = it.next();
                    if (req.expireAt <= now)
                    {
                        req.future.completeExceptionally(new IllegalStateException("request timeout"));
                        it.remove();
                    }
                }
                if (list.isEmpty())
                {
                    pending.remove(entry.getKey(), list);
                }
            }
        }
    }
}
