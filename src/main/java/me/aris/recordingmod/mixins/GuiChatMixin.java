package me.aris.recordingmod.mixins;

import kotlin.Pair;
import me.aris.recordingmod.ClientEvent;
import me.aris.recordingmod.LiteModRecordingModKt;
import me.aris.recordingmod.ReplayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiChat.class)
public class GuiChatMixin {
  @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;getChatComponent(II)Lnet/minecraft/util/text/ITextComponent;"))
  private ITextComponent getChatComponent(GuiNewChat instance, int mx, int my) {
    return getiTextComponent(instance, mx, my);
  }

  @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;getChatComponent(II)Lnet/minecraft/util/text/ITextComponent;"))
  private ITextComponent clickgetChatComponent(GuiNewChat instance, int mx, int my) {
    return getiTextComponent(instance, mx, my);
  }

  @Nullable
  private ITextComponent getiTextComponent(GuiNewChat instance, int mx, int my) {
    if (LiteModRecordingModKt.getActiveReplay() != null) {
      ClientEvent.GuiState guiState = ReplayState.INSTANCE.getNextGuiState();
      int x, y;
      if (guiState != null && Minecraft.getMinecraft().currentScreen != null) {
        Pair<Integer, Integer> pos = guiState.getLameMousePos(Minecraft.getMinecraft().getRenderPartialTicks()).getScaledXY();
        x = pos.component1();
        y = pos.component2();
      } else {
        x = Display.getWidth() / 2;
        y = Display.getHeight() / 2;
      }
      return instance.getChatComponent(x, y);
    } else {
      return instance.getChatComponent(mx, my);
    }
  }
}
