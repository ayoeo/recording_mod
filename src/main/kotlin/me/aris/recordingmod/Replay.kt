package me.aris.recordingmod

import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer

object Replay {
  val trackedKeybinds = arrayOf(
    mc.gameSettings.keyBindForward,
    mc.gameSettings.keyBindBack,
    mc.gameSettings.keyBindRight,
    mc.gameSettings.keyBindLeft,
    mc.gameSettings.keyBindJump,
    mc.gameSettings.keyBindSprint,
    mc.gameSettings.keyBindSneak,
    mc.gameSettings.keyBindAttack,
    mc.gameSettings.keyBindUseItem,
    mc.gameSettings.keyBindTogglePerspective,
    mc.gameSettings.keyBindPlayerList,
    mc.gameSettings.keyBindPickBlock
  )

  var tickdex = 0
  var replaying = false
  var moveForward = 0f
  var moveStrafe = 0f
  var jumping = false
  var sneaking = false

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
