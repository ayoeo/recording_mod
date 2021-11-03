package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @Shadow
  @Nullable
  public GuiScreen currentScreen;

  @Shadow
  public EntityPlayerSP player;

  @Shadow
  protected abstract void resize(int width, int height);

  @Shadow
  private static int debugFPS;

  @Inject(at = @At("HEAD"), method = "runGameLoop", cancellable = true)
  private void preGameLoop(CallbackInfo ci) {
    if (Keyboard.isKeyDown(Keyboard.KEY_M)) {
      this.resize(15360, 8640);
    }
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      if (LiteModRecordingModKt.preGameLoop())
        ci.cancel();
    }
  }


  @Inject(at = @At("HEAD"), method = "displayGuiScreen")
  private void onGuiClose(@Nullable GuiScreen guiScreenIn, CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      if (!(currentScreen instanceof GuiContainer) && guiScreenIn == null) {
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
    if (Recorder.INSTANCE.getRecording() && player != null) {
      ClientEvent.writeClientEvent(new ClientEvent.Look());

      // Save key state if we're in a gui
      if (currentScreen != null) {
        ClientEvent.writeClientEvent(new ClientEvent.SetKeybinds());

        // Gui Recording owwaaah
        float adjX = (float) Mouse.getX() / (float) Display.getWidth();
        float adjY = (float) (Display.getHeight() - Mouse.getY()) / (float) Display.getHeight();
        Recorder.INSTANCE.getCursorPositions().add(
          new RenderedPosition(
            1.0f,
            new MousePosition(adjX, adjY)
          )
        );

        ClientEvent.writeClientEvent(new ClientEvent.GuiState());
        Recorder.INSTANCE.getCursorPositions().add(
          new RenderedPosition(
            0.0f,
            new MousePosition(adjX, adjY)
          )
        );
      }
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      if (LiteModRecordingModKt.preTick()) {
        ci.cancel();
      }
    }
  }

  @Inject(at = @At("TAIL"), method = "runTick")
  private void postTick(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      if (player != null) {
        ClientEvent.writeClientEvent(new ClientEvent.Absolutes());
      }
      Recorder.INSTANCE.endTick();
    }
  }

  @Inject(at = @At("TAIL"), method = "processKeyBinds")
  private void postKeyprocess(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      // Save current key state
      ClientEvent.writeClientEvent(new ClientEvent.HeldItem());
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

