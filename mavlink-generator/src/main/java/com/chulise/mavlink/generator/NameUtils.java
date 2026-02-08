package com.chulise.mavlink.generator;

public class NameUtils
{
    // HEARBEAT -> HeartbeatView
    public static String toClassName(String mavlinkName)
    {
        return toPascalCase(mavlinkName) + "View";
    }

    // custom_mode -> customMode
    public static String toCamelCase(String s)
    {
        String pascal = toPascalCase(s);
        return pascal.substring(0, 1).toLowerCase() + pascal.substring(1);
    }

    // snake_case -> PascalCase
    private static String toPascalCase(String s)
    {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toLowerCase().toCharArray())
        {
            if (c == '_')
            {
                nextUpper = true;
            } else
            {
                if (nextUpper)
                {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else
                {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}