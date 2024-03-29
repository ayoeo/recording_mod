package me.aris.recordingmod

import com.mumfrey.liteloader.core.LiteLoader
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel
import com.mumfrey.liteloader.modconfig.ConfigPanelHost
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiDownloadTerrain
import org.apache.commons.io.comparator.LastModifiedFileComparator
import java.io.File
import java.nio.charset.Charset

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
    mod.finalRenderPath = this.finalRenderPath.text
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
    mc.displayGuiScreen(GuiDownloadTerrain())

    Thread {
      File("blueprints").listFiles()
        ?.filter { it.extension == "bp" }
        ?.filter {  File("proxies", "${it.nameWithoutExtension}.mp4").exists()  }
        ?.filterNot {  File(mod.finalRenderPath, "${it.nameWithoutExtension}.mp4").exists()  }
        ?.forEach { file ->
          println("rendering blueprint ${file.name}")
          val splitthefileohgod = file.nameWithoutExtension.split('-')[0]
          val starttickdex = splitthefileohgod.split("..")[0].toInt()
          val endtickdex = splitthefileohgod.split("..")[1].toInt()
          val name = file.nameWithoutExtension.split('-')[1]
          val recFile = File(mod.recordingPath, "$name/$name.rec")

          // clean it up now
          val f = File(System.getProperty("java.io.tmpdir"), "uncompressed_recordings")
          f.mkdirs()

          val uncompressedRecording = File(f, name)
          if (!uncompressedRecording.exists()) {
            f.listFiles()?.forEach { it.delete() }
            val exename = File(mod.sevenZipPath).absolutePath
            val pb = ProcessBuilder(exename, "x", recFile.absolutePath)
            pb.directory(f)
            pb.redirectOutput()
            pb.redirectError()
            val proc = pb.start()
            val result = proc.waitFor()
            if (result != 0) {
              System.err.println("Could not decompress recording: ${recFile.name} ($result)")
              return@forEach
            }
          } else {
            println("Recording was already decompressed we're good")
          }

          println("rendering $name...")
          var started = false
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
            started = true
          }

          while (Renderer.isRendering || !started) {
            Thread.sleep(10)
          }
          println("Finished render...")
        }

      println("Finished all blueprint renders")
    }.start()
  }

  private fun markAllFiles() {
    File(mod.recordingPath)
      .listFiles()
      ?.filter { it.isDirectory }
      ?.forEach { file ->
        try {
          createMarkers(file)
        } catch (e: Exception) {
          println("Failed to generate markers for file: $file")
          e.printStackTrace()
        }
      }
  }

  private fun generateBlueprintsFromMarkers() {
    File("markers")
      .listFiles()
      ?.sortedWith(LastModifiedFileComparator())
      ?.withIndex()?.forEach { (i, file) ->
        val name = file.nameWithoutExtension
        val split = name.split('-')
        val recordingName = split.last()
        val xxx = file.readText(Charset.defaultCharset()).toInt()
        val start = xxx - 20 * 20
        val end = xxx + 20 * 5
        val blueprint =
          File("blueprints/$start..$end-${recordingName}.bp")
        blueprint.parentFile.mkdirs()
        blueprint.createNewFile()
      }
  }

  override fun addOptions(host: ConfigPanelHost) {
    var x = 0
    var y = 0
    this.addControl(GuiButton(0, x, y, 65, 20, "Recordings")) {
      mc.displayGuiScreen(RecordingGui)
    }

    x += 70
    this.addControl(GuiButton(1, x, y, 65, 20, "Markers")) {
      mc.displayGuiScreen(MarkerGui)
    }

    x = 0
    y += 25
    this.addControl(GuiButton(1, x, y, 105, 20, "Render Blueprints")) {
      renderBlueprints(false)
    }

    x += 110
    this.addControl(GuiButton(1, x, y, 145, 20, "Render Blueprint Proxies")) {
      renderBlueprints(true)
    }

    x = 0
    y += 25
    this.addControl(GuiButton(1, x, y, 65, 20, "Generate Markers")) {
      markAllFiles()
    }

    x += 70
    this.addControl(GuiButton(1, x, y, 180, 20, "Generate Blueprints From Markers")) {
      generateBlueprintsFromMarkers()
    }

    x = 0
    y += 45
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Recording Path")
    this.recordingPath =
      this.addTextField(0, mc.fontRenderer.getStringWidth("Recording Path") + 10, y - 10, 300, 20)
    this.recordingPath.setMaxLength(1024)
    this.recordingPath.text = mod.recordingPath

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Final Render Path")
    this.finalRenderPath = this.addTextField(
      0,
      mc.fontRenderer.getStringWidth("Final Render Path") + 10,
      y - 10,
      300,
      20
    )
    this.finalRenderPath.setMaxLength(1024)
    this.finalRenderPath.text = mod.finalRenderPath

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "7-Zip Path")
    this.sevenZipPath = this.addTextField(
      1, mc.fontRenderer.getStringWidth("7-Zip Path") + 10, y - 10, 300, 20
    )
    this.sevenZipPath.setMaxLength(1024)
    this.sevenZipPath.text = mod.sevenZipPath

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Rendering Width")
    this.renderingWidth =
      this.addTextField(
        2,
        mc.fontRenderer.getStringWidth("Rendering Width") + 10,
        y - 10,
        300,
        20
      )
    this.renderingWidth.text = mod.renderingWidth.toString()

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Rendering Height")
    this.renderingHeight =
      this.addTextField(
        3,
        mc.fontRenderer.getStringWidth("Rendering Hieght") + 10,
        y - 10,
        300,
        20
      )
    this.renderingHeight.text = mod.renderingHeight.toString()

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Rendering Fps")
    this.renderingFps =
      this.addTextField(3, mc.fontRenderer.getStringWidth("Rendering Fps") + 10, y - 10, 300, 20)
    this.renderingFps.text = mod.renderingFps.toString()

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Blend Factor")
    this.blendFactor =
      this.addTextField(4, mc.fontRenderer.getStringWidth("Blend Factor") + 10, y - 10, 300, 20)
    this.blendFactor.text = mod.blendFactor.toString()

    y += 35
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Proxy Rendering Width")
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
    this.addLabel(0, x, y, 0, 0, 0xFFFFFF, "Proxy Rendering Height")
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
