package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.Replay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @Shadow
  public EntityPlayerSP player;

  @Shadow
  public GameSettings gameSettings;

  @Inject(at = @At("HEAD"), method = "runGameLoop", cancellable = true)
  private void preGameLoop(CallbackInfo ci) {
    if (Replay.INSTANCE.getReplaying()) {
      if (LiteModRecordingModKt.preGameLoop())
        ci.cancel();
    }
  }

  @Inject(at = @At("HEAD"), method = "displayInGameMenu", cancellable = true)
  private void gameAlwaysInFocus(CallbackInfo ci) {
    // TODO - record a client event here (opened escape menu)
    if (Replay.INSTANCE.getReplaying()) {
      ci.cancel();
    }
  }

  @Inject(at = @At("HEAD"), method = "runTick", cancellable = true)
  private void preTick(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getWriteLaterLock().lock();

      ClientEvent.Look.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();
    } else if (Replay.INSTANCE.getReplaying()) {
      if (LiteModRecordingModKt.preTick()) {
        ci.cancel();
      }
    }
  }

  @Inject(at = @At("TAIL"), method = "runTick")
  private void postTick(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.endTick();
      ClientEvent.AbsolutePosition.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      if (Recorder.INSTANCE.shouldSavePoint()) {
        ClientEvent.SavePoint.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      }
    } else if (Replay.INSTANCE.getReplaying() && player != null) {
      // TODO - idk
    }
  }

  @Inject(at = @At("TAIL"), method = "processKeyBinds")
  private void postKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      Recorder.INSTANCE.getWriteLaterLock().lock();
      ClientEvent.CurrentItem.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();

      if (gameSettings.keyBindUseItem.isKeyDown()) {
        Recorder.INSTANCE.setLastInteraction(Recorder.INSTANCE.getTickdex());
      }
    }
  }

  @Inject(at = @At("HEAD"), method = "processKeyBinds")
  private void preKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      Recorder.INSTANCE.getWriteLaterLock().lock();
      ClientEvent.Keybinds.write(new PacketBuffer(Recorder.INSTANCE.getToWritelater()));
      Recorder.INSTANCE.getWriteLaterLock().unlock();
    } else if (Replay.INSTANCE.getReplaying()) {
      // Set key state
      for (int i = 0; i < Replay.INSTANCE.getTrackedKeybinds().length; i++) {
        Pair<Boolean, Integer> pair = Replay.INSTANCE.getKeybinds().get(i);
        ((KeyBindingAccessor) Replay.INSTANCE.getTrackedKeybinds()[i]).setPressed(pair.getFirst());
        ((KeyBindingAccessor) Replay.INSTANCE.getTrackedKeybinds()[i]).setPressTime(pair.getSecond());
      }
      if (Minecraft.getMinecraft().player != null) {
        Minecraft.getMinecraft().player.inventory.currentItem = Replay.INSTANCE.getCurrentItem();
      }
    }
  }
}

