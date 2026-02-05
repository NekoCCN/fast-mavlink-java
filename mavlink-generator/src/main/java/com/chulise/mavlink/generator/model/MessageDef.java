package com.chulise.mavlink.generator.model;

import java.util.List;

public record MessageDef(
        int id,
        String name,
        String description,
        List<FieldDef> fields
) {}