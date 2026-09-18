package dev.funtime.pe.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class BedrockBatchCodec {
    private BedrockBatchCodec() {
    }

    public static byte[] encode(List<byte[]> packets, boolean compressed) throws IOException {
        final BedrockBuffer batch = new BedrockBuffer();
        for (byte[] packet : packets) {
            batch.writeVarInt(packet.length).writeBytes(packet);
        }
        final byte[] data = batch.toByteArray();
        if (!compressed) {
            return data;
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(data);
        }
        return output.toByteArray();
    }

    public static List<byte[]> decode(byte[] data, boolean compressed) throws IOException {
        final byte[] raw;
        if (compressed) {
            try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(data));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                inflater.transferTo(output);
                raw = output.toByteArray();
            }
        } else {
            raw = data;
        }

        final BedrockBuffer.Reader reader = BedrockBuffer.reader(raw);
        final List<byte[]> packets = new ArrayList<>();
        while (reader.remaining() > 0) {
            final int length = reader.readVarInt();
            packets.add(reader.readBytes(length));
        }
        return packets;
    }
}