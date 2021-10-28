package me.aris.recordingmod

import com.mumfrey.liteloader.Configurable
import com.mumfrey.liteloader.HUDRenderListener
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.Recorder.recording
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import kotlin.math.pow

val mc: Minecraft
  get() = Minecraft.getMinecraft()

var activeReplay: Replay? = null

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
        activeReplay?.skipBackwards(20 * 10)
        println("Skipping back 10 seconds...")
        return true
      }

      Keyboard.KEY_Q -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipBackwards(20 * 30)
        LittleTestPerformanceTrackerThing.printTimings()
        println("Skipping back 30 seconds...")
        return true
      }

      Keyboard.KEY_F -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipForward(20 * 60 * 5)
        LittleTestPerformanceTrackerThing.printTimings()

        println("Skipping 5 minutes...")

        return true
      }

      Keyboard.KEY_D -> {
        // SKIP MOMENT SKIPMENT
        activeReplay?.skipForward(20 * 10)
        println("Skipping 10 seconds...")
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

  // MOUSE WILL NOT BE
  while (Mouse.next()) {
  }
  Mouse.getDX()
  Mouse.getDY()
  // MOUSE WILL NOT BE

  return checkKeybinds()

  // TODO - also something something wait for tick do X renders change partialticks
  //  just really fuck with the timer and make it work how we want it to

  // Here, we're just sort of running the game normally
  // Not skipping, rewinding, paused, etc.
//  return false
}

// Return true to cancel the tick
fun preTick(): Boolean {
  if (activeReplay?.reachedEnd() == true) {
    paused = true
  }

  if (paused) {
    return true
  }

  activeReplay?.playNextTick()

  mc.player?.rotationYaw = ReplayState.nextYaw
  mc.player?.rotationPitch = ReplayState.nextPitch

  return false
}
