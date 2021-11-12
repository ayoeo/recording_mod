package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
  @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V", shift = At.Shift.AFTER), method = "updateCameraAndRender")
  private void postGui(float partialTicks, long nanoTime, CallbackInfo ci) {
    ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
    if (guiState != null) {
      guiState.drawMouseCursor(Minecraft.getMinecraft().getRenderPartialTicks());
    }
  }

  private static Pair<Integer, Integer> lastPosition = new Pair<>(0, 0);
  private static long lastTime = 0L;

  @Inject(at = @At("HEAD"), method = "updateCameraAndRender")
  private void render(float partialTicks, long nanoTime, CallbackInfo ci) {
    if (Minecraft.getMinecraft().currentScreen == null && LiteModRecordingModKt.getActiveReplay() != null) {
      ReplayState.INSTANCE.updateCameraRotations(Minecraft.getMinecraft().getRenderPartialTicks());
      return;
    }

    if (Recorder.INSTANCE.getRecording()) {
      float adjX = (float) Mouse.getX() / (float) Minecraft.getMinecraft().displayWidth;
      float adjY = (float) Mouse.getY() / (float) Minecraft.getMinecraft().displayHeight;
      Pair<Integer, Integer> previous = lastPosition;//cursorPositions.get(cursorPositions.size() - 1);
      long deltaTime = Minecraft.getSystemTime() - lastTime;
      if ((previous.component1() == Mouse.getX()
        && previous.component2() == Mouse.getY()
        && deltaTime < 5) || deltaTime < 3
      ) {
        // eh
        return;
      }

      lastTime = Minecraft.getSystemTime();
      lastPosition = new Pair<>(Mouse.getX(), Mouse.getY());
      Recorder.INSTANCE.getCursorPositions().add(
        new RenderedPosition(
          partialTicks,
          new MousePosition(adjX, adjY)
        )
      );
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
      if (guiState != null && Minecraft.getMinecraft().currentScreen != null) {
        MousePosition pos = guiState.getLameMousePos(Minecraft.getMinecraft().getRenderPartialTicks());
        Pair<Integer, Integer> xy = pos.getScaledXY();
        int x = xy.getFirst();
        int y = xy.getSecond();
        Mouse.setGrabbed(true);
        Mouse.setCursorPosition(x, y);
      } else {
        // Center it : )
        Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);

        // HAHAHAHAHAHA LOOKING??
        // TODO
      }

    }
  }
}
