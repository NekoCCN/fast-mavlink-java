package com.chulise.mavlink.quarkus;

import java.util.List;

public final class MavlinkRegistrarContext
{
    private final List<MavlinkListenerRuntime> runtimes;

    MavlinkRegistrarContext(List<MavlinkListenerRuntime> runtimes)
    {
        this.runtimes = runtimes;
    }

    public MavlinkDispatcher dispatcher(String listenerId)
    {
        for (MavlinkListenerRuntime runtime : runtimes)
        {
            if (runtime.id().equals(listenerId))
            {
                return runtime.dispatcher();
            }
        }
        return null;
    }
}
