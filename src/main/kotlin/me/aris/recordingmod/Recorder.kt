package me.aris.recordingmod

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.EnumConnectionState
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.Packet
import net.minecraft.network.PacketBuffer
import sun.awt.Mutex
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object Recorder {
  var writeLaterLock = Mutex()
  var toWritelater: ByteBuf = Unpooled.directBuffer(1024 * 1024 * 50)

  private var recordingFile: BufferedOutputStream? = null
  private var tickCount = 0L // TODO - store tick count

  @Volatile
  var recording = false
  var recordingThread: Thread? = null

  fun joinGame() {
    tickCount = 0

    // Create new recording file
    val date = Calendar.getInstance().time
    val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
    val formatedDate = formatter.format(date)
    println("ok... $formatedDate.rec")
    val filePath = File("$formatedDate.rec")
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
        println("Wrote $index bytes to file.")

        Thread.sleep(1000)
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
  private val packetData = Unpooled.directBuffer(1024 * 1024 * 20)
  private val packetHeader = Unpooled.directBuffer(64)
  fun savePacket(packet: Packet<*>) {
    val id =
      EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, packet) ?: return

    // PACKET DATA MOMENT
    val packetDataBuffer = PacketBuffer(packetData)
    packet.writePacketData(packetDataBuffer)

    // The packet needs to be ok we have to safe it keep the packet safe
    packet.readPacketData(PacketBuffer(packetData.duplicate()))

    val packetSize = packetData.writerIndex()
    val packetHeaderBuffer = PacketBuffer(packetHeader)

    packetHeaderBuffer.writeVarInt(id)
    packetHeaderBuffer.writeVarInt(packetSize)

    writeLaterLock.lock()
    if (toWritelater.writableBytes() < packetHeaderBuffer.writerIndex() + packetSize) {
      toWritelater.capacity((toWritelater.capacity() * 1.5).toInt())
    }
    packetHeader.readBytes(toWritelater, packetHeaderBuffer.writerIndex())
    packetData.readBytes(toWritelater, packetSize)
    writeLaterLock.unlock()

    packetData.clear()
    packetHeader.clear()
  }

  fun endTick() {
    writeLaterLock.lock()
//    ClientEvent.Look.write(PacketBuffer(toWritelater))
    // TODO - store tick count
    ClientEvent.TickEnd.write(PacketBuffer(toWritelater))
    // TODO - store tick count
    writeLaterLock.unlock()
  }
}
