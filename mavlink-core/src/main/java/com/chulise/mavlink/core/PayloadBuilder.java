package com.chulise.mavlink.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PayloadBuilder
{
    private ByteBuffer buffer;
    private int baseOffset;

    public PayloadBuilder wrap(ByteBuffer buffer, int offset)
    {
        this.buffer = buffer;
        this.baseOffset = offset;
        if (buffer.order() != ByteOrder.LITTLE_ENDIAN)
        {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return this;
    }

    public PayloadBuilder putUint8(int offset, int value)
    {
        buffer.put(baseOffset + offset, (byte) value);
        return this;
    }

    public PayloadBuilder putInt8(int offset, byte value)
    {
        buffer.put(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putUint16(int offset, int value)
    {
        buffer.putShort(baseOffset + offset, (short) value);
        return this;
    }

    public PayloadBuilder putInt16(int offset, short value)
    {
        buffer.putShort(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putUint32(int offset, long value)
    {
        buffer.putInt(baseOffset + offset, (int) value);
        return this;
    }

    public PayloadBuilder putInt32(int offset, int value)
    {
        buffer.putInt(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putFloat(int offset, float value)
    {
        buffer.putFloat(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putUint64(int offset, long value)
    {
        buffer.putLong(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putInt64(int offset, long value)
    {
        buffer.putLong(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putDouble(int offset, double value)
    {
        buffer.putDouble(baseOffset + offset, value);
        return this;
    }

    public PayloadBuilder putBytes(int offset, byte[] values)
    {
        return putBytes(offset, values, values.length);
    }

    public PayloadBuilder putBytes(int offset, byte[] values, int length)
    {
        ByteBuffer dst = buffer.duplicate();
        dst.position(baseOffset + offset);
        dst.put(values, 0, length);
        return this;
    }

    public PayloadBuilder putInt16Array(int offset, short[] values)
    {
        return putInt16Array(offset, values, values.length);
    }

    public PayloadBuilder putInt16Array(int offset, short[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putShort(baseOffset + offset + (i * 2), values[i]);
        }
        return this;
    }

    public PayloadBuilder putUint16Array(int offset, int[] values)
    {
        return putUint16Array(offset, values, values.length);
    }

    public PayloadBuilder putUint16Array(int offset, int[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putShort(baseOffset + offset + (i * 2), (short) values[i]);
        }
        return this;
    }

    public PayloadBuilder putInt32Array(int offset, int[] values)
    {
        return putInt32Array(offset, values, values.length);
    }

    public PayloadBuilder putInt32Array(int offset, int[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putInt(baseOffset + offset + (i * 4), values[i]);
        }
        return this;
    }

    public PayloadBuilder putUint32Array(int offset, long[] values)
    {
        return putUint32Array(offset, values, values.length);
    }

    public PayloadBuilder putUint32Array(int offset, long[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putInt(baseOffset + offset + (i * 4), (int) values[i]);
        }
        return this;
    }

    public PayloadBuilder putFloatArray(int offset, float[] values)
    {
        return putFloatArray(offset, values, values.length);
    }

    public PayloadBuilder putFloatArray(int offset, float[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putFloat(baseOffset + offset + (i * 4), values[i]);
        }
        return this;
    }

    public PayloadBuilder putInt64Array(int offset, long[] values)
    {
        return putInt64Array(offset, values, values.length);
    }

    public PayloadBuilder putInt64Array(int offset, long[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putLong(baseOffset + offset + (i * 8), values[i]);
        }
        return this;
    }

    public PayloadBuilder putUint64Array(int offset, long[] values)
    {
        return putUint64Array(offset, values, values.length);
    }

    public PayloadBuilder putUint64Array(int offset, long[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putLong(baseOffset + offset + (i * 8), values[i]);
        }
        return this;
    }

    public PayloadBuilder putDoubleArray(int offset, double[] values)
    {
        return putDoubleArray(offset, values, values.length);
    }

    public PayloadBuilder putDoubleArray(int offset, double[] values, int length)
    {
        for (int i = 0; i < length; i++)
        {
            buffer.putDouble(baseOffset + offset + (i * 8), values[i]);
        }
        return this;
    }
}
