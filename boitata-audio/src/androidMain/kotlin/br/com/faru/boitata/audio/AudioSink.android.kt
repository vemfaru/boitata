package br.com.faru.boitata.audio

/** Android's engine comes in the next step; until then a silent sink that keeps time. */
public actual fun createAudioSink(): AudioSink = SilentAudioSink()
