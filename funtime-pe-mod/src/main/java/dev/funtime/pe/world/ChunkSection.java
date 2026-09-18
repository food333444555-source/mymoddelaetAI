package dev.funtime.pe.world;

import java.util.Objects;

/**
 * Java-facing equivalent of a Minecraft ChunkSection.
 *
 * <p>The section keeps the Bedrock storage private and exposes mapped
 * JavaBlockState values to the renderer. Light values are deliberately
 * represented separately so a later Bedrock light decoder can fill them
 * without changing the Java-facing API.</p>
 */
public final class ChunkSection {
    public static final int SIZE = BedrockChunkSection.SIZE;

    private final BedrockChunkSection source;

    ChunkSection(BedrockChunkSection source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public int sectionY() {
        return source.sectionY();
    }

    public JavaBlockState getBlockState(int x, int y, int z) {
        return new JavaBlockState(
                source.get(x, y, z).name(),
                JavaBlockMapper.map(source.get(x, y, z)));
    }

    public boolean isEmpty() {
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    if (!source.get(x, y, z).name().equals("minecraft:air")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public int nonAirBlockCount() {
        int count = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    if (!source.get(x, y, z).name().equals("minecraft:air")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public int blockLight(int x, int y, int z) {
        return 0;
    }

    public int skyLight(int x, int y, int z) {
        return 15;
    }
}