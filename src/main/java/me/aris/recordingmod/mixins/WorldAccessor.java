package me.aris.recordingmod.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(World.class)
public interface WorldAccessor {
  @Accessor
  List<Entity> getUnloadedEntityList();

  @Invoker
  boolean invokeIsChunkLoaded(int x, int z, boolean allowEmpty);

  @Invoker
  void invokeOnEntityRemoved(Entity entityIn);

  @Invoker
  void invokeTickPlayers();
}
