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

## Light through a translucent surface: Teatro de Sombras

A surface with `Optics.Paper()` (or `Frosted`, or tinted `Glass`) lets light through. With the lamp **behind** it and things between the lamp and the surface, the audience sees the light that comes through as a **glow** and, on it, the **shadows** of the things in between, exactly as a hand behind a sheet:

- The shadow is the thing's outline enlarged by `t = (screen - lamp) / (thing - lamp)`: the same geometry as a shadow on a back wall, valid with the light on either side of the thing (`ShadowMath.onWallFromPoint` accepts both). A thing pressed against the paper has `t` near 1: a small, sharp shadow. A thing near the lamp has a huge, soft one. Give the surface a larger `maxShadowScale` (the default 3.2 stops a level lamp putting a gigantic shadow on a wall; a theatre screen wants far more).
- The glow is the light on a flat surface (Lambert, centred on the point of the paper nearest the lamp, at the lamp's height), multiplied by what the paper lets through (`1 - opacity`), tinted by it, and **spread wider and softer by `scatter`**: thin clear glass shows the lamp as it is, paper makes a soft glow. `drawWallLight(rig, screen, seconds)` draws it for a lamp on either side.
- The shadows are drawn on the glow by `drawCastShadows(..., floor = false)`: a dark overlay keeps `1 - alpha` of the light under it, which is what an object in front of a lamp does to the paper behind.

`drawThroughShadows(shadows, lamps, casters, surfaceFootY, optics)` is the same theatre for a surface that is only a picture in a bigger scene (a sheet of paper in a typewriter, a paper lantern): you give it the lamps behind the surface (`ThroughLamp`, flame, ground point, strength and size) and the things in between (`ThroughCaster`, an outline drawn at its own place, its depth, its top and bottom), and it does the rest:

- **Where:** each outline is scaled away from the lamp's flame by `t`, so a thing stays where it stands and grows the nearer it is to the lamp (at most `maxScale`, 1.6 by default: a sheet is small).
- **Softness by distance:** the penumbra widens with the thing's distance from the surface and the lamp's size (shifted copies of the outline: 6 at High, 3 at Medium, one hard shadow at Low).
- **Solid things** (`solid = true`, a metal plate) stop all the light, so they first erase every other shadow where they stand: nothing on them can be darker than they are.
- **Contact** (`contact`, 0..1): a thing touching the surface (the plate the paper lies against) is fully dark where it touches, at its bottom, and fades toward its top to `1 - contact` of that, softly, as light creeps round through the paper where the two part.
- **Darkness** follows the surface's optics (`Optics.throughShare()`: thin onionskin shows sharper, darker shadows than heavy bond).
- **Cost:** the shadows are drawn into a small picture (`ThroughShadows(scale = 0.25f)`, which also softens them) and redrawn only when a lamp, a thing or the surface changes; any other frame draws one image.

The playground's **Shadow theatre** tab is this: a paper screen in a frame, a lamp (candle, hurricane lamp or the Boitatá's fire) behind it, the characters and animals as puppets you drag between them, red or amber paper, and a Backstage view of the puppeteers' side.

## Glass that blurs, bends and dirties

`Optics` also says what a pane does to what is seen *through* it, not only to the light: `blur` (frosted or dirty), `distortion` (old hand-made glass), `haze` (a milky veil) and `tint` (stained glass). In the Window room:

- **The view.** What is outside is recorded once into an offscreen layer, drawn again in strips shifted by a slow wave (`distortion`), and the result is blurred (`blur`) by the platform's blur effect, then veiled (`haze`) and speckled with dirt. Where the platform cannot blur (Android before 12) the view stays sharp. At Low quality only the haze and dirt are drawn. A clean pane costs nothing extra.
- **The light.** The patch the window throws on the floor is tinted by the glass, dimmed by what it stops, and, when the glass scatters, drawn into a layer, blurred and added, so it lands wider and softer.
- One setting drives both, so a dirty window looks dirty through the glass and in the light it lets in. The playground's Window tab has Clean, Dirty, Frosted, Old and Stained glass.

## Liquid: reflection and refraction

A `Liquid` is a pond lying on the ground: an ellipse (a round pond seen at a slant) that is `maxDepth` deep at the middle and nothing at the shore, with `optics` for what it does to what is seen through it. The physics is in `LiquidMath`; the drawing in `drawWaterSurface`, `drawReflection`, `drawStandingInWater` and `drawGlitter`.

- **Reflection.** What stands at the edge is mirrored about its waterline, clipped to the pond, shaken sideways by ripples (strips, each shifted by a slow wave: no shader, so it is the same on every target) and fading with how far down it goes. Its strength is the water's Fresnel reflectance, Schlick's `R0 + (1 - R0) * (1 - cos)^5` with `R0 = ((n - 1) / (n + 1))^2`: about 2% looking straight down and over 60% at a grazing look, but only about 6% at our camera angle, so `Liquid.reflectance` defaults to a stylised 0.42. The moon is mirrored the same way, flattened by the slant.
- **Refraction.** What is under the surface looks nearer to it than it is, by the index (`apparentDepth = depth / n`, 0.75 for water). A thing wading in is drawn in two parts: above the surface as it is; below it squashed to `1/n` toward the waterline (its feet look higher than they are), tinted and dimmed by the water, shaken by the ripples, with rings spreading from where it meets the water. How deep it stands is `Liquid.depthAt` (a bowl: 0 at the shore, 1 in the middle).
- **Glitter.** A light over or near the water leaves a column of short bright dashes under it toward its mirror image: wider and brighter near, breaking up with the ripples.
- **Cost.** Reflections and wading are drawn in a handful of strips (14, 7 or 1 by quality), clipped to the pond; nothing else is allocated.

The playground's **Pond** tab: drag the people into the water, and watch the caiman and the capybara wade through it, the macaw reflected as it flies over, and the fire and Lampião's lamp glittering on it.

## Liquids in containers

A `LiquidVessel` is a container (sizes in meters, so the physics is the real thing's) with a `Fluid` in it (`Water`, `Tea`, `Wine`, `Milk`, `Oil`, `Kerosene`: colour, viscosity, index of refraction, cloudiness, whether it burns). Three small pieces of physics, all pure and tested:

- **The surface** (`WaveSurface`) is the one-dimensional wave equation with damping: a drop (`drop`) or a pour (`pour`) makes ripples that travel at `sqrt(g * depth) / width`, bounce off the walls and die away with the volume kept. The liquid also **sloshes**: pushed sideways (`accelerate`) or turned, it is pulled toward a slanted surface at the frequency of its fundamental mode, `omega^2 = (pi g / w) tanh(pi d / w)` (about 3 Hz for a glass, 1.5 Hz for a bucket). Thick liquids damp it more.
- **The container** (`TippingBody`) is a rectangle on a floor that tips about its bottom corner: its centre of mass is above the corner until the *tipping angle* `atan(width / height)`, so a gentle knock (`knock`) rocks it back upright and a hard one tips it over, then it falls to lie on its side and bounces a little (`angle'' = 3 g / (4 R) sin(angle - tipping angle)`).
- **Spilling.** `Tilt` works out, for any tilt, how much liquid the container holds before its level reaches the lowest point of the rim (`capacity`); anything above runs over the lip at the rate of a weir (Torricelli: it leaves at `sqrt(2 g h)`, flow `Cd * (2/3) * sqrt(2 g) * h^1.5`, h the depth over the lip), slower for viscous liquids. Volume is conserved (`spilled`).

Drawing: `drawVessel` draws the glass, the liquid at its level (staying level in the world as the glass tips) with the surface rippling, a bright surface line, and, through the liquid, **what is behind** the glass magnified by `1 + (index - 1) * 0.75` and tinted; `mirrored` draws the glossy table's reflection. `drawSpillStream` draws the stream (a parabola from the lip, as thick as `Spill.thickness` says) with its splash. `drawCaustic` draws, beside a filled glass, a faint **coloured shadow** (the glass lets the light through tinted) and a bright **caustic** where the glass focuses it, shimmering with the surface.

**Puddles.** What is spilled lands (`Spill.reach`) and joins or starts a `Puddle`: a thin film of settled thickness 3 mm (water) to 6 mm (oil) whose radius goes toward `sqrt(volume / (pi * thickness))` (a glassful makes a puddle 15 cm across), fast at first and slowly after, slower the thicker the liquid. A spilled `flammable` fluid is a fire body (`Material.SpilledOil`) in the bench's `FireWorld`: a puddle that reaches the flame catches, burns across its whole surface and is used up (the puddle shrinks with `burnt`).

The playground's **Liquids** tab: tap a glass (it ripples), drag it along the table (it sloshes), pour, drop something in, knock it over, light the spill.

## Weather on glass

A `PaneWeather` is a small simulation of one pane, stepped at a fixed rate so the same weather and seed always give the same pane (`SurfaceClimate` is the weather: temperatures inside and out, humidity, rain, snow, wind, and how much the window is open):

- **Fog.** Water condenses where the glass is colder than the room's **dew point** (`SurfaceClimate.dewPoint`, Magnus's formula: 9 degrees for 20 degrees at 50%); the glass is nearer the outside's temperature than the inside's. The colder the glass, the faster. An open window mixes outside air in and dries it.
- **Frost.** Below 0 degrees ice grows from the edges inward in a fixed pattern, and thaws above freezing.
- **Beads.** Rain lands as beads that stick until heavy enough, then run, **clearing a streak through the fog** as they go; heavy fog runs by itself.
- **Snow** piles on the sill and melts. **Heat** (`heat` at a place: a lamp by the glass) evaporates fog and melts ice there; **wiping** (`wipe`) clears the fog under a hand, and it comes back.

Drawing: `drawPaneWeather` draws the fog as a veil in a few shades (merged into runs: a few hundred rectangles, not thousands), the ice as frosted cells with crystals that grow in along stems, and the beads with a rim and highlight; `drawSillSnow` the snow. The Window room also turns the pane's state into optics (more blur, haze and scatter, less clear), so the sky blurs and the light patch softens as the glass fogs or freezes. In the playground's Window tab: Rain, Snow, Frost, Fog chips, temperature and humidity, a lamp by the glass, wipe by dragging, and an open window.

## Hot liquids, and the edge of the table

- **Temperature** (`LiquidVessel.temperature`): liquid cools toward the room's (Newton's law: `T' = -(T - room) / tau`, `coolingSeconds` is how long a vessel takes), and pouring mixes by volume (`T = (T1 V1 + T2 V2) / (V1 + V2)`: 90 degrees poured into as much again at 20 gives 55). `heat(...)` and `heatTo(...)` heat it (a plate under it); it cannot pass the boil.
- **Steam** (`steam`, 0 at 45 degrees to 1 at 95): wisps rise from the surface and sway, thicker the hotter it is. **Boiling** (`boil`, from about 80 degrees): bubbles start at the bottom, speed up as they rise and burst at the surface, stirring it a little. **Foam** from a pour (`pour(..., foamy = true)`) floats on top and fades over about a minute.
- **A mug** (`VesselShape.Mug`) is opaque ceramic: only the surface of what is in it is seen through the opening, a little below the rim.
- **The edge of the table.** A puddle that has spread to an end of the table runs off it in drops in proportion to how much of it hangs over (`Puddle.remove`); the drops fall under gravity (`Drips`, a fixed pool, nothing allocated) and join a puddle on the floor, in their own liquid. Liquid that is thrown past the end by a tipping glass falls straight to the floor.
- The playground's Liquids tab: pour hot coffee into the mug, heat a glass, slide the jug to the end and knock it over.

The plan for the next object that goes into a liquid (a folded paper strip in the coffee, for the Typist) is in [docs/design/benzedrine-coffee-plan.md](design/benzedrine-coffee-plan.md).

## Setting things down, and pouring

**Stains.** A wet foot leaves its mark. `LiquidVessel.bottomWet` (0 to 1) is how wet the outside of the foot is: what runs over the lip runs down the side to it, standing in a puddle wets it, a drink much colder than the room sweats onto it, and it dries on its own in about half a minute. `LiquidVessel.base` (`VesselBase`) is the foot: a whole disc (a bottle, a jug) or a ring of some width (the rim of a tumbler, the foot of a mug). When a container is set down (`SetDown` notices that it was moving and has come to rest) `StainField.touch` takes the wet off the foot and presses it into the surface as a `StainMark`; a foot that stands a while wet seeps a little more into the same mark, and a foot set down on its own mark adds to it instead of piling up a new one.

How strong a mark is, and how long it lasts, is the surface's business (`SurfaceMaterial`): `gain = wet * porosity * (0.35 + 0.65 * turbidity)`, so bare wood (porosity 0.8) takes a lot and glaze (0.04) almost nothing. The mark dries away at the surface's `dryRate`, down to a **permanent** part, `gain * permanence * turbidity`: coffee and wine (cloudy, coloured) stay on wood, while a ring of clear water has no permanent part and dries away. Clear water darkens the surface itself (`SurfaceMaterial.tone`); a coloured liquid leaves its own colour. `StainField` is a fixed pool (nothing allocated while it runs); when it is full the weakest mark goes. `drawStains` draws each mark as a flat ellipse, a ring for a rim foot, with a darker dried edge.

**Pouring.** `PourJob(source, target, homeX, targetX)` is a hand pouring one container into another, in four phases: it slides the source beside the target and lifts it over the rim (`LiquidVessel.lift`); it tips it (`TippingBody.hold`, which turns it smoothly and does not let it fall over) a little more while too little runs over the lip and a little less while too much, and keeps the stream on the middle of the mouth (the lip's `lipX` plus `Spill.reach` from the real fall height, down to the level in the target, `landHeight`); when the source is empty or the target nearly full it sets it upright; and carries it home. The stream that lands in the mouth is poured in (`LiquidVessel.receive`: the volume kept, scaled for the two depths), and what misses is `missed`, at `missX`, for the caller to make a puddle of. Mixing is by volume (`Fluid.mix`: colour, thickness, cloudiness, index and weight averaged; it burns if either does; the temperatures mix as in any pour), so milk into coffee is a paler, cloudier coffee. `VesselAction` is what a menu offers for a container, and `VesselWheel` is the radial menu: the choices open from the container's top along an arc with a spring (`animate = false` for people who ask for less motion).

**Held, dribbling, overflowing.** An action with `hold` (sipping, pouring) goes on for as long as it is pressed: `Sipper` takes a gulp at once and one every half second, and a `PourLevel.UntilStopped` pour runs until `PourJob.stop()` (let go), then the source is set upright and carried home. Keep pouring into a full container and it runs over the brim; it stops once it is level (the last film runs off at once, since a weir's flow dies away too slowly to look right). When a spill stops a little clings to the lip (`LiquidVessel.dribble`, taken out of the volume) and lets go in beads (`takeDribbleDrop`): from a tipped or lifted container they fall to the table or into the container beneath, from one standing home they run down the side and wet the foot, so a container that has just poured can leave a ring.

**Sipping, as seen by the drinker.** `SipJob` brings the container from where it stands in toward the camera: `SipPose` is the middle of its base in pixels, a scale and a lean toward the viewer. `PovViewpoint` is first person: it grows until its rim is about half the width of the view across, low in the middle of it, and its top leans in to the eye, so it is seen foreshortened (its height shrinks by the cosine of the lean), its opening opens into an ellipse (the sine) showing the rim, the dark inside and the surface of the liquid, and the base drops out of the bottom of the view. The scene is dimmed with dark edges (`drawSipOverlay`). The lean grows with each gulp as there is less in the glass (as you tip it up for the last of it); each gulp (`Sipper`, at once and then every half second while held) lowers the level. The vessel itself stays upright, so nothing can spill. A `Drinker` is who is sipping: `Drinker.You` looks through `PovViewpoint`, and another character in another camera position and angle is another `SipViewpoint` that gives `drinkPose`. `drawVesselAt` draws the vessel at a pose.

**Sounds.** What happens with a liquid is said, not played: `LiquidVessel.emit` tells the vessel's `events` (a `LiquidEvents`) a `LiquidCue` (what, how strongly 0..1, which vessel, how glassy). Continuous cues are sent every step while they go on with the level they are at (`Stream`: the flow over a lip or into another container; `Boil`: how hard; `Soak`: how fast water goes into soil); the rest once (`Splash` where a stream lands in liquid, `Gulp` per mouthful, `Drip`, `SetDown` when a moved container comes to rest, `Knock` when one lies down). `LiquidSounds` (in `boitata-audio-synth`) makes the sounds in code, like `Gunshot`: a gulp is two falling glugs, a drip a rising plink (a big drop is lower), a splash a burst of band-passed water noise with bubbles, a set-down a thud whose pitch and ring follow the tone (clay dull and short, glass high, longer and with an inharmonic partial), and the stream, soak and boil are seamless loops. `LiquidSoundMap` is which sound goes with which cue: the default, a `PcmBuffer` of yours (`set`), muted (`setMuted`) or louder or quieter (`setGain`). `LiquidAudioPlayer` (the `LiquidAudio` composable runs it with the scene) schedules one-off sounds on the sink's own clock the moment they are heard, no more often than a splash, drip or gulp can be told apart, pans them by where the vessel stands (`panOf`), and eases each continuous loop up quickly and down slowly with the level. The playground's Liquids tab has a Sounds row to hear or mute each kind (Sound on, up in the corner, turns the sink on).

**Watering plants.** A pot is a container that holds soil, and the soil holds the plant, and each reacts to the one before it. `Planter(pot, patch)` (in `boitata-plants`) makes the pot's `LiquidVessel` a pour target like any other: `LiquidVessel.solid` is the cross-section the soil takes up at the bottom, so what is poured in stands on top of it, `fill` is how full the headroom is, and anything over the rim runs over. Each step the standing water soaks in at `rate = (0.1 + 0.6 * drainage) * (1 - moisture)` per second (sand fast, clay slow, slower as the soil wets), raising the moisture by what the soil can hold (`poreArea = soil * 0.45 * capacity`), scaled by what the liquid is (`PlantFluids`: `hydrates = (1 - 0.6 * turbidity - 0.7 * viscosity) * (1 - 0.8 * toxicity)`), and leaving poison in the soil (`SoilMoisture.contamination`, `Fluid.toxicity`: water 0, kerosene 1, a mix in between). Soil wetter than field capacity (0.8) drains out of the hole in the bottom (`takeDrained`: the bench runs it onto the table and wets the pot's foot, so it can leave a ring). `PlantModel` does the rest: a thirsty plant's hydration rises with the moisture and it perks up, poison adds to its stress, and a soil that stays soaked for hours (`saturatedHours`) drowns a plant that wants little water. The playground's fern pot: water it from the tumbler, the jar or the jug, or pour kerosene on it, and wait an hour.

`drawVessel` draws a lifted container raised by its `lift` (its argument is now `floorY`, the surface it stands on), and `drawSpillStream` ends the stream at `landHeight`, with the splash there.

The playground's **Liquids** tab: tap a container and a wheel opens (Heat, Pour into ..., Drop in, Knock over). The coffee jar and the milk jug pour into the mug, the wine and the kerosene into the tumbler; the chips choose what the table is made of (wood, varnished wood, stone, ceramic, metal) and wipe the stains. Spill coffee on the wood, set the mug down in it and slide it away.

## Setting a liquid: viscosity, density and the rest

A `Fluid` takes every number you care about, and `Fluid.copy(...)` makes a variation of any preset (`Fluid.Coffee.copy(viscosity = 0.8f)`); a vessel's liquid can be swapped or changed at any time (`LiquidVessel.fluid`, the volume stays). The presets: `Water`, `Tea`, `Coffee`, `Wine`, `Milk`, `Oil`, `Kerosene`, `Honey`, `Mercury` (`Fluid.All`).

| Number | What it does |
|---|---|
| `viscosity` 0..1 (water 0, honey 1) | thick liquids slosh less (more damping), spill slowly (the weir flow is divided by `1 + 4 v`), and spread slowly into a thicker puddle. **Heat thins it**: `effectiveViscosity` falls by a factor `e` for every 36 degrees above 20, so warm honey flows. |
| `density` in kg/m^3 (water 1000, kerosene 800, mercury 13534) | the liquid's **weight** (`liquidMass = density * volume * depth`) weighs on the container: it lowers the centre of mass, which raises the tipping angle and changes the resistance to turning, so a glass with a little mercury in it is far harder to knock over than one with water, and a glass with a little water is steadier than an empty one. (It is also what things will float or sink in, when something is dropped into a liquid.) |
| `index` | how much the liquid magnifies and bends what is seen through the glass. |
| `turbidity` | how cloudy it is, 0 clear to 1 opaque. |
| `flammable` | whether a spilled puddle burns. |

`TippingBody.setLoad(containerMass, liquidMass, liquidDepth)` is the physics behind the density: the centre of mass is the average of the container's and the liquid's, and the inertia the sum of theirs about the corner.

## The edges of a table

A puddle runs off the table wherever it spreads past an edge: **the ends** (the drops fall beside the table), **the front** (they fall down in front of it, over the apron) and **the back** (they fall behind it, out of sight: the liquid is simply lost). How much runs off is in proportion to how much of the puddle hangs over; the drops fall under gravity (`Drips`) and join a puddle on the floor in their own liquid. A glass's coloured shadow and caustic are drawn only on the table's top, never past its ends.
