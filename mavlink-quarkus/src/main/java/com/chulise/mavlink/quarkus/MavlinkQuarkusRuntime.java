package com.chulise.mavlink.quarkus;

import com.chulise.mavlink.core.MavlinkPacketWriter;
import com.chulise.mavlink.core.MavlinkParser;
import com.chulise.mavlink.core.MavlinkView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class MavlinkQuarkusRuntime
{
    @Inject
    BeanManager beanManager;

    @Inject
    Instance<MavlinkRegistrar> registrars;

    @Inject
    MavlinkClientRegistry registry;

    private final List<MavlinkListenerRuntime> runtimes = new ArrayList<>();

    @PostConstruct
    void init()
    {
        Config config = ConfigProvider.getConfig();
        List<MavlinkListenerConfig> listeners = MavlinkConfigReader.readListeners(config);
        if (listeners.isEmpty())
        {
            return;
        }

        for (MavlinkListenerConfig cfg : listeners)
        {
            MavlinkParser parser = buildParser(cfg.parser);
            MavlinkRequestManager requestManager = buildRequestManager(cfg.request);
            MavlinkDispatcher dispatcher = new MavlinkDispatcher(requestManager);
            MavlinkTransport transport = buildTransport(cfg);
            MavlinkPacketWriter.Encoder encoder = buildEncoder(cfg.writer);
            MavlinkRequestOptions defaultOptions = buildDefaultRequestOptions(cfg.request);
            com.chulise.mavlink.core.MavlinkDialect dialect = MavlinkDialectResolver.resolve(cfg.dialect);

            MavlinkListenerRuntime runtime = new MavlinkListenerRuntime(cfg.id,
                    parser,
                    dispatcher,
                    transport,
                    encoder,
                    requestManager,
                    defaultOptions,
                    dialect);
            runtimes.add(runtime);
            registry.register(runtime.client());
        }

        if (!registerGeneratedHandlers())
        {
            registerHandlers();
        }

        for (MavlinkListenerRuntime runtime : runtimes)
        {
            runtime.start();
        }
    }

    @PreDestroy
    void shutdown()
    {
        for (MavlinkListenerRuntime runtime : runtimes)
        {
            runtime.close();
        }
        runtimes.clear();
    }

    private void registerHandlers()
    {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class);
        for (Bean<?> bean : beans)
        {
            Class<?> beanClass = bean.getBeanClass();
            MavlinkListener listener = beanClass.getAnnotation(MavlinkListener.class);
            if (listener == null)
            {
                continue;
            }

            MavlinkListenerRuntime runtime = findRuntime(listener.value());
            if (runtime == null)
            {
                throw new IllegalStateException("listener id not found: " + listener.value());
            }

            CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
            Object instance = beanManager.getReference(bean, beanClass, ctx);

            for (Method method : beanClass.getDeclaredMethods())
            {
                MavlinkSubscribe sub = method.getAnnotation(MavlinkSubscribe.class);
                if (sub == null)
                {
                    continue;
                }
                registerMethod(runtime, instance, method, sub);
            }
        }
    }

    private boolean registerGeneratedHandlers()
    {
        if (registrars == null || registrars.isUnsatisfied())
        {
            return false;
        }

        MavlinkRegistrarContext context = new MavlinkRegistrarContext(runtimes);
        for (MavlinkRegistrar registrar : registrars)
        {
            registrar.register(context);
        }
        return true;
    }

    private void registerMethod(MavlinkListenerRuntime runtime, Object instance, Method method, MavlinkSubscribe sub)
    {
        if (method.getParameterCount() != 1)
        {
            throw new IllegalStateException("subscriber must have exactly one parameter: " + method);
        }

        Class<?> paramType = method.getParameterTypes()[0];
        boolean raw = sub.raw() || paramType.equals(com.chulise.mavlink.core.MavlinkPacketView.class);

        MavlinkHandlerInvoker invoker = MavlinkHandlerInvokers.from(unreflect(method, instance));

        if (raw)
        {
            runtime.dispatcher().registerRaw(invoker);
            return;
        }

        Class<?> viewType = resolveViewType(sub, paramType);
        if (!MavlinkView.class.isAssignableFrom(viewType))
        {
            throw new IllegalStateException("subscriber parameter must be MavlinkView: " + method);
        }
        @SuppressWarnings("unchecked")
        Class<? extends MavlinkView> typed = (Class<? extends MavlinkView>) viewType;

        int messageId = resolveMessageId(sub, typed);
        ThreadLocal<? extends MavlinkView> pool = ThreadLocal.withInitial(() -> newViewInstance(typed));

        runtime.dispatcher().addHandler(messageId, typed, pool, invoker);
    }

    private static java.lang.invoke.MethodHandle unreflect(Method method, Object instance)
    {
        try
        {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).bindTo(instance);
        } catch (IllegalAccessException e)
        {
            throw new IllegalStateException("cannot access method: " + method, e);
        }
    }

    private static Class<?> resolveViewType(MavlinkSubscribe sub, Class<?> paramType)
    {
        if (!sub.value().equals(Void.class))
        {
            return sub.value();
        }
        return paramType;
    }

    private static int resolveMessageId(MavlinkSubscribe sub, Class<? extends MavlinkView> viewType)
    {
        if (sub.messageId() >= 0)
        {
            return sub.messageId();
        }
        return MavlinkIntrospector.messageId(viewType);
    }

    private static MavlinkView newViewInstance(Class<? extends MavlinkView> viewType)
    {
        try
        {
            return viewType.getDeclaredConstructor().newInstance();
        } catch (Exception e)
        {
            throw new IllegalStateException("cannot create view: " + viewType.getName(), e);
        }
    }

    private static MavlinkParser buildParser(MavlinkParserConfig cfg)
    {
        MavlinkParser.Options.Builder builder = MavlinkParser.Options.builder();
        if (cfg != null)
        {
            if (cfg.strict != null) builder.strict(cfg.strict);
            if (cfg.allowUnknown != null) builder.allowUnknown(cfg.allowUnknown);
            if (cfg.requireSignature != null) builder.requireSignature(cfg.requireSignature);
            if (cfg.requireSigned != null) builder.requireSigned(cfg.requireSigned);
            if (cfg.signatureWindow != null) builder.signatureWindow(cfg.signatureWindow);
            if (cfg.signatureMapCapacity != null) builder.signatureMapCapacity(cfg.signatureMapCapacity);
            if (cfg.signatureMapLoadFactor != null) builder.signatureMapLoadFactor(cfg.signatureMapLoadFactor);
            if (cfg.secretKey != null) builder.secretKey(cfg.secretKey);
        }
        return new MavlinkParser(builder.build());
    }

    private static MavlinkPacketWriter.Encoder buildEncoder(MavlinkWriterConfig cfg)
    {
        MavlinkPacketWriter.Builder builder = MavlinkPacketWriter.builder();
        if (cfg != null)
        {
            if (cfg.sysId != null) builder.sysId(cfg.sysId);
            if (cfg.compId != null) builder.compId(cfg.compId);
            if (cfg.compatFlags != null) builder.compatFlags(cfg.compatFlags);
            if (cfg.incompatFlags != null) builder.incompatFlags(cfg.incompatFlags);
            if (cfg.trimExtensionZeros != null) builder.trimExtensionZeros(cfg.trimExtensionZeros);
            if (cfg.linkId != null) builder.linkId(cfg.linkId);
            if (cfg.secretKey != null) builder.secretKey(cfg.secretKey);
        }
        return builder.build();
    }

    private static MavlinkRequestManager buildRequestManager(MavlinkRequestConfig cfg)
    {
        long timeout = cfg != null && cfg.timeoutMs != null ? cfg.timeoutMs : 1000;
        int maxPending = cfg != null && cfg.maxPending != null ? cfg.maxPending : 1024;
        MavlinkResponseMatcher matcher = MavlinkResponseMatchers.fromConfig(cfg == null ? null : cfg.defaultMatch);
        return new MavlinkRequestManager(timeout, maxPending, matcher);
    }

    private static MavlinkRequestOptions buildDefaultRequestOptions(MavlinkRequestConfig cfg)
    {
        MavlinkRequestOptions.Builder builder = MavlinkRequestOptions.builder();
        if (cfg != null && cfg.timeoutMs != null)
        {
            builder.timeoutMs(cfg.timeoutMs);
        }
        if (cfg != null && cfg.expectedSysId != null)
        {
            builder.expectedSysId(cfg.expectedSysId);
        }
        if (cfg != null && cfg.expectedCompId != null)
        {
            builder.expectedCompId(cfg.expectedCompId);
        }
        if (cfg != null && cfg.expectedLinkId != null)
        {
            builder.expectedLinkId(cfg.expectedLinkId);
        }
        return builder.build();
    }

    private static MavlinkTransport buildTransport(MavlinkListenerConfig cfg)
    {
        InetSocketAddress bind = MavlinkSocketAddress.parse(cfg.bind);
        InetSocketAddress remote = MavlinkSocketAddress.parse(cfg.remote);
        String transport = cfg.transport == null ? "udp" : cfg.transport.trim().toLowerCase();
        if ("udp".equals(transport))
        {
            return new UdpTransport(bind, remote, 2048);
        }
        if ("tcp".equals(transport))
        {
            return new TcpTransport(bind, remote, 4096);
        }
        if ("tcp-server".equals(transport) || "tcp_server".equals(transport) || "tcpserver".equals(transport))
        {
            return new TcpServerTransport(bind, 4096);
        }
        throw new IllegalArgumentException("unsupported transport: " + cfg.transport);
    }

    private MavlinkListenerRuntime findRuntime(String id)
    {
        for (MavlinkListenerRuntime runtime : runtimes)
        {
            if (runtime.id().equals(id))
            {
                return runtime;
            }
        }
        return null;
    }
}
