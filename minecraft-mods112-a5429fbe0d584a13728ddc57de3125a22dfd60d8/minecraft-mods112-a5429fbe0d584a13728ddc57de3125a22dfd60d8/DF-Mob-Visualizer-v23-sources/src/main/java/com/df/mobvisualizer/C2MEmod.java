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
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
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
    private static final KeyBinding TOGGLE_MOB_HIGHLIGHTS = new KeyBinding(
            "key.df_mob_visualizer.toggle_mob_highlights",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
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
    private boolean mobHighlightsOpen;
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
        mobHighlightsOpen = config.seeThroughMobs;
        
        KeyBindingHelper.registerKeyBinding(TOGGLE_HUD);
        KeyBindingHelper.registerKeyBinding(TOGGLE_CHUNKS);
        KeyBindingHelper.registerKeyBinding(TOGGLE_MOB_HIGHLIGHTS);
        KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS);
        KeyBindingHelper.registerKeyBinding(CLEAR_SESSION);
        KeyBindingHelper.registerKeyBinding(CLEAR_CHUNKS);

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
        WorldRenderEvents.BEFORE_ENTITIES.register(this::renderThroughWalls);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::renderChunks);
    }

    private void tick(MinecraftClient client) {
        while (TOGGLE_HUD.wasPressed()) { hudOpen = !hudOpen; config.showHud = hudOpen; config.save(); }
        while (TOGGLE_CHUNKS.wasPressed()) { chunksOpen = !chunksOpen; config.showChunkOverlay = chunksOpen; config.save(); }
        while (TOGGLE_MOB_HIGHLIGHTS.wasPressed()) {
            mobHighlightsOpen = !mobHighlightsOpen;
            config.seeThroughMobs = mobHighlightsOpen;
            config.save();
        }
        hudOpen = config.showHud;
        chunksOpen = config.showChunkOverlay;
        mobHighlightsOpen = config.seeThroughMobs;
        
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
            boolean returned = state.isReturned(id);
            
            int color = MobColors.forEntity(typeId, id, currentMaxId, config, hurt, returned, alert);
            if (chargedCreeper && MobColors.customColor("minecraft:charged_creeper", config) == null) {
                color = config.chargedCreeperColor;
            }
            if (hurt) color = config.hurtColor;
            
            TrackedMob tracked = new TrackedMob(id, typeId, entity.getName().getString(),
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(),
                    alert, color, player, hurt, chargedCreeper, renamed, returned);
            state.accept(tracked);
            if (centerMatches(tracked)) centerMarkers.add(tracked);
        }
        calculateCenter(centerMarkers);
        
        if (client.world.getTime() % 100 == 0) {
            state.saveSessionIfDirty();
            state.saveChunks();
        }
    }

    public static void applyKeyConfig(MobOverlayConfig config) {
        TOGGLE_HUD.setBoundKey(key(config.hudKey, config.hudScanCode));
        TOGGLE_CHUNKS.setBoundKey(key(config.chunksKey, config.chunksScanCode));
        TOGGLE_MOB_HIGHLIGHTS.setBoundKey(key(config.mobHighlightsKey, config.mobHighlightsScanCode));
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
        drawContext.fill(x - 4, y - 4, x + config.hudWidth, y + lineCount * 11 + 6,
                0x880B0710);
        drawContext.drawTextWithShadow(textRenderer, Text.literal("DF Mob Visualizer  [F8]"), x, y, 0xFFE8D7FF);
        y += 12;
        drawContext.getMatrices().push();
        float totalScale = config.hudScale * config.hudTextScale;
        drawContext.getMatrices().scale(totalScale, totalScale, 1.0f);
        x = Math.round(x / totalScale);
        y = Math.round(y / totalScale);
        
        drawContext.drawTextWithShadow(textRenderer, Text.literal("Мобов: " + state.currentMobCount()
                + "   Сессия: " + state.sessionCount() + "   Чанков: " + state.visibleChunks().size()), x, y, 0xFFFFFFFF);
        y += 12;
        drawContext.drawTextWithShadow(textRenderer, Text.literal("MAX ID: " + state.currentMaxId()
                + "   MAX ID история: " + state.maxSeenId()), x, y, 0xFFFFFFFF);
        y += 12;
        drawContext.drawTextWithShadow(textRenderer, Text.literal("F7 — подсветка | F9 — чанки | F10 — настройки | F8 — HUD"), x, y, 0xFFB9A7C9);
        y += 14;
        drawContext.drawTextWithShadow(textRenderer,
                Text.literal(centerChunkX == Integer.MIN_VALUE ? "ЦЕНТР: нет мобов"
                        : "ЦЕНТР: X " + (centerChunkX * 16 + 8) + " Z " + (centerChunkZ * 16 + 8)
                        + " (" + centerMarkerCount + " мобов)"),
                x, y, centerChunkX == Integer.MIN_VALUE ? 0xFFB9A7C9 : 0xFFFFD34E);
        y += 14;

        for (TrackedMob mob : state.visibleMobs()) {
            int color = mob.color();
            drawContext.drawTextWithShadow(textRenderer, Text.literal(statusTags(mob)
                    + (mob.chargedCreeper() ? "[CHARGED] " : "")
                    + mob.name() + (mob.renamed() ? " [переименован]" : "") + "  ID-" + mob.id()
                    + " (" + formatPercent(mob.id(), state.currentMaxId()) + "%)"
                    + "  XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]"), x, y, color);
            y += 11;
        }
        y += 3;
        drawContext.drawTextWithShadow(textRenderer,
                Text.literal("ИГРОКИ (" + state.visiblePlayers().size() + ")"), x, y, 0xFF9EDBFF);
        y += 12;
        for (TrackedMob mob : state.visiblePlayers()) {
            drawContext.drawTextWithShadow(textRenderer,
                            Text.literal(mob.name() + " ID-" + mob.id()
                            + " XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]"),
                    x, y, mob.color());
            y += 11;
        }
        y += 3;
        drawContext.drawTextWithShadow(textRenderer, Text.literal("СЕССИЯ (" + state.sessionCount() + ")"), x, y, 0xFFFFD34E);
        y += 12;
        for (TrackedMob mob : state.visibleSession()) {
            int color = mob.color();
            String reason = statusTags(mob).replace("[", "").replace("]", "").trim()
                    .replace(" ", ", ");
            if (reason.isBlank()) reason = "MOB";
            drawContext.drawTextWithShadow(textRenderer, Text.literal("[" + reason + "] "
                    + mob.name() + "  ID-" + mob.id()
                    + " (" + formatPercent(mob.id(), state.currentMaxId()) + "%)"
                    + "  XYZ[" + mob.x() + ", " + mob.y() + ", " + mob.z() + "]"), x, y, color);
            y += 11;
        }
        drawContext.getMatrices().pop();
    }

    private String statusTags(TrackedMob mob) {
        StringBuilder tags = new StringBuilder();
        if (mob.alert()) tags.append("[ALERT] ");
        if (mob.hurt()) tags.append("[HURT] ");
        if (mob.returned()) tags.append("[RETURNED] ");
        if (mob.chargedCreeper()) tags.append("[CHARGED] ");
        if (mob.renamed()) tags.append("[RENAMED] ");
        if (mob.player()) tags.append("[PLAYER] ");
        return tags.toString();
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
        if (config.alertEntityTypes == null || config.alertEntityTypes.isBlank()) return true;
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
        return false;
    }

    private boolean shouldHighlight(TrackedMob mob) {
        if (config.highlightAll) return true;
        if (config.highlightHurt && mob.hurt()) return true;
        if (config.highlightAlert && mob.alert()) return true;
        if (config.highlightReturned && mob.returned()) return true;
        if (config.highlightCharged && mob.chargedCreeper()) return true;
        if (config.highlightRenamed && mob.renamed()) return true;
        if (config.highlightPlayers && mob.player()) return true;
        return false;
    }

    private void renderThroughWalls(WorldRenderContext context) {
        if (!config.enabled || !mobHighlightsOpen || !config.seeThroughMobs || state == null
                || MinecraftClient.getInstance().world == null
                || context.matrixStack() == null || context.consumers() == null) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = context.camera().getPos();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        
        try {
            RenderSystem.lineWidth(2.0f);
            VertexConsumer wallLines = context.consumers().getBuffer(RenderLayer.getLines());
            
            for (Entity entity : client.world.getEntities()) {
                if (entity == client.player) continue;
                if (!(entity instanceof LivingEntity)) continue;
                
                int id = entity.getId();
                String typeId = entityTypeId(entity);
                boolean alert = isAlert(entity, id, state.currentMaxId());
                boolean hurt = entity instanceof LivingEntity living && living.hurtTime > 0;
                boolean chargedCreeper = entity instanceof CreeperEntity creeper && creeper.isCharged();
                boolean renamed = entity.hasCustomName();
                boolean returned = state.isReturned(id);
                int color = MobColors.forEntity(typeId, id, state.currentMaxId(), config, hurt, returned, alert);
                if (chargedCreeper && MobColors.customColor("minecraft:charged_creeper", config) == null) {
                    color = config.chargedCreeperColor;
                }
                if (hurt) color = config.hurtColor;
                
                TrackedMob mob = new TrackedMob(id, typeId, entity.getName().getString(),
                        entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(),
                        alert, color, entity.isPlayer(), hurt, chargedCreeper, renamed, returned);
                
                if (!shouldHighlight(mob)) continue;
                if (!isBehindBlock(client, entity, cameraPos)) continue;
                
                context.matrixStack().push();
                try {
                    dispatcher.render(entity, 
                        entity.getX() - cameraPos.x,
                        entity.getY() - cameraPos.y, 
                        entity.getZ() - cameraPos.z,
                        entity.getYaw(), 
                        context.matrixStack(), 
                        context.consumers(),
                        15728880);
                } finally {
                    context.matrixStack().pop();
                }
                
                drawEntityBox(wallLines, entity, cameraPos, color, 0.95f);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
        }
    }

    private static boolean isBehindBlock(MinecraftClient client, Entity entity, Vec3d cameraPos) {
        if (client.world == null) return false;
        Vec3d target = entity.getBoundingBox().getCenter();
        var hit = client.world.raycast(new RaycastContext(cameraPos, target,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && hit.getPos().squaredDistanceTo(cameraPos) + 0.01
                < target.squaredDistanceTo(cameraPos);
    }

    private static String entityTypeId(Entity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()).toString().toLowerCase(Locale.ROOT);
    }

    private static boolean scanFallbackDown(MinecraftClient client, int keyCode, int scanCode) {
        return keyCode != GLFW.GLFW_KEY_UNKNOWN && keyCode != 0
                && InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode);
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
            Map<Long, Integer> chunkHeights = new HashMap<>();
            
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
                int surfaceY = chunkHeights.computeIfAbsent(chunkKey, k -> 
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

    private static void drawEntityBox(VertexConsumer v, Entity entity, Vec3d cameraPos,
                                      int color, float alpha) {
        Box box = entity.getBoundingBox();
        double minX = box.minX - cameraPos.x;
        double minY = box.minY - cameraPos.y;
        double minZ = box.minZ - cameraPos.z;
        double maxX = box.maxX - cameraPos.x;
        double maxY = box.maxY - cameraPos.y;
        double maxZ = box.maxZ - cameraPos.z;
        float r = ((color >>> 16) & 255) / 255.0f;
        float g = ((color >>> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;

        linePlain(v, minX, minY, minZ, maxX, minY, minZ, r, g, b, alpha);
        linePlain(v, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, alpha);
        linePlain(v, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, alpha);
        linePlain(v, minX, minY, maxZ, minX, minY, minZ, r, g, b, alpha);
        linePlain(v, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        linePlain(v, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, alpha);
        linePlain(v, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
        linePlain(v, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, alpha);
        linePlain(v, minX, minY, minZ, minX, maxY, minZ, r, g, b, alpha);
        linePlain(v, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, alpha);
        linePlain(v, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, alpha);
        linePlain(v, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, alpha);
    }

    private static void linePlain(VertexConsumer v, double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        v.vertex((float) x1, (float) y1, (float) z1)
                .color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        v.vertex((float) x2, (float) y2, (float) z2)
                .color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
    }
}
