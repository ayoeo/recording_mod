package me.aris.recordingmod.mixins;

import net.minecraft.network.play.server.SPacketSpawnMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SPacketSpawnMob.class)
public interface SPacketSpawnMobAccessor {
  @Accessor(value = "x")
  void setX(double x);

  @Accessor(value = "y")
  void setY(double y);

  @Accessor(value = "z")
  void setZ(double z);
}


