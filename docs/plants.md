# Plants

Plants that live with the hour, the weather and the wind. Everything is pure arithmetic on `boitata-core`'s sky and wind, so the same days give the same plants and a day can be scrubbed.

## The pieces

- `DayClock` (core): a day number and an hour. `SkyModel` only knows the hour; anything that lives longer than a day needs this.
- `PlantSpecies`: what a kind needs (`lightNeed`, `waterNeed`, `heatTolerance`) and how it is built (`stiffness`, `leafFlex`, `phototropism`, `growthPerDay`). Presets: `Fern`, `Grass`, `Flower`, `Succulent`, `Palm`, `Pine`.
- `Soil`: `capacity`, `drainage`, `fertility`. Presets: `Sand`, `Loam`, `Clay`.
- `PlantEnvironment.of(sky, windStrength)`: the few numbers the plant reads (daylight, light on the plant, which side it comes from, rain, heat, wind). A low sun gives less light than a high one even when clear.
- `PlantModel.step` / `stepSoil`: the rules. `PlantPatch` holds a bed (one soil, several plants) and steps both together without allocating.

## What happens

- Rain fills the soil; drainage, sun, heat and wind empty it; the plants drink.
- A plant fills toward what the soil gives (it needs `waterNeed` of it); hot sun empties it.
- Stress is the worst of: drought, a drowned root (a wet soil for a plant that wants little), too little light by day, too much light for a plant of the shade, and heat.
- Only a healthy plant grows, and only by day, in a soil with fertility.
- Leaves turn toward the side the light is on, slowly.

## Drawing

`drawPlant(state, species, daylight, feetX, feetY, bend) { ... }` scales the drawing to its growth, droops it if thirsty (or at rest at night) and leans it toward the light, around the foot. Take `bend` from `species.windResponse(state.turgor, wetLeaves).bend(wind, seconds, place)`: a thirsty plant is floppier, and wet leaves are heavier. Colour it yourself from `state.stress`. Casting its shadow is the light module's job (`CastShadow` with a height of `state.growth` times the full height).

```kotlin
val day = rememberRunningDay(DayClock.at(7f), hoursPerSecond = 0.5f)
val patch = remember { PlantPatch(Soil.Loam, listOf(PlantSpecies.Fern, PlantSpecies.Flower)) }
TendPlants(patch, day, weather = { SkyWeather(cloud = 0.3f) }, windStrength = { 0.2f })
```

Read `day.clock` only in draw blocks: it changes every frame.

## The moon

`MoonPhase.of(day.clock)` gives the moon for the calendar day, so a long run shows it waxing to full and waning again every 29.53 days. Use `rememberSmoothMoon(target)` when the phase can be changed by hand: the lit part morphs into the new phase over about a second.

## Where it fits

A plant has a size: `PlantSpecies.matureHeight`, `spread` and `hang`, in metres. `species.fit(Surface(width, depth, clearanceAbove, hangRoom), PotSize(width, height), growth = 1f)` says whether a potted plant can stand on a surface and, if not, `TooTall` (the pot and the plant would touch the board above), `TooWide`, `TooDeep` or `HangsTooFar` (trailing stems with no room below the edge). The game builds the `Surface` from its own shelves, desks and floor; the rule is here so every game asks the same question. It checks full size by default, so a plant does not grow into the board above it later; pass `growth` to ask about the plant as it is now.
