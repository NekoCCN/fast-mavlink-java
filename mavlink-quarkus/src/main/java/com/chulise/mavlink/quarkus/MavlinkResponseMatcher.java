package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketView;

@FunctionalInterface
public interface MavlinkResponseMatcher
{
    boolean matches(MavlinkPacketView packet, PendingRequest request);
}
