package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkDialect;

final class MavlinkDialectResolver
{
    private MavlinkDialectResolver()
    {
    }

    static MavlinkDialect resolve(String name)
    {
        if (name == null || name.isBlank())
        {
            return null;
        }

        String v = name.trim();
        if ("common".equalsIgnoreCase(v))
        {
            return new com.chulise.mavlink.messages.CommonDialect();
        }

        try
        {
            Class<?> type = Class.forName(v);
            if (!MavlinkDialect.class.isAssignableFrom(type))
            {
                throw new IllegalArgumentException("dialect must implement MavlinkDialect: " + v);
            }
            return (MavlinkDialect) type.getDeclaredConstructor().newInstance();
        } catch (Exception e)
        {
            throw new IllegalStateException("cannot create dialect: " + v, e);
        }
    }
}
