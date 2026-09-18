package dev.funtime.pe.world;

import java.util.Map;

/**
 * Small version-scoped item runtime registry used until the server's complete
 * item palette is decoded. Unknown runtime ids are retained instead of being
 * silently treated as empty stacks.
 */
public final class BedrockItemMapper {
    private static final Map<Integer, String> BUILTIN = Map.ofEntries(
            Map.entry(0, "minecraft:air"),
            Map.entry(1, "minecraft:stone"),
            Map.entry(2, "minecraft:grass_block"),
            Map.entry(3, "minecraft:dirt"),
            Map.entry(4, "minecraft:cobblestone"),
            Map.entry(5, "minecraft:oak_planks"),
            Map.entry(6, "minecraft:oak_sapling"),
            Map.entry(12, "minecraft:sand"),
            Map.entry(13, "minecraft:gravel"),
            Map.entry(17, "minecraft:oak_log"),
            Map.entry(20, "minecraft:glass"),
            Map.entry(256, "minecraft:iron_shovel"),
            Map.entry(257, "minecraft:iron_pickaxe"),
            Map.entry(258, "minecraft:iron_axe"),
            Map.entry(267, "minecraft:iron_sword"),
            Map.entry(268, "minecraft:wooden_sword"),
            Map.entry(272, "minecraft:stone_sword"),
            Map.entry(278, "minecraft:diamond_pickaxe"),
            Map.entry(260, "minecraft:apple")
    );

    private BedrockItemMapper() {
    }

    public static String name(int runtimeId) {
        return BUILTIN.getOrDefault(runtimeId, "bedrock:item_" + runtimeId);
    }

    public static int runtimeId(String itemId) {
        if (itemId == null) {
            return 0;
        }
        for (Map.Entry<Integer, String> entry : BUILTIN.entrySet()) {
            if (entry.getValue().equals(itemId)) {
                return entry.getKey();
            }
        }
        if (itemId.startsWith("bedrock:item_")) {
            try {
                return Integer.parseInt(itemId.substring("bedrock:item_".length()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}