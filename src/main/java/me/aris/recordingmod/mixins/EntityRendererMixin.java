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
  @Inject(at = @At("HEAD"), method = "updateCameraAndRender")
  private void render(float partialTicks, long nanoTime, CallbackInfo ci) {
    if (Minecraft.getMinecraft().currentScreen == null) return;

    if (Recorder.INSTANCE.getRecording()) {
      float adjX = (float) Mouse.getX() / (float) Display.getWidth();
      float adjY = (float) (Display.getHeight() - Mouse.getY()) / (float) Display.getHeight();
      Recorder.INSTANCE.getCursorPositions().add(
        new RenderedPosition(
          partialTicks,
          new MousePosition(adjX, adjY)
        )
      );
    } else if (LiteModRecordingModKt.getActiveReplay() != null) {
      ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
      if (guiState != null) {
        RenderedPosition pos = guiState.getMousePos(Minecraft.getMinecraft().getRenderPartialTicks());
        Pair<Integer, Integer> xy = pos.component2().getScaledXY();
        int x = xy.getFirst();
        int y = xy.getSecond();
        Mouse.setGrabbed(true);
        Mouse.setCursorPosition(x, y);
        // TODO - how do I move mouse!?!??!!! to here?!?!!!
      }
    }
  }
}
