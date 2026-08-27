package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Compact in-game settings panel. The values are intentionally written to the
 * same JSON config as the renderer, so changing them does not require a mod
 * restart.
 */
public final class MobSettingsScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final MobOverlayState state;
    private int waitingForKey;
    private ButtonWidget hudKeyButton;
    private ButtonWidget chunksKeyButton;
    private ButtonWidget mobHighlightsKeyButton;
    private ButtonWidget settingsKeyButton;
    private TextFieldWidget pinnedTypesField;
    private TextFieldWidget highlightTypesField;
    private TextFieldWidget customColorsField;
    private TextFieldWidget chunkRulesField;
    private int scrollOffset;

    public MobSettingsScreen(Screen parent, MobOverlayConfig config, MobOverlayState state) {
        super(Text.literal("DF Mob Visualizer — настройки"));
        this.parent = parent;
        this.config = config;
        this.state = state;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int y = 45;
        addDrawableChild(ButtonWidget.builder(Text.literal("Мод: "
                        + (config.enabled ? "включён" : "выключен")),
                button -> {
                    config.enabled = !config.enabled;
                    button.setMessage(Text.literal("Мод: "
                            + (config.enabled ? "включён" : "выключен")));
                    save();
                }).dimensions(left, y, 310, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Чанки только по правилам: "
                        + (config.markOnlyRuleChunks ? "да" : "нет")),
                button -> {
                    config.markOnlyRuleChunks = !config.markOnlyRuleChunks;
                    button.setMessage(Text.literal("Чанки только по правилам: "
                            + (config.markOnlyRuleChunks ? "да" : "нет")));
                    save();
                }).dimensions(left, y, 310, 20).build());
        y += 25;
        chunkRulesField = new TextFieldWidget(textRenderer, left, y, 310, 20,
                Text.literal("Правила цветов чанков"));
        chunkRulesField.setMaxLength(4000);
        chunkRulesField.setText(config.chunkColorRules == null ? "" : config.chunkColorRules);
        chunkRulesField.setPlaceholder(Text.literal("id<50=#C855E8FF;percent<5=#FFFF2020"));
        addDrawableChild(chunkRulesField);
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Размер HUD: ", config.hudScale,
                0.5, 2.0, 100));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Размер текста: ", config.hudTextScale,
                0.5, 2.0, 100));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Ширина окна: ", config.hudWidth,
                220, 1200, 0));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Прозрачность заливки: ",
                config.chunkOpacity, 0.0, 1.0, 100));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Прозрачность границы: ",
                config.chunkBorderOpacity, 0.0, 1.0, 100));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Порог сессии (%): ",
                config.sessionPercentLimit, 0.0, 100.0, 1));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Интервал сканирования (тики): ",
                config.scanIntervalTicks, 1.0, 100.0, 0));
        y += 28;
        addDrawableChild(new Slider(this, left, y, 310, 20, "Дальность чанков: ",
                config.renderDistanceChunks, 1.0, 64.0, 1));
        y += 25;
        pinnedTypesField = new TextFieldWidget(textRenderer, left, y, 310, 20,
                Text.literal("Типы мобов для сессии"));
        pinnedTypesField.setMaxLength(1000);
        pinnedTypesField.setText(config.pinnedEntityTypes == null ? "" : config.pinnedEntityTypes);
        pinnedTypesField.setPlaceholder(Text.literal("minecraft:zombie, minecraft:creeper"));
        addDrawableChild(pinnedTypesField);
        y += 25;
        highlightTypesField = new TextFieldWidget(textRenderer, left, y, 310, 20,
                Text.literal("Мобы для подсветки через блоки"));
        highlightTypesField.setMaxLength(2000);
        highlightTypesField.setText(config.highlightEntityTypes == null ? "" : config.highlightEntityTypes);
        highlightTypesField.setPlaceholder(Text.literal("Оставь пустым для категорий или: zombie, creeper"));
        addDrawableChild(highlightTypesField);
        y += 25;
        customColorsField = new TextFieldWidget(textRenderer, left, y, 310, 20,
                Text.literal("Цвета мобов"));
        customColorsField.setMaxLength(2000);
        customColorsField.setText(config.customMobColors == null ? "" : config.customMobColors);
        customColorsField.setPlaceholder(Text.literal("minecraft:zombie=#FFFF0000, minecraft:creeper=#FF00FF00"));
        addDrawableChild(customColorsField);
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранять сессию: "
                        + (config.persistSession ? "да" : "нет")), button -> {
                    config.persistSession = !config.persistSession;
                    button.setMessage(Text.literal("Сохранять сессию: "
                            + (config.persistSession ? "да" : "нет")));
                    save();
                }).dimensions(left, y, 310, 20).build());
        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal(sessionLabel()), button -> {
            config.sessionEnabled = !config.sessionEnabled;
            button.setMessage(Text.literal(sessionLabel()));
            save();
        }).dimensions(left, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("F7: " + (config.seeThroughMobs ? "мобы включены" : "мобы выключены")),
                button -> {
                    config.seeThroughMobs = !config.seeThroughMobs;
                    button.setMessage(Text.literal("F7: " + (config.seeThroughMobs ? "мобы включены" : "мобы выключены")));
                    save();
                }).dimensions(left + 160, y, 150, 20).build());
        y += 25;
        addDrawableChild(toggle(left, y, 100, "F7 сессия", () -> config.highlightSessionMobs));
        addDrawableChild(toggle(left + 105, y, 100, "F7 alert", () -> config.highlightAlertMobs));
        addDrawableChild(toggle(left + 210, y, 100, "F7 низкий ID", () -> config.highlightLowIds));
        y += 25;
        addDrawableChild(toggle(left, y, 100, "F7 урон", () -> config.highlightHurtMobs));
        addDrawableChild(toggle(left + 105, y, 100, "F7 hostile", () -> config.highlightHostileMobs));
        addDrawableChild(toggle(left + 210, y, 100, "F7 игроки", () -> config.highlightPlayers));
        y += 25;
        addDrawableChild(toggle(left, y, 100, "Центр alert", () -> config.centerAlertMobs));
        addDrawableChild(toggle(left + 105, y, 100, "Центр сессия", () -> config.centerSessionMobs));
        addDrawableChild(toggle(left + 210, y, 100, "Центр низкий ID", () -> config.centerLowIds));
        y += 25;
        addDrawableChild(toggle(left, y, 100, "Центр урон", () -> config.centerHurtMobs));
        addDrawableChild(toggle(left + 105, y, 100, "Центр hostile", () -> config.centerHostileMobs));
        addDrawableChild(toggle(left + 210, y, 100, "Центр игроки", () -> config.centerPlayers));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 150, 20, "Порог F7 (%): ",
                config.highlightPercentLimit, 0.0, 100.0, 1));
        addDrawableChild(new Slider(this, left + 160, y, 150, 20, "Порог центра (%): ",
                config.centerPercentLimit, 0.0, 100.0, 1));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 150, 20, "Тёмно-красный до (%): ",
                config.darkRedPercent, 0.0, 100.0, 1));
        addDrawableChild(new Slider(this, left + 160, y, 150, 20, "Красный до (%): ",
                config.redPercent, 0.0, 100.0, 1));
        y += 25;
        addDrawableChild(new Slider(this, left, y, 150, 20, "Тёмно-оранжевый до (%): ",
                config.darkOrangePercent, 0.0, 100.0, 1));
        addDrawableChild(new Slider(this, left + 160, y, 150, 20, "Оранжевый до (%): ",
                config.orangePercent, 0.0, 100.0, 1));
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Закреплять: " + pinLabel()), button -> {
            rotatePins();
            button.setMessage(Text.literal("Закреплять: " + pinLabel()));
            save();
        }).dimensions(left, y, 310, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Цвета ID: " + colorLabel()), button -> {
            cycleColors();
            button.setMessage(Text.literal("Цвета ID: " + colorLabel()));
            save();
        }).dimensions(left, y, 310, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сортировка: " + sortLabel()), button -> {
            config.hudSortMode = (config.hudSortMode + 1) % 3;
            button.setMessage(Text.literal("Сортировка: " + sortLabel()));
            save();
        }).dimensions(left, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Фильтр: " + filterLabel()), button -> {
            config.hostileOnly = !config.hostileOnly;
            button.setMessage(Text.literal("Фильтр: " + filterLabel()));
            save();
        }).dimensions(left + 160, y, 150, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Игроки: " + (config.showPlayers ? "да" : "нет")),
                button -> {
                    config.showPlayers = !config.showPlayers;
                    button.setMessage(Text.literal("Игроки: " + (config.showPlayers ? "да" : "нет")));
                    save();
                }).dimensions(left, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Другие сущности: " + (config.includeOtherEntities ? "да" : "нет")),
                button -> {
                    config.includeOtherEntities = !config.includeOtherEntities;
                    button.setMessage(Text.literal("Другие сущности: " + (config.includeOtherEntities ? "да" : "нет")));
                    save();
                }).dimensions(left + 160, y, 150, 20).build());
        y += 25;
        hudKeyButton = addKeyButton(left, y, "HUD", config.hudKey, 1);
        chunksKeyButton = addKeyButton(left + 160, y, "Чанки", config.chunksKey, 2);
        y += 25;
        mobHighlightsKeyButton = addKeyButton(left, y, "Мобы", config.mobHighlightsKey, 3);
        settingsKeyButton = addKeyButton(left + 160, y, "Настройки", config.settingsKey, 4);
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Клавиша очистки сессии"),
                button -> {
                    waitingForKey = 5;
                    refreshKeyLabels();
                }).dimensions(left, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Клавиша очистки чанков"),
                button -> {
                    waitingForKey = 6;
                    refreshKeyLabels();
                }).dimensions(left + 160, y, 150, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить клавиши F7/F8/F9/F10"),
                button -> {
                    config.mobHighlightsKey = GLFW.GLFW_KEY_F7;
                    config.hudKey = GLFW.GLFW_KEY_F8;
                    config.chunksKey = GLFW.GLFW_KEY_F9;
                    config.settingsKey = GLFW.GLFW_KEY_F10;
                    config.clearSessionKey = GLFW.GLFW_KEY_UNKNOWN;
                    config.clearChunksKey = GLFW.GLFW_KEY_UNKNOWN;
                    C2MEmod.applyKeyConfig(config);
                    refreshKeyLabels();
                    save();
                }).dimensions(left, y, 310, 20).build());
        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Очистить сессию (" + state.sessionCount() + ")"),
                button -> { state.clearSession(); save(); }).dimensions(left, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Очистить историю чанков"),
                button -> { state.clearChunks(); save(); }).dimensions(left + 160, y, 150, 20).build());
        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить MAX ID"),
                button -> { state.clearMaxId(); save(); }).dimensions(left, y, 150, 20).build());
        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> close())
                .dimensions(left, y, 310, 20).build());
    }

    private String sessionLabel() {
        return "Сессия: " + (config.sessionEnabled ? "включена" : "выключена");
    }

    private String pinLabel() {
        return (config.pinLowIds ? "низкий ID, " : "")
                + (config.pinHurtMobs ? "урон, " : "")
                + (config.pinHostileMobs ? "враждебные, " : "")
                + (config.pinPlayers ? "игроки" : "ничего");
    }

    private void rotatePins() {
        if (config.pinLowIds && config.pinHurtMobs && config.pinPlayers && !config.pinHostileMobs) {
            config.pinHostileMobs = true;
        } else if (config.pinHostileMobs) {
            config.pinLowIds = config.pinHurtMobs = config.pinHostileMobs = config.pinPlayers = false;
        } else {
            config.pinLowIds = config.pinHurtMobs = config.pinPlayers = true;
        }
    }

    private String colorLabel() {
        return "низкий / красный / оранжевый";
    }

    private String sortLabel() {
        return config.hudSortMode == 1 ? "тип" : config.hudSortMode == 2 ? "опасность" : "ID";
    }

    private String filterLabel() {
        return config.hostileOnly ? "только враждебные" : "все мобы";
    }

    private void cycleColors() {
        int purple = config.purpleColor;
        config.purpleColor = config.darkRedColor;
        config.darkRedColor = config.redColor;
        config.redColor = config.darkOrangeColor;
        config.darkOrangeColor = config.orangeColor;
        config.orangeColor = purple;
    }

    private ButtonWidget addKeyButton(int x, int y, String label, int key, int target) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label + ": " + keyName(key)),
                ignored -> {
                    waitingForKey = target;
                    refreshKeyLabels();
                }).dimensions(x, y, 150, 20).build();
        addDrawableChild(button);
        return button;
    }

    private ButtonWidget toggle(int x, int y, int width, String label,
                                java.util.function.BooleanSupplier getter) {
        return ButtonWidget.builder(Text.literal(label + ": " + (getter.getAsBoolean() ? "да" : "нет")),
                button -> {
                    if (label.equals("F7 сессия")) config.highlightSessionMobs = !config.highlightSessionMobs;
                    else if (label.equals("F7 alert")) config.highlightAlertMobs = !config.highlightAlertMobs;
                    else if (label.equals("F7 низкий ID")) config.highlightLowIds = !config.highlightLowIds;
                    else if (label.equals("F7 урон")) config.highlightHurtMobs = !config.highlightHurtMobs;
                    else if (label.equals("F7 hostile")) config.highlightHostileMobs = !config.highlightHostileMobs;
                    else if (label.equals("F7 игроки")) config.highlightPlayers = !config.highlightPlayers;
                    else if (label.equals("Центр alert")) config.centerAlertMobs = !config.centerAlertMobs;
                    else if (label.equals("Центр сессия")) config.centerSessionMobs = !config.centerSessionMobs;
                    else if (label.equals("Центр низкий ID")) config.centerLowIds = !config.centerLowIds;
                    else if (label.equals("Центр урон")) config.centerHurtMobs = !config.centerHurtMobs;
                    else if (label.equals("Центр hostile")) config.centerHostileMobs = !config.centerHostileMobs;
                    else if (label.equals("Центр игроки")) config.centerPlayers = !config.centerPlayers;
                    button.setMessage(Text.literal(label + ": " + (getter.getAsBoolean() ? "да" : "нет")));
                    save();
                }).dimensions(x, y, width, 20).build();
    }

    private String keyName(int key) {
        return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }

    private void refreshKeyLabels() {
        if (hudKeyButton != null) hudKeyButton.setMessage(Text.literal((waitingForKey == 1 ? "Нажмите клавишу: " : "HUD: ") + keyName(config.hudKey)));
        if (chunksKeyButton != null) chunksKeyButton.setMessage(Text.literal((waitingForKey == 2 ? "Нажмите клавишу: " : "Чанки: ") + keyName(config.chunksKey)));
        if (mobHighlightsKeyButton != null) mobHighlightsKeyButton.setMessage(Text.literal((waitingForKey == 3 ? "Нажмите клавишу: " : "Мобы: ") + keyName(config.mobHighlightsKey)));
        if (settingsKeyButton != null) settingsKeyButton.setMessage(Text.literal((waitingForKey == 4 ? "Нажмите клавишу: " : "Настройки: ") + keyName(config.settingsKey)));
    }

    private void save() {
        config.normalize();
        config.save();
    }

    @Override
    public void close() {
        if (pinnedTypesField != null) config.pinnedEntityTypes = pinnedTypesField.getText();
        if (highlightTypesField != null) config.highlightEntityTypes = highlightTypesField.getText();
        if (customColorsField != null) config.customMobColors = customColorsField.getText();
        if (chunkRulesField != null) config.chunkColorRules = chunkRulesField.getText();
        save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingForKey != 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waitingForKey = 0;
                refreshKeyLabels();
                return true;
            }
            if (waitingForKey == 1) config.hudKey = keyCode;
            else if (waitingForKey == 2) config.chunksKey = keyCode;
            else if (waitingForKey == 3) config.mobHighlightsKey = keyCode;
            else if (waitingForKey == 4) config.settingsKey = keyCode;
            else if (waitingForKey == 5) config.clearSessionKey = keyCode;
            else config.clearChunksKey = keyCode;
            waitingForKey = 0;
            C2MEmod.applyKeyConfig(config);
            refreshKeyLabels();
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFE8D7FF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("F7 — подсветка мобов | F9 — чанки | F8 — HUD"),
                width / 2, height - 28, 0xFFB9A7C9);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int shift = verticalAmount > 0 ? 24 : -24;
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int next = Math.max(-430, Math.min(0, scrollOffset + shift));
        int actualShift = next - scrollOffset;
        if (actualShift == 0) return true;
        scrollOffset = next;
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
                widget.setY(widget.getY() + actualShift);
            }
        }
        return true;
    }

    private static final class Slider extends SliderWidget {
        private final MobSettingsScreen screen;
        private final String label;
        private final double min;
        private final double max;
        private final int decimals;

        Slider(MobSettingsScreen screen, int x, int y, int width, int height, String label,
               double value, double min, double max, int decimals) {
            super(x, y, width, height, Text.empty(), (value - min) / (max - min));
            this.screen = screen;
            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            updateMessage();
        }

        private double actual() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + String.format("%." + decimals + "f", actual())));
        }

        @Override
        protected void applyValue() {
            double actual = actual();
            if (label.startsWith("Размер")) screen.config.hudScale = (float) actual;
            else if (label.startsWith("Размер текста")) screen.config.hudTextScale = (float) actual;
            else if (label.startsWith("Ширина окна")) screen.config.hudWidth = (int) Math.round(actual);
            else if (label.startsWith("Прозрачность заливки")) screen.config.chunkOpacity = (float) actual;
            else if (label.startsWith("Прозрачность границы")) screen.config.chunkBorderOpacity = (float) actual;
            else if (label.startsWith("Порог сессии")) screen.config.sessionPercentLimit = actual;
            else if (label.startsWith("Порог F7")) screen.config.highlightPercentLimit = actual;
            else if (label.startsWith("Порог центра")) screen.config.centerPercentLimit = actual;
            else if (label.startsWith("Тёмно-красный")) screen.config.darkRedPercent = actual;
            else if (label.startsWith("Красный")) screen.config.redPercent = actual;
            else if (label.startsWith("Тёмно-оранжевый")) screen.config.darkOrangePercent = actual;
            else if (label.startsWith("Оранжевый")) screen.config.orangePercent = actual;
            else if (label.startsWith("Дальность чанков")) screen.config.renderDistanceChunks = actual;
            else screen.config.scanIntervalTicks = (int) Math.round(actual);
            screen.config.save();
        }
    }
}