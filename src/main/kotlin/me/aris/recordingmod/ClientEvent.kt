package me.aris.recordingmod

import com.sun.javafx.geom.Vec3f
import me.aris.recordingmod.mixins.KeyBindingAccessor
import net.minecraft.network.PacketBuffer
import net.minecraft.util.math.Vec3d
import org.lwjgl.util.vector.Vector3f

enum class ClientEvent {
  TickEnd,
  Position,
  Sprinting,
  Keybinds,
  CurrentItem,
  Look;

  fun process(): Boolean {
    when (this) {
      TickEnd -> {
        return true
      }

      Position -> {
        Replay.travelArgs = Vector3f(
          bufferbuffersobufferbuffer.readFloat(),
          bufferbuffersobufferbuffer.readFloat(),
          bufferbuffersobufferbuffer.readFloat()
        )
        Replay.playerMotion = Vec3d(
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble()
        )
        Replay.playerPos = Vec3d(
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble()
        )
      }

      Sprinting -> {
        Replay.sprinting = bufferbuffersobufferbuffer.readBoolean()
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

      Position -> {
        packetBuffer.writeFloat(Replay.travelArgs.x)
        packetBuffer.writeFloat(Replay.travelArgs.y)
        packetBuffer.writeFloat(Replay.travelArgs.z)
        packetBuffer.writeDouble(mc.player.motionX)
        packetBuffer.writeDouble(mc.player.motionY)
        packetBuffer.writeDouble(mc.player.motionZ)
        packetBuffer.writeDouble(mc.player.posX)
        packetBuffer.writeDouble(mc.player.posY)
        packetBuffer.writeDouble(mc.player.posZ)
      }

      Sprinting -> {
        packetBuffer.writeBoolean(mc.player.isSprinting)
      }

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
