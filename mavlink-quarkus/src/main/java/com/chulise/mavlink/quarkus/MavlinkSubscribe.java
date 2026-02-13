package com.chulise.mavlink.quarkus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MavlinkSubscribe
{
    Class<?> value() default Void.class;

    int messageId() default -1;

    boolean raw() default false;
}
