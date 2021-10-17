package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.Replay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @Inject(at = @At("HEAD"), method = "runTick")
  private void runTick(CallbackInfo ci) {
    LiteModRecordingModKt.preTick();
  }
//
//  @Inject(at = @At("HEAD"), method = "clickMouse")
//  private void leftClick(CallbackInfo ci) {
//    if (Recorder.INSTANCE.getRecording()) {
//      Recorder.INSTANCE.getWriteLaterLock().lock();
//      ClientEvent.LeftClick.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
//      Recorder.INSTANCE.getWriteLaterLock().unlock();
//    }
//  }
//
//  @Inject(at = @At("HEAD"), method = "rightClickMouse")
//  private void rightClick(CallbackInfo ci) {
//    if (Recorder.INSTANCE.getRecording()) {
//      Recorder.INSTANCE.getWriteLaterLock().lock();
//      ClientEvent.RightClick.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
//      Recorder.INSTANCE.getWriteLaterLock().unlock();
//    }
//  }

  @Inject(at = @At("HEAD"), method = "processKeyBinds")
  private void preKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      Recorder.INSTANCE.getWriteLaterLock().lock();
      ClientEvent.Keybinds.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      ClientEvent.CurrentItem.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();
    } else if (Replay.INSTANCE.getReplaying()) {
      // Set key state
      for (int i = 0; i < Replay.INSTANCE.getTrackedKeybinds().length; i++) {
        Pair<Boolean, Integer> pair = Replay.INSTANCE.getKeybinds().get(i);
        ((KeyBindingAccessor) Replay.INSTANCE.getTrackedKeybinds()[i]).setPressed(pair.getFirst());
        ((KeyBindingAccessor) Replay.INSTANCE.getTrackedKeybinds()[i]).setPressTime(pair.getSecond());
      }
      Minecraft.getMinecraft().player.inventory.currentItem = Replay.INSTANCE.getCurrentItem();
    }
  }
}

