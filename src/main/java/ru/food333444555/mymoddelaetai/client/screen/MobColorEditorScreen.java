package ru.food333444555.mymoddelaetai.client.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import ru.food333444555.mymoddelaetai.config.MobColorManager;

public class MobColorEditorScreen extends Screen {
    protected MobColorEditorScreen() {
        super(new LiteralText("Mob Color Editor"));
    }

    @Override
    protected void init() {
        super.init();
        // Later: add widgets. For now we show simple help text and list count of mobs.
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, "Mob Color Editor (placeholder)", this.width / 2, 20, 0xFFFFFF);
        String info = "Mobs in config: " + MobColorManager.getInstance().mobs.size();
        drawTextWithShadow(matrices, this.textRenderer, info, 10, 40, 0xCCCCCC);
        drawTextWithShadow(matrices, this.textRenderer, "Press ESC to close", 10, 60, 0x888888);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
