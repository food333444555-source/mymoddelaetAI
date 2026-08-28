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
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
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
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private int centerMarkerCount;
    private boolean clearSessionScanDown;
    private boolean clearChunksScanDown;
    private final Map<Long, Integer> surfaceHeightCache = new HashMap<>();
    private Object surfaceCacheWorld;
    private long surfaceCacheStamp = Long.MIN_VALUE;

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
        List<TrackedMob> centerMarkers = new ArrayList<>();

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
            if (centerMatches(tracked)) centerMarkers.add(tracked);
        }
        state.finishLiveScan(client.player.getBlockX(), client.player.getBlockZ());

        if (config.centerBySession) {
            calculateCenter(new ArrayList<>(state.visibleSession()));
        } else {
            calculateCenter(centerMarkers);
        }

        if (client.world.getTime() % 100 == 0) {
            state.saveSessionIfDirty();
            state.saveChunks();
        }
    }

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

    private void renderHud(net.minecraft.client.gui.DrawContext drawContext) {
        if (!config.enabled || !hudOpen || state == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int x = config.hudX;
        int y = config.hudY;
        int lineCount = state.currentMobCount() + state.sessionCount()
                + state.visiblePlayers().size() + 9;

        int bgAlpha = (int)(config.hudBackgroundOpacity * 255) << 24;
        int bgColor = bgAlpha | 0x0B0710;
        drawContext.fill(x - 4, y - 4, x + config.hudWidth, y + lineCount * 11 + 6, bgColor);

        drawContext.drawTextWithShadow(textRenderer, Text.literal("DF Mob Visualizer  [F8]"), x, y, 0xFFE8D7FF);
        y += 12;
        drawContext.getMatrices().push();
        float totalScale = config.hudScale * config.hudTextScale;
        drawContext.getMatrices().scale(totalScale, totalScale, 1.0f);
        x = Math.round(x / totalScale);
        y = Math.round(y / totalScale);

        boolean shadow = config.hudTextShadow;
        drawText(drawContext, textRenderer, Text.literal("Мобов: " + state.currentMobCount()
                + "   Сессия: " + state.sessionCount() + "   Чанков: " + state.visibleChunks().size()), x, y, 0xFFFFFFFF, shadow);
        y += 12;
        drawText(drawContext, textRenderer, Text.literal("MAX ID: " + state.currentMaxId()
                + "   MAX ID история: " + state.maxSeenId()), x, y, 0xFFFFFFFF, shadow);
        y += 12;
        drawText(drawContext, textRenderer, Text.literal("F9 — чанки | F10 — настройки | F8 — HUD"), x, y, 0xFFB9A7C9, shadow);
        y += 14;
        drawText(drawContext, textRenderer,
                Text.literal(centerChunkX == Integer.MIN_VALUE ? "ЦЕНТР: нет мобов"
                        : "ЦЕНТР: X " + (centerChunkX * 16 + 8) + " Z " + (centerChunkZ * 16 + 8)
                        + " (" + centerMarkerCount + " мобов)"),
                x, y, centerChunkX == Integer.MIN_VALUE ? 0xFFB9A7C9 : 0xFFFFD34E, shadow);
        y += 14;

        for (TrackedMob mob : state.visibleMobs()) {
            int color = mob.color();
            int tagX = x;
            tagX = drawTag(drawContext, textRenderer, "ALERT", mob.alert(), config.hudAlertColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "HURT", mob.hurt(), config.hudHurtColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "RETURNED", mob.returned(), config.hudReturnedColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "CHARGED", mob.chargedCreeper(), config.hudChargedColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "RENAMED", mob.renamed(), config.hudRenamedColor, tagX, y, shadow);

            String hurtStarText = mob.hurtStar() && !mob.hurt() ? "[HURT*] " : "";
            String nameText = hurtStarText + mob.name() + (mob.renamed() ? " [переименован]" : "")
                    + "  ID-" + mob.id()
                    + " (" + formatPercent(mob.id(), state.currentMaxId()) + "%)"
                    + "  XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]";
            drawText(drawContext, textRenderer, Text.literal(nameText), tagX, y, color, shadow);
            y += 11;
        }
        y += 3;
        drawText(drawContext, textRenderer,
                Text.literal("ИГРОКИ (" + state.visiblePlayers().size() + ")"), x, y, config.hudPlayerColor, shadow);
        y += 12;
        for (TrackedMob mob : state.visiblePlayers()) {
            String text = mob.name() + " ID-" + mob.id()
                    + " XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]";
            drawText(drawContext, textRenderer, Text.literal(text), x, y, mob.color(), shadow);
            y += 11;
        }
        y += 3;
        drawText(drawContext, textRenderer, Text.literal("СЕССИЯ (" + state.sessionCount() + ")"), x, y, config.hudSessionColor, shadow);
        y += 12;
        for (TrackedMob mob : state.visibleSession()) {
            int color = mob.color();
            int tagX = x;
            tagX = drawTag(drawContext, textRenderer, "ALERT", mob.alert(), config.hudAlertColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "HURT", mob.hurt(), config.hudHurtColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "RETURNED", mob.returned(), config.hudReturnedColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "CHARGED", mob.chargedCreeper(), config.hudChargedColor, tagX, y, shadow);
            tagX = drawTag(drawContext, textRenderer, "RENAMED", mob.renamed(), config.hudRenamedColor, tagX, y, shadow);

            String nameText = mob.name() + "  ID-" + mob.id()
                    + " (" + formatPercent(mob.id(), state.currentMaxId()) + "%)"
                    + "  XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]";
            drawText(drawContext, textRenderer, Text.literal(nameText), tagX, y, color, shadow);
            y += 11;
        }
        drawContext.getMatrices().pop();
    }

    private static void drawText(net.minecraft.client.gui.DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        if (shadow) ctx.drawTextWithShadow(tr, text, x, y, color);
        else ctx.drawText(tr, text, x, y, color, false);
    }

    private static int drawTag(net.minecraft.client.gui.DrawContext ctx, TextRenderer tr, String tag, boolean active, int color, int x, int y, boolean shadow) {
        if (!active) return x;
        String text = "[" + tag + "] ";
        drawText(ctx, tr, Text.literal(text), x, y, color, shadow);
        return x + tr.getWidth(text);
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

    private void calculateCenter(List<TrackedMob> markers) {
        if (markers.size() < 2) {
            centerChunkX = Integer.MIN_VALUE;
            centerChunkZ = Integer.MIN_VALUE;
            centerMarkerCount = 0;
            return;
        }
        List<Integer> xs = markers.stream().map(TrackedMob::x).sorted().toList();
        List<Integer> zs = markers.stream().map(TrackedMob::z).sorted().toList();
        int minX = xs.get(0), maxX = xs.get(xs.size() - 1);
        int minZ = zs.get(0), maxZ = zs.get(zs.size() - 1);
        if (Math.max(maxX - minX, maxZ - minZ) > 128) {
            centerChunkX = Integer.MIN_VALUE;
            centerChunkZ = Integer.MIN_VALUE;
            centerMarkerCount = 0;
            return;
        }
        centerChunkX = xs.get(xs.size() / 2) >> 4;
        centerChunkZ = zs.get(zs.size() / 2) >> 4;
        centerMarkerCount = markers.size();
    }

    private static String entityTypeId(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString().toLowerCase(Locale.ROOT);
    }
}
