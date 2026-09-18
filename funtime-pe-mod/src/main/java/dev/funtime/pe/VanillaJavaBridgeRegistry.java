package dev.funtime.pe;

import net.minecraft.client.MinecraftClient;

/**
 * Client-thread owner for the active vanilla Java bridge.
 */
final class VanillaJavaBridgeRegistry {
    private static VanillaJavaBridge active;

    private VanillaJavaBridgeRegistry() {
    }

    static void install(VanillaJavaBridge bridge) {
        close();
        active = bridge;
    }

    static void tick(MinecraftClient client) {
        if (active != null) {
            if (client.world == null) {
                close();
            } else {
                active.tick();
            }
        }
    }

    static void close() {
        if (active != null) {
            active.close();
            active = null;
        }
    }
}