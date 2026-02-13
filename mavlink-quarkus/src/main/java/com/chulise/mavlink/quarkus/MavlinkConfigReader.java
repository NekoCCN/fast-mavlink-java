package com.chulise.mavlink.quarkus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;

final class MavlinkConfigReader
{
    private static final String P_ROOT = "mavlink.";
    private static final String P_LISTENERS = "mavlink.listeners";
    private static final String P_LISTENER_PREFIX = "mavlink.listener.";

    private MavlinkConfigReader()
    {
    }

    static List<MavlinkListenerConfig> readListeners(Config config)
    {
        List<MavlinkListenerConfig> list = readListenersFromList(config);
        if (!list.isEmpty())
        {
            return list;
        }

        List<String> ids = readListenerIds(config);
        if (ids.isEmpty())
        {
            return Collections.emptyList();
        }

        List<MavlinkListenerConfig> listeners = new ArrayList<>(ids.size());
        for (String id : ids)
        {
            String prefix = P_LISTENER_PREFIX + id + ".";
            String transport = MavlinkConfigUtil.getString(config, prefix + "transport").orElse("udp");
            String bind = MavlinkConfigUtil.getString(config, prefix + "bind").orElse(null);
            String remote = MavlinkConfigUtil.getString(config, prefix + "remote").orElse(null);
            String dialect = MavlinkConfigUtil.getString(config, prefix + "dialect").orElse(null);

            MavlinkParserConfig parser = readParser(config, prefix + "parser.");
            MavlinkWriterConfig writer = readWriter(config, prefix + "writer.");
            MavlinkRequestConfig request = readRequest(config, prefix + "request.");

            listeners.add(new MavlinkListenerConfig(id, transport, bind, remote, dialect, parser, writer, request));
        }
        return listeners;
    }

    private static List<String> readListenerIds(Config config)
    {
        Optional<String> raw = config.getOptionalValue(P_LISTENERS, String.class);
        if (raw.isEmpty())
        {
            raw = config.getOptionalValue(P_ROOT + "listener-ids", String.class);
        }
        if (raw.isEmpty())
        {
            return Collections.emptyList();
        }

        String[] parts = raw.get().split(",");
        List<String> ids = new ArrayList<>(parts.length);
        for (String p : parts)
        {
            String v = p.trim();
            if (!v.isEmpty())
            {
                ids.add(v);
            }
        }
        return ids;
    }

    private static MavlinkParserConfig readParser(Config config, String prefix)
    {
        Boolean strict = MavlinkConfigUtil.getBoolean(config, prefix + "strict").orElse(null);
        Boolean allowUnknown = MavlinkConfigUtil.getBoolean(config, prefix + "allow-unknown").orElse(null);
        Boolean requireSignature = MavlinkConfigUtil.getBoolean(config, prefix + "require-signature").orElse(null);
        Boolean requireSigned = MavlinkConfigUtil.getBoolean(config, prefix + "require-signed").orElse(null);
        Long signatureWindow = MavlinkConfigUtil.getLong(config, prefix + "signature-window").orElse(null);
        Integer capacity = MavlinkConfigUtil.getInt(config, prefix + "signature-map-capacity").orElse(null);
        Float loadFactor = MavlinkConfigUtil.getFloat(config, prefix + "signature-map-load-factor").orElse(null);
        byte[] secretKey = MavlinkConfigUtil.getString(config, prefix + "secret-key")
                .map(MavlinkConfigUtil::parseSecretKey)
                .orElse(null);

        return new MavlinkParserConfig(strict, allowUnknown, requireSignature, requireSigned,
                signatureWindow, capacity, loadFactor, secretKey);
    }

    private static MavlinkWriterConfig readWriter(Config config, String prefix)
    {
        Integer sysId = MavlinkConfigUtil.getInt(config, prefix + "sys-id").orElse(null);
        Integer compId = MavlinkConfigUtil.getInt(config, prefix + "comp-id").orElse(null);
        Integer compatFlags = MavlinkConfigUtil.getInt(config, prefix + "compat-flags").orElse(null);
        Integer incompatFlags = MavlinkConfigUtil.getInt(config, prefix + "incompat-flags").orElse(null);
        Boolean trim = MavlinkConfigUtil.getBoolean(config, prefix + "trim-extension-zeros").orElse(null);
        Integer linkId = MavlinkConfigUtil.getInt(config, prefix + "link-id").orElse(null);
        byte[] secretKey = MavlinkConfigUtil.getString(config, prefix + "secret-key")
                .map(MavlinkConfigUtil::parseSecretKey)
                .orElse(null);

        return new MavlinkWriterConfig(sysId, compId, compatFlags, incompatFlags, trim, linkId, secretKey);
    }

    private static MavlinkRequestConfig readRequest(Config config, String prefix)
    {
        String defaultMatch = MavlinkConfigUtil.getString(config, prefix + "default-match").orElse(null);
        Long timeout = MavlinkConfigUtil.getLong(config, prefix + "timeout-ms").orElse(null);
        Integer maxPending = MavlinkConfigUtil.getInt(config, prefix + "max-pending").orElse(null);

        Integer expectedSysId = MavlinkConfigUtil.getInt(config, prefix + "expected-sys-id").orElse(null);
        Integer expectedCompId = MavlinkConfigUtil.getInt(config, prefix + "expected-comp-id").orElse(null);
        Integer expectedLinkId = MavlinkConfigUtil.getInt(config, prefix + "expected-link-id").orElse(null);

        return new MavlinkRequestConfig(defaultMatch, timeout, maxPending,
                expectedSysId, expectedCompId, expectedLinkId);
    }

    private static List<MavlinkListenerConfig> readListenersFromList(Config config)
    {
        List<Integer> indices = new ArrayList<>();
        for (String name : config.getPropertyNames())
        {
            Integer idx = parseListIndex(name);
            if (idx != null && !indices.contains(idx))
            {
                indices.add(idx);
            }
        }
        if (indices.isEmpty())
        {
            return Collections.emptyList();
        }

        indices.sort(Integer::compareTo);
        List<MavlinkListenerConfig> listeners = new ArrayList<>(indices.size());
        for (int idx : indices)
        {
            String prefix = P_ROOT + "listeners[" + idx + "].";
            String id = MavlinkConfigUtil.getString(config, prefix + "id").orElse(String.valueOf(idx));
            String transport = MavlinkConfigUtil.getString(config, prefix + "transport").orElse("udp");
            String bind = MavlinkConfigUtil.getString(config, prefix + "bind").orElse(null);
            String remote = MavlinkConfigUtil.getString(config, prefix + "remote").orElse(null);
            String dialect = MavlinkConfigUtil.getString(config, prefix + "dialect").orElse(null);

            MavlinkParserConfig parser = readParser(config, prefix + "parser.");
            MavlinkWriterConfig writer = readWriter(config, prefix + "writer.");
            MavlinkRequestConfig request = readRequest(config, prefix + "request.");

            listeners.add(new MavlinkListenerConfig(id, transport, bind, remote, dialect, parser, writer, request));
        }
        return listeners;
    }

    private static Integer parseListIndex(String name)
    {
        String prefix = P_ROOT + "listeners[";
        if (!name.startsWith(prefix))
        {
            return null;
        }
        int end = name.indexOf(']');
        if (end < 0 || end <= prefix.length())
        {
            return null;
        }
        String idx = name.substring(prefix.length(), end);
        try
        {
            return Integer.parseInt(idx);
        } catch (NumberFormatException e)
        {
            return null;
        }
    }
}
