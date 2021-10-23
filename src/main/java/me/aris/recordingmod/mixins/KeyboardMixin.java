package me.aris.recordingmod.mixins;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
  @Inject(at = @At("HEAD"), method = "isKeyDown", remap = false)
  private static void keyDownHook(int key, CallbackInfoReturnable<Boolean> cir) {
    // TODO - idk keyboard hook
  }
}
