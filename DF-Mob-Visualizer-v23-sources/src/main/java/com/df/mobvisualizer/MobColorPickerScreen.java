package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import java.util.function.IntConsumer;

/**
 * Visual per-entity color editor. The hue ring is the primary control;
 * channel sliders remain available for precise RGB/alpha adjustment.
 */
public final class MobColorPickerScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final String entityId;
    private int red, green, blue, alpha;
    private float hue, saturation, brightness;
    private final IntConsumer onSave;

    public MobColorPickerScreen(Screen parent, MobOverlayConfig config, String entityId) {
        super(Text.literal("Цвет моба"));
        this.parent = parent;
        this.config = config;
        this.entityId = entityId;
        this.onSave = null;
        int selected = MobColors.customColor(entityId, config) == null
                ? 0xFF55AAFF : MobColors.customColor(entityId, config);
        alpha = (selected >>> 24) & 255;
        setRgb(selected & 0xFFFFFF);
    }

    public MobColorPickerScreen(Screen parent, String label, int selected, IntConsumer onSave) {
        super(Text.literal("Выбор цвета"));
        this.parent = parent;
        this.config = null;
        this.entityId = label;
        this.onSave = onSave;
        alpha = (selected >>> 24) & 255;
        setRgb(selected & 0xFFFFFF);
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        addDrawableChild(new Channel(this, left, 155, "Красный", red, 0));
        addDrawableChild(new Channel(this, left, 180, "Зелёный", green, 1));
        addDrawableChild(new Channel(this, left, 205, "Синий", blue, 2));
        addDrawableChild(new Channel(this, left, 230, "Прозрачность", alpha, 3));
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> {
            if (onSave != null) onSave.accept(color()); else putColor();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(left, 265, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),
                b -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(left + 160, 265, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Пресет: ярко-жёлтый"), b -> {
            setRgb(255, 220, 40);
            alpha = 255;
            rebuildPicker();
        }).dimensions(left, 295, 310, 20).build());
    }

    private void rebuildPicker() { clearChildren(); init(); }

    private void putColor() {
        StringBuilder result = new StringBuilder();
        if (config.customMobColors != null) {
            for (String item : config.customMobColors.split(",")) {
                String[] pair = item.trim().split("=", 2);
                if (pair.length == 2 && !pair[0].trim().equalsIgnoreCase(entityId)) {
                    if (result.length() > 0) result.append(',');
                    result.append(item.trim());
                }
            }
        }
        if (result.length() > 0) result.append(',');
        result.append(entityId).append("=#").append(String.format("%08X", color()));
        config.customMobColors = result.toString();
        config.save();
    }

    private int color() { return (alpha << 24) | (red << 16) | (green << 8) | blue; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Цвет: " + entityId),
                width / 2, 25, 0xFFE8D7FF);
        drawColorWheel(context, width / 2, 90);
        context.fill(width / 2 - 155, 125, width / 2 + 155, 145, color());
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(entityId),
                width / 2, 340, color());
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(String.format("Предпросмотр  #%08X", color())),
                width / 2, 355, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawColorWheel(DrawContext context, int cx, int cy) {
        int radius = 52;
        for (int y = -radius; y <= radius; y += 2) {
            for (int x = -radius; x <= radius; x += 2) {
                double distance = Math.sqrt(x * x + y * y);
                if (distance > radius || distance < radius * 0.63) continue;
                float selectedHue = (float) ((Math.atan2(y, x) / (Math.PI * 2.0) + 0.5) % 1.0);
                context.fill(cx + x, cy + y, cx + x + 2, cy + y + 2,
                        0xFF000000 | hsvToRgb(selectedHue, 1.0f, 1.0f));
            }
        }
        int markerX = cx + Math.round((float) Math.cos(hue * Math.PI * 2.0) * 42);
        int markerY = cy + Math.round((float) Math.sin(hue * Math.PI * 2.0) * 42);
        context.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - 90.0;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance >= 33 && distance <= 55) {
            hue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2.0) + 0.5) % 1.0);
            setRgb(hsvToRgb(hue, saturation, brightness));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setRgb(int packed) {
        red = (packed >>> 16) & 255;
        green = (packed >>> 8) & 255;
        blue = packed & 255;
        float[] hsv = rgbToHsv(red, green, blue);
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
    }

    private void setRgb(int r, int g, int b) { setRgb((r << 16) | (g << 8) | b); }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float d = max - min, h = 0;
        if (d != 0) {
            if (max == rf) h = ((gf - bf) / d) % 6f;
            else if (max == gf) h = (bf - rf) / d + 2f;
            else h = (rf - gf) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float r, g, b;
        int sector = (int) (h * 6) % 6;
        if (sector == 0) { r = c; g = x; b = 0; }
        else if (sector == 1) { r = x; g = c; b = 0; }
        else if (sector == 2) { r = 0; g = c; b = x; }
        else if (sector == 3) { r = 0; g = x; b = c; }
        else if (sector == 4) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8)
                | (int) ((b + m) * 255);
    }

    private static final class Channel extends SliderWidget {
        private final MobColorPickerScreen screen;
        private final String label;
        private final int channel;

        Channel(MobColorPickerScreen screen, int x, int y, String label, int value, int channel) {
            super(x, y, 310, 20, Text.empty(), value / 255.0);
            this.screen = screen;
            this.label = label;
            this.channel = channel;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Text.literal(label + ": " + Math.round(value * 255)));
        }

        @Override protected void applyValue() {
            int current = (int) Math.round(value * 255);
            if (channel == 0) screen.red = current;
            else if (channel == 1) screen.green = current;
            else if (channel == 2) screen.blue = current;
            else screen.alpha = current;
        }
    }
}