package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL.*
import me.aris.recordingmod.LiteModRecordingMod.Companion.mod
import me.aris.recordingmod.Renderer.endTick
import me.aris.recordingmod.mixins.MinecraftAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.shader.Framebuffer
import net.minecraft.client.util.JsonException
import net.minecraft.util.ResourceLocation
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import sun.nio.ch.DirectBuffer
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt

external fun startEncode(
  file: String,
  width: Int,
  height: Int,
  fps: Int,
  yA: Long,
  uA: Long,
  vA: Long,
  yB: Long,
  uB: Long,
  vB: Long,
  isProxy: Boolean
): Boolean

external fun sendFrame(useBufferB: Boolean): Boolean
external fun finishEncode()

sealed class GLUniform(val index: kotlin.Int) {
  class Float(index: kotlin.Int) : GLUniform(index) {
    fun set(value: kotlin.Float) {
      GL20.glUniform1f(this.index, value)
    }
  }

  class Int(index: kotlin.Int) : GLUniform(index) {
    fun set(x: kotlin.Int) {
      GL20.glUniform1i(this.index, x)
    }
  }
}

class MappedBuffer(bufferSize: Long) {
  private val name = OpenGlHelper.glGenBuffers()
  val data: ByteBuffer

  init {
    OpenGlHelper.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, this.name)
    GL44.glBufferStorage(
      GL43.GL_SHADER_STORAGE_BUFFER,
      bufferSize,
      GL30.GL_MAP_READ_BIT or GL44.GL_MAP_PERSISTENT_BIT or GL44.GL_MAP_COHERENT_BIT
    )

    this.data = GL30.glMapBufferRange(
      GL43.GL_SHADER_STORAGE_BUFFER,
      0,
      bufferSize,
      GL30.GL_MAP_READ_BIT,
      null
    )
  }

  fun bindBufferBase(index: Int) {
    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, this.name)
  }

  fun delete() {
    OpenGlHelper.glDeleteBuffers(name)
  }
}

class DoubleBufferedChannels(size: Long) {
  private val yChannelA = MappedBuffer(size)
  private val uChannelA = MappedBuffer(
    if (LiteModRecordingMod.mod.useYuv444) {
      size
    } else {
      size / 4
    }
  )
  private val vChannelA = MappedBuffer(
    if (LiteModRecordingMod.mod.useYuv444) {
      size
    } else {
      size / 4
    }
  )

  private val yChannelB = MappedBuffer(size)
  private val uChannelB = MappedBuffer(
    if (LiteModRecordingMod.mod.useYuv444) {
      size
    } else {
      size / 4
    }
  )
  private val vChannelB = MappedBuffer(
    if (LiteModRecordingMod.mod.useYuv444) {
      size
    } else {
      size / 4
    }
  )

  var useBufferB = false

  private var fence: GLSync? = null

  fun yuvPointers() = longArrayOf(
    (this.yChannelA.data as DirectBuffer).address(),
    (this.uChannelA.data as DirectBuffer).address(),
    (this.vChannelA.data as DirectBuffer).address(),
    (this.yChannelB.data as DirectBuffer).address(),
    (this.uChannelB.data as DirectBuffer).address(),
    (this.vChannelB.data as DirectBuffer).address()
  )

  fun swap() {
    this.useBufferB = !this.useBufferB
  }

  // Returns true if there's data prepared in this buffer
  // There won't be for the first frame drawn, so it returns false and we drop it who cares : )
  fun waitForFence(): Boolean {
    this.fence?.let { fence ->
      GL32.glClientWaitSync(fence, 0, Long.MAX_VALUE)
      GL32.glDeleteSync(fence)
      return true
    }

    return false
  }

  // run right after compute dispatch
  fun makeFence() {
    this.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
  }

  fun delete() {
    this.yChannelA.delete()
    this.uChannelA.delete()
    this.vChannelA.delete()

    this.yChannelB.delete()
    this.uChannelB.delete()
    this.vChannelB.delete()
    this.fence?.let { GL32.glDeleteSync(it) }
  }

  fun bindBuffers() {
    if (!this.useBufferB) {
      this.yChannelA.bindBufferBase(1)
      this.uChannelA.bindBufferBase(2)
      this.vChannelA.bindBufferBase(3)
    } else {
      this.yChannelB.bindBufferBase(1)
      this.uChannelB.bindBufferBase(2)
      this.vChannelB.bindBufferBase(3)
    }
  }
}

// TODO - the renderingFps is good
class RendererState(
  val proxy: Boolean,
  val file: String,
  maybeTheBlendFactorWeDontKnow: Int,
  val videoFps: Int
) {
  val blendFactor = if (proxy) {
    1
  } else {
    maybeTheBlendFactorWeDontKnow
  }

  val renderingFps = videoFps * blendFactor
  val initialSystemTime = Minecraft.getSystemTime()
  var encodeThread: Thread? = null
  var partialFrames = 0
  var frameIndex = 0

  fun checkFinished() {
    if (activeReplay!!.tickdex >= endTick) {
      System.currentTimeMillis()
      Renderer.finishRender()
      paused = true
    }
  }

  private val bufferSize = (mc.displayWidth * mc.displayHeight).toLong()
  val yuvBuffers = DoubleBufferedChannels(bufferSize)
  var lolWeDropTheFirstFrame = true
  val accumBuffer = MappedBuffer((mc.displayWidth * mc.displayHeight * 16).toLong())
}

fun loadComputeShader(file: String): Int {
  val resourcelocation = ResourceLocation("shaders/program/$file")
  val iresource = mc.resourceManager.getResource(resourcelocation)

  val abyte = IOUtils.toByteArray(BufferedInputStream(iresource.inputStream))
  val bytebuffer = BufferUtils.createByteBuffer(abyte.size)
  bytebuffer.put(abyte)
  bytebuffer.position(0)
  val i = OpenGlHelper.glCreateShader(GL43.GL_COMPUTE_SHADER)
  OpenGlHelper.glShaderSource(i, bytebuffer)
  OpenGlHelper.glCompileShader(i)

  if (OpenGlHelper.glGetShaderi(i, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
    val s = StringUtils.trim(OpenGlHelper.glGetShaderInfoLog(i, 32768))
    val jsonexception =
      JsonException("Couldn't compile compute shader: $file - $s")
    jsonexception.setFilenameAndFlush(resourcelocation.path)
    throw jsonexception
  }

  IOUtils.closeQuietly(iresource)

  return i
}

interface Shader {
  fun shader(): Int
}

val accumProgram = run {
  val shader = loadComputeShader("frame_accumulate.comp")
  val program = OpenGlHelper.glCreateProgram()
  OpenGlHelper.glAttachShader(program, shader)
  OpenGlHelper.glLinkProgram(program)

  program
}

val convertProgram = run {
  val shader = loadComputeShader("convert.comp")
  val program = OpenGlHelper.glCreateProgram()
  OpenGlHelper.glAttachShader(program, shader)
  OpenGlHelper.glLinkProgram(program)

  program
}

val frameBlendFramebuffer = Framebuffer(mc.displayWidth, mc.displayHeight, false)

val useYuv444Uniform = GLUniform.Float(
  OpenGlHelper.glGetUniformLocation(convertProgram, "use_yuv444")
)

val blendFactorUniform = GLUniform.Float(
  OpenGlHelper.glGetUniformLocation(accumProgram, "blendFactor")
)

val frameImageUniform = GLUniform.Int(
  OpenGlHelper.glGetUniformLocation(accumProgram, "frame")
)

val outFrameImageUniform = GLUniform.Int(
  OpenGlHelper.glGetUniformLocation(accumProgram, "out_frame")
)

val inputImageUniform = GLUniform.Int(
  OpenGlHelper.glGetUniformLocation(convertProgram, "input_image")
)

val firstFrameUniform = GLUniform.Int(
  OpenGlHelper.glGetUniformLocation(accumProgram, "firstFrame")
)

object Renderer {
  init {
//    val dll = File("librecording_mod_native.so")
    val os = System.getProperty("os.name").toLowerCase()
    if (os.contains("windows")) {
      val dll = File("recording_mod_native.dll")
      System.load(dll.absolutePath)
    } else {
      // idk mac stuff do later lol
    }
  }

  val lastSystemTimeField = run {
    try {
      val shadersClass = Class.forName("net.optifine.shaders.Shaders")
      val field = shadersClass.getDeclaredField("lastSystemTime")
      field.isAccessible = true
      field
    } catch (e: Exception) {
      // No optifine I guess L
      null
    }
  }

  val frameTimeField = run {
    try {
      val shadersClass = Class.forName("net.optifine.shaders.Shaders")
      val field = shadersClass.getDeclaredField("frameTime")
      field.isAccessible = true
      field
    } catch (e: Exception) {
      // No optifine I guess L
      null
    }
  }

  val frameTimeCounterField = run {
    try {
      val shadersClass = Class.forName("net.optifine.shaders.Shaders")
      val field = shadersClass.getDeclaredField("frameTimeCounter")
      field.isAccessible = true
      field
    } catch (e: Exception) {
      // No optifine I guess L
      null
    }
  }

  // TODO - set these with keybinds
  var startTick = 0
  var endTick = 0

  data class SloMoRegion(
    val range: IntRange,
    val slowMultiplier: Int
  )

  var sloMoRegions = mutableListOf<SloMoRegion>()

  var sloMoStartTick = 0
  var sloMoEndTick = 0

  val isRendering: Boolean
    get() = this.rendererState != null

  val isProxy: Boolean
    get() = this.rendererState?.proxy == true

  @Volatile
  private var rendererState: RendererState? = null

  fun startRender(proxy: Boolean) {
    // Finish up just in case
    this.finishRender()
    paused = false
//    if (proxy) {
    activeReplay?.skipTo(this.startTick)
//    } else {
//      activeReplay?.skipToRealSlow(this.startTick)
//    }

    // set it to the right size
    if (proxy) {
      (mc as MinecraftAccessor).invokeResize(
        LiteModRecordingMod.mod.proxyRenderingWidth,
        LiteModRecordingMod.mod.proxyRenderingHeight
      )
    } else {
      (mc as MinecraftAccessor).invokeResize(
        LiteModRecordingMod.mod.renderingWidth,
        LiteModRecordingMod.mod.renderingHeight
      )
    }
    frameBlendFramebuffer.createFramebuffer(mc.displayWidth, mc.displayHeight)
    val file = "$startTick-${activeReplay!!.replayFile.nameWithoutExtension}.mp4"
    val filePath = if (proxy) {
      "proxies/$file"
    } else {
      "${mod.finalRenderPath}/$file"
    }
    File(filePath).parentFile.mkdirs()
    println("Rendering $filePath...")

    if (proxy) {
      val blueprint =
        File("blueprints/$startTick..$endTick-${activeReplay!!.replayFile.nameWithoutExtension}.bp")
      blueprint.parentFile.mkdirs()
      blueprint.createNewFile()
      sloMoRegions.forEach { region ->
        blueprint.appendText("${region.range},${region.slowMultiplier}\r\n")
      }
    }

    val state = RendererState(
      proxy,
      filePath,
      LiteModRecordingMod.mod.blendFactor,
      LiteModRecordingMod.mod.renderingFps
    )
    this.rendererState = state

    val pointers = state.yuvBuffers.yuvPointers()
    if (!startEncode(
        state.file,
        mc.displayWidth, // TODO - broken with non 16:9??? - oh who cares
        mc.displayHeight,
        state.videoFps,
        pointers[0],
        pointers[1],
        pointers[2],
        pointers[3],
        pointers[4],
        pointers[5],
        isProxy
      )
    ) {
      println("Failed to start encoding")
      finishRender()
    }
  }

  fun captureFrame() {
    val state = this.rendererState!!

    OpenGlHelper.glUseProgram(accumProgram)
    if (state.frameIndex % state.blendFactor == 0) {
      firstFrameUniform.set(1)
    } else {
      firstFrameUniform.set(0)
    }
    frameImageUniform.set(0)
    blendFactorUniform.set(state.blendFactor.toFloat())

    GlStateManager.setActiveTexture(GL_TEXTURE0)
    GL42.glBindImageTexture(
      0,
      mc.framebuffer.framebufferTexture,
      0,
      false,
      0,
      GL_READ_ONLY,
      GL_RGBA8
    )
    state.accumBuffer.bindBufferBase(1)

    GL43.glDispatchCompute(
      ceil(mc.displayWidth / 32.0).toInt(),
      ceil(mc.displayHeight / 32.0).toInt(),
      1
    )

    if ((state.frameIndex + 1) % state.blendFactor != 0 && state.blendFactor > 1) {
      // meh
      state.frameIndex++
      state.partialFrames++
      return
    }

    // Compute shader moment
    OpenGlHelper.glUseProgram(accumProgram)
    firstFrameUniform.set(2)
    outFrameImageUniform.set(0)

    GlStateManager.setActiveTexture(GL_TEXTURE0)
    GL42.glBindImageTexture(
      0,
      frameBlendFramebuffer.framebufferTexture,
      0,
      false,
      0,
      GL_WRITE_ONLY,
      GL_RGBA8
    )
    state.accumBuffer.bindBufferBase(1)

    GL43.glDispatchCompute(
      ceil(mc.displayWidth / 32.0).toInt(),
      ceil(mc.displayHeight / 32.0).toInt(),
      1
    )
    GL11.glFinish()

    state.encodeThread?.join()
    val shouldWrite: Boolean = state.yuvBuffers.waitForFence()
    state.yuvBuffers.swap()

    //-------------------Compute shader stuff-------------------//
    OpenGlHelper.glUseProgram(convertProgram)
    useYuv444Uniform.set(if (LiteModRecordingMod.mod.useYuv444) 1f else 0f)
    inputImageUniform.set(0)

    // Use mc's framebuffer as the input image
    GlStateManager.setActiveTexture(GL_TEXTURE0)
    GL42.glBindImageTexture(
      0,
      frameBlendFramebuffer.framebufferTexture,
      0,
      false,
      0,
      GL_READ_ONLY,
      GL_RGBA8
    )

    // Set YUV uniforms so we can get data : o
    state.yuvBuffers.bindBuffers()

    GL43.glDispatchCompute(
      ceil(mc.displayWidth / 4.0 / 32.0).toInt(),
      ceil(mc.displayHeight / 32.0).toInt(),
      1
    )
    state.yuvBuffers.makeFence()
//    println("Shader part ${state.frameIndex}, $shaderPart")
    //-------------------Compute shader stuff-------------------//

    // TODO - stop buffer a from being written somehow
    if (shouldWrite) {
      state.encodeThread = Thread {
        if (state.lolWeDropTheFirstFrame) {
          state.lolWeDropTheFirstFrame = false
        } else {
          if (!sendFrame(!state.yuvBuffers.useBufferB)) {
            finishRender()
            return@Thread
          }
        }
      }

      // We don't have data for the first frame, so... drop it hahahahhahahahhahhaha
      rendererState?.encodeThread?.start()
    }

    state.frameIndex++
    state.partialFrames++
    state.checkFinished()
  }

  internal fun finishRender() {
    this.rendererState?.let { state ->
      state.encodeThread?.join()

      finishEncode()

      // Unmap / delete buffers
      // TODO - we need to finish writing the buffers here and stuff hahahahahaha (or just don't idc)
      state.yuvBuffers.delete()
      state.accumBuffer.delete()
    }

    this.rendererState = null
    (mc as MinecraftAccessor).invokeResize(Display.getWidth(), Display.getHeight())
//    if (isProxy) {
//    }
  }

  fun setSystemTime() {
    val state = this.rendererState
    if (state != null) {
      val framesPerTick = state.renderingFps / 20
      // Time for shaders and such
      ReplayState.systemTime =
        state.initialSystemTime + ((state.frameIndex / framesPerTick.toFloat()) * 50).toLong()
    }
  }

  fun `slowItDown?`(current: Pair<Int, Float>): Int? {
    return this.sloMoRegions.firstOrNull {
      current.first in it.range
    }?.slowMultiplier
  }

  var slowMultiplier = 1

  fun getTickData(): Pair<Int, Float> {
    val state = this.rendererState!!
    var framesPerTick = (state.renderingFps / 20) * slowMultiplier

    val elapsedTicks = if (state.partialFrames > framesPerTick) {
      state.partialFrames -= framesPerTick
      val slowMaybeMultiplier =
        `slowItDown?`(Pair(activeReplay!!.tickdex, state.partialFrames / framesPerTick.toFloat()))
      if (slowMaybeMultiplier != null) {
        slowMultiplier = slowMaybeMultiplier
        framesPerTick *= slowMultiplier
      } else {
        slowMultiplier = 1
      }
      1
    } else {
      0
    }

    val partialFrames = state.partialFrames
    val partialTicks = partialFrames / framesPerTick.toFloat()

    return Pair(elapsedTicks, partialTicks)
  }

  private fun renderTexturedRect(x: Int, y: Int, width: Int, height: Int) {
    glDisableDepthTest()
    glDisableLighting()
    glDisableBlend()
    glAlphaFunc(GL_GREATER, 0.01F)
    glEnableTexture2D()
    glColor4f(1.0F, 1.0F, 1.0F, 1.0F)
    val tess = Tessellator.getInstance()
    val renderer = tess.buffer
    renderer.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX)
    renderer.pos(x.toDouble(), (y + height).toDouble(), 0.0).tex(0.0, 0.0).endVertex()
    renderer.pos((x + width).toDouble(), (y + height).toDouble(), 0.0).tex(1.0, 0.0).endVertex()
    renderer.pos((x + width).toDouble(), y.toDouble(), 0.0).tex(1.0, 1.0).endVertex()
    renderer.pos(x.toDouble(), y.toDouble(), 0.0).tex(0.0, 1.0).endVertex()
    tess.draw()
  }

  fun drawOverlay() {
    GlStateManager.viewport(0, 0, Display.getWidth(), Display.getHeight())
    GlStateManager.clear(256)
    GlStateManager.matrixMode(5889)
    GlStateManager.loadIdentity()
    GlStateManager.ortho(
      0.0,
      Display.getWidth().toDouble(),
      Display.getHeight().toDouble(),
      0.0,
      0.0,
      1.0
    )
    GlStateManager.matrixMode(5888)
    GlStateManager.loadIdentity()
    GlStateManager.translate(0.0F, 0.0F, -1.0F)

    Gui.drawRect(0, 0, Display.getWidth(), Display.getHeight(), 0xFF111111.toInt())
    frameBlendFramebuffer.bindFramebufferTexture() // TODO - aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
//    mc.framebuffer.bindFramebufferTexture()
    val borderW = Display.getWidth() / 4
    val borderH = Display.getHeight() / 4

    // TODO - draw the blurred framebuffer instead of this one :)
    renderTexturedRect(
      borderW,
      borderH,
      Display.getWidth() - borderW * 2,
      Display.getHeight() - borderH * 2
    )
    frameBlendFramebuffer.unbindFramebufferTexture()
    glScaled(3.0, 3.0, 1.0)
    val renderingText = "Rendering"
    mc.fontRenderer.drawStringWithShadow(
      renderingText,
      ((Display.getWidth() / 2) / 3.0 - mc.fontRenderer.getStringWidth(renderingText) / 2).toFloat(),
      borderH / 3f - 12f,
      0xFFCCCCCC.toInt()
    )

    val framesPerTick = rendererState!!.renderingFps / 20.0
    val renderedFrames = rendererState!!.frameIndex
    val totalFrames = (endTick - startTick) * framesPerTick
    val progressText =
      "$renderedFrames / ${totalFrames.toInt()} (${((renderedFrames / totalFrames) * 100.0).roundToInt()}%)"
    mc.fontRenderer.drawStringWithShadow(
      progressText,
      ((Display.getWidth() / 2) / 3.0 - mc.fontRenderer.getStringWidth(progressText) / 2).toFloat(),
      (Display.getHeight() - borderH) / 3f + 3f,
      0xFFCCCCCC.toInt()
    )
  }

  fun makeSlowMoArea(mult: Int) {
    this.sloMoRegions.add(
      SloMoRegion(
        sloMoStartTick..sloMoEndTick,
        mult
      )
    )
  }
}
