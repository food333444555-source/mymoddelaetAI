package com.df.mobvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class HeightInputScreen extends Screen {
    private final Screen parent;
    private final MobOverlayConfig config;
    private final boolean isHeight;
    private TextFieldWidget inputField;

    public HeightInputScreen(Screen parent, MobOverlayConfig config, boolean isHeight) {
        super(Text.literal(isHeight ? "Высота слоя" : "Толщина слоя"));
        this.parent = parent;
        this.config = config;
        this.isHeight = isHeight;
    }

    @Override
    protected void init() {
        int centerX = width / 2 - 75;
        int centerY = height / 2 - 20;

        String currentValue = isHeight 
            ? String.valueOf(config.chunkYOffset) 
            : String.valueOf(config.chunkHeight);

        inputField = new TextFieldWidget(textRenderer, centerX, centerY, 150, 20, Text.literal("Введите число"));
        inputField.setText(currentValue);
        inputField.setMaxLength(10);
        inputField.setPlaceholder(Text.literal(isHeight ? "-10.0 до 200.0" : "0.0 до 50.0"));
        addDrawableChild(inputField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), button -> {
            try {
                double value = Double.parseDouble(inputField.getText().trim());
                if (isHeight) {
                    config.chunkYOffset = Math.max(-10.0, Math.min(200.0, value));
                } else {
                    config.chunkHeight = Math.max(0.0, Math.min(50.0, value));
                }
                config.save();
                MinecraftClient.getInstance().setScreen(parent);
            } catch (NumberFormatException e) {
                inputField.setText("Ошибка!");
            }
        }).dimensions(centerX, centerY + 25, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), button -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(centerX + 80, centerY + 25, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(isHeight ? "Высота: -10 (под землёй) до 200 (в воздухе)" 
                                  : "Толщина: 0 (плоский) до 50 (объёмный)"),
            button -> {}
        ).dimensions(centerX, centerY + 50, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 50, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
