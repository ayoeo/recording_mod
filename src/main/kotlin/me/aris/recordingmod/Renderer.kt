package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL
import io.netty.buffer.ByteBuf
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.resources.IResource
import net.minecraft.client.shader.ShaderLoader
import net.minecraft.client.util.JsonException
import net.minecraft.util.ResourceLocation
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.math.ceil
import kotlin.system.measureNanoTime

private const val PIXEL_FORMAT = "yuv444p"
private const val CODEC = "libx264"

fun createBuffer(size: Long): Pair<ByteBuffer, Int> {
  val bufferId = OpenGlHelper.glGenBuffers()
  OpenGlHelper.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferId)
  GL44.glBufferStorage(
    GL43.GL_SHADER_STORAGE_BUFFER,
    size,
    GL30.GL_MAP_READ_BIT or GL44.GL_MAP_PERSISTENT_BIT or GL44.GL_MAP_COHERENT_BIT
  )

  return Pair(
    GL30.glMapBufferRange(
      GL43.GL_SHADER_STORAGE_BUFFER,
      0,
      size,
      GL30.GL_MAP_READ_BIT,
      null
    ),
    bufferId
  )
}

class RendererState(file: String, val fps: Int) {
  var pipeThread: Thread? = null
  var ffmpegInput = run {
    val pb = ProcessBuilder(
      "ffmpeg", // TODO - .exe?
//      "/usr/local/bin/ffmpeg", // TODO - this for mac haha (set path)
      "-nostdin",
      "-r",
      "$fps",
      "-f",
      "rawvideo",
      "-pixel_format",
//      "0bgr",
      "yuv444p",
      "-s",
      "${mc.displayWidth}x${mc.displayHeight}",
//      "500x500",
      "-i",
      "pipe:0",
      "-r",
      "$fps",
      "-pix_fmt",
      PIXEL_FORMAT,
      "-y",
      "-c:v",
      CODEC,
      "-preset:v",
      "ultrafast",
      "-rc:v",
      "vbr",
      "-b:v",
      "0",
      "-crf:v",
      "13",
      "-profile:v",
      "high444",
      "-vsync",
      "2",
      file
    )
    pb.redirectError(File("help.txt"))
    val proc = pb.start()
    proc.outputStream
  }
  var partialFrames = 0
  var frameIndex = 0
  var initialSystemTime = ReplayState.lastSystemTime

  fun checkFinished() {
    val framesPerTick = fps / 20.0
    if (this.frameIndex >= (Renderer.endTick - Renderer.startTick) * framesPerTick) {
      println("Done rendering: $frameIndex, $framesPerTick")
      Renderer.finishRender()
    }
  }

  val bufferSize = (mc.displayWidth * mc.displayHeight).toLong()
  val littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut =
    ByteArray(bufferSize.toInt())
  val yChannel = createBuffer(bufferSize)
  val uChannel = createBuffer(bufferSize)
  val vChannel = createBuffer(bufferSize)
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
  // TODO - set these with keybinds
  var startTick = 0
  var endTick = 0

  val isRendering: Boolean
    get() = this.rendererState != null

  private var rendererState: RendererState? = null
  private val unsafe = run {
    val f = Unsafe::class.java.getDeclaredField("theUnsafe")
    f.isAccessible = true
    f.get(null) as Unsafe
  }

  fun startRender() {
    // TODO - pass in more options determined by config (codec, frame size)
    this.rendererState = RendererState("filement_woah.mp4", 1200)

    activeReplay?.skipTo(this.startTick)
  }

  private fun printTimings(name: String, block: () -> Unit) {
    val time = measureNanoTime(block)
    println("$name: ${time / 1000000f}")
  }


  fun captureFrame() {
    val state = this.rendererState!!

    // this has to join before we start changing stuff?? hahahahah
    state.pipeThread?.join()

    // /ahaha
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
    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, state.yChannel.second)
    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, state.uChannel.second)
    GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, state.vChannel.second)

    GL43.glDispatchCompute(
      ceil(mc.displayWidth / 4.0 / 32.0).toInt(),
      ceil(mc.displayHeight / 32.0).toInt(),
      1
    )

    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    // TODO - USE THE OTHER THING NOT GLFINISH IT'S LIKE IT BUT FASTER IDK
    GL11.glFinish()
    // or this is fine we're finishing anyway hahahaha (end of frame)
    // TODO -see how fast it is compared to normal mc if we just do this and don't
    //   send the data to other stuff to ffmpeg at all ??? : ) )

    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah
    // TODO - prevent resizing really tho hhahahahahaheheah

    state.pipeThread = Thread {
      val output = state.ffmpegInput
      // TODO - this should directly copy memory to the rust pointer
      unsafe.copyMemory(
        null,
        (state.yChannel.first as DirectBuffer).address(),
        state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut,
        unsafe.arrayBaseOffset(ByteArray::class.java).toLong(),
        state.bufferSize
      )
      output.write(state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut)

      unsafe.copyMemory(
        null,
        (state.uChannel.first as DirectBuffer).address(),
        state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut,
        unsafe.arrayBaseOffset(ByteArray::class.java).toLong(),
        state.bufferSize
      )
      output.write(state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut)

      unsafe.copyMemory(
        null,
        (state.vChannel.first as DirectBuffer).address(),
        state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut,
        unsafe.arrayBaseOffset(ByteArray::class.java).toLong(),
        state.bufferSize
      )
      output.write(state.littleByteBufferOfBytesToHoldItBecauseImStuckOnTheJvmAndCantGetOut)
      output.flush()
    }
    state.pipeThread!!.start()
//    println("Encode: ${encode / 1000000f}")

    state.frameIndex++
    state.partialFrames++
    state.checkFinished()
  }

  internal fun finishRender() {
    this.rendererState?.let { state ->
      state.pipeThread?.join()
      state.ffmpegInput.close()

      // Unmap / delete buffers
      OpenGlHelper.glDeleteBuffers(state.yChannel.second)
      OpenGlHelper.glDeleteBuffers(state.uChannel.second)
      OpenGlHelper.glDeleteBuffers(state.vChannel.second)
    }
    ReplayState.systemTime = null

    this.rendererState = null
  }

  fun cancelRender() {
    TODO("Finish and then delete the file? lol")
  }

  fun pauseRender() {
    TODO("just stop advancing each frame")
  }

  fun unpauseRender() {
    TODO("um... keep advancing each frame?")
  }

  fun getTickData(): Pair<Int, Float> {
    val state = this.rendererState!!

    val framesPerTick = state.fps / 20

    // Time for shaders and such
//    ReplayState.systemTime =
//      state.initialSystemTime + ((state.frameIndex / framesPerTick.toFloat()) * 50).toLong()
    // TODO - turned off to monitor fps


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
