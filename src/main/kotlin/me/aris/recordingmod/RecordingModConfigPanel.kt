package me.aris.recordingmod

import com.mojang.authlib.GameProfile
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import io.netty.buffer.Unpooled
import me.aris.recordingmod.Replay.generateRestorePoints
import me.aris.recordingmod.Replay.packetsTime
import me.aris.recordingmod.Replay.replaying
import me.aris.recordingmod.Replay.restorePoints
import me.aris.recordingmod.Replay.tickdex
import net.minecraft.client.gui.GuiButton
import net.minecraft.network.PacketBuffer
import java.io.File
import java.util.*

//val f = File("testo")

val f = File("anfun")
var by = f.readBytes()
var bufferbuffersobufferbuffer = PacketBuffer(Unpooled.copiedBuffer(by))

// TODO - use the actual player's profile lmao
var netHandler = NetHandlerReplayClient(
  mc,
  null,
  GameProfile(UUID.randomUUID(), "Guy")
)

fun initReplay() {
  packetsTime.clear()
  bufferbuffersobufferbuffer.resetReaderIndex()
//  bufferbuffersobufferbuffer = PacketBuffer(Unpooled.copiedBuffer(by))
  netHandler = NetHandlerReplayClient(
    mc,
    null,
    GameProfile(UUID.randomUUID(), "Guy")
  )
}

class RecordingModConfigPanel : AbstractConfigPanel() {
  override fun getPanelTitle() = "Recording Mod Config"

  override fun onPanelHidden() = Unit

  override fun addOptions(host: ConfigPanelHost?) {
    this.addControl(GuiButton(0, 0, 0, "Help")) {
      println("idk man")
      replaying = !replaying
      generateRestorePoints()
      println("${restorePoints.size} restore points found")
      tickdex = 0
    }
  }
}
