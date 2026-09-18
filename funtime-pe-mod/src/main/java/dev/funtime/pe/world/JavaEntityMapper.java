package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Resolves a Bedrock entity identifier to the Java entity registry.
 */
public final class JavaEntityMapper {
    private static final Map<String, String> ALIASES = Map.of(
            "minecraft:player", "minecraft:player",
            "minecraft:zombie_villager", "minecraft:zombie_villager",
            "minecraft:item", "minecraft:item"
    );

    private JavaEntityMapper() {
    }

    public static EntityType<?> map(String bedrockType) {
        final String javaName = ALIASES.getOrDefault(
                bedrockType == null ? "minecraft:pig" : bedrockType,
                bedrockType == null ? "minecraft:pig" : bedrockType);
        final EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(javaName));
        return type == null ? EntityType.PIG : type;
    }
}