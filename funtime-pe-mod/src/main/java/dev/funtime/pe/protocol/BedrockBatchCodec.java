package dev.funtime.pe.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class BedrockBatchCodec {
    private static final int MAX_BATCH_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PACKET_COUNT = 4096;

    private BedrockBatchCodec() {
    }

    public static byte[] encode(List<byte[]> packets, boolean compressed) throws IOException {
        final BedrockBuffer batch = new BedrockBuffer();
        for (byte[] packet : packets) {
            if (packet == null || packet.length > MAX_BATCH_BYTES) {
                throw new IOException("Invalid Bedrock packet in batch");
            }
            batch.writeVarInt(packet.length).writeBytes(packet);
        }
        final byte[] data = batch.toByteArray();
        if (data.length > MAX_BATCH_BYTES) {
            throw new IOException("Bedrock batch is too large");
        }
        if (!compressed) {
            return data;
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(
                output, new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            deflater.write(data);
        }
        return output.toByteArray();
    }

    public static List<byte[]> decode(byte[] data, boolean compressed) throws IOException {
        if (data == null || data.length > MAX_BATCH_BYTES) {
            throw new IOException("Invalid Bedrock batch size");
        }
        final byte[] raw;
        if (compressed) {
            try (InflaterInputStream inflater = new InflaterInputStream(
                         new ByteArrayInputStream(data), new Inflater(true));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                inflater.transferTo(output);
                raw = output.toByteArray();
            }
        } else {
            raw = data;
        }
        if (raw.length > MAX_BATCH_BYTES) {
            throw new IOException("Decompressed Bedrock batch is too large");
        }

        final BedrockBuffer.Reader reader = BedrockBuffer.reader(raw);
        final List<byte[]> packets = new ArrayList<>();
        while (reader.remaining() > 0) {
            if (packets.size() >= MAX_PACKET_COUNT) {
                throw new IOException("Too many packets in Bedrock batch");
            }
            final int length = reader.readVarInt();
            if (length < 0 || length > reader.remaining() || length > MAX_BATCH_BYTES) {
                throw new IOException("Invalid packet length in Bedrock batch: " + length);
            }
            packets.add(reader.readBytes(length));
        }
        return packets;
    }
}