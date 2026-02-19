package com.chulise.mavlink.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class MavlinkQuarkusProducer
{
    @Inject
    MavlinkClientRegistry registry;

    @Inject
    MavlinkQuarkusRuntime runtime;

    @Produces
    public MavlinkClient defaultMavlinkClient()
    {
        runtime.ensureStarted();
        Map<String, MavlinkClient> all = registry.all();
        if (all.isEmpty())
        {
            throw new IllegalStateException("No MAVLink listeners configured.");
        }
        if (all.size() == 1)
        {
            return all.values().iterator().next();
        }
        Config config = ConfigProvider.getConfig();
        String id = config.getOptionalValue("mavlink.client.default", String.class).orElse(null);
        if (id != null && !id.isBlank())
        {
            MavlinkClient client = registry.get(id.trim());
            if (client != null)
            {
                return client;
            }
        }
        throw new IllegalStateException("Multiple MAVLink listeners configured; use @MavlinkClientId or set mavlink.client.default.");
    }

    @Produces
    @MavlinkClientId
    public MavlinkClient mavlinkClient(InjectionPoint injectionPoint)
    {
        runtime.ensureStarted();
        MavlinkClientId qualifier = injectionPoint.getAnnotated().getAnnotation(MavlinkClientId.class);
        String id = qualifier == null ? "" : qualifier.value();
        MavlinkClient client = registry.get(id);
        if (client == null)
        {
            throw new IllegalStateException("Mavlink client not found: " + id);
        }
        return client;
    }
}
