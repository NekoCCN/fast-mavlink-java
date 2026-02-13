package com.chulise.mavlink.quarkus;

final class MavlinkParserConfig
{
    final Boolean strict;
    final Boolean allowUnknown;
    final Boolean requireSignature;
    final Boolean requireSigned;
    final Long signatureWindow;
    final Integer signatureMapCapacity;
    final Float signatureMapLoadFactor;
    final byte[] secretKey;

    MavlinkParserConfig(Boolean strict,
                        Boolean allowUnknown,
                        Boolean requireSignature,
                        Boolean requireSigned,
                        Long signatureWindow,
                        Integer signatureMapCapacity,
                        Float signatureMapLoadFactor,
                        byte[] secretKey)
    {
        this.strict = strict;
        this.allowUnknown = allowUnknown;
        this.requireSignature = requireSignature;
        this.requireSigned = requireSigned;
        this.signatureWindow = signatureWindow;
        this.signatureMapCapacity = signatureMapCapacity;
        this.signatureMapLoadFactor = signatureMapLoadFactor;
        this.secretKey = secretKey;
    }
}
