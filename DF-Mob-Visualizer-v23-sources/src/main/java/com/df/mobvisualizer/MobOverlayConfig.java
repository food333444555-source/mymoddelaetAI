package com.df.mobvisualizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MobOverlayConfig {
    // ===== ОСНОВНЫЕ =====
    public boolean enabled = false;
    public int scanIntervalTicks = 5;
    public double renderDistanceChunks = 12.0;
    
    // ===== HUD =====
    public boolean showHud = false;
    public float hudScale = 1.0f;
    public float hudTextScale = 1.0f;
    public int hudWidth = 420;
    public int hudX = 8;
    public int hudY = 8;
    public int hudSortMode = 0;
    public boolean showPlayers = true;
    public boolean includeOtherEntities = false;
    
    // ===== ЧАНКИ =====
    public boolean showChunkOverlay = false;
    public boolean showChunkFill = true;
    public float chunkOpacity = 0.55f;
    public float chunkFillStrength = 1.0f;
    public float chunkBorderOpacity = 0.95f;
    public boolean markOnlyRuleChunks = true;
    public String chunkColorRules = "id<50=#C855E8FF;id<10000=#FF7A0000;percent<5=#FF7A0000;percent<20=#FFFF2020;percent<30=#FFFF7A00;percent<50=#FFFFB000";
    public double chunkYOffset = 0.5;
    public double chunkHeight = 1.0;
    
    // ===== ЦВЕТА ПО ID =====
    public int purpleIdLimit = 10_000;
    public int purpleColor = 0xC855E8FF;
    public int darkRedColor = 0xFF7A0000;
    public int redColor = 0xFFFF2020;
    public int darkOrangeColor = 0xFFFF7A00;
    public int orangeColor = 0xFFFFB000;
    public double darkRedPercent = 5.0;
    public double redPercent = 20.0;
    public double darkOrangePercent = 30.0;
    public double orangePercent = 50.0;
    public String idColorRules = "id<=10001=#C855E8FF";
    public String percentColorRules = "percent<30=#FFFF2020;percent<50=#FFFFB000";
    
    // ===== ИНДИВИДУАЛЬНЫЕ ЦВЕТА =====
    public String customMobColors = "";
    public int chargedCreeperColor = 0xFFB36BFF;
    public int alertColor = 0xFFFFD34E;
    public int hurtColor = 0xFFFF3030;
    public int hurtStarColor = 0xFFFFAA00;
    
    // ===== ALERT =====
    public boolean alertEnabled = true;
    public String alertEntityTypes = "minecraft:zombie, minecraft:creeper, minecraft:skeleton";
    // per-mob override: if a mob is present here, it will always be added to session when alerted
    public String alertSessionEntityTypes = "";
    public int alertMode = 0;
    public int alertGap = 100_000;
    public double alertPercent = 80.0;
    public boolean alertAddToSession = true;
    public boolean alertCenter = true;
    public boolean alertHighlight = true;
    
    // ===== HURT =====
    public boolean hurtEnabled = true;
    public boolean hurtAddToSession = true;
    public boolean hurtCenter = true;
    public boolean hurtHighlight = true;
    
    // ===== RETURNED =====
    public boolean returnedEnabled = true;
    public String returnedEntityTypes = "minecraft:zombie, minecraft:creeper, minecraft:skeleton";
    // per-mob override for returned
    public String returnedSessionEntityTypes = "";
    public boolean returnedAddToSession = true;
    public boolean returnedCenter = true;
    public boolean returnedHighlight = true;
    
    // ===== СЕССИЯ =====
    public boolean sessionEnabled = true;
    public boolean persistSession = true;
    public String pinnedEntityTypes = "";
    public boolean pinHurtMobs = true;
    public boolean pinLowIds = true;
    
    // ===== ПОДСВЕТКА (НОВАЯ) =====
    public boolean seeThroughMobs = false;
    public boolean highlightHurt = true;
    public boolean highlightAlert = true;
    public boolean highlightReturned = true;
    public boolean highlightCharged = true;
    public boolean highlightRenamed = false;
    public boolean highlightPlayers = false;
    public boolean highlightAll = false;
    // per-mob override for highlight
    public String highlightSessionEntityTypes = "";
    
    // ===== ПОДСВЕТКА (СТАРАЯ ДЛЯ СОВМЕСТИМОСТИ) =====
    public boolean highlightSessionMobs = false;
    public boolean highlightAlertMobs = false;
    public boolean highlightLowIds = false;
    public boolean highlightHurtMobs = false;
    public boolean highlightHostileMobs = false;
    public String highlightEntityTypes = "";
    
    // ===== ЦЕНТРИРОВАНИЕ =====
    public boolean centerAlertMobs = true;
    public boolean centerReturnedMobs = true;
    public boolean centerHurtMobs = true;
    public boolean centerPlayers = false;
    // list of entity types for centering
    public String centerEntityTypes = "";
    // per-mob override for center
    public String centerSessionEntityTypes = "";
    
    // ===== ЦЕНТРИРОВАНИЕ (СТАРОЕ) =====
    public boolean centerSessionMobs = true;
    public boolean centerLowIds = true;
    public boolean centerHostileMobs = true;
    
    // ===== ПРОЦЕНТНЫЕ ЛИМИТЫ =====
    public double sessionPercentLimit = 10.0;
    public double highlightPercentLimit = 30.0;
    public double centerPercentLimit = 20.0;
    
    // ===== СТАРЫЕ ПОЛЯ =====
    public boolean hostileOnly = false;
    public boolean pinHostileMobs = false;
    public boolean pinPlayers = true;
    
    // ===== ЦВЕТА ДЛЯ ПОДСВЕТКИ =====
    public int highlightHurtColor = 0xFFFF3030;
    public int highlightAlertColor = 0xFFC855E8;
    public int highlightReturnedColor = 0xFFFFD34E;
    public int highlightChargedColor = 0xFFB36BFF;
    public int highlightRenamedColor = 0xFFFFFFFF;
    public int highlightPlayerColor = 0xFF55CCFF;
    
    // ===== КЛАВИШИ =====
    public int hudKey = 297;
    public int chunksKey = 298;
    public int mobHighlightsKey = 296;
    public int settingsKey = 299;
    public int clearSessionKey = 294;
    public int clearChunksKey = 295;
    public int hudScanCode = 0;
    public int chunksScanCode = 0;
    public int mobHighlightsScanCode = 0;
    public int settingsScanCode = 0;
    public int clearSessionScanCode = 0;
    public int clearChunksScanCode = 0;
    
    // ===== JSON =====
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("df-mob-visualizer.json");

    public static MobOverlayConfig load() {
        if (!Files.exists(FILE)) return new MobOverlayConfig();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            MobOverlayConfig config = GSON.fromJson(reader, MobOverlayConfig.class);
            return config == null ? new MobOverlayConfig() : config;
        } catch (Exception e) {
            System.err.println("[DF Mob Visualizer] Failed to load config: " + e.getMessage());
            return new MobOverlayConfig();
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException ignored) {
        }
    }

    public void normalize() {
        chunkYOffset = Math.max(-10.0, Math.min(200.0, chunkYOffset));
        chunkHeight = Math.max(0.0, Math.min(50.0, chunkHeight));
        chunkOpacity = Math.max(0.0f, Math.min(1.0f, chunkOpacity));
        chunkFillStrength = Math.max(0.1f, Math.min(3.0f, chunkFillStrength));
        chunkBorderOpacity = Math.max(0.0f, Math.min(1.0f, chunkBorderOpacity));
        renderDistanceChunks = Math.max(1.0, Math.min(64.0, renderDistanceChunks));
        hudScale = Math.max(0.5f, Math.min(2.0f, hudScale));
        hudTextScale = Math.max(0.5f, Math.min(2.0f, hudTextScale));
        hudWidth = Math.max(220, Math.min(1200, hudWidth));
        hudX = Math.max(0, hudX);
        hudY = Math.max(0, hudY);
        scanIntervalTicks = Math.max(1, Math.min(100, scanIntervalTicks));
        purpleIdLimit = Math.max(0, purpleIdLimit);
        darkRedPercent = clampPercent(darkRedPercent);
        redPercent = clampPercent(redPercent);
        darkOrangePercent = clampPercent(darkOrangePercent);
        orangePercent = clampPercent(orangePercent);
        alertGap = Math.max(0, alertGap);
        alertPercent = clampPercent(alertPercent);
        alertMode = alertMode == 1 ? 1 : 0;
        sessionPercentLimit = clampPercent(sessionPercentLimit);
        highlightPercentLimit = clampPercent(highlightPercentLimit);
        centerPercentLimit = clampPercent(centerPercentLimit);
        if (alertEntityTypes == null) alertEntityTypes = "";
        if (returnedEntityTypes == null) returnedEntityTypes = "";
        if (pinnedEntityTypes == null) pinnedEntityTypes = "";
        if (chunkColorRules == null) chunkColorRules = "";
        if (idColorRules == null) idColorRules = "";
        if (percentColorRules == null) percentColorRules = "";
        if (customMobColors == null) customMobColors = "";
        if (highlightEntityTypes == null) highlightEntityTypes = "";
        if (centerEntityTypes == null) centerEntityTypes = "";
        if (alertSessionEntityTypes == null) alertSessionEntityTypes = "";
        if (returnedSessionEntityTypes == null) returnedSessionEntityTypes = "";
        if (highlightSessionEntityTypes == null) highlightSessionEntityTypes = "";
        if (centerSessionEntityTypes == null) centerSessionEntityTypes = "";
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
