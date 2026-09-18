package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import dev.funtime.pe.protocol.BedrockClient;
import dev.funtime.pe.world.ClientWorld;
import dev.funtime.pe.world.JavaInventoryState;
import dev.funtime.pe.world.JavaWorldState;

import java.io.IOException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Java-facing play session. The underlying Bedrock client is deliberately
 * hidden here so the screen can behave like a Java multiplayer screen.
 */
public final class JavaPlaySession implements AutoCloseable {
    private final BedrockClient network;
    private final JavaWorldState world;
    private final JavaInventoryState inventory;

    public JavaPlaySession(BedrockClient network) {
        this.network = network;
        this.world = new JavaWorldState(network.worldState());
        this.inventory = network.inventory();
    }

    public JavaWorldState world() {
        return world;
    }

    public ClientWorld clientWorld() {
        return world.clientWorld();
    }

    public JavaInventoryState inventory() {
        return inventory;
    }

    public boolean isJoined() {
        return network.isJoined();
    }

    public void sendMove(double x, double y, double z, float yaw, float pitch,
                         boolean onGround, long tick) throws IOException {
        network.sendMove(x, y, z, yaw, pitch, onGround, tick);
    }

    public void sendChat(String message) throws IOException {
        network.sendChat(message);
    }

    public void sendHotbarSelect(int slot) throws IOException {
        network.sendHotbarSelect(slot);
    }

    public void sendUseItemOnBlock(BlockPos position, Direction face,
                                   double clickX, double clickY, double clickZ)
            throws IOException {
        network.sendUseItemOnBlock(position, face, clickX, clickY, clickZ);
    }

    public void sendContainerClose(int containerId) throws IOException {
        network.sendContainerClose(containerId);
    }

    public void sendContainerSwap(int containerId, int firstSlot, int secondSlot)
            throws IOException {
        network.sendContainerSwap(containerId, firstSlot, secondSlot);
    }

    public void sendPlayerAction(int action, int x, int y, int z, int face)
            throws IOException {
        network.sendPlayerAction(action, x, y, z, face);
    }

    public void sendBlockBreakStart(int x, int y, int z, int face)
            throws IOException {
        network.sendBlockBreakStart(x, y, z, face);
    }

    public void sendBlockBreakAbort(int x, int y, int z, int face)
            throws IOException {
        network.sendBlockBreakAbort(x, y, z, face);
    }

    public void sendBlockBreakStop(int x, int y, int z, int face)
            throws IOException {
        network.sendBlockBreakStop(x, y, z, face);
    }

    @Override
    public void close() {
        network.close();
    }
}