package me.aris.recordingmod.mixins;

import me.aris.recordingmod.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.*;
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
  ) throws Exception {
    if (Minecraft.getMinecraft().isSingleplayer() && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
      if (packet instanceof SPacketJoinGame) {
        Recorder.INSTANCE.joinGame();
      }

      if (Recorder.INSTANCE.getRecording()) {
        Integer id = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
        PacketBuffer packetDataBuffer = new PacketBuffer(Recorder.INSTANCE.getPacketData());
        packet.writePacketData(packetDataBuffer);

        // The packet needs to be ok we have to safe it keep the packet safe
        packet.readPacketData(new PacketBuffer(Recorder.INSTANCE.getPacketData().duplicate()));

        if (id != null) {
          Recorder.INSTANCE.savePacket(id, packetDataBuffer);
        }
        Recorder.INSTANCE.getPacketData().clear();
      }
    }
  }
}

