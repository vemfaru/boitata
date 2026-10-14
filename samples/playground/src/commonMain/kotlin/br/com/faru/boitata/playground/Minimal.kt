package br.com.faru.boitata.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.BoitataScene
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.CastShadow
import br.com.faru.boitata.light.Fire
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.WindowLight
import br.com.faru.boitata.scene.depthDrag
import br.com.faru.boitata.weather.Rain

// The snippets in the README, kept here so they are compiled with the rest.

/** Rain over a night sky: a scene, and the rain in it. */
@Composable
fun MinimalRain() {
    // A fresh breeze: the rain leans with it, and so does everything else in the scene that reacts to wind.
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF0B1220)), wind = Wind.Windy) {
        Rain(intensity = 0.8f)
    }
}

/** A campfire with embers, and the same fire in the Boitatá's colours. */
@Composable
fun MinimalFire() {
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF06080C))) {
        Fire(Modifier.align(Alignment.Center).size(260.dp), intensity = 0.9f, palette = FlamePalette.Boitata)
    }
}

/** A post and its shadow, thrown away from a lamp. */
@Composable
fun MinimalShadow() {
    val lamp = LightSource(x = 200f, y = 120f, color = Color(0xFFFFD08A), strength = 0.9f, reach = 700f, groundX = 200f, groundY = 360f)
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF14110D))) {
        CastShadow(lamp, feetX = 520f, feetY = 380f, height = 160f) { color ->
            // Feet at (0, 0), up is negative y; `color` is the shadow's colour when it is drawn as a shadow.
            drawRect(color, Offset(-8f, -160f), Size(16f, 160f))
        }
    }
}

/** Light through a window, at five in the afternoon. */
@Composable
fun MinimalWindowLight() {
    val sky = SkyModel.at(hour = 17f, SkyWeather.Clear)
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF3A2F26))) {
        WindowLight(
            window = Rect(80f, 60f, 330f, 320f), floorY = 380f, depth = 160f, lean = 0.9f,
            color = sky.lightColor, strength = sky.lightStrength, elevation = sky.sunElevation,
        )
    }
}

/** Two things on a ground you can drag: back makes them small, forward makes them big, and the nearer one covers the other. */
@Composable
fun MinimalDepth() {
    val stage = remember {
        val perspective = Perspective(frontEdgeY = 600f, backEdgeY = 300f, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = 480f)
        DepthStage(perspective, 20f, 300f, 780f, 600f).apply {
            add(DepthItem("post", feetX = 250f, feetY = 480f, baseHeight = 120f, aspect = 0.2f))
            add(DepthItem("crate", feetX = 520f, feetY = 560f, baseHeight = 60f, aspect = 1.1f))
        }
    }
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF14110D)).depthDrag(stage)) {
        Canvas(Modifier.fillMaxSize()) {
            // Farthest first, so what is nearer covers it. The positions are read here, in the draw block: dragging redraws and recomposes nothing.
            for (item in stage.ordered()) {
                val h = stage.height(item); val w = h * item.aspect
                drawRect(Color(0xFFC79B5E), Offset(item.feetX - w / 2f, item.feetY - h), Size(w, h))
            }
        }
    }
}

/** A wind from the right, and a palm that sways in it, drawn at its foot with `sway`. The rain and a fire in the same scene lean and blow too. */
@Composable
fun MinimalWind() {
    val seconds = rememberSceneSeconds()
    val sceneWind = rememberSceneWind()
    BoitataScene(Modifier.fillMaxSize().background(Color(0xFF0B1220)), wind = Wind(speed = 0.6f, direction = -1f, gustiness = 0.5f)) {
        Rain(intensity = 0.7f)
        Canvas(Modifier.fillMaxSize()) {
            // A palm is anchored but flexible: the wind bends it, a stiffer thing less, and a rigid one not at all.
            val bend = WindResponse.Palm.bend(sceneWind.wind, seconds(), place = 0.5f)
            sway(feetX = size.width / 2f, feetY = size.height, bend = bend) {
                drawRect(Color(0xFF2F5D3A), Offset(size.width / 2f - 6f, size.height - 220f), Size(12f, 220f))
            }
        }
    }
}

