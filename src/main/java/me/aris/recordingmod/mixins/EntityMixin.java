package me.aris.recordingmod.mixins;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
  @Shadow
  public double posX;

  @Shadow
  public double posY;

  @Shadow
  public double posZ;

  @Inject(at = @At("HEAD"), method = "move")
  private void postMove(CallbackInfo ci) {
    // noinspection ConstantConditions
//    if ((Object) this == Minecraft.getMinecraft().player) {
//      if (Replay.INSTANCE.getReplaying()) {
//        Vec3d pos = Replay.INSTANCE.getPlayerPos();
//        this.posX = pos.x;
//        this.posY = pos.y;
//        this.posZ = pos.z;
//      }
//      } else if (Recorder.INSTANCE.getRecording()) {
//        Recorder.INSTANCE.getWriteLaterLock().lock();
//        ClientEvent.Position.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
//        Recorder.INSTANCE.getWriteLaterLock().unlock();
//    }
//  }
  }
}
