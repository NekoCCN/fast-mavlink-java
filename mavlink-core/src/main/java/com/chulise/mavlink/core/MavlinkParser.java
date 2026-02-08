package com.chulise.mavlink.core;

import java.nio.ByteBuffer;

public class MavlinkParser
{
    private final MavlinkPacketView packetView = new MavlinkPacketView();
    private final Options options;
    private final IntLongHashMap signatureTimestamps;
    private static final long TIMESTAMP_UNSET = -1L;

    public MavlinkParser()
    {
        this.options = Options.builder().build();
        this.signatureTimestamps = initSignatureMap(this.options);
    }

    public MavlinkParser(Options options)
    {
        this.options = (options == null) ? Options.builder().build() : options;
        this.signatureTimestamps = initSignatureMap(this.options);
    }

    public ParseResult next(ByteBuffer buffer, int startOffset)
    {
        return nextInternal(buffer, startOffset, null, options);
    }

    public ParseResult next(ByteBuffer buffer, int startOffset, MavlinkDialect dialect)
    {
        return nextInternal(buffer, startOffset, dialect, options);
    }

    public ParseResult nextStrict(ByteBuffer buffer, int startOffset, MavlinkDialect dialect)
    {
        Options strictOptions = Options.builder()
                .strict(true)
                .allowUnknown(false)
                .requireSignature(false)
                .requireSigned(false)
                .build();
        return nextInternal(buffer, startOffset, dialect, strictOptions);
    }

    public ParseResult nextStrict(ByteBuffer buffer, int startOffset, MavlinkDialect dialect, byte[] secretKey, boolean allowUnknown)
    {
        Options strictOptions = Options.builder()
                .strict(true)
                .allowUnknown(allowUnknown)
                .requireSignature(true)
                .requireSigned(false)
                .secretKey(secretKey)
                .build();
        return nextInternal(buffer, startOffset, dialect, strictOptions);
    }

    private ParseResult nextInternal(ByteBuffer buffer,
                                     int startOffset,
                                     MavlinkDialect dialect,
                                     Options options)
    {
        int limit = buffer.limit();
        int cursor = startOffset;

        while (cursor < limit)
        {
            int magic = buffer.get(cursor) & 0xFF;
            if (magic != MavlinkPacketView.MAGIC_V1 && magic != MavlinkPacketView.MAGIC_V2)
            {
                cursor++;
                continue;
            }

            int headerLen = (magic == MavlinkPacketView.MAGIC_V2) ? 10 : 6;
            if (cursor + headerLen > limit)
            {
                return null;
            }

            int payloadLen = buffer.get(cursor + 1) & 0xFF;

            int crcLen = 2;
            int signatureLen = 0;

            if (magic == MavlinkPacketView.MAGIC_V2)
            {
                int incompatFlags = buffer.get(cursor + 2);
                if ((incompatFlags & MavlinkPacketView.INCOMPAT_FLAG_SIGNED) != 0)
                {
                    signatureLen = MavlinkPacketView.SIGNATURE_LEN;
                }
            }

            int totalPacketLen = headerLen + payloadLen + crcLen + signatureLen;

            if (cursor + totalPacketLen > limit)
            {
                return null;
            }

            packetView.wrap(buffer, cursor);

            if (options.strict() && !validateStrict(packetView, dialect, options))
            {
                cursor++;
                continue;
            }

            return new ParseResult(packetView, totalPacketLen, cursor);
        }

        return null;
    }

    public record ParseResult(MavlinkPacketView view, int length, int startOffset)
    {
    }

    private boolean validateStrict(MavlinkPacketView packet,
                                   MavlinkDialect dialect,
                                   Options options)
    {
        if (packet.isV2())
        {
            int unknown = packet.getIncompatFlags() & ~MavlinkPacketView.KNOWN_INCOMPAT_FLAGS;
            if (unknown != 0)
            {
                return false;
            }
        }

        if (options.requireSigned() && packet.isV2() && !packet.hasSignature())
        {
            return false;
        }

        if (packet.hasSignature() && options.requireSignature())
        {
            byte[] secretKey = options.secretKey();
            if (secretKey == null || secretKey.length == 0)
            {
                return false;
            }
            if (!packet.validateSignature(secretKey))
            {
                return false;
            }

            if (options.signatureWindowEnabled())
            {
                if (!validateSignatureWindow(packet, options))
                {
                    return false;
                }
            }
        }

        if (dialect == null)
        {
            return options.allowUnknown();
        }

        MavlinkView view = dialect.resolve(packet.getMessageId());
        if (view == null)
        {
            return options.allowUnknown();
        }

        int payloadLen = packet.getPayloadLength();
        int lenV1 = view.getLengthV1();
        int lenV2 = view.getLengthV2();

        if (packet.isV2())
        {
            if (payloadLen < lenV1 || payloadLen > lenV2)
            {
                return false;
            }
        } else
        {
            if (payloadLen != lenV1)
            {
                return false;
            }
        }

        return packet.validateCrc(view.getCrcExtra());
    }

    private IntLongHashMap initSignatureMap(Options options)
    {
        if (!options.signatureWindowEnabled())
        {
            return null;
        }
        return new IntLongHashMap(options.signatureMapCapacity(), options.signatureMapLoadFactor());
    }

    private boolean validateSignatureWindow(MavlinkPacketView packet, Options options)
    {
        if (signatureTimestamps == null)
        {
            return true;
        }

        int linkId = packet.getSignatureLinkId();
        if (linkId < 0)
        {
            return false;
        }

        int key = (packet.getSysId() << 16) | (packet.getCompId() << 8) | linkId;
        long ts = packet.getSignatureTimestamp();
        if (ts < 0)
        {
            return false;
        }

        long last = signatureTimestamps.getOrDefault(key, TIMESTAMP_UNSET);
        if (last == TIMESTAMP_UNSET)
        {
            signatureTimestamps.put(key, ts);
            return true;
        }

        long window = options.signatureBackwardWindow();
        if (ts + window < last)
        {
            return false;
        }

        if (ts > last)
        {
            signatureTimestamps.put(key, ts);
        }

        return true;
    }

    public static final class Options
    {
        private final boolean strict;
        private final boolean allowUnknown;
        private final boolean requireSignature;
        private final boolean requireSigned;
        private final byte[] secretKey;
        private final boolean signatureWindowEnabled;
        private final long signatureBackwardWindow;
        private final int signatureMapCapacity;
        private final float signatureMapLoadFactor;

        private Options(Builder builder)
        {
            this.strict = builder.strict;
            this.allowUnknown = builder.allowUnknown;
            this.requireSignature = builder.requireSignature;
            this.requireSigned = builder.requireSigned;
            this.secretKey = builder.secretKey;
            this.signatureWindowEnabled = builder.signatureWindowEnabled;
            this.signatureBackwardWindow = builder.signatureBackwardWindow;
            this.signatureMapCapacity = builder.signatureMapCapacity;
            this.signatureMapLoadFactor = builder.signatureMapLoadFactor;
        }

        public static Builder builder()
        {
            return new Builder();
        }

        public boolean strict()
        {
            return strict;
        }

        public boolean allowUnknown()
        {
            return allowUnknown;
        }

        public boolean requireSignature()
        {
            return requireSignature;
        }

        public boolean requireSigned()
        {
            return requireSigned;
        }

        public byte[] secretKey()
        {
            return secretKey;
        }

        public boolean signatureWindowEnabled()
        {
            return signatureWindowEnabled;
        }

        public long signatureBackwardWindow()
        {
            return signatureBackwardWindow;
        }

        public int signatureMapCapacity()
        {
            return signatureMapCapacity;
        }

        public float signatureMapLoadFactor()
        {
            return signatureMapLoadFactor;
        }

        public static final class Builder
        {
            private boolean strict;
            private boolean allowUnknown;
            private boolean requireSignature;
            private boolean requireSigned;
            private byte[] secretKey;
            private boolean signatureWindowEnabled;
            private long signatureBackwardWindow;
            private int signatureMapCapacity = 1024;
            private float signatureMapLoadFactor = 0.5f;

            private Builder()
            {
            }

            public Builder strict(boolean value)
            {
                this.strict = value;
                return this;
            }

            public Builder allowUnknown(boolean value)
            {
                this.allowUnknown = value;
                return this;
            }

            public Builder requireSignature(boolean value)
            {
                this.requireSignature = value;
                return this;
            }

            public Builder requireSigned(boolean value)
            {
                this.requireSigned = value;
                return this;
            }

            public Builder secretKey(byte[] value)
            {
                this.secretKey = value;
                return this;
            }

            public Builder signatureWindow(long backwardWindow)
            {
                this.signatureWindowEnabled = true;
                this.signatureBackwardWindow = Math.max(0, backwardWindow);
                return this;
            }

            public Builder signatureMapCapacity(int capacity)
            {
                this.signatureMapCapacity = Math.max(2, capacity);
                return this;
            }

            public Builder signatureMapLoadFactor(float loadFactor)
            {
                this.signatureMapLoadFactor = loadFactor;
                return this;
            }

            public Options build()
            {
                return new Options(this);
            }
        }
    }
}
