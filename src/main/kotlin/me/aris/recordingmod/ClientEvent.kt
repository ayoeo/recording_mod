package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL.*
import io.netty.buffer.ByteBuf
import me.aris.recordingmod.PacketIDsLol.spawnMobID
import me.aris.recordingmod.PacketIDsLol.spawnObjectID
import me.aris.recordingmod.PacketIDsLol.spawnPlayerID
import me.aris.recordingmod.mixins.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.entity.passive.AbstractHorse
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import net.minecraft.network.play.server.SPacketSpawnMob
import net.minecraft.network.play.server.SPacketSpawnObject
import net.minecraft.network.play.server.SPacketSpawnPlayer
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.nio.ByteBuffer

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

    clientEvents.forEach { event ->
      event.processEvent(ReplayState)
    }

    serverPackets.forEach { processPacket(it) }
  }

  fun replayFast(ourIndex: Int, ignorePackets: HashSet<Pair<Int, Int>>) {
    // idc idk idc whatever whatever whatever it's fine this is fine not indicative of a more severe problem idc
//    mc.player?.world = mc.world

    ReplayState.nextAbsoluteState?.runitbaby(true)

    fun isChunkLoaded(x: Double, z: Double) =
      !mc.world.getChunk(MathHelper.floor(x / 16.0), MathHelper.floor(z / 16.0)).isEmpty

    clientEvents.forEach { event ->
      event.processEvent(ReplayState)
    }

    var spawnedPlayer = false
    serverPackets.withIndex()
      .filterNot { Pair(ourIndex, it.index) in ignorePackets }
      .forEach { (i, rawPacket) ->
        val fixedUpPacketSoHorsesArentBad = when (rawPacket.packetID) {
          spawnPlayerID -> (rawPacket.cookPacket() as SPacketSpawnPlayer).apply {
            spawnedPlayer = true
            println("spawn player?: $x, $z, $uniqueId")
            if (!isChunkLoaded(this.x, this.z)) {
              println("WAIT Player not spawned?: $x, $z, $uniqueId")
              (this as SPacketSpawnPlayerAccessor).setX(mc.player.posX)
              (this as SPacketSpawnPlayerAccessor).setY(-500.0)
              (this as SPacketSpawnPlayerAccessor).setZ(mc.player.posZ)
            }
          }
          spawnMobID -> (rawPacket.cookPacket() as SPacketSpawnMob).apply {
            if (!isChunkLoaded(this.x, this.z)) {
              (this as SPacketSpawnMobAccessor).setX(mc.player.posX)
              (this as SPacketSpawnMobAccessor).setY(-500.0)
              (this as SPacketSpawnMobAccessor).setZ(mc.player.posZ)
            }
          }
          spawnObjectID -> (rawPacket.cookPacket() as SPacketSpawnObject).apply {
            if (!isChunkLoaded(this.x, this.z)) {
              (this as SPacketSpawnObjectAccessor).setX(mc.player.posX)
              (this as SPacketSpawnObjectAccessor).setY(-500.0)
              (this as SPacketSpawnObjectAccessor).setZ(mc.player.posZ)
            }
          }
          else -> null
        }

        if (fixedUpPacketSoHorsesArentBad != null) {
          fixedUpPacketSoHorsesArentBad.processPacket(activeReplay!!.netHandler)
        } else {
          processPacket(rawPacket)
        }
      }

    // haha forgot this haha
    mc.player?.rotationYaw = ReplayState.nextYaw
    mc.player?.rotationPitch = ReplayState.nextPitch

    mc.entityRenderer.getMouseOver(1.0F)

    if (spawnedPlayer) {
//      mc.runTick()
//      println("run it baby")
    } else {
      // Keybindment
      val mc = mc as MinecraftAccessor
      if (mc.leftClickCounter > 0) {
        --mc.leftClickCounter
      }
      if (mc.rightClickDelayTimer > 0) {
        --mc.rightClickDelayTimer
      }
      mc.invokeProcessKeyBinds()
    }

    // TODO - something player spawn something?
    // TODO - chunk loading maybe, like it's not done yet? the chunk needs to be loaded first???
    // TODO - chunk loading maybe, like it's not done yet? the chunk needs to be loaded first???
    // TODO - chunk loading maybe, like it's not done yet? the chunk needs to be loaded first???
    // TODO - chunk loading maybe, like it's not done yet? the chunk needs to be loaded first???
  }
}

private inline fun processPacket(rawPacket: RawServerPacket) {
//  LittleTestPerformanceTrackerThing.timePacket(rawPacket.cookPacket())
  // TODO - don't time packet, use below
  try {
    rawPacket.cookPacket().processPacket(activeReplay!!.netHandler)
  } catch (ignored: Exception) {
    println("Failed processing packet: ${rawPacket.packetID}")
//    ignored.printStackTrace()
  }
}

sealed class ClientEvent {
  companion object {
    private var lastPos = RenderedPosition(1f, MousePosition(0f, 0f))

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
    // TODO - ESCAPE KEY
    // TODO - ESCAPE KEY
    // TODO - ESCAPE KEY
    // TODO - ESCAPE KEY
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
          is GuiState -> -7
          is Resize -> -8
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
      -7 -> GuiState()
      -8 -> Resize

      else -> TODO("Idk handle fucked up data or something")
    }
  }

  open fun processEvent(replayState: ReplayState) = Unit
  open fun loadFromBuffer(buffer: PacketBuffer) = Unit
  protected open fun writeToBuffer(buffer: PacketBuffer) = Unit

  object TickEnd : ClientEvent()

  object CloseScreen : ClientEvent() {
    override fun processEvent(replayState: ReplayState) {
      mc.displayGuiScreen(null)
    }
  }

  class GuiState : ClientEvent() {
    companion object {
      fun setKeysDown() {
        val keysDown = Recorder.keysDown
        keysDown[0] = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
        keysDown[1] = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
        keysDown[2] = Keyboard.isKeyDown(Keyboard.KEY_LMETA)
        keysDown[3] = Keyboard.isKeyDown(Keyboard.KEY_RMETA)
        keysDown[4] = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
        keysDown[5] = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
        keysDown[6] = Keyboard.isKeyDown(Keyboard.KEY_LMENU)
        keysDown[7] = Keyboard.isKeyDown(Keyboard.KEY_RMENU)
      }
    }

    private val mousePositions = mutableListOf<RenderedPosition>()
    private val guiInputEvents = mutableListOf<GuiInputEventWithMoreStuff>()
    private val keysDown = BooleanArray(8)
    private var systemTime = 0L

    override fun loadFromBuffer(buffer: PacketBuffer) {
      val positionsCount = buffer.readVarInt()
      for (i in 0 until positionsCount) {
        val pos = RenderedPosition(
          buffer.readFloat(),
          MousePosition(buffer.readFloat(), buffer.readFloat())
        )
        this.mousePositions.add(pos)
      }

      val guiEventsCount = buffer.readVarInt()
      for (i in 0 until guiEventsCount) {
        this.guiInputEvents.add(
          GuiInputEventWithMoreStuff(
            GuiInputEvent.readEvent(buffer.readVarInt(), buffer),
            buffer.readLong(),
            buffer.readFloat()
          )
        )
      }

      for (i in keysDown.indices) {
        keysDown[i] = buffer.readBoolean()
      }

      this.systemTime = buffer.readLong()
    }

    // Runs at the start of each tick while a gui is open
    override fun writeToBuffer(buffer: PacketBuffer) {
//      if (mc.currentScreen != null) {
      // Store last tick's mouse movements
      buffer.writeVarInt(Recorder.cursorPositions.size)
      Recorder.cursorPositions.forEach {
        buffer.writeFloat(it.partialTicks)
        buffer.writeFloat(it.position.x)
        buffer.writeFloat(it.position.y)
      }

      buffer.writeVarInt(Recorder.guiInputEvents.size)
      Recorder.guiInputEvents.forEach { (event, systemTime, partialTicks) ->
        event.writeEvent(buffer)
        buffer.writeLong(systemTime)
        buffer.writeFloat(partialTicks)
      }

      Recorder.keysDown.forEach { element ->
        buffer.writeBoolean(element)
      }
      buffer.writeLong(Minecraft.getSystemTime())
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!
      // TODO - Store clipboard info???!!!!?!?!

      Recorder.cursorPositions.clear()
      Recorder.guiInputEvents.clear()
//      }
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextGuiProcessState = this
      replayState.currentGuiState = this

      // I actually don't care shut up this is fine
      activeReplay?.let { replay ->
        replayState.nextGuiState =
          replay.ticks.getOrNull(replay.tickdex + 1)?.clientEvents?.firstOrNull { it is GuiState } as GuiState?
        replayState.nextGuiStateButLikeAfterThisOneGodHelpUsAll =
          replay.ticks.getOrNull(replay.tickdex + 2)?.clientEvents?.firstOrNull { it is GuiState } as GuiState?
      }
    }

    private val keydownBuffer = Keyboard::class.java.getDeclaredField("keyDownBuffer")
      .apply { this.isAccessible = true }

    fun executeEvents() {
      val keydownBuffer = keydownBuffer.get(null) as ByteBuffer
      keydownBuffer.put(Keyboard.KEY_LSHIFT, if (keysDown[0]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_RSHIFT, if (keysDown[1]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_LMETA, if (keysDown[2]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_RMETA, if (keysDown[3]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_LCONTROL, if (keysDown[4]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_RCONTROL, if (keysDown[5]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_LMENU, if (keysDown[6]) 1 else 0)
      keydownBuffer.put(Keyboard.KEY_RMENU, if (keysDown[7]) 1 else 0)

      guiInputEvents.forEach { (event, time, partialTicks) ->
        ReplayState.systemTime = time

        if (skipping) {
          val pos = this.getLameMousePos(partialTicks)
          val (x, y) = pos.getScaledXY()
          Mouse.setGrabbed(true)
          Mouse.setCursorPosition(x, y)
          val scaledresolution = ScaledResolution(mc);
          val i1 = scaledresolution.scaledWidth;
          val j1 = scaledresolution.scaledHeight;
          val xlmao = Mouse.getX() * i1 / mc.displayWidth;
          val ylmao = j1 - Mouse.getY() * j1 / mc.displayHeight - 1;
          val currentScreen = mc.currentScreen
          if (currentScreen is GuiContainer) {
            currentScreen.inventorySlots.inventorySlots.forEach { slot ->
              currentScreen as GuiContainerAccessor
              if (currentScreen.invokeIsMouseOverSlot(slot, xlmao, ylmao) && slot.isEnabled) {
                currentScreen.setHoveredSlot(slot)
              }
            }
          }
        }

        if (mc.currentScreen != null) event.process()
        ReplayState.systemTime = null
      }
    }

    fun getLameMousePos(partialTicks: Float): MousePosition {
      val (prev, cur) = this.getPositionsAround(partialTicks)
      return cur.calculatePosition(prev, partialTicks)
    }

    private val cursor = ResourceLocation("recordingmod", "textures/cursor.png")
    private fun glDrawTexturedRect(
      x: Double,
      y: Double,
      width: Double,
      height: Double,
      u: Double,
      v: Double,
      u2: Double,
      v2: Double
    ) {
      glDisableDepthTest()
      glDisableLighting();
      glDisableBlend();
      glAlphaFunc(GL_GREATER, 0.01F);
      glEnableTexture2D();
      glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.enableBlend()
      GlStateManager.tryBlendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ZERO
      )
      GlStateManager.blendFunc(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
      )

      val texMapScale = 0.001953125F

      val tessellator = Tessellator.getInstance()
      val worldRenderer = tessellator.buffer
      worldRenderer.begin(GL_QUADS, VF_POSITION_TEX)
      worldRenderer.pos(x + 0, y + height, 0.0).tex(u * texMapScale, v2 * texMapScale).endVertex()
      worldRenderer.pos(x + width, y + height, 0.0).tex(u2 * texMapScale, v2 * texMapScale)
        .endVertex()
      worldRenderer.pos(x + width, y + 0, 0.0).tex(u2 * texMapScale, v * texMapScale).endVertex()
      worldRenderer.pos(x + 0, y + 0, 0.0).tex(u * texMapScale, v * texMapScale).endVertex()
      tessellator.draw()
    }

    fun drawMouseCursor(partialTicks: Float) {
      val scaledRes = ScaledResolution(mc)

      val (prev, cur) = this.getPositionsAround(partialTicks)
      val mousePos = cur.calculatePosition(prev, partialTicks)
      val x = mousePos.x * scaledRes.scaledWidth_double
      val y = scaledRes.scaledHeight - (mousePos.y * scaledRes.scaledHeight_double)

      // Drawment
      mc.textureManager.bindTexture(cursor)
      glDrawTexturedRect(x, y, 12.0, 12.0, 0.0, 0.0, 48.0, 48.0)
    }

    private fun getPositionsAround(partialTicks: Float): Pair<RenderedPosition, RenderedPosition> {
      var prevPos = this.mousePositions.firstOrNull()
      var latestPos: RenderedPosition? = null

      for (i in 0 until this.mousePositions.size) {
        val curPos = this.mousePositions[i]

        if (curPos.partialTicks >= partialTicks) {
          latestPos = curPos

          prevPos = if (i == 0) {
            val prev = ReplayState.currentGuiState?.mousePositions?.asReversed()?.firstOrNull()
            prev?.copy(partialTicks = prev.partialTicks - 1f) ?: prevPos
          } else {
            this.mousePositions[i - 1]
          }
          break
        }
      }

      // Oooh we're at the end aren't we
      if (latestPos == null) {
        val nextFirstPos =
          ReplayState.nextGuiStateButLikeAfterThisOneGodHelpUsAll?.mousePositions?.firstOrNull()
        latestPos = nextFirstPos?.copy(partialTicks = nextFirstPos.partialTicks + 1f)
        val curLastPos = this.mousePositions.lastOrNull()
        prevPos = curLastPos//?.copy(partialTicks = curLastPos.partialTicks - 1f)
      }

      latestPos?.let { lastPos = it }

      return Pair(prevPos ?: lastPos, latestPos ?: lastPos)
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
    private var resolution = Triple(0.0, 0.0, 1)

    var systemTime = 0L
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
      this.resolution = Triple(buffer.readDouble(), buffer.readDouble(), buffer.readVarInt())

      this.systemTime = buffer.readLong()
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
      buffer.writeDouble(Recorder.scaledRes.first)
      buffer.writeDouble(Recorder.scaledRes.second)
      buffer.writeVarInt(Recorder.scaledRes.third)

      buffer.writeLong(Minecraft.getSystemTime())
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
//      if (replayState.scaledRes != this.resolution) {
//        
//      }
      replayState.scaledRes = this.resolution
    }

    fun runitbaby(isFastReplay: Boolean) {
      if (mc.player == null) return
      ReplayState.lastSystemTime = this.systemTime

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
      setPosOfThisThing.setPosition(this.position.x, this.position.y, this.position.z)
      setPosOfThisThing.motionX = this.motion.x
      setPosOfThisThing.motionY = this.motion.y
      setPosOfThisThing.motionZ = this.motion.z

      if (riding != null) {
        riding.rotationYaw = this.ridingYaw
        riding.rotationPitch = this.ridingPitch
      }
    }
  }

  class Look : ClientEvent() {
    private var yaw = 0f
    private var pitch = 0f

    // TODO - write + read these :  )))
    //  and store prev + cur + next look ticks hahaha
    var cameraRotations = mutableListOf<CameraRotation>()

    override fun loadFromBuffer(buffer: PacketBuffer) {
      this.yaw = buffer.readFloat()
      this.pitch = buffer.readFloat()

      val rotationsCount = buffer.readVarInt()
      for (i in 0 until rotationsCount) {
        val pos = CameraRotation(
          buffer.readFloat(),
          buffer.readFloat(),
          buffer.readFloat()
        )
        this.cameraRotations.add(pos)
      }
    }

    override fun writeToBuffer(buffer: PacketBuffer) {
      buffer.writeFloat(mc.player.rotationYaw)
      buffer.writeFloat(mc.player.rotationPitch)

      buffer.writeVarInt(Recorder.rotations.size)
      Recorder.rotations.forEach {
        buffer.writeFloat(it.yaw)
        buffer.writeFloat(it.pitch)
        buffer.writeFloat(it.partialTicks)
      }

      Recorder.rotations.clear()
    }

    override fun processEvent(replayState: ReplayState) {
      replayState.nextYaw = this.yaw
      replayState.nextPitch = this.pitch

//      activeReplay?.let { replay ->
//        replayState.cameraRotations = ReplayState.CameraRotationsAround(
//          null,//CameraRotationsAtTick(this.cameraRotations, activeReplay!!.tickdex),
//        )
//      }
    }
  }

  object Resize : ClientEvent() {
    override fun processEvent(replayState: ReplayState) {
      println("resizing hahahahahhahahahaahhahahhhahahahhahhaahahahhahhahhah")
      (mc as MinecraftAccessor).invokeResize(mc.displayWidth, mc.displayHeight)
    }
  }
}
