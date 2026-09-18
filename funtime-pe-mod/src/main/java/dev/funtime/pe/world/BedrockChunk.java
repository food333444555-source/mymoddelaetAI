package dev.funtime.pe.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BedrockChunk {
    private final int chunkX;
    private final int chunkZ;
    private final List<BedrockChunkSection> sections = new ArrayList<>();

    public BedrockChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public List<BedrockChunkSection> sections() {
        return Collections.unmodifiableList(sections);
    }

    public void addSection(BedrockChunkSection section) {
        sections.add(section);
    }
}