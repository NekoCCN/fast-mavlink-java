package com.chulise.mavlink.core;

import java.util.Arrays;

final class IntLongHashMap
{
    private static final int EMPTY_KEY = -1;

    private int[] keys;
    private long[] values;
    private int mask;
    private int size;
    private int resizeAt;
    private final float loadFactor;

    IntLongHashMap(int capacity, float loadFactor)
    {
        if (loadFactor <= 0.0f || loadFactor >= 1.0f)
        {
            throw new IllegalArgumentException("loadFactor must be between 0 and 1");
        }
        int cap = nextPowerOfTwo(Math.max(2, capacity));
        this.loadFactor = loadFactor;
        this.keys = new int[cap];
        this.values = new long[cap];
        Arrays.fill(keys, EMPTY_KEY);
        this.mask = cap - 1;
        this.resizeAt = (int) (cap * loadFactor);
    }

    long getOrDefault(int key, long defaultValue)
    {
        int idx = mix(key) & mask;
        while (true)
        {
            int k = keys[idx];
            if (k == EMPTY_KEY)
            {
                return defaultValue;
            }
            if (k == key)
            {
                return values[idx];
            }
            idx = (idx + 1) & mask;
        }
    }

    void put(int key, long value)
    {
        int idx = mix(key) & mask;
        while (true)
        {
            int k = keys[idx];
            if (k == EMPTY_KEY)
            {
                keys[idx] = key;
                values[idx] = value;
                if (++size >= resizeAt)
                {
                    rehash(keys.length << 1);
                }
                return;
            }
            if (k == key)
            {
                values[idx] = value;
                return;
            }
            idx = (idx + 1) & mask;
        }
    }

    private void rehash(int newCapacity)
    {
        int cap = nextPowerOfTwo(newCapacity);
        int[] oldKeys = keys;
        long[] oldValues = values;

        keys = new int[cap];
        values = new long[cap];
        Arrays.fill(keys, EMPTY_KEY);
        mask = cap - 1;
        resizeAt = (int) (cap * loadFactor);
        size = 0;

        for (int i = 0; i < oldKeys.length; i++)
        {
            int k = oldKeys[i];
            if (k != EMPTY_KEY)
            {
                put(k, oldValues[i]);
            }
        }
    }

    private static int nextPowerOfTwo(int value)
    {
        int v = value - 1;
        v |= v >>> 1;
        v |= v >>> 2;
        v |= v >>> 4;
        v |= v >>> 8;
        v |= v >>> 16;
        return v + 1;
    }

    private static int mix(int x)
    {
        int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }
}
