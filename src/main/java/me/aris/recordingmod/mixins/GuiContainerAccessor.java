package me.aris.recordingmod.mixins;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiContainer.class)
public interface GuiContainerAccessor {
  @Accessor
  void setHoveredSlot(Slot slot);

  @Invoker
  boolean invokeIsMouseOverSlot(Slot slotIn, int mouseX, int mouseY);
}
