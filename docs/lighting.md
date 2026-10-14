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
