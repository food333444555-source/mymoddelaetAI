package com.df.mobvisualizer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MobColorsEditorScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final List<String> mobIds = new ArrayList<>();
    private final Map<String, Integer> colors = new LinkedHashMap<>();
    private int selectedIndex = 0;

    private TextFieldWidget rField;
    private TextFieldWidget gField;
    private TextFieldWidget bField;
    private TextFieldWidget aField;

    public MobColorsEditorScreen(Screen parent, MobOverlayConfig config) {
        super(Text.literal("Редактор цветов мобов"));
        this.parent = parent;
        this.config = config;
        Registries.ENTITY_TYPE.getIds().stream().map(Identifier::toString).sorted().forEach(mobIds::add);
        loadFromConfig();
    }

    private void loadFromConfig() {
        colors.clear();
        String raw = config.customMobColors == null ? "" : config.customMobColors.trim();
        if (raw.isEmpty()) return;
        String[] parts = raw.split("\\s*,\\s*");
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            String id = kv[0].trim();
            String hex = kv[1].trim();
            try {
                if (hex.startsWith("#")) hex = hex.substring(1);
                int val = (int)Long.parseLong(hex, 16);
                colors.put(id, val);
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = 10;
        int top = 20;
        int listW = Math.min(300, this.width / 2 - 20);
        int listH = this.height - 80;

        // buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить и вернуться"), b -> { saveToConfig(); MinecraftClient.getInstance().setScreen(parent); }).dimensions(this.width - 210, this.height - 30, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> MinecraftClient.getInstance().setScreen(parent)).dimensions(this.width - 420, this.height - 30, 200, 20).build());

        // color fields
        int fx = this.width / 2 + 20;
        int fy = 40;
        rField = new TextFieldWidget(this.textRenderer, fx, fy, 50, 20, Text.literal("R"));
        gField = new TextFieldWidget(this.textRenderer, fx + 60, fy, 50, 20, Text.literal("G"));
        bField = new TextFieldWidget(this.textRenderer, fx + 120, fy, 50, 20, Text.literal("B"));
        aField = new TextFieldWidget(this.textRenderer, fx + 180, fy, 50, 20, Text.literal("A"));
        this.addDrawableChild(rField);
        this.addDrawableChild(gField);
        this.addDrawableChild(bField);
        this.addDrawableChild(aField);

        // apply color button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Применить цвет"), b -> {
            applyColorToSelected();
        }).dimensions(fx, fy + 30, 120, 20).build());

        // populate fields for initial selection
        if (!mobIds.isEmpty()) updateFieldsForSelection();
    }

    private void updateFieldsForSelection() {
        String id = mobIds.get(selectedIndex);
        Integer col = colors.get(id);
        int a=255,r=255,g=255,b=255;
        if (col != null) {
            // col may be ARGB or RGB depending on stored length; treat as AARRGGBB if length > 0
            int v = col;
            if ((v & 0xFF000000) != 0) {
                a = (v >> 24) & 0xFF;
                r = (v >> 16) & 0xFF;
                g = (v >> 8) & 0xFF;
                b = v & 0xFF;
            } else {
                r = (v >> 16) & 0xFF;
                g = (v >> 8) & 0xFF;
                b = v & 0xFF;
            }
        }
        rField.setText(Integer.toString(r));
        gField.setText(Integer.toString(g));
        bField.setText(Integer.toString(b));
        aField.setText(Integer.toString(a));
    }

    private void applyColorToSelected() {
        String id = mobIds.get(selectedIndex);
        int r = parseIntOrDefault(rField.getText().trim(), 255);
        int g = parseIntOrDefault(gField.getText().trim(), 255);
        int b = parseIntOrDefault(bField.getText().trim(), 255);
        int a = parseIntOrDefault(aField.getText().trim(), 255);
        r = clamp(r,0,255); g = clamp(g,0,255); b = clamp(b,0,255); a = clamp(a,0,255);
        int argb = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        colors.put(id, argb);
    }

    private int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Редактор цветов мобов"), width/2, 10, 0xFFFFFF);

        // left: list
        int startY = 30;
        int lineH = 12;
        int visible = (this.height - 80) / lineH;
        int listW = Math.min(300, this.width/2 - 20);
        int x = 10;
        int y = startY;
        int idx = 0;
        for (int i = 0; i < visible && i + scrollIndex() < mobIds.size(); i++) {
            String id = mobIds.get(i + scrollIndex());
            boolean sel = (i + scrollIndex()) == selectedIndex;
            int col = sel ? 0xFFFFFF88 : 0xFFFFFFFF;
            context.drawTextWithShadow(textRenderer, Text.literal((sel ? "> " : "  ") + id), x, y, col);
            y += lineH;
            idx++;
        }

        // right: color preview and fields
        int fx = this.width/2 + 20;
        int fy = 20;
        context.drawTextWithShadow(textRenderer, Text.literal("Выбран: " + (mobIds.isEmpty() ? "(нет)" : mobIds.get(selectedIndex))), fx, fy, 0xFFFFFF);
        fy += 18;

        // preview box
        int previewX = fx;
        int previewY = fy;
        int previewSize = 40;
        Integer colv = colors.get(mobIds.get(selectedIndex));
        int rgba = colv == null ? 0xFFFFFFFF : colv;
        // draw colored rectangle (simple)
        context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, rgba);
        fy += previewSize + 6;

        context.drawTextWithShadow(textRenderer, Text.literal("R:"), fx, fy, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("G:"), fx + 60, fy, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("B:"), fx + 120, fy, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("A:"), fx + 180, fy, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private int scrollIndex() {
        // simple: ensure selectedIndex is visible; compute start as max(0, selectedIndex - visible/2)
        int lineH = 12;
        int visible = (this.height - 80) / lineH;
        int start = Math.max(0, Math.min(Math.max(0, mobIds.size() - visible), selectedIndex - visible/2));
        return start;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startY = 30;
        int lineH = 12;
        int visible = (this.height - 80) / lineH;
        int index = (int)((mouseY - startY) / lineH) + scrollIndex();
        if (index >= 0 && index < mobIds.size()) {
            selectedIndex = index;
            updateFieldsForSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dir = verticalAmount > 0 ? -1 : 1;
        selectedIndex = Math.max(0, Math.min(mobIds.size()-1, selectedIndex + dir));
        updateFieldsForSelection();
        return true;
    }

    private void saveToConfig() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String,Integer> e : colors.entrySet()) {
            String id = e.getKey();
            int v = e.getValue();
            String hex = String.format("#%08X", v);
            parts.add(id + "=" + hex);
        }
        config.customMobColors = String.join(", ", parts);
        config.save();
    }

    public boolean isPauseScreen() { return false; }
}
