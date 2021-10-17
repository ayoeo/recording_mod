package me.aris.recordingmod.mixins;

import me.aris.recordingmod.Recorder;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.util.IThreadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketThreadUtil.class)
abstract class PacketThreadUtilMixin {
  @Inject(at = @At("HEAD"), method = "checkThreadAndEnqueue")
  private static <T extends INetHandler> void in(
    final Packet<T> packet,
    T processor,
    IThreadListener scheduler,
    CallbackInfo ci
  ) {
    if (scheduler.isCallingFromMinecraftThread()) {
      if (packet instanceof SPacketJoinGame) {
        Recorder.INSTANCE.joinGame();
      }

      Recorder.INSTANCE.savePacket(packet);
    }
  }
}

