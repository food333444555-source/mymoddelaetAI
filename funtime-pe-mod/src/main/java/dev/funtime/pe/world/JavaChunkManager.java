package dev.funtime.pe.world;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java-facing chunk manager.
 *
 * <p>The manager provides the same lookup boundary that a Java world renderer
 * needs: loaded chunks, chunk lookup and immutable snapshots. It intentionally
 * does not expose Bedrock packet objects.</p>
 */
public final class JavaChunkManager {
    private final BedrockWorldState source;

    JavaChunkManager(BedrockWorldState source) {
        this.source = source;
    }

    public WorldChunk getChunk(int chunkX, int chunkZ) {
        final BedrockChunk chunk = source.chunk(chunkX, chunkZ);
        return chunk == null ? null : new WorldChunk(chunk);
    }

    public Collection<WorldChunk> loadedChunks() {
        final Map<Long, WorldChunk> snapshot = new LinkedHashMap<>();
        for (BedrockChunk chunk : source.chunks()) {
            final long key = ((long) chunk.chunkX() << 32) ^ (chunk.chunkZ() & 0xffffffffL);
            snapshot.put(key, new WorldChunk(chunk));
        }
        return Collections.unmodifiableCollection(snapshot.values());
    }

    public int loadedChunkCount() {
        return source.chunkCount();
    }
}