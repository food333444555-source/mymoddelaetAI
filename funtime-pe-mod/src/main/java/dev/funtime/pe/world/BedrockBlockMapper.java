package dev.funtime.pe.world;

import java.util.Map;

public final class BedrockBlockMapper {
    private static final Map<Integer, BedrockBlockState> BUILTIN = Map.of(
            0, BedrockBlockState.air(),
            1, new BedrockBlockState("minecraft:stone", Map.of()),
            2, new BedrockBlockState("minecraft:grass_block", Map.of()),
            3, new BedrockBlockState("minecraft:dirt", Map.of()),
            4, new BedrockBlockState("minecraft:cobblestone", Map.of()),
            5, new BedrockBlockState("minecraft:planks", Map.of())
    );

    private BedrockBlockMapper() {
    }

    public static BedrockBlockState mapRuntimeId(int runtimeId) {
        return BUILTIN.getOrDefault(runtimeId, BedrockBlockState.air());
    }
}