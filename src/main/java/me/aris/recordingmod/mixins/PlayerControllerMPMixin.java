package me.aris.recordingmod.mixins;

import me.aris.recordingmod.LiteModRecordingModKt;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
abstract class PlayerControllerMPMixin {
  @Shadow
  protected abstract void syncCurrentPlayItem();

  @Inject(at = @At("HEAD"), method = "updateController", cancellable = true)
  private void in(CallbackInfo ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      this.syncCurrentPlayItem();
      ci.cancel();
    }
  }

  @Inject(at = @At("HEAD"), method = "onPlayerDestroyBlock", cancellable = true)
  private void in(BlockPos itemstack1, CallbackInfoReturnable<Boolean> cir) {
    if (LiteModRecordingModKt.getActiveReplay() != null && LiteModRecordingModKt.getSkipping()) {
      // don't? break it?
      System.out.println("don't break it? (DONT BREAK IT) " + itemstack1);
      cir.cancel();
    }
  }
}

