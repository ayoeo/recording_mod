package me.aris.recordingmod

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.netty.buffer.Unpooled
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import me.aris.recordingmod.PacketIDsLol.blockChangeID
import me.aris.recordingmod.PacketIDsLol.bossBarID
import me.aris.recordingmod.PacketIDsLol.chatID
import me.aris.recordingmod.PacketIDsLol.chunkDataID
import me.aris.recordingmod.PacketIDsLol.chunkUnloadID
import me.aris.recordingmod.PacketIDsLol.customSoundID
import me.aris.recordingmod.PacketIDsLol.destroyEntityID
import me.aris.recordingmod.PacketIDsLol.displayObjective
import me.aris.recordingmod.PacketIDsLol.effectID
import me.aris.recordingmod.PacketIDsLol.entityEffectID
import me.aris.recordingmod.PacketIDsLol.explosionID
import me.aris.recordingmod.PacketIDsLol.headerFooterID
import me.aris.recordingmod.PacketIDsLol.joinGameID
import me.aris.recordingmod.PacketIDsLol.multiBlockChangeID
import me.aris.recordingmod.PacketIDsLol.playerListID
import me.aris.recordingmod.PacketIDsLol.respawnID
import me.aris.recordingmod.PacketIDsLol.scoreboardObjective
import me.aris.recordingmod.PacketIDsLol.soundID
import me.aris.recordingmod.PacketIDsLol.spawnEXPOrbID
import me.aris.recordingmod.PacketIDsLol.spawnGlobalID
import me.aris.recordingmod.PacketIDsLol.spawnMobID
import me.aris.recordingmod.PacketIDsLol.spawnObjectID
import me.aris.recordingmod.PacketIDsLol.spawnPaintingID
import me.aris.recordingmod.PacketIDsLol.spawnPlayerID
import me.aris.recordingmod.PacketIDsLol.teamsID
import me.aris.recordingmod.PacketIDsLol.updateScore
import me.aris.recordingmod.mixins.GuiContainerCreativeAccessor
import me.aris.recordingmod.mixins.MinecraftAccessor
import me.aris.recordingmod.mixins.SPacketMultiBlockChangeAccessor
import net.minecraft.client.gui.GuiDownloadTerrain
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiContainerCreative
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.*
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.util.math.MathHelper
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.event.HoverEvent
import org.lwjgl.input.Keyboard
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess
import kotlin.system.measureNanoTime

data class TimestampedRotation(val yaw: Float, val pitch: Float, var timestamp: Timestamp)
data class Timestamp(val tickdex: Int, val partialTicks: Float) {
  operator fun minus(timestamp: Timestamp) =
    (this.tickdex - timestamp.tickdex).toFloat() + (this.partialTicks - timestamp.partialTicks)
}

object ReplayState {
  private fun findNext(replay: Replay, now: Timestamp): TimestampedRotation? {
    var hahaha: TimestampedRotation? = null
    if (replay.tickdex >= replay.totalTicks) return null

    replay.keepLoading()

    val (i, _) = replay.ticks!!
      .subList(
        replay.tickdex - replay.loadedTickdex,
        min(replay.tickdex - replay.loadedTickdex + 4, replay.ticks!!.size - 1)
      )
      .withIndex()
      .find { (i, tick) ->
        tick?.clientEvents?.any { clientEvent ->
          val ret = clientEvent is ClientEvent.Look && clientEvent.cameraRotations.any {
            val timestamped = it.timestamped(replay.tickdex + i)
            val ret = timestamped.timestamp - now > 0f
            if (ret) {
              // hahahaheehah
              hahaha = timestamped
            }
            ret
          }
          ret
        } == true
      } ?: return null

    return hahaha
  }

  var wasOnHorse = false

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
          nextRotationAtTick = afterNextRotationsAtTick
        }
      }
    }

    nextRotationAtTick?.let {
      if (it.timestamp - this.nextRotation.timestamp > 0f && it.timestamp - now > 0f) {
        this.nextRotation = it
      }
    }

    var (yaw, pitch) = calculateRotation(this.nextRotation, this.prevRotation, now)
    if (this.nextRotation.timestamp - this.prevRotation.timestamp < 0) {
      yaw = this.nextRotation.yaw
      pitch = this.nextRotation.pitch
    }

    if (nextAbsoluteState?.ridingID != null && nextAbsoluteState?.ridingID != -1 && !wasOnHorse) {
      val ent = mc.world.getEntityByID(nextAbsoluteState!!.ridingID)
      yaw = nextRotationAtTick?.yaw ?: ent?.rotationYaw ?: 0f
      pitch = nextRotationAtTick?.pitch ?: ent?.rotationPitch ?: 0f
      this.nextRotation = TimestampedRotation(yaw, pitch, now)
    }
    wasOnHorse = nextAbsoluteState?.ridingID != -1


    if (lockPov) {
      mc.player?.let {
        it.prevRotationYaw = yaw
        it.prevRotationPitch = pitch
        it.rotationYaw = yaw
        it.rotationPitch = pitch
      }
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

    val theplace = Pair(
      deltaX * partialPartialTicks + prev.yaw,
      deltaY * partialPartialTicks + prev.pitch
    )
//    if (Keyboard.isKeyDown(Keyboard.KEY_B)) {
//      println("calculate rotation: \n\t$prev \n\t$next \n\t$theplace")
//    }

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
  var lastSystemTime = 0L

  var nextHeldItem = 0

  var nextYaw = 0f
  var nextPitch = 0f
  var nextAbsoluteState: ClientEvent.Absolutes? = null
}

private val killRegex =
  Regex("""((?:\[.*] )?(?:.* )?(?:.*)) was slain by ((?:\[.*] )?(?:.* )?(?:.*)) with .*.""")

private val maxBufferSize = 1024 * 1024 * 16
private val replayFileBuffer: ByteBuffer = ByteBuffer.allocate(maxBufferSize)
fun createMarkers(replay: File) {
  val name = replay.nameWithoutExtension
  val recFile = File(mod.recordingPath, "$name.rec")
  // clean it up now
  val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
  f.mkdirs()

  val uncompressedRecording = File(f, name)
  if (!uncompressedRecording.exists()) {
    f.listFiles()?.forEach { it.delete() }
    val exename = "\"${File(mod.sevenZipPath).absolutePath}\""

    val proc = Runtime.getRuntime()
      .exec(
        "$exename x \"${recFile.absolutePath}\"",
        arrayOf(),
        f
      )
    proc.waitFor()
  }
  val replay = File(f, name)

  val raFile = RandomAccessFile(replay, "r")
  val fileChannel = raFile.channel
  var startPosition = 0L
  var tickCount = 0

  fileChannels@ while (true) {
    replayFileBuffer.clear()
    val bittsts = fileChannel.read(replayFileBuffer, startPosition)
    replayFileBuffer.position(0)
    replayFileBuffer.limit(bittsts)
    val fileSize = replay.length()

    val buffer = PacketBuffer(Unpooled.wrappedBuffer(replayFileBuffer))

    while (true) {
      if (buffer.readableBytes() <= 0) {
        break@fileChannels
      } else if (fileSize - startPosition > maxBufferSize && buffer.readableBytes() <= 1024 * 1024 * 2 + 32) {
        // Check if we need to get another buffer
        startPosition += buffer.readerIndex()
        continue@fileChannels
      }

      val i = buffer.readVarInt()
      if (i < 0) {
        val clientEvent = ClientEvent.eventFromId(i)
        clientEvent.loadFromBuffer(buffer)

        if (clientEvent is ClientEvent.TickEnd) {
          tickCount++
          continue
        }
      } else {
        val size = buffer.readVarInt()
        val bytes = ByteArray(size)
        buffer.readBytes(bytes)

        // TODO - check packet
//          serverPackets.add()
        if (i == chatID) {
          val packItUpShipItSomewhereIdcWhereManAnywhere =
            RawServerPacket(i, size, bytes).cookPacket()
          packItUpShipItSomewhereIdcWhereManAnywhere as SPacketChat
          val msg = packItUpShipItSomewhereIdcWhereManAnywhere.chatComponent
          val txt = msg.unformattedText
          val unformatted = TextFormatting.getTextWithoutFormattingCodes(txt)!!
          if (txt.contains("dropped: SHOW")) {
            val t = msg.siblings
            t.filter {
              it.unformattedComponentText.equals("SHOW")
            }.forEach { component ->
              val hover = component.style.hoverEvent
              if (hover?.action == HoverEvent.Action.SHOW_ITEM) {
                val jsonThing =
                  Gson().fromJson(hover.value.unformattedComponentText, JsonObject::class.java)
                val mcName = jsonThing["id"]?.asString
                val tierColor =
                  jsonThing["tag"].asJsonObject["display"].asJsonObject["Name"].asString[3]

                // We don't want maps and dungeon fragments stay back
                if (mcName == "minecraft:iron_nugget" || mcName == "minecraft:filled_map") return@forEach

                val tier = when (tierColor) {
                  'e' -> "t5"
                  'd' -> "t4"
                  'b' -> "t3"
                  'a' -> "t2"
                  'f' -> "t1"
                  else -> "UNK"
                }
                val type = when (mcName) {
                  "minecraft:bow" -> "bow"
                  "minecraft:shield" -> "shield"
                  else -> mcName?.split("_")?.get(1) ?: "UNK"
                }
                val rarity =
                  jsonThing["tag"]
                    .asJsonObject["display"]
                    .asJsonObject["Lore"].asJsonArray
                    .findLast { it.asString[4] == '§' }
                    ?.asString?.substring(
                      6
                    ) ?: "UNK"

                val markerName = "drop_${tier}_${rarity.toLowerCase()}_${type}"
                makeMarker(replay, true, markerName, tickCount)
                println("Found drop: $markerName")
              }
            }
          } else if (killRegex.matches(unformatted)) {
            val groups = killRegex.find(unformatted)!!.groups
            val killed = groups[1]?.value
            val killer = groups[2]?.value
            val markerName = "$killer KILLED $killed"
            makeMarker(replay, false, markerName, tickCount)
            println("Death: $markerName")
          }
        }
      }
    }
  }
  raFile.close()
}

fun makeMarker(recordingFile: File, drop: Boolean, name: String, tickdex: Int) {
  val recordingName = recordingFile.nameWithoutExtension
  File(if (drop) "generated_markers/drops" else "generated_markers/pks").mkdirs()
  var s = name.replace("[\\\\/:*?\"<>|]".toRegex(), "_");
  while (File(
      if (drop) "generated_markers/drops" else "generated_markers/pks",
      "$s-$recordingName"
    ).exists()
  ) {
    s += "_"
  }

  File(
    if (drop) "generated_markers/drops" else "generated_markers/pks",
    "$s-$recordingName"
  ).writeText("$tickdex")
}

data class CameraRotationsAtTick(val cameraRotations: List<CameraRotation>, val tickdex: Int)

class Replay(val replayFile: File) {
  var tickdex = 0

  var totalTicks = 0
  var ticks: MutableList<ReplayTick?>? = null
  var loadedTickdex = 0
  private val tickPositions = mutableListOf<Long>()

  var netHandler = NetHandlerReplayClient(
    mc,
    null, // TODO - make this the replay screen !!!!!
    mc.session.profile
  )

  private fun InputStream.readVarInt(): Int {
    var i = 0
    var j = 0

    while (true) {
      val b0 = this.read()
      i = i or ((b0 and 127) shl j++ * 7)

      if (j > 5) {
        throw RuntimeException("VarInt too big")
      }

      if ((b0 and 128) != 128) {
        break
      }
    }

    return i
  }

  private val maxBufferSize = 1024 * 1024 * 16
  private val replayFileBuffer: ByteBuffer = ByteBuffer.allocate(maxBufferSize)

  fun keepLoading(tickdex: Int = this.tickdex) {
    if (this.ticks != null && tickdex - this.loadedTickdex < this.ticks!!.size - 10 && tickdex - this.loadedTickdex >= 0) return
    if (this.ticks == null) {
      this.ticks = MutableList(20 * 60 * 5) { null }
    }
    // move it up move it up now
    this.loadedTickdex = max(tickdex - 20, 0)

    var startPosition = this.tickPositions[this.loadedTickdex]
    val fileSize = replayFile.length()
    var tickCount = 0

    val raFile = RandomAccessFile(replayFile, "r")
    val fileChannel = raFile.channel

    val clientEvents = mutableListOf<ClientEvent>()
    val serverPackets = mutableListOf<RawServerPacket>()

    fileChannels@ while (true) {
      try {
        replayFileBuffer.clear()
        val bittsts = fileChannel.read(replayFileBuffer, startPosition)
        replayFileBuffer.position(0)
        replayFileBuffer.limit(bittsts)

        val buffer = PacketBuffer(Unpooled.wrappedBuffer(replayFileBuffer))

        while (true) {
          if (loadedTickdex + tickCount >= this.totalTicks) {
            break@fileChannels
          }

          // Check if we need to get another buffer
          if (fileSize - startPosition > maxBufferSize && buffer.readableBytes() <= 1024 * 1024 * 2 + 32) {
            startPosition += buffer.readerIndex()
            continue@fileChannels
          }

          val i = buffer.readVarInt()
          if (i < 0) {
            val clientEvent = ClientEvent.eventFromId(i)
            clientEvent.loadFromBuffer(buffer)

            if (clientEvent is ClientEvent.TickEnd) {
              if (loadedTickdex + tickCount >= this.totalTicks || tickCount >= this.ticks!!.size) {
                break@fileChannels
              }
              this.ticks!![tickCount] =
                ReplayTick(clientEvents.toList(), serverPackets.toList())
              clientEvents.clear()
              serverPackets.clear()
              tickCount++

              continue
            } else {
              clientEvents.add(clientEvent)
            }
          } else {
            val size = buffer.readVarInt()
            val bytes = ByteArray(size)
            buffer.readBytes(bytes)
            serverPackets.add(RawServerPacket(i, size, bytes))
          }
        }
      } catch (uhoh: Exception) {
        println("horrible error")
        uhoh.printStackTrace()
        exitProcess(-1)
      }
    }
    raFile.close()

//    println("Keep loading from ${tickdex}: ${loadTime / 1000000}ms")
  }

  init {
    val loadTime = measureNanoTime {
      var startPosition = 0L
      val fileSize = replayFile.length()
      var tickCount = 0

      val raFile = RandomAccessFile(replayFile, "r")
      val fileChannel = raFile.channel

      this.tickPositions.add(0)

      fileChannels@ while (true) {
        replayFileBuffer.clear()
        val bittsts = fileChannel.read(replayFileBuffer, startPosition)
        replayFileBuffer.position(0)
        replayFileBuffer.limit(bittsts)

        val buffer = PacketBuffer(Unpooled.wrappedBuffer(replayFileBuffer))

        while (true) {
          // Check if we've reached the end of our buffer
          // TODO - why is reader index 0 what is it doing when reading
          if (buffer.readableBytes() <= 0) {
            break@fileChannels
          }
          // Check if we need to get another buffer
          else if (fileSize - startPosition > maxBufferSize && buffer.readableBytes() <= 1024 * 1024 * 2 + 32) {
            startPosition += buffer.readerIndex()
            continue@fileChannels
          }

          val i = buffer.readVarInt()
          if (i < 0) {
            val clientEvent = ClientEvent.eventFromId(i)
            clientEvent.loadFromBuffer(buffer)

            if (clientEvent is ClientEvent.TickEnd) {
              val filePos = startPosition + buffer.readerIndex()
              this.tickPositions.add(filePos)
              tickCount++
              continue
            }
          } else {
            val size = buffer.readVarInt()
            buffer.skipBytes(size)
          }
        }
      }
      raFile.close()

      this.totalTicks = tickCount
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
//    var lastRespawnPacket: Pair<Int, Int>? = null

    // pre process
    val preprocesstime = measureNanoTime {
      for (i in 0 until targetTick - tickdex) {
        this.keepLoading(this.tickdex + i)
        val tick = this.ticks!![this.tickdex - loadedTickdex + i]!!
        tick.serverPackets.withIndex().forEach { (i2, rawPacket) ->
          // OH YEAH
          val packetProcessIndex = Pair(i, i2)

          if (rawPacket.packetID == soundID || rawPacket.packetID == customSoundID || rawPacket.packetID == effectID || rawPacket.packetID == entityEffectID) {
            ignoredPacketInfo.add(packetProcessIndex)
          }

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
          // TODO - see how much of this you can put back hahahah
          if (rawPacket.packetID == destroyEntityID) {
            val packet = rawPacket.cookPacket() as SPacketDestroyEntities
            packet.entityIDs.forEach { entID ->
              val spawnIndex = spawnedEntities[entID]
              if (spawnIndex != null) {
//                if (Keyboard.isKeyDown(Keyboard.KEY_P)) {
//                ignoredPacketInfo.add(spawnIndex)
//                }
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
              // ^^^ this breaks players and horses haha idk man help me
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
        this.keepLoading(this.tickdex + i)
        val tick = this.ticks!![this.tickdex - loadedTickdex + i]!!
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

//            mc.world.chunkProvider.loadChunk(chunkCoords.first.toInt(), chunkCoords.second.toInt())
//            println("Tried to spawn something: $isgen")
          }
        }
      }
    }

    println("Preprocess time: ${preprocesstime / 1000000}")

    val replayTime = measureNanoTime {
      for (i in 0 until targetTick - tickdex) {
        if (this.tickdex < this.totalTicks) {
          this.keepLoading()
          this.ticks!![this.tickdex - loadedTickdex]!!.replayFast(
            i,
            ignoredPacketInfo
          ) // TODO - ignorediososfji
          this.tickdex++
        }
      }
    }
    println("Replay time: ${replayTime / 1000000}")

    skipping = false
  }

  fun playNextTick() {
    if (this.tickdex < this.totalTicks) {
      this.keepLoading()
      this.ticks!![this.tickdex - loadedTickdex]?.replayFull()
      this.tickdex++
    }
  }

  fun restart() {
    ReplayState.resetRotations()
    val scaled = ScaledResolution(mc)
    ReplayState.scaledRes =
      Triple(scaled.scaledWidth_double, scaled.scaledHeight_double, scaled.scaleFactor)
    ReplayState.currentGuiState = null
    ReplayState.nextGuiState = null
    ReplayState.nextAbsoluteState = null
    ReplayState.nextGuiProcessState = null
    ReplayState.wasOnHorse = false
    ReplayState.nextKeybindingState = ClientEvent.trackedKeybinds.map {
      Pair(false, 0)
    }

    // TODO - store uuid and stuff and name in the replay file
    // TODO - when you do this, also store the creative tab (lmao)
    // TODO - when you do this, also store the creative tab (lmao)
    GuiContainerCreative.INVENTORY_BACKGROUND // remove if you want this to crash lmao
    val selected = GuiContainerCreativeAccessor.getSelectedTabIndex()
    GuiContainerCreativeAccessor.setSelectedTabIndex(0)
    // TODO - when you do this, also store the creative tab (lmao)
    // TODO - when you do this, also store the creative tab (lmao)

    this.tickdex = 0

    mc.ingameGUI.chatGUI.clearChatMessages(true)
//    mc.gameSettings.showDebugInfo = true
    mc.loadWorld(null)
    this.netHandler = NetHandlerReplayClient(
      mc,
      null,
      mc.session.profile
    )
  }

  fun skipBackwards(ticks: Int) {
    val targetTick = (this.tickdex - ticks).coerceAtLeast(0)
    println("target skip tick: $targetTick")
    skipTo(targetTick)
//    skipping = true
//    this.restart()
//    for (i in 0..20) {
//      mc.runTick()
//    }
//    playUntil(targetTick)
  }

  fun skipToRealSlow(targetTick: Int) {
    println("Skipping real slow to: $targetTick")
    skipping = true

    mc.displayGuiScreen(GuiDownloadTerrain())
    this.restart()

    for (i in 0..targetTick) {
      mc.runTick()
    }
  }

  fun skipTo(targetTick: Int) {
    println("Skipping to: $targetTick")
    skipping = true

//    mc.displayGuiScreen(GuiDownloadTerrain())
    this.restart()

    // Find last tickdex that contains a respawn packet
    if (targetTick > 20) {
      val fastTargetTick = (targetTick.coerceAtMost(this.totalTicks - 1) - 20).coerceAtLeast(0)
      var latestTickdex = 0
      val latestRespawnPacketAndOthers = mutableListOf<Pair<Int, Int>>()
      for (i in 0 until fastTargetTick) {
        this.keepLoading(i)
        val tick = this.ticks!![i - loadedTickdex]!!
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
      var joinPacket: Packet<NetHandlerReplayClient>? = null
      this.keepLoading()
      this.ticks!!.filterNotNull().forEach { tick ->
        tick.serverPackets.firstOrNull { it.packetID == joinGameID }?.let {
          joinPacket = it.cookPacket()
          return@forEach
        }
      }
      joinPacket?.processPacket(netHandler)

      this.keepLoading(latestTickdex)
      val firstTick = this.ticks!![latestTickdex - loadedTickdex]!!

      // Replay important packets that bungee like doesn't care about???
      for (i in 0 until latestTickdex) {
        this.keepLoading(i)
        val tick = this.ticks!![i - loadedTickdex]!!
        tick.serverPackets.filter {
          it.packetID == teamsID //- didn't need this i guess lol
            || it.packetID == updateScore
            || it.packetID == bossBarID
            || it.packetID == scoreboardObjective
            || it.packetID == displayObjective
            || it.packetID == playerListID
            || it.packetID == headerFooterID
            || it.packetID == respawnID
        }.forEach {
          if (it.packetID == respawnID) {
            val respawnPacket = it.cookPacket() as SPacketRespawn
            if (respawnPacket.dimensionID == mc.player.dimension) {
              mc.world.setWorldScoreboard(Scoreboard())
            }
            mc.player.dimension = respawnPacket.dimensionID
          } else {
            processPacket(it)
          }
        }

//        blipping = true
//        mc.runTick()
//        blipping = false
      }

      // Respawn + extra packets that tick
      latestRespawnPacketAndOthers.forEach { (i, i2) ->
        this.keepLoading(i)
        processPacket(this.ticks!![i - loadedTickdex]!!.serverPackets[i2])
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
    if (targetTick > 0) {
      for (i in 0..20.coerceAtMost(targetTick)) {
        mc.runTick()
      }
    }
    skipping = false
    paused = wasPaused
  }

  fun moveOneTick() {
    this.skipForward(1)
    val newPartialTicks = 0f
    (mc as MinecraftAccessor).timer.renderPartialTicks = newPartialTicks
    (mc as MinecraftAccessor).timer.elapsedPartialTicks = newPartialTicks
  }

  fun skipForward(ticks: Int) {
    val targetTick = (this.tickdex + ticks).coerceAtMost(this.totalTicks - 1)

    if (ticks > 20 * 30) {
      this.skipTo(targetTick)
    } else {
      this.playUntil(targetTick)
    }
    println("SKIPPED!!! TO TARGET TICK: $targetTick")
  }

  fun reachedEnd() = this.tickdex >= this.totalTicks
}
