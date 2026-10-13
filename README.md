# Boitatá

<!-- logo goes here: docs/images/logo.png -->

**Light, weather and scenes for 2D games in Kotlin Multiplatform.** Android, iOS, desktop and web (Wasm), all from Compose Multiplatform.

Named after the Boitatá, the fire serpent of Brazilian folklore: a guardian whose flames light the dark and never burn the forest.

> **Status: 0.x, not published yet.** The API will change. Extracted from [The Typist](https://thetypist.app), where it lights a noir desk.

## What is in it

| Module | What it gives | Depends on |
|---|---|---|
| `boitata-core` | Pure maths and models, nothing drawn: `Perspective`, `FastNoise`, `SkyModel` (sun, moon, stars, daylight by hour), `StormModel` (seeded lightning strikes) | Compose runtime/graphics types only |
| `boitata-scene` | `SceneCamera`, `SceneViewport` (fits a world to any window), `Modifier.worldBounds` | core |
| `boitata-light` | `LightSource`, `LightShade`, soft drop shadows, `CastSilhouette` (shadows laid on the ground), flicker, eased lights | core, scene |
| `boitata-weather` | `SkyWindow` (a sky with parallax, clouds and mist), `rememberStorm` (lightning flash and bolt) | core |

Rain, snow, fog and steam shaders, a prop layout and a small physics module follow. See [docs/architecture.md](docs/architecture.md).

## Use it (once published)

```kotlin
// build.gradle.kts of your KMP module
commonMain.dependencies {
    implementation("br.com.faru.boitata:boitata-light:<version>")
    implementation("br.com.faru.boitata:boitata-weather:<version>")
}
```

## Try it

A playground app with live controls (time of day, weather, lightning, light colour) is planned under `samples/`.

## Build

```bash
./gradlew build
```

Needs JDK 21 and an Android SDK (`sdk.dir` in `local.properties`). Targets: Android, iOS (arm64 and simulator), JVM, wasmJs.

## Licence

[Apache-2.0](LICENSE). The licence covers the code, not the Boitatá name or logo.
