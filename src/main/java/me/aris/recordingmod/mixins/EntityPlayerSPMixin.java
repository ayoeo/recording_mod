package me.aris.recordingmod.mixins;

import com.mojang.authlib.GameProfile;
import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.ReplayState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
abstract class EntityPlayerSPMixin extends EntityPlayer {
  public EntityPlayerSPMixin(World worldIn, GameProfile gameProfileIn) {
    super(worldIn, gameProfileIn);
  }

  @Inject(at = @At("HEAD"), method = "closeScreen")
  private void closeScreen(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      ClientEvent.writeClientEvent(ClientEvent.CloseScreen.INSTANCE);
    }
  }

  @Inject(at = @At("HEAD"), method = "onLivingUpdate")
  private void in(CallbackInfo ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      this.rotationYaw = ReplayState.INSTANCE.getNextYaw();
      this.rotationPitch = ReplayState.INSTANCE.getNextPitch();
    }
  }
}
