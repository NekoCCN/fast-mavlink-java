package com.chulise.mavlink.generator;

import com.chulise.mavlink.generator.model.*;
import java.util.*;

public class LayoutEngine
{
    public MessageLayout calculate(MessageDef msg)
    {
        List<FieldDef> standard = new ArrayList<>();
        List<FieldDef> extension = new ArrayList<>();

        for (FieldDef f : msg.fields())
        {
            if (f.isExtension()) extension.add(f);
            else standard.add(f);
        }

        List<FieldDef> sortedStandard = new ArrayList<>(standard);
        sortedStandard.sort((a, b) ->
        {
            int sizeA = getBaseSize(a.baseType());
            int sizeB = getBaseSize(b.baseType());

            if (sizeA != sizeB)
            {
                return Integer.compare(sizeB, sizeA);
            }
            return 0;
        });

        Map<String, Integer> offsets = new LinkedHashMap<>();
        int cursor = 0;

        for (FieldDef f : sortedStandard)
        {
            offsets.put(f.name(), cursor);
            cursor += getBaseSize(f.baseType()) * f.arrayLength();
        }
        int lenV1 = cursor;

        for (FieldDef f : extension)
        {
            offsets.put(f.name(), cursor);
            cursor += getBaseSize(f.baseType()) * f.arrayLength();
        }
        int lenTotal = cursor;

        int crc = calculateCrcExtra(msg.name(), sortedStandard);

        return new MessageLayout(msg.id(), msg.name(), lenV1, lenTotal, crc, offsets);
    }

    private int calculateCrcExtra(String name, List<FieldDef> sortedFields)
    {
        CrcX25 crc = new CrcX25();

        crc.update(name + " ");

        for (FieldDef f : sortedFields)
        {
            String type = f.baseType();
            String fieldName = f.name();
            int arrayLen = f.arrayLength();

            if ("uint8_t_mavlink_version".equals(type))
            {
                type = "uint8_t";
            }

            crc.update(type + " ");
            crc.update(fieldName + " ");

            if (arrayLen > 1)
            {
                crc.update((char) arrayLen);
            }
        }

        int res = crc.get();
        return (res & 0xFF) ^ ((res >> 8) & 0xFF);
    }

    private int getBaseSize(String type)
    {
        if (type.contains("64") || type.equals("double"))
            return 8;

        if (type.contains("32") || type.equals("float"))
            return 4;

        if (type.contains("16"))
            return 2;
        return 1;
    }
}