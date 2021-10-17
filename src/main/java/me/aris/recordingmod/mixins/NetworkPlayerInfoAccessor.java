package me.aris.recordingmod.mixins;

import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.world.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetworkPlayerInfo.class)
public interface NetworkPlayerInfoAccessor {
  @Accessor
  void setGameType(GameType gameMode);
  
  @Accessor
  void setResponseTime(int responseTime);
}
