package me.aris.recordingmod.mixins;

import me.aris.recordingmod.LiteModRecordingModKt;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}

