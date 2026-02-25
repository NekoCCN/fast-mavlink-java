package com.chulise.mavlink.generator;

import com.chulise.mavlink.core.MavlinkDialect;
import com.chulise.mavlink.core.MavlinkPacketView;
import com.chulise.mavlink.core.MavlinkView;
import com.chulise.mavlink.generator.model.MessageDef;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DialectGenerator
{
    private final Path outputDir;

    public DialectGenerator(Path outputDir)
    {
        this.outputDir = outputDir;
    }

    public void generate(String dialectName, List<MessageDef> messages, Map<Integer, String> classNamesById)
    {
        String baseName = toPascalCase(dialectName);
        String className = baseName + "Dialect";
        String packageName = "com.chulise.mavlink.messages";

        ClassName visitorType = generateVisitorInterface(packageName, baseName, messages, classNamesById);
        ClassName poolType = ClassName.get(packageName, className, "ViewPool");

        MethodSpec.Builder resolveMethod = MethodSpec.methodBuilder("resolve")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(MavlinkView.class)
                .addParameter(int.class, "messageId");

        resolveMethod.beginControlFlow("switch (messageId)");

        Set<Integer> seenResolveIds = new HashSet<>();
        for (MessageDef msg : messages)
        {
            if (!seenResolveIds.add(msg.id()))
            {
                continue;
            }
            ClassName viewClass = ClassName.get(packageName, classNamesById.get(msg.id()));

            resolveMethod.addStatement("case $L: return new $T()", msg.id(), viewClass);
        }

        resolveMethod.addStatement("default: return null");
        resolveMethod.endControlFlow();

        MethodSpec.Builder acceptNewMethod = MethodSpec.methodBuilder("acceptNew")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Allocates a new view instance per call.\n")
                .addParameter(MavlinkPacketView.class, "packet")
                .addParameter(visitorType, "visitor")
                .addStatement("int messageId = packet.getMessageId()");

        acceptNewMethod.beginControlFlow("switch (messageId)");

        Set<Integer> seenAcceptNewIds = new HashSet<>();
        for (MessageDef msg : messages)
        {
            if (!seenAcceptNewIds.add(msg.id()))
            {
                continue;
            }
            ClassName viewClass = ClassName.get(packageName, classNamesById.get(msg.id()));
            acceptNewMethod.addCode("case $L: {\n", msg.id());
            acceptNewMethod.addStatement("    $T v = new $T()", viewClass, viewClass);
            acceptNewMethod.addStatement("    v.wrap(packet)");
            acceptNewMethod.addStatement("    visitor.visit(v)");
            acceptNewMethod.addStatement("    break");
            acceptNewMethod.addCode("}\n");
        }

        acceptNewMethod.addCode("default: break;\n");
        acceptNewMethod.endControlFlow();

        MethodSpec acceptMethod = MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(MavlinkPacketView.class, "packet")
                .addParameter(visitorType, "visitor")
                .addStatement("acceptNew(packet, visitor)")
                .build();

        MethodSpec.Builder acceptFastMethod = MethodSpec.methodBuilder("acceptFast")
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Fast path: reuses per-thread view instances. Do not retain view references beyond this call.\n"
                        + "Thread-confined; do not share across threads or cache.\n")
                .addParameter(MavlinkPacketView.class, "packet")
                .addParameter(visitorType, "visitor")
                .addStatement("$T pool = VIEW_POOL.get()", poolType)
                .addStatement("int messageId = packet.getMessageId()");

        acceptFastMethod.beginControlFlow("switch (messageId)");

        Set<Integer> seenAcceptFastIds = new HashSet<>();
        for (MessageDef msg : messages)
        {
            if (!seenAcceptFastIds.add(msg.id()))
            {
                continue;
            }
            ClassName viewClass = ClassName.get(packageName, classNamesById.get(msg.id()));
            String fieldName = "v" + msg.id();
            acceptFastMethod.addCode("case $L: {\n", msg.id());
            acceptFastMethod.addStatement("    $T v = pool.$N", viewClass, fieldName);
            acceptFastMethod.addStatement("    v.wrap(packet)");
            acceptFastMethod.addStatement("    visitor.visit(v)");
            acceptFastMethod.addStatement("    break");
            acceptFastMethod.addCode("}\n");
        }

        acceptFastMethod.addCode("default: break;\n");
        acceptFastMethod.endControlFlow();

        MethodSpec.Builder supportsMethod = MethodSpec.methodBuilder("supports")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(boolean.class)
                .addParameter(int.class, "messageId")
                .addStatement("return resolve(messageId) != null");

        FieldSpec viewPoolField = FieldSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(ThreadLocal.class), poolType),
                        "VIEW_POOL",
                        Modifier.PRIVATE,
                        Modifier.STATIC,
                        Modifier.FINAL)
                .initializer("$T.withInitial($T::new)", ThreadLocal.class, poolType)
                .build();

        TypeSpec viewPoolType = buildViewPoolType(messages, packageName, classNamesById);

        TypeSpec dialectClass = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(MavlinkDialect.class)
                .addJavadoc("Generated Dialect Registry for: $L\n", dialectName)
                .addField(viewPoolField)
                .addType(viewPoolType)
                .addMethod(resolveMethod.build())
                .addMethod(acceptMethod)
                .addMethod(acceptNewMethod.build())
                .addMethod(acceptFastMethod.build())
                .addMethod(supportsMethod.build())
                .build();

        try
        {
            JavaFile.builder(packageName, dialectClass)
                    .indent("    ")
                    .build()
                    .writeTo(outputDir);
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to generate dialect " + className, e);
        }
    }

    private TypeSpec buildViewPoolType(List<MessageDef> messages, String packageName, Map<Integer, String> classNamesById)
    {
        TypeSpec.Builder poolBuilder = TypeSpec.classBuilder("ViewPool")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        Set<Integer> seenIds = new HashSet<>();
        for (MessageDef msg : messages)
        {
            if (!seenIds.add(msg.id()))
            {
                continue;
            }
            ClassName viewClass = ClassName.get(packageName, classNamesById.get(msg.id()));
            String fieldName = "v" + msg.id();
            poolBuilder.addField(FieldSpec.builder(viewClass, fieldName, Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T()", viewClass)
                    .build());
        }

        return poolBuilder.build();
    }

    private ClassName generateVisitorInterface(String packageName, String baseName, List<MessageDef> messages, Map<Integer, String> classNamesById)
    {
        String interfaceName = baseName + "Visitor";
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)
                .addModifiers(Modifier.PUBLIC);

        Set<Integer> seenIds = new HashSet<>();
        for (MessageDef msg : messages)
        {
            if (!seenIds.add(msg.id()))
            {
                continue;
            }
            ClassName viewClass = ClassName.get(packageName, classNamesById.get(msg.id()));
            interfaceBuilder.addMethod(MethodSpec.methodBuilder("visit")
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addParameter(viewClass, "msg")
                    .addCode("// no-op\n")
                    .build());
        }

        try
        {
            JavaFile.builder(packageName, interfaceBuilder.build())
                    .indent("    ")
                    .build()
                    .writeTo(outputDir);
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to generate visitor " + interfaceName, e);
        }

        return ClassName.get(packageName, interfaceName);
    }

    private String toPascalCase(String s)
    {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
