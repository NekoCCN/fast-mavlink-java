package com.chulise.mavlink.quarkus;

final class MavlinkRequestConfig
{
    final String defaultMatch;
    final Long timeoutMs;
    final Integer maxPending;
    final Integer expectedSysId;
    final Integer expectedCompId;
    final Integer expectedLinkId;

    MavlinkRequestConfig(String defaultMatch,
                         Long timeoutMs,
                         Integer maxPending,
                         Integer expectedSysId,
                         Integer expectedCompId,
                         Integer expectedLinkId)
    {
        this.defaultMatch = defaultMatch;
        this.timeoutMs = timeoutMs;
        this.maxPending = maxPending;
        this.expectedSysId = expectedSysId;
        this.expectedCompId = expectedCompId;
        this.expectedLinkId = expectedLinkId;
    }
}
