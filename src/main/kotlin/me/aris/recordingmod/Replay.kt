package me.aris.recordingmod

import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import me.aris.recordingmod.PacketIDsLol.blockChangeID
import me.aris.recordingmod.PacketIDsLol.bossBarID
import me.aris.recordingmod.PacketIDsLol.chunkDataID
import me.aris.recordingmod.PacketIDsLol.chunkUnloadID
import me.aris.recordingmod.PacketIDsLol.destroyEntityID
import me.aris.recordingmod.PacketIDsLol.explosionID
import me.aris.recordingmod.PacketIDsLol.multiBlockChangeID
import me.aris.recordingmod.PacketIDsLol.playerListID
import me.aris.recordingmod.PacketIDsLol.respawnID
import me.aris.recordingmod.PacketIDsLol.scoreboardID
import me.aris.recordingmod.PacketIDsLol.spawnEXPOrbID
import me.aris.recordingmod.PacketIDsLol.spawnGlobalID
import me.aris.recordingmod.PacketIDsLol.spawnMobID
import me.aris.recordingmod.PacketIDsLol.spawnObjectID
import me.aris.recordingmod.PacketIDsLol.spawnPaintingID
import me.aris.recordingmod.PacketIDsLol.spawnPlayerID
import me.aris.recordingmod.PacketIDsLol.teamsID
import me.aris.recordingmod.mixins.GuiContainerCreativeAccessor
import me.aris.recordingmod.mixins.SPacketMultiBlockChangeAccessor
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiContainerCreative
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.*
import net.minecraft.util.math.MathHelper
import org.lwjgl.input.Keyboard
import java.io.File
import java.util.*
import kotlin.math.hypot
import kotlin.system.measureNanoTime

data class TimestampedRotation(val yaw: Float, val pitch: Float, val timestamp: Timestamp)
data class Timestamp(val tickdex: Int, val partialTicks: Float) {
  operator fun minus(timestamp: Timestamp) =
    (this.tickdex - timestamp.tickdex).toFloat() + (this.partialTicks - timestamp.partialTicks)
}

object ReplayState {
  private fun findNext(replay: Replay, now: Timestamp): TimestampedRotation? {
    var hahaha: TimestampedRotation? = null
    if (replay.tickdex >= replay.ticks.size) return null

    val (i, _) = replay.ticks
      .subList(replay.tickdex, replay.ticks.size - 1)
      .withIndex()
      .find { (i, tick) ->
        tick.clientEvents.any { clientEvent ->
          val ret = clientEvent is ClientEvent.Look && clientEvent.cameraRotations.any {
//            println("${it.timestamped(replay.tickdex + i).timestamp}, $now")
            val timestamped = it.timestamped(replay.tickdex + i)
            val ret = timestamped.timestamp - now > 0f
            if (ret) {
              // hahahaheehah
              hahaha = timestamped
            }
            ret
          }
          ret
        }
      } ?: return null

    return hahaha
  }

  fun updateCameraRotations(partialTicks: Float) {
    val replay = activeReplay!!
    val tickdex = replay.tickdex
    val now = Timestamp(tickdex, partialTicks)

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float) = hypot(x1 - x2, y1 - y2)

    val prevDelta = distance(
      this.nextRotation.yaw,
      this.nextRotation.pitch,
      this.prevRotation.yaw,
      this.prevRotation.pitch
    )
    var nextRotationAtTick = findNext(replay, now)

    nextRotationAtTick?.let {
      val currentDelta = distance(
        this.nextRotation.yaw,
        this.nextRotation.pitch,
        it.yaw,
        it.pitch
      )

      val afterNextRotationsAtTick = findNext(replay, it.timestamp)
      if (afterNextRotationsAtTick != null) {
        val nextDelta = distance(
          afterNextRotationsAtTick.yaw,
          afterNextRotationsAtTick.pitch,
          it.yaw,
          it.pitch
        )

        if (nextDelta > currentDelta && currentDelta < prevDelta * 0.8) {
//          println("low old delta ($prevDelta) ($currentDelta), skipping")
          nextRotationAtTick = afterNextRotationsAtTick
        }
      }
    }
//    println("latest: ${latestRotation?.timestamp}, $now")

    nextRotationAtTick?.let {
      if (it.timestamp - this.nextRotation.timestamp > 0f && it.timestamp - now > 0f) {
//        println("cool new next: ${it.timestamp - now}")
//        this.prevRotation = this.nextRotation
        this.nextRotation = it
      }
    }
    val (yaw, pitch) = calculateRotation(this.nextRotation, this.prevRotation, now)
    mc.player?.let {
      it.prevRotationYaw = yaw
      it.prevRotationPitch = pitch
      it.rotationYaw = yaw
      it.rotationPitch = pitch
    }
    this.prevRotation = TimestampedRotation(yaw, pitch, now)
  }


  private fun calculateRotation(
    next: TimestampedRotation,
    prev: TimestampedRotation,
    now: Timestamp
  ): Pair<Float, Float> {
    if (prev == next) {
      return Pair(next.yaw, next.pitch)
    }

    val timeBetween = next.timestamp - prev.timestamp
    val whereweat = now - prev.timestamp
    // TODO - is this right lmao
    val partialPartialTicks = (now - prev.timestamp) / timeBetween
    val deltaX = next.yaw - prev.yaw
    val deltaY = next.pitch - prev.pitch

    return Pair(
      deltaX * partialPartialTicks + prev.yaw,
      deltaY * partialPartialTicks + prev.pitch
    )
  }

  private var prevRotation = TimestampedRotation(0f, 0f, Timestamp(0, 0f))
  private var nextRotation = TimestampedRotation(0f, 0f, Timestamp(0, 0f))

  fun resetRotations() {
    this.prevRotation = TimestampedRotation(0f, 0f, Timestamp(0, 0f))
    this.nextRotation = TimestampedRotation(0f, 0f, Timestamp(0, 0f))
  }

  var nextKeybindingState = ClientEvent.trackedKeybinds.map {
    Pair(false, 0)
  }

  var nextGuiState: ClientEvent.GuiState? = null
  var nextGuiStateButLikeAfterThisOneGodHelpUsAll: ClientEvent.GuiState? = null
  var currentGuiState: ClientEvent.GuiState? = null


  data class CameraRotationsAround(
    val current: CameraRotationsAtTick?,
    val next: CameraRotationsAtTick?
  )

//  var cameraRotations = CameraRotationsAround(null, null)

  var nextGuiProcessState: ClientEvent.GuiState? = null
  var scaledRes = Triple(640.0, 360.0, 1)
  var systemTime: Long? = null

  var nextHeldItem = 0

  var nextYaw = 0f
  var nextPitch = 0f
  var nextAbsoluteState: ClientEvent.Absolutes? = null
}

data class CameraRotationsAtTick(val cameraRotations: List<CameraRotation>, val tickdex: Int)

class Replay(replayFile: File) {
  var tickdex = 0
  val ticks: MutableList<ReplayTick> = mutableListOf()

  var netHandler = NetHandlerReplayClient(
    mc,
    null, // TODO - make this the replay screen !!!!!
    GameProfile(UUID.randomUUID(), "Guy")
  )

  init {
    val loadTime = measureNanoTime {
      val buffer = PacketBuffer(Unpooled.wrappedBuffer(replayFile.readBytes()))
      val clientEvents = mutableListOf<ClientEvent>()
      val serverPackets = mutableListOf<RawServerPacket>()

      while (true) {
        // Check if we've reached the end of our buffer
        if (buffer.readableBytes() == 0) break

        val i = buffer.readVarInt()
        if (i < 0) {
          val clientEvent = ClientEvent.eventFromId(i)
          clientEvent.loadFromBuffer(buffer)

          if (clientEvent is ClientEvent.TickEnd) {
            this.ticks.add(ReplayTick(clientEvents.toList(), serverPackets.toList()))
            clientEvents.clear()
            serverPackets.clear()
            continue
          } else {
            clientEvents.add(clientEvent)
          }
        } else {
          val size = buffer.readVarInt()
          serverPackets.add(RawServerPacket(i, size, buffer.readBytes(size)))
        }
      }
    }

    println("Total load time for ${replayFile.name}: ${loadTime / 1000000}ms")
  }

  private fun playUntil(targetTick: Int) {
    skipping = true
    val ignoredPacketInfo = hashSetOf<Pair<Int, Int>>()

    // WOAH!
    val loadedChunks = hashMapOf<Pair<Int, Int>, Pair<Int, Int>>()
    val changedBlockChunks = hashMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
    val ignoredChunks =
      hashMapOf<Pair<Int, Int>, MutableList<Pair<Pair<Int, Int>, Pair<Int, Int>>>>()
    val spawnedEntities = hashMapOf<Int, Pair<Int, Int>>()
    var lastRespawnPacket: Pair<Int, Int>? = null

    // pre process
    val preprocesstime = measureNanoTime {
      for (i in 0 until targetTick - tickdex) {
        val tick = this.ticks[this.tickdex + i]
        tick.serverPackets.withIndex().forEach { (i2, rawPacket) ->
          // OH YEAH
          val packetProcessIndex = Pair(i, i2)

          // Block changes
          if (rawPacket.packetID == explosionID) {
            val packet = rawPacket.cookPacket() as SPacketExplosion
            packet.affectedBlockPositions.map {
              val chunkX = it.x shr 4
              val chunkZ = it.z shr 4
              changedBlockChunks.getOrPut(Pair(chunkX, chunkZ)) {
                mutableListOf()
              }.add(packetProcessIndex)
            }
          }
          if (rawPacket.packetID == multiBlockChangeID) {
            val packet = rawPacket.cookPacket() as SPacketMultiBlockChange
            val chunkPos = (packet as SPacketMultiBlockChangeAccessor).chunkPos
            changedBlockChunks.getOrPut(Pair(chunkPos.x, chunkPos.z)) {
              mutableListOf()
            }.add(packetProcessIndex)
          }
          if (rawPacket.packetID == blockChangeID) {
            val packet = rawPacket.cookPacket() as SPacketBlockChange
            val chunkX = packet.blockPosition.x shr 4
            val chunkZ = packet.blockPosition.z shr 4
            changedBlockChunks.getOrPut(Pair(chunkX, chunkZ)) {
              mutableListOf()
            }.add(packetProcessIndex)
          }

          // Chunk loads
          if (rawPacket.packetID == chunkDataID) {
            val packet = rawPacket.cookPacket() as SPacketChunkData
            val chunkCoords = Pair(packet.chunkX, packet.chunkZ)
            loadedChunks[chunkCoords] = packetProcessIndex
          }

          // Spawning stuff
          val id = when (rawPacket.packetID) {
            spawnPlayerID -> (rawPacket.cookPacket() as SPacketSpawnPlayer).entityID
            spawnMobID -> (rawPacket.cookPacket() as SPacketSpawnMob).entityID
            spawnEXPOrbID -> (rawPacket.cookPacket() as SPacketSpawnExperienceOrb).entityID
            spawnPaintingID -> (rawPacket.cookPacket() as SPacketSpawnPainting).entityID
            spawnObjectID -> (rawPacket.cookPacket() as SPacketSpawnObject).entityID
            spawnGlobalID -> (rawPacket.cookPacket() as SPacketSpawnGlobalEntity).entityId
            else -> null
          }
          if (id != null) {
            if (Keyboard.isKeyDown(Keyboard.KEY_P))
              spawnedEntities[id] = packetProcessIndex
          }

          // UN AND DE
          // UN AND DE
          // UN AND DE

          // Entity despawns
          if (rawPacket.packetID == destroyEntityID) {
            val packet = rawPacket.cookPacket() as SPacketDestroyEntities
            packet.entityIDs.forEach { entID ->
              val spawnIndex = spawnedEntities[entID]
              if (spawnIndex != null) {
                if (Keyboard.isKeyDown(Keyboard.KEY_P))
                  ignoredPacketInfo.add(spawnIndex)
                spawnedEntities.remove(entID)
              }
            }
          }

          // Chunk unloads
          if (rawPacket.packetID == chunkUnloadID) {
            val packet = rawPacket.cookPacket() as SPacketUnloadChunk
            val chunkCoords = Pair(packet.x, packet.z)
            val loadIndex = loadedChunks[chunkCoords]
            if (loadIndex != null) {
              ignoredPacketInfo.add(loadIndex)
              ignoredPacketInfo.add(packetProcessIndex)

              // Track ignored chunks in case we need them back
              ignoredChunks.getOrPut(chunkCoords) {
                mutableListOf()
              }.add(Pair(loadIndex, packetProcessIndex))

              // Only remove unload chunk once!!!
              loadedChunks.remove(chunkCoords)
            }

            val changedIndices = changedBlockChunks[chunkCoords]
            if (changedIndices != null) {
              ignoredPacketInfo.addAll(changedIndices)
              changedBlockChunks.remove(chunkCoords)
            }
          }
        }
      }

      for (i in 0 until targetTick - tickdex) {
        val tick = this.ticks[this.tickdex + i]
        tick.serverPackets.withIndex().forEach { (i2, rawPacket) ->
          val packetProcessIndex = Pair(i, i2)

          // Who cares if the entity isn't spawned anyway right xd
          if (packetProcessIndex in ignoredPacketInfo) {
            return@forEach
          }

          val chunkCoords = when (rawPacket.packetID) {
            spawnPlayerID -> (rawPacket.cookPacket() as SPacketSpawnPlayer).let { Pair(it.x, it.z) }
            spawnMobID -> (rawPacket.cookPacket() as SPacketSpawnMob).let { Pair(it.x, it.z) }
            spawnEXPOrbID -> (rawPacket.cookPacket() as SPacketSpawnExperienceOrb).let {
              Pair(
                it.x,
                it.z
              )
            }
            spawnPaintingID -> (rawPacket.cookPacket() as SPacketSpawnPainting).let {
              Pair(
                it.position.x.toDouble(),
                it.position.z.toDouble()
              )
            }
            spawnObjectID -> (rawPacket.cookPacket() as SPacketSpawnObject).let { Pair(it.x, it.z) }
            spawnGlobalID -> (rawPacket.cookPacket() as SPacketSpawnGlobalEntity).let {
              Pair(
                it.x,
                it.z
              )
            }
            else -> null
          }

          if (chunkCoords != null) {
            val packetsToRestore = ignoredChunks[chunkCoords.let {
              Pair(
                MathHelper.floor(it.first / 16.0),
                MathHelper.floor(it.second / 16.0)
              )
            }]

            // Allow these chunks through because they're necessary for the entity to spawn correctly
            packetsToRestore?.forEach {
              ignoredPacketInfo.remove(it.first)
              ignoredPacketInfo.remove(it.second)
            }
          }
        }
      }
    }

    println("Preprocess time: ${preprocesstime / 1000000}")

    val replayTime = measureNanoTime {
      for (i in 0 until targetTick - tickdex) {
        if (this.tickdex < this.ticks.size) {
          this.ticks[this.tickdex].replayFast(i, ignoredPacketInfo)
          this.tickdex++
        }
      }
    }
    println("Replay time: ${replayTime / 1000000}")

    skipping = false
  }

  fun playNextTick() {
    if (this.tickdex < this.ticks.size) {
//      val mc = mc as MinecraftAccessor
      this.ticks[this.tickdex].replayFull()
      this.tickdex++
    }
  }

  fun restart() {
    ReplayState.resetRotations()
    val scaled = ScaledResolution(mc)
    ReplayState.scaledRes =
      Triple(scaled.scaledWidth_double, scaled.scaledHeight_double, scaled.scaleFactor)

    // TODO - store uuid and stuff and name in the replay file
    // TODO - when you do this, also store the creative tab (lmao)
    // TODO - when you do this, also store the creative tab (lmao)
    GuiContainerCreative.INVENTORY_BACKGROUND // remove if you want this to crash lmao
    val selected = GuiContainerCreativeAccessor.getSelectedTabIndex()
    GuiContainerCreativeAccessor.setSelectedTabIndex(0)
    // TODO - when you do this, also store the creative tab (lmao)
    // TODO - when you do this, also store the creative tab (lmao)

    this.tickdex = 0
    ReplayState.nextAbsoluteState = null

    mc.ingameGUI.chatGUI.clearChatMessages(true)
    mc.gameSettings.showDebugInfo = true
    mc.loadWorld(null)
    this.netHandler = NetHandlerReplayClient(
      mc,
      null,
      GameProfile(UUID.randomUUID(), "Guy")
    )
  }

  fun skipBackwards(ticks: Int) {
    val targetTick = (this.tickdex - ticks).coerceAtLeast(0)
    skipTo(targetTick)
  }

  fun skipTo(targetTick: Int) {
    skipping = true
    this.restart()

    // Find last tickdex that contains a respawn packet
    if (targetTick > 20) {
      val fastTargetTick = (targetTick - 20).coerceAtLeast(0)
      var latestTickdex = 0
      val latestRespawnPacketAndOthers = mutableListOf<Pair<Int, Int>>()
      for (i in 0 until fastTargetTick) {
        val tick = this.ticks[i]
        tick.serverPackets.withIndex().forEach { (i2, rawPacket) ->
          val packetProcessIndex = Pair(i, i2)
          if (latestTickdex == i) {
            // store packets
            latestRespawnPacketAndOthers.add(packetProcessIndex)
          }
          if (rawPacket.packetID == respawnID) {
            latestTickdex = i
            latestRespawnPacketAndOthers.clear()

            latestRespawnPacketAndOthers.add(packetProcessIndex)
          }
        }
      }

      // Run that join packet
      this.ticks[0].serverPackets.firstOrNull()?.cookPacket()?.processPacket(netHandler)
      val firstTick = this.ticks[latestTickdex]

      // Replay important packets that bungee like doesn't care about???
      for (i in 0 until latestTickdex) {
        val tick = this.ticks[i]
        tick.serverPackets.filter {
          it.packetID == teamsID
            || it.packetID == bossBarID
            || it.packetID == scoreboardID
            || it.packetID == playerListID
          // TODO - add more stuff here if it's crashing lol
        }.forEach {
          it.cookPacket().processPacket(netHandler)
        }
      }

      // Respawn + extra packets that tick
      latestRespawnPacketAndOthers.forEach { (i, i2) ->
        this.ticks[i].serverPackets[i2].cookPacket().processPacket(netHandler)
      }

      // Client stuff too I guess
      firstTick.clientEvents.forEach { event ->
        event.processEvent(ReplayState)
      }

      this.tickdex = latestTickdex + 1

      // Ok we're good
      this.playUntil(fastTargetTick)
    }

    val wasPaused = paused
    paused = false
    for (i in 0..20.coerceAtMost(targetTick)) {
      mc.runTick()
    }
//    skipping = true
//    for (i in 0..80) {
//      mc.runTick()
//    }
    skipping = false
    paused = wasPaused
  }

  fun skipForward(ticks: Int) {
    val targetTick = (this.tickdex + ticks).coerceAtMost(this.ticks.size - 1)

    if (ticks > 20 * 30) {
      this.skipTo(targetTick)
    } else {
      this.playUntil(targetTick)
      for (i in 0..ticks) {
        break
        if (this.tickdex < this.ticks.size) {
          this.ticks[this.tickdex].replayFast(i, hashSetOf())
          this.tickdex++
        }
//        mc.runTick()
      }
    }
    println("SKIPPED!!! TO TARGET TICK: $targetTick")
  }

  fun reachedEnd() = this.tickdex >= this.ticks.size
}
