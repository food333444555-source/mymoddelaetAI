package dev.funtime.pe;

public enum BedrockVersion {
    BEDROCK_1_21_50("1.21.50", 766),
    DEFAULT("1.21.50", 766);

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
}