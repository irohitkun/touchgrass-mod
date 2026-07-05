package com.touchgrass.mod.mixin;

import com.touchgrass.mod.ModeManager;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The illusion cat/dog/parrot are cosmetically "tamed" (owner set directly,
 * bypassing the feeding mechanic — see ModeManager#spawnIllusionAnimals).
 * This mixin stops any real player interaction — re-taming, breeding items,
 * sit-toggle — from changing their state during the break. Their vanilla
 * wander/follow/sit AI stays fully active; they just ignore player input.
 */
@Mixin(TameableEntity.class)
public class TamedInteractionMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void touchgrass$blockRealTaming(PlayerEntity player, Hand hand,
                                             CallbackInfoReturnable<ActionResult> cir) {
        TameableEntity self = (TameableEntity) (Object) this;
        if (self.getScoreboardTags().contains(ModeManager.ILLUSION_TAG)) {
            // PASS = inert: no taming, no sit-toggle, no breeding.
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
