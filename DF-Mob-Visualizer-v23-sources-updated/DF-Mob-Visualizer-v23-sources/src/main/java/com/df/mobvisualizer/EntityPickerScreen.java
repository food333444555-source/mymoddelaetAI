package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared entity browser used by custom colors and session filters.
 */
public final class EntityPickerScreen extends Screen {
    private static final String NONE_SELECTION = "__NONE__";

    public enum Mode {
        COLORS,
        ALERT_SESSION,
        RETURNED_SESSION,
        ALERT_TYPES,
        RETURNED_TYPES
    }

    private static final int LIST_TOP = 88;
    private static final int ROW_HEIGHT = 25;
    private static final Map<String, String> RUSSIAN_NAMES = russianNames();

    private final Screen parent;
    private final MobOverlayConfig config;
    private final Mode mode;
    private final List<EntityEntry> entities;
    private TextFieldWidget searchField;
    private String searchText = "";
    private int scrollOffset;

    public EntityPickerScreen(Screen parent, MobOverlayConfig config, Mode mode) {
        super(Text.literal(titleFor(mode)));
        this.parent = parent;
        this.config = config;
        this.mode = mode;
        this.entities = buildEntityCatalog(mode == Mode.COLORS);
    }

    @Override
    protected void init() {
        clearChildren();
        int left = width / 2 - 220;

        searchField = new TextFieldWidget(textRenderer, left, 38, 440, 20,
                Text.literal("Поиск"));
        searchField.setMaxLength(120);
        searchField.setText(searchText);
        searchField.setPlaceholder(Text.literal("русское или английское название, ID..."));
        searchField.setChangedListener(value -> {
            searchText = value;
            scrollOffset = 0;
        });
        addDrawableChild(searchField);

        if (mode != Mode.COLORS) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Выбрать всех"), button -> {
                setSelection(Set.of());
                save();
            }).dimensions(left, 64, 140, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Снять всех"), button -> {
                setNoneSelection();
                save();
            }).dimensions(left + 150, 64, 140, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), button -> close())
                    .dimensions(left + 300, 64, 140, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), button -> close())
                    .dimensions(left + 300, 64, 140, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = width / 2 - 220;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleFor(mode)),
                width / 2, 15, 0xFFE8D7FF);
        context.drawTextWithShadow(textRenderer,
                Text.literal(mode == Mode.COLORS
                        ? "Нажми на сущность, чтобы открыть редактор цвета"
                        : "Галочка означает: тип отслеживается"),
                left, 74, 0xFFB9A7C9);

        List<EntityEntry> matches = filteredEntities();
        int rows = visibleRows();
        if (scrollOffset > Math.max(0, matches.size() - rows)) {
            scrollOffset = Math.max(0, matches.size() - rows);
        }

        Set<String> selected = mode == Mode.COLORS ? Set.of() : selectedTypes();
        boolean allSelected = mode != Mode.COLORS && isAllSelected();
        boolean noneSelected = mode != Mode.COLORS && isNoneSelected();
        for (int row = 0; row < rows; row++) {
            int index = scrollOffset + row;
            if (index >= matches.size()) break;
            EntityEntry entry = matches.get(index);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean checked = mode != Mode.COLORS && !noneSelected
                    && (allSelected || selected.contains(entry.id));
            int background = row % 2 == 0 ? 0x6620182A : 0x66402A4A;
            context.fill(left, y, left + 440, y + ROW_HEIGHT - 2, background);
            if (mode != Mode.COLORS) {
                context.drawTextWithShadow(textRenderer, Text.literal(checked ? "☑" : "☐"),
                        left + 7, y + 7, checked ? 0xFF75E39A : 0xFFB9A7C9);
            }
            String label = entry.russian + " / " + entry.english + "  [" + entry.id + "]";
            String trimmed = textRenderer.trimToWidth(label, 400);
            context.drawTextWithShadow(textRenderer, Text.literal(trimmed),
                    left + (mode == Mode.COLORS ? 8 : 28), y + 7, 0xFFFFFFFF);
        }

        String counter = matches.size() + " сущностей";
        if (mode != Mode.COLORS) {
            counter += " | выбрано: " + (allSelected ? "ВСЕ" : noneSelected ? "0" : selected.size());
        }
        context.drawTextWithShadow(textRenderer, Text.literal(counter),
                left, height - 28, 0xFFB9A7C9);
        if (scrollOffset > 0) {
            context.drawTextWithShadow(textRenderer, Text.literal("▲ ещё выше"),
                    left + 300, height - 28, 0xFFB9A7C9);
        }
        if (scrollOffset + rows < matches.size()) {
            context.drawTextWithShadow(textRenderer, Text.literal("▼ ещё ниже"),
                    left + 350, height - 28, 0xFFB9A7C9);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = width / 2 - 220;
        if (mouseX < left || mouseX > left + 440 || mouseY < LIST_TOP) return false;
        int row = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        if (row < 0 || row >= visibleRows()) return false;
        List<EntityEntry> matches = filteredEntities();
        int index = scrollOffset + row;
        if (index >= matches.size()) return false;

        EntityEntry entry = matches.get(index);
        if (mode == Mode.COLORS) {
            MinecraftClient.getInstance().setScreen(
                    new MobColorPickerScreen(this, config, entry.id));
        } else {
            Set<String> selected = new HashSet<>(selectedTypes());
            if (isAllSelected()) selected.addAll(allEntityIds());
            if (isNoneSelected()) selected.clear();
            if (!selected.remove(entry.id)) selected.add(entry.id);
            if (selected.size() == allEntityIds().size()) setSelection(Set.of());
            else if (selected.isEmpty()) setNoneSelection();
            else setSelection(selected);
            save();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<EntityEntry> matches = filteredEntities();
        int maxOffset = Math.max(0, matches.size() - visibleRows());
        if (verticalAmount < 0) scrollOffset = Math.min(maxOffset, scrollOffset + 3);
        if (verticalAmount > 0) scrollOffset = Math.max(0, scrollOffset - 3);
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 42) / ROW_HEIGHT);
    }

    private List<EntityEntry> filteredEntities() {
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return entities;
        return entities.stream()
                .filter(entry -> entry.id.toLowerCase(Locale.ROOT).contains(query)
                        || entry.english.toLowerCase(Locale.ROOT).contains(query)
                        || entry.russian.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private Set<String> selectedTypes() {
        String raw = switch (mode) {
            case ALERT_SESSION -> config.alertSessionEntityTypes;
            case RETURNED_SESSION -> config.returnedSessionEntityTypes;
            case ALERT_TYPES -> config.alertEntityTypes;
            case RETURNED_TYPES -> config.returnedEntityTypes;
            default -> "";
        };
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase(NONE_SELECTION)) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(value -> normalizeId(value))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private void setSelection(Set<String> selection) {
        String value = selection.stream()
                .map(EntityPickerScreen::normalizeId)
                .filter(item -> !item.isBlank())
                .sorted()
                .collect(Collectors.joining(", "));
        switch (mode) {
            case ALERT_SESSION -> config.alertSessionEntityTypes = value;
            case RETURNED_SESSION -> config.returnedSessionEntityTypes = value;
            case ALERT_TYPES -> config.alertEntityTypes = value;
            case RETURNED_TYPES -> config.returnedEntityTypes = value;
        }
    }

    private void setNoneSelection() {
        switch (mode) {
            case ALERT_SESSION -> config.alertSessionEntityTypes = NONE_SELECTION;
            case RETURNED_SESSION -> config.returnedSessionEntityTypes = NONE_SELECTION;
            case ALERT_TYPES -> config.alertEntityTypes = NONE_SELECTION;
            case RETURNED_TYPES -> config.returnedEntityTypes = NONE_SELECTION;
        }
    }

    private boolean isAllSelected() {
        if (mode == Mode.ALERT_TYPES || mode == Mode.RETURNED_TYPES) return false;
        String raw = switch (mode) {
            case ALERT_SESSION -> config.alertSessionEntityTypes;
            case RETURNED_SESSION -> config.returnedSessionEntityTypes;
            default -> "";
        };
        return raw == null || raw.isBlank();
    }

    private boolean isNoneSelected() {
        String raw = switch (mode) {
            case ALERT_SESSION -> config.alertSessionEntityTypes;
            case RETURNED_SESSION -> config.returnedSessionEntityTypes;
            case ALERT_TYPES -> config.alertEntityTypes;
            case RETURNED_TYPES -> config.returnedEntityTypes;
            default -> "";
        };
        return raw != null && raw.trim().equalsIgnoreCase(NONE_SELECTION);
    }

    private Set<String> allEntityIds() {
        return entities.stream().map(entry -> entry.id).collect(Collectors.toSet());
    }

    private void save() {
        config.normalize();
        config.save();
    }

    @Override
    public void close() {
        save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static String titleFor(Mode mode) {
        return switch (mode) {
            case COLORS -> "Цвета мобов";
            case ALERT_SESSION -> "ALERT: мобы в сессию";
            case RETURNED_SESSION -> "RETURNED: мобы в сессию";
            case ALERT_TYPES -> "ALERT: выбор мобов";
            case RETURNED_TYPES -> "RETURNED: выбор мобов";
        };
    }

    private static List<EntityEntry> buildEntityCatalog(boolean includePseudoEntities) {
        Set<String> ids = new HashSet<>();
        if (includePseudoEntities) ids.add("minecraft:charged_creeper");
        Registries.ENTITY_TYPE.getIds().forEach(id -> ids.add(id.toString().toLowerCase(Locale.ROOT)));
        return ids.stream()
                .map(EntityPickerScreen::buildEntry)
                .sorted(Comparator.comparing(entry -> entry.english))
                .toList();
    }

    private static EntityEntry buildEntry(String id) {
        String english = englishName(id);
        String russian = RUSSIAN_NAMES.getOrDefault(id, localizedName(id));
        if (russian.equals(id) || russian.startsWith("entity.")) russian = english;
        return new EntityEntry(id, english, russian);
    }

    private static String localizedName(String id) {
        return Text.translatable("entity." + id.replace(':', '.')).getString();
    }

    private static String englishName(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : path.split(" ")) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String normalizeId(String value) {
        String id = value.trim().toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private record EntityEntry(String id, String english, String russian) {
    }

    private static Map<String, String> russianNames() {
        Map<String, String> names = new HashMap<>();
        String[][] values = {
                {"allay", "Алей"}, {"armadillo", "Броненосец"}, {"axolotl", "Аксолотль"},
                {"bat", "Летучая мышь"}, {"bee", "Пчела"}, {"blaze", "Ифрит"},
                {"bogged", "Заболотник"}, {"breeze", "Бриз"}, {"camel", "Верблюд"},
                {"cat", "Кошка"}, {"cave_spider", "Пещерный паук"},
                {"chicken", "Курица"}, {"cod", "Треска"}, {"cow", "Корова"},
                {"creaking", "Скрипун"}, {"creeper", "Крипер"}, {"dolphin", "Дельфин"},
                {"donkey", "Осёл"},
                {"drowned", "Утопленник"}, {"elder_guardian", "Древний страж"},
                {"ender_dragon", "Дракон Края"}, {"enderman", "Странник Края"},
                {"endermite", "Чешуйница Края"}, {"evoker", "Заклинатель"},
                {"fox", "Лиса"}, {"frog", "Лягушка"}, {"ghast", "Гаст"},
                {"glow_squid", "Светящийся спрут"}, {"goat", "Коза"}, {"guardian", "Страж"},
                {"hoglin", "Хоглин"}, {"horse", "Лошадь"}, {"husk", "Кадавр"},
                {"iron_golem", "Железный голем"}, {"llama", "Лама"}, {"magma_cube", "Лавовый куб"},
                {"mooshroom", "Грибная корова"}, {"mule", "Мул"}, {"ocelot", "Оцелот"},
                {"panda", "Панда"}, {"parrot", "Попугай"}, {"phantom", "Фантом"},
                {"pig", "Свинья"}, {"piglin", "Пиглин"}, {"piglin_brute", "Брутальный пиглин"},
                {"pillager", "Разбойник"}, {"polar_bear", "Белый медведь"},
                {"pufferfish", "Иглобрюх"}, {"rabbit", "Кролик"}, {"ravager", "Разоритель"},
                {"salmon", "Лосось"}, {"sheep", "Овца"}, {"shulker", "Шалкер"},
                {"silverfish", "Чешуйница"}, {"skeleton", "Скелет"}, {"skeleton_horse", "Лошадь-скелет"},
                {"slime", "Слизень"}, {"sniffer", "Нюхач"}, {"snow_golem", "Снежный голем"},
                {"spider", "Паук"}, {"squid", "Спрут"}, {"stray", "Зимогор"},
                {"strider", "Лавомерка"}, {"tadpole", "Головастик"}, {"trader_llama", "Лама торговца"},
                {"tropical_fish", "Тропическая рыба"}, {"turtle", "Черепаха"},
                {"vex", "Вредина"}, {"villager", "Житель"}, {"vindicator", "Поборник"},
                {"wandering_trader", "Странствующий торговец"}, {"warden", "Хранитель"},
                {"witch", "Ведьма"}, {"wither", "Иссушитель"}, {"wither_skeleton", "Скелет-иссушитель"},
                {"wolf", "Волк"}, {"zoglin", "Зоглин"}, {"zombie", "Зомби"},
                {"zombie_horse", "Лошадь-зомби"}, {"zombie_villager", "Зомби-житель"},
                {"zombified_piglin", "Зомбифицированный пиглин"}, {"player", "Игрок"}
        };
        for (String[] value : values) names.put("minecraft:" + value[0], value[1]);
        return names;
    }
}
