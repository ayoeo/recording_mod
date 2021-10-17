package me.aris.recordingmod

import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer

object Replay {
  val trackedKeybinds = arrayOf(
    mc.gameSettings.keyBindAttack,
    mc.gameSettings.keyBindUseItem,
    mc.gameSettings.keyBindSwapHands,
    mc.gameSettings.keyBindSprint
  )

  var replaying = false
  var moveForward = 0f
  var moveStrafe = 0f
  var jumping = false
  var sneaking = false

  var keybinds = trackedKeybinds.map {
    Pair(false, 0)
  }

  //  var sprinting = false
  var nextYaw = 0f
  var nextPitch = 0f

  private fun readTickEvents() {
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

  // Processes up to one tick of packets
  fun processReplayPackets() {
    readTickEvents()
  }
}
