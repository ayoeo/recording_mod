package me.aris.recordingmod.mixins;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
  @Accessor
  void setPressed(boolean pressed);

  @Accessor
  int getPressTime();

  @Accessor
  void setPressTime(int pressTime);
}
