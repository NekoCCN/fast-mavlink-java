package com.chulise.mavlink.quarkus;

public final class MavlinkRequestOptions
{
    final long timeoutMs;
    final int expectedSysId;
    final int expectedCompId;
    final int expectedLinkId;
    final MavlinkResponseMatcher matcher;

    private MavlinkRequestOptions(Builder builder)
    {
        this.timeoutMs = builder.timeoutMs;
        this.expectedSysId = builder.expectedSysId;
        this.expectedCompId = builder.expectedCompId;
        this.expectedLinkId = builder.expectedLinkId;
        this.matcher = builder.matcher;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private long timeoutMs = 1000;
        private int expectedSysId = -1;
        private int expectedCompId = -1;
        private int expectedLinkId = -1;
        private MavlinkResponseMatcher matcher;

        public Builder timeoutMs(long value)
        {
            this.timeoutMs = Math.max(0, value);
            return this;
        }

        public Builder expectedSysId(int value)
        {
            this.expectedSysId = value;
            return this;
        }

        public Builder expectedCompId(int value)
        {
            this.expectedCompId = value;
            return this;
        }

        public Builder expectedLinkId(int value)
        {
            this.expectedLinkId = value;
            return this;
        }

        public Builder matcher(MavlinkResponseMatcher value)
        {
            this.matcher = value;
            return this;
        }

        public MavlinkRequestOptions build()
        {
            return new MavlinkRequestOptions(this);
        }
    }
}
