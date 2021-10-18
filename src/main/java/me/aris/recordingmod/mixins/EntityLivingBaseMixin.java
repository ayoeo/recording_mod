package me.aris.recordingmod.mixins;

import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.Replay;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLivingBase.class)
abstract class EntityLivingBaseMixin extends Entity {
  public EntityLivingBaseMixin(World worldIn) {
    super(worldIn);
  }

  @ModifyVariable(method = "travel", at = @At("HEAD"), ordinal = 0)
  private float strafe(float in) {
    if ((Object) this == Minecraft.getMinecraft().player && Replay.INSTANCE.getReplaying()) {
      return Replay.INSTANCE.getTravelArgs().x;
    } else {
      return in;
    }
  }

  @ModifyVariable(method = "travel", at = @At("HEAD"), ordinal = 1)
  private float vertical(float in) {
    if ((Object) this == Minecraft.getMinecraft().player && Replay.INSTANCE.getReplaying()) {
      return Replay.INSTANCE.getTravelArgs().y;
    } else {
      return in;
    }
  }

  @ModifyVariable(method = "travel", at = @At("HEAD"), ordinal = 2)
  private float forward(float in) {
    if ((Object) this == Minecraft.getMinecraft().player && Replay.INSTANCE.getReplaying()) {
      return Replay.INSTANCE.getTravelArgs().z;
    } else {
      return in;
    }
  }

  @Inject(at = @At("HEAD"), method = "travel")
  private void preMove(float strafe, float vertical, float forward, CallbackInfo ci) {
    // noinspection ConstantConditions
    if ((Object) this == Minecraft.getMinecraft().player) {
      if (Replay.INSTANCE.getReplaying()) {
        Vec3d motion = Replay.INSTANCE.getPlayerMotion();
        this.motionX = motion.x;
        this.motionY = motion.y;
        this.motionZ = motion.z;
        Vec3d pos = Replay.INSTANCE.getPlayerPos();
        this.posX = pos.x;
        this.posY = pos.y;
        this.posZ = pos.z;
      } else if (Recorder.INSTANCE.getRecording()) {
        Recorder.INSTANCE.getWriteLaterLock().lock();
        Replay.INSTANCE.setTravelArgs(new Vector3f(strafe, vertical, forward));
        ClientEvent.Position.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
        Recorder.INSTANCE.getWriteLaterLock().unlock();
      }
    }
  }
}
