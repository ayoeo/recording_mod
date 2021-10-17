package me.aris.recordingmod.mixins;

import com.mojang.authlib.GameProfile;
import me.aris.recordingmod.Replay;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
abstract class EntityPlayerSPMixin extends EntityPlayer {
  @Shadow
  public abstract void setSprinting(boolean sprinting);

  public EntityPlayerSPMixin(World worldIn, GameProfile gameProfileIn) {
    super(worldIn, gameProfileIn);
  }

  @Inject(at = @At("HEAD"), method = "onLivingUpdate")
  private void in(CallbackInfo ci) {
    if (Replay.INSTANCE.getReplaying()) {
      this.rotationYaw = Replay.INSTANCE.getNextYaw();
      this.rotationPitch = Replay.INSTANCE.getNextPitch();
    }
  }

  @Inject(
    at = @At(
      value = "FIELD",
      target = "Lnet/minecraft/entity/player/PlayerCapabilities;allowFlying:Z",
      shift = At.Shift.BEFORE
    ),
    method = "onLivingUpdate")
  private void sprint(CallbackInfo ci) {
//    if (Replay.INSTANCE.getReplaying()) {
//      setSprinting(Replay.INSTANCE.getSprinting());
//    } else if (Recorder.INSTANCE.getRecording()) {
//      Recorder.INSTANCE.getWriteLaterLock().lock();
//      ClientEvent.Sprinting.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
//      Recorder.INSTANCE.getWriteLaterLock().unlock();
//    }
  }
}

