# C3 FIRST OPEN SKY: shared layout for every layer

Status: DRAFT. Groundwork and camera plan; blockout animatic built, plates not yet painted.

Written: 2026-10-16

Shot C3 of `storyboard-v3.md`: 12.0 seconds, 1920x1080, 24 fps (288 frames), silent.
Every layer and moving element obeys this sheet, so the pieces composite into one
camera. The numbers live in `c3_core.py`; this sheet explains them.

## Purpose and payoff

The emotional centre of the arrival. The company lived for years inside an asteroid
mine, where every horizon was a rock wall a few metres away and every "sky" was a
ceiling. C3 is the first time they see a horizon, weather, and air with distance in it.
Nobody speaks. The shot lets them, and the player, look.

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
- **The lean.** Between 3.5 and 9.5 s the passenger leans 5 cm toward the glass. The
  window frame grows 4% and opens more sky at the edges while the world does not grow
  at all (it is kilometres away). It is the only motion in the shot that belongs to a
  person rather than to the ship.

## Camera

| Item | Value | Why |
|---|---|---|
| Eye point | The reveal camera's own eye point (`02 REVEAL / near glass`, frame 144), about 1.2 m from the glass | The painted cabin was projected from this point, so a camera here may rotate freely and still see the paint exactly (`c3_cabin_render.py`). |
| Lens | 26 mm on a 36 mm sensor: f = 1386.7 px at 1920, 69.4 x 42.6 degrees | Same lens as the reveal and as C2 (`patrol-abeam/c2_core.py`). |
| Pitch | **9 degrees down** (the reveal looks 19.3 down) | Horizon at y = 320; window head at y = 77 (10 degrees above level); sill at y = 967 (26 degrees below). |
| Truck | The ship flies bow-right at **20 m/s**, 400 m above the canopy, sinking 2 m/s. The world slides right to left (aft) and rises slightly. | Same flow as the parallax-window study and C2: port window, world moves aft. Rates below come from these numbers through the lens. |
| Push | **The lean**: the eye moves 5 cm toward the glass, eased over 3.5–9.5 s. Cabin scale 1.2/(1.2-0.05) = 1.0435; the world scales 1.00004. | A physical dolly, about the principal point. No zoom. The cabin's depth spread (1.1–1.25 m) changes its scale by under 0.3%, so the cabin stays one card. |
| Vibration | 2.4 px of world shake, decaying with a 0.9 s time constant | The airframe's last buffeting from the entry, felt as the world moving; the eye is fixed to the ship. Audio eases with it. |
| Duration | 12.0 s | Storyboard target. |

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

## Scale and distance

Canopy treated as flat 400 m below the eye for the row distances; the hills make the
real ground nearer on the ridges. "Slide" is the screen travel over the whole shot.

| Frame row (y) | What is there | Slant distance | Slide over 12 s |
|---:|---|---:|---:|
| 77 | Window head (sky, 10 degrees up) | cloud deck about 4.6 km | about 70 px |
| 320 | Geometric horizon (true horizon dips 0.64 degrees, 15 px lower) | 71 km to the edge | 0 |
| 340 | Horizon range | 30–60 km | 8–13 |
| 400 | Far ridges | 7 km | 48 |
| 500 | Ridges behind the middle forest | 3.1 km | 107 |
| 600 | Middle forest | 2.0 km | 166 |
| 800 | River, near bank | 1.2 km | 280 |
| 967 | Sill (lowest ground visible) | 0.9 km | 366 |

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
| Horizon | 40 km | 8 px | Clear-sky gradient, horizon cumulus, the blue range | `managed-land-realism/assets/sky-realism-v1.png`; range = band `mountains` of the master | Range behind the far crests: **1,255 master px of slivers, nothing exists** (Codex fill, `c3-inpaint-mountains.png`) |
| Far | 7 km | 48 px | Far wooded ridges; planned: the overhead cumulus row | Band `far`; master RGB where shown | 12,846 px, continued by `far-realism-v1` (salvage) |
| Mid | 1.8 km | 185 px | Middle forest, river and banks | Band `mid`; master RGB where shown | 9,925 px, continued by `mid-realism-v1` (salvage) |
| Near | 1.0 km | 333 px | Nearest canopy bank, lower right | Band `near`; master RGB | none |
| Cabin | 1.2 m | 0 (lean only) | Window frame and cabin | `c3_cabin_render.py` re-view of `cabin-realism-v1`, top band from `assets/c3-cabin-top-v1.png` | — |

Each band's distance is the median canopy distance over the rows it occupies. The
plate is the master scaled 1.2 (its own horizon row 200 lands on y = 320, which makes
its implied lens this lens) with its left edge at x = 100. `c3_bands.py` walks the
whole shot, lean included, and finds **no pixel needed beyond the master's canvas**.

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

| Fill | Area | Method |
|---|---|---|
| Range behind the far crests | 1,255 master px of slivers | Edit target: the range band over neutral grey, the slivers painted flat mid-grey, master as style reference. Brief: continue the range's lower slopes into the grey only. Composite back only the grey region, after checking registration with `c3_bands.register`. |
| De-misted far and mid bands | the 43,800 px mist mask | One edit per band: remove the valley mist and paint the canopy beneath. Then the mist cards are exact: alpha = (master − de-misted) / (mist white − de-misted), a difference matte, so the drifting mist is the approved mist and not an invention. |
| Plate resolution | every band | The master is 1672 px wide; the plate rule is 3840. Re-render each band as two overlapping wide strips (image_gen returns about 2048x768) with the upscaled band as the edit target, then register each strip back with `c3_bands.register` before blending. Reject any strip that moves a crown or a bank by more than 2 px. |
| Cabin head | done (v1) | See below. |

**The lesson from the one completion already run** (`assets/c3-cabin-top-v1.png`):
Codex keeps finished paint registered (1 px below the seam) but redraws geometry it
is only *told* about. Asked for a window head at y = 77, it drew a deeper rim with
its glass edge at y = 169. Geometry belongs in the guide, drawn, not in the brief.
Every fill above therefore marks its region in the edit target itself.

## Moving elements

All of them ride a host layer at its rate plus their own travel (storyboard-v3,
*Moving elements*). Own travel is what differs from the host: depth offset, wind,
flight. `c3_core.ELEMENTS` holds the numbers.

| Element | Host | True distance | Own travel | Method and why it is safe |
|---|---|---:|---|---|
| Atlas lifter | Mid | 2.2 km | 60 m/s bow-right, climbing 8 m/s | One side-view card (bow right). Net screen motion about +25 px/s right, 6 px/s up: it crosses slowly *against* the world. Bearing change about 12 degrees: end-face sliver about 4 px, so no perspective change shows. Blockout uses the earlier painted card `parallax-window/traffic/atlas-painted-v1.png`; the final card is a realism repaint (open question 7). |
| Flock | Near | 0.7 km | 8 m/s aft | Eleven fliers from a flap sheet (blockout: a procedural 6 px chevron cycle at 3.2 flaps/s, phases staggered). They pass the ship and enter from behind the right jamb at about 2.5 s, crossing the bright low sky, where a dark speck reads. Flapping is the only internal motion; no perspective. |
| Low mist | Mid | 1.7 km | 1 m/s breeze from the bow | Translucent card: the mid band's own mist, difference-matted once the de-misted band exists. Depth offset and breeze together slide it about 20 px aft against the forest under it. |
| High mist | Far | 4.5 km | the same breeze | The same from the far band; slides about 30 px aft against the ridges it lies between. |
| Entry's last cloud | Near | 0.8 km | none | `managed-land-realism/assets/clouds-realism-v1.png`, already used by the entry tail. Slides aft and burns off over the first 1.6 s. |
| Gloved hand (optional) | Cabin | 0.6 m | — | A 3–4 frame sheet on the sill, loosening at about 9.5 s, after the lean. Owner call (open question 8). |

Nothing near the camera moves fast, nothing rotates, and nothing changes scale except
the cabin under the lean.

## Picking up from the entry transition

- The entry tail (`entry-window/`) ends on the forest reveal's first frame: the reveal
  camera at 19.3 degrees down, the orange cabin, and the forest. C3's camera differs
  only in pitch, from the same eye point, so the entry tail can be re-rendered on C3's
  camera with no new geometry: its cloud cards are already 2D layers in front of the
  forest, and the cabin re-view is exactly `c3_cabin_render.py`.
- **The join sits inside the cloud.** The entry ends in thinning cloud; C3's first
  1.6 s is that cloud burning off and sliding aft. Whiteout is the most forgiving
  place for any mismatch between the two renders.
- The world shake carries over from the entry and decays in C3's first 3 s; the audio
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

## Files

| File | Role |
|---|---|
| `c3_core.py` | Camera, flight, lean, plate placement, layers and elements. |
| `c3_cabin_guide.py` | Blender: greybox cabin, depth and camera at the C3 pitch. |
| `c3_cabin_render.py` | Blender: the painted cabin re-viewed at the C3 pitch, plus the mask of what the projector never saw. |
| `c3_bands.py` | Band masks, registration, mist, and the fill maps. |
| `compose_c3.py` | Blockout stills in `review/`; `--movie` writes `build/c3-blockout.mp4`. |
| `prompts/`, `assets/` | Codex briefs and masters with provenance. |

## Owner decisions (2026-10-16)

- **The 9-degree tilt is accepted.** Rework the entry transition as needed to hide
  the tilt change from C2's 18.6 degrees.
- **The one window is the orange-and-cream cabin**: this shot's own model-matched
  cabin, which settles question 2. Keep its geometry and palette; repaint the head
  strip with the window rim drawn into the edit image (question 3).

## Open questions

1. **Pitch, shared with the entry.** C3 proposes 9 degrees down; the entry tail
   (re-planned in `patrol-abeam/`) would have to end on the same camera. C2 sits at
   18.6. Does the entry adopt C3's camera, with the pitch change hidden in the C2→entry
   cut?
2. **Which cabin.** `storyboard-v3.md` names the grey `realism-lookdev` frame for C2, C3,
   C4 and the entry, and `patrol-abeam/c2_core.py` uses its aperture. But that keyframe
   is not registered to the Blender cabin (its left jamb sits about 75 px from the
   geometry's), so it cannot be re-viewed at another pitch. The orange
   `cabin-realism-v1` can, and is what the entry and the reveal already use. Proposal:
   keep the Blender-registered geometry and give it the grey palette in one reskin
   generation from `c3-cabin-geometry-p09.png`, used by every window shot. Storyboard
   change proposed: "one frame throughout" means one geometry and one palette, not one
   keyframe image.
3. **Cabin head v2.** `c3-cabin-top-v1.png` drew the window head 92 px too deep; the
   compositor lifts it into place, which stretches the jamb paint between y = 77 and
   213 about 3x vertically. A v2 with the rim drawn into the edit target fixes it, and
   can be the same generation as question 2.
4. **The sky has depth, and the storyboard has five layers.** Overhead cumulus is about
   5 km away and the horizon cumulus about 35 km, so one sky plate is wrong by about
   60 px over the shot at the top of the window. Plan: a C3 sky generation as a clear
   gradient plus a transparent cumulus row, the row riding the far layer. This needs
   the reading above, that a layer is a rate and may carry disjoint cut-outs. Confirm.
5. **Depth inside a band.** A card cannot shear: the near band really spans 0.9–1.5 km
   (366–230 px of slide) and the mid band 1.2–5 km (280–85 px). At 20 m/s the error is
   tolerable; faster would show the bands as cards. Alternative if it shows: fold the
   range into the far layer (about 40 px of error) and spend the freed layer splitting
   the mid band at the river.
6. **Plate resolution.** Approve the band-strip regeneration path, or accept the
   1672-px master (soft at 1080p, well short of the 3840 plate rule).
7. **Atlas card.** The blockout card is the earlier painted style. A realism card
   wants a guide rendered from the reveal blend's Atlas at this camera, lit by the
   shared sun (top and upper-left lit, near flank in partial shade), then reskinned.
   One generation.
8. **The gloved hand.** At 9 degrees the lower-left sleeve of the reveal framing is out
   of frame. Put a hand on the sill (a short sheet, loosening after the lean), or drop
   it?
9. **Storyboard wording.** C3's layer list could read: "horizon (sky and range); far
   ridges; middle forest and river; near canopy; fixed window frame and cabin", with
   the lifter, flock and mist as moving elements riding those layers.
10. **The flock's species.** Jangala fauna or generic birds? At 6 px it is a silhouette
    either way; a painted sheet matters only if a later shot comes closer.
