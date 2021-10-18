package me.aris.recordingmod

import com.mojang.authlib.GameProfile
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import io.netty.buffer.Unpooled
import me.aris.recordingmod.Replay.replaying
import me.aris.recordingmod.Replay.tickdex
import net.minecraft.client.gui.GuiButton
import net.minecraft.network.PacketBuffer
import java.io.File
import java.util.*

val f = File("inv.rec")
var by = f.readBytes()
var bufferbuffersobufferbuffer = PacketBuffer(Unpooled.wrappedBuffer(by))

// TODO - use the actual player's profile lmao
var netHandler = NetHandlerReplayClient(
  mc,
  null,
  GameProfile(UUID.randomUUID(), "Guy")
)

fun initReplay() {
  by = f.readBytes()
  bufferbuffersobufferbuffer = PacketBuffer(Unpooled.wrappedBuffer(by))
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
      tickdex = 0
    }
  }
}
