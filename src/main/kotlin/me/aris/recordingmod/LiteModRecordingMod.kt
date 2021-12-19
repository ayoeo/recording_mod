package me.aris.recordingmod

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mumfrey.liteloader.Configurable
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.core.LiteLoader
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.Recorder.compressRecording
import me.aris.recordingmod.Recorder.recording
import me.aris.recordingmod.Recorder.recordingFile
import me.aris.recordingmod.Recorder.tickdex
import me.aris.recordingmod.mixins.MinecraftAccessor
import me.aris.recordingmod.mixins.TimerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.*
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File

val mc: Minecraft
  get() = Minecraft.getMinecraft()

var activeReplay: Replay? = null

class RecordingEntry(index: Int, name: String) : GuiListExtended.IGuiListEntry {
  override fun updatePosition(slotIndex: Int, x: Int, y: Int, partialTicks: Float) {
//    TODO("Not yet implemented")
  }

  override fun drawEntry(
    slotIndex: Int,
    x: Int,
    y: Int,
    listWidth: Int,
    slotHeight: Int,
    mouseX: Int,
    mouseY: Int,
    isSelected: Boolean,
    partialTicks: Float
  ) {
    Gui.drawRect(x, y, x + 32, y + 10, -1601138544)
//    TODO("Not yet implemented")
  }

  override fun mousePressed(
    slotIndex: Int, mouseX: Int, mouseY: Int, mouseEvent: Int, relativeX: Int, relativeY: Int
  ): Boolean {
//    TODO("Not yet implemented")
    return true
  }

  override fun mouseReleased(
    slotIndex: Int, x: Int, y: Int, mouseEvent: Int, relativeX: Int, relativeY: Int
  ) {
//    TODO("Not yet implemented")
  }
}

class MarkItGui : GuiScreen() {
  private lateinit var nameOfTheMahkah: GuiTextField

  override fun initGui() {
    this.nameOfTheMahkah = GuiTextField(
      1,
      mc.fontRenderer,
      this.width / 2 - 100,
      this.height / 2 - 10 - 20,
      200,
      20
    )
    this.nameOfTheMahkah.isFocused = true
    this.nameOfTheMahkah.maxStringLength = 128

    this.addButton(
      GuiButton(
        0,
        this.width / 2 - 50,
        this.height / 2,
        100,
        20,
        "Save Marker"
      )
    )
    super.initGui()
  }

  override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
    this.nameOfTheMahkah.drawTextBox()
    super.drawScreen(mouseX, mouseY, partialTicks)
  }

  override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
    this.nameOfTheMahkah.mouseClicked(mouseX, mouseY, mouseButton)
    super.mouseClicked(mouseX, mouseY, mouseButton)
  }

  override fun keyTyped(typedChar: Char, keyCode: Int) {
    this.nameOfTheMahkah.textboxKeyTyped(typedChar, keyCode)
    if (keyCode == Keyboard.KEY_RETURN) {
      this.actionPerformed(this.buttonList.first())
    }
    super.keyTyped(typedChar, keyCode)
  }

  override fun actionPerformed(button: GuiButton) {
    when (button.id) {
      0 -> {
        val recordingName = recordingFile!!.nameWithoutExtension
        File("markers").mkdirs()
        File(
          "markers",
          "${nameOfTheMahkah.text.replace(' ', '_')}-$recordingName"
        ).writeText("$tickdex")
        Minecraft.getMinecraft().displayGuiScreen(null)
      }
    }
    super.actionPerformed(button)
  }
}
//Not to be confused with market gui 

class RecordingsList(
  width: Int,
  height: Int
) : GuiListExtended(mc, width, height, 32, height - 64, 12) {
  val selected: Int? = null
  private val recordings = mutableListOf<RecordingEntry>()

  init {
    File(LiteModRecordingMod.mod.recordingPath)
      .listFiles()
      ?.filter { it.extension == "rec" }
      ?.withIndex()?.forEach { (i, file) ->
        this.recordings.add(RecordingEntry(i, file.nameWithoutExtension))
      }
  }

  override fun drawScreen(mouseXIn: Int, mouseYIn: Int, partialTicks: Float) {
    super.drawScreen(mouseXIn, mouseYIn, partialTicks)
  }

  override fun getSize(): Int {
    return this.recordings.size
  }

  override fun getListEntry(index: Int): IGuiListEntry {
    return this.recordings[index]
  }
}

class RecordingGui : GuiScreen() {
  private lateinit var recordingsList: RecordingsList

  override fun initGui() {
    val scaledRes = ScaledResolution(Minecraft.getMinecraft())
    recordingsList = RecordingsList(
      scaledRes.scaledWidth,
      scaledRes.scaledHeight
    )
  }

  override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
    this.drawDefaultBackground()
    recordingsList.drawScreen(mouseX, mouseY, partialTicks)
    super.drawScreen(mouseX, mouseY, partialTicks)
  }

  //  upda
//  override fun handleKeyboardInput() {
//    this.recordingsList.key()
//    super.handleKeyboardInput()
//  }

  override fun keyTyped(typedChar: Char, keyCode: Int) {
    val i = this.recordingsList.selected
    val entry = i?.let { this.recordingsList.getListEntry(it) }
    super.keyTyped(typedChar, keyCode)
  }

  override fun handleMouseInput() {
    this.recordingsList.handleMouseInput()
    super.handleMouseInput()
  }
}

@ExposableOptions(
  strategy = ConfigStrategy.Unversioned, filename = "recording_mod.json", aggressive = true
)
class LiteModRecordingMod : LiteMod, Tickable, Configurable {
  @Expose
  @SerializedName("recording_path")
  var recordingPath = "recordings"

  val codeFast = KeyBinding("codecodecodefast", Keyboard.KEY_L, "recording");

  override fun upgradeSettings(v: String?, c: File?, o: File?) {}

  companion object {
    lateinit var mod: LiteModRecordingMod
  }

  init {
    mod = this
//    next thing you do is make a gui that shows all the recordings, can decompress and play them (put in temp folder)

    File("recordings_in_progress").listFiles()?.forEach { file ->
      println("Found uncompressed recording: $file")
      compressRecording(file)
    }
    File(recordingPath)
      .listFiles()
      ?.filter { it.extension == "part" }
      ?.forEach { old ->
        old.delete()
      }
  }

  override fun onTick(
    minecraft: Minecraft, partialTicks: Float, inGame: Boolean, clock: Boolean
  ) {
    if (!inGame && recording) {
//      Recorder.leaveGame()
    }

    if (inGame && mc.currentScreen == null) {
      if (this.codeFast.isPressed) {
        mc.displayGuiScreen(MarkItGui())
      }
    }
  }

  override fun getName(): String = "Recording Mod"
  override fun getVersion(): String = "0.1"

  override fun init(configPath: File?) {
    LiteLoader.getInput().registerKeyBinding(this.codeFast)
  }

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
  @Suppress("ControlFlowWithEmptyBody") while (Mouse.next())
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
