# Plan: the folded strip in the coffee (Benzedrine, 1930s)

Not built. This records what the liquid work already provides, what is missing, and the order to build it in, so the strip can be added without reworking the liquids.

**The thing.** A Benzedrine inhaler was a small tube holding a folded paper strip soaked in amphetamine; people took the strip out of the tube and dropped it into coffee. In the game that is an object the player pulls from its capsule and drops into the cup, and watches steep. It is a period detail in a 1930s typewriter-career story; it is shown as paper soaking in coffee, nothing more.

## What happens to a folded strip in hot coffee

1. **It falls and lands.** It drops from the hand with a little air drag and hits the surface: a splash and rings (`LiquidVessel.drop`, already there).
2. **It floats, then soaks.** Dry paper floats (air in the fibres, surface tension). Liquid creeps into it by capillary action, which goes as `sqrt(time)` (the Washburn equation), faster the hotter the coffee (about twice as fast per 10 degrees). As it soaks its density rises from about 700 to over 1100 kg/m^3: when it passes the coffee's it sinks (Archimedes), slowly, turning as it goes (Stokes drag).
3. **It unfolds.** The folds are hinges with a rest angle; wet paper loses its stiffness, so the rest angle relaxes toward flat and the strip opens as it sinks.
4. **It darkens and turns translucent**, its edges soften, and any print on it bleeds a little into the coffee.
5. **It gives up what is in it** (the `dissolved` fraction goes 0 to 1 by soaking and temperature). Dark coffee hides colour, so the cue is a faint fizz of small bubbles and a pale wisp around the strip, and the state itself is what the game uses.
6. **It comes to rest** on the bottom, curled and flat, and stays there.

Stirring with a spoon carries it round and dips the surface in the middle (a vortex: the surface is a paraboloid, `h = w^2 r^2 / 2g`), and speeds up the steeping.

## What the liquids already give us

- A vessel with a surface that ripples and a level that rises as things are added (`LiquidVessel`, `WaveSurface`).
- Temperature that cools and mixes (`temperature`, `coolingSeconds`), steam, foam: so "hot enough to soak fast" is a number we already have.
- A mug that shows only the surface through its opening (`VesselShape.Mug`), where a drop or a strip would be seen to land.
- Splash and ripples (`drop`), bubbles (`bubbles`), colour and cloudiness per fluid.

## What is missing

1. **Things in a liquid** (a general `Immersed` body): a position and velocity inside the vessel, a density, a size, buoyancy (`rho_liquid * g * V - m g`), drag, resting on the bottom and the walls, being carried by a swirl, and a hook that gets the temperature and the time. The strip is one; a sugar cube, a biscuit, an ice cube, a lump of coal in a lamp are the same machinery with different rules (soak, dissolve, melt).
2. **A soft body for the strip**: a chain of 4 to 6 short rigid segments joined by hinges with a rest angle, so it can be folded and unfold. Cheap (a few dozen floats), stepped with the liquid.
3. **Absorption and dissolving as state**: `wetness` 0..1, `dissolved` 0..1, driven by the Washburn rate and temperature, and exposed so the game can read them (`steeping`, and callbacks such as `onSoaked`, `onDissolved`).
4. **A stir** (`vessel.stir(x, speed)`): an angular velocity that decays with viscosity, carries immersed bodies round, and dips the surface in the middle.
5. **Drawing an object through the liquid**: what is under the surface is drawn as `drawStandingInWater` does it (refracted, tinted, squashed to `1/n`), but inside the vessel's polygon. For a mug only the part near the surface is visible.
6. **Art** (to make when we get there): the inhaler tube, closed and with its cap off; the folded strip in three states (folded, half open, wet and translucent); optionally the strip's printed label. Until then, drawn in code.

## Order, each step tested and committed on its own

1. `Immersed` bodies in a vessel: drop, float, sink, rest on the bottom, splash, buoyancy; tests (a body denser than the liquid sinks, a lighter one floats, volume of the liquid rises by what is put in).
2. The folded strip: hinged segments, unfolding as it wets; tests (the rest angle relaxes with wetness, hotter is faster).
3. Soaking, dissolving, and the state exposed to the game; the fizz and the pale wisp.
4. Stirring: the vortex, the dip, carrying the strip.
5. The art, and a coffee-making scene: pull the strip from the capsule, drop it in, stir, watch.

## Questions to settle before building

- **Folded how?** Concertina (zigzag) or rolled? It decides the hinge layout and the art.
- **What does the game read?** A single `steeping` 0..1 (and when it is fully dissolved), or the temperature and time as well.
- **How fast?** Real steeping takes minutes; the game may want seconds. `Immersed` takes a time scale like the fire does.
