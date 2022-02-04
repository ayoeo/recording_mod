package me.aris.recordingmod

import com.mumfrey.liteloader.core.LiteLoader
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import net.minecraft.client.gui.GuiButton

class RecordingModConfigPanel : AbstractConfigPanel() {
  override fun getPanelTitle() = "Recording Mod Config"

  private lateinit var recordingPath: ConfigTextField
  private lateinit var sevenZipPath: ConfigTextField

  override fun onPanelHidden() {
    mod.recordingPath = this.recordingPath.text
    mod.sevenZipPath = this.sevenZipPath.text
    LiteLoader.getInstance().writeConfig(mod)
  }

  override fun addOptions(host: ConfigPanelHost) {
    this.addControl(GuiButton(0, 0, 0, 40, 20, "Recordings")) {
      mc.displayGuiScreen(RecordingGui)
    }

    this.addControl(GuiButton(1, 60, 0, 40, 20, "Markers")) {
      mc.displayGuiScreen(MarkerGui)
    }

    this.recordingPath = this.addTextField(0, 0, 35, 300, 30)
    this.recordingPath.setMaxLength(1024)
    this.sevenZipPath = this.addTextField(1, 0, 70, 300, 30)
    this.sevenZipPath.setMaxLength(1024)
    this.recordingPath.text = mod.recordingPath
    this.sevenZipPath.text = mod.sevenZipPath
  }
}
