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

/** The demos, in the order of the tabs. */
enum class Demo(val label: String) { Forest("Boitatá"), Weather("Weather"), Window("Window light"), Shadows("Shadows"), Fire("Fire") }

/** The whole playground: pick a demo, play with its controls. The same scenes are what the README's GIFs are rendered from. */
@Composable
fun PlaygroundApp() {
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0B0D10), surface = Color(0xFF14181D), primary = Color(0xFFF2A83B))) {
        var demo by remember { mutableStateOf(Demo.Forest) }
        BoitataScene(Modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
        val seconds = rememberSceneSeconds()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in Demo.entries) FilterChip(selected = d == demo, onClick = { demo = d }, label = { Text(d.label) })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ProvideSprites { sprites ->
                CompositionLocalProvider(LocalSprites provides sprites) {
                when (demo) {
                    Demo.Forest -> ForestDemo(seconds)
                    Demo.Weather -> WeatherDemo(seconds)
                    Demo.Window -> WindowDemo(seconds)
                    Demo.Shadows -> ShadowDemo(seconds)
                    Demo.Fire -> FireDemo(seconds)
                }
                }
                }
            }
        }
        }
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
private fun ForestDemo(seconds: () -> Float) {
    var rain by remember { mutableFloatStateOf(0f) }
    var fog by remember { mutableFloatStateOf(0.35f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ForestScene(seconds, rain = rain, fog = fog) }
        Controls { Labelled("Rain", rain, 0f..1f) { rain = it }; Labelled("Mist", fog, 0f..1f) { fog = it } }
    }
}

@Composable
private fun WeatherDemo(seconds: () -> Float) {
    var hour by remember { mutableFloatStateOf(14f) }
    var kind by remember { mutableStateOf(WeatherKind.Rain) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { WeatherScene(seconds, hour, kind) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in WeatherKind.entries) FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
            }
            Labelled("Hour", hour, 0f..24f) { hour = it }
        }
    }
}

@Composable
private fun WindowDemo(seconds: () -> Float) {
    var hour by remember { mutableFloatStateOf(16.5f) }
    var cloud by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { WindowScene(seconds, hour, cloud) }
        Controls { Labelled("Hour", hour, 0f..24f) { hour = it }; Labelled("Cloud", cloud, 0f..1f) { cloud = it } }
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
        ) { ShadowScene({ light }, reach) }
        Controls { Text("Drag the light", color = Color(0xFFB7BEC8)); Labelled("Reach", reach, 0.3f..1.4f) { reach = it } }
    }
}

@Composable
private fun FireDemo(seconds: () -> Float) {
    var intensity by remember { mutableFloatStateOf(0.9f) }
    var kind by remember { mutableStateOf(FireKind.Campfire) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { FireScene(seconds, intensity, kind) }
        Controls {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (k in FireKind.entries) FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) }) }
            Labelled("Fire", intensity, 0f..1f) { intensity = it }
        }
    }
}
