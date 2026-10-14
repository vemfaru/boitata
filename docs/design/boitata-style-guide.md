# Boitatá: art style guide

> Give this whole file to the illustrator (or image tool) before asking for any asset. Every rule below is a hard rule unless it says "may".

**Boitatá** is an open-source Kotlin library for **light, shadows, weather and time of day in 2D scenes**. Its name comes from the Boitatá of Brazilian
Tupi-Guarani folklore (*mboi tatá*, "fire snake"): a giant serpent made of translucent flame with huge lantern eyes, a **guardian of the forest** who
punishes those who burn it, whose fire **never scorches living plants**. Pronounced *boi-ta-TÁ*; always written with the accent.

---

## 1. The idea in one line

**A woodcut print of a Brazilian forest, where the only colour is the light.**

The style is **xilogravura**, the carved-woodblock print of Brazilian *literatura de cordel*: bold black shapes, carved white lines, hatching for shadow,
no gradients. Each picture is printed in **ink plus one spot colour, and that spot colour is the light of that moment** (the sun at dawn, the moon at night,
or the fire). This is also what the library does: it lights a scene. So the art shows it: change the hour or the weather, and the spot colour and the carving change.

---

## 2. Two modes

| | **Night** (default, the home of the Boitatá) | **Day** |
|---|---|---|
| Ground | Night `#0E0C0A` | Paper `#F1E6D0` |
| Line and shape | Paper `#F1E6D0` carved out of the dark ("white-line" woodcut) | Ink `#14110F` |
| Spot colour | the moon, or fire | the sun |
| Use for | logo on dark, banners, hero scenes | docs, light pages, print |

Every mark (logo, icons) exists in both modes.

---

## 3. Colour

### Base (always)

| Name | Hex | Role |
|---|---|---|
| Ink | `#14110F` | warm black: all line and mass in Day mode |
| Night | `#0E0C0A` | the dark ground in Night mode |
| Paper | `#F1E6D0` | aged cream: ground in Day mode, lines in Night mode |

### Fire (the one colour that is *always* the Boitatá's)

| Name | Hex | Role |
|---|---|---|
| Ember | `#C2410C` | the flame's outer body, the tail, sparks |
| Amber | `#F2A83B` | the flame's main tone, the brand accent |
| Heart | `#FFF1C2` | the hottest core, the eyes' light, lightning |

**Amber, Ember and Heart mean fire (or the Boitatá). Nothing else is ever those colours.**

### Light of the hour (the spot colour; use exactly one per picture besides fire)

| Moment | Spot colour | Hex | Sky treatment |
|---|---|---|---|
| Dawn | Coral | `#E58C7A` | low sun on the horizon, long horizontal bands |
| Morning | Pale gold | `#F2C14E` | sun climbing, light bands thinning |
| Noon | Bone | `#FFF3C4` | sun high and small, almost plain paper, hardest ink |
| Dusk | Burnt orange + violet | `#D9622B` + `#5B3A6B` | the only moment with two spot colours: orange at the horizon, violet above |
| Night | Moon blue | `#A9B8E8` | moon, stars, cold rim light on leaves |

### Living green

| Name | Hex | Role |
|---|---|---|
| Leaf | `#2F5D3A` | **only** on plants touched by firelight, to show the fire does not burn them. It is the one saturated green. Everything else in the forest is Ink or Paper |

---

## 4. The carving (how things are drawn)

Everything looks **cut from wood**, then printed.

**Line weights** (ratio 4 : 2 : 1, shown at a 1024 px canvas)

| Name | Weight | Used for |
|---|---|---|
| Heavy | 12 px | outlines, trunks, the serpent's contour |
| Medium | 6 px | branches, details, carved lines inside shapes |
| Fine | 3 px | hatching, rain, grass |

- **Gap between hatch lines is at least 2× their own width** (so they never clog at small sizes).
- **Line ends are cut, not rounded**: slightly tapered, with a small irregularity (±8% in width along a stroke), like a gouge. Never perfectly uniform, never wobbly.
- **No gradients, no blur, no soft shadows, no 3D, no drop shadows.** Tone is made only by **hatching density** (more lines = darker) and **stippling** (dots).
- **A subtle paper grain** (≤ 6% opacity) over the whole picture is allowed in scenes and banners, never in logos or icons.
- Edges of shapes are **slightly rough** (a 1–2 px bite here and there), never smooth vector-perfect, never torn.
- **Glow** around fire is drawn as **concentric carved rings or radiating lines in Amber and Ember**, not as a blur.

---

## 5. The Boitatá (the character)

**It is a symbol first.** It must still read at 16 × 16 px and in one colour.

Unit **T** = the thickness of the body. Every other measure comes from T.

| Part | Rule |
|---|---|
| **Body** | One continuous band of **even thickness T**. It never tapers into a thin tail |
| **Length / pose** | About **14 T**, coiled in **2 to 2.5 turns** (symbol), or in an S-curve with **at least 2 bends** (scenes). No pose is a single arc: that would look like a tadpole or a comma |
| **Head** | A **wedge**, never a ball: **2.2 T long, 1.8 T wide** at the back, tapering to a flat snout. Raised, looking forward. No fangs, no open jaws, no tongue |
| **Eyes** | Two perfectly **round** eyes, each **0.7 T** across, **0.9 T** apart, set in the back half of the head. The **brightest thing in every picture**: Heart `#FFF1C2` with an Amber rim. In one-colour versions they are knocked out as holes |
| **Flame body** | Translucent fire shown as **two nested tones**: the outer band in Ember, a lighter inner band of width **0.5 T** in Amber, centred. A fine carved line runs along the inner band |
| **Gradient along the body** | Not a gradient: the outer band is Amber near the head and **steps** to Ember over three or four segments toward the tail |
| **Tail** | Ends in a small **flame tip**, 1.5 T long, curling up |
| **Crest** | **Four flame tongues** rise from the back behind the head, heights **0.6 T, 0.9 T, 0.7 T, 0.5 T**, teardrop-shaped |
| **Pose** | Calm and watchful, never attacking. Head level or slightly raised |

**Never:** scales, a ball-shaped head, a thin tapering tail, a single-arc body, fangs, a leaf (the meaning lives in the posture and the lit eyes), any human or feather imagery.

---

## 6. The forest

Brazilian vegetation only. **No pine, fir or conifer-cliché triangles.** The mixed forest at night:

| Plant | Proportions (of its own height H) |
|---|---|
| **Araucária** (Paraná pine) | bare trunk 60% H, then a **flat umbrella crown** 40% H, as wide as 0.9 H, of upturned branches |
| **Palm** (buriti, açaí) | slim trunk 70% H, a burst of **7 to 9 arching fronds**, spread 0.7 H |
| **Sumaúma** (kapok) | trunk 1/5 H wide, flaring at the base into **buttress roots** 1/2 H wide; high flat crown |
| **Ferns, bromeliads** | clumps 0.1 H, fanned leaves, always at the foot of trees |
| **Vines (cipós)** | long vertical curves hanging from branches, Medium line |
| **Embaúba leaves** | large hand-shaped leaves, only in the near foreground |

Trees are **carved silhouettes**: solid Ink (or Paper in Night mode) with a few carved white lines for bark and leaf veins. Trees near the fire take **horizontal Amber hatching on the side facing it**.

---

## 7. Showing each feature (the state matrix)

The library's features are shown **with the carving itself**: the *direction* and *density* of the lines say what is happening.

### Weather

| State | Drawn as |
|---|---|
| **Clear** | plain paper (Day) or stars as small carved diamonds (Night); no hatching in the sky |
| **Cloudy** | scalloped cloud shapes with **horizontal Fine hatching** underneath |
| **Rain** | **short slanted Fine lines**, 12° from vertical, in Paper (Night) or Ink (Day); closer and thicker in front, thinner behind |
| **Storm** | dense rain plus a **zigzag bolt** in Heart with an Amber edge; the sky gets heavy **cross-hatching**; the whole picture gets a Heart flash line |
| **Snow** | **carved dots** of three sizes, drifting, with a few short slanted strokes of wind |
| **Fog** | **horizontal bands of stipple** (dots) between the tree layers, thicker low; layers behind are drawn lighter |
| **Wind** | **long curved flow lines** (the cordel swirl), trees bending the same way, leaves and sparks streaming along them, **the flame leans** the same way |

### Time of day

The **sky** is a flat tint of the hour's spot colour printed in **horizontal bands** (3 to 5 bands, each a little denser than the one above), never a gradient. The sun or moon is a carved circle with **radiating Fine lines**. **Stars** (Night only) are tiny carved diamonds. Shadows (below) get **longer toward dawn and dusk and shortest at noon**.

### Fire

Flame = **teardrop tongues in three layers** (outer Ember, body Amber, core Heart) with **carved vertical lines** inside. **Embers** are tiny Amber diamonds rising. A fire always has a **ring of concentric carved arcs** around it showing its glow, and the trees and stones near it get Amber hatching on their lit side.

### Light through a window

A dark room; the lit patch on the floor is a **parallelogram in the hour's spot colour, divided by the bars of the window frame** into panes, with a few **dust specks** (dots) in the beam. The beam is long and leaning at dawn and dusk, short and upright at noon.

### Cast shadows

Shadows are **solid Ink (Day) or deep Night-brown (Night) hatched silhouettes** laid on the ground **away from the light**, long when the light is low or near, short when it is high. They are the library's trademark: **show them whenever there is light**.

### Parallax and depth

Scenes are drawn in **separate layers, far to near**, so they can move at different speeds:
1. Sky (hour's spot colour in bands, sun or moon, stars)
2. Far tree line, in mist (lighter, fewer carved lines)
3. Mid trees (the ones that cast shadows)
4. The subject (the fire or the serpent) with its glow rings
5. Near foliage framing the picture, almost solid
6. Weather on top (rain, fog, snow, wind lines)

**Far = lighter and fewer lines; near = darker and bolder lines.**

---

## 8. Proportions of a scene

For any scene canvas of height **H**:

| Element | Rule |
|---|---|
| Horizon | at **62% H** from the top |
| Far tree line | up to **14% H** tall, base on the horizon |
| Mid trees | **28% to 42% H** tall, bases between 66% and 76% H |
| Near foliage | frames the **left, right and bottom 20%** only; the centre 60% stays open |
| Moon / sun | diameter **5.5% H** (noon sun 4%), placed in the upper 35% |
| Fire | flame height **18% to 45% H**; glow rings out to **1.1 × flame height** |
| Serpent (scene) | body thickness T = **4.5% H** |
| Shadows | length = object height × (1.6 − distance to light ÷ light reach), minimum 0.25 |

---

## 9. Logo

| Piece | Rule |
|---|---|
| **Symbol** | The serpent coiled in a circle (about 72% of the canvas), head raised at the top, two lantern eyes. Heavy contour, two-tone flame inside. No text inside |
| **Canvas** | 1024 × 1024; symbol diameter **720**; padding **152** (15%) on every side |
| **Wordmark** | `boitatá` lowercase, in a **sturdy carved-feeling display face** (e.g. Alfa Slab One or Rozha One), accent kept; Amber on Night, Ink on Paper |
| **Lockup** | symbol left, wordmark right, gap = **20% of the symbol's width**, 8% margin; canvas **2400 × 800** |
| **One colour** | a version in Amber only on Night, one in Ink only on Paper; the eyes become holes |
| **Small** | a 32 × 32 favicon that is the silhouette and the two eyes only |
| **Never** | stretch, add a leaf, recolour, put on a busy picture without a dark scrim, use blur or gradients |

---

## 10. Icons

128 × 128 grid, **12 px padding**, **Heavy 4 px strokes** (the cut look), cut line ends, one colour (Paper on Night / Ink on Day) with **the fire parts in Amber**.
Set of 12: sun, moon, rain, snow, fog, storm, wind, fire, window, shadow, lantern, clock (hour of day).
Same visual weight each; no fills except flame.

---

## 11. Type

- Wordmark and headings: a sturdy display face with some hand-cut character (Alfa Slab One, Rozha One or similar), lowercase for the wordmark.
- Body and captions: **Inter**. Code: **JetBrains Mono**.
- Night mode text is Paper `#F1E6D0` with Amber for emphasis; Day mode text is Ink. Never pure white or pure black.

---

## 12. Respect

The Boitatá belongs to the Indigenous peoples of Brazil first. Credit it as **Tupi-Guarani** folklore. **No feather headdresses, war paint or stock "native" imagery**, and no one dressed up as
any of it. The serpent is a guardian, never a monster to be slain.

People are welcome when they come from the tradition the woodcut itself comes from: the **cordel** of Northeastern Brazil. **Lampião**, **Maria Bonita** and the **caçador** (a sertão hunter)
are drawn as cordel woodcuts, with respect: real people of the sertão, their hats, belts and sandals right, never a caricature, never a joke. The same goes for the animals (onça-pintada,
tamanduá-bandeira, capivara, lobo-guará, jacaré, tatu, arara, tucano): drawn as themselves, in side profile, not as cartoons. Nobody hunts the serpent, and no animal is shown hurt.

---

## 13. Assets to make

| # | Asset | Size | Notes |
|---|---|---|---|
| 1 | Logo symbol, Night + Day + one-colour | 1024² | section 9 |
| 2 | Lockup, Night + Day | 2400 × 800 | section 9 |
| 3 | Small marks: app icon 512², avatar 500², favicon 32² | | |
| 4 | README banner | 2400 × 800 | the serpent in the forest; left third calm for the lockup |
| 5 | Social preview | 1280 × 640 | 5% safe margins |
| 6 | Feature cards (Weather, Window light, Shadows, Fire, Play) | 1200 × 675 | one per feature, section 7 |
| 7 | **Time of day set**: dawn, morning, noon, dusk, night | 1920 × 1080 | the same forest, five hours; only the spot colour, sky bands and shadow length change |
| 8 | **Weather set**: clear, cloudy, rain, storm, snow, fog, wind | 1920 × 1080 | the same forest at one hour |
| 9 | Scene layers (sky, far trees, mid trees, near foliage), transparent | 1920 × 1080 each | section 7 |
| 10 | Props, single silhouettes with feet at the bottom centre | 512² each | araucária, palm, sumaúma, fern, bromeliad on log, stone ring, lantern, window with bars, crate; Lampião, Maria Bonita, a caçador and Lampião's lamp; onça-pintada, tamanduá-bandeira, capivara, lobo-guará, jacaré, tatu, arara, tucano |
| 11 | The serpent, hero art, transparent | 1920 × 1080 | S-curve through the frame |
| 12 | Icon set (12) | 128² | section 10 |
| 13 | Blog cover | 1600 × 900 | the serpent logo + a typewriter silhouette lit by it |

---

## 14. Prompts

Paste **Prompt 0** once at the start of each session; then use the others one by one. Attach your folklore reference to prompts 1 and 11 (it is only for the *identity*, never to copy).

**Prompt 0 (style).** `Follow the attached Boitatá art style guide exactly: Brazilian cordel woodcut (xilogravura), ink plus one spot colour that is the light of the moment, carved hatching instead of gradients, the Boitatá as a bold even-thickness serpent with a wedge head and two round lantern eyes, Brazilian vegetation only (no pines), and the palette and proportions it gives.`

**1. Symbol.** `Design the logo symbol, 1024x1024, Night #0E0C0A ground, no text. Woodcut style, Paper-coloured carved contour. The Boitatá coiled in 2 to 2.5 turns inside a circle of 720 px, body thickness T=60 px, wedge head raised at the top, two round lantern eyes 42 px across in Heart #FFF1C2 with an Amber rim, flame body in two nested tones (outer Ember stepping to Amber near the head, inner Amber band of half the thickness), four flame tongues of heights 0.6T, 0.9T, 0.7T, 0.5T along the back, tail ending in a small curled flame tip. Calm, watchful. Give 4 variations.`

**2. Mono + Day.** `From the chosen symbol make: Amber-only on Night; Night-only on Paper #F1E6D0 with the eyes as holes; full colour on Paper (Ink contour, Ember and Amber flame). Same geometry.`

**3. Lockup.** `Make the 2400x800 lockup per the guide: symbol left at 70% of the height, the word "boitatá" in a sturdy display face (Alfa Slab One), accent kept, gap 20% of the symbol's width. One on Night with Amber text, one on Paper with Ink text.`

**4. Banner.** `README banner 2400x800: a night forest in woodcut style, layered far to near (Moon blue #A9B8E8 sky in horizontal bands with carved stars and a moon; far tree line in mist; mid araucárias, buriti palms and one sumaúma; near foliage framing the edges). The Boitatá in a long S-curve through the middle trees lights the nearest trunks with Amber hatching on the side facing it, which throw long hatched shadows away from it; a few Leaf #2F5D3A leaves touched by the firelight, unburnt. Left third calm and dark for the lockup.`

**5. Time of day set (do 5 frames).** `Take the same forest scene and draw it at dawn, morning, noon, dusk and night. Change ONLY: the spot colour (Coral, Pale gold, Bone, Burnt orange with violet above, Moon blue), the horizontal sky bands, the sun or moon position (carved circle with radiating lines), and the length and direction of the hatched shadows (long at dawn and dusk, shortest at noon). Keep trees, ground and composition identical. Day frames use Paper ground and Ink lines; the night frame uses Night ground and Paper lines.`

**6. Weather set (do 7 frames).** `Take the same forest scene at dusk and draw: clear; cloudy (scalloped clouds with horizontal Fine hatching); rain (short slanted Fine lines); storm (dense rain, heavy cross-hatched sky, a zigzag bolt in Heart with an Amber edge); snow (carved dots of three sizes); fog (horizontal stipple bands between tree layers, far layers lighter); wind (long curved flow lines, trees and the flame leaning the same way, leaves and sparks streaming). Keep trees and composition identical.`

**7. Window light.** `A dark room in woodcut style with a four-pane window; the lit patch on the floor is a parallelogram in the spot colour divided into panes by the frame's bars, with dust specks in the beam. Draw it at dawn (long and leaning), noon (short and upright) and dusk (long and leaning the other way). The furniture (a plant, a stool, a standing lamp) throws long hatched shadows away from the window.`

**8. Fire.** `A fire in the dark in woodcut style: teardrop tongues in three layers (Ember, Amber, Heart) with carved vertical lines, tiny Amber diamond embers rising, a ring of concentric carved arcs showing its glow, a circle of stones with Amber hatching on the lit side and long hatched shadows away from the fire. Make three: campfire, the Boitatá's fire, and the fire leaning in the wind.`

**9. Layers (4 transparent files).** `Draw the forest scene as four separate transparent layers, 1920x1080 each: far tree line in mist (up to 14% H, fewer lines); mid trees (7 to 9 standalone araucárias, buriti palms and one sumaúma, bases at 66-76% H, 28-42% H tall, feet clearly at the bottom); near foliage (embaúba leaves, ferns, vines, a huge trunk at the far left, centre 60% empty); sky (bands of the night spot colour, carved stars, moon).`

**10. Props.** `A sheet of 9 single silhouettes in woodcut style, each in its own 512x512 frame with feet at the bottom centre and 10% padding: araucária, buriti palm, sumaúma trunk with buttress roots, fern clump, bromeliad on a log, ring of 7 stones, oil lantern on a post, four-pane window with bars, a wooden crate.` Then the people, the lamp and the animals, as Brazilian cordel woodcuts in one flat Ink with white gouge cuts: `Lampião the cangaceiro, Maria Bonita and a sertão caçador with a shotgun on his shoulder, frontal, each with one empty closed fist hanging clear of the body to hold a lamp; a hurricane kerosene lamp (a lampião) whose glass is a clear window; and in side profile facing right an onça-pintada, a tamanduá-bandeira, a capivara, a lobo-guará, a jacaré, a tatu, and an arara and a tucano each perched on a branch stump.` The full prompts, the export and the anchors are in [playground-assets-prompts.md](playground-assets-prompts.md), group 6.

**11. Hero serpent.** `1920x1080, transparent: the Boitatá alone in a long S-curve with at least two bends, body thickness 4.5% of the height, flame in two nested tones, wedge head raised, two round lantern eyes as the brightest points, a flame tip tail, four crest tongues, Amber-and-Ember rings of carved arcs for its glow, tiny diamond embers drifting off it.`

**12. Icons.** `A set of 12 icons on a 128x128 grid, 12 px padding, 4 px cut-end strokes, Paper on Night with the fire parts in Amber: sun, moon, rain, snow, fog, storm, wind, fire, window, shadow, lantern, clock. Same weight; no fills except flame.`

---

## 15. Checklist before anything ships

- [ ] Reads as a woodcut: hatching and cut lines, no gradient, no blur, no soft shadow.
- [ ] One spot colour for the light of the moment, plus fire. Amber/Ember/Heart used only for fire.
- [ ] If the Boitatá is present: even body, wedge head, two round eyes that are the brightest point, no tadpole shape.
- [ ] Brazilian vegetation, no pines.
- [ ] Shadows fall away from the light and are shown wherever there is light.
- [ ] Far layers lighter, near layers bolder.
- [ ] No Indigenous costume imagery.
- [ ] Reads at a quarter of its size, and in one colour (marks).
