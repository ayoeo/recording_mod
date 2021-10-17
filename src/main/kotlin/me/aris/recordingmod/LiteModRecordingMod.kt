package me.aris.recordingmod

import com.mumfrey.liteloader.Configurable
import com.mumfrey.liteloader.HUDRenderListener
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.Recorder.recording
import me.aris.recordingmod.Recorder.toWritelater
import me.aris.recordingmod.Recorder.writeLaterLock
import me.aris.recordingmod.Replay.processReplayPackets
import me.aris.recordingmod.Replay.replaying
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.network.PacketBuffer
import java.io.File
import kotlin.math.pow
import kotlin.reflect.KParameter


val mc: Minecraft
  get() = Minecraft.getMinecraft()

@ExposableOptions(
  strategy = ConfigStrategy.Unversioned,
  filename = "recording_mod.json",
  aggressive = true
)
class LiteModDRImprovement : LiteMod, Tickable, HUDRenderListener, Configurable {
  override fun upgradeSettings(v: String?, c: File?, o: File?) {}

  override fun onTick(
    minecraft: Minecraft,
    partialTicks: Float,
    inGame: Boolean,
    clock: Boolean
  ) {
    if (!inGame && recording) {
      Recorder.leaveGame()
    }

    if (!clock) return

    // Raaa
    if (!replaying && mc.player != null) Recorder.endTick()
  }

  override fun onPreRenderHUD(screenWidth: Int, screenHeight: Int) {
    GlStateManager.pushMatrix()
    val scaleFactor = ScaledResolution(mc).scaleFactor.toDouble()
    val scale = scaleFactor / scaleFactor.pow(2.0)
    GlStateManager.scale(scale, scale, 1.0)

    // TODO - draw stuff maybe idk man

    GlStateManager.popMatrix()
  }

  override fun onPostRenderHUD(screenWidth: Int, screenHeight: Int) {
  }

  override fun getName(): String = "Recording Mod"
  override fun getVersion(): String = "0.1"

  override fun init(configPath: File?) = Unit

  override fun getConfigPanelClass(): Class<out ConfigPanel> {
    return RecordingModConfigPanel::class.java
  }
}

// Do it before the tick to make it work correctly
fun preTick() {
  if (replaying) {
    processReplayPackets()
    // TODO - in between yaw isn't like there idk for rendering it's bad but that's ok for now
    if (replaying) {
      mc.player.rotationYaw = Replay.nextYaw
      mc.player.rotationPitch = Replay.nextPitch
    }
  } else if (recording) {
    writeLaterLock.lock()
    ClientEvent.Look.write(PacketBuffer(toWritelater))
    writeLaterLock.unlock()
  }
}
