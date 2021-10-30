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

object Recorder {
  var tickdex = 0L
  var writeLaterLock = Mutex()
  var toWritelater: ByteBuf = Unpooled.directBuffer(1024 * 1024 * 50)

  private var recordingFile: BufferedOutputStream? = null

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

  // Reusable buffers for writing packet data
//  private val packetDatazz = Unpooled.directBuffer(1024 * 1024 * 20)
  val packetData = Unpooled.directBuffer(1024 * 1024 * 20);
  private val packetHeader = Unpooled.directBuffer(64)
  fun savePacket(id: Int, packetBuffer: PacketBuffer) {
//    println("saving packet: $id ${packetBuffer.readableBytes()}")
    // PACKET DATA MOMENT
//    val packetDataBuffer = PacketBuffer(packetData)
//    packet.writePacketData(packetDataBuffer)


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

//    packetData.clear()
    packetHeader.clear()
  }

  fun endTick() {
    tickdex++
    ClientEvent.writeClientEvent(ClientEvent.TickEnd)
  }
}