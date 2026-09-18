package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import dev.funtime.pe.world.JavaInventoryState;
import dev.funtime.pe.world.JavaItemStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/**
 * Java-style inventory surface. Slot synchronization is supplied by the
 * Java-facing session and can later be replaced by server inventory packets.
 */
final class PeInventoryScreen extends Screen {
    private final Screen parent;
    private final JavaPlaySession session;
    private int pendingContainerSlot = -1;

    PeInventoryScreen(Screen parent, JavaPlaySession session) {
        super(Text.literal("Инвентарь"));
        this.parent = parent;
        this.session = session;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        final int containerId = session.inventory().openContainerId();
        if (containerId >= 0) {
            try {
                session.sendContainerClose(containerId);
            } catch (IOException ignored) {
                // The network loop will report a failed close if needed.
            }
        }
        this.client.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            final int containerId = session.inventory().openContainerId();
            final int left = this.width / 2 - 92;
            final int top = this.height / 2 - 58;
            if (containerId >= 0 && mouseY >= top - 28 && mouseY < top + 32
                    && mouseX >= left && mouseX < left + 180) {
                final int slot = ((int) mouseY - (top - 28)) / 20 * 9
                        + ((int) mouseX - left) / 20;
                if (slot >= 0 && slot < 27) {
                    if (pendingContainerSlot < 0) {
                        pendingContainerSlot = slot;
                    } else {
                        try {
                            session.sendContainerSwap(containerId,
                                    pendingContainerSlot, slot);
                        } catch (IOException ignored) {
                            // The network loop reports the connection failure.
                        }
                        pendingContainerSlot = -1;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        final JavaInventoryState inventory = session.inventory();
        final int left = this.width / 2 - 92;
        final int top = this.height / 2 - 58;
        final int containerId = inventory.openContainerId();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, top - 24, 0xFFFFFF);
        if (containerId >= 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Контейнер " + containerId),
                    this.width / 2, top - 44, 0xFFDD88);
            drawContainerGrid(context, inventory, left, top - 28, containerId);
        }
        drawGrid(context, inventory, left, top, 27, JavaInventoryState.HOTBAR_SIZE);
        drawGrid(context, inventory, left, top + 56, 9, 0);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("E или Esc — закрыть"), left, top + 86, 0xB0B0B0);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContainerGrid(DrawContext context, JavaInventoryState inventory,
                                   int left, int top, int containerId) {
        for (int index = 0; index < 27; index++) {
            final int x = left + (index % 9) * 20;
            final int y = top + (index / 9) * 20;
            final boolean selected = pendingContainerSlot == index;
            context.fill(x, y, x + 18, y + 18,
                    selected ? 0xFF65542B : 0xFF202027);
            context.fill(x, y, x + 18, y + 1,
                    selected ? 0xFFFFD54A : 0xFF777777);
            final JavaItemStack stack = inventory.containerSlot(containerId, index);
            if (!stack.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(shortName(stack.itemId())), x + 2, y + 4, 0xFFFFFF);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(Integer.toString(stack.count())), x + 13, y + 11, 0xFFFFFF);
            }
        }
    }

    private void drawGrid(DrawContext context, JavaInventoryState inventory,
                          int left, int top, int count, int firstSlot) {
        for (int index = 0; index < count; index++) {
            final int x = left + (index % 9) * 20;
            final int y = top + (index / 9) * 20;
            final int slot = firstSlot + index;
            final boolean selected = slot < JavaInventoryState.HOTBAR_SIZE
                    && inventory.selectedSlot() == slot;
            context.fill(x, y, x + 18, y + 18, 0xFF2B2B32);
            context.fill(x, y, x + 18, y + 1,
                    selected ? 0xFFFFD54A : 0xFF777777);
            final JavaItemStack stack = inventory.slot(slot);
            if (!stack.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(shortName(stack.itemId())), x + 2, y + 4, 0xFFFFFF);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(Integer.toString(stack.count())), x + 13, y + 11, 0xFFFFFF);
            }
        }
    }

    private static String shortName(String itemId) {
        final int separator = itemId.indexOf(':');
        final String name = separator >= 0 ? itemId.substring(separator + 1) : itemId;
        return name.length() > 8 ? name.substring(0, 8) : name;
    }
}