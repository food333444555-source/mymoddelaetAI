package dev.funtime.pe;

import dev.funtime.pe.mixin.ClientPlayNetworkHandlerInvoker;
import dev.funtime.pe.world.ChunkSection;
import dev.funtime.pe.world.BedrockWorldState;
import dev.funtime.pe.world.JavaChunkView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionTypes;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Installs the translated session into Minecraft's real Java client runtime.
 *
 * <p>This is the boundary between the Bedrock transport and vanilla. The
 * client gets a real ClientWorld and ClientPlayerEntity, so vanilla camera,
 * HUD, world renderer and screens can be used. Bedrock remains the wire
 * protocol behind this bridge.</p>
 */
public final class VanillaJavaBridge implements AutoCloseable {
    private static final Map<ClientPlayNetworkHandler, VanillaJavaBridge> ACTIVE =
            new WeakHashMap<>();

    private final MinecraftClient client;
    private final JavaPlaySession session;
    private final ClientConnection javaConnection;
    private final ClientPlayNetworkHandler networkHandler;
    private final ClientWorld world;
    private boolean closed;

    private VanillaJavaBridge(MinecraftClient client, PeServerEntry server,
                              JavaPlaySession session) {
        this.client = client;
        this.session = session;
        this.javaConnection = new ClientConnection(NetworkSide.CLIENTBOUND);
        final ServerInfo serverInfo = new ServerInfo(
                server.name(),
                server.host() + ":" + server.port(),
                ServerInfo.ServerType.OTHER);
        final ClientConnectionState connectionState = new ClientConnectionState(
                client.getTelemetryManager().createWorldSession(
                        false, Duration.ZERO, null),
                serverInfo);
        this.networkHandler = new ClientPlayNetworkHandler(
                client, javaConnection, connectionState);
        this.world = new ClientWorld(
                networkHandler,
                new ClientWorld.Properties(Difficulty.NORMAL, false, false),
                World.OVERWORLD,
                networkHandler.getRegistryManager()
                        .getOrThrow(RegistryKeys.DIMENSION_TYPE)
                        .getOrThrow(DimensionTypes.OVERWORLD),
                10,
                10,
                client.worldRenderer,
                false,
                0L,
                63);
    }

    /**
     * Enters the ordinary Minecraft world lifecycle instead of opening a
     * project-specific play screen.
     */
    public static VanillaJavaBridge enter(MinecraftClient client, PeServerEntry server,
                                           JavaPlaySession session) {
        final VanillaJavaBridge bridge = new VanillaJavaBridge(client, server, session);
        synchronized (ACTIVE) {
            ACTIVE.put(bridge.networkHandler, bridge);
        }
        bridge.startVanillaWorld();
        return bridge;
    }

    private void startVanillaWorld() {
        final VanillaPlayer player = new VanillaPlayer(
                client, world, networkHandler, new StatHandler(), new ClientRecipeBook());
        final var position = session.clientWorld();
        player.refreshPositionAndAngles(
                position.playerX(), position.playerY(), position.playerZ(),
                position.playerYaw(), position.playerPitch());
        ((ClientPlayNetworkHandlerInvoker) networkHandler).funtimepe$startWorldLoading(
                player, world, DownloadingTerrainScreen.WorldEntryReason.OTHER);
        client.setScreen(null);
        syncChunks();
    }

    public ClientPlayNetworkHandler networkHandler() {
        return networkHandler;
    }

    public void tick() {
        if (closed || !session.isJoined() || client.player == null) {
            return;
        }
        final var player = client.player;
        try {
            session.sendMove(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(), player.isOnGround(),
                    client.world == null ? 0L : client.world.getTime());
        } catch (IOException exception) {
            close();
        }
        if (client.world != null && client.world.getTime() % 5L == 0L) {
            syncChunks();
        }
        syncBlockUpdates();
    }

    private void syncChunks() {
        if (closed || client.world != world) {
            return;
        }
        final ClientChunkManager manager = world.getChunkManager();
        for (JavaChunkView javaChunk : session.world().chunks()) {
            final WorldChunk vanillaChunk = manager.getChunk(
                    javaChunk.chunkX(), javaChunk.chunkZ(), ChunkStatus.FULL, true);
            if (vanillaChunk == null) {
                continue;
            }
            for (ChunkSection section : javaChunk.sections()) {
                final int baseY = section.sectionY() * 16;
                for (int y = 0; y < ChunkSection.SIZE; y++) {
                    for (int z = 0; z < ChunkSection.SIZE; z++) {
                        for (int x = 0; x < ChunkSection.SIZE; x++) {
                            final var state = section.getBlockState(x, y, z);
                            if (state.isAir()) {
                                continue;
                            }
                            vanillaChunk.setBlockState(
                                    new BlockPos(
                                            javaChunk.chunkX() * 16 + x,
                                            baseY + y,
                                            javaChunk.chunkZ() * 16 + z),
                                    state.javaState(),
                                    false);
                        }
                    }
                }
            }
        }
    }

    private void syncBlockUpdates() {
        if (closed || client.world != world) {
            return;
        }
        final BedrockWorldState source = session.world().sourceState();
        final ClientChunkManager manager = world.getChunkManager();
        BedrockWorldState.BlockUpdate update;
        while ((update = source.pendingBlockUpdates().poll()) != null) {
            final WorldChunk chunk = manager.getChunk(
                    Math.floorDiv(update.x(), 16),
                    Math.floorDiv(update.z(), 16),
                    ChunkStatus.FULL, true);
            if (chunk == null) {
                continue;
            }
            chunk.setBlockState(
                    new BlockPos(update.x(), update.y(), update.z()),
                    dev.funtime.pe.world.JavaBlockMapper.map(update.state()),
                    false);
        }
    }

    public static boolean sendChat(ClientPlayNetworkHandler handler, String message) {
        final VanillaJavaBridge bridge;
        synchronized (ACTIVE) {
            bridge = ACTIVE.get(handler);
        }
        if (bridge == null || bridge.closed) {
            return false;
        }
        try {
            bridge.session.sendChat(message);
        } catch (IOException exception) {
            bridge.close();
        }
        return true;
    }

    public static boolean sendBlockAction(BlockPos position, Direction direction, int action) {
        final VanillaJavaBridge bridge;
        synchronized (ACTIVE) {
            bridge = ACTIVE.values().stream().findFirst().orElse(null);
        }
        if (bridge == null || bridge.closed || position == null || direction == null) {
            return false;
        }
        try {
            final int face = switch (direction) {
                case DOWN -> 0;
                case UP -> 1;
                case NORTH -> 2;
                case SOUTH -> 3;
                case WEST -> 4;
                case EAST -> 5;
            };
            bridge.session.sendPlayerAction(action, position.getX(), position.getY(),
                    position.getZ(), face);
            return true;
        } catch (IOException exception) {
            bridge.close();
            return true;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        synchronized (ACTIVE) {
            ACTIVE.remove(networkHandler);
        }
        try {
            javaConnection.disconnect(net.minecraft.text.Text.literal("Соединение закрыто"));
        } catch (RuntimeException ignored) {
            // The local Java connection may not have a Netty channel.
        }
        session.close();
    }

    private static final class VanillaPlayer extends net.minecraft.client.network.ClientPlayerEntity {
        private VanillaPlayer(MinecraftClient client, ClientWorld world,
                              ClientPlayNetworkHandler handler,
                              StatHandler stats, ClientRecipeBook recipeBook) {
            super(client, world, handler, stats, recipeBook, false, false);
        }
    }
}
