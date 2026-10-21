# Performance

Boitatá is built so that **using it the obvious way is smooth**, and the ways that are not smooth are hard to reach or are caught by the build.
This page says what it promises, how that is checked, and what it cannot promise.

## What you get by default

Put your scene in a `BoitataScene` and use the composables (`Rain`, `Snow`, `Fog`, `Fire`, `SkyWindow`, `WindowLight`, `CastShadow`):

```kotlin
BoitataScene(Modifier.fillMaxSize()) {
    SkyWindow(sky = { sky }, layers = layers, parallax = { parallax })
    Rain(intensity = 0.8f)
    Fire(Modifier.size(260.dp))
}
```

| Promise | How |
|---|---|
| **One frame loop** for the whole scene, however many things move | `BoitataScene` owns a single `SceneClock`; every effect reads its time from it |
| **Nothing recomposes per frame** | time and quality are read as lambdas, inside draw blocks only |
| **Motion is smooth, not stepped** | every moving picture is drawn through `drawImageSmooth`, with a float translation; there is no public API that takes whole-pixel offsets for something that moves |
| **A heavy scene gets lighter by itself** | `Quality` (High, Medium, Low) scales particles, glow and sampling; an adaptive governor steps it down after about a second of slow frames and back up after several calm seconds, waiting longer each time it relapses |
| **It stops when you are not looking** | the clock pauses when the screen is not in the foreground (background tab, locked phone, another app) |
| **It respects "reduce motion"** | Android (animations removed), iOS (Reduce Motion) and web (`prefers-reduced-motion`) are read; time stands still. Force it with `SceneConfig(reducedMotion = On)` |
| **The same frame for the same time** | rain, snow, fog, embers and storms are functions of time alone, so they can be scrubbed, paused and recorded |

## What it asks of you

1. **Read time and quality inside a draw block** (`drawBehind`, `Canvas`, `graphicsLayer {}`), never in the body of a composable. The library's own composables do this. If you
   write your own, use `val seconds = rememberSceneSeconds()` and call `seconds()` in the draw block.
2. **Put one `BoitataScene` around the scene.** Without one, each effect makes a clock of its own: still correct, but not shared, so not paused or budgeted together.
3. **Prefer the composables to the `draw*` functions.** The low-level ones (`drawRain`, `drawFlame`, `drawCastShadow`, ...) are marked `@AdvancedBoitataApi` and warn when you use
   them: they draw exactly what you tell them, so reading time in the draw phase and scaling by quality becomes your job.
4. **Do not snap moving things to whole pixels.** Use `drawImageSmooth` for any picture that moves.

## What the library does for you in the draw phase

- **No shader per particle.** A glow repeated many times a frame (an ember, a fog bank, a cloud puff) is one `unitGlow` stamped with `drawGlow` at each place: a transform instead of a new gradient. Use the same pair in your own `draw*` code.
- **Made once per change.** Shadows and the light's shading build their brushes, paints and matrices in `drawWithCache`, so a frame that only moves something else costs no allocation.
- **Blurs follow quality.** A blur is a layer redone whenever the scene redraws: a cast or wall shadow blurs fully at High, less at Medium and not at all at Low (`shadowBlurScale`); the star field and the particles scale with it too.

## How it is checked

| Check | What it catches | Run it |
|---|---|---|
| `checkPerformanceRules` (a build task, in CI) | a second `withFrameNanos` loop, `drawImage` at integer offsets, bicubic sampling, `delay` loops in the library, JVM-only calls in common code (they break wasm and iOS) | `./gradlew checkPerformanceRules` |
| `boitata-testing` effect tests (in CI) | every effect changes the picture on every frame; the sky's parallax and clouds never stand still more than a few frames; Low quality costs clearly less than High | `./gradlew :boitata-testing:jvmTest` |
| `QualityGovernorTest` | the governor steps down, steps up only after a hold, and does not flicker on a scene that is only too heavy at High | `./gradlew :boitata-core:jvmTest` |
| Playground `benchFrames` | frame cost of the weather scene, before and after a change | `./gradlew :samples:playground-desktop:benchFrames` |

`boitata-testing` is also a helper for your own scenes: `measureScene { MyScene() }` draws it frame by frame and tells you the cost and how many frames were identical to the one before.

```kotlin
val stats = measureScene(frames = 180) { MyScene() }
assertTrue(stats.longestStill <= 3, "stood still for ${stats.longestStill} frames")
```

## What it cannot promise

- **Your own drawing.** Read state in composition, allocate in a draw block, or draw ten thousand things, and no library can save the frame.
- **Real devices.** The costs measured here are CPU rasterisation on a laptop: good for "this change made it faster" and "Low is cheaper than High", not for what a particular phone's GPU
  does. Test on a release build on a real device (and an iPhone at 120 Hz) before you call a scene smooth.
- **Pre-scaled art and cached tints** (scaling each picture once to the screen size, building colour filters once) are not done: they have to be measured first, and the sampling and snapping fixes
  already took the sky layers from about 61 ms to 19 ms of CPU a frame.
- **Fire spread keeps state.** `FireWorld` depends on what has already burnt, so unlike rain and snow it is not a function of time alone: it steps at a fixed rate on the scene clock, deterministically for a seed, and costs about the square of the number of things in it per step (fine for dozens of things, not thousands).
- **`rememberFlicker`** and **`rememberEasedLight`** return plain values: reading them recomposes the caller (twelve times a second for the flicker, every frame of an easing for the light). Where they only feed drawing, use `rememberFlickerState` / `rememberEasedLightState` and read `.value` in the draw block, and pass the light to `LightShade` as a lambda.

## History

The rules exist because each one was a real bug here: the sky's parallax stood still for over a second at a time and then jumped a pixel (placement by integer offsets); the shadow
code sampled bicubic and cost several times more than needed; a forest scene used `Math.toDegrees` and broke the web build. See [CHANGELOG.md](../CHANGELOG.md).
