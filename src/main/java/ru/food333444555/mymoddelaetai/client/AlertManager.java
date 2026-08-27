package ru.food333444555.mymoddelaetai.client;

import net.minecraft.client.MinecraftClient;

public class AlertManager {
    public static void showSimpleToast(String text) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            // Placeholder: for now print to console. Later we can add toasts/notifications.
            mc.player.sendMessage(new net.minecraft.text.LiteralText(text), false);
        } catch (Throwable t) {
            System.out.println("Toast: " + text);
        }
    }
}
