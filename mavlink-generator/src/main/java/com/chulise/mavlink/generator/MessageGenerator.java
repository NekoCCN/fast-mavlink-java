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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MessageGenerator
{
    private final Path outputDir;

    public MessageGenerator(Path outputDir)
    {
        this.outputDir = outputDir;
    }

    public void generate(MessageDef msg, MessageLayout layout, String className)
    {
        Map<String, String> safeFieldNames = buildSafeFieldNames(msg, layout);

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
        addPayloadType(classBuilder, msg, layout, safeFieldNames);
        addPayloadPoolType(classBuilder);
        addPackMethod(classBuilder, msg, layout, safeFieldNames);

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

    private void addPackMethod(TypeSpec.Builder classBuilder, MessageDef msg, MessageLayout layout,
            Map<String, String> safeFieldNames)
    {
        ClassName byteBuffer = ClassName.get(ByteBuffer.class);
        ClassName byteOrder = ClassName.get(ByteOrder.class);
        ClassName packetWriter = ClassName.get("com.chulise.mavlink.core", "MavlinkPacketWriter");
        ClassName packetView = ClassName.get("com.chulise.mavlink.core", "MavlinkPacketView");
        TypeName payloadType = ClassName.bestGuess("Payload");
        ClassName encoderType = ClassName.get("com.chulise.mavlink.core", "MavlinkPacketWriter", "Encoder");

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
            method.addParameter(paramType, safeFieldNames.get(field.name()));
        }

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }

            String offsetName = "OFF_" + field.name().toUpperCase();
            String varName = safeFieldNames.get(field.name());
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

        MethodSpec.Builder packPayload = MethodSpec.methodBuilder("pack")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(payloadType, "payload")
                .addJavadoc("Writes payload to buffer using a payload object. Returns LENGTH_V2.\n");

        packPayload.addStatement("return pack(buffer, offset, $L)", joinPayloadFieldArgs(msg, layout, safeFieldNames));
        classBuilder.addMethod(packPayload.build());

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
            String paramName = safeFieldNames.get(field.name());
            packV1.addParameter(paramType, paramName);
            packV2.addParameter(paramType, paramName);
        }

        packV1.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V1", packetView);
        packV1.addStatement("pack(buffer, payloadOffset, $L)", joinFieldArgs(msg, layout, safeFieldNames));
        packV1.addStatement("return $T.writeV1InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V1)", packetWriter);
        classBuilder.addMethod(packV1.build());

        MethodSpec.Builder packV1Payload = MethodSpec.methodBuilder("packV1")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "pktSequence")
                .addParameter(int.class, "pktSysId")
                .addParameter(int.class, "pktCompId")
                .addParameter(payloadType, "payload")
                .addJavadoc("Writes a MAVLink V1 packet using a payload object. Returns total packet length.\n");

        packV1Payload.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V1", packetView);
        packV1Payload.addStatement("pack(buffer, payloadOffset, payload)");
        packV1Payload.addStatement("return $T.writeV1InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V1)", packetWriter);
        classBuilder.addMethod(packV1Payload.build());

        packV2.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V2", packetView);
        packV2.addStatement("pack(buffer, payloadOffset, $L)", joinFieldArgs(msg, layout, safeFieldNames));
        packV2.addStatement("return $T.writeV2InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V2, LENGTH_V1, pktTrimExtensionZeros, pktCompatFlags, pktIncompatFlags, pktSecretKey, pktLinkId, pktSignatureTimestamp)", packetWriter);
        classBuilder.addMethod(packV2.build());

        MethodSpec.Builder packV2Payload = MethodSpec.methodBuilder("packV2")
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
                .addParameter(payloadType, "payload")
                .addJavadoc("Writes a MAVLink V2 packet using a payload object. Returns total packet length.\n");

        packV2Payload.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V2", packetView);
        packV2Payload.addStatement("pack(buffer, payloadOffset, payload)");
        packV2Payload.addStatement("return $T.writeV2InPlace(buffer, offset, pktSequence, pktSysId, pktCompId, ID, CRC, LENGTH_V2, LENGTH_V1, pktTrimExtensionZeros, pktCompatFlags, pktIncompatFlags, pktSecretKey, pktLinkId, pktSignatureTimestamp)", packetWriter);
        classBuilder.addMethod(packV2Payload.build());

        MethodSpec.Builder packV1Encoder = MethodSpec.methodBuilder("packV1")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(encoderType, "encoder")
                .addParameter(int.class, "pktSequence")
                .addParameter(payloadType, "payload")
                .addJavadoc("Writes a MAVLink V1 packet using an encoder. Returns total packet length.\n");

        packV1Encoder.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V1", packetView);
        packV1Encoder.addStatement("pack(buffer, payloadOffset, payload)");
        packV1Encoder.addStatement("return encoder.writeV1(buffer, offset, pktSequence, ID, CRC, LENGTH_V1)");
        classBuilder.addMethod(packV1Encoder.build());

        MethodSpec.Builder packV2Encoder = MethodSpec.methodBuilder("packV2")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(int.class)
                .addParameter(byteBuffer, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(encoderType, "encoder")
                .addParameter(int.class, "pktSequence")
                .addParameter(long.class, "pktSignatureTimestamp")
                .addParameter(payloadType, "payload")
                .addJavadoc("Writes a MAVLink V2 packet using an encoder. Returns total packet length.\n");

        packV2Encoder.addStatement("int payloadOffset = offset + $T.HEADER_LEN_V2", packetView);
        packV2Encoder.addStatement("pack(buffer, payloadOffset, payload)");
        packV2Encoder.addStatement("return encoder.writeV2(buffer, offset, pktSequence, ID, CRC, LENGTH_V2, LENGTH_V1, pktSignatureTimestamp)");
        classBuilder.addMethod(packV2Encoder.build());
    }

    private String joinFieldArgs(MessageDef msg, MessageLayout layout, Map<String, String> safeFieldNames)
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
            sb.append(safeFieldNames.get(field.name()));
            first = false;
        }
        return sb.toString();
    }

    private String joinPayloadFieldArgs(MessageDef msg, MessageLayout layout, Map<String, String> safeFieldNames)
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
            sb.append("payload.").append(safeFieldNames.get(field.name()));
            first = false;
        }
        return sb.toString();
    }

    private void addPayloadType(TypeSpec.Builder classBuilder, MessageDef msg, MessageLayout layout,
            Map<String, String> safeFieldNames)
    {
        TypeSpec.Builder payload = TypeSpec.classBuilder("Payload")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        MethodSpec.Builder reset = MethodSpec.methodBuilder("reset")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.bestGuess("Payload"));

        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }
            TypeName type = field.isArray()
                    ? ArrayTypeName.of(writerElementType(field.baseType()))
                    : writerScalarType(field.baseType());
            String safeFieldName = safeFieldNames.get(field.name());
            payload.addField(FieldSpec.builder(type, safeFieldName, Modifier.PUBLIC).build());

            String fieldName = safeFieldName;
            if (type instanceof ArrayTypeName)
            {
                reset.addStatement("this.$L = null", fieldName);
            } else if (type.equals(TypeName.LONG))
            {
                reset.addStatement("this.$L = 0L", fieldName);
            } else if (type.equals(TypeName.FLOAT))
            {
                reset.addStatement("this.$L = 0f", fieldName);
            } else if (type.equals(TypeName.DOUBLE))
            {
                reset.addStatement("this.$L = 0d", fieldName);
            } else if (type.equals(TypeName.BYTE))
            {
                reset.addStatement("this.$L = (byte) 0", fieldName);
            } else if (type.equals(TypeName.SHORT))
            {
                reset.addStatement("this.$L = (short) 0", fieldName);
            } else
            {
                reset.addStatement("this.$L = 0", fieldName);
            }
        }

        reset.addStatement("return this");
        payload.addMethod(reset.build());

        classBuilder.addType(payload.build());
    }

    private void addPayloadPoolType(TypeSpec.Builder classBuilder)
    {
        ClassName payloadType = ClassName.bestGuess("Payload");
        ClassName threadLocal = ClassName.get(ThreadLocal.class);

        FieldSpec poolField = FieldSpec.builder(
                        ParameterizedTypeName.get(threadLocal, payloadType),
                        "POOL",
                        Modifier.PRIVATE,
                        Modifier.STATIC,
                        Modifier.FINAL)
                .initializer("$T.withInitial($T::new)", threadLocal, payloadType)
                .build();

        MethodSpec getMethod = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(payloadType)
                .addStatement("return POOL.get().reset()")
                .build();

        TypeSpec poolType = TypeSpec.classBuilder("PayloadPool")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addField(poolField)
                .addMethod(getMethod)
                .build();

        classBuilder.addType(poolType);
    }

    private Map<String, String> buildSafeFieldNames(MessageDef msg, MessageLayout layout)
    {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (FieldDef field : msg.fields())
        {
            if (!layout.offsets().containsKey(field.name()))
            {
                continue;
            }
            String base = NameUtils.toCamelCase(field.name());
            String safe = toSafeIdentifier(base);
            if (PACK_RESERVED_NAMES.contains(safe))
            {
                safe = "field" + capitalize(safe);
            }
            String unique = safe;
            int n = 2;
            while (used.contains(unique))
            {
                unique = safe + n;
                n++;
            }
            used.add(unique);
            names.put(field.name(), unique);
        }
        return names;
    }

    private String toSafeIdentifier(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return "field";
        }
        String candidate = raw;
        if (!Character.isJavaIdentifierStart(candidate.charAt(0)))
        {
            candidate = "field" + capitalize(candidate);
        }
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < candidate.length(); i++)
        {
            char ch = candidate.charAt(i);
            if (Character.isJavaIdentifierPart(ch))
            {
                normalized.append(ch);
            }
        }
        if (normalized.isEmpty())
        {
            return "field";
        }
        String result = normalized.toString();
        if (JAVA_KEYWORDS.contains(result))
        {
            return "field" + capitalize(result);
        }
        return result;
    }

    private static String capitalize(String s)
    {
        if (s == null || s.isEmpty())
        {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null"));

    private static final Set<String> PACK_RESERVED_NAMES = new HashSet<>(Arrays.asList(
            "buffer", "offset", "index", "payload", "value", "values", "addr", "limit", "i",
            "pktSequence", "pktSysId", "pktCompId", "pktCompatFlags", "pktIncompatFlags",
            "pktTrimExtensionZeros", "pktSecretKey", "pktLinkId", "pktSignatureTimestamp",
            "payloadOffset", "encoder", "messageId"));

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
