package br.com.faru.boitata.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import br.com.faru.boitata.audio.AudioSink
import br.com.faru.boitata.audio.BoitataAudio
import br.com.faru.boitata.audio.createAudioSink
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.sky.StormModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.clock.BoitataScene
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.wind.Wind

/** The demos, in the order of the tabs. */
enum class Demo(val label: String) { Forest("Boitatá"), Weather("Weather"), Window("Window light"), Shadows("Shadows"), Depth("Depth"), Lights("Lights"), Stage("Light stage"), Burn("Burn"), Fire("Fire") }

/** The whole playground: pick a demo, play with its controls. The same scenes are what the README's GIFs are rendered from. */
@Composable
fun PlaygroundApp() {
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0B0D10), surface = Color(0xFF14181D), primary = Color(0xFFF2A83B))) {
        var demo by remember { mutableStateOf(Demo.Forest) }
        var sound by remember { mutableStateOf(false) }
        // The wind and the moon are the whole playground's: set them once and every scene that has wind or a moon follows.
        var wind by remember { mutableStateOf(Wind.Breeze) }
        var moon by remember { mutableStateOf(MoonPhase.Full) }
        var southern by remember { mutableStateOf(true) }
        val sink = remember { createAudioSink() }
        DisposableEffect(sink) { onDispose { sink.release() } }
        BoitataScene(Modifier.fillMaxSize().background(Color(0xFF0B0D10)), wind = wind) {
        val seconds = rememberSceneSeconds()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in Demo.entries) FilterChip(selected = d == demo, onClick = { demo = d }, label = { Text(d.label) })
                // The browser keeps sound locked until a tap: turning it on is that tap.
                FilterChip(selected = sound, onClick = { sound = !sound; if (sound) sink.unlock() }, label = { Text(if (sound) "Sound on" else "Sound off") })
            }
            WindControls(wind) { wind = it }
            MoonControls(moon, southern, onPhase = { moon = it }, onHemisphere = { southern = it })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ProvideSprites { sprites ->
                CompositionLocalProvider(LocalSprites provides sprites) {
                when (demo) {
                    Demo.Forest -> ForestDemo(seconds, sink, sound, moon, southern)
                    Demo.Weather -> WeatherDemo(seconds, sink, sound, moon, southern)
                    Demo.Window -> WindowDemo(seconds, moon, southern)
                    Demo.Shadows -> ShadowDemo(seconds)
                    Demo.Depth -> DepthDemo(seconds)
                    Demo.Lights -> LightsDemo(seconds, moon, southern)
                    Demo.Stage -> LightStageDemo(seconds)
                    Demo.Burn -> BurnDemo(seconds, sink, sound)
                    Demo.Fire -> FireDemo(seconds, sink, sound)
                }
                }
                }
            }
        }
        }
    }
}

/** The wind for the whole playground: a few named winds, and sliders for the strength, the direction and how gusty it is. */
@Composable
private fun WindControls(wind: Wind, onChange: (Wind) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Wind", color = Color(0xFFB7BEC8))
            for ((name, preset) in listOf("Calm" to Wind.Calm, "Breeze" to Wind.Breeze, "Windy" to Wind.Windy, "Gale" to Wind.Gale)) {
                FilterChip(selected = wind.speed == preset.speed && wind.gustiness == preset.gustiness, onClick = { onChange(preset.copy(direction = if (wind.direction == 0f) 1f else wind.direction)) }, label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("Speed ${(wind.speed * 100).toInt()}%", color = Color(0xFFB7BEC8)); Slider(wind.speed, { onChange(wind.copy(speed = it)) }, valueRange = 0f..1f) }
            Column(Modifier.weight(1f)) { Text(if (wind.direction < -0.05f) "Blowing left" else if (wind.direction > 0.05f) "Blowing right" else "No direction", color = Color(0xFFB7BEC8)); Slider(wind.direction, { onChange(wind.copy(direction = it)) }, valueRange = -1f..1f) }
            Column(Modifier.weight(1f)) { Text("Gusts ${(wind.gustiness * 100).toInt()}%", color = Color(0xFFB7BEC8)); Slider(wind.gustiness, { onChange(wind.copy(gustiness = it)) }, valueRange = 0f..1f) }
        }
    }
}

/** Which moon, and from which hemisphere: it sets which side is lit. */
@Composable
private fun MoonControls(phase: MoonPhase, southern: Boolean, onPhase: (MoonPhase) -> Unit, onHemisphere: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Moon", color = Color(0xFFB7BEC8))
        for (p in MoonPhase.All) FilterChip(selected = p === phase, onClick = { onPhase(p) }, label = { Text(p.label) })
        FilterChip(selected = southern, onClick = { onHemisphere(!southern) }, label = { Text(if (southern) "Southern sky" else "Northern sky") })
    }
}

@Composable
private fun Controls(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xCC0B0D10)).padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
}

@Composable
private fun Labelled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp).fillMaxWidth(0.22f), color = Color(0xFFB7BEC8))
        Slider(value, onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ForestDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean, moon: MoonPhase, southern: Boolean) {
    var rain by remember { mutableFloatStateOf(0f) }
    var fog by remember { mutableFloatStateOf(0.35f) }
    var hunter by remember { mutableStateOf(true) }
    var wildlife by remember { mutableStateOf(true) }
    var resetKey by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ForestScene(seconds, rain = rain, fog = fog, moon = moon, southern = southern, hunter = hunter, wildlife = wildlife, resetKey = resetKey) }
        // The serpent is a fire: its crackle is always there; the rain follows the slider, the mist muffles.
        BoitataAudio(sink, weather = SkyWeather(cloud = 0.2f + 0.4f * rain, precipitation = rain, fog = fog * 0.5f), fire = 0.7f, enabled = sound)
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = hunter, onClick = { hunter = !hunter }, label = { Text("Hunter") })
                FilterChip(selected = wildlife, onClick = { wildlife = !wildlife }, label = { Text("Wildlife") })
                FilterChip(selected = false, onClick = { resetKey++ }, label = { Text("Start again") })
                Text("The serpent's head is a fire that never burns down, not even in the rain. When it passes close, the hunter (flammable) catches and chars. The animals are not flammable and are never touched.", color = Color(0xFFB7BEC8))
            }
            Labelled("Rain", rain, 0f..1f) { rain = it }; Labelled("Mist", fog, 0f..1f) { fog = it }
        }
    }
}

@Composable
private fun WeatherDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(14f) }
    var kind by remember { mutableStateOf(WeatherKind.Rain) }
    val storm = remember { StormModel(seed = 17L, intensity = 1f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { WeatherScene(seconds, hour, kind, stormModel = storm, moon = moon, southern = southern) }
        // The thunder comes from the same storm as the lightning drawn above, so each flash is followed by its sound, distance over the speed of sound later.
        BoitataAudio(sink, storm = if (kind == WeatherKind.Storm) storm else null, weather = kind.skyWeather(), enabled = sound)
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in WeatherKind.entries) FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
            }
            Labelled("Hour", hour, 0f..24f) { hour = it }
        }
    }
}

@Composable
private fun WindowDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(16.5f) }
    var cloud by remember { mutableFloatStateOf(0f) }
    var open by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { WindowScene(seconds, hour, cloud, moon = moon, southern = southern, windowOpen = open) }
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = open, onClick = { open = !open }, label = { Text(if (open) "Window open" else "Window shut") })
                Text("Wind reaches the plant only through the window: shut it and the plant, and the dust in the light, go still.", color = Color(0xFFB7BEC8))
            }
            Labelled("Hour", hour, 0f..24f) { hour = it }; Labelled("Cloud", cloud, 0f..1f) { cloud = it }
        }
    }
}

@Composable
private fun ShadowDemo(seconds: () -> Float) {
    var light by remember { mutableStateOf(Offset(0.4f, 0.45f)) }
    var reach by remember { mutableFloatStateOf(0.8f) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).pointerInput(Unit) {
                detectDragGestures(onDragStart = { light = Offset(it.x / size.width, it.y / size.height) }) { change, _ ->
                    light = Offset((change.position.x / size.width).coerceIn(0.02f, 0.98f), (change.position.y / size.height).coerceIn(0.05f, 0.95f))
                }
            },
        ) { ShadowScene({ light }, reach, seconds = seconds) }
        Controls { Text("Drag the light", color = Color(0xFFB7BEC8)); Labelled("Reach", reach, 0.3f..1.4f) { reach = it } }
    }
}

@Composable
private fun DepthDemo(seconds: () -> Float) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { DepthScene(seconds) }
        Controls { Text("Drag anything. Up sends it back, small and tucked behind what is nearer; down brings it forward, bigger. Lampião's lamp is the light: carry him about and every shadow swings. Tap a thing to switch whether the wind moves it (palms, plants and the pine do by default).", color = Color(0xFFB7BEC8)) }
    }
}

@Composable
private fun LightsDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(19f) }
    var on by remember { mutableStateOf(LightKind.entries.toSet()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LightsScene(seconds, hour = hour, moon = moon, southern = southern, on = on) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in LightKind.entries) FilterChip(selected = k in on, onClick = { on = if (k in on) on - k else on + k }, label = { Text(k.label) })
            }
            Labelled("Hour", hour, 0f..24f) { hour = it }
            Text("Each light throws its own shadow, its own way: the sun and moon (far away) in parallel, long at dawn; the fire, candle and lamp (near) fanning out from them; the lantern only inside its cone. Drag the fire, the candle, the lantern post or Lampião (he carries the lamp) and watch every shadow swing. The animals wander on their own and cannot burn.", color = Color(0xFFB7BEC8))
        }
    }
}

@Composable
private fun LightStageDemo(seconds: () -> Float) {
    var on by remember { mutableStateOf(StageLamp.entries.toSet()) }
    var height by remember { mutableFloatStateOf(0.65f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LightStageScene(seconds, on = on, heightScale = height) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in StageLamp.entries) FilterChip(selected = l in on, onClick = { on = if (l in on) on - l else on + l }, label = { Text(l.label, color = l.color) })
            }
            Labelled("Lamp height", height, 0.4f..2f) { height = it }
            Text("Four lamps light the stage from four places at once. Drag a lamp (a glowing orb) and its shadows swing while the others stay; drag a person across and their shadows turn as they pass between the lamps. Lower the lamps for long shadows.", color = Color(0xFFB7BEC8))
        }
    }
}

@Composable
private fun BurnDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean) {
    var rain by remember { mutableFloatStateOf(0f) }
    var guardian by remember { mutableStateOf(false) }
    var resetKey by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { BurnScene(seconds, rain = rain, guardian = guardian, resetKey = resetKey, sink = sink, sound = sound) }
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = guardian, onClick = { guardian = !guardian }, label = { Text("Boitatá's fire") })
                FilterChip(selected = false, onClick = { resetKey++ }, label = { Text("Start again") })
                Text("Drag things to the fire. Downwind they warm, smoke, catch and char; upwind they stay safe. Dry grass first, a pine last. The Boitatá's fire spares every plant and puts out ordinary fires.", color = Color(0xFFB7BEC8))
            }
            Labelled("Rain", rain, 0f..1f) { rain = it }
        }
    }
}

@Composable
private fun FireDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean) {
    var intensity by remember { mutableFloatStateOf(0.9f) }
    var kind by remember { mutableStateOf(FireKind.Campfire) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { FireScene(seconds, intensity, kind) }
        BoitataAudio(sink, fire = intensity, enabled = sound)
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (k in FireKind.entries) FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) }) }
            Labelled("Fire", intensity, 0f..1f) { intensity = it }
        }
    }
}
