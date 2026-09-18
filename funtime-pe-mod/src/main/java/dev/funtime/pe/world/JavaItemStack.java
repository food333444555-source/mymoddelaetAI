package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

/**
 * Java-facing item stack. The Bedrock item identity is retained until the
 * version-specific item registry is attached to the session.
 */
public record JavaItemStack(String itemId, int count, int damage) {
    public JavaItemStack {
        itemId = itemId == null ? "minecraft:air" : itemId;
        count = Math.max(0, count);
        damage = Math.max(0, damage);
    }

    public static JavaItemStack empty() {
        return new JavaItemStack("minecraft:air", 0, 0);
    }

    public boolean isEmpty() {
        return count <= 0 || "minecraft:air".equals(itemId);
    }
}