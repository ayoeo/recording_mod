package me.aris.recordingmod.mixins;

import kotlin.Triple;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.ReplayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScaledResolution.class)
class ScaledResolutionMixin {
  @Shadow
  private double scaledWidthD;

  @Shadow
  private double scaledHeightD;

  @Shadow
  private int scaleFactor;

  @Shadow
  private int scaledWidth;

  @Shadow
  private int scaledHeight;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void constructorHead(
    Minecraft minecraftClient,
    CallbackInfo ci
  ) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.setScaledRes(new Triple<>(this.scaledWidthD, this.scaledHeightD, this.scaleFactor));
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      Triple<Double, Double, Integer> res = ReplayState.INSTANCE.getScaledRes();
      this.scaledWidthD = res.getFirst();
      this.scaledHeightD = res.getSecond();
      this.scaledWidth = (int) Math.ceil(this.scaledWidthD);
      this.scaledHeight = (int) Math.ceil(this.scaledHeightD);
      this.scaleFactor = res.getThird();
    }
  }
}
