package com.chulise.mavlink.quarkus;

@FunctionalInterface
public interface MavlinkHandlerInvoker
{
    void invoke(Object arg);
}
