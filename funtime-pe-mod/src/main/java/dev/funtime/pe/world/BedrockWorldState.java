package dev.funtime.pe.world;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe Bedrock-side world model. The network thread writes here; the
 * client/render bridge consumes immutable snapshots on the Minecraft thread.
 */
public final class BedrockWorldState {
    private final Map<Long, BedrockChunk> chunks = new ConcurrentHashMap<>();
    private final Map<Long, BedrockEntityState> entities = new ConcurrentHashMap<>();
    private final Queue<BlockUpdate> pendingBlockUpdates = new ConcurrentLinkedQueue<>();
    private volatile int dimension;
    private volatile int minY = -64;
    private volatile int maxY = 320;
    private volatile long localRuntimeId;
    private volatile double playerX;
    private volatile double playerY;
    private volatile double playerZ;
    private volatile float playerYaw;
    private volatile float playerPitch;
    private volatile long gameTime;

    public void configure(int dimension, int minY, int maxY, long localRuntimeId) {
        this.dimension = dimension;
        this.minY = minY;
        this.maxY = maxY;
        this.localRuntimeId = localRuntimeId;
    }

    public void setLocalPlayer(double x, double y, double z,
                               float yaw, float pitch, long runtimeId) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerYaw = yaw;
        this.playerPitch = pitch;
        this.localRuntimeId = runtimeId;
    }

    public void moveLocalPlayer(double x, double y, double z,
                                float yaw, float pitch) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerYaw = yaw;
        this.playerPitch = pitch;
    }

    public void respawn(double x, double y, double z,
                        float yaw, float pitch, long runtimeId) {
        chunks.clear();
        entities.clear();
        pendingBlockUpdates.clear();
        setLocalPlayer(x, y, z, yaw, pitch, runtimeId);
        gameTime = 0;
    }

    public void changeDimension(int dimension, double x, double y, double z) {
        chunks.clear();
        entities.clear();
        pendingBlockUpdates.clear();
        this.dimension = dimension;
        moveLocalPlayer(x, y, z, playerYaw, playerPitch);
    }

    public int dimension() {
        return dimension;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public long localRuntimeId() {
        return localRuntimeId;
    }

    public double playerX() {
        return playerX;
    }

    public double playerY() {
        return playerY;
    }

    public double playerZ() {
        return playerZ;
    }

    public float playerYaw() {
        return playerYaw;
    }

    public float playerPitch() {
        return playerPitch;
    }

    public long gameTime() {
        return gameTime;
    }

    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    public void putChunk(BedrockChunk chunk) {
        if (chunk != null) {
            chunks.put(chunkKey(chunk.chunkX(), chunk.chunkZ()), chunk);
        }
    }

    public BedrockChunk chunk(int chunkX, int chunkZ) {
        return chunks.get(chunkKey(chunkX, chunkZ));
    }

    public void removeChunk(int chunkX, int chunkZ) {
        chunks.remove(chunkKey(chunkX, chunkZ));
    }

    public void setBlock(int worldX, int worldY, int worldZ,
                         BedrockBlockState state) {
        final BedrockChunk chunk = chunk(Math.floorDiv(worldX, 16),
                Math.floorDiv(worldZ, 16));
        if (chunk != null) {
            chunk.set(worldX, worldY, worldZ, state);
            pendingBlockUpdates.add(new BlockUpdate(worldX, worldY, worldZ,
                    state == null ? BedrockBlockState.air() : state));
        }
    }

    public Queue<BlockUpdate> pendingBlockUpdates() {
        return pendingBlockUpdates;
    }

    public Collection<BedrockChunk> chunks() {
        return chunks.values();
    }

    public int chunkCount() {
        return chunks.size();
    }

    public void putEntity(BedrockEntityState entity) {
        if (entity != null) {
            entities.put(entity.runtimeId(), entity);
        }
    }

    public BedrockEntityState entity(long runtimeId) {
        return entities.get(runtimeId);
    }

    public void removeEntity(long runtimeId) {
        entities.remove(runtimeId);
    }

    public Collection<BedrockEntityState> entities() {
        return entities.values();
    }

    public void clear() {
        chunks.clear();
        entities.clear();
        pendingBlockUpdates.clear();
        gameTime = 0;
    }

    public record BlockUpdate(int x, int y, int z, BedrockBlockState state) {
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}