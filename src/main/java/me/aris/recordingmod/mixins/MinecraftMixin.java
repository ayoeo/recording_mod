package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.Recorder;
import me.aris.recordingmod.ReplayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @Shadow
  public GameSettings gameSettings;

  @Shadow
  @Nullable
  public GuiScreen currentScreen;

  @Shadow
  public EntityPlayerSP player;

  @Inject(at = @At("HEAD"), method = "runGameLoop", cancellable = true)
  private void preGameLoop(CallbackInfo ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      if (LiteModRecordingModKt.preGameLoop())
        ci.cancel();
    }
  }


  @Inject(at = @At("HEAD"), method = "displayGuiScreen")
  private void onGuiClose(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      if (!(currentScreen instanceof GuiContainer)) {
        ClientEvent.writeClientEvent(ClientEvent.CloseScreen.INSTANCE);
      }
    }
  }

  @Inject(at = @At("HEAD"), method = "displayInGameMenu", cancellable = true)
  private void gameAlwaysInFocus(CallbackInfo ci) {
    // TODO - record a client event here (opened escape menu)
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      ci.cancel();
    }
  }

  @Inject(at = @At("HEAD"), method = "runTick", cancellable = true)
  private void preTick(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      ClientEvent.writeClientEvent(new ClientEvent.Look());

      // Save key state if we're in a gui
      if (currentScreen != null) {
        ClientEvent.writeClientEvent(new ClientEvent.SetKeybinds());
      }
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      if (LiteModRecordingModKt.preTick()) {
        ci.cancel();
      }
    }
  }

  @Inject(at = @At("TAIL"), method = "runTick")
  private void postTick(CallbackInfo ci) {
//    if (ReplayState.INSTANCE.getNextPositionInfo() != null) {
//      ReplayState.INSTANCE.getNextPositionInfo().runit();
//    }

    if (Recorder.INSTANCE.getRecording()) {
      if (player != null) {
        ClientEvent.writeClientEvent(new ClientEvent.Absolutes());
      }
      Recorder.INSTANCE.endTick();
//      if (Recorder.INSTANCE.shouldSavePoint()) {
//        ClientEvent.writeClientEvent(new ClientEvent.SavePoint());

//      }
    }
  }

  @Inject(at = @At("TAIL"), method = "processKeyBinds")
  private void postKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      ClientEvent.writeClientEvent(new ClientEvent.HeldItem());

      if (gameSettings.keyBindUseItem.isKeyDown()) {
        Recorder.INSTANCE.setLastInteraction(Recorder.INSTANCE.getTickdex());
      }
    }
  }

  @Inject(at = @At("HEAD"), method = "processKeyBinds")
  private void preKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      ClientEvent.writeClientEvent(new ClientEvent.SetKeybinds());
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      // Set key state
      for (int i = 0; i < ClientEvent.Companion.getTrackedKeybinds().length; i++) {
        Pair<Boolean, Integer> pair = ReplayState.INSTANCE.getNextKeybindingState().get(i);
        ((KeyBindingAccessor) ClientEvent.Companion.getTrackedKeybinds()[i]).setPressed(pair.getFirst());
        ((KeyBindingAccessor) ClientEvent.Companion.getTrackedKeybinds()[i]).setPressTime(pair.getSecond());
      }

      if (Minecraft.getMinecraft().player != null) {
        Minecraft.getMinecraft().player.inventory.currentItem = ReplayState.INSTANCE.getNextHeldItem();
      }
    }
  }
}

