package com.chulise.mavlink.quarkus;

import java.lang.invoke.MethodHandle;

final class MavlinkHandlerInvokers
{
    private MavlinkHandlerInvokers()
    {
    }

    static MavlinkHandlerInvoker from(MethodHandle handle)
    {
        return arg ->
        {
            try
            {
                handle.invoke(arg);
            } catch (Throwable t)
            {
                throw new IllegalStateException("handler invocation failed", t);
            }
        };
    }
}
