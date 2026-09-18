package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

public enum BedrockVersion {
    BEDROCK_26_50("26.50", 2193),
    DEFAULT("26.50", 2193);

    private final String name;
    private final int protocolVersion;

    BedrockVersion(String name, int protocolVersion) {
        this.name = name;
        this.protocolVersion = protocolVersion;
    }

    public String displayName() {
        return name;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public static BedrockVersion fromProtocol(int protocolVersion) {
        for (BedrockVersion version : values()) {
            if (version.protocolVersion == protocolVersion) {
                return version;
            }
        }
        return null;
    }

    public static boolean isSupported(int protocolVersion) {
        return fromProtocol(protocolVersion) != null;
    }

    public static String displayName(int protocolVersion) {
        final BedrockVersion version = fromProtocol(protocolVersion);
        return version == null ? "неизвестная Bedrock-версия" : version.displayName();
    }
}