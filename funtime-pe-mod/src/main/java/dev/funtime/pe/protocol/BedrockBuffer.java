package dev.funtime.pe.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class BedrockBuffer {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public BedrockBuffer writeByte(int value) {
        output.write(value & 0xff);
        return this;
    }

    public BedrockBuffer writeBytes(byte[] value) {
        output.writeBytes(value);
        return this;
    }

    public BedrockBuffer writeShortBigEndian(int value) {
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
        return this;
    }

    public BedrockBuffer writeShortLittleEndian(int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        return this;
    }

    public BedrockBuffer writeIntBigEndian(int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
        return this;
    }

    public BedrockBuffer writeIntLittleEndian(int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
        return this;
    }

    public BedrockBuffer writeLongBigEndian(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
        return this;
    }

    public BedrockBuffer writeLongLittleEndian(long value) {
        for (int shift = 0; shift < 64; shift += 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
        return this;
    }

    public BedrockBuffer writeFloatLittleEndian(float value) {
        return writeIntLittleEndian(Float.floatToIntBits(value));
    }

    public BedrockBuffer writeDoubleLittleEndian(double value) {
        return writeLongLittleEndian(Double.doubleToLongBits(value));
    }

    public BedrockBuffer writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public BedrockBuffer writeTriadLittleEndian(int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        return this;
    }

    public BedrockBuffer writeVarInt(int value) {
        int current = value;
        while ((current & ~0x7f) != 0) {
            writeByte((current & 0x7f) | 0x80);
            current >>>= 7;
        }
        return writeByte(current);
    }

    public BedrockBuffer writeVarLong(long value) {
        long current = value;
        while ((current & ~0x7fL) != 0) {
            writeByte((int) (current & 0x7f) | 0x80);
            current >>>= 7;
        }
        return writeByte((int) current);
    }

    public BedrockBuffer writeZigZagInt(int value) {
        return writeVarInt((value << 1) ^ (value >> 31));
    }

    public BedrockBuffer writeZigZagLong(long value) {
        return writeVarLong((value << 1) ^ (value >> 63));
    }

    public BedrockBuffer writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        return writeBytes(bytes);
    }

    public BedrockBuffer writeUuid(UUID uuid) {
        return writeLongBigEndian(uuid.getMostSignificantBits())
                .writeLongBigEndian(uuid.getLeastSignificantBits());
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }

    public static Reader reader(byte[] value) {
        return new Reader(value);
    }

    public static final class Reader {
        private final byte[] data;
        private int position;

        private Reader(byte[] data) {
            this.data = data;
        }

        public int remaining() {
            return data.length - position;
        }

        public int readUnsignedByte() throws IOException {
            require(1);
            return data[position++] & 0xff;
        }

        public boolean readBoolean() throws IOException {
            return readUnsignedByte() != 0;
        }

        public int readUnsignedShortBigEndian() throws IOException {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }

        public int readUnsignedShortLittleEndian() throws IOException {
            return readUnsignedByte() | (readUnsignedByte() << 8);
        }

        public int readIntLittleEndian() throws IOException {
            return readUnsignedByte()
                    | (readUnsignedByte() << 8)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 24);
        }

        public int readIntBigEndian() throws IOException {
            return (readUnsignedByte() << 24)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8)
                    | readUnsignedByte();
        }

        public long readUnsignedLongBigEndian() throws IOException {
            long value = 0;
            for (int index = 0; index < 8; index++) {
                value = (value << 8) | readUnsignedByte();
            }
            return value;
        }

        public long readLongLittleEndian() throws IOException {
            long value = 0;
            for (int index = 0; index < 8; index++) {
                value |= (long) readUnsignedByte() << (index * 8);
            }
            return value;
        }

        public long readLongBigEndian() throws IOException {
            long value = 0;
            for (int index = 0; index < 8; index++) {
                value = (value << 8) | readUnsignedByte();
            }
            return value;
        }

        public UUID readUuid() throws IOException {
            return new UUID(readLongBigEndian(), readLongBigEndian());
        }

        public float readFloatLittleEndian() throws IOException {
            return Float.intBitsToFloat(readIntLittleEndian());
        }

        public double readDoubleLittleEndian() throws IOException {
            return Double.longBitsToDouble(readLongLittleEndian());
        }

        public int readTriadLittleEndian() throws IOException {
            return readUnsignedByte()
                    | (readUnsignedByte() << 8)
                    | (readUnsignedByte() << 16);
        }

        public int readVarInt() throws IOException {
            int value = 0;
            int shift = 0;
            while (shift < 35) {
                int part = readUnsignedByte();
                value |= (part & 0x7f) << shift;
                if ((part & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
            throw new IOException("Invalid Bedrock varint");
        }

        public int readZigZagInt() throws IOException {
            final int value = readVarInt();
            return (value >>> 1) ^ -(value & 1);
        }

        public long readVarLong() throws IOException {
            long value = 0;
            int shift = 0;
            while (shift < 70) {
                int part = readUnsignedByte();
                value |= (long) (part & 0x7f) << shift;
                if ((part & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
            throw new IOException("Invalid Bedrock varlong");
        }

        public String readString() throws IOException {
            int length = readVarInt();
            if (length < 0 || length > remaining()) {
                throw new IOException("Invalid Bedrock string length: " + length);
            }
            String value = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        public byte[] readBytes(int length) throws IOException {
            if (length < 0 || length > remaining()) {
                throw new IOException("Invalid Bedrock byte length: " + length);
            }
            byte[] result = new byte[length];
            System.arraycopy(data, position, result, 0, length);
            position += length;
            return result;
        }

        public void skip(int length) throws IOException {
            require(length);
            position += length;
        }

        private void require(int length) throws IOException {
            if (length < 0 || position > data.length - length) {
                throw new IOException("Unexpected end of Bedrock packet");
            }
        }
    }
}