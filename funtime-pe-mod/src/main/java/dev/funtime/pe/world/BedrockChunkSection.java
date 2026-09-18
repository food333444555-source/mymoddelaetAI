package dev.funtime.pe.world;

import java.util.Arrays;

public final class BedrockChunkSection {
    public static final int SIZE = 16;
    private final int sectionY;
    private final BedrockBlockState[] blocks = new BedrockBlockState[SIZE * SIZE * SIZE];

    public BedrockChunkSection(int sectionY) {
        this.sectionY = sectionY;
        Arrays.fill(blocks, BedrockBlockState.air());
    }

    public int sectionY() {
        return sectionY;
    }

    public synchronized BedrockBlockState get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public synchronized void set(int x, int y, int z, BedrockBlockState state) {
        blocks[index(x, y, z)] = state == null ? BedrockBlockState.air() : state;
    }

    private static int index(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= SIZE || y >= SIZE || z >= SIZE) {
            throw new IndexOutOfBoundsException(x + "," + y + "," + z);
        }
        return (y * SIZE + z) * SIZE + x;
    }
}