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
  private lateinit var renderingWidth: ConfigTextField
  private lateinit var renderingHeight: ConfigTextField
  private lateinit var renderingFps: ConfigTextField
  private lateinit var blendFactor: ConfigTextField
  private lateinit var proxyRenderingWidth: ConfigTextField
  private lateinit var proxyRenderingHeight: ConfigTextField

  override fun onPanelHidden() {
    mod.recordingPath = this.recordingPath.text
    mod.sevenZipPath = this.sevenZipPath.text
    mod.renderingWidth = (this.renderingWidth.text.toIntOrNull() ?: mod.renderingWidth) / 8 * 8
    mod.renderingHeight = (this.renderingHeight.text.toIntOrNull() ?: mod.renderingHeight) / 8 * 8
    mod.renderingFps = (this.renderingFps.text.toIntOrNull() ?: mod.renderingFps)
    mod.blendFactor = (this.blendFactor.text.toIntOrNull() ?: mod.blendFactor)
    mod.proxyRenderingWidth =
      (this.proxyRenderingWidth.text.toIntOrNull() ?: mod.proxyRenderingWidth) / 8 * 8
    mod.proxyRenderingHeight =
      (this.proxyRenderingHeight.text.toIntOrNull() ?: mod.proxyRenderingHeight) / 8 * 8
    LiteLoader.getInstance().writeConfig(mod)
  }

  override fun addOptions(host: ConfigPanelHost) {
    this.addControl(GuiButton(0, 0, 0, 65, 20, "Recordings")) {
      mc.displayGuiScreen(RecordingGui)
    }

    this.addControl(GuiButton(1, 70, 0, 65, 20, "Markers")) {
      mc.displayGuiScreen(MarkerGui)
    }

    this.addLabel(0, 0, 45, 0, 0, 0xFFFFFF, "Recording Path")
    this.recordingPath =
      this.addTextField(0, mc.fontRenderer.getStringWidth("Recording Path") + 10, 35, 300, 20)
    this.recordingPath.setMaxLength(1024)
    this.recordingPath.text = mod.recordingPath

    this.addLabel(0, 0, 80, 0, 0, 0xFFFFFF, "7-Zip Path")
    this.sevenZipPath =
      this.addTextField(1, mc.fontRenderer.getStringWidth("7-Zip Path") + 10, 70, 300, 20)
    this.sevenZipPath.setMaxLength(1024)
    this.sevenZipPath.text = mod.sevenZipPath

    this.addLabel(0, 0, 115, 0, 0, 0xFFFFFF, "Rendering Width")
    this.renderingWidth =
      this.addTextField(2, mc.fontRenderer.getStringWidth("Rendering Width") + 10, 105, 300, 20)
    this.renderingWidth.text = mod.renderingWidth.toString()

    this.addLabel(0, 0, 150, 0, 0, 0xFFFFFF, "Rendering Height")
    this.renderingHeight =
      this.addTextField(3, mc.fontRenderer.getStringWidth("Rendering Hieght") + 10, 140, 300, 20)
    this.renderingHeight.text = mod.renderingHeight.toString()

    this.addLabel(0, 0, 185, 0, 0, 0xFFFFFF, "Rendering Fps")
    this.renderingFps =
      this.addTextField(3, mc.fontRenderer.getStringWidth("Rendering Fps") + 10, 175, 300, 20)
    this.renderingFps.text = mod.renderingFps.toString()

    this.addLabel(0, 0, 220, 0, 0, 0xFFFFFF, "Blend Factor")
    this.blendFactor =
      this.addTextField(4, mc.fontRenderer.getStringWidth("Blend Factor") + 10, 210, 300, 20)
    this.blendFactor.text = mod.blendFactor.toString()

    this.addLabel(0, 0, 255, 0, 0, 0xFFFFFF, "Proxy Rendering Width")
    this.proxyRenderingWidth =
      this.addTextField(
        5,
        mc.fontRenderer.getStringWidth("Proxy Rendering Width") + 10,
        245,
        300,
        20
      )
    this.proxyRenderingWidth.text = mod.proxyRenderingWidth.toString()

    this.addLabel(0, 0, 290, 0, 0, 0xFFFFFF, "Proxy Rendering Height")
    this.proxyRenderingHeight =
      this.addTextField(
        6,
        mc.fontRenderer.getStringWidth("Proxy Rendering Height") + 10,
        280,
        300,
        20
      )
    this.proxyRenderingHeight.text = mod.proxyRenderingHeight.toString()

    this.addControl(
      GuiButton(
        2,
        0,
        315,
        "Use Yuv444: ${mod.useYuv444}"
      )
    ) {
      mod.useYuv444 = !mod.useYuv444
      it.displayString = "Use Yuv444: ${mod.useYuv444}"
    }
  }
}
