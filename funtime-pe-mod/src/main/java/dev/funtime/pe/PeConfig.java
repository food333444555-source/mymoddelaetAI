package dev.funtime.pe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class PeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "funtimepe.json";

    private static ConfigFile data = new ConfigFile();

    private PeConfig() {
    }

    static void load() {
        final Path file = file();
        if (!Files.exists(file)) {
            data.servers.add(PeServerEntry.createDefault());
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            ConfigFile loaded = GSON.fromJson(reader, ConfigFile.class);
            data = loaded == null ? new ConfigFile() : loaded;
        } catch (IOException | JsonParseException exception) {
            System.err.println("[FUNTIME PE] Cannot read config: " + exception.getMessage());
            data = new ConfigFile();
        }

        if (data.servers == null) {
            data.servers = new ArrayList<>();
        }
        data.servers.removeIf(server -> server == null);
        if (data.servers.isEmpty()) {
            data.servers.add(PeServerEntry.createDefault());
        }
        boolean migrated = false;
        for (PeServerEntry server : data.servers) {
            migrated |= server.normalize();
            int before = server.protocolVersion();
            server.migrateLegacyProtocol();
            migrated |= before != server.protocolVersion();
        }
        if (data.account == null) {
            data.account = new PeAccountState();
        }
        if (migrated) {
            save();
        }
    }

    static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            System.err.println("[FUNTIME PE] Cannot save config: " + exception.getMessage());
        }
    }

    static List<PeServerEntry> servers() {
        return data.servers;
    }

    static PeAccountState account() {
        return data.account;
    }

    static void addServer(PeServerEntry server) {
        data.servers.add(server);
        save();
    }

    static void removeServer(PeServerEntry server) {
        if (server == null) {
            return;
        }
        data.servers.removeIf(candidate -> candidate != null
                && java.util.Objects.equals(candidate.id(), server.id()));
        if (data.servers.isEmpty()) {
            data.servers.add(PeServerEntry.createDefault());
        }
        save();
    }

    static void replaceServers(PeServerEntry... servers) {
        data.servers = new ArrayList<>(Arrays.asList(servers));
        save();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static final class ConfigFile {
        private int version = 1;
        private List<PeServerEntry> servers = new ArrayList<>();
        private PeAccountState account = new PeAccountState();
    }
}