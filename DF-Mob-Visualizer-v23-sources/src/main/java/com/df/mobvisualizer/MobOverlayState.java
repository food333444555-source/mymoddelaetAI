package com.df.mobvisualizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class MobOverlayState {
    private final MobOverlayConfig config;
    private final Map<Integer, TrackedMob> mobs = new HashMap<>();
    private final Map<Integer, TrackedMob> session = new HashMap<>();
    private final Map<Long, ChunkMark> chunks = new HashMap<>();
    private final Set<Integer> returnedIds = new HashSet<>();
    private int maxId;
    private int currentMaxId;
    private boolean sessionDirty;
    private boolean chunksDirty;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("df-mob-visualizer-chunks.json");
    private static final Path SESSION_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("df-mob-visualizer-session.json");
    private static final Path MAX_ID_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("df-mob-visualizer-max-id.json");

    public MobOverlayState(MobOverlayConfig config) {
        this.config = config;
        loadChunks();
        loadSession();
        loadMaxId();
    }

    public synchronized void beginLiveScan(int currentMaxId) {
        mobs.clear();
        this.currentMaxId = currentMaxId;
        if (currentMaxId > maxId) {
            maxId = currentMaxId;
            saveMaxId();
        }
    }

    public synchronized void accept(TrackedMob mob) {
        mobs.put(mob.id(), mob);
        
        boolean wasInSession = session.containsKey(mob.id());
        boolean isReturned = returnedIds.contains(mob.id());
        
        if (isReturned) {
            returnedIds.remove(mob.id());
            if (config.returnedEnabled && matchesReturnedType(mob.type())) {
                session.put(mob.id(), mob);
                sessionDirty = true;
            }
        }
        
        boolean explicitType = pinnedTypes().stream().anyMatch(type ->
                mob.type().equalsIgnoreCase(type) || mob.type().toLowerCase(Locale.ROOT).endsWith(":" + type));
        
        boolean pin = config.sessionEnabled
                && ((config.alertEnabled && mob.alert() && config.alertAddToSession)
                || (config.hurtEnabled && mob.hurt() && config.hurtAddToSession)
                || (config.returnedEnabled && mob.returned() && config.returnedAddToSession)
                || explicitType);
        
        if (pin || session.containsKey(mob.id())) {
            TrackedMob previous = session.put(mob.id(), mob);
            if (previous == null || !previous.equals(mob)) sessionDirty = true;
        }
        
        Integer ruleColor = MobColors.chunkColor(mob.id(), currentMaxId, config);
        if (ruleColor == null) return;
        long key = chunkKey(mob.chunkX(), mob.chunkZ());
        ChunkMark old = chunks.get(key);
        int color = ruleColor;
        boolean alert = mob.alert() || (old != null && old.alert());
        if (old != null && !old.ring() && isMoreSignificant(old.color(), color)) {
            color = old.color();
        }
        ChunkMark next = new ChunkMark(mob.chunkX(), mob.chunkZ(), color, alert, false,
                System.currentTimeMillis());
        if (old == null || old.color() != next.color() || old.alert() != next.alert()) {
            chunks.put(key, next);
            chunksDirty = true;
        }
        addNeighborRing(mob.chunkX(), mob.chunkZ(), color);
    }

    private void addNeighborRing(int centerX, int centerZ, int sourceColor) {
        int ringColor = (sourceColor & 0x00ffffff) | 0x70000000;
        long now = System.currentTimeMillis();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int x = centerX + dx;
                int z = centerZ + dz;
                long key = chunkKey(x, z);
                if (chunks.containsKey(key)) continue;
                chunks.put(key, new ChunkMark(x, z, ringColor, false, true, now));
                chunksDirty = true;
            }
        }
    }

    public synchronized Collection<TrackedMob> visibleMobs() {
        ArrayList<TrackedMob> result = new ArrayList<>(mobs.values());
        result.removeIf(TrackedMob::player);
        if (config.hudSortMode == 1) {
            result.sort(Comparator.comparing(TrackedMob::type).thenComparingInt(TrackedMob::id));
        } else if (config.hudSortMode == 2) {
            result.sort(Comparator.<TrackedMob>comparingInt(mob -> mob.player() ? 0 : 1)
                    .thenComparingInt(TrackedMob::id));
        } else {
            result.sort(Comparator.comparingInt(TrackedMob::id));
        }
        return result;
    }

    public synchronized Collection<TrackedMob> visiblePlayers() {
        ArrayList<TrackedMob> result = new ArrayList<>();
        for (TrackedMob mob : mobs.values()) {
            if (mob.player()) result.add(mob);
        }
        result.sort(Comparator.comparingInt(TrackedMob::id));
        return result;
    }

    public synchronized boolean isInSession(int id) {
        return session.containsKey(id);
    }

    public synchronized boolean isReturned(int id) {
        return returnedIds.contains(id);
    }

    public synchronized Collection<TrackedMob> visibleSession() {
        ArrayList<TrackedMob> result = new ArrayList<>(session.values());
        result.sort(Comparator.comparingInt(TrackedMob::id));
        return result;
    }

    public synchronized int currentMobCount() {
        int count = 0;
        for (TrackedMob mob : mobs.values()) {
            if (!mob.player()) count++;
        }
        return count;
    }

    public synchronized int sessionCount() {
        return session.size();
    }

    public synchronized int maxSeenId() {
        return maxId;
    }

    public synchronized int currentMaxId() {
        return currentMaxId;
    }

    public synchronized Collection<ChunkMark> visibleChunks() {
        return new ArrayList<>(chunks.values());
    }

    public synchronized int maxId() {
        return maxId;
    }

    public synchronized void clearLiveMobs() {
        mobs.clear();
    }

    public synchronized void clearSession() {
        session.clear();
        returnedIds.clear();
        sessionDirty = false;
        saveSession();
    }

    public synchronized void saveSessionIfDirty() {
        if (!sessionDirty || !config.persistSession) return;
        sessionDirty = false;
        saveSession();
    }

    public synchronized void clearChunks() {
        chunks.clear();
        chunksDirty = true;
        saveChunks();
    }

    public synchronized void clearMaxId() {
        maxId = 0;
        try {
            Files.deleteIfExists(MAX_ID_FILE);
        } catch (Exception ignored) {
        }
    }

    private Set<String> pinnedTypes() {
        if (config.pinnedEntityTypes == null || config.pinnedEntityTypes.isBlank()) return Set.of();
        return Arrays.stream(config.pinnedEntityTypes.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "minecraft:"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean matchesReturnedType(String type) {
        if (config.returnedEntityTypes == null || config.returnedEntityTypes.isBlank()) return true;
        String normalized = type.toLowerCase(Locale.ROOT);
        for (String raw : config.returnedEntityTypes.split(",")) {
            String wanted = raw.trim().toLowerCase(Locale.ROOT);
            if (wanted.isBlank()) continue;
            if (!wanted.contains(":")) wanted = "minecraft:" + wanted;
            if (normalized.equals(wanted) || normalized.endsWith(":" + wanted.substring(wanted.indexOf(':') + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMoreSignificant(int oldColor, int newColor) {
        return severity(oldColor) > severity(newColor);
    }

    private static int severity(int color) {
        int rgb = color & 0x00ffffff;
        if (rgb == 0x9e9e9e) return 0;
        if ((rgb & 0xff0000) > 0 && (rgb & 0x00ff00) < 0x5000) return 3;
        if ((rgb & 0xff0000) > 0 && (rgb & 0x00ff00) > 0x5000) return 2;
        return 1;
    }

    private synchronized void saveSession() {
        try (Writer writer = Files.newBufferedWriter(SESSION_FILE)) {
            GSON.toJson(new ArrayList<>(session.values()), writer);
        } catch (Exception ignored) {
        }
    }

    private synchronized void loadSession() {
        if (!config.persistSession || !Files.exists(SESSION_FILE)) return;
        try (Reader reader = Files.newBufferedReader(SESSION_FILE)) {
            List<TrackedMob> saved = GSON.fromJson(reader,
                    new TypeToken<List<TrackedMob>>() {}.getType());
            if (saved != null) {
                for (TrackedMob mob : saved) session.put(mob.id(), mob);
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void loadMaxId() {
        if (!Files.exists(MAX_ID_FILE)) return;
        try (Reader reader = Files.newBufferedReader(MAX_ID_FILE)) {
            Integer saved = GSON.fromJson(reader, Integer.class);
            if (saved != null) maxId = Math.max(0, saved);
        } catch (Exception ignored) {
        }
    }

    private synchronized void saveMaxId() {
        try (Writer writer = Files.newBufferedWriter(MAX_ID_FILE)) {
            GSON.toJson(maxId, writer);
        } catch (Exception ignored) {
        }
    }

    public static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public synchronized void saveChunks() {
        if (!chunksDirty && Files.exists(FILE)) return;
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(new ArrayList<>(chunks.values()), writer);
            chunksDirty = false;
        } catch (Exception ignored) {
        }
    }

    private synchronized void loadChunks() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            List<ChunkMark> saved = GSON.fromJson(reader,
                    new TypeToken<List<ChunkMark>>() {}.getType());
            if (saved != null) {
                for (ChunkMark mark : saved) {
                    chunks.put(chunkKey(mark.chunkX(), mark.chunkZ()), mark);
                }
                chunksDirty = false;
            }
        } catch (Exception ignored) {
        }
    }
}
