package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MobSettingsScreenV2 extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final MobOverlayState state;
    private String page = "main";
    private int listPage;
    private int waitingForKey;
    private final List<String> entityIds = new ArrayList<>();
    private TextFieldWidget entitySearchField;
    private TextFieldWidget idRulesField;
    private TextFieldWidget percentRulesField;
    private TextFieldWidget alertGapField;
    private TextFieldWidget alertPercentField;
    private TextFieldWidget alertTypesField;
    private TextFieldWidget returnedTypesField;

    public MobSettingsScreenV2(Screen parent, MobOverlayConfig config, MobOverlayState state) {
        super(Text.literal("DF Mob Visualizer"));
        this.parent = parent;
        this.config = config;
        this.state = state;
        entityIds.add("minecraft:charged_creeper");
        Registries.ENTITY_TYPE.getIds().stream().map(Object::toString)
                .sorted().forEach(entityIds::add);
    }

    @Override
    protected void init() {
        clearChildren();
        int left = width / 2 - 155;
        if (page.equals("main")) {
            button(left, 55, 310, "Общие настройки", b -> open("general"));
            button(left, 80, 310, "HUD и отображение", b -> open("hud"));
            button(left, 105, 310, "ALERT мобы", b -> open("alert"));
            button(left, 130, 310, "RETURNED мобы", b -> open("returned"));
            button(left, 155, 310, "HURT (раненые)", b -> open("hurt"));
            button(left, 180, 310, "Подсветка через стены", b -> open("highlight"));
            button(left, 205, 310, "Центрирование", b -> open("center"));
            button(left, 230, 310, "Цвета мобов", b -> open("colors"));
            button(left, 255, 310, "Клавиши / бинды", b -> open("keys"));
            button(left, 280, 310, "Цвета по ID и проценту", b -> open("idcolors"));
            button(left, 305, 310, "Очистка данных", b -> open("cleanup"));
            button(left, 330, 310, "Готово", b -> close());
            return;
        }
        button(left, 35, 90, "← Разделы", b -> open("main"));
        
        if (page.equals("alert")) {
            buildAlert(left);
        } else if (page.equals("returned")) {
            buildReturned(left);
        } else if (page.equals("hurt")) {
            buildHurt(left);
        } else if (page.equals("highlight")) {
            buildHighlight(left);
        } else if (page.equals("center")) {
            buildCenter(left);
        } else if (page.equals("keys")) {
            buildKeys(left);
        } else if (page.equals("general")) {
            buildGeneral(left);
        } else if (page.equals("idcolors")) {
            buildIdColors(left);
        } else if (page.equals("hud")) {
            buildHud(left);
        } else if (page.equals("colors")) {
            buildColors(left);
        } else {
            buildCleanup(left);
        }
    }

    private void buildAlert(int left) {
        toggle(left, 65, "ALERT включён", config.alertEnabled, () -> config.alertEnabled = !config.alertEnabled);
        toggle(left, 90, "Режим: " + (config.alertMode == 0 ? "разница ID" : "процент"),
                true, () -> config.alertMode = config.alertMode == 0 ? 1 : 0);
        
        alertGapField = new TextFieldWidget(textRenderer, left, 115, 150, 20, Text.literal("Разница ID"));
        alertGapField.setText(Integer.toString(config.alertGap));
        addDrawableChild(alertGapField);
        
        alertPercentField = new TextFieldWidget(textRenderer, left + 160, 115, 150, 20, Text.literal("Процент"));
        alertPercentField.setText(Double.toString(config.alertPercent));
        addDrawableChild(alertPercentField);
        
        alertTypesField = new TextFieldWidget(textRenderer, left, 145, 310, 20, Text.literal("Типы мобов для ALERT"));
        alertTypesField.setMaxLength(4000);
        alertTypesField.setText(config.alertEntityTypes == null ? "" : config.alertEntityTypes);
        alertTypesField.setPlaceholder(Text.literal("zombie, creeper, skeleton (пусто = все)"));
        addDrawableChild(alertTypesField);
        
        toggle(left, 175, "Добавлять в сессию", config.alertAddToSession, () -> config.alertAddToSession = !config.alertAddToSession);
        toggle(left, 200, "Центрировать", config.alertCenter, () -> config.alertCenter = !config.alertCenter);
        toggle(left, 225, "Подсвечивать через стены", config.alertHighlight, () -> config.alertHighlight = !config.alertHighlight);
    }

    private void buildReturned(int left) {
        toggle(left, 65, "RETURNED включён", config.returnedEnabled, () -> config.returnedEnabled = !config.returnedEnabled);
        
        returnedTypesField = new TextFieldWidget(textRenderer, left, 90, 310, 20, Text.literal("Типы мобов для RETURNED"));
        returnedTypesField.setMaxLength(4000);
        returnedTypesField.setText(config.returnedEntityTypes == null ? "" : config.returnedEntityTypes);
        returnedTypesField.setPlaceholder(Text.literal("zombie, creeper, skeleton (пусто = все)"));
        addDrawableChild(returnedTypesField);
        
        toggle(left, 120, "Добавлять в сессию", config.returnedAddToSession, () -> config.returnedAddToSession = !config.returnedAddToSession);
        toggle(left, 145, "Центрировать", config.returnedCenter, () -> config.returnedCenter = !config.returnedCenter);
        toggle(left, 170, "Подсвечивать через стены", config.returnedHighlight, () -> config.returnedHighlight = !config.returnedHighlight);
    }

    private void buildHurt(int left) {
        toggle(left, 65, "HURT включён", config.hurtEnabled, () -> config.hurtEnabled = !config.hurtEnabled);
        toggle(left, 90, "Добавлять в сессию", config.hurtAddToSession, () -> config.hurtAddToSession = !config.hurtAddToSession);
        toggle(left, 115, "Центрировать", config.hurtCenter, () -> config.hurtCenter = !config.hurtCenter);
        toggle(left, 140, "Подсвечивать через стены", config.hurtHighlight, () -> config.hurtHighlight = !config.hurtHighlight);
        
        button(left, 165, 150, "Цвет HURT: " + hex(config.hurtColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT", config.hurtColor, color -> {
                    config.hurtColor = color;
                    save();
                    init();
                })));
        button(left + 160, 165, 150, "Цвет HURT*: " + hex(config.hurtStarColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT*", config.hurtStarColor, color -> {
                    config.hurtStarColor = color;
                    save();
                    init();
                })));
    }

    private void buildHighlight(int left) {
        toggle(left, 65, "Подсветка включена", config.seeThroughMobs, () -> config.seeThroughMobs = !config.seeThroughMobs);
        toggle(left, 90, "HURT (раненые)", config.highlightHurt, () -> config.highlightHurt = !config.highlightHurt);
        toggle(left, 115, "ALERT", config.highlightAlert, () -> config.highlightAlert = !config.highlightAlert);
        toggle(left, 140, "RETURNED (вернувшиеся)", config.highlightReturned, () -> config.highlightReturned = !config.highlightReturned);
        toggle(left, 165, "CHARGED (заряженные)", config.highlightCharged, () -> config.highlightCharged = !config.highlightCharged);
        toggle(left, 190, "RENAMED (переименованные)", config.highlightRenamed, () -> config.highlightRenamed = !config.highlightRenamed);
        toggle(left, 215, "Игроки", config.highlightPlayers, () -> config.highlightPlayers = !config.highlightPlayers);
        toggle(left, 240, "ALL (все мобы)", config.highlightAll, () -> config.highlightAll = !config.highlightAll);
    }

    private void buildCenter(int left) {
        toggle(left, 65, "ALERT мобы", config.centerAlertMobs, () -> config.centerAlertMobs = !config.centerAlertMobs);
        toggle(left, 90, "RETURNED мобы", config.centerReturnedMobs, () -> config.centerReturnedMobs = !config.centerReturnedMobs);
        toggle(left, 115, "HURT мобы", config.centerHurtMobs, () -> config.centerHurtMobs = !config.centerHurtMobs);
        toggle(left, 140, "Игроки", config.centerPlayers, () -> config.centerPlayers = !config.centerPlayers);
    }

    private void buildKeys(int left) {
        button(left, 65, 150, keyLabel("HUD", config.hudKey, config.hudScanCode, 1), b -> waitKey(1));
        button(left + 160, 65, 150, keyLabel("Мобы", config.mobHighlightsKey, config.mobHighlightsScanCode, 2), b -> waitKey(2));
        button(left, 90, 150, keyLabel("Настройки", config.settingsKey, config.settingsScanCode, 3), b -> waitKey(3));
        button(left + 160, 90, 150, keyLabel("Чанки", config.chunksKey, config.chunksScanCode, 4), b -> waitKey(4));
        button(left, 115, 150, keyLabel("Очистить сессию", config.clearSessionKey, config.clearSessionScanCode, 5), b -> waitKey(5));
        button(left + 160, 115, 150, keyLabel("Очистить чанки", config.clearChunksKey, config.clearChunksScanCode, 6), b -> waitKey(6));
        button(left, 150, 310, "Сбросить все клавиши", b -> {
            config.hudKey = GLFW.GLFW_KEY_F8; config.mobHighlightsKey = GLFW.GLFW_KEY_F7;
            config.settingsKey = GLFW.GLFW_KEY_F10; config.chunksKey = GLFW.GLFW_KEY_F9;
            config.clearSessionKey = GLFW.GLFW_KEY_F5; config.clearChunksKey = GLFW.GLFW_KEY_F6;
            config.hudScanCode = 0; config.mobHighlightsScanCode = 0;
            config.settingsScanCode = 0; config.chunksScanCode = 0;
            config.clearSessionScanCode = 0; config.clearChunksScanCode = 0;
            C2MEmod.applyKeyConfig(config); save(); init();
        });
        if (waitingForKey != 0) button(left, 185, 310, "Нажми клавишу (ESC — отмена)", b -> {});
    }

    private void buildGeneral(int left) {
        toggle(left, 65, "Мод", config.enabled, () -> config.enabled = !config.enabled);
        toggle(left, 90, "Сессия", config.sessionEnabled, () -> config.sessionEnabled = !config.sessionEnabled);
        toggle(left, 115, "Игроки в HUD", config.showPlayers, () -> config.showPlayers = !config.showPlayers);
        toggle(left, 140, "Другие сущности", config.includeOtherEntities, () -> config.includeOtherEntities = !config.includeOtherEntities);
        toggle(left, 165, "Сохранять сессию", config.persistSession, () -> config.persistSession = !config.persistSession);
    }

    private void buildIdColors(int left) {
        button(left, 65, 310, "Настроить цвета по ID (приоритет 1)", b ->
                MinecraftClient.getInstance().setScreen(new ColorRulesScreen(this, config, true)));
        button(left, 95, 310, "Настроить цвета по проценту (приоритет 2)", b ->
                MinecraftClient.getInstance().setScreen(new ColorRulesScreen(this, config, false)));
        button(left, 130, 310, "Сбросить правила цветов", b -> {
            config.idColorRules = "id<=10001=#C855E8FF";
            config.percentColorRules = "percent<30=#FFFF2020;percent<50=#FFFFB000";
            save();
        });
    }

    private void buildHud(int left) {
        toggle(left, 65, "HUD", config.showHud, () -> config.showHud = !config.showHud);
        toggle(left, 90, "Подсветка через стены", config.seeThroughMobs, () -> config.seeThroughMobs = !config.seeThroughMobs);
        toggle(left, 115, "Карта чанков", config.showChunkOverlay,
                () -> config.showChunkOverlay = !config.showChunkOverlay);
        toggle(left, 140, "Заливка чанков", config.showChunkFill,
                () -> config.showChunkFill = !config.showChunkFill);
        
        button(left, 165, 150, "Высота слоя: " + config.chunkYOffset + " блоков",
                b -> MinecraftClient.getInstance().setScreen(new HeightInputScreen(this, config, true)));
        
        button(left + 160, 165, 150, "Толщина: " + config.chunkHeight + " блок(ов)",
                b -> MinecraftClient.getInstance().setScreen(new HeightInputScreen(this, config, false)));
        
        button(left, 190, 310, "Прозрачность: " + percent(config.chunkOpacity),
                b -> { config.chunkOpacity = nextOpacity(config.chunkOpacity); save(); init(); });
        button(left, 215, 310, "Усиление: " + String.format(Locale.ROOT, "%.1fx", config.chunkFillStrength),
                b -> { config.chunkFillStrength = nextStrength(config.chunkFillStrength); save(); init(); });
        button(left, 240, 310, "Граница: " + percent(config.chunkBorderOpacity),
                b -> { config.chunkBorderOpacity = nextOpacity(config.chunkBorderOpacity); save(); init(); });
        toggle(left, 265, "Игроки в HUD", config.showPlayers, () -> config.showPlayers = !config.showPlayers);
        button(left, 290, 310, "Сбросить позицию HUD", b -> { config.hudX = 8; config.hudY = 8; save(); });
        button(left, 315, 310, "← Назад к разделам", b -> open("main"));
    }

    private void buildColors(int left) {
        button(left, 65, 310, "← Назад к разделам", b -> open("main"));
    }

    private void buildCleanup(int left) {
        button(left, 65, 310, "Очистить сессию (" + state.sessionCount() + ")", b -> { state.clearSession(); save(); });
        button(left, 90, 310, "Очистить историю чанков", b -> { state.clearChunks(); save(); });
        button(left, 115, 310, "Сбросить MAX ID", b -> { state.clearMaxId(); save(); });
    }

    private static String percent(float value) {
        return Math.round(value * 100.0f) + "%";
    }

    private static float nextOpacity(float value) {
        float[] values = {0.15f, 0.30f, 0.45f, 0.60f, 0.75f, 0.90f, 1.0f};
        for (float candidate : values) {
            if (value < candidate - 0.001f) return candidate;
        }
        return values[0];
    }

    private static float nextStrength(float value) {
        float[] values = {0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f};
        for (float candidate : values) {
            if (value < candidate - 0.001f) return candidate;
        }
        return values[0];
    }

    private static String hex(int color) {
        return String.format("#%08X", color);
    }

    private void toggle(int x, int y, String label, boolean value, Runnable action) {
        button(x, y, 310, label + ": " + (value ? "ВКЛ" : "ВЫКЛ"), b -> { action.run(); save(); init(); });
    }

    private void toggle(int x, int y, String label, boolean value, Runnable action, boolean dummy) {
        button(x, y, 310, label + ": " + (value ? "ВКЛ" : "ВЫКЛ"), b -> { action.run(); save(); init(); });
    }

    private void waitKey(int target) { waitingForKey = target; init(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingForKey == 0) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { waitingForKey = 0; init(); return true; }
        switch (waitingForKey) {
            case 1 -> { config.hudKey = keyCode; config.hudScanCode = scanCode; }
            case 2 -> { config.mobHighlightsKey = keyCode; config.mobHighlightsScanCode = scanCode; }
            case 3 -> { config.settingsKey = keyCode; config.settingsScanCode = scanCode; }
            case 4 -> { config.chunksKey = keyCode; config.chunksScanCode = scanCode; }
            case 5 -> { config.clearSessionKey = keyCode; config.clearSessionScanCode = scanCode; }
            case 6 -> { config.clearChunksKey = keyCode; config.clearChunksScanCode = scanCode; }
        }
        waitingForKey = 0;
        C2MEmod.applyKeyConfig(config);
        save(); init();
        return true;
    }

    private void open(String next) { page = next; listPage = 0; waitingForKey = 0; init(); }

    private void button(int x, int y, int w, String label, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, 20).build());
    }

    private String keyLabel(String name, int key, int scanCode, int target) {
        return (waitingForKey == target ? "Нажми: " : name + ": ") + keyName(key, scanCode);
    }

    private String keyName(int key, int scanCode) {
        if ((key == GLFW.GLFW_KEY_UNKNOWN || key == 0) && scanCode == 0) return "не назначено";
        if ((key == GLFW.GLFW_KEY_UNKNOWN || key == 0) && scanCode > 0) {
            return InputUtil.Type.SCANCODE.createFromCode(scanCode).getLocalizedText().getString();
        }
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(page.equals("main") ? "DF Mob Visualizer" : pageTitle()),
                width / 2, 15, 0xFFE8D7FF);
        super.render(context, mouseX, mouseY, delta);
    }

    private String pageTitle() {
        return switch (page) {
            case "alert" -> "ALERT мобы";
            case "returned" -> "RETURNED мобы";
            case "hurt" -> "HURT (раненые)";
            case "highlight" -> "Подсветка через стены";
            case "center" -> "Центрирование";
            case "keys" -> "Настройка клавиш";
            case "general" -> "Общие настройки";
            case "hud" -> "HUD и отображение";
            case "idcolors" -> "Цвета по ID и проценту";
            case "colors" -> "Цвета мобов";
            default -> "Очистка данных";
        };
    }

    private void save() {
        if (alertGapField != null) {
            try { config.alertGap = Integer.parseInt(alertGapField.getText().trim()); }
            catch (NumberFormatException ignored) { }
        }
        if (alertPercentField != null) {
            try { config.alertPercent = Double.parseDouble(alertPercentField.getText().trim()); }
            catch (NumberFormatException ignored) { }
        }
        if (alertTypesField != null) config.alertEntityTypes = alertTypesField.getText();
        if (returnedTypesField != null) config.returnedEntityTypes = returnedTypesField.getText();
        config.normalize();
        config.save();
    }

    @Override public void close() {
        save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
