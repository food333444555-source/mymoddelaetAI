package dev.funtime.pe.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Map<Integer, BedrockBlockState> RUNTIME = new ConcurrentHashMap<>(BUILTIN);

    public static BedrockBlockState mapRuntimeId(int runtimeId) {
        return RUNTIME.getOrDefault(runtimeId, BedrockBlockState.air());
    }

    public static void registerRuntimeId(int runtimeId, BedrockBlockState state) {
        if (runtimeId >= 0 && state != null) {
            RUNTIME.put(runtimeId, state);
        }
    }

    public static void registerRuntimeId(int runtimeId, String name) {
        if (name != null && !name.isBlank()) {
            registerRuntimeId(runtimeId, new BedrockBlockState(name, Map.of()));
        }
    }

    public static BedrockBlockState mapRuntimeIdOrNull(int runtimeId) {
        return RUNTIME.get(runtimeId);
    }

    public static void replaceRuntimePalette(Map<Integer, BedrockBlockState> palette) {
        RUNTIME.clear();
        RUNTIME.putAll(BUILTIN);
        if (palette != null) {
            palette.forEach(BedrockBlockMapper::registerRuntimeId);
        }
    }
}