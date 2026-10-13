package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer

public actual fun createAudioSink(): AudioSink = WebAudioSink()

/**
 * The browser: Web Audio, which has a clock of its own (`AudioContext.currentTime`) and starts a sound at an exact time on it. Browsers keep audio locked until the user touches the page,
 * so [unlock] must be called from a click, key press or tap; until then sounds are scheduled but not heard.
 */
internal class WebAudioSink : AudioSink {
    init { jsCreate() }

    private class Sound(override val startAt: Double, val handle: Int) : ScheduledSound {
        override fun cancel() = jsStop(handle)
    }

    private class Bed(val handle: Int, initial: Float) : LoopVoice {
        override var gain: Float = initial
            set(value) { field = value; jsSetGain(handle, value.toDouble()) }
        override fun stop() = jsStop(handle)
    }

    override fun now(): Double = jsNow()

    override fun play(buffer: PcmBuffer, gain: Float, pan: Float, startAt: Double): ScheduledSound =
        Sound(startAt, jsPlay(toBuffer(buffer), gain.toDouble(), pan.toDouble(), startAt))

    override fun loop(buffer: PcmBuffer, gain: Float): LoopVoice = Bed(jsLoop(toBuffer(buffer), gain.toDouble()), gain)

    override fun suspend() = jsSuspend()
    override fun resume() = jsResume()
    override fun unlock() = jsResume()
    override fun release() = jsClose()

    /** Copies the samples into a Float32Array once, then into an AudioBuffer. */
    private fun toBuffer(buffer: PcmBuffer): Int {
        val id = jsNewBuffer(buffer.samples.size)
        val samples = buffer.samples
        for (i in samples.indices) jsSet(id, i, samples[i])
        return jsSeal(id, buffer.sampleRate)
    }
}

private fun jsCreate(): Unit = js(
    """{
      const g = globalThis;
      if (!g.__boitata) {
        g.__boitata = { ctx: new (g.AudioContext || g.webkitAudioContext)({ latencyHint: 'interactive' }), pending: [], buffers: [], nodes: [] };
      }
    }""",
)

private fun jsNow(): Double = js("globalThis.__boitata.ctx.currentTime")

private fun jsNewBuffer(length: Int): Int = js("(globalThis.__boitata.pending.push(new Float32Array(length)), globalThis.__boitata.pending.length - 1)")

private fun jsSet(buffer: Int, index: Int, value: Float): Unit = js("{ globalThis.__boitata.pending[buffer][index] = value; }")

private fun jsSeal(buffer: Int, rate: Int): Int = js(
    """(function () {
      const a = globalThis.__boitata;
      const data = a.pending[buffer];
      const b = a.ctx.createBuffer(1, data.length, rate);
      b.copyToChannel(data, 0);
      a.pending[buffer] = null;
      a.buffers.push(b);
      return a.buffers.length - 1;
    })()""",
)

private fun jsPlay(buffer: Int, gain: Double, pan: Double, startAt: Double): Int = js(
    """(function () {
      const a = globalThis.__boitata, ctx = a.ctx;
      const src = ctx.createBufferSource(); src.buffer = a.buffers[buffer];
      const g = ctx.createGain(); g.gain.value = gain;
      src.connect(g);
      let out = g;
      if (ctx.createStereoPanner) { const p = ctx.createStereoPanner(); p.pan.value = pan; g.connect(p); out = p; }
      out.connect(ctx.destination);
      src.start(Math.max(startAt, ctx.currentTime));
      const handle = a.nodes.push({ src: src, gain: g }) - 1;
      src.onended = function () { a.nodes[handle] = null; };
      return handle;
    })()""",
)

private fun jsLoop(buffer: Int, gain: Double): Int = js(
    """(function () {
      const a = globalThis.__boitata, ctx = a.ctx;
      const src = ctx.createBufferSource(); src.buffer = a.buffers[buffer]; src.loop = true;
      const g = ctx.createGain(); g.gain.value = gain;
      src.connect(g); g.connect(ctx.destination);
      src.start();
      return a.nodes.push({ src: src, gain: g }) - 1;
    })()""",
)

/** A short ramp, so a change of level is never a click. */
private fun jsSetGain(handle: Int, gain: Double): Unit = js(
    """{
      const a = globalThis.__boitata, n = a.nodes[handle];
      if (n) n.gain.gain.setTargetAtTime(gain, a.ctx.currentTime, 0.05);
    }""",
)

private fun jsStop(handle: Int): Unit = js(
    """{
      const a = globalThis.__boitata, n = a.nodes[handle];
      if (n) { try { n.src.stop(); } catch (e) {} a.nodes[handle] = null; }
    }""",
)

private fun jsResume(): Unit = js("{ const a = globalThis.__boitata; if (a && a.ctx.state !== 'running') a.ctx.resume(); }")
private fun jsSuspend(): Unit = js("{ const a = globalThis.__boitata; if (a && a.ctx.state === 'running') a.ctx.suspend(); }")
private fun jsClose(): Unit = js("{ const a = globalThis.__boitata; if (a) { a.ctx.close(); globalThis.__boitata = null; } }")
