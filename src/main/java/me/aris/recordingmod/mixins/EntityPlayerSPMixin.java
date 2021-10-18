package me.aris.recordingmod.mixins;

import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.Recorder;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
abstract class EntityPlayerSPMixin {

  @Inject(at = @At("HEAD"), method = "closeScreen")
  private void closeScreen(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getWriteLaterLock().lock();
      ClientEvent.CloseInventory.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();
    }
  }
}
