package me.aris.recordingmod.mixins;

import me.aris.recordingmod.Shader;
import net.minecraft.client.shader.ShaderLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin implements Shader {
  @Final
  @Shadow
  private int shader;

  @Override
  public int shader() {
    return this.shader;
  }
}
