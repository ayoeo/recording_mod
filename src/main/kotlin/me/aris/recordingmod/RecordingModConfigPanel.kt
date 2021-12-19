package me.aris.recordingmod

import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiTextField
import java.io.File

class RecordingModConfigPanel : AbstractConfigPanel() {
  override fun getPanelTitle() = "Recording Mod Config"

  private lateinit var recordingPath: ConfigTextField

  override fun onPanelHidden() {
    LiteModRecordingMod.mod.recordingPath = this.recordingPath.text
  }

  override fun addOptions(host: ConfigPanelHost) {
    this.addControl(GuiButton(0, 0, 0, "Help")) {
      mc.displayGuiScreen(RecordingGui())
    }

    this.recordingPath = this.addTextField(0, 0, 35, 100, 30)
    this.recordingPath.setMaxLength(512)
    val mod = host.getMod<LiteModRecordingMod>()
    this.recordingPath.text = mod.recordingPath
  }
}
