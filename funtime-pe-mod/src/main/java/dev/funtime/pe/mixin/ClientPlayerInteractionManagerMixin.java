package dev.funtime.pe.mixin;

import dev.funtime.pe.VanillaJavaBridge;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Converts Java block-breaking calls into Bedrock PlayerAction packets while
 * the synthetic Java world is backed by a Bedrock session.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    private BlockPos funtimepe$breakingPosition;
    private Direction funtimepe$breakingDirection;

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void funtimepe$startBreak(BlockPos position, Direction direction,
                                      CallbackInfoReturnable<Boolean> callback) {
        if (VanillaJavaBridge.sendBlockAction(position, direction, 0)) {
            funtimepe$breakingPosition = position.toImmutable();
            funtimepe$breakingDirection = direction;
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void funtimepe$stopBreak(BlockPos position,
                                     CallbackInfoReturnable<Boolean> callback) {
        final Direction direction = funtimepe$breakingDirection == null
                ? Direction.UP : funtimepe$breakingDirection;
        if (VanillaJavaBridge.sendBlockAction(
                funtimepe$breakingPosition == null ? position : funtimepe$breakingPosition,
                direction, 2)) {
            callback.setReturnValue(true);
        }
    }
}