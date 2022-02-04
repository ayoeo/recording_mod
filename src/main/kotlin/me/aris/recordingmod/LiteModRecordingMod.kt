package me.aris.recordingmod

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mumfrey.liteloader.LiteMod
import com.mumfrey.liteloader.Tickable
import com.mumfrey.liteloader.core.LiteLoader
import com.mumfrey.liteloader.modconfig.ConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigStrategy
import com.mumfrey.liteloader.modconfig.ExposableOptions
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import me.aris.recordingmod.Recorder.compressRecording
import me.aris.recordingmod.Recorder.compressThread
import me.aris.recordingmod.Recorder.recording
import me.aris.recordingmod.Recorder.recordingFile
import me.aris.recordingmod.Recorder.tickdex
import me.aris.recordingmod.mixins.MinecraftAccessor
import me.aris.recordingmod.mixins.TimerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.*
import net.minecraft.client.settings.KeyBinding
import org.apache.commons.io.comparator.LastModifiedFileComparator
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.io.File
import java.nio.charset.Charset

val mc: Minecraft
  get() = Minecraft.getMinecraft()

var activeReplay: Replay? = null

class RecordingEntry(val index: Int, val name: String) : GuiListExtended.IGuiListEntry {
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
    Gui.drawRect(x, y, x + listWidth, y + slotHeight, 0xFF000000.toInt())
    Gui.drawRect(
      x + 1,
      y + 1,
      x + listWidth - 1,
      y + slotHeight - 1,
      if (isSelected) 0xFF666666.toInt() else 0xFF888888.toInt()
    )

    mc.fontRenderer.drawString(
      name,
      x + listWidth / 2 - mc.fontRenderer.getStringWidth(name) / 2,
      y + 2,
      0
    )
  }

  override fun mousePressed(
    slotIndex: Int, mouseX: Int, mouseY: Int, mouseEvent: Int, relativeX: Int, relativeY: Int
  ): Boolean {
    return true
  }

  override fun mouseReleased(
    slotIndex: Int, x: Int, y: Int, mouseEvent: Int, relativeX: Int, relativeY: Int
  ) {
//    TODO("Not yet implemented")
  }
}

class MarkerEntry(
  val index: Int,
  val markerName: String,
  val recordingName: String,
  val tickdex: Int
) :
  GuiListExtended.IGuiListEntry {
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
    Gui.drawRect(x, y, x + listWidth, y + slotHeight, 0xFF000000.toInt())
    Gui.drawRect(
      x + 1,
      y + 1,
      x + listWidth - 1,
      y + slotHeight - 1,
      if (isSelected) 0xFF666666.toInt() else 0xFF888888.toInt()
    )

    var displayName = this.markerName
    while (mc.fontRenderer.getStringWidth(displayName) >= listWidth - 10) {
      displayName = displayName.dropLast(1)
    }
    val indexText = "${index + 1}:"
    mc.fontRenderer.drawString(
      indexText,
      x - 4 - mc.fontRenderer.getStringWidth(indexText),
      y + 2,
      0xDDDDDD
    )
    mc.fontRenderer.drawString(
      displayName,
      x + listWidth / 2 - mc.fontRenderer.getStringWidth(displayName) / 2,
      y + 2,
      0
    )
  }

  override fun mousePressed(
    slotIndex: Int, mouseX: Int, mouseY: Int, mouseEvent: Int, relativeX: Int, relativeY: Int
  ): Boolean {
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
        var s = nameOfTheMahkah.text.replace("[\\\\/:*?\"<>|]".toRegex(), "_");
        while (File("markers", "$s-$recordingName").exists()) {
          s += "_"
        }

        File(
          "markers",
          "$s-$recordingName"
        ).writeText("$tickdex")
        Minecraft.getMinecraft().displayGuiScreen(null)
      }
    }
    super.actionPerformed(button)
  }
}
//Not to be confused with market gui 

class MarkerList(
  width: Int,
  height: Int
) : GuiListExtended(mc, width, height, 24, height - 24, 16) {
  val selected: Int? = null
  private val markers = mutableListOf<MarkerEntry>()

  override fun getListWidth(): Int {
    return 150
  }

  init {
    File("markers")
      .listFiles()
      ?.sortedWith(LastModifiedFileComparator())
      ?.withIndex()?.forEach { (i, file) ->
        val name = file.nameWithoutExtension
        val split = name.split('-')
        val markerName = split.first()
        val recordingName = split.last()
        this.markers.add(
          MarkerEntry(
            i,
            markerName,
            recordingName,
            file.readText(Charset.defaultCharset()).toInt()
          )
        )
      }
  }

  override fun elementClicked(
    index: Int,
    p_elementClicked_2_: Boolean,
    p_elementClicked_3_: Int,
    p_elementClicked_4_: Int
  ) {
//    println("element clicked: $index $p_elementClicked_2_ $p_elementClicked_3_ $p_elementClicked_4_")
    // TODO - set last played index to 'index' and then it will be a different color which we CAN USE TO TELL WHERE WE ARE HAHA
    val name = this.markers[index].recordingName
    val tickdex = this.markers[index].tickdex
    println("uncompressing $name")
    val recFile = File(mod.recordingPath, "$name.rec")

    // clean it up now
    val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
    f.mkdirs()

    val uncompressedRecording = File(f, name)
    if (!uncompressedRecording.exists()) {
      f.listFiles()?.forEach { it.delete() }
//      val os = System.getProperty("os.name").toLowerCase()
      val exename = "\"${File(mod.sevenZipPath).absolutePath}\""

      val proc = Runtime.getRuntime()
        .exec(
          "$exename x \"${recFile.absolutePath}\"",
          arrayOf(),
          f
        )
      proc.waitFor()
    } else {
      println("Recording was already uncompressed we're good")
    }
    println("playing $name")
    mc.displayGuiScreen(GuiDownloadTerrain())
    activeReplay = Replay(File(f, name))
    activeReplay?.restart()
    activeReplay?.skipTo(tickdex)
    paused = false
//    println("clicked on $n")
  }

  override fun getSize(): Int {
    return this.markers.size
  }

  override fun getListEntry(index: Int): GuiListExtended.IGuiListEntry {
    return this.markers[index]
  }
}

class RecordingsList(
  width: Int,
  height: Int
) : GuiListExtended(mc, width, height, 24, height - 24, 16) {
  val selected: Int? = null
  private val recordings = mutableListOf<RecordingEntry>()

  override fun getListWidth(): Int {
    return 150
  }

  init {
    File(mod.recordingPath)
      .listFiles()
      ?.filter { it.extension == "rec" }
      ?.withIndex()?.forEach { (i, file) ->
        this.recordings.add(RecordingEntry(i, file.nameWithoutExtension))
      }
  }

  override fun elementClicked(
    index: Int,
    p_elementClicked_2_: Boolean,
    p_elementClicked_3_: Int,
    p_elementClicked_4_: Int
  ) {
//    println("element clicked: $index $p_elementClicked_2_ $p_elementClicked_3_ $p_elementClicked_4_")
    // TODO - set last played index to 'index' and then it will be a different color which we CAN USE TO TELL WHERE WE ARE HAHA
    val name = this.recordings[index].name
    println("uncompressing $name")
    val recFile = File(mod.recordingPath, "$name.rec")

    // clean it up now
    val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
    f.mkdirs()

    val uncompressedRecording = File(f, name)
    if (!uncompressedRecording.exists()) {
      f.listFiles()?.forEach { it.delete() }
//      val os = System.getProperty("os.name").toLowerCase()
      val exename = "\"${File(mod.sevenZipPath).absolutePath}\""

      val proc = Runtime.getRuntime()
        .exec(
          "$exename x \"${recFile.absolutePath}\"",
          arrayOf(),
          f
        )
      proc.waitFor()
    } else {
      println("Recording was already uncompressed we're good")
    }
    println("playing $name")
    mc.displayGuiScreen(GuiDownloadTerrain())
    activeReplay = Replay(File(f, name))
    activeReplay?.restart()
    paused = false
//    println("clicked on $n")
  }

  override fun getSize(): Int {
    return this.recordings.size
  }

  override fun getListEntry(index: Int): GuiListExtended.IGuiListEntry {
    return this.recordings[index]
  }
}

object RecordingGui : GuiScreen() {
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

object MarkerGui : GuiScreen() {
  private lateinit var markerList: MarkerList

  override fun initGui() {
    val scaledRes = ScaledResolution(Minecraft.getMinecraft())
    markerList = MarkerList(
      scaledRes.scaledWidth,
      scaledRes.scaledHeight
    )
  }

  override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
    this.drawDefaultBackground()
    markerList.drawScreen(mouseX, mouseY, partialTicks)
    super.drawScreen(mouseX, mouseY, partialTicks)
  }

  override fun keyTyped(typedChar: Char, keyCode: Int) {
    val i = this.markerList.selected
    val entry = i?.let { this.markerList.getListEntry(it) }
    super.keyTyped(typedChar, keyCode)
  }

  override fun handleMouseInput() {
    this.markerList.handleMouseInput()
    super.handleMouseInput()
  }
}

@ExposableOptions(
  strategy = ConfigStrategy.Unversioned, filename = "recording_mod.json", aggressive = true
)
class LiteModRecordingMod : LiteMod, Tickable, com.mumfrey.liteloader.Configurable {
  @Expose
  @SerializedName("recording_path")
  var recordingPath = "recordings"

  @Expose
  @SerializedName("seven_zip_path")
  var sevenZipPath = "C:/Program Files/7-Zip"

  val codeFast = KeyBinding("codecodecodefast", Keyboard.KEY_L, "recording");

  override fun upgradeSettings(v: String?, c: File?, o: File?) {}

  companion object {
    lateinit var mod: LiteModRecordingMod

    fun checkAndCompressFiles() {
      File(mod.recordingPath)
        .listFiles()
        ?.filter { it.extension == "part" }
        ?.forEach { old ->
          old.delete()
        }

      File("recordings_in_progress").listFiles()
        ?.filter { it.extension != "wav" }
        ?.forEach { file ->
          println("Found uncompressed recording: $file")
          compressRecording(file)
        }
    }
  }

  init {
    mod = this

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

    val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
    f.mkdirs()
    f.deleteRecursively()

//    next thing you do is make a gui that shows all the recordings, can decompress and play them (put in temp folder)
    compressThread = Thread() {
      checkAndCompressFiles()
    }
    compressThread?.start()
  }

  override fun getConfigPanelClass(): Class<out ConfigPanel> {
    return RecordingModConfigPanel::class.java
  }
}

var skipping = false
var blipping = false
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

      Keyboard.KEY_Z -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
        activeReplay?.skipBackwards(20 * 60 * 10)
        LittleTestPerformanceTrackerThing.printTimings()
        println("Skipping back 10 minutes...")
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
        println("we're at $tickdex")
        LittleTestPerformanceTrackerThing.printTimings()
        println("Skipping 10 seconds...")
        return true
      }

      Keyboard.KEY_O -> {
        // SKIP MOMENT SKIPMENT
        LittleTestPerformanceTrackerThing.resetTimings()
//        activeReplay?.restart()
//        activeReplay?.skipTo(149706) // 162 // breaks chunk stuff - 1_16_08_16_56

//        activeReplay?.skipTo(180600) //   01_11_00_10_01 hla helmet horse weird thing
        activeReplay?.skipTo(67832) //   t2 fight haha 12_17_20_47_39
//        activeReplay?.skipTo(236000) //   01_04_04_50_58 hla riph stronghold avalon
//        activeReplay?.skipTo(67794) // 162  idk maybe broken players?? (THICKMENT THICK SO THICK)
//        activeReplay?.skipTo(334893) // 162  idk maybe broken players??
//        activeReplay?.skipTo(125420) // 162 // breaks chunk stuff (THICKMENT THICKMENT SO THICK)
//        activeReplay?.skipTo(367287) // 162 // thickment
        LittleTestPerformanceTrackerThing.printTimings()
        println("SKIPPING TO THAT ONE PLACE YOU LIKE")
        return true
      }

      Keyboard.KEY_P -> {
        mc.loadWorld(null)
        activeReplay = null
        ReplayState.currentGuiState = null
        ReplayState.nextGuiState = null
        ReplayState.nextAbsoluteState = null
        ReplayState.nextGuiProcessState = null
        ReplayState.wasOnHorse = false
        ReplayState.nextKeybindingState = ClientEvent.trackedKeybinds.map {
          Pair(false, 0)
        }
        mc.displayGuiScreen(RecordingGui)
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
  @Suppress("ControlFlowWithEmptyBody") while (Mouse.next()) {
    Mouse.getDX()
    Mouse.getDY()
  }
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

  if (mc.currentScreen is RecordingGui) {
    return true
  }

  if (paused) {
    return true
  }

  if (blipping) return false
  activeReplay?.playNextTick()

  // TODO - put this back lol
  mc.player?.rotationYaw = ReplayState.nextYaw
  mc.player?.rotationPitch = ReplayState.nextPitch

  return false
}
