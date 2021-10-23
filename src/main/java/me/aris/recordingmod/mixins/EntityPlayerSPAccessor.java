package me.aris.recordingmod.mixins;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPlayerSP.class)
public interface EntityPlayerSPAccessor {
  @Accessor
  int getSprintToggleTimer();
}


