package dev.funtime.pe.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * In-session Bedrock resource-pack chunk transfer.
 *
 * <p>The downloaded bytes are kept separate from the Java renderer. Applying
 * a Bedrock pack needs a pack-format/resource-registry adapter; silently
 * treating the bytes as Java assets would corrupt the client registry.</p>
 */
public final class BedrockResourcePackTransfer {
    private final Map<String, Pack> packs = new HashMap<>();
    private int expectedPackCount;

    public void expect(int packCount) throws IOException {
        if (packCount < 0 || packCount > 4096) {
            throw new IOException("Invalid expected Bedrock resource-pack count");
        }
        expectedPackCount = packCount;
    }

    public String acceptDataInfo(BedrockBuffer.Reader reader) throws IOException {
        final String packId = reader.readString();
        final int maxChunkSize = reader.readIntLittleEndian();
        final int chunkCount = reader.readIntLittleEndian();
        final long compressedSize = reader.readLongLittleEndian();
        if (maxChunkSize <= 0 || chunkCount < 0 || chunkCount > 1_000_000
                || compressedSize < 0) {
            throw new IOException("Invalid Bedrock resource-pack data info");
        }
        packs.put(packId, new Pack(packId, maxChunkSize, chunkCount, compressedSize));
        return packId;
    }

    public String acceptChunkData(BedrockBuffer.Reader reader) throws IOException {
        final String packId = reader.readString();
        final int chunkIndex = reader.readIntLittleEndian();
        reader.readLongLittleEndian(); // progress in compressed bytes
        final Pack pack = packs.get(packId);
        if (pack == null) {
            throw new IOException("Unknown Bedrock resource-pack: " + packId);
        }
        if (chunkIndex < 0 || chunkIndex >= pack.chunkCount()) {
            throw new IOException("Invalid Bedrock resource-pack chunk: " + chunkIndex);
        }
        final byte[] data = reader.readBytes(reader.remaining());
        pack.put(chunkIndex, data);
        return packId;
    }

    public byte[] nextChunkRequest(String packId) {
        final Pack pack = packs.get(packId);
        return pack == null ? null : nextChunkRequest(pack);
    }

    public boolean allComplete() {
        return packs.size() == expectedPackCount && packs.values().stream()
                .allMatch(pack -> pack.received() == pack.chunkCount());
    }

    public boolean isComplete(String packId) {
        final Pack pack = packs.get(packId);
        return pack != null && pack.received() == pack.chunkCount();
    }

    public byte[] completedPack(String packId) {
        final Pack pack = packs.get(packId);
        return pack == null || !isComplete(packId) ? null : pack.bytes();
    }

    private static byte[] nextChunkRequest(Pack pack) {
        final int next = pack.nextMissingChunk();
        if (next < 0) {
            return new BedrockBuffer()
                    .writeString(pack.packId())
                    .writeIntLittleEndian(-1)
                    .toByteArray();
        }
        return new BedrockBuffer()
                .writeString(pack.packId())
                .writeIntLittleEndian(next)
                .toByteArray();
    }

    private static final class Pack {
        private final String packId;
        private final int maxChunkSize;
        private final int chunkCount;
        private final long compressedSize;
        private final Map<Integer, byte[]> chunks = new HashMap<>();

        private Pack(String packId, int maxChunkSize, int chunkCount, long compressedSize) {
            this.packId = packId;
            this.maxChunkSize = maxChunkSize;
            this.chunkCount = chunkCount;
            this.compressedSize = compressedSize;
        }

        private String packId() {
            return packId;
        }

        private int chunkCount() {
            return chunkCount;
        }

        private int received() {
            return chunks.size();
        }

        private void put(int index, byte[] data) {
            chunks.putIfAbsent(index, data);
        }

        private int nextMissingChunk() {
            for (int index = 0; index < chunkCount; index++) {
                if (!chunks.containsKey(index)) {
                    return index;
                }
            }
            return -1;
        }

        private byte[] bytes() {
            final ByteArrayOutputStream output = new ByteArrayOutputStream(
                    (int) Math.min(Integer.MAX_VALUE, compressedSize));
            for (int index = 0; index < chunkCount; index++) {
                output.writeBytes(chunks.get(index));
            }
            return output.toByteArray();
        }
    }
}