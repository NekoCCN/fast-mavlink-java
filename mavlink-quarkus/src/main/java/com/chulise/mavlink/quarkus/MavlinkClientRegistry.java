package com.chulise.mavlink.quarkus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@jakarta.enterprise.context.ApplicationScoped
public class MavlinkClientRegistry
{
    private final Map<String, MavlinkClient> clients = new HashMap<>();

    void register(MavlinkClient client)
    {
        clients.put(client.id(), client);
    }

    public MavlinkClient get(String id)
    {
        return clients.get(id);
    }

    public Map<String, MavlinkClient> all()
    {
        return Collections.unmodifiableMap(clients);
    }
}
