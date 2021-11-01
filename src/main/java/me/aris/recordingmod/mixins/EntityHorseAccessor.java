package me.aris.recordingmod.mixins;

import net.minecraft.entity.passive.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractHorse.class)
public interface EntityHorseAccessor {
  @Invoker
  void invokeMakeHorseRear();
}
