package ru.food333444555.mymoddelaetai.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static KeyBinding OPEN_MOB_EDITOR;

    public static void register() {
        // Category and key names can be localized later
        OPEN_MOB_EDITOR = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mymoddelaetai.open_mob_editor",
                GLFW.GLFW_KEY_F10,
                "category.mymoddelaetai"
        ));
    }
}
