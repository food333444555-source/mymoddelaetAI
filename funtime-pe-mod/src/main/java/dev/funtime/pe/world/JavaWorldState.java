package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Java-facing world model. Bedrock transport objects do not cross this
 * boundary into the presentation layer.
 */
public final class JavaWorldState {
    private final BedrockWorldState source;
    private final ClientWorld clientWorld;

    public JavaWorldState(BedrockWorldState source) {
        this.source = source;
        this.clientWorld = new ClientWorld(source);
    }

    public ClientWorld clientWorld() {
        return clientWorld;
    }

    public double playerX() {
        return clientWorld.playerX();
    }

    public double playerY() {
        return clientWorld.playerY();
    }

    public double playerZ() {
        return clientWorld.playerZ();
    }

    public float playerYaw() {
        return clientWorld.playerYaw();
    }

    public float playerPitch() {
        return clientWorld.playerPitch();
    }

    public long gameTime() {
        return clientWorld.getTime();
    }

    public long localRuntimeId() {
        return source.localRuntimeId();
    }

    public BedrockWorldState sourceState() {
        return source;
    }

    public int chunkCount() {
        return clientWorld.getChunkManager().loadedChunkCount();
    }

    public Collection<JavaChunkView> chunks() {
        return clientWorld.loadedChunks().stream()
                .map(JavaChunkView::new)
                .collect(Collectors.toUnmodifiableList());
    }

    public Collection<JavaEntityView> entities() {
        return source.entities().stream().map(JavaEntityView::from)
                .collect(Collectors.toUnmodifiableList());
    }

    public void moveLocalPlayer(double x, double y, double z, float yaw, float pitch) {
        source.moveLocalPlayer(x, y, z, yaw, pitch);
    }
}