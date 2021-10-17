package me.aris.recordingmod.mixins;

import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.Replay;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MovementInputFromOptions.class)
public class MovementInputFromOptionsMixin extends MovementInput {
  @Inject(at = @At("TAIL"), method = "updatePlayerMoveState")
  private void in(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getWriteLaterLock().lock();
      ClientEvent.Input.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();
    } else if (Replay.INSTANCE.getReplaying()) {
      moveForward = Replay.INSTANCE.getMoveForward();
      moveStrafe = Replay.INSTANCE.getMoveStrafe();
      jump = Replay.INSTANCE.getJumping();
      sneak = Replay.INSTANCE.getSneaking();
    }
  }
}
