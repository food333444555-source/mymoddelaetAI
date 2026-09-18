package dev.funtime.pe.world;

import java.util.Collection;

/**
 * Java-facing ClientWorld boundary for a translated Bedrock session.
 *
 * <p>This is intentionally a project type, not a subclass of Minecraft's
 * internal ClientWorld. Constructing the vanilla class would require a Java
 * ClientPlayNetworkHandler and Java dimension codec, neither of which a
 * Bedrock server provides. The public API mirrors the world operations needed
 * by the Java renderer while keeping that protocol mismatch out of the UI.</p>
 */
public final class ClientWorld {
    private final BedrockWorldState source;
    private final JavaChunkManager chunkManager;

    ClientWorld(BedrockWorldState source) {
        this.source = source;
        this.chunkManager = new JavaChunkManager(source);
    }

    public JavaChunkManager getChunkManager() {
        return chunkManager;
    }

    public WorldChunk getChunk(int chunkX, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkZ);
    }

    public JavaBlockState getBlockState(int x, int y, int z) {
        final WorldChunk chunk = getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return chunk == null
                ? new JavaBlockState("minecraft:air", JavaBlockMapper.map(BedrockBlockState.air()))
                : chunk.getBlockState(x, y, z);
    }

    public Collection<WorldChunk> loadedChunks() {
        return chunkManager.loadedChunks();
    }

    public int dimension() {
        return source.dimension();
    }

    public int bottomY() {
        return source.minY();
    }

    public int topY() {
        return source.maxY();
    }

    public long getTime() {
        return source.gameTime();
    }

    public double playerX() {
        return source.playerX();
    }

    public double playerY() {
        return source.playerY();
    }

    public double playerZ() {
        return source.playerZ();
    }

    public float playerYaw() {
        return source.playerYaw();
    }

    public float playerPitch() {
        return source.playerPitch();
    }
}