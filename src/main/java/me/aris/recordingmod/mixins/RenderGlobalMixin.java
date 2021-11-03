package me.aris.recordingmod.mixins;

import me.aris.recordingmod.LiteModRecordingModKt;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(RenderGlobal.class)
abstract class RenderGlobalMixin {
  @Shadow
  private Set<RenderChunk> chunksToUpdate;

  @Shadow
  public abstract void updateChunks(long finishTimeNano);

  @Inject(at = @At("TAIL"), method = "updateChunks")
  private void fixChunkThing(long finishTime, CallbackInfo ci) {
    if (LiteModRecordingModKt.getActiveReplay() != null && !this.chunksToUpdate.isEmpty()) {
      // EXTENDING CHUNK LOAD HAHAHAHAEEHAH
      this.updateChunks(finishTime + 1000000000);
    }
  }
}
