# Focus: one thing sharp, the rest behind glass

A scene often has to say "this one": the thing a wheel is open on, the thing the mouse rests on, the thing being inspected. The engine does it the way a camera does, with depth of field: the thing in focus stays sharp at its own place, and everything else is blurred and darkened a little. Package `br.com.faru.boitata.scene.focus`.

## The pieces

- **`FocusStrength(radius, darken)`** is how far the rest is pushed back. The app keeps its own strengths (a light one while a wheel is open, a full one while something is inspected); the engine only eases between them.
- **`Modifier.focusBlur(strength, motion, wash, blurEnabled, edges)`** blurs and darkens one layer. Everything animated is read in the draw phase, so changing the focus recomposes nothing under it. The blur comes in over 200 ms and goes over 160 ms (`FocusMotion`); a change of strength (light to full) eases the radius; under reduced motion it lands at once. The darkening only falls where the layer draws something.
- **`FocusPasses(focus, strength, scene, sharp, front)`** draws a scene with one thing in focus: the whole scene blurred, then the thing drawn sharp again at its place, then what stands in front of it blurred again, so a thing in front stays in front. When the focus moves from one thing to another the blur stays and the two cross-fade over 120 ms (`rememberFocusFade`): the blur never flickers.
- **`HoverIntent`** says what a mouse means: over a thing for 80 ms is a *preview* of it (sweeping across a room does not flicker through everything), resting still on it for 650 ms is a *dwell*, as good as a click. Leaving to nothing ends the preview at once. Touch is left alone: a tap is the only way to focus there. `rememberHoverIntent` runs it on the scene's real time (so it works under reduced motion), and `Modifier.hoverIntent(state) { position -> whatIsHere }` feeds it from the pointer.
- **`PopMotion` / `rememberPopReveal`** make a set of items (a wheel's keys) pop in one after another: each grows from 60% with a small overshoot and fades in, 24 ms after the one before, a later group 60 ms after the first; labels follow 30 ms after their key. Closing pops them out last first, 15 ms apart. The scale, opacity and label offset of each item are read in the draw phase.

## Where a blur cannot be drawn

`blurSupported()` (in `boitata-core`) is false on Android before 12, where `BlurEffect` does nothing. There, and whenever the scene's quality governor has stepped down to `Quality.Low` because frames run long, `focusBlur` keeps only the darkening. The focus still reads; it only loses the softness.

## Cost

Each blurred layer is one offscreen pass. `FocusPasses` costs the scene once blurred, the thing in focus once sharp, and what is in front of it once more; with no focus it is the scene drawn once. A scene that does not move could cache its blurred passes; the engine does not yet, because most scenes here move (flames, rain, ripples).
