package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkView;
import java.util.ArrayList;
import java.util.List;

final class MavlinkHandlerGroup
{
    private final Class<? extends MavlinkView> viewType;
    private final ThreadLocal<? extends MavlinkView> viewPool;
    private final List<MavlinkHandlerInvoker> handlers = new ArrayList<>();

    MavlinkHandlerGroup(Class<? extends MavlinkView> viewType, ThreadLocal<? extends MavlinkView> viewPool)
    {
        this.viewType = viewType;
        this.viewPool = viewPool;
    }

    Class<? extends MavlinkView> viewType()
    {
        return viewType;
    }

    void add(MavlinkHandlerInvoker handler)
    {
        handlers.add(handler);
    }

    MavlinkView view()
    {
        return viewPool.get();
    }

    void dispatch(MavlinkView view)
    {
        for (MavlinkHandlerInvoker handler : handlers)
        {
            handler.invoke(view);
        }
    }
}
