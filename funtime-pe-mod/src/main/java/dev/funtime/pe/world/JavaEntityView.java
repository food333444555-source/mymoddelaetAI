package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.entity.EntityType;

/**
 * Java-facing entity projection used by the Java renderer and HUD.
 */
public record JavaEntityView(long runtimeId, long uniqueId, String type,
                             String name, double x, double y, double z,
                             float yaw, float pitch, boolean player,
                             EntityType<?> javaType) {
    public static JavaEntityView from(BedrockEntityState source) {
        return new JavaEntityView(source.runtimeId(), source.uniqueId(), source.type(),
                source.name(), source.x(), source.y(), source.z(),
                source.yaw(), source.pitch(), source.player(),
                JavaEntityMapper.map(source.type()));
    }
}