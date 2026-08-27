package com.df.mobvisualizer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MobEventSelectionScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final String targetField; // e.g. "pinnedEntityTypes" or "highlightEntityTypes"
    private final List<String> mobIds = new ArrayList<>();
    private final Map<String, Boolean> selected = new LinkedHashMap<>();
    private int scrollOffset = 0;

    public MobEventSelectionScreen(Screen parent, MobOverlayConfig config, String targetField) {
        super(Text.literal("Выбор мобов"));
        this.parent = parent;
        this.config = config;
        this.targetField = targetField;

        Registries.ENTITY_TYPE.getIds().stream().map(Identifier::toString)
                .sorted().forEach(mobIds::add);

        // initialize selection from current config value
        String current = "";
        if ("pinnedEntityTypes".equals(targetField)) current = config.pinnedEntityTypes == null ? "" : config.pinnedEntityTypes;
        else if ("highlightEntityTypes".equals(targetField)) current = config.highlightEntityTypes == null ? "" : config.highlightEntityTypes;
        else if ("alertEntityTypes".equals(targetField)) current = config.alertEntityTypes == null ? "" : config.alertEntityTypes;
        else if ("returnedEntityTypes".equals(targetField)) current = config.returnedEntityTypes == null ? "" : config.returnedEntityTypes;

        List<String> parts = new ArrayList<>();
        if (!current.isEmpty()) {
            for (String s : current.split("\\s*,\\s*")) {
                parts.add(s.trim());
            }
        }
        for (String id : mobIds) selected.put(id, parts.contains(id));
    }

    @Override
    protected void init() {
        super.init();
        int btnY = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Применить"), b -> {
            applySelection();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width/2 - 100, btnY, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(this.width/2 + 2, btnY, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("All"), b -> { for (String k : selected.keySet()) selected.put(k, true); })
                .dimensions(10, btnY, 60, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("None"), b -> { for (String k : selected.keySet()) selected.put(k, false); })
                .dimensions(75, btnY, 60, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width/2, 10, 0xFFFFFF);

        int startY = 30;
        int lineH = 12;
        int visible = (this.height - 70) / lineH;
        List<String> page = mobIds.stream().skip(scrollOffset).limit(visible).collect(Collectors.toList());
        int y = startY;
        for (String id : page) {
            boolean sel = selected.getOrDefault(id, false);
            int col = sel ? 0xA0FF8080 : 0xFFFFFFFF;
            context.drawTextWithShadow(textRenderer, Text.literal((sel ? "[x] " : "[ ] ") + id), 10, y, col);
            y += lineH;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int shift = verticalAmount > 0 ? -1 : 1;
        int visible = (this.height - 70) / 12;
        scrollOffset = Math.max(0, Math.min(Math.max(0, mobIds.size() - visible), scrollOffset + shift));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startY = 30;
        int lineH = 12;
        int visible = (this.height - 70) / lineH;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int index = (my - startY) / lineH;
        if (index >= 0 && index < visible) {
            int listIndex = scrollOffset + index;
            if (listIndex >= 0 && listIndex < mobIds.size()) {
                String id = mobIds.get(listIndex);
                selected.put(id, !selected.getOrDefault(id, false));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applySelection() {
        List<String> chosen = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : selected.entrySet()) if (e.getValue()) chosen.add(e.getKey());
        String joined = String.join(", ", chosen);
        if ("pinnedEntityTypes".equals(targetField)) config.pinnedEntityTypes = joined;
        else if ("highlightEntityTypes".equals(targetField)) config.highlightEntityTypes = joined;
        else if ("alertEntityTypes".equals(targetField)) config.alertEntityTypes = joined;
        else if ("returnedEntityTypes".equals(targetField)) config.returnedEntityTypes = joined;
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
