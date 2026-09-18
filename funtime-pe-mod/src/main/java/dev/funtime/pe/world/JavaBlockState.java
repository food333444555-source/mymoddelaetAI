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

/**
 * Java-facing block value. The protocol identity is retained for resource-pack
 * and fallback rendering, while javaState is the state consumed by the Java
 * presentation layer.
 */
public record JavaBlockState(String name, BlockState javaState) {
    public JavaBlockState {
        name = name == null ? "minecraft:air" : name;
        javaState = javaState == null ? Blocks.AIR.getDefaultState() : javaState;
    }

    public boolean isAir() {
        return javaState.isAir();
    }
}