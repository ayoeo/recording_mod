package me.aris.recordingmod.mixins;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainerCreative.class)
public interface GuiContainerCreativeAccessor {
  @Accessor
  static int getSelectedTabIndex() {
    throw new AssertionError();
  }

  @Accessor
  static void setSelectedTabIndex(int i) {
    throw new AssertionError();
  }
}
