package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketView;
import com.chulise.mavlink.core.MavlinkView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MavlinkDispatcher
{
    private final Map<Integer, MavlinkHandlerGroup> handlers = new HashMap<>();
    private final List<MavlinkHandlerInvoker> rawHandlers = new ArrayList<>();
    private final MavlinkRequestManager requestManager;

    MavlinkDispatcher(MavlinkRequestManager requestManager)
    {
        this.requestManager = requestManager;
    }

    public void addHandler(int messageId,
                           Class<? extends MavlinkView> viewType,
                           ThreadLocal<? extends MavlinkView> pool,
                           MavlinkHandlerInvoker handler)
    {
        MavlinkHandlerGroup group = handlers.get(messageId);
        if (group == null)
        {
            MavlinkHandlerGroup created = new MavlinkHandlerGroup(viewType, pool);
            created.add(handler);
            handlers.put(messageId, created);
            return;
        }
        if (!group.viewType().equals(viewType))
        {
            throw new IllegalStateException("messageId " + messageId + " already registered for " + group.viewType().getName());
        }
        group.add(handler);
    }

    public void registerRaw(MavlinkHandlerInvoker handler)
    {
        rawHandlers.add(handler);
    }

    void dispatch(MavlinkPacketView packet)
    {
        MavlinkHandlerGroup group = handlers.get(packet.getMessageId());
        if (group != null)
        {
            MavlinkView view = group.view();
            view.wrap(packet);
            requestManager.onPacket(packet);
            group.dispatch(view);
        } else
        {
            requestManager.onPacket(packet);
        }

        if (!rawHandlers.isEmpty())
        {
            for (MavlinkHandlerInvoker handler : rawHandlers)
            {
                handler.invoke(packet);
            }
        }
    }
}
