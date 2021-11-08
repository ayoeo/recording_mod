package me.aris.recordingmod.mixins;

import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Replay;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Timer.class)
abstract class TimerMixin {
  @Shadow
  private long lastSyncSysClock;

  @Inject(at = @At("HEAD"), method = "updateTimer", cancellable = true)
  private void messWithTimer(CallbackInfo ci) {
    Replay replay = LiteModRecordingModKt.getActiveReplay();
    if (replay != null) {
      if (LiteModRecordingModKt.getPaused() || replay.reachedEnd()) {
        this.lastSyncSysClock = Minecraft.getSystemTime();
        ci.cancel();
      }
    }
  }
}

