package dev.funtime.pe;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

        ClientTickEvents.END_CLIENT_TICK.register(VanillaJavaBridgeRegistry::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                ignored -> {
                    VanillaJavaBridgeRegistry.close();
                    PeConnection.closeActive();
                });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MultiplayerScreen) || !INITIALIZED_SCREENS.add(screen)) {
                return;
            }

            final ButtonWidget button = ButtonWidget.builder(
                            Text.literal("Сетевая игра"),
                            ignored -> client.setScreen(new PeServerListScreen(screen)))
                    .dimensions(Math.max(5, scaledWidth - 155), scaledHeight - 28, 150, 20)
                    .build();
            ((ScreenAccessor) screen).funtimepe$addDrawableChild(button);
        });
    }
}