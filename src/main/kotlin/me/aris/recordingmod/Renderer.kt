package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureUtil
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import sun.misc.Unsafe
import java.io.File
import java.nio.IntBuffer
import kotlin.system.measureNanoTime

private const val PIXEL_FORMAT = "yuv420p"
private const val CODEC = "libx264"

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
      "0bgr",
      "-s",
      "${mc.displayWidth}x${mc.displayHeight}",
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
      "high",
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


  val pixelBuffer1: IntBuffer = BufferUtils.createIntBuffer(mc.displayWidth * mc.displayHeight)
  val pixelBuffer2: IntBuffer = BufferUtils.createIntBuffer(mc.displayWidth * mc.displayHeight)
  var useBuffer2 = false
  val pixelValues = IntArray(mc.displayWidth * mc.displayHeight)
  val rawBytes = ByteArray(pixelValues.size * 4)
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
    f.isAccessible = true;
    f.get(null) as Unsafe
  }

  fun startRender() {
    // TODO - pass in more options determined by config (codec, frame size)
    this.rendererState = RendererState("filement_woah.mp4", 1200)

    activeReplay?.skipTo(this.startTick)
  }

  fun captureFrame() {
    GL11.glFinish()

    this.rendererState?.let { state ->
      val pixelBufferInUse = if (state.useBuffer2) state.pixelBuffer2 else state.pixelBuffer1
      state.useBuffer2 = !state.useBuffer2

      pixelBufferInUse.clear()
      val captureFb = measureNanoTime {
        mc.framebuffer.bindFramebuffer(false)
        GlStateManager.glReadPixels(
          0,
          0,
          mc.displayWidth,
          mc.displayHeight,
          GL.GL_RGBA,
          GL.GL_UNSIGNED_INT_8_8_8_8,
          pixelBufferInUse
        )
      }
      println("Capture FB: ${captureFb / 1000000f}")

      val encode = measureNanoTime {
        state.pipeThread?.join()
        state.pipeThread = Thread {
          (if (state.useBuffer2) state.pixelBuffer2 else state.pixelBuffer1).clear()
          pixelBufferInUse.get(state.pixelValues)
          TextureUtil.processPixelValues(state.pixelValues, mc.displayWidth, mc.displayHeight)

          val output = state.ffmpegInput
          unsafe.copyMemory(
            state.pixelValues,
            unsafe.arrayBaseOffset(IntArray::class.java).toLong(),
            state.rawBytes,
            unsafe.arrayBaseOffset(ByteArray::class.java).toLong(),
            state.rawBytes.size.toLong()
          )
          output.write(state.rawBytes)
          output.flush()
        }
        state.pipeThread!!.start()
      }
      println("Encode: ${encode / 1000000f}")

      state.frameIndex++
      state.partialFrames++
      state.checkFinished()
    }
  }

  internal fun finishRender() {
    this.rendererState?.let { state ->
      state.pipeThread?.join()
      state.ffmpegInput.close()
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

    // Time for shaders and such
    ReplayState.systemTime =
      state.initialSystemTime + ((state.frameIndex / state.fps.toFloat()) * 50).toLong()

    val framesPerTick = state.fps / 20

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
