package me.aris.recordingmod.mixins;

import me.aris.recordingmod.LiteModRecordingModKt;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHelper.class)
abstract class MouseHelperMixin {
  @Shadow
  public int deltaX;

  @Shadow
  public int deltaY;
 
  @Redirect(at = @At("HEAD"), method = "mouseXYChange")
  private void mouseXYChange() {
    if (LiteModRecordingModKt.getActiveReplay() == null) {
      this.deltaX = Mouse.getDX();
      this.deltaY = Mouse.getDY();
    } else {
      this.deltaX = 0;
      this.deltaY = 0;
    }
  }
}
