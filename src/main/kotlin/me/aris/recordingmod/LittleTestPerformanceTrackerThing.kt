package me.aris.recordingmod

import net.minecraft.network.Packet
import kotlin.system.measureNanoTime

object LittleTestPerformanceTrackerThing {
  private val packetTimes = hashMapOf<Class<Packet<NetHandlerReplayClient>>, Long>()

  fun resetTimings() {
    packetTimes.clear()
  }

  fun timePacket(packet: Packet<NetHandlerReplayClient>) {
    val processNanos = measureNanoTime {
      packet.processPacket(activeReplay!!.netHandler)
    }

    val existing = packetTimes.getOrDefault<Class<*>, Long>(packet.javaClass, 0)
    packetTimes[packet.javaClass] = existing + processNanos
  }

  fun printTimings() {
    for ((oh, oho) in packetTimes.asIterable().sortedBy { it.value }) {
      println("Packets: $oh, ${oho / 1000000}")
    }
  }
}
