package com.chulise.mavlink.quarkus;

@FunctionalInterface
interface MavlinkHandlerInvoker
{
    void invoke(Object arg);
}
