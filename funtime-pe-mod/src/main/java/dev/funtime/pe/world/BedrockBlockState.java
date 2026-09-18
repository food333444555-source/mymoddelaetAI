package dev.funtime.pe.world;

import java.util.Collections;
import java.util.Map;

public record BedrockBlockState(String name, Map<String, String> properties) {
    public BedrockBlockState {
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(properties);
    }

    public static BedrockBlockState air() {
        return new BedrockBlockState("minecraft:air", Map.of());
    }
}