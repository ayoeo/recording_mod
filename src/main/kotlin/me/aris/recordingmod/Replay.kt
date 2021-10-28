package me.aris.recordingmod

import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import me.aris.recordingmod.mixins.SPacketMultiBlockChangeAccessor
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.*
import java.io.File
import java.util.*
import kotlin.system.measureNanoTime

object ReplayState {
  var nextKeybindingState = ClientEvent.trackedKeybinds.map {
    Pair(false, 0)
  }

  var nextHeldItem = 0

  var nextYaw = 0f
  var nextPitch = 0f
}

class Replay(replayFile: File) {
  private var tickdex = 0
  private val ticks: MutableList<ReplayTick> = mutableListOf()
  private val restorePoints: HashMap<Int, ClientEvent.SavePoint> = hashMapOf()

  var netHandler = NetHandlerReplayClient(
    mc,
    null,
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

          // End of tick, push it
          if (clientEvent is ClientEvent.SavePoint) {
            this.restorePoints[this.ticks.size] = clientEvent
          }

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
    val chunkDataID =
      EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketChunkData())
    val multiBlockChangeID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketMultiBlockChange()
      )
    val blockChangeID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketBlockChange()
      )
    val chunkUnloadID =
      EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketUnloadChunk())
    val spawnPlayerID =
      EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketSpawnPlayer())
    val spawnMobID =
      EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, SPacketSpawnMob())
    val spawnEXPOrbID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketSpawnExperienceOrb()
      )
    val spawnPaintingID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketSpawnPainting()
      )
    val spawnObjectID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketSpawnObject()
      )
    val spawnGlobalID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketSpawnGlobalEntity()
      )
    val destroyEntityID =
      EnumConnectionState.PLAY.getPacketId(
        EnumPacketDirection.CLIENTBOUND,
        SPacketDestroyEntities()
      )

    val ignoredPacketInfo = hashSetOf<Pair<Int, Int>>()

    // WOAH!
    val loadedChunks = hashMapOf<Pair<Int, Int>, Pair<Int, Int>>()
    val changedBlockChunks = hashMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
    val spawnedEntities = hashMapOf<Int, Pair<Int, Int>>()

    // pre process
    val preprocesstime = measureNanoTime {
      for (i in 0 until targetTick - tickdex) {
        val tick = this.ticks[this.tickdex + i]
        for ((i2, rawPacket) in tick.serverPackets.withIndex()) {
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
            for (entID in packet.entityIDs) {
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
    // TODO - get this from the same thing that stores replay metadata ^^^
    mc.gameSettings.thirdPersonView = 0;

    mc.ingameGUI.chatGUI.clearChatMessages(true)
    this.netHandler = NetHandlerReplayClient(
      mc,
      null,
      GameProfile(UUID.randomUUID(), "Guy")
    )
    mc.loadWorld(null)
  }

  fun skipBackwards(ticks: Int) {
    val targetTick = (this.tickdex - ticks).coerceAtLeast(0)
    this.restart()

    skipForward(targetTick)
  }

  // TODO - idk
//  fun skipTo(tickdex: Int) {
//    val targetTick = this.tickdex.coerceIn(0, this.ticks.size - 1)
//    skipForward(targetTick)
//  }

  fun skipForward(ticks: Int) {
    var targetTick = (this.tickdex + ticks).coerceAtMost(this.ticks.size - 1)
//    targetTick = 6030
//    targetTick = 21000
    println("TARGET TICK: $targetTick")
//    targetTick = 60 * 20

    this.playUntil(targetTick)
//    for (i in 0 until targetTick - tickdex) {
//      this.playNextTick()
//
//      // TODO - track perspective change, inventory open, close, ALL INVENTORY STUFF
//      // and yeah just like stuff like that replay it and it's fine
//    }
  }

  fun reachedEnd() = this.tickdex >= this.ticks.size

  // TODO - restore
  private fun findClosestRestorePoint(
    targetTick: Int,
    after: Boolean
  ): Pair<Int, ClientEvent.SavePoint>? {
//    val targetTick = 18000
    var current: Pair<Int, ClientEvent.SavePoint>? = null
    // TODO - binary search
    if (!after) {
      for ((tickdex, point) in restorePoints) {
        if (tickdex < targetTick) {
          current = Pair(tickdex, point)
        } else {
          break
        }
      }
    } else {
      for ((tickdex, point) in restorePoints) {
        if (tickdex > targetTick) {
          current = Pair(tickdex, point)
          break
        }
      }
    }

    return current
  }
}
