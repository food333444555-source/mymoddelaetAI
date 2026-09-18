package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Converts the protocol-side Bedrock block identity into a Java BlockState.
 *
 * The protocol mapper intentionally stays independent from Minecraft classes.
 * This class is the Java-facing boundary used by the renderer/world bridge.
 * Unknown Bedrock blocks fall back to air until their resource-pack/runtime
 * palette mapping is registered.
 */
public final class JavaBlockMapper {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("minecraft:grass", "minecraft:grass_block"),
            Map.entry("minecraft:wood", "minecraft:oak_log"),
            Map.entry("minecraft:planks", "minecraft:oak_planks"),
            Map.entry("minecraft:still_water", "minecraft:water"),
            Map.entry("minecraft:flowing_water", "minecraft:water"),
            Map.entry("minecraft:still_lava", "minecraft:lava"),
            Map.entry("minecraft:flowing_lava", "minecraft:lava"),
            Map.entry("minecraft:double_plant", "minecraft:sunflower"),
            Map.entry("minecraft:lit_furnace", "minecraft:furnace"),
            Map.entry("minecraft:unlit_furnace", "minecraft:furnace"),
            Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
            Map.entry("minecraft:wooden_trapdoor", "minecraft:oak_trapdoor"),
            Map.entry("minecraft:wooden_button", "minecraft:oak_button"),
            Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"),
            Map.entry("minecraft:torch", "minecraft:torch"),
            Map.entry("minecraft:glowstone", "minecraft:glowstone")
    );

    private JavaBlockMapper() {
    }

    public static BlockState map(BedrockBlockState bedrockState) {
        if (bedrockState == null || bedrockState.name() == null
                || "minecraft:air".equals(bedrockState.name())) {
            return Blocks.AIR.getDefaultState();
        }

        final String javaName = ALIASES.getOrDefault(bedrockState.name(), bedrockState.name());
        try {
            final var block = Registries.BLOCK.get(Identifier.of(javaName));
            return block == null ? Blocks.AIR.getDefaultState() : block.getDefaultState();
        } catch (IllegalArgumentException invalidIdentifier) {
            return Blocks.AIR.getDefaultState();
        }
    }
}