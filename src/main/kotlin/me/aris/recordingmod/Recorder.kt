package me.aris.recordingmod

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.aris.recordingmod.mixins.GuiScreenAccessor
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.network.PacketBuffer
import org.lwjgl.input.Mouse
import sun.awt.Mutex
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class MousePosition(val x: Float, val y: Float) {
  fun getScaledXY() =
    Pair((x * mc.displayWidth.toFloat()).toInt(), (y * mc.displayHeight.toFloat()).toInt())
}

data class GuiInputEventWithMoreStuff(
  val event: GuiInputEvent,
  val time: Long,
  val partialTicks: Float
)

data class CameraRotation(
  val yaw: Float,
  val pitch: Float,
  val partialTicks: Float
) {
  fun timestamped(tickdex: Int) = TimestampedRotation(yaw, pitch, Timestamp(tickdex, partialTicks))

}

sealed class GuiInputEvent {
  companion object {
    fun readEvent(id: Int, buffer: PacketBuffer) = when (id) {
      -1 -> KeyTypedEvent(buffer.readChar(), buffer.readVarInt())
      -2 -> MouseClickedEvent(
        buffer.readVarInt(),
        buffer.readVarInt(),
        buffer.readVarInt()
      )
      -3 -> MouseReleasedEvent(
        buffer.readVarInt(),
        buffer.readVarInt(),
        buffer.readVarInt()
      )
      -4 -> MouseClickMoveEvent(
        buffer.readVarInt(),
        buffer.readVarInt(),
        buffer.readVarInt(),
        buffer.readLong()
      )
      -5 -> ScrollEvent(buffer.readVarInt())

      else -> TODO("Idk handle fucked up data or something")
    }
  }

  abstract fun writeEvent(buffer: PacketBuffer)
  abstract fun process()

  data class KeyTypedEvent(
    val typedChar: Char,
    val keyCode: Int
  ) : GuiInputEvent() {
    override fun writeEvent(buffer: PacketBuffer) {
      buffer.writeVarInt(-1)
      buffer.writeChar(this.typedChar.toInt())
      buffer.writeVarInt(this.keyCode)
    }

    override fun process() {
      println("Keypress: $keyCode") // TODO - force keybinds to be the same as they wereeeeee
      // TODO - test this by changing inventory keybind
      (mc.currentScreen as GuiScreenAccessor?)?.invokeKeyTyped(typedChar, keyCode)
    }
  }

  data class MouseClickedEvent(
    val mouseX: Int,
    val mouseY: Int,
    val mouseButton: Int
  ) : GuiInputEvent() {
    override fun writeEvent(buffer: PacketBuffer) {
      buffer.writeVarInt(-2)
      buffer.writeVarInt(this.mouseX)
      buffer.writeVarInt(this.mouseY)
      buffer.writeVarInt(this.mouseButton)
    }

    override fun process() {
      val screen = mc.currentScreen as GuiScreenAccessor?
      if (screen != null) {
        screen.setEventButton(this.mouseButton)
        (screen as GuiScreenAccessor?)?.invokeMouseClicked(
          this.mouseX,
          this.mouseY,
          this.mouseButton
        )
      }
    }
  }

  data class MouseReleasedEvent(
    val mouseX: Int,
    val mouseY: Int,
    val state: Int
  ) : GuiInputEvent() {
    override fun writeEvent(buffer: PacketBuffer) {
      buffer.writeVarInt(-3)
      buffer.writeVarInt(this.mouseX)
      buffer.writeVarInt(this.mouseY)
      buffer.writeVarInt(this.state)
    }

    override fun process() {
      val screen = mc.currentScreen as GuiScreenAccessor?
      if (screen != null) {
        screen.setEventButton(-1)
        screen.invokeMouseReleased(
          this.mouseX,
          this.mouseY,
          this.state
        )
      }
    }
  }

  data class MouseClickMoveEvent(
    val mouseX: Int,
    val mouseY: Int,
    val clickedMouseButton: Int,
    val timeSinceLastClick: Long
  ) : GuiInputEvent() {
    override fun writeEvent(buffer: PacketBuffer) {
      buffer.writeVarInt(-4)
      buffer.writeVarInt(this.mouseX)
      buffer.writeVarInt(this.mouseY)
      buffer.writeVarInt(this.clickedMouseButton)
      buffer.writeLong(this.timeSinceLastClick)
    }

    override fun process() {
      (mc.currentScreen as GuiScreenAccessor?)?.invokeMouseClickMove(
        this.mouseX,
        this.mouseY,
        this.clickedMouseButton,
        this.timeSinceLastClick
      )
    }
  }

  data class ScrollEvent(val dwheel: Int) : GuiInputEvent() {
    override fun writeEvent(buffer: PacketBuffer) {
      buffer.writeVarInt(-5)
      buffer.writeVarInt(this.dwheel)
    }

    private val eventDwheel = Mouse::class.java.getDeclaredField("event_dwheel")
      .apply { this.isAccessible = true }

    override fun process() {
      eventDwheel.setInt(null, dwheel)
      mc.currentScreen?.handleMouseInput()
    }
  }
}

data class RenderedPosition(
  val partialTicks: Float,
  val position: MousePosition
) {
  fun getUIPosition(): Pair<Int, Int> {
    val scaledX = this.position.x * mc.displayWidth.toFloat()
    val scaledY = this.position.y * mc.displayHeight.toFloat()
    val i = scaledX * mc.currentScreen!!.width / mc.displayWidth
    val j =
      mc.currentScreen!!.height - scaledY * mc.currentScreen!!.height / mc.displayHeight - 1
    return Pair(i.toInt(), j.toInt())
  }

  fun calculatePosition(prev: RenderedPosition, partialTicks: Float): MousePosition {
    if (prev == this) return this.position

    val curPartialTicks = this.partialTicks
    val prevPartialTicks = prev.partialTicks
    val timeBetween = curPartialTicks - prevPartialTicks
    val partialPartialTicks = (partialTicks - prevPartialTicks) / timeBetween
    val deltaX = this.position.x - prev.position.x
    val deltaY = this.position.y - prev.position.y

    return MousePosition(
      deltaX * partialPartialTicks + prev.position.x,
      deltaY * partialPartialTicks + prev.position.y
    )
  }
}

object Recorder {
  private var tickdex = 0L
  var writeLaterLock = Mutex()
  var toWritelater: ByteBuf = Unpooled.directBuffer(1024 * 1024 * 50)

  private var recordingFile: BufferedOutputStream? = null

  // Gui stuff idk
  val cursorPositions = mutableListOf<RenderedPosition>()
  val guiInputEvents = mutableListOf<GuiInputEventWithMoreStuff>()
  val keysDown = BooleanArray(8)
  var scaledRes = run {
    val scaled = ScaledResolution(mc)
    Triple(scaled.scaledWidth_double, scaled.scaledHeight_double, scaled.scaleFactor)
  }

  // Player look and stuff good good very important
  val rotations = mutableListOf<CameraRotation>()

  @Volatile
  var recording = false
  private var recordingThread: Thread? = null

  fun joinGame() {
    tickdex = 0

    // Create new recording file
    val date = Calendar.getInstance().time
    val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
    val formatedDate = formatter.format(date)

    File("recordings").mkdirs()
    val filePath = File("recordings/$formatedDate.rec")
    recordingFile = BufferedOutputStream(FileOutputStream(filePath, true))

    recording = true
    recordingThread = Thread {
      while (recording) {
        // File writes
        writeLaterLock.lock()
        val index = toWritelater.writerIndex()
        toWritelater.readBytes(recordingFile, index)
        toWritelater.clear()
        writeLaterLock.unlock()

        Thread.sleep(250)
      }
    }
    recordingThread!!.start()
  }

  fun leaveGame() {
    recording = false
    recordingThread?.join()
    recordingFile?.close()
  }

  // TODO - option to 'mark' current tick as a POI type thing
  //  (use 'S' and 'E' for start and end for rendering)

  // Reusable buffers for writing packet data
  val packetData = Unpooled.directBuffer(1024 * 1024 * 20)
  private val packetHeader = Unpooled.directBuffer(64)

  fun savePacket(id: Int, packetBuffer: PacketBuffer) {
    val packetSize = packetBuffer.readableBytes()
    val packetHeaderBuffer = PacketBuffer(packetHeader)
    packetHeaderBuffer.writeVarInt(id)
    packetHeaderBuffer.writeVarInt(packetSize)

    writeLaterLock.lock()
    if (toWritelater.writableBytes() < packetHeaderBuffer.writerIndex() + packetSize) {
      toWritelater.capacity((toWritelater.capacity() * 1.5).toInt())
    }
    packetHeader.readBytes(toWritelater, packetHeaderBuffer.writerIndex())
    packetBuffer.readBytes(toWritelater, packetSize)
    writeLaterLock.unlock()

    packetHeader.clear()
  }

  fun endTick() {
    tickdex++
    ClientEvent.writeClientEvent(ClientEvent.TickEnd)
  }
}
