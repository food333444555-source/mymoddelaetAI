package ru.food333444555.mymoddelaetai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MobColorManager {
    private static final MobColorManager INSTANCE = new MobColorManager();
    private static final Path CONFIG_PATH = Paths.get("config", "mobs_colors.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, MobSettings> mobs = new HashMap<>();
    public MobSettings defaults = new MobSettings();

    private MobColorManager() { }

    public static MobColorManager getInstance() {
        return INSTANCE;
    }

    public void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                    JsonObject root = gson.fromJson(r, JsonObject.class);
                    if (root.has("defaults")) {
                        defaults = gson.fromJson(root.get("defaults"), MobSettings.class);
                    }
                    if (root.has("mobs")) {
                        Type mapType = new TypeToken<Map<String, MobSettings>>(){}.getType();
                        mobs = gson.fromJson(root.get("mobs"), mapType);
                    }
                }
            } else {
                // create default file
                saveConfig();
            }
        } catch (Exception e) {
            System.err.println("Failed to load mobs_colors.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            root.add("defaults", gson.toJsonTree(defaults));
            root.add("mobs", gson.toJsonTree(mobs));
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            System.err.println("Failed to save mobs_colors.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public MobSettings getSettingsForEntity(Entity entity) {
        if (entity == null) return defaults;
        EntityType<?> type = entity.getType();
        String id = getEntityId(type);
        MobSettings s = mobs.get(id);
        if (s == null) {
            // return a copy of defaults with minimal fields
            return defaults;
        }
        return s;
    }

    public MobSettings getSettingsById(String id) {
        return mobs.getOrDefault(id, defaults);
    }

    public void setSettings(String id, MobSettings settings) {
        mobs.put(id, settings);
    }

    public void ensureMobExists(String id) {
        mobs.putIfAbsent(id, new MobSettings());
    }

    public void autoFillFromRegistry() {
        // Best-effort: try to add all known entity types as keys with default values
        try {
            for (EntityType<?> et : EntityType.TYPES) {
                String id = getEntityId(et);
                if (id != null && !mobs.containsKey(id)) {
                    mobs.put(id, new MobSettings());
                }
            }
        } catch (Throwable t) {
            // older/newer mappings may differ; ignore if fails
        }
    }

    private String getEntityId(EntityType<?> type) {
        try {
            if (type == null) return null;
            if (type.getRegistryName() != null) return type.getRegistryName().toString();
        } catch (Throwable t) {
            // fallback
        }
        // As fallback use toString
        return type.toString();
    }
}
