package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class PeServerListScreen extends Screen {
    private final Screen parent;
    private final List<ButtonWidget> joinButtons = new ArrayList<>();
    private ButtonWidget addServerButton;
    private ButtonWidget accountButton;
    private ButtonWidget backButton;
    private Text status = Text.literal("");
    private boolean connecting;

    public PeServerListScreen(Screen parent) {
        super(Text.literal("Сетевая игра"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        joinButtons.clear();
        final List<PeServerEntry> servers = PeConfig.servers();
        final int left = this.width / 2 - 155;
        final int rowWidth = 310;
        final int firstRow = 48;
        final int rowHeight = 34;
        final int joinWidth = 74;

        for (int index = 0; index < servers.size() && index < 7; index++) {
            final PeServerEntry server = servers.get(index);
            final int y = firstRow + index * rowHeight;
            final ButtonWidget joinButton = ButtonWidget.builder(
                            Text.literal("Зайти"),
                            ignored -> connect(server))
                    .dimensions(left + rowWidth - joinWidth - 46, y, joinWidth, 28)
                    .build();
            joinButton.active = !connecting;
            this.addDrawableChild(joinButton);
            joinButtons.add(joinButton);

            final ButtonWidget editButton = ButtonWidget.builder(Text.literal("..."),
                            ignored -> this.client.setScreen(new PeServerScreen(this, server)))
                    .dimensions(left + rowWidth - 40, y, 40, 28)
                    .build();
            editButton.active = !connecting;
            this.addDrawableChild(editButton);
        }

        final int controlsY = firstRow + Math.min(servers.size(), 7) * rowHeight + 8;
        addServerButton = ButtonWidget.builder(Text.literal("Добавить сервер"),
                        ignored -> {
                            if (!connecting) {
                                this.client.setScreen(new PeServerScreen(this, null));
                            }
                        })
                .dimensions(left, controlsY, rowWidth, 20)
                .build();
        addServerButton.active = !connecting;
        this.addDrawableChild(addServerButton);

        accountButton = ButtonWidget.builder(
                        Text.literal("Аккаунт: " + PeConfig.account().displayName()),
                        ignored -> {
                            if (!connecting) {
                                this.client.setScreen(new PeAccountScreen(this));
                            }
                        })
                .dimensions(left, controlsY + 28, rowWidth, 20)
                .build();
        accountButton.active = !connecting;
        this.addDrawableChild(accountButton);

        backButton = ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(left, controlsY + 56, rowWidth, 20)
                .build();
        backButton.active = !connecting;
        this.addDrawableChild(backButton);
    }

    private void connect(PeServerEntry server) {
        if (connecting) {
            return;
        }
        if (!server.isValid()) {
            status = Text.literal("Запись сервера заполнена неправильно.");
            return;
        }
        if (server.authMode() == PeAuthMode.MICROSOFT
                && !PeConfig.account().isMicrosoftConnected()) {
            connecting = false;
            updateButtonsState();
            this.client.setScreen(new PeNoticeScreen(this,
                    "Для этого сервера нужен Microsoft/Xbox вход.\n"
                            + "Откройте раздел аккаунта и завершите вход по коду устройства."));
            return;
        }
        connecting = true;
        updateButtonsState();
        status = Text.literal("Подготовка подключения...");
        PeConnection.connect(MinecraftClient.getInstance(), this, server, this::setStatus);
    }

    private void updateButtonsState() {
        for (ButtonWidget button : joinButtons) {
            if (button != null) {
                button.active = !connecting;
            }
        }
        if (addServerButton != null) {
            addServerButton.active = !connecting;
        }
        if (accountButton != null) {
            accountButton.active = !connecting;
        }
        if (backButton != null) {
            backButton.active = !connecting;
        }
    }

    private void setStatus(Text status) {
        MinecraftClient.getInstance().execute(() -> {
            this.status = status;
            final String value = status.getString();
            if (value.startsWith("Подключение не выполнено")
                    || value.startsWith("Игровая сессия остановлена")
                    || value.startsWith("Соединение закрыто")
                    || value.startsWith("Не удалось открыть")) {
                connecting = false;
                updateButtonsState();
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        final List<PeServerEntry> servers = PeConfig.servers();
        final int left = this.width / 2 - 155;
        final int rowWidth = 310;
        final int firstRow = 48;
        final int rowHeight = 34;
        final int joinWidth = 74;
        for (int index = 0; index < servers.size() && index < 7; index++) {
            final int y = firstRow + index * rowHeight;
            if (inside(mouseX, mouseY, left + rowWidth - joinWidth - 46, y,
                    joinWidth, 28)) {
                connect(servers.get(index));
                return true;
            }
            if (inside(mouseX, mouseY, left + rowWidth - 40, y, 40, 28)) {
                if (!connecting) {
                    this.client.setScreen(new PeServerScreen(this, servers.get(index)));
                }
                return true;
            }
        }

        final int controlsY = firstRow + Math.min(servers.size(), 7) * rowHeight + 8;
        if (inside(mouseX, mouseY, left, controlsY, rowWidth, 20)) {
            if (!connecting) {
                this.client.setScreen(new PeServerScreen(this, null));
            }
            return true;
        }
        if (inside(mouseX, mouseY, left, controlsY + 28, rowWidth, 20)) {
            if (!connecting) {
                this.client.setScreen(new PeAccountScreen(this));
            }
            return true;
        }
        if (inside(mouseX, mouseY, left, controlsY + 56, rowWidth, 20)) {
            close();
            return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Сетевая игра"),
                this.width / 2, 34, 0xA0A0A0);
        final List<PeServerEntry> servers = PeConfig.servers();
        final int left = this.width / 2 - 155;
        final int firstRow = 48;
        for (int index = 0; index < servers.size() && index < 7; index++) {
            final PeServerEntry server = servers.get(index);
            final int y = firstRow + index * 34;
            context.drawTextWithShadow(this.textRenderer, Text.literal(server.name()),
                    left + 8, y + 4, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(server.host() + ":" + server.port()),
                    left + 8, y + 16, 0xA0A0A0);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height - 18, 0xFFCC66);
        super.render(context, mouseX, mouseY, delta);
    }
}
