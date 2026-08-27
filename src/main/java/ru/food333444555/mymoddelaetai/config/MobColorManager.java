package ru.food333444555.mymoddelaetai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityType;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                autoFillFromRegistry();
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

    public MobSettings getSettingsForId(String id) {
        return mobs.getOrDefault(id, defaults);
    }

    public MobSettings getSettingsForEntityType(EntityType<?> type) {
        String id = Registry.ENTITY_TYPE.getId(type).toString();
        return getSettingsForId(id);
    }

    public MobSettings getSettingsForEntity(net.minecraft.entity.Entity entity) {
        if (entity == null) return defaults;
        EntityType<?> type = entity.getType();
        String id = Registry.ENTITY_TYPE.getId(type).toString();
        return getSettingsForId(id);
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
        try {
            for (EntityType<?> et : Registry.ENTITY_TYPE) {
                String id = Registry.ENTITY_TYPE.getId(et).toString();
                if (id != null && !mobs.containsKey(id)) {
                    mobs.put(id, new MobSettings());
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public List<String> listAllMobIds() {
        return new ArrayList<>(mobs.keySet());
    }

    public void addEventToMob(String id, String event) {
        MobSettings s = mobs.computeIfAbsent(id, k -> new MobSettings());
        if (s.alertEvents == null) s.alertEvents = new ArrayList<>();
        if (!s.alertEvents.contains(event)) s.alertEvents.add(event);
    }

    public void removeEventFromMob(String id, String event) {
        MobSettings s = mobs.get(id);
        if (s == null || s.alertEvents == null) return;
        s.alertEvents.remove(event);
    }

    public boolean mobHasEvent(String id, String event) {
        MobSettings s = mobs.get(id);
        return s != null && s.alertEvents != null && s.alertEvents.contains(event);
    }

    public List<String> listMobsWithEvent(String event) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, MobSettings> e : mobs.entrySet()) {
            if (e.getValue().alertEvents != null && e.getValue().alertEvents.contains(event)) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
