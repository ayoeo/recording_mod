package me.aris.recordingmod

import com.mumfrey.liteloader.Configurable
import com.mumfrey.liteloader.HUDRenderListener
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.Recorder.recording
import me.aris.recordingmod.Replay.replayOneTick
import me.aris.recordingmod.Replay.replayOneTickPackets
import me.aris.recordingmod.Replay.replaying
import me.aris.recordingmod.Replay.tickdex
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import kotlin.math.max
import kotlin.math.pow

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

//    if (!clock) return

    // Raaa
//    if (!replaying && mc.player != null) Recorder.endTick()
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

var paused = false

private fun skipForward(ticks: Int) {
  for (fuckYou in 0 until ticks) {
    replayOneTick()
  }
}

private fun rewind(ticks: Int) {
  // go to beginning??!?!
  mc.loadWorld(null)
  initReplay()
  val skip = max(tickdex - ticks, 0)
  tickdex = 0
  skipForward(skip)
}

fun checkKeybinds(): Boolean {
  val keys = mutableListOf<Int>()
  while (Keyboard.next()) {
    val down = Keyboard.getEventKeyState()
    val keycode = if (Keyboard.getEventKey() == 0) {
      Keyboard.getEventCharacter().toInt() + 256
    } else {
      Keyboard.getEventKey()
    }

    if (down) {
      keys.add(keycode)

    }
  }

  for (key in keys) {
    when (key) {
      Keyboard.KEY_SPACE -> {
        paused = !paused
      }

      Keyboard.KEY_A -> {
        // SKIP MOMENT SKIPMENT
        rewind(20 * 1)
        return true
      }

      Keyboard.KEY_Q -> {
        // SKIP MOMENT SKIPMENT
        rewind(20 * 30)
        return true
      }

      Keyboard.KEY_F -> {
        // SKIP MOMENT SKIPMENT
        skipForward(20 * 60 * 1)
        return true
      }

      Keyboard.KEY_D -> {
        // SKIP MOMENT SKIPMENT
        skipForward(20 * 10)
        return true
      }
    }
  }

  return false
}

// True if we're taking over :)
fun preGameLoop(): Boolean {
  // Custom key handling is required here
  // This also wipes keybinds to remove player interaction from the game
  while (Mouse.next()) {
  }
  return checkKeybinds()

  // TODO - force pause if we've reached the end of the replay
  // TODO - force pause if we've reached the end of the replay

  // TODO - also something something wait for tick do X renders change partialticks
  //  just really fuck with the timer and make it work how we want it to

  // Here, we're just sort of running the game normally
  // Not skipping, rewinding, paused, etc.
//  return false
}

// Return true to cancel the tick
fun preTick(): Boolean {
  if (paused) {
    return true
  }

  replayOneTickPackets()

//  mc.player?.rotationYaw = Replay.nextYaw
//  mc.player?.rotationPitch = Replay.nextPitch

  tickdex++
  return false
}
// Do it before the tick to make it work correctly
//fun replaySingleTick() {
////  replayOneTickPackets()
//  mc.player.rotationYaw = Replay.nextYaw
//  mc.player.rotationPitch = Replay.nextPitch
//
//  // mc tick stuff here
//}
