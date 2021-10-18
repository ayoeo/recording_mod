package me.aris.recordingmod.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;
import java.util.concurrent.FutureTask;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
  @Accessor
  Timer getTimer();

  @Accessor
  Queue<FutureTask<?>> getScheduledTasks();
}
