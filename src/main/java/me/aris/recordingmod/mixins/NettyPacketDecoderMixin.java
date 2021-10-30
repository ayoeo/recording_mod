package me.aris.recordingmod.mixins;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import me.aris.recordingmod.Recorder;
import net.minecraft.network.*;
import net.minecraft.network.play.server.SPacketJoinGame;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = NettyPacketDecoder.class)
public class NettyPacketDecoderMixin {
  @Shadow
  @Final
  private EnumPacketDirection direction;

  @Inject(at = @At("HEAD"), method = "decode")
  private void in(
    ChannelHandlerContext context,
    ByteBuf bytes,
    List<Object> idk,
    CallbackInfo ci
  ) {
    try {
      EnumConnectionState connectionState = context.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get();
      if (connectionState != EnumConnectionState.PLAY) return;

      ByteBuf buf = bytes.duplicate();
      PacketBuffer packetbuffer = new PacketBuffer(buf);
      int i = packetbuffer.readVarInt();
      Packet<?> packet = connectionState.getPacket(this.direction, i);

      if (packet != null) {
        if (packet instanceof SPacketJoinGame) {
          Recorder.INSTANCE.joinGame();
        }

        if (Recorder.INSTANCE.getRecording()) {
          Recorder.INSTANCE.savePacket(i, packetbuffer);
        }
      }
    } catch (Exception ignored) {
      ignored.printStackTrace();
    }
  }
}
