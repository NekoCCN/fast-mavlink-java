package com.chulise.mavlink.quarkus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;

final class MavlinkConfigUtil
{
    private MavlinkConfigUtil()
    {
    }

    static Optional<Boolean> getBoolean(Config config, String key)
    {
        return config.getOptionalValue(key, Boolean.class);
    }

    static Optional<Integer> getInt(Config config, String key)
    {
        return config.getOptionalValue(key, Integer.class);
    }

    static Optional<Long> getLong(Config config, String key)
    {
        return config.getOptionalValue(key, Long.class);
    }

    static Optional<Float> getFloat(Config config, String key)
    {
        return config.getOptionalValue(key, Float.class);
    }

    static Optional<String> getString(Config config, String key)
    {
        return config.getOptionalValue(key, String.class);
    }

    static byte[] parseSecretKey(String value)
    {
        if (value == null)
        {
            return null;
        }

        String v = value.trim();
        if (v.isEmpty())
        {
            return null;
        }

        if (v.regionMatches(true, 0, "hex:", 0, 4))
        {
            return hexToBytes(v.substring(4).trim());
        }

        if (v.regionMatches(true, 0, "base64:", 0, 7))
        {
            return Base64.getDecoder().decode(v.substring(7).trim());
        }

        return v.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hexToBytes(String hex)
    {
        String v = hex.replace(" ", "");
        if ((v.length() & 1) != 0)
        {
            throw new IllegalArgumentException("hex key must have even length");
        }

        int len = v.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++)
        {
            int hi = hexDigit(v.charAt(i * 2));
            int lo = hexDigit(v.charAt(i * 2 + 1));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int hexDigit(char c)
    {
        if (c >= '0' && c <= '9')
        {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f')
        {
            return 10 + (c - 'a');
        }
        if (c >= 'A' && c <= 'F')
        {
            return 10 + (c - 'A');
        }
        throw new IllegalArgumentException("invalid hex digit: " + c);
    }
}
