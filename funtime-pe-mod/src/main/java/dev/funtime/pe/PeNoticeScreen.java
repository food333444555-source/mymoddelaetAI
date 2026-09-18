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

final class PeNoticeScreen extends Screen {
    private final Screen parent;
    private final String message;

    PeNoticeScreen(Screen parent, String message) {
        super(Text.literal("Сетевая игра"));
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), ignored -> close())
                .dimensions(this.width / 2 - 100, this.height / 2 + 30, 200, 20)
                .build());
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        final String[] lines = this.message.split("\\R", -1);
        final int firstLine = this.height / 2 - 24 - (lines.length - 1) * 5;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, firstLine - 24, 0xFFFFFF);
        for (int index = 0; index < lines.length; index++) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lines[index]),
                    this.width / 2, firstLine + index * 12, 0xFFCC66);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}