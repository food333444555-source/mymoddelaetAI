package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Visual editor for the ten ID or percentage color bands. No rule syntax is
 * exposed to the player: thresholds are sliders and colors open the same
 * picker used by the mob-color menu.
 */
public final class ColorRulesScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final boolean idMode;
    private final double[] limits = new double[10];
    private final int[] colors = new int[10];
    private final boolean[] enabled = new boolean[10];

    public ColorRulesScreen(Screen parent, MobOverlayConfig config, boolean idMode) {
        super(Text.literal(idMode ? "Цвета по ID" : "Цвета по проценту"));
        this.parent = parent;
        this.config = config;
        this.idMode = idMode;
        loadRules(idMode ? config.idColorRules : config.percentColorRules);
    }

    @Override
    protected void init() {
        clearChildren();
        int left = width / 2 - 205;
        for (int i = 0; i < 10; i++) {
            int y = 42 + i * 24;
            final int index = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((enabled[i] ? "✓ " : "○ ")
                            + "Правило " + (i + 1)), b -> {
                enabled[index] = !enabled[index];
                init();
            }).dimensions(left, y, 105, 20).build());
            addDrawableChild(new LimitSlider(left + 110, y, 160, limits[i], index));
            addDrawableChild(ButtonWidget.builder(Text.literal("Цвет"),
                    b -> MinecraftClient.getInstance().setScreen(
                            new MobColorPickerScreen(this, idMode ? "ID " + (index + 1) : "% " + (index + 1),
                                    colors[index], color -> {
                                colors[index] = color;
                                init();
                            }))).dimensions(left + 275, y, 95, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),
                b -> { saveRules(); close(); }).dimensions(left, 290, 180, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"),
                b -> close()).dimensions(left + 190, 290, 180, 20).build());
    }

    private final class LimitSlider extends SliderWidget {
        private final int index;
        LimitSlider(int x, int y, int width, double limit, int index) {
            super(x, y, width, 20, Text.empty(), idMode ? limit / 100000.0 : limit / 100.0);
            this.index = index;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Text.literal(idMode
                    ? String.format(Locale.ROOT, "ID ≤ %.0f", limits[index])
                    : String.format(Locale.ROOT, "Процент ≤ %.2f%%", limits[index])));
        }
        @Override protected void applyValue() {
            limits[index] = idMode
                    ? Math.round(value * 100000.0)
                    : Math.round(value * 10000.0) / 100.0;
            updateMessage();
        }
    }

    private void loadRules(String raw) {
        String[] entries = raw == null ? new String[0] : raw.split(";");
        for (int i = 0; i < Math.min(10, entries.length); i++) {
            String[] pair = entries[i].split("=", 2);
            if (pair.length != 2) continue;
            try {
                String condition = pair[0].trim().replace(" ", "");
                String prefix = idMode ? "id" : "percent";
                if (!condition.startsWith(prefix)) continue;
                String expression = condition.substring(prefix.length());
                if (expression.startsWith("<=") || expression.startsWith(">="))
                    expression = expression.substring(2);
                else if (expression.startsWith("<") || expression.startsWith(">") || expression.startsWith("="))
                    expression = expression.substring(1);
                else continue;
                limits[i] = Double.parseDouble(expression);
                String hex = pair[1].trim().replace("#", "").replace("0x", "").replace("0X", "");
                long parsed = Long.parseLong(hex, 16);
                colors[i] = hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
                enabled[i] = true;
            } catch (NumberFormatException ignored) { }
        }
        for (int i = 0; i < 10; i++) {
            if (colors[i] == 0) colors[i] = idMode ? 0xFFC855E8 : 0xFFFFB000;
        }
    }

    private void saveRules() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 10; i++) if (enabled[i]) {
            if (result.length() > 0) result.append(';');
            result.append(idMode ? "id<" : "percent<")
                    .append(idMode ? String.format(Locale.ROOT, "%.0f", limits[i])
                            : String.format(Locale.ROOT, "%.2f", limits[i]))
                    .append("=#").append(String.format("%08X", colors[i]));
        }
        if (idMode) config.idColorRules = result.toString();
        else config.percentColorRules = result.toString();
        config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(idMode ? "Цвета по ID — приоритет 1" : "Цвета по проценту — приоритет 2"),
                width / 2, 15, 0xFFE8D7FF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Переключатель — включить правило, ползунок — порог, «Цвет» — палитра"),
                width / 2, 28, 0xFFB9A7C9);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}