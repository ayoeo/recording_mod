package me.aris.recordingmod.mixins;

import me.aris.recordingmod.CameraRotation;
import me.aris.recordingmod.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
  @Shadow
  public float rotationYaw;

  @Shadow
  public float rotationPitch;

  private static long lastTime = 0L;

  @Inject(at = @At("TAIL"), method = "turn")
  private void turn(float yaw, float pitch, CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // noinspection ConstantConditions
      if ((Entity) (Object) this == Minecraft.getMinecraft().player) {
        long deltaTime = Minecraft.getSystemTime() - lastTime;
        if ((yaw == 0f && pitch == 0f && deltaTime < 5)) return;
        // heheahhhaha
        lastTime = Minecraft.getSystemTime();
        Recorder.INSTANCE.getRotations().add(
          new CameraRotation(
            rotationYaw,
            rotationPitch,
            Minecraft.getMinecraft().getRenderPartialTicks()
          )
        );
      }
    }
  }
}
