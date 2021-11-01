package me.aris.recordingmod

import io.netty.buffer.ByteBuf
import me.aris.recordingmod.PacketIDsLol.spawnEXPOrbID
import me.aris.recordingmod.PacketIDsLol.spawnGlobalID
import me.aris.recordingmod.PacketIDsLol.spawnMobID
import me.aris.recordingmod.PacketIDsLol.spawnObjectID
import me.aris.recordingmod.PacketIDsLol.spawnPaintingID
import me.aris.recordingmod.PacketIDsLol.spawnPlayerID
import me.aris.recordingmod.mixins.*
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.entity.passive.AbstractHorse
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.*
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.lwjgl.input.Keyboard

class SavePoint(
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
)

class RawServerPacket(val packetID: Int, val size: Int, val buffer: ByteBuf) {
  fun cookPacket(): Packet<NetHandlerReplayClient> {
    @Suppress("UNCHECKED_CAST")
    val packet: Packet<NetHandlerReplayClient> =
      EnumConnectionState.PLAY.getPacket(
        EnumPacketDirection.CLIENTBOUND,
        this.packetID
      ) as Packet<NetHandlerReplayClient>

    packet.readPacketData(PacketBuffer(this.buffer.duplicate()))
    return packet
  }
}

class ReplayTick(
  val clientEvents: List<ClientEvent>,
  val serverPackets: List<RawServerPacket>
) {
  fun replayFull() {
    ReplayState.nextAbsoluteState?.runitbaby(false)

    serverPackets.forEach { processPacket(it) }

    clientEvents.forEach { event ->
      event.processEvent(ReplayState)
    }
  }

  fun replayFast(ourIndex: Int, ignorePackets: HashSet<Pair<Int, Int>>) {
    ReplayState.nextAbsoluteState?.runitbaby(true)

    fun isChunkLoaded(x: Double, z: Double) =
      !mc.world.getChunk(MathHelper.floor(x / 16.0), MathHelper.floor(z / 16.0)).isEmpty

    serverPackets.withIndex()
      .filterNot { Pair(ourIndex, it.index) in ignorePackets }
      .forEach { (i, rawPacket) ->
        val fixedUpPacketSoHorsesArentBad = when (rawPacket.packetID) {
          spawnPlayerID -> (rawPacket.cookPacket() as SPacketSpawnPlayer).apply {
            if (!isChunkLoaded(this.x, this.z)) {
              (this as SPacketSpawnPlayerAccessor).setX(mc.player.posX)
              (this as SPacketSpawnPlayerAccessor).setY(-500.0)
              (this as SPacketSpawnPlayerAccessor).setZ(mc.player.posZ)
            }
          }
          spawnMobID -> (rawPacket.cookPacket() as SPacketSpawnMob).apply {
            if (this.entityType == 100) {
              println("FUCKIN HORSE YEAYYAAYAYA: ${this.x}, ${this.z}, (${mc.player.posX}, ${mc.player.posZ}")
            }
            if (!isChunkLoaded(this.x, this.z)) {
              println("TRIED SPAWNING BAD MOB in chunk!!! at xx, chunkY")
              (this as SPacketSpawnMobAccessor).setX(mc.player.posX)
              (this as SPacketSpawnMobAccessor).setY(-500.0)
              (this as SPacketSpawnMobAccessor).setZ(mc.player.posZ)
            }
          }
          spawnEXPOrbID -> (rawPacket.cookPacket() as SPacketSpawnExperienceOrb).apply {}
          spawnPaintingID -> (rawPacket.cookPacket() as SPacketSpawnPainting).apply {}
          spawnObjectID -> (rawPacket.cookPacket() as SPacketSpawnObject).apply {}
          spawnGlobalID -> (rawPacket.cookPacket() as SPacketSpawnGlobalEntity).apply {}

          else -> null
        }

        if (fixedUpPacketSoHorsesArentBad != null) {
          fixedUpPacketSoHorsesArentBad.processPacket(activeReplay!!.netHandler)
        } else {
          processPacket(rawPacket)
        }
//        if (id != null) {
//          val spawnedEntity = mc.world.getEntityByID(id)!!

//          if (spawnedEntity is EntityHorse) {
//            println("TRIED SPAWNING $id in chunk!!! at $chunkX, $chunkY")
//          }
//          if (mc.world.getChunk(chunkX, chunkY) is EmptyChunk) {
//            println("TRIED SPAWNING $id in empty chunk at $chunkX, $chunkY")
//          }
//        }
      }

    clientEvents.forEach { event ->
//      ReplayState.nextPositionInfo?.runit()
      event.processEvent(ReplayState)
    }

    // Keybindment
    val mc = mc as MinecraftAccessor
    if (mc.leftClickCounter > 0) {
      --mc.leftClickCounter;
    }
    if (mc.rightClickDelayTimer > 0) {
      --mc.rightClickDelayTimer;
    }
    mc.invokeProcessKeyBinds()
  }
}

private inline fun processPacket(rawPacket: RawServerPacket) {
//  LittleTestPerformanceTrackerThing.timePacket(rawPacket.cookPacket())
  // TODO - don't time packet, use below
  rawPacket.cookPacket().processPacket(activeReplay!!.netHandler)
}


sealed class ClientEvent {
  companion object {
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
      mc.gameSettings.keyBindTogglePerspective, // TODO - remove later
      mc.gameSettings.keyBindPlayerList,
      mc.gameSettings.keyBindPickBlock,
      mc.gameSettings.keyBindInventory,
      mc.gameSettings.keyBindChat,
      mc.gameSettings.keyBindCommand,
      mc.gameSettings.keyBindAdvancements,
      mc.gameSettings.keyBindLoadToolbar,
      mc.gameSettings.keyBindSaveToolbar
    )
    // TODO - ESCAPE KEY

    @JvmStatic
    fun writeClientEvent(event: ClientEvent) {
      Recorder.writeLaterLock.lock()
      val buffer = PacketBuffer(Recorder.toWritelater)
      buffer.writeVarInt(
        when (event) {
          TickEnd -> -1
          CloseScreen -> -2
          is SetKeybinds -> -3
          is HeldItem -> -4
          is Absolutes -> -5
          is Look -> -6
          is SavePoint -> -7
        }
      )
      event.writeToBuffer(buffer)
      Recorder.writeLaterLock.unlock()
    }

    fun eventFromId(id: Int) = when (id) {
      -1 -> TickEnd
      -2 -> CloseScreen
      -3 -> SetKeybinds()
      -4 -> HeldItem()
      -5 -> Absolutes()
      -6 -> Look()
      -7 -> SavePoint()

      else -> TODO("Idk handle fucked up data or something")
    }
  }

  open fun processEvent(replayState: ReplayState) = Unit
  open fun loadFromBuffer(buffer: PacketBuffer) = Unit
  protected open fun writeToBuffer(buffer: PacketBuffer) = Unit

  object TickEnd : ClientEvent()

  object CloseScreen : ClientEvent() {
    override fun processEvent(replayState: ReplayState) {
      if (mc.currentScreen is GuiContainer) {
        mc.player.closeScreen()
      } else {
        mc.displayGuiScreen(null)
      }
    }
  }

  class SetKeybinds : ClientEvent() {
    private lateinit var keybindState: List<Pair<Boolean, Int>>

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.keybindState = trackedKeybinds.map {
        Pair(buffer.readBoolean(), buffer.readVarInt())
      }
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      trackedKeybinds.forEach {
        it as KeyBindingAccessor
        buffer.writeBoolean(it.isKeyDown)
        buffer.writeVarInt(it.pressTime)
      }
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextKeybindingState = this.keybindState
    }
  }

  class HeldItem : ClientEvent() {
    private var heldItem = 0

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.heldItem = buffer.readVarInt()
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      buffer.writeVarInt(mc.player.inventory.currentItem)
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextHeldItem = this.heldItem
    }
  }

  class Absolutes : ClientEvent() {
    private var creativeFlying = false
    private var thirdPersonView = 0
    private var sprintTicksLeft = 0
    private var isSprinting = false
    private var itemInUseCount = 0

    private var ridingID = 0
    private var ridingYaw = 0f
    private var ridingPitch = 0f
    private var ridingRearing = false

    private lateinit var position: Vec3d
    private lateinit var motion: Vec3d

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.creativeFlying = buffer.readBoolean()
      this.thirdPersonView = buffer.readVarInt()
      this.sprintTicksLeft = buffer.readVarInt()
      this.isSprinting = buffer.readBoolean()
      this.itemInUseCount = buffer.readVarInt()

      this.ridingID = buffer.readVarInt()
      if (this.ridingID != -1) {
        this.ridingYaw = buffer.readFloat()
        this.ridingPitch = buffer.readFloat()
        this.ridingRearing = buffer.readBoolean()
      }
      this.position = Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
      this.motion = Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      buffer.writeBoolean(mc.player.capabilities.isFlying)
      buffer.writeVarInt(mc.gameSettings.thirdPersonView)
      buffer.writeVarInt(mc.player.sprintingTicksLeft)
      buffer.writeBoolean(mc.player.isSprinting)
      buffer.writeVarInt(mc.player.itemInUseCount)

      val riding = mc.player?.ridingEntity
      buffer.writeVarInt(riding?.entityId ?: -1)

      if (riding != null) {
        buffer.writeFloat(riding.rotationYaw)
        buffer.writeFloat(riding.rotationPitch)
        if (riding is AbstractHorse) {
          buffer.writeBoolean(riding.isRearing)
        }
      }

      buffer.writeDouble(riding?.posX ?: mc.player.posX)
      buffer.writeDouble(riding?.posY ?: mc.player.posY)
      buffer.writeDouble(riding?.posZ ?: mc.player.posZ)
      buffer.writeDouble(riding?.motionX ?: mc.player.motionX)
      buffer.writeDouble(riding?.motionY ?: mc.player.motionY)
      buffer.writeDouble(riding?.motionZ ?: mc.player.motionZ)
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextAbsoluteState = this
    }

    fun runitbaby(isFastReplay: Boolean) {
      val riding = if (this.ridingID == -1) null else mc.world?.getEntityByID(this.ridingID)

      if (riding != null && !isFastReplay) {
        mc.player.startRiding(riding, true)
      } else {
        mc.player.dismountRidingEntity()
      }

      if (isFastReplay) {
        mc.player.setPositionAndUpdate(this.position.x, this.position.y, this.position.z)
        riding?.setPositionAndUpdate(this.position.x, this.position.y, this.position.z)
      }

      // REAR MOMENT
      if (riding is AbstractHorse) {
        riding.isRearing = this.ridingRearing
      }

      // mmm
      mc.player.capabilities.isFlying = this.creativeFlying
      mc.gameSettings.thirdPersonView = this.thirdPersonView
      mc.gameSettings.thirdPersonView = this.thirdPersonView
      mc.player.isSprinting = this.isSprinting
      mc.player.sprintingTicksLeft = this.sprintTicksLeft
      (mc.player as EntityLivingBaseAccessor).setActiveItemStackUseCount(this.itemInUseCount)

      val setPosOfThisThing = riding ?: mc.player
      if (Keyboard.isKeyDown(Keyboard.KEY_M)) {
        setPosOfThisThing.setPosition(this.position.x, this.position.y, this.position.z)
        setPosOfThisThing.motionX = this.motion.x
        setPosOfThisThing.motionY = this.motion.y
        setPosOfThisThing.motionZ = this.motion.z
      }

      if (riding != null) {
        riding.rotationYaw = this.ridingYaw
        riding.rotationPitch = this.ridingPitch
      }
    }
  }

  class Look : ClientEvent() {
    private var yaw = 0f
    private var pitch = 0f

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.yaw = buffer.readFloat()
      this.pitch = buffer.readFloat()
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      buffer.writeFloat(mc.player.rotationYaw)
      buffer.writeFloat(mc.player.rotationPitch)
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextYaw = this.yaw
      replayState.nextPitch = this.pitch
    }
  }

  class SavePoint : ClientEvent() {
    private lateinit var restorePoint: me.aris.recordingmod.SavePoint

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.restorePoint = SavePoint(
        buffer.readDouble(),
        buffer.readDouble(),
        buffer.readDouble(),
        buffer.readDouble(),
        buffer.readDouble(),
        buffer.readDouble(),
        buffer.readBoolean(),
        buffer.readVarInt(),
        buffer.readVarInt(),
        buffer.readBoolean(),
        buffer.readVarInt(),
        buffer.readLong()
      )
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      // TODO - remove some unnecessary stuff here (after we test existing 15min file)
      val savedEnt = mc.player.ridingEntity ?: mc.player

      buffer.writeDouble(savedEnt.posX)
      buffer.writeDouble(savedEnt.posY)
      buffer.writeDouble(savedEnt.posZ)

      buffer.writeDouble(savedEnt.motionX)
      buffer.writeDouble(savedEnt.motionY)
      buffer.writeDouble(savedEnt.motionZ)

      buffer.writeBoolean(mc.player.capabilities.isFlying)
      buffer.writeVarInt(mc.gameSettings.thirdPersonView)
      buffer.writeVarInt(mc.player.sprintingTicksLeft)
      buffer.writeBoolean(mc.player.isSprinting)

      buffer.writeVarInt(mc.player.ridingEntity?.entityId ?: -1)

      buffer.writeLong(Recorder.tickdex)
    }

    override fun processEvent(replayState: ReplayState) {
      // TODO - yeah remove everything but this basically
      mc.player?.motionX = this.restorePoint.motionX
      mc.player?.motionY = this.restorePoint.motionY
      mc.player?.motionZ = this.restorePoint.motionZ
      mc.player?.capabilities?.isFlying = this.restorePoint.isFlying
      mc.gameSettings?.thirdPersonView = this.restorePoint.perspective
      mc.player?.sprintingTicksLeft = this.restorePoint.sprintTicksLeft
      mc.player?.isSprinting = this.restorePoint.sprinting
    }
  }
}
