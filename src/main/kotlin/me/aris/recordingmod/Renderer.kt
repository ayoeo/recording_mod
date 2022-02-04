package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
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
import kotlin.system.measureNanoTime

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
  vB: Long
)

external fun sendFrame(useBufferB: Boolean)
external fun finishEncode()

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
  val yChannelA = MappedBuffer(size)
  private val uChannelA = MappedBuffer(size)
  private val vChannelA = MappedBuffer(size)

  private val yChannelB = MappedBuffer(size)
  private val uChannelB = MappedBuffer(size)
  private val vChannelB = MappedBuffer(size)

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

class RendererState(val file: String, val renderingFps: Int, val videoFps: Int) {
  val initialSystemTime = Minecraft.getSystemTime()
  var encodeThread: Thread? = null
  var partialFrames = 0
  var frameIndex = 0

  fun checkFinished() {
    val framesPerTick = renderingFps / 20.0
    if (this.frameIndex >= (Renderer.endTick - Renderer.startTick) * framesPerTick) {
      println("Done rendering: $frameIndex, $framesPerTick")
      Renderer.finishRender()
    }
  }

  private val bufferSize = (mc.displayWidth * mc.displayHeight).toLong()
  val yuvBuffers = DoubleBufferedChannels(bufferSize)
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

val convertProgram = run {
  val shader = loadComputeShader("convert.comp")
  val program = OpenGlHelper.glCreateProgram()
  OpenGlHelper.glAttachShader(program, shader)
  OpenGlHelper.glLinkProgram(program)

  program
}

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

  // TODO - set these with keybinds
  var startTick = 0
  var endTick = 0

  val isRendering: Boolean
    get() = this.rendererState != null

  private var rendererState: RendererState? = null

  fun startRender() {
    // Finish up just in case
    this.finishRender()
    activeReplay?.skipTo(this.startTick)

//    println("start encode: $startTick")
    // TODO - pass in more options determined by config (codec, frame size)
    val frameBlendScale = 1 // TODO - set this in options
    // TODO - not 600 or do the blend and stuff haha and the speed is weird?
    val state = RendererState("filement_woah.mp4", 60 * frameBlendScale, 60)
    this.rendererState = state

    val bufferaddr = state.yuvBuffers.yChannelA.data
//    println("bufferA: $bufferaddr")
//    println("size: ${mc.displayWidth} ${mc.displayHeight}")
    val pointers = state.yuvBuffers.yuvPointers()
    startEncode(
      state.file,
      mc.displayWidth, // TODO - broken with non 16:9???
      mc.displayHeight,
      state.videoFps,
      pointers[0],
      pointers[1],
      pointers[2],
      pointers[3],
      pointers[4],
      pointers[5]
    )
  }

  private fun printTimings(name: String, block: () -> Unit) {
    val time = measureNanoTime(block)
    println("$name: ${time / 1000000f}")
  }

  fun captureFrame() {
    val state = this.rendererState!!
//    println("capturing frame: ${state.frameIndex}")

    // this has to join before we start changing stuff?? hahahahah
//    println("Joining encode thread: ${state.frameIndex}")
    state.encodeThread?.join()

    // We need to know that our previous buffer is ready to go
    val shouldWrite = state.yuvBuffers.waitForFence()
    state.yuvBuffers.swap()

    //-------------------Compute shader stuff-------------------//
    OpenGlHelper.glUseProgram(convertProgram)

    // Use mc's framebuffer as the input image
    GlStateManager.setActiveTexture(GL.GL_TEXTURE0)
    GL42.glBindImageTexture(
      0,
      mc.framebuffer.framebufferTexture,
      0,
      false,
      0,
      GL.GL_READ_ONLY,
      GL.GL_RGBA8
    )
    OpenGlHelper.glUniform1i(0, 0)

    // Set YUV uniforms so we can get data : o
    state.yuvBuffers.bindBuffers()

    GL43.glDispatchCompute(
      ceil(mc.displayWidth / 4.0 / 32.0).toInt(),
      ceil(mc.displayHeight / 32.0).toInt(),
      1
    )
    state.yuvBuffers.makeFence()
    //-------------------Compute shader stuff-------------------//

    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah

    // TODO - stop buffer a from being written somehow
    if (shouldWrite) {
      state.encodeThread = Thread {
        // TODO
        //  startRender() -> set frame data to generated/mapped opengl buffers
        //  each frame in this thread -> call send_frame() and receive_packet()
        //  when those calls are done, the thread can join because we encoded a frame : o

//        println("Sending frame: ${state.frameIndex}")
//      sendFrame(!state.yuvBuffers.useBufferB) // write from the buffer not in use (inverted)
        sendFrame(!state.yuvBuffers.useBufferB) // write from the buffer not in use (inverted)
      }

      // We don't have data for the first frame, so... drop it hahahahhahahahhahhaha
//      println("Starting render frame thread: ${state.frameIndex}")
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
    }

    this.rendererState = null
  }

  fun pauseRender() {
    // TODO - this is just like normal pause...

    TODO("just stop advancing each frame")
  }

  fun unpauseRender() {
    // TODO - this is also just like normal pause...

    TODO("um... keep advancing each frame?")
  }

  fun setSystemTime() {
    val state = this.rendererState
    if (state != null) {
      val framesPerTick = state.renderingFps / 20
      // Time for shaders and such
      // TODO - this is broken...
      //  does weird stuff and glitches out...
      // TODO - turned off to monitor fps
      ReplayState.systemTime =
        state.initialSystemTime + ((state.frameIndex / framesPerTick.toFloat()) * 50).toLong()
    }
  }

  fun getTickData(): Pair<Int, Float> {
    val state = this.rendererState!!
    val framesPerTick = state.renderingFps / 20

    val elapsedTicks = if (state.partialFrames > framesPerTick) {
      state.partialFrames -= framesPerTick
      1
    } else {
      0
    }

    val partialFrames = state.partialFrames
    val partialTicks = partialFrames / framesPerTick.toFloat()

    return Pair(elapsedTicks, partialTicks)
  }
}
