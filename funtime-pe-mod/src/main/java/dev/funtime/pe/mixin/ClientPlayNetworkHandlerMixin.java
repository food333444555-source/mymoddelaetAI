package dev.funtime.pe.mixin;

import dev.funtime.pe.VanillaJavaBridge;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes vanilla chat calls into the Bedrock session while the Java bridge is
 * active. Other vanilla actions are handled by the client-tick bridge and are
 * added here as their packet translators become available.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void funtimepe$sendChatMessage(String content, CallbackInfo callback) {
        if (VanillaJavaBridge.sendChat(
                (ClientPlayNetworkHandler) (Object) this, content)) {
            callback.cancel();
        }
    }
}