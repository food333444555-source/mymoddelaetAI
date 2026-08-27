package com.df.mobvisualizer;

public record TrackedMob(
        int id,
        String type,
        String name,
        int x,
        int y,
        int z,
        boolean alert,
        int color,
        boolean player,
        boolean hurt,
        boolean chargedCreeper,
        boolean renamed,
        boolean returned
) {
    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
