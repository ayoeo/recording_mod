package me.aris.recordingmod.mixins;

import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MovementInputFromOptions.class)
public class MovementInputFromOptionsMixin extends MovementInput {
  @Inject(at = @At("TAIL"), method = "updatePlayerMoveState")
  private void in(CallbackInfo ci) {
  }
}
