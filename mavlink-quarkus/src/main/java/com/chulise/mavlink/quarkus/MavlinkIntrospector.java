package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkView;
import java.lang.reflect.Field;

final class MavlinkIntrospector
{
    private MavlinkIntrospector()
    {
    }

    static int messageId(Class<? extends MavlinkView> viewType)
    {
        try
        {
            Field id = viewType.getField("ID");
            return id.getInt(null);
        } catch (NoSuchFieldException e)
        {
            throw new IllegalStateException("missing ID field on " + viewType.getName(), e);
        } catch (IllegalAccessException e)
        {
            throw new IllegalStateException("unable to read ID field on " + viewType.getName(), e);
        }
    }
}
