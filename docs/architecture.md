# Architecture

Dependencies point down only.

```
boitata-light ───> boitata-core
      │
      └──────────> boitata-scene

boitata-weather ─> boitata-core
```


## Rules
- A module depends on other Boitatá modules and Compose only. No game names, colours, fonts, art, resources or rules: the game passes its art and sizes in.
- Anything that can be maths is maths: `SkyModel`, `StormModel` and `Perspective` are pure and unit-tested, and the Compose layers only draw them.
- Everything in `commonMain` must work on wasmJs.

## Weather and light
`LocalSceneFlash` (in `boitata-light`) carries the flash of lightning; `rememberStorm` (in `boitata-weather`) produces it and the game provides it.
So the light shades the room for a strike without light and weather depending on each other.

## Planned
- `Atmosphere.at(hour, weather)`: one pure description (ambient colour, key light, shadow softness, fog, particle rates) that weather renders and light consumes.
- Weather shaders (rain, snow, fog, steam) with static fallbacks.
- Movable props with depth-sorted layout.
- `boitata-physics`: fixed-step loop, springs, particles.
