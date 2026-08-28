package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
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
    private TextFieldWidget returnedDistanceField;

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
            button(left, 80, 310, "HUD — внешний вид", b -> open("hud_style"));
            button(left, 105, 310, "HUD — содержимое", b -> open("hud_content"));
            button(left, 130, 310, "ALERT мобы", b -> open("alert"));
            button(left, 155, 310, "RETURNED мобы", b -> open("returned"));
            button(left, 180, 310, "HURT (раненые)", b -> open("hurt"));
            button(left, 205, 310, "Центрирование / Центр", b -> open("center"));
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
        } else if (page.equals("center")) {
            buildCenter(left);
        } else if (page.equals("keys")) {
            buildKeys(left);
        } else if (page.equals("general")) {
            buildGeneral(left);
        } else if (page.equals("idcolors")) {
            buildIdColors(left);
        } else if (page.equals("hud_style")) {
            buildHudStyle(left);
        } else if (page.equals("hud_content")) {
            buildHudContent(left);
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

        button(left, 145, 310, "Типы мобов для ALERT" + countSummary(config.alertEntityTypes), b -> 
            MinecraftClient.getInstance().setScreen(new EntityPickerScreen(this, config, EntityPickerScreen.Mode.ALERT_TYPES)));

        button(left, 170, 310, "Выбор мобов для сессии", b -> MinecraftClient.getInstance().setScreen(
                new EntityPickerScreen(this, config, EntityPickerScreen.Mode.ALERT_SESSION)));
        toggle(left, 200, "Добавлять в сессию", config.alertAddToSession,
                () -> config.alertAddToSession = !config.alertAddToSession);
        toggle(left, 225, "Центрировать", config.alertCenter, () -> config.alertCenter = !config.alertCenter);

        button(left, 255, 150, "Цвет ALERT: " + hex(config.alertColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "ALERT", config.alertColor, color -> {
                    config.alertColor = color;
                    save();
                    init();
                })));
        button(left + 160, 255, 150, "Цвет тега ALERT: " + hex(config.hudAlertColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "ALERT HUD", config.hudAlertColor, color -> {
                    config.hudAlertColor = color;
                    save();
                    init();
                })));
    }

    private void buildReturned(int left) {
        toggle(left, 65, "RETURNED включён", config.returnedEnabled, () -> config.returnedEnabled = !config.returnedEnabled);

        button(left, 90, 310, "Типы мобов для RETURNED" + countSummary(config.returnedEntityTypes), b -> 
            MinecraftClient.getInstance().setScreen(new EntityPickerScreen(this, config, EntityPickerScreen.Mode.RETURNED_TYPES)));

        button(left, 120, 310, "Выбор мобов для сессии", b -> MinecraftClient.getInstance().setScreen(
                new EntityPickerScreen(this, config, EntityPickerScreen.Mode.RETURNED_SESSION)));
        returnedDistanceField = new TextFieldWidget(textRenderer, left, 150, 150, 20,
                Text.literal("Дистанция RETURNED"));
        returnedDistanceField.setText(Double.toString(config.returnedDistanceBlocks));
        returnedDistanceField.setPlaceholder(Text.literal("по X/Z, по умолчанию 86"));
        addDrawableChild(returnedDistanceField);
        toggle(left + 160, 150, "Добавлять в сессию", config.returnedAddToSession,
                () -> config.returnedAddToSession = !config.returnedAddToSession);
        toggle(left, 180, "Центрировать", config.returnedCenter,
                () -> config.returnedCenter = !config.returnedCenter);

        button(left, 210, 150, "Цвет RETURNED: " + hex(config.returnedColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "RETURNED", config.returnedColor, color -> {
                    config.returnedColor = color;
                    save();
                    init();
                })));
        button(left + 160, 210, 150, "Цвет тега RETURNED: " + hex(config.hudReturnedColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "RETURNED HUD", config.hudReturnedColor, color -> {
                    config.hudReturnedColor = color;
                    save();
                    init();
                })));
    }

    private void buildHurt(int left) {
        toggle(left, 65, "HURT включён", config.hurtEnabled, () -> config.hurtEnabled = !config.hurtEnabled);
        toggle(left, 90, "Добавлять в сессию", config.hurtAddToSession, () -> config.hurtAddToSession = !config.hurtAddToSession);
        toggle(left, 115, "Центрировать", config.hurtCenter, () -> config.hurtCenter = !config.hurtCenter);

        button(left, 145, 150, "Цвет HURT: " + hex(config.hurtColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT", config.hurtColor, color -> {
                    config.hurtColor = color;
                    save();
                    init();
                })));
        button(left + 160, 145, 150, "Цвет HURT*: " + hex(config.hurtStarColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT*", config.hurtStarColor, color -> {
                    config.hurtStarColor = color;
                    save();
                    init();
                })));
        button(left, 175, 310, "Цвет тега HURT: " + hex(config.hudHurtColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT HUD", config.hudHurtColor, color -> {
                    config.hudHurtColor = color;
                    save();
                    init();
                })));
    }

    private void buildCenter(int left) {
        toggle(left, 65, "Центр включён", config.centerEnabled, () -> config.centerEnabled = !config.centerEnabled);
        toggle(left, 90, "ALERT мобы", config.centerAlertMobs, () -> config.centerAlertMobs = !config.centerAlertMobs);
        toggle(left, 115, "RETURNED мобы", config.centerReturnedMobs, () -> config.centerReturnedMobs = !config.centerReturnedMobs);
        toggle(left, 140, "HURT мобы", config.centerHurtMobs, () -> config.centerHurtMobs = !config.centerHurtMobs);
        toggle(left, 165, "Игроки", config.centerPlayers, () -> config.centerPlayers = !config.centerPlayers);
        toggle(left, 190, "СЕССИЯ", config.centerSessionMobs, () -> config.centerSessionMobs = !config.centerSessionMobs);
        toggle(left, 215, "НИЗКИЙ ID", config.centerLowIds, () -> config.centerLowIds = !config.centerLowIds);
        toggle(left, 240, "ВРАЖДЕБНЫЕ", config.centerHostileMobs, () -> config.centerHostileMobs = !config.centerHostileMobs);

        button(left, 270, 310, "Таймаут: " + config.centerTimeoutSeconds + " сек",
                b -> { config.centerTimeoutSeconds = nextTimeout(config.centerTimeoutSeconds); save(); init(); });
        button(left, 295, 310, "Очистка по расстоянию: " + config.centerClearDistanceChunks + " чанков",
                b -> { config.centerClearDistanceChunks = nextClearDist(config.centerClearDistanceChunks); save(); init(); });
        button(left, 320, 310, "Мин. маркеров: " + config.centerMinMarkers,
                b -> { config.centerMinMarkers = nextMinMarkers(config.centerMinMarkers); save(); init(); });
        button(left, 345, 310, "Макс. разброс: " + config.centerMaxSpreadBlocks + " блоков",
                b -> { config.centerMaxSpreadBlocks = nextMaxSpread(config.centerMaxSpreadBlocks); save(); init(); });
    }

    private void buildHudStyle(int left) {
        toggle(left, 65, "Фон HUD", config.hudShowBackground, () -> config.hudShowBackground = !config.hudShowBackground);
        toggle(left, 90, "Системный шрифт", config.hudUseSystemFont, () -> config.hudUseSystemFont = !config.hudUseSystemFont);
        toggle(left, 115, "Тень текста", config.hudTextShadow, () -> config.hudTextShadow = !config.hudTextShadow);
        toggle(left, 140, "Индикаторы функций", config.hudShowIndicators, () -> config.hudShowIndicators = !config.hudShowIndicators);

        button(left, 165, 150, "Прозрачность фона: " + percent(config.hudBackgroundOpacity),
                b -> { config.hudBackgroundOpacity = nextOpacity(config.hudBackgroundOpacity); save(); init(); });
        button(left + 160, 165, 150, "Цвет фона: " + hex(config.hudBackgroundColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Фон HUD", config.hudBackgroundColor | 0xFF000000, color -> {
                    config.hudBackgroundColor = color & 0xFFFFFF;
                    save();
                    init();
                })));

        button(left, 190, 150, "Размер шрифта: " + String.format(Locale.ROOT, "%.1f", config.hudSystemFontSize),
                b -> { config.hudSystemFontSize = nextFontSize(config.hudSystemFontSize); save(); init(); });
        button(left + 160, 190, 150, "Масштаб HUD: " + String.format(Locale.ROOT, "%.1fx", config.hudScale),
                b -> { config.hudScale = nextHudScale(config.hudScale); save(); init(); });

        button(left, 215, 150, "Масштаб текста: " + String.format(Locale.ROOT, "%.1fx", config.hudTextScale),
                b -> { config.hudTextScale = nextHudScale(config.hudTextScale); save(); init(); });
        button(left + 160, 215, 150, "Ширина окна: " + config.hudWidth,
                b -> { config.hudWidth = nextHudWidth(config.hudWidth); save(); init(); });

        button(left, 240, 310, "Сбросить позицию HUD", b -> { config.hudX = 8; config.hudY = 8; save(); });
        button(left, 265, 310, "← Назад к разделам", b -> open("main"));
    }

    private void buildHudContent(int left) {
        button(left, 65, 150, "Цвет заголовка: " + hex(config.hudTitleColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Заголовок", config.hudTitleColor, color -> {
                    config.hudTitleColor = color;
                    save();
                    init();
                })));
        button(left + 160, 65, 150, "Цвет инфо: " + hex(config.hudInfoColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Инфо", config.hudInfoColor, color -> {
                    config.hudInfoColor = color;
                    save();
                    init();
                })));

        button(left, 90, 150, "Цвет подсказок: " + hex(config.hudHintColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Подсказки", config.hudHintColor, color -> {
                    config.hudHintColor = color;
                    save();
                    init();
                })));
        button(left + 160, 90, 150, "Цвет центра: " + hex(config.hudCenterColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Центр", config.hudCenterColor, color -> {
                    config.hudCenterColor = color;
                    save();
                    init();
                })));

        button(left, 115, 150, "Цвет индик. вкл: " + hex(config.hudIndicatorColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Индикатор ВКЛ", config.hudIndicatorColor, color -> {
                    config.hudIndicatorColor = color;
                    save();
                    init();
                })));
        button(left + 160, 115, 150, "Цвет индик. выкл: " + hex(config.hudIndicatorOffColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Индикатор ВЫКЛ", config.hudIndicatorOffColor, color -> {
                    config.hudIndicatorOffColor = color;
                    save();
                    init();
                })));

        button(left, 145, 150, "Цвет ALERT тега: " + hex(config.hudAlertColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "ALERT тег", config.hudAlertColor, color -> {
                    config.hudAlertColor = color;
                    save();
                    init();
                })));
        button(left + 160, 145, 150, "Цвет HURT тега: " + hex(config.hudHurtColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "HURT тег", config.hudHurtColor, color -> {
                    config.hudHurtColor = color;
                    save();
                    init();
                })));

        button(left, 170, 150, "Цвет RETURNED тега: " + hex(config.hudReturnedColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "RETURNED тег", config.hudReturnedColor, color -> {
                    config.hudReturnedColor = color;
                    save();
                    init();
                })));
        button(left + 160, 170, 150, "Цвет CHARGED тега: " + hex(config.hudChargedColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "CHARGED тег", config.hudChargedColor, color -> {
                    config.hudChargedColor = color;
                    save();
                    init();
                })));

        button(left, 195, 150, "Цвет RENAMED тега: " + hex(config.hudRenamedColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "RENAMED тег", config.hudRenamedColor, color -> {
                    config.hudRenamedColor = color;
                    save();
                    init();
                })));
        button(left + 160, 195, 150, "Цвет игроков: " + hex(config.hudPlayerColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Игроки", config.hudPlayerColor, color -> {
                    config.hudPlayerColor = color;
                    save();
                    init();
                })));

        button(left, 220, 310, "Цвет сессии: " + hex(config.hudSessionColor),
                b -> MinecraftClient.getInstance().setScreen(new MobColorPickerScreen(this, "Сессия", config.hudSessionColor, color -> {
                    config.hudSessionColor = color;
                    save();
                    init();
                })));

        button(left, 250, 310, "← Назад к разделам", b -> open("main"));
    }

    private void buildKeys(int left) {
        button(left, 65, 150, keyLabel("HUD", config.hudKey, config.hudScanCode, 1), b -> waitKey(1));
        button(left + 160, 65, 150, keyLabel("Настройки", config.settingsKey, config.settingsScanCode, 3), b -> waitKey(3));
        button(left, 90, 150, keyLabel("Чанки", config.chunksKey, config.chunksScanCode, 4), b -> waitKey(4));
        button(left + 160, 90, 150, keyLabel("Очистить сессию", config.clearSessionKey, config.clearSessionScanCode, 5), b -> waitKey(5));
        button(left, 115, 150, keyLabel("Очистить чанки", config.clearChunksKey, config.clearChunksScanCode, 6), b -> waitKey(6));
        button(left, 150, 310, "Сбросить все клавиши", b -> {
            config.hudKey = GLFW.GLFW_KEY_F8;
            config.settingsKey = GLFW.GLFW_KEY_F10; 
            config.chunksKey = GLFW.GLFW_KEY_F9;
            config.clearSessionKey = GLFW.GLFW_KEY_F5; 
            config.clearChunksKey = GLFW.GLFW_KEY_F6;
            config.hudScanCode = 0; 
            config.settingsScanCode = 0;
            config.chunksScanCode = 0;
            config.clearSessionScanCode = 0; 
            config.clearChunksScanCode = 0;
            C2MEmod.applyKeyConfig(config); 
            save(); 
            init();
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

    private void buildColors(int left) {
        button(left, 65, 310, "Открыть список всех сущностей", b -> MinecraftClient.getInstance().setScreen(
                new EntityPickerScreen(this, config, EntityPickerScreen.Mode.COLORS)));
        button(left, 95, 310, "← Назад к разделам", b -> open("main"));
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
        float[] values = {0.0f, 0.15f, 0.30f, 0.45f, 0.60f, 0.75f, 0.90f, 1.0f};
        for (float candidate : values) {
            if (value < candidate - 0.001f) return candidate;
        }
        return values[0];
    }

    private static float nextFontSize(float value) {
        float[] values = {6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 16.0f, 18.0f, 20.0f, 24.0f};
        for (float v : values) if (value < v - 0.001f) return v;
        return values[0];
    }

    private static float nextHudScale(float value) {
        float[] values = {0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2.0f};
        for (float v : values) if (value < v - 0.001f) return v;
        return values[0];
    }

    private static int nextHudWidth(int value) {
        int[] values = {220, 280, 320, 360, 400, 420, 480, 520, 560, 600, 700, 800, 1000, 1200};
        for (int v : values) if (value < v) return v;
        return values[0];
    }

    private static float nextStrength(float value) {
        float[] values = {0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f};
        for (float candidate : values) {
            if (value < candidate - 0.001f) return candidate;
        }
        return values[0];
    }

    private static int nextTimeout(int value) {
        int[] values = {10, 20, 30, 45, 60, 90, 120, 180, 300};
        for (int v : values) if (value < v) return v;
        return values[0];
    }

    private static int nextClearDist(int value) {
        int[] values = {1, 2, 4, 6, 8, 12, 16, 24, 32};
        for (int v : values) if (value < v) return v;
        return values[0];
    }

    private static int nextMinMarkers(int value) {
        int[] values = {1, 2, 3, 4, 5, 7, 10, 15, 20};
        for (int v : values) if (value < v) return v;
        return values[0];
    }

    private static int nextMaxSpread(int value) {
        int[] values = {16, 32, 64, 96, 128, 160, 192, 256, 384, 512};
        for (int v : values) if (value < v) return v;
        return values[0];
    }

    private static String hex(int color) {
        return String.format("#%08X", color);
    }

    private static String countSummary(String types) {
        if (types == null || types.isBlank()) return ": нет";
        int count = types.split(",").length;
        return ": " + count + " типов";
    }

    private void toggle(int x, int y, String label, boolean value, Runnable action) {
        button(x, y, 310, label + ": " + (value ? "ВКЛ" : "ВЫКЛ"), b -> { action.run(); save(); init(); });
    }

    private void waitKey(int target) { waitingForKey = target; init(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waitingForKey == 0) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { waitingForKey = 0; init(); return true; }
        switch (waitingForKey) {
            case 1 -> { config.hudKey = keyCode; config.hudScanCode = scanCode; }
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
            case "center" -> "Центрирование / Центр";
            case "keys" -> "Настройка клавиш";
            case "general" -> "Общие настройки";
            case "hud_style" -> "HUD — внешний вид";
            case "hud_content" -> "HUD — цвета и содержимое";
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
        if (returnedDistanceField != null) {
            try {
                config.returnedDistanceBlocks = Double.parseDouble(returnedDistanceField.getText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        config.normalize();
        config.save();
    }

    @Override public void close() {
        save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
