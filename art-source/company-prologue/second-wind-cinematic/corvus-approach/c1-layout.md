# C1 CORVUS: shared layout for every layer

Shot C1 of `storyboard-v3.md`: about 38 seconds, 1920x1080, 24 fps. Every
painted layer obeys this sheet, so the pieces composite into one camera.

## Camera and light

- **Camera:** a 50 mm lens on a 36 mm sensor, so the focal length is
  **F = 2667 px** at 1920x1080 (the same lens `render_planet.py` and
  `flotilla_guides.py` render through). The camera sits about 36,500 km above
  Jangala's surface, at the outer customs station, above the lanes' orbital plane
  and looking **44 degrees down across it**. Jangala is a whole distant globe,
  centre near (1290, 560) and about 760 px across. The camera pushes in slowly
  (scale 1.00 to 1.07) and trucks right; nothing rotates.
- **Sun:** Corvus, `c1_core.SUN_IN_CAMERA` = (-0.62, 0.50, 0.50) in camera space:
  upper left and toward the camera. In the orbital plane's frame that is high
  overhead (about 49 degrees up) and to the left, so a hull's **dorsal deck and
  left faces are lit, and the flanks turned toward the camera are in shadow**.
- **Vacuum:** there is no ambient light and no air. Shadow sides go to near
  black. The only fill is a faint cool planet-shine on surfaces facing Jangala,
  which is behind the traffic and right of centre, so it touches right-facing
  shadow sides (about 3% of the key). Nothing gets distance haze or blue fog:
  a far hull is only smaller, never paler.
- **Self-lit:** engine bells, running lights, beacons, windows and the station's
  teal rings. Composited as separate additive glows so they can flicker and blink.
- **Look:** grounded photographic science fiction. Tactile, weathered industrial
  metal, believable scale, restrained colour. Approved surface-style target:
  `../realism-lookdev/` and `../../second-wind-design-v3.reference.png`
  (materials only). No toy, low-poly or cartoon look. No lens flares, no text.

## Scale and distance

Every body has a real size and a camera distance Z. Its screen size is
`F x size / Z`, and its parallax rate (the compositor's depth) is

    depth = 0.04 + 171 m / Z

which is a camera that pans a little (the 0.04 every body shares, the planet's
rate) and trucks about 9 m over the shot (the 171 m / Z term). It puts Second
Wind at 0.6 when it is 306 m away, as `c1_ship.py` has it, and leaves anything beyond about 20 km
effectively riding with the planet, as it physically should. `c1_traffic.py`
projects each body from a camera-space trajectory every frame, so its size,
position and depth come from one Z and cannot disagree.

Sprite pixels in the installed game are read as metres (as in
`flotilla_guides.py`), except the station, whose sprite is a game icon.

| Body | Size | Distance | On screen | Depth |
|---|---|---|---|---|
| Jangala | 6,000 km radius | 42,500 km to centre (36,500 km to the surface) | 760 px globe; 16 km per px at its centre | 0.040 |
| Descent shuttles leaving the keel | 60–70 m | 22 → 60 km over a burn | 8 → 3 px, closing on the globe's centre | 0.048 → 0.043 |
| Jangala Station (outer customs station) | 2.4 km tall | 22 km | 290 px tall, over the globe's day side | 0.048 |
| Inbound and outbound lanes, parked ships | 60–400 m | 19–26 km | 6–45 px | 0.046–0.049 |
| Far flotilla wedge (Eagles, Enforcers, Lashers) | 94–217 m | 11 km | 23–53 px | 0.055 |
| Ox tug | 78 m | 9 km | 23 px | 0.059 |
| Mid flotilla (Onslaught, two Dominators) | 175–353 m | 7.6 km | 75–125 px | 0.062 |
| Near Tarsus, outbound | 190 m | 3.5 km | 145 px long | 0.088 |
| Hero Atlas, inbound | 384 m | 3.6 km | 285 px long | 0.087 |
| Hero Hegemony Dominator | 175 m (210 m beam) | 2.5 km | 190 px long | 0.107 |
| Second Wind (entrance) | 65.6 m (0.65 m per hull unit) | 131 m to about 1,530 m | about 1340 to 115 px long | 1.35 to 0.15 |

**Why the station is out here.** At Jangala's distance a 2.4 km station would be
0.15 px, so a station that reads as a structure cannot hug the limb: it is 22 km
from the camera and 36,000 km in front of the planet. That is the story's outer
customs station, where the inbound lane is checked, the picket holds, and the
hail comes from. Its occlusion of the bright globe is what shows the distance.
The lanes converge on it, so their traffic is station-scale small (6–45 px) and
reads as small hulls and running lights. The flotilla is a picket a few
kilometres out, between the camera and the station, which is why it is larger
than the lane traffic and smaller than the hero hulls.

**Second Wind.** Its constants belong to `c1_ship.py`: 0.65 m per hull unit, so
about 66 m long, a small converted mining escort. It is from 131 m to about
1,530 m away and is at parallax 0.6 at 306 m. This sheet uses the same
`depth = 0.04 + 171/Z` and width `F x length / Z`, so every other hull is sized
against it. A Lasher (94 m) is half as long again as Second Wind, an Enforcer
(135 m) is twice as long, the hero Dominator (175 m) almost three times, and the
Atlas (384 m) and Onslaught (353 m) more than five times. Vanilla sprite pixels
are read as metres throughout.

## Layers, back to front

| Layer | Depth | Screen footprint at 1920x1080 | Source |
|---|---|---|---|
| Star sky | 0.03 | Full frame plus overscan | `assets/star-sky-v1.png` |
| Jangala (3D sphere) | 0.04 | Whole globe, centre near (1290, 560), about 760 px across | `layers/planet.png` from `render_planet.py` |
| Orbital traffic lights | 0.04 | Faint blinking points over the globe | `c1_traffic.py` |
| Jangala Station | 0.048 | Centred near (1095, 352) over the day side, 290 px tall; beacons, arm strobes, approach chase lights | `assets/station-v2.png`, graded |
| Lanes: inbound, outbound, descent, parked, arrival | 0.043–0.049 | Inbound from lower left rising to the station; outbound down-right across the night side; shuttles from the keel toward the globe | `assets/traffic-lane-v1-*.png` from `traffic_render_cards.py` |
| Hegemony flotilla (two layers) and its detaching Lasher pair | 0.055–0.062 | Upper left, window (247, 148, 576 x 324); the Lashers peel off toward the lower left | `assets/heg-flotilla-*-v2*`, graded; Lashers are lane renders |
| Ox tug | 0.059 | Lower centre, crossing right | lane render |
| Near Tarsus | 0.088 | Lower right, crossing left below the night side | `assets/traffic-near-tarsus-out-v1.png` |
| Hero Atlas | 0.087 | Top left, crossing right above the flotilla | `assets/traffic-atlas-v3.png` |
| Hero Hegemony warship | 0.107 | Top right, gliding left and toward camera | `assets/heg-hero-v4.png` |
| Second Wind (entrance) | 1.35 → 0.15 | Enters from off-screen bottom left, recedes toward the station | `c1_ship.py` |

Hull identity comes from the installed game's own sprites (read-only,
`C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics`):
`ships/onslaught/onslaught_hegemony.png`, `ships/dominator/dominator_hegemony.png`,
`ships/eagle/eagle_hegemony.png`, `ships/enforcer/...`, `ships/atlas/atlas.png`,
`ships/tarsus/tarsus_base.png`, `ships/buffalo/...`, `stations/station_jangala.png`.
They are top-down and are identity references only (hull plan, livery,
proportions); paint the hulls in the perspective above, never as flat top-down
sprites.

## How a hull gets its light

- **Lane traffic (under about 60 px)** is not painted. `traffic_render_cards.py`
  builds each vanilla hull as a 3D heightfield textured with its own sprite,
  poses it in the orbital plane at the heading it travels, and renders it under
  the C1 sun with no world light. Silhouette, heading and light are exact; at
  that size painted detail would not survive anyway.
- **Near hulls** are painted with ImageGen over a guide from the same script
  (`--guide`): the blockout rendered in grey under the C1 sun is both the pose and
  the **lighting authority**, and the brief states the vacuum light outright. The
  card shares the guide's framing, so the guide's metres-per-pixel and anchors
  (port, starboard, dorsal top) place it and its lights.
- **Older painted masters** (station, flotilla) are graded toward the model by
  `c1_traffic.light_grade`: black point (no haze lift), a luminance power curve
  into black shadows, the key's fall-off across the subject, a trace of cool
  planet-shine in the shadow side, and self-lit pixels left untouched.

## Motion rules for traffic

- Every craft moves in 3D in the orbital plane and is projected, so screen speed
  falls with distance and a receding hull shrinks on its own.
- A card's heading is the heading it travels: no hull slides sideways, and a
  card is only ever translated and scaled (no turn needs a sequence in C1).
- Speeds are station-approach speeds (tens of metres per second near the
  camera, around 100 m/s in the lanes). Some craft accelerate from rest, the
  lane's lead brakes to its berth with its drive cold and bow thrusters
  blipping, a shuttle is talked in along the station's chase lights, and
  parked ships wait with drives off.
- Engines breathe and flicker; running lights are steady red and green with a
  white double strobe; the station's beacons fade rather than switch.

## Deliverables

- Generate with `python tools/codex_imagegen.py` (skill `codex-imagegen`). Each
  call is one paid generation and takes 30–90 s. Keep briefs in `prompts/`,
  masters plus `.provenance.json` in `assets/`, named `<layer>-v<N>.png`.
- Every compositing layer except the sky needs a **true transparent background**.
  Verify alpha (corners transparent, subject opaque) before handing it back.
- Cap your own generations at **four** unless one more clearly fixes a named
  defect. Report the accepted file, its dimensions, where the subject sits in it,
  and every rejected attempt with the reason.
- Do not commit, do not edit files outside your layer, and do not touch
  `compose_c1.py`. The lead integrates.

## Known scale debt (for the re-render pass)

Second Wind uses the ship pipeline's 0.65 m per hull unit (about 66 m). The traffic
model above still counts a vanilla sprite pixel as one metre, so every Hegemony hull
and freighter is 1/0.65 too long in metres. On-screen sizes are unaffected, because
size and distance scale together, but parallax and the hull-to-hull comparisons in
the text are off. Fix this on the pass that re-renders C1 with the new Second Wind:
derive the traffic's metres from the same `metres_per_unit`, scaling lengths and
distances together, and keep the station's 2.4 km as a real-world figure.
