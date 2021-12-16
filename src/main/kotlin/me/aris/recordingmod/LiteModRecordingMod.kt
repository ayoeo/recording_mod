package me.aris.recordingmod

import com.mumfrey.liteloader.Configurable
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.Recorder.compressRecording
import me.aris.recordingmod.Recorder.recording
import me.aris.recordingmod.mixins.MinecraftAccessor
import me.aris.recordingmod.mixins.TimerAccessor
import net.minecraft.client.Minecraft
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File

val mc: Minecraft
  get() = Minecraft.getMinecraft()

var activeReplay: Replay? = null

@ExposableOptions(
  strategy = ConfigStrategy.Unversioned,
  filename = "recording_mod.json",
  aggressive = true
)
class LiteModRecordingMod : LiteMod, Tickable, Configurable {
  override fun upgradeSettings(v: String?, c: File?, o: File?) {}

  init {
//    next thing you do is make a gui

    File("recordings_in_progress").listFiles()?.forEach { file ->
      println("Found uncompressed recording: $file")
      compressRecording(file)
    }
  }

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

  override fun getName(): String = "Recording Mod"
  override fun getVersion(): String = "0.1"

  override fun init(configPath: File?) = Unit

  override fun getConfigPanelClass(): Class<out ConfigPanel> {
    return RecordingModConfigPanel::class.java
  }
}

var skipping = false
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

  keys.forEach { key ->
    when (key) {
      Keyboard.KEY_SPACE -> {
        paused = !paused

        Renderer.finishRender()
      }

      Keyboard.KEY_A -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipBackwards(20 * 10)
        LittleTestPerformanceTrackerThing.printTimings()
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

      Keyboard.KEY_R -> {
        Renderer.startRender()
      }

      Keyboard.KEY_S -> {
        Renderer.startTick = activeReplay?.tickdex ?: 0
      }

      Keyboard.KEY_E -> {
        Renderer.endTick = activeReplay?.tickdex ?: 0
      }

      Keyboard.KEY_F -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipForward(20 * 60 * 5)
        LittleTestPerformanceTrackerThing.printTimings()

        println("Skipping 5 minutes...")

        return true
      }

      Keyboard.KEY_G -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipForward(20 * 60 * 60)
        LittleTestPerformanceTrackerThing.printTimings()

        println("Skipping 5 minutes...")

//        SevenZFile.
        return true
      }

      Keyboard.KEY_D -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipForward(20 * 2)
        LittleTestPerformanceTrackerThing.printTimings()
        println("Skipping 10 seconds...")
        return true
      }

      Keyboard.KEY_O -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipTo(490) // 162
//        activeReplay?.restart()
        LittleTestPerformanceTrackerThing.printTimings()
        println("SKIPPING TO THAT ONE PLACE YOU LIKE")
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
  @Suppress("ControlFlowWithEmptyBody")
  while (Mouse.next());
  Mouse.getDX()
  Mouse.getDY()
  // TODO - let mouse back for gui? hahahah
  // MOUSE WILL NOT BE

  if (Keyboard.isKeyDown(Keyboard.KEY_K)) {
    ((mc as MinecraftAccessor).timer as TimerAccessor).tickLength = 1000f / 5f
  } else {
    ((mc as MinecraftAccessor).timer as TimerAccessor).tickLength = 1000f / 20f
  }

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
    return false
  }

  if (paused) {
    return true
  }

//  if (skipping) return false
  activeReplay?.playNextTick()

  // TODO - put this back lol
  mc.player?.rotationYaw = ReplayState.nextYaw
  mc.player?.rotationPitch = ReplayState.nextPitch

  return false
}
