package com.chulise.mavlink.core;

public interface MessageSpecProvider
{
    MessageSpec get(int messageId);
}
