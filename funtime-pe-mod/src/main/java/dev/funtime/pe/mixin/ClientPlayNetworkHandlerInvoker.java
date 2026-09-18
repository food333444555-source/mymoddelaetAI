package dev.funtime.pe.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the vanilla world bootstrap that is package-private in Minecraft.
 *
 * <p>The PE bridge creates a real client world without a Java server login,
 * so it must enter the same lifecycle as a normal Java connection.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerInvoker {
    @Invoker("startWorldLoading")
    void funtimepe$startWorldLoading(
            ClientPlayerEntity player,
            ClientWorld world,
            DownloadingTerrainScreen.WorldEntryReason reason
    );
}