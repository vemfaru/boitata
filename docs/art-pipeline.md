# Art pipeline

How a game's pictures should be made and prepared so Boitatá draws them light and casts their shadows right. The game keeps its own recipe (sizes, names, its tools);
this is what the engine needs from any of them.

## 1. Cropped to content, with a frame

A layer drawn on a whole canvas so the layers line up (a room's shelf, a curtain, a window leaf) is mostly empty, and empty pixels cost as much memory and upload as painted
ones. Store each picture cut to its content and keep where it sat as an `ArtFrame` (`boitata-core`, `core/draw/FramedArt.kt`): the canvas size and the box of the stored part.

- Everything that places the picture keeps using the canvas's box. Draw it with `FramedPainter` (an `Image` filling the canvas's box lines up as before) or `drawFramed`.
- `tools/art/boitata_art.py crop` cuts a rendered PNG and returns the frame; an importer calls it before encoding and writes the frame next to the picture.
- A cut that keeps more than 90% of the canvas is not worth a frame; the picture stays whole.
- Two pixels of margin stay round the content, so a smoothed draw keeps its edge.

## 2. One shadow outline per thing

A shadow is cast from a picture ([lighting.md](lighting.md), receivers): each point of it is carried along the light's rays. The art itself is the wrong picture for it:

- it is big (thousands of pixels), where a shadow, soft and drawn from several points of the light, needs about 160 px;
- it holds what does not cast: steam, a flame's glow, a lit dial, smoke;
- a thing drawn as several layers casts each one in turn, and needs a layer of its own per point of the light so the overlaps do not darken.

So each thing (and each state whose outline differs: a full and an empty bottle are the same, a lamp with its shade tilted is not) gets one **outline**: black, alpha only,
160 px tall, the same box as the art's canvas. `tools/art/boitata_art.py outline` makes it from the art's layers: what is at least 160/255 opaque casts, so glows and steam
fall away and gaps (a mug's handle, a lamp's frame) stay open.

## 3. What the picture cannot say

A picture is seen from one place, so it cannot tell how deep a thing is or how it meets the room. Write these next to the art, for `ObjectFacts` (`core/light/Silhouette.kt`):

| Fact | Why | If unknown |
|---|---|---|
| Real size W × D × H (cm) | the footprint's depth sets where the casting card stands; the height checks the picture | the base is taken as deep as it is wide |
| Base | the share of the picture's height where it touches its surface | the lowest solid row (`SilhouetteMetrics`) |
| Kind | `Standing` casts its height, `Lying` only its thickness (a sheet is drawn tall because it is seen from above), `Hung` casts on its wall close behind it | `Standing` |
| Gap (hung) | how far in front of the wall it hangs | 1.5 cm |

## 4. No painted shadows

A shadow painted in the art (under a thing, on the wall behind it) doubles the one the engine casts and stays put when the light moves. Paint only the thing; a soft contact
darkening right under the base is the one exception, and it belongs in the outline's footprint, not the art.

## 5. A room's geometry

For a room drawn as a backdrop, write where its surfaces are, so its shadows land on them (`ShadowRoom`): the camera (eye height, distance, pitch), the height of each top
(a desk) and the box of it on the picture, and the line where the wall meets the floor. Without them a shadow that falls past a desk's edge cannot be put on the right floor.
