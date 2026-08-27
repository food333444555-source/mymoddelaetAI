package ru.food333444555.mymoddelaetai.client.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack as MS; // placeholder to avoid name clash
import net.minecraft.text.LiteralText;
import ru.food333444555.mymoddelaetai.config.MobColorManager;

import java.util.*;

public class MobEventSelectionScreen extends Screen {
    private final String eventKey; // e.g. "returned" or "hurt" or "highlightThroughWalls"
    private final List<String> mobIds;
    private final Map<String, Boolean> selected = new LinkedHashMap<>();

    public MobEventSelectionScreen(String eventKey) {
        super(new LiteralText("Select mobs for event"));
        this.eventKey = eventKey;
        this.mobIds = new ArrayList<>();
        this.mobIds.addAll(MobColorManager.getInstance().listAllMobIds());
        Collections.sort(this.mobIds);
        for (String id : this.mobIds) {
            boolean has = MobColorManager.getInstance().mobHasEvent(id, eventKey);
            selected.put(id, has);
        }
    }

    @Override
    protected void init() {
        super.init();
        int btnY = this.height - 30;
        this.addButton(new ButtonWidget(this.width/2 - 100, btnY, 100, 20, new LiteralText("Apply"), btn -> {
            applySelection();
            this.onClose();
        }));
        this.addButton(new ButtonWidget(this.width/2 + 2, btnY, 100, 20, new LiteralText("Cancel"), btn -> this.onClose()));

        // simple select all / deselect all
        this.addButton(new ButtonWidget(10, btnY, 80, 20, new LiteralText("All"), btn -> {
            for (String k : selected.keySet()) selected.put(k, true);
        }));
        this.addButton(new ButtonWidget(95, btnY, 80, 20, new LiteralText("None"), btn -> {
            for (String k : selected.keySet()) selected.put(k, false);
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, "Select mobs for: " + eventKey, this.width/2, 10, 0xFFFFFF);

        int y = 30;
        int x = 10;
        int lineH = 12;
        int perPage = (this.height - 70) / lineH;

        int i = 0;
        for (String id : this.mobIds) {
            int yy = y + i * lineH;
            if (yy < 30) { i++; continue; }
            if (yy > this.height - 50) break;
            boolean sel = selected.getOrDefault(id, false);
            int col = sel ? 0xA0FF8080 : 0xFFFFFFFF;
            drawTextWithShadow(matrices, this.textRenderer, (sel ? "[x] " : "[ ] ") + id, x, yy, col);
            i++;
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = 30;
        int lineH = 12;
        int i = 0;
        for (String id : this.mobIds) {
            int yy = y + i * lineH;
            if (yy > this.height - 50) break;
            if (mouseY >= yy && mouseY < yy + lineH) {
                boolean cur = selected.getOrDefault(id, false);
                selected.put(id, !cur);
                return true;
            }
            i++;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applySelection() {
        for (Map.Entry<String, Boolean> e : selected.entrySet()) {
            if (e.getValue()) {
                MobColorManager.getInstance().addEventToMob(e.getKey(), eventKey);
            } else {
                MobColorManager.getInstance().removeEventFromMob(e.getKey(), eventKey);
            }
        }
        MobColorManager.getInstance().saveConfig();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
