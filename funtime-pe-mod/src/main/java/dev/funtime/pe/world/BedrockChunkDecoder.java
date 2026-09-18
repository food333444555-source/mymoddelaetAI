package dev.funtime.pe.world;

import dev.funtime.pe.protocol.BedrockBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class BedrockChunkDecoder {
    private BedrockChunkDecoder() {
    }

    /**
     * Reads the paletted sub-chunk storage used by LevelChunk. The caller must
     * pass the payload after chunk coordinates, dimension and sub-chunk count.
     * Network packet framing and compression stay outside this decoder.
     */
    public static BedrockChunk decode(int chunkX, int chunkZ, int minSectionY,
                                      int sectionCount, byte[] payload) throws IOException {
        if (sectionCount < 0 || sectionCount > 512) {
            throw new IOException("Invalid Bedrock section count: " + sectionCount);
        }
        final BedrockBuffer.Reader reader = BedrockBuffer.reader(payload);
        final BedrockChunk chunk = new BedrockChunk(chunkX, chunkZ);
        for (int section = 0; section < sectionCount; section++) {
            final int version = reader.readUnsignedByte();
            if (version == 0 || version > 32) {
                throw new IOException("Unsupported Bedrock sub-chunk version: " + version);
            }
            /*
             * Versions before the multi-storage format contain one storage.
             * Newer formats carry a storage count byte. Keep the distinction
             * explicit so malformed packets cannot silently shift the reader.
             */
            final int storageCount = version >= 8 ? reader.readUnsignedByte() : 1;
            if (storageCount < 1 || storageCount > 8) {
                throw new IOException("Invalid Bedrock storage count: " + storageCount);
            }
            final BedrockChunkSection result = new BedrockChunkSection(minSectionY + section);
            for (int storage = 0; storage < storageCount; storage++) {
                decodeStorage(reader, result);
            }
            chunk.addSection(result);
        }
        return chunk;
    }

    private static void decodeStorage(BedrockBuffer.Reader reader,
                                      BedrockChunkSection section) throws IOException {
        final int header = reader.readUnsignedByte();
        final int bitsPerBlock = (header & 0xff) >>> 1;
        if (bitsPerBlock == 0) {
            final int paletteIndex = reader.readVarInt();
            final BedrockBlockState state = BedrockBlockMapper.mapRuntimeId(paletteIndex);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.set(x, y, z, state);
                    }
                }
            }
            return;
        }

        if (bitsPerBlock < 1 || bitsPerBlock > 16) {
            throw new IOException("Unsupported Bedrock bits-per-block: " + bitsPerBlock);
        }
        final int valuesPerWord = Math.max(1, 32 / bitsPerBlock);
        final int wordCount = (4096 + valuesPerWord - 1) / valuesPerWord;
        final int[] words = new int[wordCount];
        for (int index = 0; index < wordCount; index++) {
            words[index] = reader.readUnsignedByte()
                    | (reader.readUnsignedByte() << 8)
                    | (reader.readUnsignedByte() << 16)
                    | (reader.readUnsignedByte() << 24);
        }

        final int paletteSize = reader.readVarInt();
        if (paletteSize < 0 || paletteSize > 4096) {
            throw new IOException("Invalid Bedrock palette size: " + paletteSize);
        }
        final List<BedrockBlockState> palette = new ArrayList<>(paletteSize);
        for (int index = 0; index < paletteSize; index++) {
            palette.add(BedrockBlockMapper.mapRuntimeId(reader.readVarInt()));
        }

        final int mask = (1 << bitsPerBlock) - 1;
        for (int block = 0; block < 4096; block++) {
            final int word = words[block / valuesPerWord];
            final int offset = (block % valuesPerWord) * bitsPerBlock;
            final int paletteIndex = (word >>> offset) & mask;
            final BedrockBlockState state = paletteIndex < palette.size()
                    ? palette.get(paletteIndex) : BedrockBlockState.air();
            final int y = block / 256;
            final int z = (block / 16) & 15;
            final int x = block & 15;
            section.set(x, y, z, state);
        }
    }
}