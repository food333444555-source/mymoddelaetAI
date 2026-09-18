package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Java-style pause screen. Escape no longer drops the network session
 * immediately; disconnect is an explicit menu action.
 */
final class PePauseScreen extends Screen {
    private final Screen playScreen;
    private final Screen serverScreen;
    private final JavaPlaySession session;

    PePauseScreen(Screen playScreen, Screen serverScreen, JavaPlaySession session) {
        super(Text.literal("Меню игры"));
        this.playScreen = playScreen;
        this.serverScreen = serverScreen;
        this.session = session;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 100;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Вернуться в игру"),
                        ignored -> resume())
                .dimensions(left, this.height / 2 - 42, 200, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Инвентарь"),
                        ignored -> this.client.setScreen(new PeInventoryScreen(
                                this, session)))
                .dimensions(left, this.height / 2 - 14, 200, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отключиться"),
                        ignored -> disconnect())
                .dimensions(left, this.height / 2 + 14, 200, 20)
                .build());
    }

    private void resume() {
        this.client.setScreen(playScreen);
    }

    private void disconnect() {
        session.close();
        this.client.setScreen(serverScreen);
    }

    @Override
    public void close() {
        resume();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 78, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}