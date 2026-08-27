package ru.food333444555.mymoddelaetai.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import ru.food333444555.mymoddelaetai.client.screen.MobColorEditorScreen;

public class FabricClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                KeyBinding kb = Keybinds.OPEN_MOB_EDITOR;
                if (kb != null && kb.wasPressed()) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null) {
                        mc.setScreen(new MobColorEditorScreen());
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
