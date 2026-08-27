package com.df.mobvisualizer;

public record ChunkMark(int chunkX, int chunkZ, int color, boolean alert, boolean ring, long lastSeen) {
}