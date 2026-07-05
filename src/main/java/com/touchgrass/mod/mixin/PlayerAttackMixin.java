package com.touchgrass.mod.mixin;

import com.touchgrass.mod.ModeManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels all attacks while Touch Grass Mode is active so the player
 * cannot hurt the illusion animals or the golem during the break.
 */
@Mixin(PlayerEntity.class)
public class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void touchgrass$cancelAttackInMode(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self instanceof ServerPlayerEntity server && ModeManager.isInForcedMode(server)) {
            ci.cancel();
        }
    }
}
