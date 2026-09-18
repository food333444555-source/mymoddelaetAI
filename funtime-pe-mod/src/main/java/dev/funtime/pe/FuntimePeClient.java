package dev.funtime.pe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.funtime.pe.mixin.ScreenAccessor;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class FuntimePeClient implements ClientModInitializer {
    private static final Set<Screen> INITIALIZED_SCREENS =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        PeConfig.load();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MultiplayerScreen) || !INITIALIZED_SCREENS.add(screen)) {
                return;
            }

            final ButtonWidget button = ButtonWidget.builder(
                            Text.literal("PE / Bedrock"),
                            ignored -> client.setScreen(new PeServerScreen(screen)))
                    .dimensions(Math.max(5, scaledWidth - 155), scaledHeight - 28, 150, 20)
                    .build();
            ((ScreenAccessor) screen).funtimepe$addDrawableChild(button);
        });
    }
}