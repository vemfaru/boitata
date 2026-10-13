# Changelog

## Unreleased
- Sound (new modules `boitata-audio-synth` and `boitata-audio`): `StormCues` (thunder at `strike.at + thunderDelay`) and `AmbienceLevels` in core; a deterministic synth (`Thunder`, `Ambience.rain/wind/fire`); `AudioSink`, a sample-accurate `SoftwareMixer`, `SceneAudioScheduler` and `BoitataAudio { }`, which schedule sounds on the device's clock from the scene's time, cancel on weather changes and seeks, and pause with the app. Sinks: desktop (`SourceDataLine`) and web (Web Audio); Android and iOS are silent placeholders for now. `RecordingAudioSink` for tests.
- The clock gains `realSeconds` and `inForeground` (thunder keeps going under reduced motion).
- `BoitataScene` and `SceneClock`: one frame loop for a whole scene, shared by every effect, paused when the screen is not in the foreground and still under reduced motion (Android, iOS, web; force it with `SceneConfig`).
- `Quality` (High, Medium, Low) and an adaptive `QualityGovernor`: rain, snow, fog, fire, embers, window dust, shadows and sampling scale with it, and a scene steps down after about a second of slow frames.
- `drawImageSmooth`: the one way to place a moving picture, with a float translation.
- `@AdvancedBoitataApi` on the low-level `draw*` functions; the composables are the safe path.
- `boitata-testing` (`measureScene`) and `checkPerformanceRules` (fails the build on a second frame loop, whole-pixel placement, bicubic sampling, `delay` loops, JVM-only code in common code).
- Breaking: `Rain`, `Snow`, `Fog`, `Fire`, `WindowLight`, `SkyWindow` take `modifier` first and read time and quality from the scene clock; `seconds` and `quality` are optional overrides. `rememberFlicker` follows the clock.
- Fix: `CastSilhouette` and `WallShadow` sampled bicubic (about three times the cost for no visible gain); now mipmapped.
- Fire (`Fire`, `drawFlameTrail`, `fireLight`), cast shadows from any drawing (`CastShadow`), window light (`WindowLight`), rain, snow and fog.
- Playground app (desktop and web), offscreen clip renderer, README GIFs, art style guide.
- `SkyWindow(horizon = ...)`: where the horizon stands, so the same sky works as a window above a desk or as a whole scene.
- Fix: the sky's layers and clouds no longer step a whole pixel at a time. They were placed with integer offsets, so a slow parallax sat still for over a second and then jumped; they now move with float translations, and use mipmapped filtering (about three times cheaper per frame).
- Woodcut art for the playground (`samples/playground/.../composeResources`), the art style guide and the prompts that made it.
- First extraction from The Typist: `boitata-core`, `boitata-scene`, `boitata-light`, `boitata-weather`.
