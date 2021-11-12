package me.aris.recordingmod.mixins;

import me.aris.recordingmod.GuiInputEvent;
import me.aris.recordingmod.GuiInputEventWithMoreStuff;
import me.aris.recordingmod.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
abstract class GuiScreenMixin {
  @Redirect(method = "handleKeyboardInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;keyTyped(CI)V"))
  private void keyTyped(GuiScreen instance, char typedChar, int keyCode) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getGuiInputEvents().add(
        new GuiInputEventWithMoreStuff(
          new GuiInputEvent.KeyTypedEvent(typedChar, keyCode),
          Minecraft.getSystemTime(),
          Minecraft.getMinecraft().getRenderPartialTicks()
        )
      );
      System.out.println("when key is typed: " + typedChar);
    }
    ((GuiScreenAccessor) instance).invokeKeyTyped(typedChar, keyCode);
  }

  @Redirect(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseClicked(III)V"))
  private void mouseClicked(GuiScreen instance, int mouseX, int mouseY, int mouseButton) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getGuiInputEvents().add(
        new GuiInputEventWithMoreStuff(
          new GuiInputEvent.MouseClickedEvent(mouseX, mouseY, mouseButton),
          Minecraft.getSystemTime(),
          Minecraft.getMinecraft().getRenderPartialTicks()
        )
      );
    }
    ((GuiScreenAccessor) instance).invokeMouseClicked(mouseX, mouseY, mouseButton);
  }

  @Redirect(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseReleased(III)V"))
  private void mouseReleased(GuiScreen instance, int mouseX, int mouseY, int state) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getGuiInputEvents().add(
        new GuiInputEventWithMoreStuff(
          new GuiInputEvent.MouseReleasedEvent(mouseX, mouseY, state),
          Minecraft.getSystemTime(),
          Minecraft.getMinecraft().getRenderPartialTicks()
        )
      );
    }
    ((GuiScreenAccessor) instance).invokeMouseReleased(mouseX, mouseY, state);
  }

  @Redirect(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseClickMove(IIIJ)V"))
  private void mouseClickMove(
    GuiScreen instance, int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick
  ) {
    if (Recorder.INSTANCE.getRecording()) {
      Recorder.INSTANCE.getGuiInputEvents().add(
        new GuiInputEventWithMoreStuff(
          new GuiInputEvent.MouseClickMoveEvent(mouseX, mouseY, clickedMouseButton, timeSinceLastClick),
          Minecraft.getSystemTime(),
          Minecraft.getMinecraft().getRenderPartialTicks()
        )
      );
    }
    ((GuiScreenAccessor) instance).invokeMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
  }


  @Inject(method = "handleMouseInput", at = @At(value = "HEAD"))
  private void handleMouseInput(CallbackInfo ci) {
    if (Recorder.INSTANCE.getRecording()) {
      if (Mouse.getEventDWheel() != 0) {
        Recorder.INSTANCE.getGuiInputEvents().add(
          new GuiInputEventWithMoreStuff(
            new GuiInputEvent.ScrollEvent(Mouse.getEventDWheel()),
            Minecraft.getSystemTime(),
            Minecraft.getMinecraft().getRenderPartialTicks()
          )
        );
      }
    }
  }
}
