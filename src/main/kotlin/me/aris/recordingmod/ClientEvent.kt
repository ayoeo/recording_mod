package me.aris.recordingmod

import me.aris.recordingmod.mixins.KeyBindingAccessor
import net.minecraft.network.PacketBuffer

enum class ClientEvent {
  TickEnd,
  CloseInventory,
  Keybinds,
  CurrentItem,
  Look;

  fun process(): Boolean {
    when (this) {
      TickEnd -> {
        return true
      }
      
      CloseInventory -> {
        mc.player.closeScreen()
      }

      Keybinds -> {
        Replay.keybinds = Replay.trackedKeybinds.map {
          Pair(bufferbuffersobufferbuffer.readBoolean(), bufferbuffersobufferbuffer.readVarInt())
        }
      }

      CurrentItem -> {
        Replay.currentItem = bufferbuffersobufferbuffer.readVarInt()
      }

      // Ticked look, not sub-tick look that's like a different thing
      Look -> {
        val yaw = bufferbuffersobufferbuffer.readFloat()
        val pitch = bufferbuffersobufferbuffer.readFloat()
        Replay.nextYaw = yaw
        Replay.nextPitch = pitch
      }
    }

    return false
  }

  fun write(packetBuffer: PacketBuffer) {
    val serializedIndex = (this.ordinal * -1) - 1
    packetBuffer.writeVarInt(serializedIndex)

    when (this) {
      TickEnd -> Unit
      CloseInventory -> Unit

      Keybinds -> {
        Replay.trackedKeybinds.forEach {
          it as KeyBindingAccessor
          packetBuffer.writeBoolean(it.isKeyDown)
          packetBuffer.writeVarInt(it.pressTime)
        }
      }

      CurrentItem -> {
        packetBuffer.writeVarInt(mc.player.inventory.currentItem)
      }

      Look -> {
        packetBuffer.writeFloat(mc.player.rotationYaw)
        packetBuffer.writeFloat(mc.player.rotationPitch)
      }
    }
  }
}
