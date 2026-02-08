package com.chulise.mavlink.generator;

import com.chulise.mavlink.core.MavlinkView;
import com.chulise.mavlink.generator.model.FieldDef;
import com.chulise.mavlink.generator.model.MessageDef;
import com.squareup.javapoet.*;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;

public class MessageGenerator
{
    private final Path outputDir;

    public MessageGenerator(Path outputDir)
    {
        this.outputDir = outputDir;
    }

    public void generate(MessageDef msg, MessageLayout layout)
    {
        String className = NameUtils.toClassName(msg.name());

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(MavlinkView.class)
                .addJavadoc("MAVLink Message: $L (ID: $L)\n$L\n", msg.name(), msg.id(), msg.description());

        classBuilder.addField(buildConst("ID", msg.id()));
        classBuilder.addField(buildConst("CRC", layout.crcExtra()));
        classBuilder.addField(buildConst("LENGTH_V1", layout.lengthV1()));
        classBuilder.addField(buildConst("LENGTH_V2", layout.lengthV2()));

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
                continue;

            int offsetVal = layout.offsets().get(field.name());
            String offsetName = "OFF_" + field.name().toUpperCase();

            // private static final int OFF_CUSTOM_MODE = 0;
            classBuilder.addField(FieldSpec.builder(int.class, offsetName)
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", offsetVal)
                    .build());

            addGetters(classBuilder, field, offsetName);
        }

        addOverrides(classBuilder);

        try
        {
            JavaFile.builder("com.chulise.mavlink.messages", classBuilder.build())
                    .indent("    ")
                    .build()
                    .writeTo(outputDir);
        } catch (IOException e)
        {
            throw new RuntimeException("Failed to generate " + className, e);
        }
    }

    private FieldSpec buildConst(String name, int val)
    {
        return FieldSpec.builder(int.class, name)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", val)
                .build();
    }

    private void addOverrides(TypeSpec.Builder builder)
    {
        builder.addMethod(buildOverride("getMessageId", "ID"));
        builder.addMethod(buildOverride("getCrcExtra", "CRC"));
        builder.addMethod(buildOverride("getLengthV1", "LENGTH_V1"));
        builder.addMethod(buildOverride("getLengthV2", "LENGTH_V2"));
    }

    private MethodSpec buildOverride(String name, String retConst)
    {
        return MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", retConst)
                .build();
    }

    private void addGetters(TypeSpec.Builder classBuilder, FieldDef field, String offsetName)
    {
        String methodName = NameUtils.toCamelCase(field.name());
        String baseType = field.baseType();
        boolean isArray = field.isArray();

        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("$L\n", field.description());

        if (isArray)
        {
            method.addParameter(int.class, "index");
        }

        // Calculate address
        String indexCalc = isArray ? " + (index * " + getByteWidth(baseType) + ")" : "";
        String addr = "offset + " + offsetName + indexCalc;

        switch (baseType)
        {
            // 1-byte
            case "uint8_t", "uint8_t_mavlink_version", "char":
                method.returns(int.class)
                        .addStatement("return $T.toUnsignedInt(buffer.get($L))", Byte.class, addr);
                break;
            case "int8_t":
                method.returns(int.class)
                        .addStatement("return buffer.get($L)", addr);
                break;

            // 2-byte
            case "uint16_t":
                method.returns(int.class)
                        .addStatement("return $T.toUnsignedInt(buffer.getShort($L))", Short.class, addr);
                break;
            case "int16_t":
                method.returns(short.class)
                        .addStatement("return buffer.getShort($L)", addr);
                break;

            // 4-byte
            case "uint32_t":
                method.returns(long.class) // Java int is signed, not unsigned 32
                        .addStatement("return $T.toUnsignedLong(buffer.getInt($L))", Integer.class, addr);
                break;
            case "int32_t":
                method.returns(int.class)
                        .addStatement("return buffer.getInt($L)", addr);
                break;
            case "float":
                method.returns(float.class)
                        .addStatement("return buffer.getFloat($L)", addr);
                break;

            // 8-byte
            case "uint64_t", "int64_t":
                method.returns(long.class)
                        .addStatement("return buffer.getLong($L)", addr);
                break;
            case "double":
                method.returns(double.class)
                        .addStatement("return buffer.getDouble($L)", addr);
                break;
        }

        classBuilder.addMethod(method.build());
    }

    private int getByteWidth(String type)
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