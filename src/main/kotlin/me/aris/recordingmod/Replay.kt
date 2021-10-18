package me.aris.recordingmod

import com.sun.javafx.geom.Vec3f
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import net.minecraft.util.math.Vec3d
import org.lwjgl.util.vector.Vector3f

object Replay {
  var tickdex = 0
  var replaying = false

  val trackedKeybinds = arrayOf(
    mc.gameSettings.keyBindAttack,
    mc.gameSettings.keyBindUseItem,
    mc.gameSettings.keyBindTogglePerspective,
    mc.gameSettings.keyBindPlayerList,
    mc.gameSettings.keyBindPickBlock,
    mc.gameSettings.keyBindSneak
  )

  var sprinting = false
  var playerPos: Vec3d = Vec3d(0.0, 0.0, 0.0)
  var playerMotion: Vec3d = Vec3d(0.0, 0.0, 0.0)
  var travelArgs: Vector3f = Vector3f(0.0f, 0.0f, 0.0f)

  var keybinds = trackedKeybinds.map {
    Pair(false, 0)
  }

  var nextYaw = 0f
  var nextPitch = 0f

  var currentItem = 0

  // TODO - idk what else
  //  what do you mean I'm worried
  fun replayOneTick() {
    // replay the not packet part
//    (mc as MinecraftAccessor).timer.updateTimer();

//    synchronized((mc as MinecraftAccessor).scheduledTasks) {
//      while (!(mc as MinecraftAccessor).scheduledTasks.isEmpty()) {
//        Util.runTask(
//          (mc as MinecraftAccessor).scheduledTasks.poll(),
//          (mc as MinecraftAccessor).logger
//        )
//      }
//    }

    // tick!?!?
    mc.runTick()
  }

  fun replayOneTickPackets() {
    while (true) {
      val i = bufferbuffersobufferbuffer.readVarInt()

      if (i < 0) {
        val enumIndex = (i * -1) - 1 // (-1, -2, -3) -> (0, 1, 2)
        val clientEvent = ClientEvent.values()[enumIndex]
        if (clientEvent.process()) return
      } else {
        val size = bufferbuffersobufferbuffer.readVarInt()
        val buf = PacketBuffer(bufferbuffersobufferbuffer.readBytes(size))
        val packet: Packet<NetHandlerReplayClient> =
          EnumConnectionState.PLAY.getPacket(
            EnumPacketDirection.CLIENTBOUND,
            i
          ) as Packet<NetHandlerReplayClient>

        packet.readPacketData(buf)
        packet.processPacket(netHandler)
      }
    }
  }
}
