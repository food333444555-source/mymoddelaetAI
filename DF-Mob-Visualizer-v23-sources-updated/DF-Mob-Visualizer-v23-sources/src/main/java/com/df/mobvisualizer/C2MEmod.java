package com.df.mobvisualizer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class C2MEmod implements ClientModInitializer {
    private static final String CATEGORY = "category.df_mob_visualizer";
    private static final KeyBinding TOGGLE_HUD = new KeyBinding(
            "key.df_mob_visualizer.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY);
    private static final KeyBinding TOGGLE_CHUNKS = new KeyBinding(
            "key.df_mob_visualizer.toggle_chunks",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            CATEGORY);
    private static final KeyBinding OPEN_SETTINGS = new KeyBinding(
            "key.df_mob_visualizer.open_settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            CATEGORY);
    private static final KeyBinding CLEAR_SESSION = new KeyBinding(
            "key.df_mob_visualizer.clear_session",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private static final KeyBinding CLEAR_CHUNKS = new KeyBinding(
            "key.df_mob_visualizer.clear_chunks",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    private MobOverlayConfig config;
    private MobOverlayState state;
    private boolean hudOpen;
    private boolean chunksOpen;
    private int scanCooldown;

    // === CENTER ===
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private long centerLastUpdate = 0;
    private int centerMarkerCount = 0;
    // === END CENTER ===

    private boolean clearSessionScanDown;
    private boolean clearChunksScanDown;
    private final Map<Long, Integer> surfaceHeightCache = new HashMap<>();
    private Object surfaceCacheWorld;
    private long surfaceCacheStamp = Long.MIN_VALUE;

    // === SYSTEM FONT CACHE ===
    private Font systemFont;
    private final Map<String, Integer> systemFontWidths = new HashMap<>();
    private int systemFontHeight = 0;

    @Override
    public void onInitializeClient() {
        config = MobOverlayConfig.load();
        config.normalize();
        applyKeyConfig(config);
        state = new MobOverlayState(config);
        hudOpen = config.showHud;
        chunksOpen = config.showChunkOverlay;

        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD);
        KeyBindingHelper.registerKeyBinding(TOGGLE_CHUNKS);
        KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS);
        KeyBindingHelper.registerKeyBinding(CLEAR_SESSION);
        KeyBindingHelper.registerKeyBinding(CLEAR_CHUNKS);

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::renderChunks);
    }

    private void tick(MinecraftClient client) {
        while (TOGGLE_HUD.wasPressed()) { hudOpen = !hudOpen; config.showHud = hudOpen; config.save(); }
        while (TOGGLE_CHUNKS.wasPressed()) { chunksOpen = !chunksOpen; config.showChunkOverlay = chunksOpen; config.save(); }
        hudOpen = config.showHud;
        chunksOpen = config.showChunkOverlay;

        while (OPEN_SETTINGS.wasPressed()) {
            client.setScreen(new MobSettingsScreenV2(client.currentScreen, config, state));
        }
        while (CLEAR_SESSION.wasPressed()) state.clearSession();
        while (CLEAR_CHUNKS.wasPressed()) state.clearChunks();

        boolean clearSessionDown = scanFallbackDown(client, config.clearSessionKey, config.clearSessionScanCode);
        if (clearSessionDown && !clearSessionScanDown) state.clearSession();
        clearSessionScanDown = clearSessionDown;

        boolean clearChunksDown = scanFallbackDown(client, config.clearChunksKey, config.clearChunksScanCode);
        if (clearChunksDown && !clearChunksScanDown) state.clearChunks();
        clearChunksScanDown = clearChunksDown;

        if (!config.enabled) return;
        if (client.world == null || client.player == null || --scanCooldown > 0) return;
        scanCooldown = config.scanIntervalTicks;

        int currentMaxId = 0;
        for (Entity entity : client.world.getEntities()) {
            currentMaxId = Math.max(currentMaxId, entity.getId());
        }
        state.beginLiveScan(currentMaxId);

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!config.showPlayers && entity.isPlayer()) continue;
            if (!config.includeOtherEntities && !(entity instanceof LivingEntity)) continue;

            int id = entity.getId();
            String typeId = entityTypeId(entity);
            boolean alert = isAlert(entity, id, currentMaxId);
            boolean player = entity.isPlayer();
            boolean hurt = entity instanceof LivingEntity living && living.hurtTime > 0;
            boolean chargedCreeper = entity instanceof CreeperEntity creeper && creeper.isCharged();
            boolean renamed = entity.hasCustomName();
            boolean returned = state.isReturned(id, typeId);
            boolean hurtStar = state.isHurtStar(id);

            int color = MobColors.forEntity(typeId, id, currentMaxId, config, hurt, returned, alert);
            if (chargedCreeper && MobColors.customColor("minecraft:charged_creeper", config) == null) {
                color = config.chargedCreeperColor;
            }
            if (hurt) color = config.hurtColor;

            TrackedMob tracked = new TrackedMob(id, typeId, entity.getName().getString(),
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(),
                    alert, color, player, hurt, chargedCreeper, renamed, returned, hurtStar);
            state.accept(tracked);
        }
        state.finishLiveScan(client.player.getBlockX(), client.player.getBlockZ());

        if (config.centerEnabled) {
            calculateCenter(client);
            checkAutoClear(client);
        }

        if (client.world.getTime() % 100 == 0) {
            state.saveSessionIfDirty();
            state.saveChunks();
        }
    }

    // === CENTER LOGIC ===
    private void calculateCenter(MinecraftClient client) {
        Map<Integer, int[]> positions = state.sessionPositions();
        if (positions.size() < config.centerMinMarkers) {
            clearCenter();
            return;
        }

        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();

        for (int[] pos : positions.values()) {
            xs.add(pos[0]);
            zs.add(pos[2]);
        }

        Collections.sort(xs);
        Collections.sort(zs);

        int minX = xs.get(0), maxX = xs.get(xs.size() - 1);
        int minZ = zs.get(0), maxZ = zs.get(zs.size() - 1);

        int spread = Math.max(maxX - minX, maxZ - minZ);
        if (spread > config.centerMaxSpreadBlocks) {
            clearCenter();
            return;
        }

        int medianX = xs.get(xs.size() / 2);
        int medianZ = zs.get(zs.size() / 2);

        centerChunkX = medianX >> 4;
        centerChunkZ = medianZ >> 4;
        centerLastUpdate = System.currentTimeMillis();
        centerMarkerCount = positions.size();
    }

    private void checkAutoClear(MinecraftClient client) {
        if (centerChunkX == Integer.MIN_VALUE) return;

        long now = System.currentTimeMillis();
        if (now - centerLastUpdate > config.centerTimeoutSeconds * 1000L) {
            clearCenter();
            return;
        }

        if (client.player != null) {
            int px = client.player.getBlockX() >> 4;
            int pz = client.player.getBlockZ() >> 4;
            int distChunks = Math.max(Math.abs(px - centerChunkX), Math.abs(pz - centerChunkZ));
            if (distChunks >= config.centerClearDistanceChunks) {
                clearCenter();
            }
        }
    }

    private void clearCenter() {
        centerChunkX = Integer.MIN_VALUE;
        centerChunkZ = Integer.MIN_VALUE;
        centerLastUpdate = 0;
        centerMarkerCount = 0;
    }
    // === END CENTER ===

    public static void applyKeyConfig(MobOverlayConfig config) {
        TOGGLE_HUD.setBoundKey(key(config.hudKey, config.hudScanCode));
        TOGGLE_CHUNKS.setBoundKey(key(config.chunksKey, config.chunksScanCode));
        OPEN_SETTINGS.setBoundKey(key(config.settingsKey, config.settingsScanCode));
        CLEAR_SESSION.setBoundKey(key(config.clearSessionKey, config.clearSessionScanCode));
        CLEAR_CHUNKS.setBoundKey(key(config.clearChunksKey, config.clearChunksScanCode));
    }

    private static InputUtil.Key key(int keyCode, int scanCode) {
        if ((keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0) && scanCode > 0) {
            return InputUtil.Type.SCANCODE.createFromCode(scanCode);
        }
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0) {
            return InputUtil.UNKNOWN_KEY;
        }
        return InputUtil.Type.KEYSYM.createFromCode(keyCode);
    }

    // === SYSTEM FONT RENDERING ===
    private void ensureSystemFont() {
        if (systemFont != null) return;
        try {
            systemFont = new Font(Font.SANS_SERIF, Font.PLAIN, (int) config.hudSystemFontSize);
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setFont(systemFont);
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            systemFontHeight = fm.getHeight();
            g2d.dispose();
        } catch (Exception e) {
            systemFont = null;
        }
    }

    private int getSystemTextWidth(String text) {
        if (systemFont == null) return 0;
        Integer cached = systemFontWidths.get(text);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setFont(systemFont);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int width = g2d.getFontMetrics().stringWidth(text);
        g2d.dispose();
        systemFontWidths.put(text, width);
        return width;
    }

    private void drawSystemText(DrawContext ctx, String text, int x, int y, int color) {
        if (systemFont == null) {
            ctx.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, color, config.hudTextShadow);
            return;
        }

        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) a = 255;

        int width = getSystemTextWidth(text);
        int height = systemFontHeight;
        if (width <= 0 || height <= 0) return;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setFont(systemFont);
        g2d.setColor(new java.awt.Color(r, g, b, a));
        g2d.drawString(text, 0, systemFontHeight - g2d.getFontMetrics().getDescent());
        g2d.dispose();

        // Convert to native image and draw
        int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int pixel = pixels[py * width + px];
                int pa = (pixel >>> 24) & 0xFF;
                if (pa > 10) {
                    int pr = (pixel >>> 16) & 0xFF;
                    int pg = (pixel >>> 8) & 0xFF;
                    int pb = pixel & 0xFF;
                    ctx.fill(x + px, y + py, x + px + 1, y + py + 1, (pa << 24) | (pr << 16) | (pg << 8) | pb);
                }
            }
        }
    }
    // === END SYSTEM FONT ===

    private void renderHud(DrawContext drawContext) {
        if (!config.enabled || !hudOpen || state == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer mcFont = client.textRenderer;

        boolean useSystem = config.hudUseSystemFont;
        if (useSystem) ensureSystemFont();

        int x = config.hudX;
        int y = config.hudY;
        float totalScale = config.hudScale * config.hudTextScale;

        // Calculate content height first
        int lineHeight = useSystem ? Math.max(11, systemFontHeight) : 11;
        int contentLines = 5; // header + info + hints + center + spacing
        contentLines += state.currentMobCount();
        contentLines += state.visiblePlayers().size() + 2;
        contentLines += state.sessionCount() + 2;

        int contentHeight = contentLines * lineHeight + 20;

        // Background
        if (config.hudShowBackground) {
            int bgAlpha = (int)(config.hudBackgroundOpacity * 255) << 24;
            int bgColor = bgAlpha | (config.hudBackgroundColor & 0xFFFFFF);
            drawContext.fill(x - 4, y - 4, x + config.hudWidth, y + contentHeight, bgColor);
        }

        drawContext.getMatrices().push();
        drawContext.getMatrices().scale(totalScale, totalScale, 1.0f);
        int sx = Math.round(x / totalScale);
        int sy = Math.round(y / totalScale);

        // === TITLE ===
        String title = "DF Mob Visualizer  [F8]";
        if (useSystem) drawSystemText(drawContext, title, sx, sy, config.hudTitleColor);
        else drawText(drawContext, mcFont, Text.literal(title), sx, sy, config.hudTitleColor, config.hudTextShadow);
        sy += lineHeight + 2;

        // === INDICATORS ===
        if (config.hudShowIndicators) {
            StringBuilder ind = new StringBuilder();
            ind.append(indicator("HUD", config.showHud));
            ind.append("  ").append(indicator("Чанки", config.showChunkOverlay));
            ind.append("  ").append(indicator("Сессия", config.sessionEnabled));
            ind.append("  ").append(indicator("Центр", config.centerEnabled));
            ind.append("  ").append(indicator("ALERT", config.alertEnabled));
            ind.append("  ").append(indicator("HURT", config.hurtEnabled));
            ind.append("  ").append(indicator("RETURNED", config.returnedEnabled));
            String indStr = ind.toString();
            if (useSystem) drawSystemText(drawContext, indStr, sx, sy, config.hudInfoColor);
            else drawText(drawContext, mcFont, Text.literal(indStr), sx, sy, config.hudInfoColor, config.hudTextShadow);
            sy += lineHeight + 2;
        }

        // === INFO LINE ===
        String info = "Мобов: " + state.currentMobCount()
                + "   Сессия: " + state.sessionCount()
                + "   Чанков: " + state.visibleChunks().size();
        if (useSystem) drawSystemText(drawContext, info, sx, sy, config.hudInfoColor);
        else drawText(drawContext, mcFont, Text.literal(info), sx, sy, config.hudInfoColor, config.hudTextShadow);
        sy += lineHeight + 1;

        String maxInfo = "MAX ID: " + state.currentMaxId() + "   MAX ID история: " + state.maxSeenId();
        if (useSystem) drawSystemText(drawContext, maxInfo, sx, sy, config.hudInfoColor);
        else drawText(drawContext, mcFont, Text.literal(maxInfo), sx, sy, config.hudInfoColor, config.hudTextShadow);
        sy += lineHeight + 1;

        String hints = "F9 — чанки | F10 — настройки | F8 — HUD";
        if (useSystem) drawSystemText(drawContext, hints, sx, sy, config.hudHintColor);
        else drawText(drawContext, mcFont, Text.literal(hints), sx, sy, config.hudHintColor, config.hudTextShadow);
        sy += lineHeight + 3;

        // === CENTER ===
        if (centerChunkX != Integer.MIN_VALUE) {
            int centerBlockX = centerChunkX * 16 + 8;
            int centerBlockZ = centerChunkZ * 16 + 8;
            double distance = -1;
            if (client.player != null) {
                double dx = client.player.getBlockX() - centerBlockX;
                double dz = client.player.getBlockZ() - centerBlockZ;
                distance = Math.sqrt(dx * dx + dz * dz);
            }
            String centerText;
            if (distance >= 0) {
                centerText = String.format("ЦЕНТР: X %d Z %d | %d мобов | %.0f блоков",
                        centerBlockX, centerBlockZ, centerMarkerCount, distance);
            } else {
                centerText = String.format("ЦЕНТР: X %d Z %d | %d мобов",
                        centerBlockX, centerBlockZ, centerMarkerCount);
            }
            if (useSystem) drawSystemText(drawContext, centerText, sx, sy, config.hudCenterColor);
            else drawText(drawContext, mcFont, Text.literal(centerText), sx, sy, config.hudCenterColor, config.hudTextShadow);
        } else {
            if (useSystem) drawSystemText(drawContext, "ЦЕНТР: нет мобов", sx, sy, config.hudHintColor);
            else drawText(drawContext, mcFont, Text.literal("ЦЕНТР: нет мобов"), sx, sy, config.hudHintColor, config.hudTextShadow);
        }
        sy += lineHeight + 3;

        // === MOBS ===
        for (TrackedMob mob : state.visibleMobs()) {
            sy = drawMobLine(drawContext, mcFont, mob, sx, sy, useSystem, lineHeight, true);
        }
        sy += 3;

        // === PLAYERS ===
        String playersTitle = "ИГРОКИ (" + state.visiblePlayers().size() + ")";
        if (useSystem) drawSystemText(drawContext, playersTitle, sx, sy, config.hudPlayerColor);
        else drawText(drawContext, mcFont, Text.literal(playersTitle), sx, sy, config.hudPlayerColor, config.hudTextShadow);
        sy += lineHeight + 2;

        for (TrackedMob mob : state.visiblePlayers()) {
            String text = mob.name() + " ID-" + mob.id()
                    + " XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]";
            if (useSystem) drawSystemText(drawContext, text, sx, sy, mob.color());
            else drawText(drawContext, mcFont, Text.literal(text), sx, sy, mob.color(), config.hudTextShadow);
            sy += lineHeight;
        }
        sy += 3;

        // === SESSION ===
        String sessionTitle = "СЕССИЯ (" + state.sessionCount() + ")";
        if (useSystem) drawSystemText(drawContext, sessionTitle, sx, sy, config.hudSessionColor);
        else drawText(drawContext, mcFont, Text.literal(sessionTitle), sx, sy, config.hudSessionColor, config.hudTextShadow);
        sy += lineHeight + 2;

        for (TrackedMob mob : state.visibleSession()) {
            sy = drawMobLine(drawContext, mcFont, mob, sx, sy, useSystem, lineHeight, false);
        }

        drawContext.getMatrices().pop();
    }

    private int drawMobLine(DrawContext ctx, TextRenderer mcFont, TrackedMob mob, int x, int y, boolean useSystem, int lineHeight, boolean isLive) {
        int tagX = x;

        boolean showAlert = mob.alert() || (!isLive && state.wasAlert(mob.id()));
        boolean showHurt = mob.hurt() || (!isLive && state.wasHurt(mob.id()));
        boolean showReturned = mob.returned() || (!isLive && state.wasReturned(mob.id()));

        tagX = drawTagLine(ctx, mcFont, "ALERT", showAlert, config.hudAlertColor, tagX, y, useSystem);
        tagX = drawTagLine(ctx, mcFont, "HURT", showHurt, config.hudHurtColor, tagX, y, useSystem);
        tagX = drawTagLine(ctx, mcFont, "RETURNED", showReturned, config.hudReturnedColor, tagX, y, useSystem);
        tagX = drawTagLine(ctx, mcFont, "CHARGED", mob.chargedCreeper(), config.hudChargedColor, tagX, y, useSystem);
        tagX = drawTagLine(ctx, mcFont, "RENAMED", mob.renamed(), config.hudRenamedColor, tagX, y, useSystem);

        String hurtStarText = mob.hurtStar() && !mob.hurt() ? "[HURT*] " : "";
        String nameText = hurtStarText + mob.name() + (mob.renamed() ? " [переименован]" : "")
                + "  ID-" + mob.id()
                + " (" + formatPercent(mob.id(), state.currentMaxId()) + "%)"
                + "  XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]";

        if (useSystem) drawSystemText(ctx, nameText, tagX, y, mob.color());
        else drawText(ctx, mcFont, Text.literal(nameText), tagX, y, mob.color(), config.hudTextShadow);

        return y + lineHeight;
    }

    private int drawTagLine(DrawContext ctx, TextRenderer mcFont, String tag, boolean active, int color, int x, int y, boolean useSystem) {
        if (!active) return x;
        String text = "[" + tag + "] ";
        if (useSystem) {
            drawSystemText(ctx, text, x, y, color);
            return x + getSystemTextWidth(text);
        } else {
            drawText(ctx, mcFont, Text.literal(text), x, y, color, config.hudTextShadow);
            return x + mcFont.getWidth(text);
        }
    }

    private String indicator(String name, boolean on) {
        return on ? "\u2713" + name : "\u2717" + name;
    }

    private static void drawText(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        if (shadow) ctx.drawTextWithShadow(tr, text, x, y, color);
        else ctx.drawText(tr, text, x, y, color, false);
    }

    private static String formatPercent(int id, int maxId) {
        double percent = maxId <= 0 ? 0.0 : id * 100.0 / maxId;
        return String.format(Locale.ROOT, "%.2f", percent);
    }

    private boolean isAlert(Entity entity, int id, int maxId) {
        String typeId = entityTypeId(entity);
        if (!config.alertEnabled || entity.isPlayer()) return false;
        if (!matchesAlertType(typeId)) return false;
        if (config.alertMode == 1) return maxId > 0 && id * 100.0 / maxId < config.alertPercent;
        return maxId > 0 && maxId - id > config.alertGap;
    }

    private boolean matchesAlertType(String type) {
        if (config.alertEntityTypes == null || config.alertEntityTypes.isBlank()) return false;
        String normalized = type.toLowerCase(Locale.ROOT);
        for (String raw : config.alertEntityTypes.split(",")) {
            String wanted = raw.trim().toLowerCase(Locale.ROOT);
            if (wanted.isBlank()) continue;
            if (!wanted.contains(":")) wanted = "minecraft:" + wanted;
            if (normalized.equals(wanted) || normalized.endsWith(":" + wanted.substring(wanted.indexOf(':') + 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean centerMatches(TrackedMob mob) {
        if (config.centerAlertMobs && mob.alert()) return true;
        if (config.centerReturnedMobs && mob.returned()) return true;
        if (config.centerHurtMobs && mob.hurt()) return true;
        if (config.centerPlayers && mob.player()) return true;
        if (config.centerSessionMobs && state.isInSession(mob.id())) return true;
        if (config.centerLowIds && mob.id() < config.purpleIdLimit) return true;
        if (config.centerHostileMobs && isHostile(mob.type())) return true;
        if (config.centerEntityTypes != null && !config.centerEntityTypes.isBlank()) {
            if (matchesType(mob.type(), config.centerEntityTypes)) return true;
        }
        return false;
    }

    private boolean matchesType(String type, String types) {
        if (types == null || types.isBlank()) return false;
        String normalized = type.toLowerCase(Locale.ROOT);
        for (String raw : types.split(",")) {
            String wanted = raw.trim().toLowerCase(Locale.ROOT);
            if (wanted.isBlank()) continue;
            if (!wanted.contains(":")) wanted = "minecraft:" + wanted;
            if (normalized.equals(wanted) || normalized.endsWith(":" + wanted.substring(wanted.indexOf(':') + 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean isHostile(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.contains("zombie") || lower.contains("skeleton") || lower.contains("spider")
                || lower.contains("creeper") || lower.contains("enderman") || lower.contains("witch")
                || lower.contains("blaze") || lower.contains("ghast") || lower.contains("slime")
                || lower.contains("magma") || lower.contains("piglin") || lower.contains("hoglin")
                || lower.contains("pillager") || lower.contains("ravager") || lower.contains("vex")
                || lower.contains("vindicator") || lower.contains("evoker") || lower.contains("guardian");
    }

    private static boolean scanFallbackDown(MinecraftClient client, int keyCode, int scanCode) {
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN && keyCode != 0
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode)) return true;
        if ((keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0) && scanCode > 0
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), scanCode)) return true;
        return false;
    }

    private void renderChunks(WorldRenderContext context) {
        if (!chunksOpen || state == null || context.consumers() == null) return;

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        try {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            VertexConsumer fills = context.consumers().getBuffer(RenderLayer.getDebugQuads());

            refreshSurfaceCache(client);

            for (ChunkMark mark : state.visibleChunks()) {
                double centerX = mark.chunkX() * 16.0 + 8.0;
                double centerZ = mark.chunkZ() * 16.0 + 8.0;
                if (Math.abs(centerX - cameraPos.x) > config.renderDistanceChunks * 16
                        || Math.abs(centerZ - cameraPos.z) > config.renderDistanceChunks * 16) continue;

                float r = ((mark.color() >>> 16) & 255) / 255f;
                float g = ((mark.color() >>> 8) & 255) / 255f;
                float b = (mark.color() & 255) / 255f;
                float alpha = config.chunkOpacity * config.chunkFillStrength;
                alpha = Math.max(0.1f, Math.min(0.9f, alpha));
                if (mark.ring()) alpha *= 0.35f;

                long chunkKey = MobOverlayState.chunkKey(mark.chunkX(), mark.chunkZ());
                int surfaceY = surfaceHeightCache.computeIfAbsent(chunkKey, k -> 
                    getChunkSurfaceHeight(client, mark.chunkX(), mark.chunkZ())
                );

                double x = mark.chunkX() * 16.0 - cameraPos.x;
                double z = mark.chunkZ() * 16.0 - cameraPos.z;
                double y = config.chunkYOffset - cameraPos.y;

                drawChunkFill(fills, x, y, z, r, g, b, alpha, config.chunkHeight);
            }
        } catch (Exception error) {
            System.err.println("[DF Mob Visualizer] Chunk overlay render failed: " + error.getMessage());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private void drawChunkFill(VertexConsumer v, double x, double y, double z,
                               float r, float g, float b, float a, double height) {
        double size = 16.0;
        double y2 = y + height;

        v.vertex((float) x, (float) y, (float) z).color(r, g, b, a);
        v.vertex((float) (x + size), (float) y, (float) z).color(r, g, b, a);
        v.vertex((float) (x + size), (float) y, (float) (z + size)).color(r, g, b, a);
        v.vertex((float) x, (float) y, (float) (z + size)).color(r, g, b, a);

        if (height > 0.1) {
            float sideAlpha = a * 0.25f;
            float bottomAlpha = a * 0.6f;

            v.vertex((float) x, (float) y2, (float) z).color(r * 0.7f, g * 0.7f, b * 0.7f, bottomAlpha);
            v.vertex((float) x, (float) y2, (float) (z + size)).color(r * 0.7f, g * 0.7f, b * 0.7f, bottomAlpha);
            v.vertex((float) (x + size), (float) y2, (float) (z + size)).color(r * 0.7f, g * 0.7f, b * 0.7f, bottomAlpha);
            v.vertex((float) (x + size), (float) y2, (float) z).color(r * 0.7f, g * 0.7f, b * 0.7f, bottomAlpha);

            v.vertex((float) x, (float) y, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y2, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) x, (float) y2, (float) z).color(r, g, b, sideAlpha);

            v.vertex((float) x, (float) y, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) x, (float) y2, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y2, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y, (float) (z + size)).color(r, g, b, sideAlpha);

            v.vertex((float) x, (float) y, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) x, (float) y2, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) x, (float) y2, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) x, (float) y, (float) (z + size)).color(r, g, b, sideAlpha);

            v.vertex((float) (x + size), (float) y, (float) z).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y2, (float) (z + size)).color(r, g, b, sideAlpha);
            v.vertex((float) (x + size), (float) y2, (float) z).color(r, g, b, sideAlpha);
        }
    }

    private int getChunkSurfaceHeight(MinecraftClient client, int chunkX, int chunkZ) {
        if (client.world == null) return 64;
        int total = 0;
        int count = 0;
        int[][] points = {{0,0}, {7,7}, {15,0}, {15,15}, {0,15}};
        for (int[] p : points) {
            int x = chunkX * 16 + p[0];
            int z = chunkZ * 16 + p[1];
            total += client.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            count++;
        }
        return count > 0 ? total / count : 64;
    }

    private void refreshSurfaceCache(MinecraftClient client) {
        long stamp = client.world.getTime() / 10L;
        if (surfaceCacheWorld != client.world || surfaceCacheStamp != stamp) {
            surfaceHeightCache.clear();
            surfaceCacheWorld = client.world;
            surfaceCacheStamp = stamp;
        }
    }

    private static String entityTypeId(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString().toLowerCase(Locale.ROOT);
    }
}
