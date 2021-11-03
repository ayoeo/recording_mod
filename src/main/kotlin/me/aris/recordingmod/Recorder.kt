package me.aris.recordingmod

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import sun.awt.Mutex
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class MousePosition(val x: Float, val y: Float) {
  fun getScaledXY() =
    Pair((x * 1920f).toInt(), (y * 1080f).toInt())
}

//data class ScreenSize(val w: Int, val h: Int)
data class RenderedPosition(
  val partialTicks: Float,
  val position: MousePosition
) {
  fun calculatePosition(prevPartialTicks: Float, partialTicks: Float) {
    // TODO - idk make it from these its a float now so go for it
    val screenSizeW = mc.displayWidth
    val screenSizeH = mc.displayWidth

//    val realPartialTicks = 
    // TODO - idk, partialticks is like the game's partial ticks and how far is that in between
    //  the previous cursor position and the current cursor position idk what is this trash help
    val partialPartialTicks = ((this.partialTicks - prevPartialTicks) * partialTicks)
    // TODO - use partialpartialticks to determine (0.5 - 0.25) * 0.5
  }
}

object Recorder {
  private var tickdex = 0L
  var writeLaterLock = Mutex()
  var toWritelater: ByteBuf = Unpooled.directBuffer(1024 * 1024 * 50)

  private var recordingFile: BufferedOutputStream? = null

  // Gui stuff idk
  val cursorPositions = mutableListOf<RenderedPosition>()

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
    println("leaving game ok")
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
