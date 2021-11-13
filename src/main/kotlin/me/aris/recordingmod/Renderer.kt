package me.aris.recordingmod

import com.mumfrey.liteloader.gl.GL
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase.setFlag
import io.humble.ferry.Buffer
import io.humble.ferry.RefCounted
import io.humble.video.*
import io.humble.video.awt.MediaPictureConverter
import io.humble.video.awt.MediaPictureConverterFactory
import io.humble.video.awt.MediaPictureConverterFactory.convertToType
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureUtil
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBGetTextureSubImage
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL45
import java.awt.Point
import java.awt.image.*
import java.nio.IntBuffer
import kotlin.system.measureNanoTime

//val width: Int
//  get() = mc.displayWidth
//
//val height: Int
//  get() = mc.displayHeight

private val PIXEL_FORMAT = PixelFormat.Type.PIX_FMT_BGR0
//PixelFormat.Type.PIX_FMT_YUV444P

class RendererState(file: String, val fps: Int) {
  private val framerate: Rational = Rational.make(1, fps)

  var partialFrames = 0
  var frameIndex = 0

  fun checkFinished() {
    val framesPerTick = fps / 20.0
    if (this.frameIndex >= (Renderer.endTick - Renderer.startTick) * framesPerTick) {
      println("Done rendering: $frameIndex, $framesPerTick")
      Renderer.finishRender()
    }
  }

  val muxer: Muxer = Muxer.make(file, null, null)
  private val format: MuxerFormat = muxer.format

  //  private val codec: Codec = Codec.findEncodingCodecByName("libx265")
  private val codec: Codec = Codec.findEncodingCodecByName("libx264rgb")
//  private val codec: Codec = Codec.findEncodingCodecByName("nvenc")

  val pixelBuffer: IntBuffer = BufferUtils.createIntBuffer(mc.displayWidth * mc.displayHeight)
  val pixelValues = IntArray(mc.displayWidth * mc.displayHeight)

  val encoder: Encoder = Encoder.make(codec).apply {
    width = mc.displayWidth
    height = mc.displayHeight
    pixelFormat = PIXEL_FORMAT
    timeBase = framerate

//    for (i in 0 until numProperties) {
//      println("Prop: ${getPropertyMetaData(i).name} ${getPropertyMetaData(i).name}")
//    }
//    setProperty("v", "0")
    setProperty("crf", 25)
    setProperty("preset", "ultrafast")
//    setProperty("an", "")
//    setFlag(Coder.Flag.)
//
    if (format.getFlag(ContainerFormat.Flag.GLOBAL_HEADER)) {
      setFlag(Coder.Flag.FLAG_GLOBAL_HEADER, true)
    }
  }

  var picture: MediaPicture = MediaPicture.make(
    encoder.width,
    encoder.height,
    PIXEL_FORMAT
  ).apply {
    timeBase = framerate
  }

  init {
    encoder.open(null, null)
    muxer.addNewStream(encoder)
    muxer.open(null, null)
  }

}

object Renderer {
  // TODO - set these with keybinds
  var startTick = 300
  var endTick = 360

  val isRendering: Boolean
    get() = this.rendererState != null

  private var rendererState: RendererState? = null
  private var converter: MediaPictureConverter? = null

  fun startRender() {
//    Codec.getInstalledCodecs().forEach { c ->
//      println("oh cool: $c")
//    }

    this.converter = null
    this.rendererState = RendererState("filement_woah.mp4", 1200)

    activeReplay?.skipTo(this.startTick)
    // TODO - set partial frame ticks or whatever to 0 nada 0 nothing
  }

  fun printTimings(name: String, block: () -> Unit) {
    val time = measureNanoTime(block)
    println("$name: ${time / 1000000f}")
  }

  fun captureFrame() {
    GlStateManager.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1)
    GlStateManager.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1)

    this.rendererState?.let { state ->
      state.pixelBuffer.clear()

      val captureFb = measureNanoTime {
        printTimings("getTexImage") {
          GlStateManager.glReadPixels(
            0,
            0,
            mc.displayWidth,
            mc.displayHeight,
            GL.GL_BGRA,
            GL.GL_UNSIGNED_INT_8_8_8_8_REV,
            state.pixelBuffer
          )
        }

        state.pixelBuffer.get(state.pixelValues)
        TextureUtil.processPixelValues(state.pixelValues, mc.displayWidth, mc.displayHeight)
      }
      println("Capture FB: ${captureFb / 1000000f}")

      val picture = measureNanoTime {
        val thatsnowyouspellpicture = state.picture.getData(0)
        thatsnowyouspellpicture.put(state.pixelValues, 0, 0, state.pixelValues.size)
        state.picture.timeStamp = state.frameIndex.toLong()
        state.picture.isComplete = true
      }
      println("Picture: ${picture / 1000000f}")

      val encode = measureNanoTime {
//        do {
        println("encode")
        val packet = MediaPacket.make()
        state.encoder.encode(packet, state.picture)
        if (packet.isComplete) {
          state.muxer.write(packet, false)
        }
//          }
//        } while (state.packet.isComplete)
      }
      println("Encode: ${encode / 1000000f}")
    }

    rendererState?.let {
      it.frameIndex++
      it.partialFrames++
      it.checkFinished()
    }
  }

  internal fun finishRender() {
    this.rendererState?.let { state ->
      val packet = MediaPacket.make()
      do {
        state.encoder.encode(packet, null)
        if (packet.isComplete) {
          state.muxer.write(packet, false)
        }
      } while (packet.isComplete)
      state.muxer.close()
    }

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
