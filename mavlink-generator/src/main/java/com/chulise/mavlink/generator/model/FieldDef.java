package com.chulise.mavlink.generator.model;

public record FieldDef(
        String type,
        String name,
        String description,
        boolean isExtension,
        int arrayLength
)
{
    public boolean isArray()
    {
        return arrayLength > 1;
    }

    public String baseType()
    {
        return type.split("\\[")[0];
    }
}