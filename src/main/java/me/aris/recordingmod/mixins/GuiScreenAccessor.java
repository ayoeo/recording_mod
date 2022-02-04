package me.aris.recordingmod.mixins;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiScreen.class)
public interface GuiScreenAccessor {
  @Accessor
  void setEventButton(int i);

  @Invoker
  void invokeKeyTyped(char typedChar, int keyCode);

  @Invoker
  void invokeMouseClicked(int mouseX, int mouseY, int mouseButton);

  @Invoker
  void invokeMouseReleased(int mouseX, int mouseY, int state);

  @Invoker
  void invokeMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick);

  @Invoker
  void invokeDrawScreen(int mouseX, int mouseY, float partialTicks);
}
