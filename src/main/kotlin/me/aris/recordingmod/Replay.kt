package me.aris.recordingmod

import me.aris.recordingmod.Replay.restorePoints
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.SPacketChunkData
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.max
import kotlin.system.measureNanoTime

object Replay {
  var fakeTicking = false
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
    mc.gameSettings.keyBindPickBlock,
    mc.gameSettings.keyBindInventory
  )

  var tickdex = 0L
  var replaying = false

  var keybinds = trackedKeybinds.map {
    Pair(false, 0)
  }

  val restorePoints = mutableListOf<RestorePoint>()

  var nextYaw = 0f
  var nextPitch = 0f

  var currentItem = 0

  // TODO - idk what else
  //  what do you mean I'm worried
  //  someone should look at that
//  fun replayOneTick() {
//    val useAbsolute = true
//    fakeTicking = true
//    val nanos = measureNanoTime {
//      replayOneTickPackets()
//      mc.runTick()
//    }
//    fakeTicking = false
//    runTicks += nanos
//  }

  var runTicks = 0L
  var readPackets = 0L
  var processPackets = 0L
  val packetsTime = hashMapOf<Class<Packet<*>>, Long>()

  fun generateRestorePoints() {
    var tickd = 0L
    while (true) {
      if (bufferbuffersobufferbuffer.readableBytes() == 0) {
        break
      }
      val i = bufferbuffersobufferbuffer.readVarInt()

      if (i < 0) {
        val enumIndex = (i * -1) - 1 // (-1, -2, -3) -> (0, 1, 2)
        val clientEvent = ClientEvent.values()[enumIndex]
        if (clientEvent.process(false, true)) {
          tickd++
        }

        if (clientEvent == ClientEvent.SavePoint) {
          println("Found save point at $tickd -> ${restorePoints.last().tickdex}")
        }
      } else {
        val size = bufferbuffersobufferbuffer.readVarInt()
        bufferbuffersobufferbuffer.skipBytes(size)
      }
    }

    initReplay() //bruv
  }

  fun replayOneTickPackets(finalPosition: Vec3d?) {
    tickdex++
    var process = 0L
    val nanos = measureNanoTime {
      while (true) {
        val i = bufferbuffersobufferbuffer.readVarInt()
        if (i < 0) {
          val enumIndex = (i * -1) - 1 // (-1, -2, -3) -> (0, 1, 2)
          val clientEvent = ClientEvent.values()[enumIndex]
          if (clientEvent.process(true, true)) break
        } else {
          val size = bufferbuffersobufferbuffer.readVarInt()
          val buf = PacketBuffer(bufferbuffersobufferbuffer.readBytes(size))
          val packet: Packet<NetHandlerReplayClient> =
            EnumConnectionState.PLAY.getPacket(
              EnumPacketDirection.CLIENTBOUND,
              i
            ) as Packet<NetHandlerReplayClient>

          packet.readPacketData(buf)
//          if (!ignorePacket(packet, finalPosition)) {
          val processNanos = measureNanoTime {
            packet.processPacket(netHandler)
          }
          val existing = packetsTime.getOrDefault<Class<*>, Long>(packet.javaClass, 0)
          packetsTime.put(packet.javaClass, existing + processNanos)
          process += processNanos
        }
//        }
      }
    }

    processPackets += process
    readPackets += (nanos - process)
  }

//  private fun ignorePacket(packet: Packet<*>, finalPosition: Vec3d?): Boolean {
//    val finalPosition = finalPosition ?: return false
//
//    if (packet is SPacketChunkData) {
////      packet.chunkX
//
//      // TODO - save this lol
//      val renderDistance = mc.gameSettings.renderDistanceChunks
//      val x = floor(finalPosition.x / 16.0).toInt()
//      val z = floor(finalPosition.z / 16.0).toInt()
//      // TODO - is this of by one?? idk
//      if ((packet.chunkX - x).absoluteValue >= renderDistance || (packet.chunkZ - z).absoluteValue >= renderDistance) {
//        return true
//      }
//    }
//
//    return false
//  }

  private fun findClosestRestorePoint(targetTick: Long, after: Boolean): RestorePoint? {
    val targetTick = 18000
//    val targetTick = 700
//    val targetTick = 1762
    var current: RestorePoint? = null
    // TODO - binary search
    if (!after) {
      for (point in restorePoints) {
        if (point.tickdex < targetTick /*- 100*/) { // 5 seconds of normal ticking
          current = point
        } else {
          break
        }
      }
    } else {
      for (point in restorePoints) {
        if (point.tickdex > targetTick /*- 100*/) { // 5 seconds of normal ticking
          current = point
          break
        }
      }
    }

    return current
  }

  fun skipForward(ticks: Long, after: Boolean, fake: Boolean) {
//    val after = false
    val targetTick = tickdex + ticks // TODO - find closest restore point before <---
    val restorePoint = findClosestRestorePoint(targetTick, after)

    runTicks = 0L
    readPackets = 0L
    processPackets = 0L
    if (restorePoint != null) {
      println("targeting $targetTick, found ${restorePoint.tickdex}")
      println("regular skipping ${(targetTick - restorePoint.tickdex)}")

      // Once
//      if (fake)
//        restorePoint.restoreFake()
//      else
      restorePoint.restore()
    } else {
      println("No restore points found")
    }
//    for (fuckYou in 0 until (targetTick - (restorePoint?.tickdex ?: 0))) {
//    mc.loadWorld(null)
//    initReplay()
//    Replay.tickdex = 0
//    for (fuckYou in 0 until (restorePoints.last().tickdex)) {
//      while (Keyboard.next()) {
//      }
//      replayOneTick()
//    }
//    val ticks = restorePoints.last().tickdex

    println("Skipped forward ${ticks / 20.0} seconds.")
    println("Tick Time: ${runTicks / 1000000.0}ms")
    println("Event Read Time: ${readPackets / 1000000.0}ms")
    println("Packet Process Time: ${processPackets / 1000000.0}ms")
    for ((oh, oho) in packetsTime.asIterable().sortedBy { it -> it.value }) {
      println("Packets: $oh, ${oho / 1000000}")
    }
//    ((mc as MinecraftAccessor).timer as TimerAccessor).setTickLength(100f)
  }

  fun rewind(ticks: Int) {
    // go to beginning??!?!
    mc.loadWorld(null)
    initReplay()
    val skip = max(tickdex - ticks, 0)
    tickdex = 0
    skipForward(skip, false, false)
  }
}
