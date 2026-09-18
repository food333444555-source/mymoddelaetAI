package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import java.util.Collections;
import java.util.List;

/**
 * Immutable Java-facing view over one decoded network chunk.
 */
public final class JavaChunkView {
    private final WorldChunk source;
    private final List<ChunkSection> sections;

    public JavaChunkView(BedrockChunk source) {
        this(new WorldChunk(source));
    }

    public JavaChunkView(WorldChunk source) {
        this.source = source;
        this.sections = Collections.unmodifiableList(source.getSections());
    }

    public int chunkX() {
        return source.chunkX();
    }

    public int chunkZ() {
        return source.chunkZ();
    }

    public List<ChunkSection> sections() {
        return sections;
    }

    public JavaBlockState block(int x, int y, int z) {
        return source.getBlockState(
                source.chunkX() * 16 + x, y, source.chunkZ() * 16 + z);
    }

    public JavaBlockState topBlock(int x, int z) {
        return source.getTopBlock(x, z, -64, 320);
    }

    public int topY(int x, int z) {
        return source.getTopY(x, z, -64, 320);
    }
}