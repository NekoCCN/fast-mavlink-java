package com.chulise.mavlink.core;

public interface MavlinkDialect
{
    MavlinkView resolve(int messageId);

    boolean supports(int messageId);
}