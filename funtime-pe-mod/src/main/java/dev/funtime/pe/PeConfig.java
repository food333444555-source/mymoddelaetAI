package dev.funtime.pe;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class PeConfig {
    static String serverName = "FUNTIME";
    static String host = "mc.funtime.su";
    static int port = 19132;

    private PeConfig() {
    }

    static void load() {
        final Path file = file();
        if (!Files.exists(file)) {
            return;
        }

        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            serverName = properties.getProperty("serverName", serverName);
            host = properties.getProperty("host", host);
            port = parsePort(properties.getProperty("port"), port);
        } catch (IOException ignored) {
            // A broken optional config should not prevent Minecraft from starting.
        }
    }

    static void save() {
        final Properties properties = new Properties();
        properties.setProperty("serverName", serverName);
        properties.setProperty("host", host);
        properties.setProperty("port", Integer.toString(port));

        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                properties.store(writer, "FUNTIME PE Bridge");
            }
        } catch (IOException ignored) {
            // The connection still works for this session if config persistence fails.
        }
    }

    private static int parsePort(String value, int fallback) {
        try {
            final int parsed = Integer.parseInt(value);
            return parsed >= 1 && parsed <= 65535 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("funtimepe.properties");
    }
}