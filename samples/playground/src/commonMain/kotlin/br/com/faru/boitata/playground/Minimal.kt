package br.com.faru.boitata.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.CastShadow
import br.com.faru.boitata.light.Fire
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.WindowLight
import br.com.faru.boitata.weather.Rain
import br.com.faru.boitata.weather.rememberSkySeconds

// The snippets in the README, kept here so they are compiled with the rest.

/** Rain over a night sky: three lines. */
@Composable
fun MinimalRain() {
    val seconds = rememberSkySeconds()
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        Rain(seconds, intensity = 0.8f, wind = 0.3f)
    }
}

/** A campfire with embers, and the same fire in the Boitatá's colours. */
@Composable
fun MinimalFire() {
    val seconds = rememberSkySeconds()
    Box(Modifier.fillMaxSize().background(Color(0xFF06080C))) {
        Fire(seconds, Modifier.align(Alignment.Center).size(260.dp), intensity = 0.9f, palette = FlamePalette.Boitata)
    }
}

/** A post and its shadow, thrown away from a lamp. */
@Composable
fun MinimalShadow() {
    val lamp = LightSource(x = 200f, y = 120f, color = Color(0xFFFFD08A), strength = 0.9f, reach = 700f, groundX = 200f, groundY = 360f)
    Box(Modifier.fillMaxSize().background(Color(0xFF14110D))) {
        CastShadow(lamp, feetX = 520f, feetY = 380f, height = 160f) { color ->
            // Feet at (0, 0), up is negative y; `color` is the shadow's colour when it is drawn as a shadow.
            drawRect(color, Offset(-8f, -160f), Size(16f, 160f))
        }
    }
}

/** Light through a window, at five in the afternoon. */
@Composable
fun MinimalWindowLight() {
    val seconds = rememberSkySeconds()
    val sky = SkyModel.at(hour = 17f, SkyWeather.Clear)
    Box(Modifier.fillMaxSize().background(Color(0xFF3A2F26))) {
        WindowLight(
            window = Rect(80f, 60f, 330f, 320f), floorY = 380f, depth = 160f, lean = 0.9f,
            color = sky.lightColor, strength = sky.lightStrength, elevation = sky.sunElevation, seconds = seconds,
        )
    }
}
