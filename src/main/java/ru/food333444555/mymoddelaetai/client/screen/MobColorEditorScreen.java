package ru.food333444555.mymoddelaetai.client.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import ru.food333444555.mymoddelaetai.config.MobColorManager;

public class MobColorEditorScreen extends Screen {

    protected MobColorEditorScreen() {
        super(new StringTextComponent("Mob Color Editor"));
    }

    @Override
    protected void init() {
        super.init();
        // TODO: add list of mobs, color pickers and checkboxes
        // For now this is a placeholder screen; we'll wire interactive widgets in later commits.
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        drawCenteredString(matrixStack, this.font, "Mob Color Editor (placeholder)", this.width / 2, 20, 0xFFFFFF);
        drawString(matrixStack, this.font, "Open config: config/mobs_colors.json", 10, 40, 0xCCCCCC);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
