package com.chulise.mavlink.generator;

import java.util.Map;

public record MessageLayout(
        int id, String name, int lengthV1, int lengthV2, int crcExtra, Map<String, Integer> offsets
)
{  }