package me.aris.recordingmod

import com.mumfrey.liteloader.core.LiteLoader
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiDownloadTerrain
import java.io.File

class RecordingModConfigPanel : AbstractConfigPanel() {
  override fun getPanelTitle() = "Recording Mod Config"

  private lateinit var recordingPath: ConfigTextField
  private lateinit var finalRenderPath: ConfigTextField
  private lateinit var sevenZipPath: ConfigTextField
  private lateinit var renderingWidth: ConfigTextField
  private lateinit var renderingHeight: ConfigTextField
  private lateinit var renderingFps: ConfigTextField
  private lateinit var blendFactor: ConfigTextField
  private lateinit var proxyRenderingWidth: ConfigTextField
  private lateinit var proxyRenderingHeight: ConfigTextField

  override fun onPanelHidden() {
    mod.recordingPath = this.recordingPath.text
    mod.finalRenderPath = this.sevenZipPath.text
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

  private fun renderBlueprints(proxy: Boolean) {
    Thread {
      File("blueprints")
        .listFiles()
        ?.filter { it.extension == "bp" }
        ?.forEach { file ->
          println("rendering blueprint ${file.name}")
          val splitthefileohgod = file.nameWithoutExtension.split('-')[0]
          val starttickdex = splitthefileohgod.split("..")[0].toInt()
          val endtickdex = splitthefileohgod.split("..")[1].toInt()
          val name = file.nameWithoutExtension.split('-')[1]
          val recFile = File(mod.recordingPath, "$name.rec")

          // clean it up now
          val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
          f.mkdirs()

          val uncompressedRecording = File(f, name)
          if (!uncompressedRecording.exists()) {
            f.listFiles()?.forEach { it.delete() }
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

          println("rendering $name...")
          Minecraft.getMinecraft().addScheduledTask {
            mc.displayGuiScreen(GuiDownloadTerrain())
            activeReplay = Replay(File(f, name))

            activeReplay?.restart()
            Renderer.sloMoRegions.clear()
            file.readLines().forEach { line ->
              val range = line.split(',')[0].split("..")
              val start = range[0].toInt()
              val end = range[1].toInt()
              val mult = line.split(',')[1].toInt()
              Renderer.sloMoRegions.add(Renderer.SloMoRegion(start..end, mult))
            }
            Renderer.startTick = starttickdex
            Renderer.endTick = endtickdex
            paused = false
            Renderer.startRender(proxy)
          }
          Thread.sleep(10000)

          while (Renderer.isRendering) {
            Thread.sleep(100)
          }
        }
    }.start()
  }

  private fun markAllFiles() {
    File(mod.recordingPath)
      .listFiles()
      ?.filter { it.extension == "rec" }
      ?.withIndex()?.forEach { (i, file) ->
        try {
          createMarkers(file)
        } catch (e: Exception) {
          println("Failed to generate markers for file: $file")
          e.printStackTrace()
        }
      }
  }

  override fun addOptions(host: ConfigPanelHost) {
    this.addControl(GuiButton(0, 0, 0, 65, 20, "Recordings")) {
      mc.displayGuiScreen(RecordingGui)
    }

    this.addControl(GuiButton(1, 70, 0, 65, 20, "Markers")) {
      mc.displayGuiScreen(MarkerGui)
    }

    this.addControl(GuiButton(1, 250, 0, 105, 20, "Render Blueprint Proxies")) {
      renderBlueprints(true)
    }

    this.addControl(GuiButton(1, 140, 0, 105, 20, "Render Blueprints")) {
      renderBlueprints(false)
    }

    this.addControl(GuiButton(1, 380, 0, 65, 20, "Generate")) {
      markAllFiles()
    }

    var y = 45
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Recording Path")
    this.recordingPath =
      this.addTextField(0, mc.fontRenderer.getStringWidth("Recording Path") + 10, y - 10, 300, 20)
    this.recordingPath.setMaxLength(1024)
    this.recordingPath.text = mod.recordingPath

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Final Render Path")
    this.finalRenderPath =
      this.addTextField(
        0,
        mc.fontRenderer.getStringWidth("Final Render Path") + 10,
        y - 10,
        300,
        20
      )
    this.finalRenderPath.setMaxLength(1024)
    this.finalRenderPath.text = mod.finalRenderPath

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "7-Zip Path")
    this.sevenZipPath =
      this.addTextField(1, mc.fontRenderer.getStringWidth("7-Zip Path") + 10, y - 10, 300, 20)
    this.sevenZipPath.setMaxLength(1024)
    this.sevenZipPath.text = mod.sevenZipPath

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Rendering Width")
    this.renderingWidth =
      this.addTextField(2, mc.fontRenderer.getStringWidth("Rendering Width") + 10, y - 10, 300, 20)
    this.renderingWidth.text = mod.renderingWidth.toString()

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Rendering Height")
    this.renderingHeight =
      this.addTextField(3, mc.fontRenderer.getStringWidth("Rendering Hieght") + 10, y - 10, 300, 20)
    this.renderingHeight.text = mod.renderingHeight.toString()

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Rendering Fps")
    this.renderingFps =
      this.addTextField(3, mc.fontRenderer.getStringWidth("Rendering Fps") + 10, y - 10, 300, 20)
    this.renderingFps.text = mod.renderingFps.toString()

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Blend Factor")
    this.blendFactor =
      this.addTextField(4, mc.fontRenderer.getStringWidth("Blend Factor") + 10, y - 10, 300, 20)
    this.blendFactor.text = mod.blendFactor.toString()

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Proxy Rendering Width")
    this.proxyRenderingWidth =
      this.addTextField(
        5,
        mc.fontRenderer.getStringWidth("Proxy Rendering Width") + 10,
        y - 10,
        300,
        20
      )
    this.proxyRenderingWidth.text = mod.proxyRenderingWidth.toString()

    y += 35
    this.addLabel(0, 0, y, 0, 0, 0xFFFFFF, "Proxy Rendering Height")
    this.proxyRenderingHeight =
      this.addTextField(
        6,
        mc.fontRenderer.getStringWidth("Proxy Rendering Height") + 10,
        y - 10,
        300,
        20
      )
    this.proxyRenderingHeight.text = mod.proxyRenderingHeight.toString()
  }
}
