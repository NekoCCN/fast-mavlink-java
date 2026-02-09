package com.chulise.mavlink.generator;

import com.chulise.mavlink.core.MavlinkView;
import com.chulise.mavlink.generator.model.FieldDef;
import com.chulise.mavlink.generator.model.MessageDef;
import com.squareup.javapoet.*;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
            addWriters(classBuilder, field, offsetName);
        }

        addOverrides(classBuilder);
        addPackMethod(classBuilder, msg, layout);

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

    private void addWriters(TypeSpec.Builder classBuilder, FieldDef field, String offsetName)
    {
        String camel = NameUtils.toCamelCase(field.name());
        String methodName = "write" + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        String baseType = field.baseType();
        boolean isArray = field.isArray();

        ClassName byteBuffer = ClassName.get(ByteBuffer.class);

        if (isArray)
        {
            TypeName elemType = writerElementType(baseType);
            TypeName arrayType = ArrayTypeName.of(elemType);

            MethodSpec.Builder bulk = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(byteBuffer, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(arrayType, "values");

            bulk.addStatement("int limit = Math.min(values.length, $L)", field.arrayLength());
            if (getByteWidth(baseType) == 1)
            {
                bulk.addStatement("$T dst = buffer.duplicate()", byteBuffer);
                bulk.addStatement("dst.position(offset + $L)", offsetName);
                bulk.addStatement("dst.put(values, 0, limit)");
            } else
            {
                bulk.beginControlFlow("for (int i = 0; i < limit; i++)");
                addWriteStatement(bulk, baseType, "offset + " + offsetName + " + (i * " + getByteWidth(baseType) + ")", "values[i]");
                bulk.endControlFlow();
            }
            classBuilder.addMethod(bulk.build());

            MethodSpec.Builder indexed = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(byteBuffer, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "index")
                    .addParameter(elemType, "value");

            indexed.addStatement("int addr = offset + $L + (index * $L)", offsetName, getByteWidth(baseType));
            addWriteStatement(indexed, baseType, "addr", "value");
            classBuilder.addMethod(indexed.build());
        } else
        {
            TypeName valueType = writerScalarType(baseType);
            MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(byteBuffer, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(valueType, "value");

            String addr = "offset + " + offsetName;
            addWriteStatement(method, baseType, addr, "value");
            classBuilder.addMethod(method.build());
        }
    }

    private TypeName writerScalarType(String baseType)
    {
        return switch (baseType)
        {
            case "uint8_t", "uint8_t_mavlink_version", "char" -> TypeName.INT;
            case "int8_t" -> TypeName.BYTE;
            case "uint16_t" -> TypeName.INT;
            case "int16_t" -> TypeName.SHORT;
            case "uint32_t" -> TypeName.LONG;
            case "int32_t" -> TypeName.INT;
            case "float" -> TypeName.FLOAT;
            case "uint64_t", "int64_t" -> TypeName.LONG;
            case "double" -> TypeName.DOUBLE;
            default -> TypeName.INT;
        };
    }

    private TypeName writerElementType(String baseType)
    {
        return switch (baseType)
        {
            case "uint8_t", "uint8_t_mavlink_version", "char", "int8_t" -> TypeName.BYTE;
            case "uint16_t", "int32_t" -> TypeName.INT;
            case "int16_t" -> TypeName.SHORT;
            case "uint32_t" -> TypeName.LONG;
            case "float" -> TypeName.FLOAT;
            case "uint64_t", "int64_t" -> TypeName.LONG;
            case "double" -> TypeName.DOUBLE;
            default -> TypeName.INT;
        };
    }

    private void addWriteStatement(MethodSpec.Builder method, String baseType, String addr, String value)
    {
        switch (baseType)
        {
            case "uint8_t", "uint8_t_mavlink_version", "char", "int8_t" ->
                    method.addStatement("buffer.put($L, (byte) $L)", addr, value);
            case "uint16_t", "int16_t" ->
                    method.addStatement("buffer.putShort($L, (short) $L)", addr, value);
            case "uint32_t", "int32_t" ->
                    method.addStatement("buffer.putInt($L, (int) $L)", addr, value);
            case "float" ->
                    method.addStatement("buffer.putFloat($L, $L)", addr, value);
            case "uint64_t", "int64_t" ->
                    method.addStatement("buffer.putLong($L, $L)", addr, value);
            case "double" ->
                    method.addStatement("buffer.putDouble($L, $L)", addr, value);
        }
    }

    private void addPackMethod(TypeSpec.Builder classBuilder, MessageDef msg, MessageLayout layout)
    {
        ClassName byteBuffer = ClassName.get(ByteBuffer.class);
        ClassName byteOrder = ClassName.get(ByteOrder.class);
        ClassName packetWriter = ClassName.get("com.chulise.mavlink.core", "MavlinkPacketWriter");
        ClassName packetView = ClassName.get("com.chulise.mavlink.core", "MavlinkPacketView");

        MethodSpec.Builder method = MethodSpec.methodBuilder("pack")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addJavadoc("Writes payload to buffer (little-endian). Returns LENGTH_V2.\n");

        method.addStatement("if (buffer.order() != $T.LITTLE_ENDIAN) buffer.order($T.LITTLE_ENDIAN)", byteOrder, byteOrder);

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }
            TypeName paramType = field.isArray()
                    ? ArrayTypeName.of(writerElementType(field.baseType()))
                    : writerScalarType(field.baseType());
            method.addParameter(paramType, NameUtils.toCamelCase(field.name()));
        }

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }

            String offsetName = "OFF_" + field.name().toUpperCase();
            String varName = NameUtils.toCamelCase(field.name());
            String baseType = field.baseType();

            if (field.isArray())
            {
                int width = getByteWidth(baseType);
                String limitVar = "limit_" + varName;
                if (width == 1)
                {
                    String dstVar = "dst_" + varName;
                    method.addStatement("int $L = Math.min($L.length, $L)", limitVar, varName, field.arrayLength());
                    method.addStatement("$T $L = buffer.duplicate()", byteBuffer, dstVar);
                    method.addStatement("$L.position(offset + $L)", dstVar, offsetName);
                    method.addStatement("$L.put($L, 0, $L)", dstVar, varName, limitVar);
                } else
                {
                    method.addStatement("int $L = Math.min($L.length, $L)", limitVar, varName, field.arrayLength());
                    method.beginControlFlow("for (int i = 0; i < $L; i++)", limitVar);
                    addWriteStatement(method, baseType,
                            "offset + " + offsetName + " + (i * " + width + ")", varName + "[i]");
                    method.endControlFlow();
                }
            } else
            {
                addWriteStatement(method, baseType, "offset + " + offsetName, varName);
            }
        }

        method.addStatement("return LENGTH_V2");
        classBuilder.addMethod(method.build());

        MethodSpec.Builder packV1 = MethodSpec.methodBuilder("packV1")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "pktSequence")
                .addParameter(int.class, "pktSysId")
                .addParameter(int.class, "pktCompId")
                .addJavadoc("Writes a MAVLink V1 packet. Returns total packet length.\n");

        MethodSpec.Builder packV2 = MethodSpec.methodBuilder("packV2")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "pktSequence")
                .addParameter(int.class, "pktSysId")
                .addParameter(int.class, "pktCompId")
                .addParameter(int.class, "pktCompatFlags")
                .addParameter(int.class, "pktIncompatFlags")
                .addParameter(boolean.class, "pktTrimExtensionZeros")
                .addParameter(byte[].class, "pktSecretKey")
                .addParameter(int.class, "pktLinkId")
                .addParameter(long.class, "pktSignatureTimestamp")
                .addJavadoc("Writes a MAVLink V2 packet. Returns total packet length.\n");

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }
            TypeName paramType = field.isArray()
                    ? ArrayTypeName.of(writerElementType(field.baseType()))
                    : writerScalarType(field.baseType());
            String paramName = NameUtils.toCamelCase(field.name());
            packV1.addParameter(paramType, paramName);
            packV2.addParameter(paramType, paramName);
        }

        packV1.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V1", packetView);
        packV1.addStatement("pack(buffer, payloadOffset, $L)", joinFieldArgs(msg, layout));
        packV1.addStatement("return $T.writeV1InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V1)", packetWriter);
        classBuilder.addMethod(packV1.build());

        packV2.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V2", packetView);
        packV2.addStatement("pack(buffer, payloadOffset, $L)", joinFieldArgs(msg, layout));
        packV2.addStatement("return $T.writeV2InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V2, LENGTH_V1, pktTrimExtensionZeros, pktCompatFlags, pktIncompatFlags, pktSecretKey, pktLinkId, pktSignatureTimestamp)", packetWriter);
        classBuilder.addMethod(packV2.build());
    }

    private String joinFieldArgs(MessageDef msg, MessageLayout layout)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }
            if (!first)
            {
                sb.append(", ");
            }
            sb.append(NameUtils.toCamelCase(field.name()));
            first = false;
        }
        return sb.toString();
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
