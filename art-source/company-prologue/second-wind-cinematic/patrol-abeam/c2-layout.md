# C2 PATROL ABEAM and the entry transition: shared layout

Shot C2 of `storyboard-v3.md` and the entry transition after it: 15.0 s plus 3.0 s,
1920x1080, 24 fps. Every layer obeys this sheet so the pieces composite into one
camera. `c2_core.py` holds the same numbers as code: camera, sun, planet, the patrol's
track and the hail timings. The guides, the blockout and any later compositor read
them from there.

Status: PRODUCTION. C2 and the entry render to final frames (see *Production build*).
The blockout sections below remain the plan of record where *Production build* does
not override them.

## Purpose and payoff

The camera moves inside the hull. C1 showed the Hegemony from far away as a fleet
holding station. C2 puts one of those warships about a hundred metres off the glass,
so its armour fills the window while Approach reads out the conditions of entry.
The patrol has come to look at them. It passes, its turrets stay trained fore and aft,
and Approach says "Patrol has your signature" while its broadside is centred in the
window.

**Payoff: scale and scrutiny, felt rather than described.** The passengers cannot see
the whole ship, only a wall of orange armour going past. Then Castle reads the
clearance back, Second Wind starts down, and the warship rises out of the top of the
glass. The window is left with Jangala below and the first heat of entry. Castle's
off-channel "Well. Somebody knows we're here." plays over the glow and the cloud. It
is the first time in years that anyone official has noticed these people, and the
window shows it before she says it.

**Continuity.** The patrol is C1's hero warship, the Dominator `heg-hero-v2`, now
alongside. The sun is C1's sun, carried into Second Wind's frame. The window frame is
the one used for C2, the entry, C3 and C4.

## Camera

| | |
|---|---|
| Position | Passenger cabin, about 1.1 m behind the glass, looking out of a **port** window. Second Wind's bow is **screen right**, so the world flows aft from right to left. That is the established window flow: `parallax-window` measured its scenery moving right to left. |
| Lens | 26 mm on a 36 mm sensor (the forest-reveal and parallax-window cabin camera). Focal length 1386.7 px at 1920 wide; horizontal field of view 69.4°. |
| Pitch | 18.6° down. This is measured from the realism frame itself: its jambs converge to a vertical vanishing point about 4670 px below the top of the frame. The Blender cabin camera reads 19.3°. |
| Glass | Measured on `realism-lookdev/window-realism-v1.png`: left jamb at x = 157 + 0.172y, right jamb at x = 1764 − 0.172y, sill at y = 704. The opening runs out through the top of the frame. Through it the camera sees from 2.7° above the horizontal to 25.4° below it, and from 30° left to 30° right. |
| Motion | **None.** No push, no truck, no shake; the frame does not move at all (storyboard). Everything that moves is outside the glass, or is a change of light on the frame. |
| Duration | C2 runs 37.70–52.70 (15.0 s) on the hail clock. The entry runs 52.70–55.70 (3.0 s). |

## Sun and light

- **One sun: Corvus**, yellow-white and hard. `c1_core.SUN_IN_CAMERA` carried into
  Second Wind's frame puts it aft, to port and above. Through this port window it is
  upper left and outside the glass: 29.5° up, 46° aft of the window normal and 65° off
  the lens axis. It is never in frame. World vector `c2_core.SUN = (-0.622, 0.609, 0.492)`.
- **What the sun lights.** The patrol's deck, turret domes and upward-facing bevels are
  in hard light. Its stern faces aft, toward the sun, so its engine block is lit when
  it pulls ahead. Its bow faces away from the sun and is dark as it enters. Its near
  (starboard) flank faces the camera, away from the sun, and is **in shadow**.
- **No ambient.** The only other source is Jangala below: cool blue planet-shine, about
  a twentieth of the sun on a surface that faces straight down. It just separates the
  shadowed flank's largest armour belts. Shadows are black and hard-edged. The flank
  reads as a silhouette against the bright planet below the horizon and against the
  lit deck above it (blockout still 2).
- **Exposure is set for sunlit metal and a daylit planet, so no stars show.** This is
  what a real camera would record. It differs from C1, which shows stars (see the open
  questions).
- **The cabin.** Sunlight enters the glass travelling forward and down. It lays a hard
  white patch on the forward (right) inner jamb and the sill. Everything else in the
  cabin is black or nearly so, apart from a faint blue planet-shine on the underside
  of the header and the lip of the sill. The current realism frame is lit like a
  daylight forest, warm and from the left, and must be relit for space (see the
  salvage list). The blockout only grades it.
- **Entry.** The heat glow outside becomes the cabin's light: orange, from the glass,
  strongest on surfaces near the opening. Then the cloud gives the cabin the sequence's
  first soft, diffuse fill. Both are light from real sources outside the glass. Neither
  is ambient.

## Scale and distance

Real lengths come from the C1 convention: one sprite pixel is one metre, so the
Dominator sprite's 220 x 180 px is 220 m of beam and 180 m of length. Screen sizes come
from f = 1386.7 px and the patrol's track (`patrol_guide.py` measures the actual
silhouettes into `guides/patrol-guides.json`).

| Subject | Real size | Distance | Screen size at 1920x1080 |
|---|---|---|---|
| Patrol: Hegemony Dominator-class cruiser (C1's hero) | 180 m long, 220 m beam, about 43 m from keel to crown | Near flank 105 m from the glass, far side 325 m; centreline 215 m out, about 3–46 m below eye level | Abeam (C2 8.0 s): 1636 x 418 px, x 219–1855 and y 102–521. It overflows both jambs and fills 59% of the glass height. |
| Patrol detail: hatch / small turret dome / engine bell | 2 m / 8 m / about 26 m across | 105 m / about 150 m / about 200 m | 26 px / 74 px / about 180 px |
| Patrol motion | Overtakes at 28 m/s relative, bow first, moving left to right | — | About 370 px/s on the near flank: 15 px per frame, so render with motion blur |
| Patrol rise (de-orbit) | Second Wind drops away at 0.8 g from C2 9.6 s | — | Rises out of the top of the glass by about 13.5 s |
| Jangala | Assumed radius 6000 km (Earth-like); vanilla gives no kilometres | Altitude 200 km, above the ~120 km entry interface. Horizon dip 14.6°, horizon 1,560 km away | Horizon at y ≈ 443 at frame centre, gently curved. Planet fills the glass below it. |
| Jangala's drift (orbit at 7.3 km/s) | — | 1,370 km at the horizon, 510 km at the sill | Aft (right to left): about 7 px/s at the horizon, 14 px/s mid-band, 19 px/s at the sill. The limb itself does not move. |
| Window glass | About 1.4 m wide | About 1.1 m from the eye | x 157–1764 at the top edge, 278–1643 at the sill |

## Layers, back to front

The frame and the glass never move. The camera does not move either, so a layer's
"depth" here is its real distance and its own screen motion, not a parallax rate.

| # | Layer | Real distance | Screen motion | Method | Owner |
|---|---|---|---|---|---|
| 1 | Space | ∞ | none | Plain black; no stars at this exposure | lead |
| 2 | Jangala, far: limb, atmosphere and horizon band | 1,000–1,560 km | about 7 px/s aft | Painted plate, 3840x2160 plus 300 px of overscan. It **translates only**; the limb is drawn so that a 105 px slide over the shot does not show. | planet agent |
| 3 | Jangala, near: surface and cloud deck below y ≈ 560 | 510–1,000 km | about 14 px/s aft | Painted plate with a soft top edge under layer 2. It translates only (210 px over the shot). | planet agent |
| 4 | Patrol hull, with engine-glow and running-light passes | 105–325 m | Overtakes left to right at 370 px/s near-flank speed, then rises out of the top | **Rendered sequence** (see *Moving elements*), 24 fps, alpha, with a separate additive glow pass | patrol agent / lead |
| 5 | Window frame, cabin and glass | 0.7–1.5 m | none; changes light state only | Painted plates on one registered geometry: space, glow and cloud states, cross-faded. Alpha from `guides/window-mask-3840.png`. | frame agent |

During the entry, layers 2–4 give way to the transition cards. These sit between the
planet and the frame, so there are never more than four layers live: the planet
(leaving), glow, cloud and frame.

## Moving elements

| Element | What it does | Method | Why |
|---|---|---|---|
| **Patrol hull** | Overtakes bow first, from the left jamb to the right. It is abeam at C2 8.0 s and rises out of the top of the glass from 9.6 s. | **Rendered sequence.** A Blender proxy on the track in `c2_core` carries painted skin projected from three ImageGen key poses: entry (3.0 s), abeam (8.0 s) and exit (11.4 s), each painted from its own guide in `guides/`. Weights blend by facing. It renders at 24 fps with a 180° shutter. | A hull that fills a 60° window cannot cross it as a card. At the left jamb we see its bow face, abeam its broadside, at the right its stern. Its far side, 325 m out, sweeps the view three times more slowly than its near flank, so the hull visibly turns about 60° in perspective across the pass. This is the owner's "camera heavily changing perspective" case, with the element moving instead of the camera. The painted light stays valid when projected because sunlight on matte armour is view-independent. Only the small specular glints are painted for one view. |
| Engine glow | Blue-white glow at the stern nozzles, brightening as the patrol pulls away | Emitter discs on the proxy's eight engine slots (read from `dominator.ship`), rendered as an additive pass. Intensity follows an 8-frame flicker sheet. | It rides the hull's own perspective, so it belongs to the same render. |
| Running lights | Red and white pinpoints on the flank and deck, on an 18% blink duty like C1's | Points on the proxy, in the same render | They follow the perspective |
| *Scrutiny beat (proposed)* | One dorsal turret is trained on Second Wind as the bow enters. It slews back to fore and aft on "Patrol has your signature". | Rotation of the turret in the same render. Its geometry is already in the proxy. | It costs nothing in a rendered sequence and makes "mounts cold" visible. See the open questions. |
| Jangala | The surface drifts aft. The limb holds. | Two translating cards (layers 2 and 3) | Distance forgives. Over 15 s the ground's change of angle is under a degree. |
| Cabin | Nothing moves. The light changes for the entry. | Cross-fades between relit frame plates, timed by `c2_core` | A light change is not a geometric change |
| Entry glow | Hot filaments stream aft (right to left) inside a glow that wipes in from the bow side | A glow card that **translates** right to left with a soft leading edge (the wipe is the card's own edge), plus a filament card scrolling at about 600 px/s | Translate only. The wipe is not an animated mask. |
| Entry cloud | Cloud overruns the glow from right to left and whites out the glass | A cloud card translating right to left, its soft leading edge 260 px wide | Same rule |

## Audio sync points

Hail clock in seconds (`build_hail_preview.timeline()`, catalog
`mod/data/marines/jangala_arrival_hail.json`, treated clips measured 2026-10-16).
`compose_blockout.py` re-derives these and refuses to run if they have moved.

| Hail clock | C2 / entry time | Line | Picture |
|---|---|---|---|
| 37.70 | C2 0.00 | — | Cut from C1. The far bow lobes are already at the left jamb, dark. |
| 38.00 | C2 0.30 | Approach keys up (squelch) | |
| 38.16–42.14 | C2 0.46–4.44 | 7: "Second Wind, you're cleared to Greywater Down on civilian corridor four." | The near bow crosses the left jamb at about 2 s (still 1 at 3.0 s). |
| 42.42–44.74 | C2 4.72–7.04 | 7: "Keep your mounts cold, and stay in the corridor." | Turret barbettes slide across the glass, trained fore and aft. |
| **45.20–46.54** | **C2 7.50–8.84** | **7: "Patrol has your signature."** | **Hull centred abeam at 8.0** (still 2): maximum fill, lit deck over a black flank against Jangala. |
| 46.64–46.86 | C2 8.94–9.16 | Approach keys down | |
| 47.46–51.46 | C2 9.76–13.76 | 8: "Corridor four, mounts cold. Thank you, Approach." | The de-orbit burn starts at 9.6, under the read-back; cabin rumble rises in the mix. The stern and lit engine block pull ahead and rise (still 3 at 11.4). The hull clears the glass at about 13.5. |
| 51.46–52.70 | C2 13.76–15.00 | — | Clear glass: Jangala and black. A breath before the heat. |
| 52.70 | Entry 0.00 | — | The glow wipe begins at the right jamb. |
| 53.06–53.47 | Entry 0.36–0.77 | 9 (off channel, no squelch): "Well." | Glow covers the glass by 1.1; the frame takes the orange (still 4 at 0.9). |
| 54.00–55.27 | Entry 1.30–2.57 | 9: "Somebody knows we're here." | The cloud wipe starts at 1.3 and covers the glass by 2.5 (still 5 at 2.2). |
| 55.58 | Entry 2.88 | Line 9 ends | |
| 55.70 | Entry 3.00 | — | Cut to C3, which opens out of the white. |

## Entry transition

**What the storyboard asks:** an orange heat glow, then cloud, wiped across the same
window frame, joining C2 to C3. It lasts 2–3 s and is not a shot in its own right.

- **Same frame, same camera.** Only the light on the frame and the cards behind the
  glass change.
- **Direction.** Both wipes run right to left, from bow to stern. The plasma sheath
  builds from the leading edge, and the cloud streams aft. That is the same flow as
  every window shot, so it never reverses at the cut.
- **Glow (0.0–1.1 s in, held to 1.5 s).** It is hottest toward the bow side and low in
  the glass, where the sheath wraps the hull's leading underside. It is dimmer and more
  filamentary aft, with filaments scrolling aft. The frame takes an orange relight that
  is strongest on the surfaces nearest the opening (`guides/transition-timing.png`).
- **Cloud (1.3–2.5 s, white held to 3.0 s).** It overruns the glow and whites out the
  glass. The frame trades the orange for the sequence's first soft fill. C3 opens out of
  the white, so the join is a match on white rather than a cut between two lit pictures.
- **Line 9 sits inside it.** "Well." plays over the glow and "Somebody knows we're
  here." over the cloud. Its last syllable ends 0.12 s before the cut. There is no
  squelch: the missing key-up is how the ear knows she has closed the channel.
- **The time skip is honest about being a wipe.** Going from 200 km to the cloud deck
  takes many minutes. The glow-then-cloud wipe is an ellipsis, which is why it is a
  transition and not a shot.
- **Sources.** Restyle the glow and cloud from `entry-window/`'s exterior cards (below).
  The blockout cuts them out of its movie as stand-ins.

## Salvage

| Existing asset | Use in C2 and the entry |
|---|---|
| `realism-lookdev/window-realism-v1.png` | **The frame: geometry and material authority.** The aperture is measured here and baked into `guides/window-mask*.png`. Reuse its geometry, materials and wear as they are. It needs (a) a 3840x2160 repaint, since the native master is 1672x941, and (b) relighting into three states: space (hard sun patch on the forward jamb and sill, black elsewhere, blue planet-shine under the header), glow (orange from the glass) and cloud (soft diffuse). These follow the geometry-first workflow: the frame itself plus `window-mask-3840.png` as the edit target, "relight only, preserve every edge". |
| `entry-window/` | Its four exterior cards (heating filaments 800 m, warm boundary glow 900 m, cloud filaments 1600 m, luminous cloud body 2300 m) and their cloud art (`managed-land-realism/assets/clouds-realism-v1.png`) become the glow and cloud cards, re-rendered flat behind the realism frame. **Its orange-and-cream Blender cabin is a different frame and is not reused.** Its glow is a pale peach haze. The storyboard's "orange heat glow" needs it hotter and weighted to the bow side, as in the blockout grade. |
| `corvus-approach/assets/heg-hero-v2.png` (read only) | Material, livery and detail reference for the patrol: the same ship |
| `corvus-approach/assets/planet-albedo-detail-v1.png`, `planet-clouds-detail-v1.png` (read only) | Jangala's surface and clouds, for continuity with C1's globe. The blockout drapes them over the 200 km view. At about 18 texels per degree they are far too coarse for a near-orbit plate, so they serve as the layout guide for a new regional plate. |
| `c1_core.SUN_IN_CAMERA` | Source of `c2_core.SUN` |
| `forest-reveal-realism/forest-reveal-realism.blend` | Confirms the camera model: 26 mm, 19.3° down, parented to `SECOND WIND \| flight root`. That parenting gives the cabin's position in ship coordinates if C2 shows a glimpse of Second Wind's own hull (see the open questions). |
| `second-wind-states/` (was the full-surface asset) | Not in C2 as planned. It becomes the source of that glimpse if the owner wants one. |
| Vanilla `dominator_hegemony.png` and `dominator.ship` (install, read only) | Hull plan for the proxy, weapon slots for the turret domes, engine slots for the glow. Read at build time and never copied. |

## Guides and blockout in this package

| File | What it is |
|---|---|
| `c2_core.py` | The shared model: camera, glass, sun, planet, track, hail times, transition phases |
| `make_guides.py` | Writes the window mask and its check image, the planet blockout, the timeline and the transition chart |
| `patrol_guide.py` | Blender (background, factory startup). Builds the Dominator heightfield proxy with turret domes and engine slots on the track. Renders 3840x2160 pose guides (form) and light guides (true sun and planet-shine only) at entry, abeam and exit, plus 1920x1080 true-light frames for the stills, and writes `guides/patrol-guides.json`. |
| `compose_blockout.py` | The five 1920x1080 review stills plus the look-test still |
| `guides/window-mask.png`, `window-mask-3840.png`, `window-mask-check.png` | Frame opaque, glass transparent; the check shows registration on the realism frame |
| `guides/patrol-pose-*.png`, `patrol-light-*.png`, `patrol-blockout-t*.png` | Silhouette and light guides at the key poses, and the blockout frames |
| `guides/planet-blockout.png` | Jangala from 200 km through the C2 camera |
| `guides/c2-timeline.png`, `guides/transition-timing.png` | One clock for the hail, the pass and the wipes; the transition's curves and glass states |
| `review/1-bow-enters.png` … `5-entry-cloud.png`, `2b-abeam-lookdev.png` | The camera plan as stills, with captions |

```bash
python make_guides.py
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python patrol_guide.py
python compose_blockout.py
```

## Look test (one paid generation)

`prompts/patrol-abeam-lookdev-v1.png` (1672x941 RGBA, with provenance alongside) came
from `prompts/patrol-abeam-lookdev-v1.txt`. Its references were the abeam pose guide
(geometry), the abeam light guide (light), the vanilla sprite (identity) and
`heg-hero-v2` (material). `review/2b-abeam-lookdev.png` composites it. It settled three
things:

1. **Identity and scale read.** Hegemony livery, barbettes along the deck and the
   engine block all read at 105 m, and the hull reads as massive behind a 1.4 m window.
   The pose and heading held. It came back reframed (alpha IoU 0.77 against the guide;
   it extended the stern and grew taller), so production passes need a registration fit
   like `heg-flotilla-mid-v2-registered`.
2. **ImageGen will not leave the flank in black shadow.** It was told twice and given a
   light guide that is black there, and still lit the flank as readable mid-dark metal.
   The physically coherent look has to be made in compositing: grade the painted flank
   down to planet-shine level using the light guide as a matte. Alternatively, accept a
   lifted flank as a deliberate cheat (see the open questions).
3. **The blockout's flank is unfit as an edit target.** The heightfield's solidified
   edge is a jagged curtain, and the generation copied it faithfully as zig-zag armour.
   The production proxy needs a modelled flank: a clean extruded side profile with
   armour belts and sponsons, built from the sprite's outline, not solidified from the
   crown.

## Production build

| Piece | Script | Output / source |
|---|---|---|
| Legion pass, fighters, mounts, running lights | `render_patrol_sequence.py` (plain Python plans from `c2_core`, then runs Blender on the same file) | `build/cinematic/c2-patrol/legion-seq/f_*.png`: Cycles, 1920x1080 RGBA, 24 fps, 180-degree motion blur, `c2_core.SUN` plus planet-shine, no world light |
| Cabin and its light passes | `c2_cabin_render.py` (Blender) | `guides/c2-cabin-paint.png` (painting + glass alpha), `c2-cabin-sun/shine/window.png` (16-bit light), `c2-cabin.json` |
| Jangala | `c2_planet.py` | `guides/c2-planet-guide.png` from C1's maps; plate `assets/c2-planet-v1.png` (ImageGen from that guide) |
| C2 frames | `compose_c2.py` | `build/cinematic/c2-patrol/{frames,c2-patrol.mp4,timeline.json,contact-sheet.png}` |
| Entry frames | `compose_entry.py` | `build/cinematic/entry/{frames,entry.mp4,timeline.json,contact-sheet.png}`; cards `assets/c2-entry-glow-v1.png`, `c2-entry-cloud-v1.png` |

```bash
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python-exit-code 1 --python c2_cabin_render.py
python c2_planet.py
python render_patrol_sequence.py            # GPU; C1_CYCLES_CPU=1 for CPU; --resume, --only A:B
python compose_c2.py
python compose_entry.py
```

Production decisions that supersede the plan above:

- **Camera.** C2 and the entry use the cabin model's own camera, 19.29 degrees down
  (`c2_core.CABIN_PITCH_DEG`), not the grey frame's 18.6. The painted cabin is rendered
  from the camera it was painted for, so nothing is re-projected. Its glass is the same
  opening the grey frame measured (left jamb x = 157 + 0.178y, sill y = 707).
- **Cabin relight is geometric, not painted.** The painting is the albedo; Cycles light
  passes of the cabin model (sun, planet-shine, a window emitter) are the light. Below
  the sill front (y 812) the model has a ledge where the painting has a wall, so direct
  sun is taken only above it. No generations were spent on the frame.
- **The patrol is the Legion model** at its own `geometry.json` placement, read at
  build time, so a new pass of the hull re-renders by running the script again
  (`C2_LEGION_BLEND` swaps the file). WS 007 tracks the camera within its arc and
  slews to fore over 1.6 s from "Patrol has your signature"; every other mount is
  laid fore or aft for the whole pass.
- **Scale is per hull.** `c2_core` reads the Legion's `metres_per_unit` and dimensions
  from its geometry.json. The starboard extreme stays 105 m from the glass and the crown
  3 m below eye level, and the pass speed and de-orbit rise scale with the hull's length,
  so the shot keeps its clock at any size. A new figure means re-running the render.
- **Fighters.** These are the Broadsword cinematic model (`broadsword-geometry`), each at
  its own `metres_per_unit`, in the same render and banking into their turns. Two
  launch from the forward hangar WS 013 at 4.10 and 4.75 s and accelerate out ahead.
  Two recover into the aft hangar WS 011 at 9.85 and 10.75 s, coming straight in from
  aft along the mouth's axis. A fighter whose span does not clear the mouth with 15%
  margin rolls just enough to thread it (`c2_core.recovery_roll`; about 34 degrees at
  Legion 0.61 and Broadsword 0.50). The aft hangars recover only, because their mouths
  face aft. No Hoplon model exists yet.
- **Running lights** are the hull's own `running_lights` from geometry.json.
- **Planet drift** is per row at the true ground rate (about 7 px/s at the horizon,
  21 at the sill), not two translating cards.
- **The entry ends on C3's frame 0** (C3's cabin at 9 degrees around flat entry white,
  per the C3 hand-off). The 19.29-to-9-degree change happens under a bloom to white
  at 2.72 s.
- **Audio cues** (player dicts). C2: squelch open, line 7, squelch close, line 8.
  Entry: line 9 at 0.16 s (no squelch), wind from 0.35 s. Neither adds a bed: C1's
  cabin bed runs under the whole hail. Line 9 is placed by its own rule, 1.4 s after
  line 8 ends. `build_hail_preview.timeline()` now gives that gap to the catalog's last
  line, C4's Nessa memory.
- **Packaging.** `cinematic_export.py --shot c2` / `--shot entry` writes
  `mod/cinematic/jangala-arrival/{c2,entry}.mp4` and their timelines.

## Owner decisions (2026-10-16)

- **The patrol is a Hegemony Legion (XIV livery), not the Dominator.** It is a
  battlecarrier, so fighters launching and recovering around its flight decks are
  part of the pass. Fighters are small beside the hull and fly as sprite cards; the
  hull itself is a real 3D model built through the ship pipeline (the "Ship detail
  refinement" session's Legion blockout). It carries named deck and launch-bay
  openings for the fighter cards and a keyable turret. The blockout has landed:
  `art-source/company-prologue/legion-geometry/legion-geometry.blend`, with the C2
  placement, flight-deck mouths and launch points, and turret pivots in its
  `geometry.json` (WS 007 is the turret flagged to train on Second Wind). At
  0.65 m per hull unit it is about 192 m long, 191 m in beam and 33 m keel to
  crown; abeam it is 1521x376 px against the Dominator stand-in's 1636x418. The
  Dominator numbers below are superseded by that file wherever they differ.
- **C2 runs 15.0 s** to carry hail lines 7 and 8. Accepted.
- **The one window is the orange-and-cream cabin** of `forest-reveal-realism/` and
  `entry-window/`, not the grey `realism-lookdev` frame. It matches the cabin model,
  so C2's frame is rendered from that model at C2's camera (`first-open-sky/`'s
  `c3_cabin_guide.py` and `c3_cabin_render.py` show the method) and its painted
  surface relit into the space, glow and cloud states. The glass measurements and
  `guides/window-mask*` here were taken off the grey frame and must be re-derived
  from the model before production. This settles question 8.
- **Second Wind is being rebuilt**, so every space shot showing it is re-rendered
  when the new model lands.

## Open questions for the owner

1. **C2 runs 15.0 s, not about 12.** The hail sets it: line 7 starts at 38.16 and line 8
   ends at 51.46. Cutting line 8 (about 5 s) would bring C2 to about 10 s, but the
   read-back is also the de-orbit cue that clears the patrol from the glass. Proposed
   storyboard edit: C2 about 15 s, entry 52.70–55.70.
2. **The patrol is a rendered sequence, not a card.** The storyboard says "the patrol
   layer moves sideways across the window". A hull that fills the glass sweeps about
   60° of view and visibly turns in perspective, so a sliding card would be wrong at
   both jambs. Proposed storyboard edit: "the patrol is a rendered sequence from painted
   key poses".
3. **How the patrol leaves.** A 220 m-beam hull 105 m away cannot clear a 60° window
   sideways at a watchable speed: its far side needs about 570 m of travel. The plan has
   Second Wind start its de-orbit on Castle's read-back, so the patrol rises out of the
   top of the glass. The alternatives are a faster pass (about 45 m/s, 25 px per frame,
   which strobes) or letting the glow wipe over the departing stern.
4. **Is the patrol the Dominator?** It gives continuity with C1's hero. An Enforcer
   (136 m) or Eagle (218 m long, narrower) would pass more cleanly but loses that link.
5. **A black flank or a lifted flank?** The physically coherent sun (C1's, carried over)
   leaves the near flank in shadow. The silhouette against Jangala reads strongly in the
   blockout. Moving the sun behind the camera would light the flank but breaks C1's and
   C3's sun direction. The recommendation is to keep it and grade the paint down.
6. **No stars in C2?** At this exposure there should be none. C1 shows stars. Keep them
   out of C2 (recommended), or allow a faint star card for continuity.
7. **Window side and flow.** This sheet uses a port window with the bow to screen right
   and the world flowing right to left, taken from `parallax-window`'s measured motion.
   Please confirm, so that C3 and C4 do not reverse it.
8. **"One frame throughout" is not yet true.** `realism-lookdev`'s frame (glass x
   157–1764) differs from the forest-reveal cabin that C3 salvages (x 232–1686), and
   from entry-window's orange-and-cream Blender cabin. Either C3's world layers are
   recomposited behind the realism frame, or the storyboard's frame decision changes.
   Proposed storyboard edit: name `realism-lookdev` as the frame for C3 explicitly.
9. **The frame needs new paintings.** A 3840x2160 repaint in three light states (space,
   glow, cloud), from one registered geometry: three or more paid generations. Approve
   the scope.
10. **Turret scrutiny beat.** One dorsal turret tracks Second Wind as the bow enters and
    slews back to fore and aft on "Patrol has your signature". It is free in the
    rendered sequence. Wanted?
11. **A glimpse of Second Wind.** The storyboard mentions "C2's glimpses" of the ship's
    damage. A fixed sliver of Second Wind's own scarred hull and a **cold mount** at the
    glass edge, rendered from the full-surface master from the cabin camera, would show
    "mounts cold" from our side. Wanted, and where?
12. **Assumed physical numbers.** Jangala's radius (6000 km) and the 200 km altitude are
    assumptions; vanilla gives neither. The 200 km altitude puts the horizon at y ≈ 443.
    A different altitude moves the horizon: 120 km puts it at about y 365, and 400 km
    lowers it to about y 583.
