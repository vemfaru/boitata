# Several lights, and where shadows fall

`LightRig` holds the lights of a scene. Every light lights a thing in its own colour and throws its own shadow, and each shadow is as dark as that light's share of all the light there.

```kotlin
val rig = LightRig(ambient = 0.1f)
rig.add(Lights.fire(x = 300f, y = 500f))                 // warm, low, unsteady, a soft shadow
rig.add(Lights.lamp(x = 700f, y = 480f))                 // steady, higher, a sharper shadow
rig.add(Lights.sun(hour = 17f))                          // far away: parallel shadows, long at dawn and dusk
rig.add(Lights.moon(hour = 0f, phase = MoonPhase.Full))  // pale blue, weaker
rig.add(Lights.lantern(x = 900f, y = 500f, aim = PI.toFloat()))  // a cone

// Per frame, in the draw phase, for each thing standing on the ground:
drawCastShadows(rig, feetX, feetY, height, seconds) { colour -> /* draw the thing, feet at (0, 0), up negative */ }
val tint = rig.shade(dark, lit, feetX, feetY, seconds)   // lit by every light that reaches it, in their colours
drawLightPools(rig, seconds)                              // the pools of light on the ground
```

| Light | What it is | Shadows |
|---|---|---|
| `PointLight` | a place, a height, a reach, a size (softness), a flicker | radiate from it; longer the lower and nearer it is |
| `SpotLight` | a point light with an aim and a cone | the same, but only inside the cone |
| `DirectionalLight` | the sun and moon: an azimuth and an elevation | parallel, 1 / tan(elevation) times the thing's height |

The presets in `Lights` differ in colour, height, softness and flicker: `fire`, `candle`, `lamp`, `hurricaneLamp`, `lantern`, `sun(hour)`, `moon(hour, phase)`, `lightning`. A light is plain data; move it by setting `x` and `y`.
Nothing is allocated while a scene draws: shadows are written into reused `ShadowCasts`. At `Quality.High` up to four lights cast on a thing, at Medium two, at Low one.

## Walls: shadows that follow the surface they fall on

By default shadows lie on the floor. Add a `Wall` to the rig and a shadow thrown toward it climbs it instead:

```kotlin
rig.add(Wall(footY = floorY, left = 0f, right = width, height = floorY))   // a back wall
rig.add(Wall(footY = crate.feetY, left = crate.left, right = crate.right, height = crate.height, owner = crate))  // the face of a crate
drawCastShadows(rig, feetX, feetY, height, seconds, ignore = thing) { ... }   // `ignore`: a thing never shadows its own face
```

A wall is a vertical surface that faces the viewer and stands on the ground along `footY`. For a light nearer the viewer than the thing, its rays run past the thing to the wall: the shadow there is the thing's outline,
enlarged by how much farther the wall is from the light than the thing is (so a thing against the wall has a shadow its own size, and one far in front of it a big soft one), and the floor shadow stops at the wall's foot. For the sun the shadow keeps
the thing's size and is moved along the ray, and only a sun shining toward the wall puts shadows on it. `drawWallLight(rig, wall, seconds)` lights the wall's face from the lights in front of it.
The geometry is pure maths in `ShadowMath` (`onWallFromPoint`, `onWallFromDirection`) with tests; walls are drawn at Medium and High quality.

Not yet: walls that face sideways, and the tops of things (a shadow lying across a table).

## The physics, and why it is cheap

Everything is worked out from three numbers per light and thing: where the light is, how high it is, and where the thing stands.

**Floor shadow of a point light.** A standing thing is a flat picture in a vertical plane. The light's ray through a point of it at height `z` meets the floor `lz / (lz - z)` times as far from the light's foot as the point's own foot is, where `lz` is the light's height: low points land near the thing and the tip lands far away (it stretches toward the tip, and a tip as high as the light has a shadow that reaches infinity). In the picture's own coordinates that is `x' = (lz * x + a * y) / (lz + y)`, `y' = c * y / (lz + y)` with `(a, c)` the light's offset from the feet: a *perspective transform*. So the whole shadow is **one matrix and one draw call**, and the canvas does the division. A far-away light (sun, moon) has parallel rays, which makes it an ordinary shear. The shadow is drawn up to 80% of the lamp's height (above that the ray is nearly level).

**Shadow on a wall.** The wall is a plane parallel to the picture, so the same ray gives something simpler: the shadow is the picture *scaled* by `t = (wall - light) / (thing - light)` (how much farther the wall is than the thing, measured from the light) and moved, an affine transform: also one matrix. A thing against the wall gets `t = 1`, one far in front gets a big, soft shadow.

**The corner.** The ray past a point reaches either the floor or the wall first. The point where it reaches both at once is at the height `ShadowMath.wallCrossing`: everything below it throws its shadow on the floor in front of the wall, everything above it on the wall. Because both shadows are exact, the floor shadow arrives at the wall's foot line exactly where the wall shadow starts (the tests check this for several lights and walls to a twentieth of a pixel). So there is no seam to blend: the floor shadow is clipped at the wall's foot, the wall shadow is clipped to above it, and they meet.
(The enlargement on a wall is capped at 3.2 so a light level with the thing does not put a gigantic shadow on the wall; the join is exact below the cap.)

**Soft edges.** A bigger light gives a softer edge, and the edge is sharp where the thing touches the ground and blurrier at the tip. Each shadow is drawn in three passes with the lamp a little lower each time, which lengthens the shadow with its feet fixed, so the tip softens and the contact stays crisp.

**Light on walls and floors.** A point light a distance `d` from a flat surface lights a point `rho` along it by `strength * falloff(r) * cos(theta)` with `r = sqrt(d² + rho²)` and `cos(theta) = d / r` (Lambert). That depends only on `rho`, so the pool is a **radial gradient sampled once from that formula**, exact and one draw: a lamp near a wall makes a small bright pool, a far one a wide dim one, and one beyond its reach none. The floor pools use the same maths.

**Cost.** Per thing, per light: a handful of multiplications to build one matrix, and up to three draws of the silhouette. Nothing is allocated except one small matrix per draw; at Low quality only the strongest light casts, once, and only on the floor.

## Things block light, and shadows land on things

Every `Billboard` in the rig is a standing thing (or a wall) with `Optics`: how much of the light that hits it is stopped (`opacity`), the colour of what passes (`tint`), and how it scatters, blurs or bends light that goes through (`scatter`, `blur`, `distortion`, `haze`). `Optics.Opaque` is a crate or a person, `Optics.Glass(tint)` clear glass, `Optics.Frosted(dirt)` dirty or frosted glass, `Optics.Paper()` paper or cloth. A thing's own picture says *where* it is, and an `AlphaMask` (a coarse copy of the picture's transparency) says where light passes between its parts.

```kotlin
val crate = Billboard(footY = crate.feetY, left = x0, right = x1, height = h, owner = "crate", mask = art.mask("crate"))
crate.silhouette = { colour -> drawArt(image, Offset(-w / 2, -h), k, colour) }   // feet at (0, 0), up negative
crate.receivesShadows = true
rig.add(crate)
```

- **Light that is in the way.** For every light, `rig.shade`, `rig.level` and `rig.shadows` ask how much of it reaches the thing: the ray from the light to three points on it (foot, middle, head) is tested against the things between, and each multiplies it by `1 - opacity * coverage`. A crate between the lamp and a person puts the person in shadow (their colour goes dark and cold); a pane of glass lets most through and tints it; two sheets of paper let a quarter through. A thing that cannot see a light throws no shadow of its own from it. Pure maths in `Transmission`, no allocation.
- **Shadows on faces.** `drawFaceShadows(rig, receiver, seconds)`, drawn right after a thing marked `receivesShadows`, lays the shadows of the things in front of it across it, projected onto its plane (the wall projection) and **cut to its outline**: the crate's shadow lies across the person only where the person is. With `faceShadows` on, such a thing is not also darkened as a whole, since its face already shows which part is in shadow; turn it off for the cheaper whole-thing darkening.
- **Order.** Draw all floor shadows first, then the things from far to near, calling `drawFaceShadows` after each: the things then cover the floor shadows they stand on without any clipping.

Cost: the visibility test is a short loop per light and thing (three rays, early exits); face shadows are two small offscreen layers per shadow that can reach the receiver (the others are skipped by a bounding test), so they are a High and Medium quality feature.
