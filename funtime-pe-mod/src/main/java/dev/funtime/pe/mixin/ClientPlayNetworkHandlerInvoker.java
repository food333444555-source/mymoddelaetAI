package dev.funtime.pe.mixin;

import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerInvoker {
    @Invoker("startWorldLoading")
    void funtimepe$startWorldLoading(
            ClientPlayerEntity player,
            ClientWorld world,
            DownloadingTerrainScreen.WorldEntryReason reason
    );
}
