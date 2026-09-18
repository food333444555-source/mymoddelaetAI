package dev.funtime.pe;

/**
 * Source version metadata for the Bedrock-to-Java bridge.
 *
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */
public final class SourceVersion {
    public static final String JAVA_VERSION = "1.21.4";
    public static final String BEDROCK_VERSION = "26.50";
    public static final int BEDROCK_PROTOCOL = 2193;
    public static final String MOD_VERSION = "0.4.1";

    private SourceVersion() {
    }

    public static String javaTitle() {
        return "Minecraft Java " + JAVA_VERSION;
    }

    public static String bridgeTitle() {
        return "Java " + JAVA_VERSION + " • Bedrock " + BEDROCK_VERSION;
    }
}