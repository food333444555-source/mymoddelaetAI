package dev.funtime.pe;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class PeNoticeScreen extends Screen {
    private final Screen parent;
    private final String message;

    PeNoticeScreen(Screen parent, String message) {
        super(Text.literal("PE / Bedrock"));
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 35, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.message),
                this.width / 2, this.height / 2 - 10, 0xFFCC66);
        super.render(context, mouseX, mouseY, delta);
    }
}