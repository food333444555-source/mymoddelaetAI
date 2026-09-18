package dev.funtime.pe.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Java-facing equivalent of a Minecraft WorldChunk.
 *
 * <p>It is a read-only view over one decoded Bedrock chunk. Network updates
 * replace block values in the Bedrock model; the view never exposes the
 * Bedrock packet or registry types to the Java-style presentation layer.</p>
 */
public final class WorldChunk {
    private final BedrockChunk source;
    private final List<ChunkSection> sections;

    WorldChunk(BedrockChunk source) {
        this.source = Objects.requireNonNull(source, "source");
        final List<ChunkSection> mappedSections = new ArrayList<>();
        for (BedrockChunkSection section : source.sections()) {
            mappedSections.add(new ChunkSection(section));
        }
        this.sections = Collections.unmodifiableList(mappedSections);
    }

    public int chunkX() {
        return source.chunkX();
    }

    public int chunkZ() {
        return source.chunkZ();
    }

    public List<ChunkSection> getSections() {
        return sections;
    }

    public ChunkSection getSection(int sectionY) {
        for (ChunkSection section : sections) {
            if (section.sectionY() == sectionY) {
                return section;
            }
        }
        return null;
    }

    public JavaBlockState getBlockState(int worldX, int worldY, int worldZ) {
        final ChunkSection section = getSection(Math.floorDiv(worldY, 16));
        if (section == null) {
            return new JavaBlockState(
                    "minecraft:air", JavaBlockMapper.map(BedrockBlockState.air()));
        }
        return section.getBlockState(
                Math.floorMod(worldX, 16),
                Math.floorMod(worldY, 16),
                Math.floorMod(worldZ, 16));
    }

    public JavaBlockState getTopBlock(int localX, int localZ, int minY, int maxY) {
        JavaBlockState result = new JavaBlockState(
                "minecraft:air", JavaBlockMapper.map(BedrockBlockState.air()));
        for (int worldY = minY; worldY < maxY; worldY++) {
            final JavaBlockState state = getBlockState(
                    chunkX() * 16 + localX, worldY, chunkZ() * 16 + localZ);
            if (!state.isAir()) {
                result = state;
            }
        }
        return result;
    }

    public int getTopY(int localX, int localZ, int minY, int maxY) {
        int result = minY;
        for (int worldY = minY; worldY < maxY; worldY++) {
            if (!getBlockState(
                    chunkX() * 16 + localX, worldY, chunkZ() * 16 + localZ).isAir()) {
                result = worldY;
            }
        }
        return result;
    }

    BedrockChunk source() {
        return source;
    }
}