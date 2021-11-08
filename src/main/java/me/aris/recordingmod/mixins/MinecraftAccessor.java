package me.aris.recordingmod.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Queue;
import java.util.concurrent.FutureTask;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
  @Accessor
  Timer getTimer();

  @Accessor
  int getLeftClickCounter();

  @Accessor
  void setLeftClickCounter(int v);

  @Accessor
  int getRightClickDelayTimer();

  @Accessor
  void setRightClickDelayTimer(int v);

  @Invoker
  void invokeProcessKeyBinds();
}
