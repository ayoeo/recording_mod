package me.aris.recordingmod

import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import me.aris.recordingmod.PacketIDsLol.blockChangeID
import me.aris.recordingmod.PacketIDsLol.bossBarID
import me.aris.recordingmod.PacketIDsLol.chunkDataID
import me.aris.recordingmod.PacketIDsLol.chunkUnloadID
import me.aris.recordingmod.PacketIDsLol.destroyEntityID
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
import me.aris.recordingmod.mixins.SPacketMultiBlockChangeAccessor
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.*
import net.minecraft.util.math.MathHelper
import java.io.File
import java.util.*
import kotlin.system.measureNanoTime

object ReplayState {
  var nextKeybindingState = ClientEvent.trackedKeybinds.map {
    Pair(false, 0)
  }
 
  var nextGuiState: ClientEvent.GuiState? = null

  var nextHeldItem = 0

  var nextYaw = 0f
  var nextPitch = 0f
  var nextAbsoluteState: ClientEvent.Absolutes? = null
}

class Replay(replayFile: File) {
  private var tickdex = 0
  private val ticks: MutableList<ReplayTick> = mutableListOf()

  var netHandler = NetHandlerReplayClient(
    mc,
    null, // TODO - make this the replay screen !!!!!
    GameProfile(UUID.randomUUID(), "Guy")
  )

  // TODO - option to store replay in memory or not
  //  we would have to do some major tweaking to make that work, though
  //  eh

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
                ignoredPacketInfo.add(spawnIndex)
                ignoredPacketInfo.add(packetProcessIndex)
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
//          this.ticks[this.tickdex].replayFull()
          this.tickdex++
        }
      }
    }
    println("Replay time: ${replayTime / 1000000}")
  }

  fun playNextTick() {
    if (this.tickdex < this.ticks.size) {
      this.ticks[this.tickdex].replayFull()
      this.tickdex++
    }
  }

  fun restart() {
    // TODO - store uuid and stuff and name in the replay file
    this.tickdex = 0
    ReplayState.nextAbsoluteState = null

    mc.ingameGUI.chatGUI.clearChatMessages(true)
    mc.gameSettings.showDebugInfo = true
    this.netHandler = NetHandlerReplayClient(
      mc,
      null,
      GameProfile(UUID.randomUUID(), "Guy")
    )
    mc.loadWorld(null)
  }

  fun skipBackwards(ticks: Int) {
    val targetTick = (this.tickdex - ticks).coerceAtLeast(0)
    skipTo(targetTick)
  }

  // TODO - EVERYTING IS SKIPTO NOW HAHAHHAHAHAHHA
  fun skipTo(targetTick: Int) {
    val playerDimension = mc.player.dimension
    this.restart()

    // Find last tickdex that contains a respawn packet

    var latestTickdex = 0
    val latestRespawnPacketAndOthers = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until targetTick) {
      val tick = this.ticks[i]
      tick.serverPackets.withIndex().forEach { (i2, rawPacket) ->
        // TODO - flip the order of these when changing dimensions
        val packetProcessIndex = Pair(i, i2)
        if (latestTickdex == i) {
          // store packets
          latestRespawnPacketAndOthers.add(packetProcessIndex)
        }
        if (rawPacket.packetID == respawnID) {
          latestTickdex = i
          latestRespawnPacketAndOthers.clear()

          // TODO - make sure this works with changing dimensions and like in singleplayer??????
          // TODO - make sure this works with changing dimensions and like in singleplayer??????
          // TODO - make sure this works with changing dimensions and like in singleplayer??????
          if ((rawPacket.cookPacket() as SPacketRespawn).dimensionID != playerDimension) {
            latestRespawnPacketAndOthers.add(packetProcessIndex)
          }
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
    this.playUntil(targetTick)

    // idk horse
//    mc.player.dismountRidingEntity()
//    mc.runTick()

    // but don't ruuuunnn itttt
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
    // TODO - rUN MORE TICKS
//    for (i in 0..20) {
//      mc.runTick()
//    }
  }

  fun skipForward(ticks: Int) {
    val targetTick = (this.tickdex + ticks).coerceAtMost(this.ticks.size - 1)

    println("SKIPPING!!! TO TARGET TICK: $targetTick")
    if (ticks > 20 * 60) {
      this.skipTo(targetTick)
    } else {
      this.playUntil(targetTick)
    }
    println("SKIPPED!!! TO TARGET TICK: $targetTick")
  }

  fun reachedEnd() = this.tickdex >= this.ticks.size
}
