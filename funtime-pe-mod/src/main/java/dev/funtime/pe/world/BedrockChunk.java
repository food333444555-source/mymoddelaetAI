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
        if (section == null) {
            return;
        }
        sections.removeIf(existing -> existing.sectionY() == section.sectionY());
        sections.add(section);
    }

    public BedrockChunkSection section(int sectionY) {
        for (BedrockChunkSection section : sections) {
            if (section.sectionY() == sectionY) {
                return section;
            }
        }
        return null;
    }

    public void set(int worldX, int worldY, int worldZ, BedrockBlockState state) {
        final BedrockChunkSection section = section(Math.floorDiv(worldY, 16));
        if (section != null) {
            section.set(Math.floorMod(worldX, 16), Math.floorMod(worldY, 16),
                    Math.floorMod(worldZ, 16), state);
        }
    }
}