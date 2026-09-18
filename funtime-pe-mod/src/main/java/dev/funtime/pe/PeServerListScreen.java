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

import java.util.List;

public final class PeServerListScreen extends Screen {
    private final Screen parent;
    private Text status = Text.literal("");
    private boolean connecting;

    public PeServerListScreen(Screen parent) {
        super(Text.literal("Сетевая игра"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final List<PeServerEntry> servers = PeConfig.servers();
        final int left = this.width / 2 - 155;
        final int rowWidth = 310;
        final int firstRow = 48;
        final int rowHeight = 34;
        final int joinWidth = 74;

        for (int index = 0; index < servers.size() && index < 7; index++) {
            final PeServerEntry server = servers.get(index);
            final int y = firstRow + index * rowHeight;
            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal("Зайти"),
                            ignored -> connect(server))
                    .dimensions(left + rowWidth - joinWidth - 46, y, joinWidth, 28)
                    .build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("..."),
                            ignored -> this.client.setScreen(new PeServerScreen(this, server)))
                    .dimensions(left + rowWidth - 40, y, 40, 28)
                    .build());
        }

        final int controlsY = firstRow + Math.min(servers.size(), 7) * rowHeight + 8;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Добавить сервер"),
                        ignored -> this.client.setScreen(new PeServerScreen(this, null)))
                .dimensions(left, controlsY, rowWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Аккаунт: " + PeConfig.account().displayName()),
                        ignored -> this.client.setScreen(new PeAccountScreen(this)))
                .dimensions(left, controlsY + 28, rowWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(left, controlsY + 56, rowWidth, 20)
                .build());
    }

    private void connect(PeServerEntry server) {
        if (connecting) {
            return;
        }
        if (!server.isValid()) {
            status = Text.literal("Запись сервера заполнена неправильно.");
            return;
        }
        connecting = true;
        status = Text.literal("Подготовка подключения...");
        PeConnection.connect(MinecraftClient.getInstance(), this, server, this::setStatus);
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
            }
        });
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
        final int rowHeight = 34;
        for (int index = 0; index < servers.size() && index < 7; index++) {
            final PeServerEntry server = servers.get(index);
            final int y = firstRow + index * rowHeight;
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