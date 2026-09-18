package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Resolves Java-facing item stacks to the native Minecraft item registry.
 */
public final class JavaItemMapper {
    private static final Map<String, String> ALIASES = Map.of(
            "minecraft:wood", "minecraft:oak_log",
            "minecraft:planks", "minecraft:oak_planks",
            "minecraft:empty", "minecraft:air"
    );

    private JavaItemMapper() {
    }

    public static ItemStack map(JavaItemStack source) {
        if (source == null || source.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final String javaName = ALIASES.getOrDefault(source.itemId(), source.itemId());
        final Item item = Registries.ITEM.get(Identifier.of(javaName));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, source.count());
    }
}