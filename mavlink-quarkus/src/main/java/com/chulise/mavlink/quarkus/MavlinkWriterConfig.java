package com.chulise.mavlink.quarkus;

final class MavlinkWriterConfig
{
    final Integer sysId;
    final Integer compId;
    final Integer compatFlags;
    final Integer incompatFlags;
    final Boolean trimExtensionZeros;
    final Integer linkId;
    final byte[] secretKey;

    MavlinkWriterConfig(Integer sysId,
                        Integer compId,
                        Integer compatFlags,
                        Integer incompatFlags,
                        Boolean trimExtensionZeros,
                        Integer linkId,
                        byte[] secretKey)
    {
        this.sysId = sysId;
        this.compId = compId;
        this.compatFlags = compatFlags;
        this.incompatFlags = incompatFlags;
        this.trimExtensionZeros = trimExtensionZeros;
        this.linkId = linkId;
        this.secretKey = secretKey;
    }
}
