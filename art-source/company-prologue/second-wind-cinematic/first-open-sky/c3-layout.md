# C3 FIRST OPEN SKY: shared layout for every layer

Status: ACTIVE. Production plates, cards and audio cues built; final frames render from `compose_c3.py`.

Written: 2026-10-16
Updated: 2026-10-22 — look pass: graded onto the C4/C5 look; a freight checkpoint town, its road and a relay mast on the far bank.

Shot C3 of `storyboard-v3.md`: 16.0 seconds, 1920x1080, 24 fps (384 frames). Cabin bed and
wind, and one line from Castle once the ship is level.
Every layer and moving element obeys this sheet, so the pieces composite into one
camera. The numbers live in `c3_core.py`; this sheet explains them.

## Purpose and payoff

The emotional centre of the arrival. The company lived for years inside an asteroid
mine, where every horizon was a rock wall a few metres away and every "sky" was a
ceiling. C3 is the first time they see a horizon, weather, and air with distance in it.
For the first seven seconds nobody speaks; then Castle, quietly: "Like the travel
reels they ran in the canteen... except this one doesn't loop." The shot lets them,
and the player, look.

How the frame sells "first sky":

- **The window frames the sky.** The camera looks out nearly level (9 degrees down,
  not the reveal's 19), so the whole window is in frame, head and sill and both jambs.
  The sky fills the top quarter of the glass at the start, and the frame reads as a
  frame around it rather than a hatch looking down at ground.
- **The light.** Morning daylight floods the cabin. The sun is out of frame upper left,
  in front of the glass, throwing a warm patch across the left jamb, and the right side
  of the cabin is in open shade. This is the brightest frame of the arrival so far:
  after C1–C2's black space and the entry's orange glow, the cabin itself is lit by a
  sky.
- **The scale of the sky.** The sky has depth: horizon cumulus 30–40 km away barely
  moves, the cloud row overhead (about 5 km away) slides like the far ridges, and a
  300 m Atlas lifter, 190 px long, climbs out against cumulus many kilometres across. A
  small flock crosses the low sky from behind the right jamb. Distance is something
  you can watch move.
- **Down, then level.** The shot opens with the ship still coming down out of the
  entry, nose low, the horizon tilted and the ground rising fast, the window
  shaking. Over the first 5–6 s the horizon swings level, the ground settles and the
  shake fades: the ship has flattened out, and what follows is calm.
- **The lean.** Between 6.5 and 12.5 s the passenger leans 5 cm toward the glass. The
  window frame grows 4% and opens more sky at the edges while the world does not grow
  at all (it is kilometres away). It is the only motion in the shot that belongs to a
  person rather than to the ship.

## Camera

| Item | Value | Why |
|---|---|---|
| Eye point | The reveal camera's own eye point (`02 REVEAL / near glass`, frame 144), about 1.2 m from the glass | The painted cabin was projected from this point, so a camera here may rotate freely and still see the paint exactly (`c3_cabin_render.py`). |
| Lens | 26 mm on a 36 mm sensor: f = 1386.7 px at 1920, 69.4 x 42.6 degrees | Same lens as the reveal and as C2 (`patrol-abeam/c2_core.py`). |
| Pitch | **9 degrees down** (the reveal looks 19.3 down) | Horizon at y = 320; window head at y = 77 (10 degrees above level); sill at y = 967 (26 degrees below). |
| Truck | The ship flies bow-right. At frame 0 it is still descending out of the entry: **30 m/s along, 16 m/s down**. Both ease (0.4–5.6 s) to level flight at **16 m/s**, 400 m above the canopy, sinking 2 m/s. The world slides right to left (aft) and rises, fast at first. | Same flow as the parallax-window study and C2: port window, world moves aft. Level speed came down from 20 m/s so the longer shot stays inside the painted plates. Rates below come from these numbers through the lens. |
| Attitude | **8 degrees nose down** at frame 0, easing level over the same span, the nose carrying 0.7 degrees past level around 5–7 s before it settles. | Through a port window the ship's pitch is a roll of the picture about the lens centre: the horizon tilts, rising toward the bow (right), and swings level. Every world layer turns together; the cabin does not. |
| Push | **The lean**: the eye moves 5 cm toward the glass, eased over 6.5–12.5 s, after the ship is level and under Castle's line. Cabin scale 1.2/(1.2-0.05) = 1.0435; the world scales 1.00004. | A physical dolly, about the principal point. No zoom. The cabin's depth spread (1.1–1.25 m) changes its scale by under 0.3%, so the cabin stays one card. |
| Shake | The camera shake the entry built, carried on: about 3 px of buffet and heave with a fraction of a degree of turn at frame 0, gone by about 5 s. It moves the **whole picture, cabin included** (a hand-held eye, not a fixed mount), with just enough zoom that no edge shows. A smaller jitter moves the world alone. | One function of time counted from the entry's start (`c3_core.camera_shake`), shared with the entry, so the cut cannot show. Audio eases with it. |
| Duration | 16.0 s | About 5.6 s of levelling out, then Castle's 5.1 s line from 7.0 s, and about 4 s more to look. |

Guides: `guides/c3-cabin-geometry-p09.png` (neutral greybox at this camera),
`guides/c3-cabin-depth-p09.png` (16-bit camera distance) and `.json` (the camera
actually used). `-p12` is the 12-degree alternative for comparison.

## Sun, time of day and atmosphere

- **One sun, shared with C2.** In Second Wind's frame the sun is aft, outboard to port
  and 29.5 degrees up (`c2_core.SUN = (-0.622, 0.609, 0.492)`). Through this window it
  is upper left, in front of the glass, about 49 degrees off the lens axis: out of
  frame. The approach heading and sun direction do not change between window shots.
- **Time:** mid-morning on Jangala, about 08:30–09:00 local solar time at a tropical
  latitude, from that 29.5-degree elevation. Morning is also why there is valley mist,
  and why it is burning off.
- **Consequences for every layer:** crowns are lit on their upper-left faces with
  shadows falling toward camera right and slightly toward the camera; nothing is
  front-lit. Forward scattering makes the haze and the low sky brighter toward the left
  of frame. The sun's mirror point on the river is out of frame left, so the river
  carries sky reflection, not a glint. The Atlas's flank facing us is in partial shade,
  its top and upper-left edges lit.
- **Atmosphere:** humid tropical air, visibility about 40 km. Aerial perspective by
  distance: far ridges (7 km) lose about a quarter of their contrast and shift blue;
  the horizon range (40 km) is about 70% sky colour; the Atlas (2.2 km) takes about 15%
  haze. Fair-weather cumulus bases about 1.2 km above the ground (800 m above the
  ship), so the clouds at the top of the window are only about 5 km away.

## One place with C4 and C5 (the look pass)

C4 and C5 are Blender renders of the same Jangala (`industrial-exterior/c4x_scene.py`,
`c4_jungle.py`): AgX, a high soft sun and aerial perspective toward a cool grey-blue haze. The
painted plates here were deeper, darker and harder-lit than that canopy. `c3_look.py` grades every
land layer and the Atlas at bake time, by its distance, onto that look: the luminance spread
compressed to C4's (crown gaps lifted into green rather than grey), the canopy's balance pulled
to C4's yellower green, and C4's own haze model (its haze colour, 1/e at 9 km) for distance. The
sky plate gets the same air with a lighter hand. Nothing is relit: the sun stays upper left.

## The settlement

Background touches for the missions the player fights here later, kept small and calm under
Castle's line. On the far bank, right of centre, about 2 km out: a freight checkpoint town with
a weigh-station canopy across its road (Junction's Weigh Station, Wayline's checkpoint gate),
two depot warehouses, sheds and houses (Relief's freight and warehouse service roads), fuel
tanks, stacked containers, a freight barge moored at a jetty, and trucks. Its red-laterite
freight road runs upstream along the bank and climbs out of the town into the hills (the
Thunder Run corridor). On the hill above it stands a 70 m relay mast in aviation red and white
with a white daytime strobe every 1.5 s (Relay Nine). The town and road are paint
(`c3_settlement.py`: drawn at true scale into an upscaled crop of the de-misted plate, repainted
by Codex, registered and pasted back through the drawing's mask before the bands are cut); the
mast is a moving element on the mid layer, since it stands above the hill's crest line where
the plate belongs to a farther layer. The town drifts from the right third of the window toward
the centre over the shot and is partly hidden behind the near bank's crowns, which slide across
it faster: depth, for free.

## Scale and distance

Canopy treated as flat 400 m below the eye for the row distances; the hills make the
real ground nearer on the ridges. "Slide" is the screen travel over the whole shot (298 m flown, 74 m descended), level
rows only; the turn of the picture comes on top of it in the first 5.6 s.

| Frame row (y) | What is there | Slant distance | Slide over 16 s |
|---:|---|---:|---:|
| 77 | Window head (sky, 10 degrees up) | cloud deck about 4.6 km | about 90 px |
| 320 | Geometric horizon (true horizon dips 0.64 degrees, 15 px lower) | 71 km to the edge | 0 |
| 340 | Horizon range | 30–60 km | 7–14 |
| 400 | Far ridges | 7 km | 59 |
| 500 | Ridges behind the middle forest | 3.1 km | 133 |
| 600 | Middle forest | 2.0 km | 207 |
| 800 | River, near bank | 1.2 km | 344 |
| 967 | Sill (lowest ground visible) | 0.9 km | 459 |

| Body | Size | Distance | On screen |
|---|---|---:|---|
| Atlas lifter | 300 m hull | 2.2 km | 189 px long; 3.5 degrees above level, climbing |
| Flier (flock of 11) | 3 m span | 0.7 km | 6 px span |
| Canopy crown | 30–45 m | 1.2 km (row 800) | 35–50 px |
| Cumulus | 1–3 km across | 5–40 km | 50–800 px |
| Cabin glass | — | 1.2 m | the frame; scale 1.0435 at full lean |

An element whose bearing changes while it crosses shows a sliver of its end face of
beam x sin(half the bearing change). For the Atlas (60 m beam, about 12 degrees of
bearing over the shot) that is about 4 px: a side-view card holds.

## Layers, back to front (five, per the storyboard)

A layer is a parallax rate, not a picture region: one layer may carry several
disjoint cut-outs that share its distance. Bands are cut from the approved master by
`c3_bands.py`; masks are in `guides/c3-band-*.png` (master pixels, 1672x941).

| Layer | Distance | Slide | Carries | Source | Behind nearer bands |
|---|---:|---:|---|---|---|
| Horizon | 40 km | 10 px | Clear-sky gradient, horizon cumulus, the blue range | `managed-land-realism/assets/sky-realism-v1.png`; range = band `mountains` of the master | Range behind the far crests: **1,255 master px of slivers, nothing exists** (Codex fill, `c3-inpaint-mountains.png`) |
| Far | 7 km | 59 px | Far wooded ridges; planned: the overhead cumulus row | Band `far`; master RGB where shown | 12,846 px, continued by `far-realism-v1` (salvage) |
| Mid | 1.8 km | 230 px | Middle forest, river and banks | Band `mid`; master RGB where shown | 9,925 px, continued by `mid-realism-v1` (salvage) |
| Near | 1.0 km | 413 px | Nearest canopy bank, lower right | Band `near`; master RGB | none |
| Cabin | 1.2 m | 0 (lean only) | Window frame and cabin | `c3_cabin_render.py` re-view of `cabin-realism-v1`, top band from `assets/c3-cabin-top-v1.png` | — |

Each band's distance is the median canopy distance over the rows it occupies. The
plate is the master scaled 1.2 (its own horizon row 200 lands on y = 320, which makes
its implied lens this lens) with its left edge at x = 100. `c3_bands.py` walked the
12 s shot, lean included, and found no pixel needed beyond the master's canvas; for
the 16 s shot with the turn, `compose_c3.py --coverage` composites every third frame
and counts window pixels that no opaque world layer covers: **none**. The sky plate
is drawn 1.45x (was 1.25x) so the turned window stays inside it.

### How the still was split

1. The three transparent extractions `forest-reveal-realism` already made from the
   master are registered back onto it by gradient correlation (offsets of 1–2 px,
   recorded in `guides/c3-bands.json`). Their value is that each one *continues behind*
   the nearer band, which is most of the inpainting C3 needs.
2. Front-most extraction wins: near, then mid, then far, then sky.
3. The far band is split once more. The horizon range is the contiguous blue run under
   the skyline, column by column (blue leads green; every wooded ridge in front is
   green-dominant), capped at 45 px and smoothed along x. It rides the horizon layer.
4. Mist is the bright, neutral, low-saturation canopy, excluding the river, whose
   blue leads its red (`guides/c3-band-mist.png`).
5. For every band, the pixels the shot will need are found by replaying the slide and
   the lean against the window aperture: green in `guides/c3-inpaint-*.png` means
   "needed behind a nearer band, and the salvaged extraction has it"; magenta means
   "needed, and nothing exists"; cyan would mean "beyond the canvas" (there is none).

### What Codex must fill, and how

| Fill | Area | Method (as built) |
|---|---|---|
| Range behind the far crests | 1,255 master px of slivers, never more than ~16 px deep | **Procedural**, no generation (`c3_layers.py`): below the range's foot each column mirrors the range's own lower slope about the foot and fades into its haze colour. At 40 km the range is ~70% sky colour and nearly featureless, so a mirrored slope cannot be told from paint. The range's skyline is pulled in 1.5 master px so the master's paler sky fringe does not ride on it against the sky plate. |
| De-misted far and mid bands | 30,100 master px removed (mist mask dilated, near band excluded) | Done in the same generations as the resolution pass: the mist is painted **flat grey into each edit target**, so the region to repaint is drawn. The mist cards are a difference matte, alpha = (master - de-misted) / (mist white - de-misted), measured over all far and mid land (the completions also cleared the thin wisps between ridges, which belong to the approved mist), after softening both images so added detail does not read as mist. |
| Plate resolution | every band | `c3_plates.py`: the shot only ever shows master rows 176-800, so the land is four overlapping tiles of 917x344 master px, each returned at 2048x768 (2.23x). Every tile registered at (0, 0) and no 96 px block drifted at all (rule: reject over 2 px). Each tile is colour-matched to the master at low frequency outside the mist (detail from Codex, broad light from the approved still), then feather-blended into a 3344x1882 plate (2x master; 1.67 plate px per frame px). Continuations behind nearer bands stay the 1x salvage extractions; only slivers of them ever show. |
| Cabin head | v2 | See below. |

**The lesson from the first completion** (`assets/c3-cabin-top-v1.png`):
Codex keeps finished paint registered (1 px below the seam) but redraws geometry it
is only *told* about. Asked for a window head at y = 77, it drew a deeper rim with
its glass edge at y = 169. Geometry belongs in the guide, drawn, not in the brief.
Every fill above therefore marks its region in the edit target itself.

**Cabin head v2** (`c3_cabin_head.py`) draws it: the painted sill rim, flipped about
the glass edge and widened to the aperture's top width, puts a rim exactly on y = 77;
the side jambs continue upward as their own paint mirrored about the y = 213 seam; the
two meet in a mitre at each upper corner. Codex repainted that draft's detail and light
and kept its lines: the completion's glass top measures y = 76 and its jambs fall within
1-3 px of the geometry, so it is used with no lift or stretch.

## Moving elements

All of them ride a host layer at its rate plus their own travel (storyboard-v3,
*Moving elements*). Own travel is what differs from the host: depth offset, wind,
flight. `c3_core.ELEMENTS` holds the numbers.

| Element | Host | True distance | Own travel | Method and why it is safe |
|---|---|---:|---|---|
| Atlas lifter | Mid | 2.2 km | 60 m/s bow-right, climbing 8 m/s | One side-view card (bow right). Net screen motion about +25 px/s right, 6 px/s up: it crosses slowly *against* the world. Bearing change about 12 degrees: end-face sliver about 4 px, so no perspective change shows. Realism card `assets/c3-atlas-v1.png`, repainted on `guides/c3-atlas-guide.png`: the reveal scene's Atlas is itself one plane carrying the painted card, so the guide is that card's exact silhouette in grey with the shared sun laid over it (`c3_atlas_guide.py`). Baked at 3x, hazed 15%. |
| Flock | Near | 0.7 km | 8 m/s aft | Eleven fliers from a procedural flap cycle (`compose_c3.bird_frame`): each wing is an arm and a hand segment, the hand lagging the arm so a downstroke reads as a stroke; 2.9-3.5 flaps/s per bird, each gliding with wings held up for 0.6 s in every 2.6 s, with a slight bob. Drawn at 6x and reduced, at 1.4x true span so the wingbeat reads. They pass the ship and enter from behind the right jamb at about 8.6 s, under Castle's line, crossing the bright low sky. No perspective. |
| Low mist | Mid | 1.7 km | 1 m/s breeze from the bow | Translucent card: the difference matte over the mid band. Depth offset and breeze together slide it about 20 px aft against the forest under it. |
| High mist | Far | 4.5 km | the same breeze | The matte over the far band and the range; slides about 30 px aft against the ridges it lies between. |
| Entry's last cloud | Near | 0.8 km | none | Frame 0 is the aperture filled with the entry's flat soft white, sRGB (244, 246, 248). It burns off over the first 1.6 s, thinnest parts first, its density from `managed-land-realism/assets/clouds-realism-v1.png` (softened: it is out of focus) sliding aft at the 800 m rate. |
| Relay mast | Mid | 2.0 km | none | Procedural card (`compose_c3.relay_mast`): a 70 m lattice in seven red and white bands, antenna panels and a dish, 48 px tall, given the grade's haze; its white strobe lights 0.09 s in every 1.5 s. Its foot is the cleared pad `c3_settlement.MAST_FOOT` painted into the plate. |
| Freight road trains (2) | Mid | 2.0 km | 12 and 11 m/s down-left along the road | Procedural cards (`compose_c3.truck_image`) on the painted riverside road (centreline measured on the plate, `compose_c3.ROAD`): a coloured cab and two pale box trailers in profile, about 17 px long, red dust kicked up behind, hazed like the mast and smaller farther up the road. A two-truck convoy out of the weigh station along the town's waterfront past the warehouses for the whole shot, red cab leading, blue about 90 m behind, both moving down-left against the scene's rightward drift (owner, 2026-10-22). The near bank's crowns cross in front of them late in the shot, as they do the town. |
| Gloved hand (optional) | Cabin | 0.6 m | — | A 3–4 frame sheet on the sill, loosening at about 9.5 s, after the lean. Owner call (open question 3). |

Nothing near the camera moves fast, nothing turns except the whole world with the ship's
attitude in the first 5.6 s, and nothing changes scale except
the cabin under the lean.

## Picking up from the entry transition

**The hand-off** (shared session notes, `c3-handoff.md`): the entry ends with its window
full of flat soft white, sRGB (244, 246, 248), on C3's camera with the cabin unchanged;
C3's frame 0 is exactly that. If the entry instead ends white over the whole frame, C3
adds a short white-to-cabin fade.

- **One window across the cut** (owner, 2026-10-19). The entry
  (`patrol-abeam/compose_entry.py`) is drawn through this shot's own cabin layer and
  camera from its first frame, relit for space and fire, and settles onto frame 0 in
  the white. Only what is outside the glass and the shake change at the cut.
- **The join sits inside the cloud.** The entry ends in thinning cloud; C3's first
  1.6 s is that cloud burning off and sliding aft. Whiteout is the most forgiving
  place for any mismatch between the two renders.
- The shake carries over from the entry: one function of time from the entry's start
  moves the whole picture in both shots, and C3 frame 0 is one frame on from the entry's
  last. It eases away over C3's first 5 s, with the horizon swinging level; the audio
  rumble eases with it.
- The flow continues right to left, as in C2 and as C4's pan must. The sun stays upper
  left, in front of the glass.
- The cut from C2 to the entry can change pitch (from C2's 18.6 to C3's 9): it is a cut
  between space and atmospheric glow, with time passing in between. The cut from C3 to
  C4 is free in the same way.

## Salvage

| Asset | C3 use |
|---|---|
| `realism-lookdev/landscape-realism-v1.png` | The approved still: all band RGB where the master shows the band. |
| `forest-reveal-realism/assets/{far,mid,near}-realism-v1.png` | Band continuations behind nearer bands (registered 1–2 px). |
| `managed-land-realism/assets/sky-realism-v1.png` | Horizon layer sky (until a C3 sky with a separate cumulus row exists). |
| `managed-land-realism/assets/clouds-realism-v1.png` | The entry's last cloud at the pickup. |
| `forest-reveal-realism/forest-reveal-realism.blend` + `assets/cabin-realism-v1.png` | The painted cabin, re-viewed at C3's pitch through the existing projector. Opened read-only. |
| `parallax-window/jangala-parallax-atlas-lift.blend` | Cabin geometry and camera for the guides. Opened read-only. |
| `parallax-window/traffic/atlas-painted-v1.png` | Atlas identity and blockout card (earlier painted style). |
| `entry-window/` | Its cloud cards and timing, re-rendered on C3's camera for the joint. |
| `realism-lookdev/window-realism-v1.png` | Look target for the cabin palette (grey) and the Atlas in daylight. |

Not used: `forest-lookdev/` (the modelled-foliage experiment is superseded by the
realism plates) and the reveal's 3D scenery.

## Audio

`audio_cues()` in `compose_c3.py`: `jangala_arrival_cabin_bed` (a 16 s seamless loop,
volume 0.55, the whole shot) and `jangala_arrival_wind` (one 12.5 s pass, volume 0.8,
its buffeting easing with the shake). Castle's travel-reels line (take `c4_reels_t06`,
5.12 s) starts at **7.0 s** (`c3_core.CASTLE_REELS_AT`, `compose_c3.voice_timing()`), once
the ship is level and the shake has gone; the arrival mix places it until the line
joins the shipped catalog, when `captions()` and `audio_cues()` carry it. Both are built by `c3_audio.py` from
repository ambience and seeded noise and registered in `audio.json`; any window shot
may reuse them. `overlays()` is empty.

## Files

| File | Role |
|---|---|
| `c3_core.py` | Camera, flight, lean, plate placement, layers and elements. |
| `c3_cabin_guide.py` | Blender: greybox cabin, depth and camera at the C3 pitch. |
| `c3_cabin_render.py` | Blender: the painted cabin re-viewed at the C3 pitch, plus the mask of what the projector never saw. |
| `c3_bands.py` | Band masks, registration, mist, and the fill maps. |
| `c3_plates.py` | Tile edit targets, registration check, and the 2x de-misted plate and mist matte. |
| `c3_cabin_head.py` | Cabin head v2 edit target with the rim drawn; `--check` measures a completion. |
| `c3_atlas_guide.py` | The Atlas guide from the reveal scene's Atlas card. |
| `c3_look.py` | The grade onto the C4/C5 look, by distance. |
| `c3_settlement.py` | The settlement's edit target and mask, its registration check, and the paste into the plate. |
| `c3_layers.py` | Bakes every production layer to `build/layers/`, graded, the settlement in. |
| `c3_audio.py` | The cabin bed and wind sources. |
| `compose_c3.py` | Final frames, review mp4 and `timeline.json` under `build/cinematic/c3-first-open-sky/`; review stills and contact sheet in `review/`; the runtime contract's data. |
| `prompts/`, `assets/` | Codex briefs and masters with provenance. |

## Owner decisions (2026-10-16)

- **The 9-degree tilt is accepted.** Rework the entry transition as needed to hide
  the tilt change from C2's 18.6 degrees.
- **The one window is the orange-and-cream cabin**: this shot's own model-matched
  cabin, which settles question 2. Keep its geometry and palette; repaint the head
  strip with the window rim drawn into the edit image (question 3).

## Open questions

Settled and removed: the shared pitch and the one cabin (owner decisions above), cabin head v2, plate resolution and the realism Atlas (built in the production pass).

1. **The sky has depth, and the storyboard has five layers.** Overhead cumulus is about
   5 km away and the horizon cumulus about 35 km, so one sky plate is wrong by about
   60 px over the shot at the top of the window. Plan: a C3 sky generation as a clear
   gradient plus a transparent cumulus row, the row riding the far layer. This needs
   the reading above, that a layer is a rate and may carry disjoint cut-outs. Confirm.
2. **Depth inside a band.** A card cannot shear: the near band really spans 0.9–1.5 km
   (366–230 px of slide) and the mid band 1.2–5 km (280–85 px). At 20 m/s the error is
   tolerable; faster would show the bands as cards. Alternative if it shows: fold the
   range into the far layer (about 40 px of error) and spend the freed layer splitting
   the mid band at the river.
3. **The gloved hand.** At 9 degrees the lower-left sleeve of the reveal framing is out
   of frame. Put a hand on the sill (a short sheet, loosening after the lean), or drop
   it?
4. **Storyboard wording.** C3's layer list could read: "horizon (sky and range); far
   ridges; middle forest and river; near canopy; fixed window frame and cabin", with
   the lifter, flock and mist as moving elements riding those layers.
5. **The flock's species.** Jangala fauna or generic birds? At 6 px it is a silhouette
    either way; a painted sheet matters only if a later shot comes closer.
