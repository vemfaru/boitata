package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt

public actual fun createAudioSink(): AudioSink = JvmAudioSink()

/**
 * Desktop: one `SourceDataLine`, fed by a mixer thread that renders a short block at a time. The sink's clock is the frames the device has *played* (its own clock, not ours), and a
 * sound scheduled for a time begins on exactly that frame. With no audio device (a server, a test machine) it keeps time by the wall clock and makes no sound.
 */
internal class JvmAudioSink(openDevice: Boolean = true) : AudioSink {
    private val rate = PcmBuffer.SAMPLE_RATE
    private val mixer = SoftwareMixer(rate)
    private val lock = Object()
    private val line: SourceDataLine? = if (openDevice) openLine() else null
    private val wallStart = System.nanoTime()
    private var wallPausedAt: Long? = null          // with no device the clock is the wall clock, and it stops while suspended too
    private var wallPausedTotal = 0L
    @Volatile private var running = true
    @Volatile private var paused = false

    private val thread = Thread(::pump, "boitata-audio").apply { isDaemon = true; start() }

    private fun openLine(): SourceDataLine? = try {
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        AudioSystem.getSourceDataLine(format).also { it.open(format, BLOCK * 4 * 6); it.start() }
    } catch (e: Exception) {
        null
    }

    override fun now(): Double = line?.let { it.longFramePosition.toDouble() / rate }
        ?: synchronized(lock) { ((wallPausedAt ?: System.nanoTime()) - wallStart - wallPausedTotal) / 1e9 }

    override fun play(buffer: PcmBuffer, gain: Float, pan: Float, startAt: Double): ScheduledSound = synchronized(lock) {
        mixer.schedule(buffer, gain, pan, startAt, (startAt * rate).roundToInt().toLong())
    }

    override fun loop(buffer: PcmBuffer, gain: Float): LoopVoice = synchronized(lock) { mixer.loop(buffer, gain) }

    override fun suspend() {
        paused = true
        line?.stop()
        synchronized(lock) { if (line == null && wallPausedAt == null) wallPausedAt = System.nanoTime() }
    }

    override fun resume() {
        paused = false
        line?.start()
        synchronized(lock) {
            wallPausedAt?.let { wallPausedTotal += System.nanoTime() - it }
            wallPausedAt = null
            lock.notifyAll()
        }
    }

    override fun unlock() {}

    override fun release() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        line?.stop(); line?.close()
    }

    private fun pump() {
        val floats = FloatArray(BLOCK * 2)
        val bytes = ByteArray(BLOCK * 4)
        while (running) {
            if (paused) {
                synchronized(lock) { while (paused && running) lock.wait() }
                continue
            }
            synchronized(lock) { mixer.render(floats, BLOCK) }
            if (line == null) { Thread.sleep(BLOCK * 1000L / rate); continue }
            for (i in 0 until BLOCK * 2) {
                val v = (floats[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
                bytes[i * 2] = (v and 0xFF).toByte()
                bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            line.write(bytes, 0, bytes.size)          // blocks until the device wants more: this is what paces the thread
        }
    }

    private companion object {
        const val BLOCK = 512
    }
}
