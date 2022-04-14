package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

  @Shadow
  public GuiIngame ingameGUI;

  @Shadow
  public int displayWidth;

  @Shadow
  public static Minecraft getMinecraft() {
    return null;
  }

  @Shadow
  public int displayHeight;


  @Shadow
  public GameSettings gameSettings;

  @Inject(at = @At("HEAD"), method = "getSystemTime", cancellable = true)
  private static void getSystemTime(CallbackInfoReturnable<Long> ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      Long l = ReplayState.INSTANCE.getSystemTime();
      if (l != null)
        ci.setReturnValue(l);
    }
  }

  @Inject(at = @At("HEAD"), method = "resize", cancellable = true)
  private void onResize(CallbackInfo ci) {
    if (Renderer.INSTANCE.isRendering()) {
      if (Renderer.INSTANCE.isProxy()) {
        displayWidth = LiteModRecordingMod.mod.getProxyRenderingWidth();
        displayHeight = LiteModRecordingMod.mod.getProxyRenderingHeight();
      } else {
        displayWidth = LiteModRecordingMod.mod.getRenderingWidth();
        displayHeight = LiteModRecordingMod.mod.getRenderingHeight();
      }
      ci.cancel();
    } else if (Recorder.INSTANCE.getRecording()) {
      ClientEvent.writeClientEvent(ClientEvent.Resize.INSTANCE);
    }
  }

  @Inject(at = @At("HEAD"), method = "runGameLoop", cancellable = true)
  private void preGameLoop(CallbackInfo ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      if (LiteModRecordingModKt.preGameLoop())
        ci.cancel();
    }
  }

  @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V", shift = At.Shift.BEFORE), method = "runGameLoop")
  private void preSwapBuffers(CallbackInfo ci) {
    if (Renderer.INSTANCE.isRendering()) {
      Renderer.INSTANCE.captureFrame();
    }
    if (Renderer.INSTANCE.isRendering()) {
      Renderer.INSTANCE.drawOverlay();
    }
  }

  @Inject(at = @At("HEAD"), method = "displayGuiScreen")
  private void onGuiClose(@Nullable GuiScreen guiScreenIn, CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      if (guiScreenIn == null) {
        ClientEvent.writeClientEvent(ClientEvent.CloseScreen.INSTANCE);
      }

      if ((guiScreenIn instanceof GuiMainMenu || guiScreenIn instanceof GuiMultiplayer) && Recorder.INSTANCE.getRecording()) {
        Recorder.INSTANCE.leaveGame();
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


  @Redirect(method = "setIngameFocus", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseHelper;grabMouseCursor()V"))
  public void grabMouseCursor(MouseHelper instance) {
    if (LiteModRecordingModKt.getActiveReplay() == null || !LiteModRecordingModKt.getLockPov()) {
      Mouse.setGrabbed(true);
      instance.deltaX = 0;
      instance.deltaY = 0;
    }
  }

  @Redirect(method = "setIngameNotInFocus", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseHelper;ungrabMouseCursor()V"))
  public void ungrabMouseCursor(MouseHelper instance) {
    if (LiteModRecordingModKt.getActiveReplay() == null || !LiteModRecordingModKt.getLockPov()) {
      Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
      Mouse.setGrabbed(false);
    }
  }

  @Inject(at = @At("HEAD"), method = "runTick", cancellable = true)
  private void preTick(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording() && player != null) {
      ClientEvent.writeClientEvent(new ClientEvent.Look());

      // Save currently held down keys
      ClientEvent.GuiState.Companion.setKeysDown();

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
    if (Recorder.INSTANCE.getRecording()) {
      if (player != null) {
        ClientEvent.writeClientEvent(new ClientEvent.Absolutes());
      }

      // Gui Recording owwaaah
      float adjX = (float) Mouse.getX() / (float) this.displayWidth;
      float adjY = (float) Mouse.getY() / (float) this.displayHeight;

      // Fix centering maybe haha
      if (currentScreen == null) {
        adjX = (float) (Display.getWidth() / 2) / (float) this.displayWidth;
        adjY = (float) (Display.getHeight() / 2) / (float) this.displayHeight;
      }

//      Recorder.INSTANCE.getCursorPositions().add(
//        new RenderedPosition(
//          1.0f,
//          new MousePosition(adjX, adjY)
//        )
//      );

      ClientEvent.writeClientEvent(new ClientEvent.GuiState());
//      Recorder.INSTANCE.getCursorPositions().add(
//        new RenderedPosition(
//          0.0f,
//          new MousePosition(adjX, adjY)
//        )
//      );
//      }

      Recorder.INSTANCE.endTick();
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      ClientEvent.GuiState state = ReplayState.INSTANCE.getNextGuiProcessState();
      if (state != null) {
        state.executeEvents();
        ReplayState.INSTANCE.setNextGuiProcessState(null);
      }
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
        if (LiteModRecordingModKt.getLockPov()) {
          ((KeyBindingAccessor) ClientEvent.Companion.getTrackedKeybinds()[i]).setPressed(pair.getFirst());
          ((KeyBindingAccessor) ClientEvent.Companion.getTrackedKeybinds()[i]).setPressTime(pair.getSecond());
        }
      }
//        if (){
//      if (gameSettings.keyBindAttack.isKeyDown()) {
//        System.out.println("ooh weird this can't be good: " + LiteModRecordingModKt.getActiveReplay().getTickdex());
//        System.out.println("yawpitch :" + player.rotationYaw + ", " + player.rotationPitch);
//        ((KeyBindingAccessor) gameSettings.keyBindAttack).setPressed(false);
//      }

      if (Minecraft.getMinecraft().player != null) {
        Minecraft.getMinecraft().player.inventory.currentItem = ReplayState.INSTANCE.getNextHeldItem();
      }
    }
  }
}

