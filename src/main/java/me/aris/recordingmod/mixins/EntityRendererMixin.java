package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
  @Shadow
  @Final
  private Minecraft mc;

  @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V", shift = At.Shift.AFTER), method = "updateCameraAndRender")
  private void postGui(float partialTicks, long nanoTime, CallbackInfo ci) {
    ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
    if (guiState != null) {
      guiState.drawMouseCursor(Minecraft.getMinecraft().getRenderPartialTicks());
    }
  }

  private static Pair<Integer, Integer> lastPosition = new Pair<>(0, 0);
  private static long lastTime = 0L;

  @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"))
  private void mouseClickMove(
    GuiScreen instance, int mouseX, int mouseY, float partialTicks
  ) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
      int x, y;
      if (guiState != null && Minecraft.getMinecraft().currentScreen != null) {
        MousePosition pos = guiState.getLameMousePos(Minecraft.getMinecraft().getRenderPartialTicks());
        Pair<Integer, Integer> xy = pos.getScaledXY();
        x = xy.getFirst();
        y = xy.getSecond();
      } else {
        // Center it : )
        x = Display.getWidth() / 2;
        y = Display.getHeight() / 2;
      }
      final ScaledResolution scaledRes = new ScaledResolution(this.mc);
      int scaledWidth = scaledRes.getScaledWidth();
      int scaledHeight = scaledRes.getScaledHeight();
      final int finalX = x * scaledWidth / this.mc.displayWidth;
      final int finalY = scaledHeight - y * scaledHeight / this.mc.displayHeight - 1;
      ((GuiScreenAccessor) instance).invokeDrawScreen(finalX, finalY, partialTicks);
    } else {
      ((GuiScreenAccessor) instance).invokeDrawScreen(mouseX, mouseY, partialTicks);
    }
  }

  @Inject(at = @At("HEAD"), method = "updateCameraAndRender")
  private void render(float partialTicks, long nanoTime, CallbackInfo ci) {
    if (Minecraft.getMinecraft().currentScreen == null && LiteModRecordingModKt.getActiveReplay() != null) {
      ReplayState.INSTANCE.updateCameraRotations(Minecraft.getMinecraft().getRenderPartialTicks());
      Renderer.INSTANCE.setSystemTime();
    } else if (Recorder.INSTANCE.getRecording()) {
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
//      ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
//      if (guiState != null && Minecraft.getMinecraft().currentScreen != null) {
//        MousePosition pos = guiState.getLameMousePos(Minecraft.getMinecraft().getRenderPartialTicks());
//        Pair<Integer, Integer> xy = pos.getScaledXY();
//        int x = xy.getFirst();
//        int y = xy.getSecond();
//        Mouse.setGrabbed(true);
//        Mouse.setCursorPosition(x, y);
////        Mouse.setGrabbed(false);
//      } else {
//        // Center it : )
////        Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
//
//        // HAHAHAHAHAHA LOOKING??
//        // TODO
//      }

      Renderer.INSTANCE.setSystemTime();
    }
  }

  private static float actualFrameTimeNotTheFakeOneThatOptifineThinksTheFrameTimeIs = 0f;

  @Inject(at = @At("HEAD"), method = "isDrawBlockOutline")
  private void onIsDrawBlockOutline(CallbackInfoReturnable<Boolean> cir) {
    Field frameTimeField = Renderer.INSTANCE.getFrameTimeField();
    Field frameTimeCounterField = Renderer.INSTANCE.getFrameTimeCounterField();
    if (frameTimeField != null && frameTimeCounterField != null) {
      try {
        float frameTime = 1f / ((float) LiteModRecordingMod.mod.getBlendFactor() * LiteModRecordingMod.mod.getRenderingFps());
        frameTimeField.setFloat(null, frameTime);

        actualFrameTimeNotTheFakeOneThatOptifineThinksTheFrameTimeIs += frameTime;
        actualFrameTimeNotTheFakeOneThatOptifineThinksTheFrameTimeIs %= 3600f;
        frameTimeCounterField.setFloat(null, actualFrameTimeNotTheFakeOneThatOptifineThinksTheFrameTimeIs);
      } catch (IllegalAccessException ignored) {
      }
    }
  }

  @Inject(at = @At("TAIL"), method = "updateCameraAndRender")
  private void postRender(float partialTicks, long nanoTime, CallbackInfo ci) {
    try {
      Field field = Renderer.INSTANCE.getLastSystemTimeField();
      Long systemTime = ReplayState.INSTANCE.getSystemTime();
      if (field != null && systemTime != null) {
        long frameTime = 1000L / ((long) LiteModRecordingMod.mod.getBlendFactor() * LiteModRecordingMod.mod.getRenderingFps());
        field.setLong(null, System.currentTimeMillis() - frameTime); // easy get
      }
    } catch (IllegalAccessException ignored) {
    }
    ReplayState.INSTANCE.setSystemTime(null);
  }
}
