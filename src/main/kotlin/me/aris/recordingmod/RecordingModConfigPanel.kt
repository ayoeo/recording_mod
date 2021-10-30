package me.aris.recordingmod

import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import net.minecraft.client.gui.GuiButton
import java.io.File

class RecordingModConfigPanel : AbstractConfigPanel() {
  override fun getPanelTitle() = "Recording Mod Config"

  override fun onPanelHidden() = Unit

  override fun addOptions(host: ConfigPanelHost?) {
    this.addControl(GuiButton(0, 0, 0, "Help")) {
      activeReplay = Replay(File("recordings/help"))
      activeReplay?.restart()
    }
  }
}
