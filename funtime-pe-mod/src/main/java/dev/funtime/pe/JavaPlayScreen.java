package dev.funtime.pe;

import net.minecraft.client.gui.screen.Screen;

/**
 * Entry point used by the Java multiplayer-style flow.
 *
 * <p>The transport is still Bedrock internally, but this name is intentional:
 * the player is not dropped into a separate PE screen after joining.</p>
 */
public final class JavaPlayScreen extends PePlayScreen {
    public JavaPlayScreen(Screen parent, JavaPlaySession connection) {
        super(parent, connection);
    }
}