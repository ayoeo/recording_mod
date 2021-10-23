package me.aris.recordingmod

import me.aris.recordingmod.Replay.replayOneTickPackets
import me.aris.recordingmod.Replay.restorePoints
import me.aris.recordingmod.mixins.KeyBindingAccessor
import net.minecraft.network.PacketBuffer
import net.minecraft.util.math.Vec3d

class RestorePoint(
  val posX: Double,
  val posY: Double,
  val posZ: Double,
  val motionX: Double,
  val motionY: Double,
  val motionZ: Double,
  val isFlying: Boolean,
  val perspective: Int,
  val sprintTicksLeft: Int,
  val sprinting: Boolean,
  val __________REMOVEITmountedEntityID: Int, // -1 for not mounted
  val tickdex: Long
) {
  fun restore() {
    mc.loadWorld(null)
    initReplay()
    Replay.tickdex = 0

    Replay.fakeTicking = true
    val last = this.tickdex - Replay.tickdex
    for (fuckYou in 0 until (this.tickdex - Replay.tickdex)) {
//      mc.player?.motionX = motionX
//      mc.player?.motionY = motionY
//      mc.player?.motionZ = motionZ
      mc.player?.capabilities?.isFlying = isFlying
      mc.gameSettings?.thirdPersonView = perspective
      mc.player?.sprintingTicksLeft = sprintTicksLeft
      mc.player?.isSprinting = sprinting

      println("$fuckYou, ${this.tickdex - Replay.tickdex - 2}")
//      if (fuckYou == last && mountedEntityID != -1) {
//        println("AAKKLKLAK")
      this.applyPlayerPositionAndStuff()
//      }

      replayOneTickPackets(Vec3d(posX, posY, posZ))
    }
    Replay.fakeTicking = false
  }

  private fun applyPlayerPositionAndStuff() {
    mc.player?.setPosition(posX, posY, posZ)
    mc.player?.motionX = motionX
    mc.player?.motionY = motionY
    mc.player?.motionZ = motionZ
    mc.player?.capabilities?.isFlying = isFlying
    mc.gameSettings?.thirdPersonView = perspective
    mc.player?.sprintingTicksLeft = sprintTicksLeft
    mc.player?.isSprinting = sprinting
  }
}

enum class ClientEvent {
  TickEnd,
  CloseInventory,
  Keybinds,
  CurrentItem,
  AbsolutePosition,
  Look,
  SavePoint;

  fun process(ingame: Boolean, useAbsolutePosition: Boolean): Boolean {
    when (this) {
      TickEnd -> {
        return true
      }

      CloseInventory -> {
        if (ingame) {
          mc.player.closeScreen()
        }
      }

      Keybinds -> {
        if (ingame) {
          Replay.keybinds = Replay.trackedKeybinds.map {
            Pair(bufferbuffersobufferbuffer.readBoolean(), bufferbuffersobufferbuffer.readVarInt())
          }
        } else {
          Replay.trackedKeybinds.forEach { _ ->
            Pair(bufferbuffersobufferbuffer.readBoolean(), bufferbuffersobufferbuffer.readVarInt())
          }
        }
      }

      CurrentItem -> {
        if (ingame) {
          Replay.currentItem = bufferbuffersobufferbuffer.readVarInt()
        } else {
          bufferbuffersobufferbuffer.readVarInt()
        }
      }

      AbsolutePosition -> {
        val riding = bufferbuffersobufferbuffer.readVarInt()
        val setPosOfThisThing = mc.world?.getEntityByID(riding) ?: mc.player
        val posX = bufferbuffersobufferbuffer.readDouble()
        val posY = bufferbuffersobufferbuffer.readDouble()
        val posZ = bufferbuffersobufferbuffer.readDouble()
        if (ingame) {
          setPosOfThisThing.setPosition(posX, posY, posZ)
        }
      }

      // Ticked look, not sub-tick look that's like a different thing
      Look -> {
        val yaw = bufferbuffersobufferbuffer.readFloat()
        val pitch = bufferbuffersobufferbuffer.readFloat()
        if (ingame) {
          Replay.nextYaw = yaw
          Replay.nextPitch = pitch
        }
      }

      SavePoint -> {
        val restorePoint = RestorePoint(
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readDouble(),
          bufferbuffersobufferbuffer.readBoolean(),
          bufferbuffersobufferbuffer.readVarInt(),
          bufferbuffersobufferbuffer.readVarInt(),
          bufferbuffersobufferbuffer.readBoolean(),
//          -1,
          bufferbuffersobufferbuffer.readVarInt(),
          bufferbuffersobufferbuffer.readLong()
        )

        if (!ingame) {
          // Push to stuff and stuff
          restorePoints.add(restorePoint)
        }
//        mc.player.setPositionAndRotation(posX, posY, posZ, yaw, pitch)
//        mc.player.motionX = motionX
//        mc.player.motionY = motionY
//        mc.player.motionZ = motionZ
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

      AbsolutePosition -> {
        val riding = mc.player.ridingEntity
        packetBuffer.writeVarInt(riding?.entityId ?: -1)
        packetBuffer.writeDouble(riding?.posX ?: mc.player.posX)
        packetBuffer.writeDouble(riding?.posY ?: mc.player.posY)
        packetBuffer.writeDouble(riding?.posZ ?: mc.player.posZ)
      }

      Look -> {
        packetBuffer.writeFloat(mc.player.rotationYaw)
        packetBuffer.writeFloat(mc.player.rotationPitch)
      }

      SavePoint -> {
        val savedEnt = mc.player.ridingEntity ?: mc.player

//        packetBuffer.writeDouble(savedEnt.posX)
//        packetBuffer.writeDouble(savedEnt.posY)
//        packetBuffer.writeDouble(savedEnt.posZ)
//
//        packetBuffer.writeDouble(savedEnt.motionX)
//        packetBuffer.writeDouble(savedEnt.motionY)
//        packetBuffer.writeDouble(savedEnt.motionZ)

        packetBuffer.writeBoolean(mc.player.capabilities.isFlying)
        packetBuffer.writeVarInt(mc.gameSettings.thirdPersonView)
        packetBuffer.writeVarInt(mc.player.sprintingTicksLeft)
        packetBuffer.writeBoolean(mc.player.isSprinting)

//        packetBuffer.writeVarInt(mc.player.ridingEntity?.entityId ?: -1)

        packetBuffer.writeLong(Recorder.tickdex)
      }
    }
  }
}
