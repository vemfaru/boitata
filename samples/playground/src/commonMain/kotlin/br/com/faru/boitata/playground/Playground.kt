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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.clock.BoitataScene
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.surface.PaneWeather
import br.com.faru.boitata.core.surface.SurfaceClimate
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.stain.SurfaceMaterial
import br.com.faru.boitata.core.sip.Drinker
import br.com.faru.boitata.core.sky.DayClock
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.rememberSmoothMoon
import br.com.faru.boitata.plants.rememberRunningDay
import br.com.faru.boitata.core.wind.Wind

/** The demos, in the order of the tabs. */
enum class Demo(val label: String) { Story("O Fogo que Guarda"), Forest("Boitatá"), Weather("Weather"), Garden("Garden"), Window("Window light"), Shadows("Shadows"), Depth("Depth"), Lights("Lights"), Stage("Light stage"), Theatre("Shadow theatre"), Pond("Pond"), Liquids("Liquids"), Burn("Burn"), Fire("Fire") }

/** The whole playground: pick a demo, play with its controls. The same scenes are what the README's GIFs are rendered from. */
@Composable
fun PlaygroundApp() {
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0B0D10), surface = Color(0xFF14181D), primary = Color(0xFFF2A83B))) {
        var demo by remember { mutableStateOf(Demo.Story) }
        var sound by remember { mutableStateOf(false) }
        // The wind and the moon are the whole playground's: set them once and every scene that has wind or a moon follows.
        var wind by remember { mutableStateOf(Wind.Breeze) }
        // The moon follows the calendar unless a phase was picked by hand; either way it eases into the new phase instead of jumping.
        var pickedMoon by remember { mutableStateOf<MoonPhase?>(null) }
        var southern by remember { mutableStateOf(true) }
        val sink = remember { createAudioSink() }
        DisposableEffect(sink) { onDispose { sink.release() } }
        BoitataScene(Modifier.fillMaxSize().background(Color(0xFF0B0D10)), wind = wind) {
        val seconds = rememberSceneSeconds()
        val calendar = rememberRunningDay(DayClock.at(7f), hoursPerSecond = 0.5f)
        val calendarStep by remember { derivedStateOf { (MoonPhase.of(calendar.clock).age * 240f).toInt() } }
        val calendarMoon = remember(calendarStep) { MoonPhase.ofAge(calendarStep / 240f) }
        val moon = rememberSmoothMoon(pickedMoon ?: calendarMoon)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in Demo.entries) FilterChip(selected = d == demo, onClick = { demo = d }, label = { Text(d.label) })
                // The browser keeps sound locked until a tap: turning it on is that tap.
                FilterChip(selected = sound, onClick = { sound = !sound; if (sound) sink.unlock() }, label = { Text(if (sound) "Sound on" else "Sound off") })
            }
            WindControls(wind) { wind = it }
            MoonControls(pickedMoon, moon, southern, onPhase = { pickedMoon = it }, onHemisphere = { southern = it })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ProvideSprites { sprites ->
                CompositionLocalProvider(LocalSprites provides sprites) {
                when (demo) {
                    Demo.Story -> StoryApp(seconds, sink, sound, moon, southern, onWorkshop = { demo = Demo.Forest })
                    Demo.Forest -> ForestDemo(seconds, sink, sound, moon, southern)
                    Demo.Weather -> WeatherDemo(seconds, sink, sound, moon, southern)
                    Demo.Garden -> GardenDemo(seconds, moon, southern, calendar)
                    Demo.Window -> WindowDemo(seconds, moon, southern)
                    Demo.Shadows -> ShadowDemo(seconds)
                    Demo.Depth -> DepthDemo(seconds)
                    Demo.Lights -> LightsDemo(seconds, moon, southern)
                    Demo.Stage -> LightStageDemo(seconds)
                    Demo.Theatre -> ShadowTheatreDemo(seconds)
                    Demo.Pond -> PondDemo(seconds, moon, southern)
                    Demo.Liquids -> LiquidsDemo(seconds)
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
            Text("Wind", color = controlText())
            for ((name, preset) in listOf("Calm" to Wind.Calm, "Breeze" to Wind.Breeze, "Windy" to Wind.Windy, "Gale" to Wind.Gale)) {
                FilterChip(selected = wind.speed == preset.speed && wind.gustiness == preset.gustiness, onClick = { onChange(preset.copy(direction = if (wind.direction == 0f) 1f else wind.direction)) }, label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("Speed ${(wind.speed * 100).toInt()}%", color = controlText()); Slider(wind.speed, { onChange(wind.copy(speed = it)) }, valueRange = 0f..1f) }
            Column(Modifier.weight(1f)) { Text(if (wind.direction < -0.05f) "Blowing left" else if (wind.direction > 0.05f) "Blowing right" else "No direction", color = controlText()); Slider(wind.direction, { onChange(wind.copy(direction = it)) }, valueRange = -1f..1f) }
            Column(Modifier.weight(1f)) { Text("Gusts ${(wind.gustiness * 100).toInt()}%", color = controlText()); Slider(wind.gustiness, { onChange(wind.copy(gustiness = it)) }, valueRange = 0f..1f) }
        }
    }
}

/** Which moon, and from which hemisphere: it sets which side is lit. */
@Composable
private fun MoonControls(picked: MoonPhase?, shown: MoonPhase, southern: Boolean, onPhase: (MoonPhase?) -> Unit, onHemisphere: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Moon", color = controlText())
        FilterChip(selected = picked == null, onClick = { onPhase(null) }, label = { Text(if (picked == null) "Calendar: ${shown.label}" else "Calendar") })
        for (p in MoonPhase.All) FilterChip(selected = p === picked, onClick = { onPhase(p) }, label = { Text(p.label) })
        FilterChip(selected = southern, onClick = { onHemisphere(!southern) }, label = { Text(if (southern) "Southern sky" else "Northern sky") })
    }
}

/** True inside the story's pauses: the controls sit on the carved paper drawer and use dark ink. */
internal val LocalPaperPanel = androidx.compose.runtime.compositionLocalOf { false }

/** The colour of a control's explanation: pale on the plain panel, dark ink on the paper drawer. */
@Composable
internal fun controlText(): Color = if (LocalPaperPanel.current) Color(0xFF3A2414) else Color(0xFFB7BEC8)

@Composable
internal fun Controls(content: @Composable () -> Unit) {
    val paper = LocalPaperPanel.current
    val drawer = if (paper) LocalSprites.current?.art("ui_drawer") else null
    Box(Modifier.fillMaxWidth()) {
        if (drawer != null) androidx.compose.foundation.Image(drawer, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
        Column(Modifier.fillMaxWidth().let { if (drawer == null) it.background(Color(0xCC0B0D10)) else it }.padding(horizontal = 28.dp, vertical = if (drawer == null) 8.dp else 14.dp)) { content() }
    }
}

@Composable
internal fun Labelled(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp).fillMaxWidth(0.22f), color = controlText())
        Slider(value, onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ForestDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean, moon: MoonPhase, southern: Boolean) {
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
                Text("The serpent's head is a fire that never burns down, not even in the rain. When it passes close, the hunter (flammable) catches and chars. The animals are not flammable and are never touched.", color = controlText())
            }
            Labelled("Rain", rain, 0f..1f) { rain = it }; Labelled("Mist", fog, 0f..1f) { fog = it }
        }
    }
}

@Composable
internal fun WeatherDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean, moon: MoonPhase, southern: Boolean) {
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
internal fun WindowDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(16.5f) }
    var cloud by remember { mutableFloatStateOf(0f) }
    var open by remember { mutableStateOf(true) }
    var glass by remember { mutableStateOf(WindowGlass.Clean) }
    var weather by remember { mutableStateOf(WindowWeather.Clear) }
    var warm by remember { mutableStateOf(false) }
    var outside by remember { mutableFloatStateOf(12f) }
    var humidity by remember { mutableFloatStateOf(0.4f) }
    val pane = remember { PaneWeather() }
    val climate = remember { SurfaceClimate().also { WindowWeather.Clear.applyTo(it) } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { WindowScene(seconds, hour, cloud, moon = moon, southern = southern, windowOpen = open, glass = glass.optics, pane = pane, climate = climate, warmPatch = warm) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = open, onClick = { open = !open }, label = { Text(if (open) "Window open" else "Window shut") })
                for (g in WindowGlass.entries) FilterChip(selected = g == glass, onClick = { glass = g }, label = { Text(g.label) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                for (wx in WindowWeather.entries) FilterChip(selected = wx == weather, onClick = { weather = wx; wx.applyTo(climate); outside = wx.outside; humidity = wx.humidity; pane.clear() }, label = { Text(wx.label) })
                FilterChip(selected = warm, onClick = { warm = !warm }, label = { Text("Lamp by the glass") })
                FilterChip(selected = false, onClick = { pane.clear() }, label = { Text("Clean the glass") })
            }
            Text("Weather on the glass: fog forms where the pane is colder than the room's dew point, ice grows in from the edges below freezing, rain runs down in beads and leaves clear streaks, snow piles on the sill. Drag over the pane to wipe it; a lamp by the glass clears its corner; an open window keeps it clear.", color = controlText())
            Labelled("Outside, degrees", outside, -20f..25f) { outside = it; climate.outsideTemp = it }
            Labelled("Humidity in the room", humidity, 0.2f..0.95f) { humidity = it; climate.humidity = it }
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
        Controls { Text("Drag the light", color = controlText()); Labelled("Reach", reach, 0.3f..1.4f) { reach = it } }
    }
}

@Composable
private fun DepthDemo(seconds: () -> Float) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { DepthScene(seconds) }
        Controls { Text("Drag anything. Up sends it back, small and tucked behind what is nearer; down brings it forward, bigger. Lampião's lamp is the light: carry him about and every shadow swings. Tap a thing to switch whether the wind moves it (palms, plants and the pine do by default).", color = controlText()) }
    }
}

@Composable
internal fun LightsDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(19f) }
    var on by remember { mutableStateOf(LightKind.entries.toSet()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LightsScene(seconds, hour = hour, moon = moon, southern = southern, on = on) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in LightKind.entries) FilterChip(selected = k in on, onClick = { on = if (k in on) on - k else on + k }, label = { Text(k.label) })
            }
            Labelled("Hour", hour, 0f..24f) { hour = it }
            Text("Each light throws its own shadow, its own way: the sun and moon (far away) in parallel, long at dawn; the fire, candle and lamp (near) fanning out from them; the lantern only inside its cone. Drag the fire, the candle, the lantern post or Lampião (he carries the lamp) and watch every shadow swing. The animals wander on their own and cannot burn.", color = controlText())
        }
    }
}

@Composable
internal fun LightStageDemo(seconds: () -> Float) {
    var on by remember { mutableStateOf(StageLamp.entries.toSet()) }
    var height by remember { mutableFloatStateOf(0.65f) }
    var walls by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LightStageScene(seconds, on = on, heightScale = height, walls = walls) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in StageLamp.entries) FilterChip(selected = l in on, onClick = { on = if (l in on) on - l else on + l }, label = { Text(l.label, color = l.color) })
                FilterChip(selected = walls, onClick = { walls = !walls }, label = { Text("Walls") })
            }
            Labelled("Lamp height", height, 0.4f..2f) { height = it }
            Text("Four lamps light the stage from four places at once. Drag a lamp (a glowing orb) and its shadows swing while the others stay; drag a person across and their shadows turn as they pass between the lamps. Lower the lamps for long shadows. Switch on Walls: shadows thrown backward climb the back wall (bigger the farther it is behind), and shadows thrown at the crate bend onto its face.", color = controlText())
        }
    }
}

@Composable
internal fun ShadowTheatreDemo(seconds: () -> Float) {
    var lamp by remember { mutableStateOf(TheatreLamp.Hurricane) }
    var paper by remember { mutableStateOf(TheatrePaper.White) }
    var backstage by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ShadowTheatreScene(seconds, lamp = lamp, paper = paper, backstage = backstage) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in TheatreLamp.entries) FilterChip(selected = l == lamp, onClick = { lamp = l }, label = { Text(l.label) })
                for (p in TheatrePaper.entries) FilterChip(selected = p == paper, onClick = { paper = p }, label = { Text(p.label) })
                FilterChip(selected = backstage, onClick = { backstage = !backstage }, label = { Text("Backstage") })
            }
            Text("Teatro de Sombras: the lamp is behind the paper and the puppets are between them. Drag a puppet toward the lamp and its shadow grows huge and soft; press it against the paper and it is small and sharp. Drag the lamp to swing every shadow. The paper's colour is the light's; backstage shows the puppeteers' side.", color = controlText())
        }
    }
}

@Composable
internal fun PondDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean) {
    var hour by remember { mutableFloatStateOf(22f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { PondScene(seconds, hour = hour, moon = moon, southern = southern) }
        Controls {
            Labelled("Hour", hour, 0f..24f) { hour = it }
            Text("Water behaves as water: everything at the edge is mirrored in it (faint, rippling, fading with depth), the moon and the lamps leave glitter, and whatever wades in is partly under water, looking nearer the surface than it is. Drag the people into the pond; the caiman and the capybara wade through it themselves.", color = controlText())
        }
    }
}

@Composable
internal fun LiquidsDemo(seconds: () -> Float) {
    val bench = remember { LiquidBench() }
    val sel = bench.selected
    val current = bench.fluid
    // The sliders show the picked vessel's liquid, and move it when you drag them.
    @Suppress("UNUSED_VARIABLE") val version = bench.fluidVersion
    var viscosity by remember(sel, version) { mutableFloatStateOf(current.viscosity) }
    var density by remember(sel, version) { mutableFloatStateOf(current.density / 1000f) }
    var cloud by remember(sel, version) { mutableFloatStateOf(current.turbidity) }
    var index by remember(sel, version) { mutableFloatStateOf(current.index) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { LiquidsScene(seconds, bench) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { bench.pour() }, label = { Text("Pour") })
                // Held down, it keeps sipping: a gulp at once and another every half second.
                val sipSource = remember { MutableInteractionSource() }
                val sipHeld by sipSource.collectIsPressedAsState()
                LaunchedEffect(sipHeld) { bench.holdSip(sipHeld) }
                FilterChip(selected = sipHeld, onClick = {}, interactionSource = sipSource, label = { Text("Hold to sip") })
                FilterChip(selected = false, onClick = { bench.pourHot() }, label = { Text("Pour hot coffee") })
                FilterChip(selected = false, onClick = { bench.heat() }, label = { Text("Heat it") })
                FilterChip(selected = false, onClick = { bench.drop() }, label = { Text("Drop in") })
                FilterChip(selected = false, onClick = { bench.knock() }, label = { Text("Knock over") })
                FilterChip(selected = false, onClick = { bench.light() }, label = { Text("Light the spill") })
                FilterChip(selected = false, onClick = { bench.refill() }, label = { Text("Refill all") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in Fluid.All) FilterChip(selected = current.name == f.name, onClick = { bench.setFluid(f) }, label = { Text(f.name) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in SurfaceMaterial.All) FilterChip(selected = bench.surface === m, onClick = { bench.surface = m }, label = { Text(m.name) })
                FilterChip(selected = false, onClick = { bench.wipe() }, label = { Text("Wipe stains") })
                FilterChip(selected = false, onClick = { bench.waitHours(1f) }, label = { Text("Wait an hour") })
                FilterChip(selected = false, onClick = { bench.waitHours(12f) }, label = { Text("Wait half a day") })
                Text("Who sips:", color = controlText())
                for (d in Drinker.All) FilterChip(selected = bench.drinker === d, onClick = { bench.drinker = d }, label = { Text("${d.name} (${d.viewpoint.name})") })
            }
            Text("Tap a glass to pick it (and drop a ripple in). Drag it along the table and the liquid lags and sloshes. Hot liquids steam, boil and cool; pouring hot coffee mixes by volume and leaves foam. Knock it over: it pours out in a stream into a puddle that spreads, and a puddle that spreads past the end or the front of the table runs off in drops to the floor. Spilled kerosene burns. Choose the picked glass's liquid, or set its viscosity, density, cloudiness and refraction below.", color = controlText())
            Labelled("Viscosity (water 0, honey 1)", viscosity, 0f..1f) { viscosity = it; bench.setFluid(bench.fluid.copy(viscosity = it)) }
            Labelled("Density, thousand kg/m³ (water 1, mercury 13.5)", density, 0.5f..14f) { density = it; bench.setFluid(bench.fluid.copy(density = it * 1000f)) }
            Labelled("Cloudiness", cloud, 0f..1f) { cloud = it; bench.setFluid(bench.fluid.copy(turbidity = it)) }
            Labelled("Refraction index", index, 1f..1.7f) { index = it; bench.setFluid(bench.fluid.copy(index = it)) }
        }
    }
}

@Composable
internal fun BurnDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean) {
    var rain by remember { mutableFloatStateOf(0f) }
    var guardian by remember { mutableStateOf(false) }
    var resetKey by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { BurnScene(seconds, rain = rain, guardian = guardian, resetKey = resetKey, sink = sink, sound = sound) }
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = guardian, onClick = { guardian = !guardian }, label = { Text("Boitatá's fire") })
                FilterChip(selected = false, onClick = { resetKey++ }, label = { Text("Start again") })
                Text("Drag things to the fire. Downwind they warm, smoke, catch and char; upwind they stay safe. Dry grass first, a pine last. The Boitatá's fire spares every plant and puts out ordinary fires.", color = controlText())
            }
            Labelled("Rain", rain, 0f..1f) { rain = it }
        }
    }
}

@Composable
internal fun FireDemo(seconds: () -> Float, sink: AudioSink, sound: Boolean) {
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
