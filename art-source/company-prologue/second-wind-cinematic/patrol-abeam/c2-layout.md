# C2 PATROL ABEAM and the entry transition: shared layout

Shot C2 of `storyboard-v3.md` and the entry transition after it: 15.0 s plus 7.0 s,
1920x1080, 24 fps. Every layer obeys this sheet so the pieces composite into one
camera. `c2_core.py` holds the same numbers as code: camera, sun, planet, the patrol's
track and the hail timings. The guides, the blockout and any later compositor read
them from there.

Status: REWORK. The owner has moved C2 to Second Wind's bridge (2026-10-18, see
*Bridge pass* below); a grey blockout is in review. The cabin-window production build
described in the rest of this sheet is what currently ships, and stays the plan of
record for the entry and for anything the bridge pass does not replace.
Updated: 2026-10-19 — the bridge pass uses the finished, textured Legion, re-framed for its 292 m size (pass at 340 m); camera pass findings and switchable refinements (owner to pick).

## Bridge pass (owner direction, 2026-10-18)

**Why it changed.** The ship sizes were settled (the Legion is 0.99 m per unit: about
292 m long, 291 m in beam, 88 m keel to crown). Measured through the cabin camera, the
old pass (Legion at 0.61, about 180 m) filled 50% of the glass at its best moment,
with the nearest armour about 113–131 m off; the same rules at 0.99 fill 78%, with
the visible flank 125–150 m off and a 43 m/s pass that moves 20 px a frame. Rather
than retune a flat wall sliding past one window, the owner asked for the shot to be
seen **from the bridge**: one continuous shot, no cut, the camera turning like
someone's head. It starts looking forward over Second Wind's own trunk and forward
turrets at the Legion ahead, follows it through the port chamfer to the port side
slits as Second Wind closes and passes, and watches it rise out of the top of the
slits when the de-orbit starts.

**The bridge.** The command deck is the one on Second Wind's model (`sw_bridge`): a
low armoured cabin about 6 m square on the forward end of the spine, with slit
glazing round the front and sides, about 55 cm of glass between the sill ledge and
the visor brow, behind heavy posts. Its interior is the model's own
(`sw_bridge_interior`, in the patched state the shot appends): a standing bridge sunk
into the waist housing, the floor 1.3 m below the slits and about 2.4 m of headroom,
with a raised flight dais forward (the owner's crow's nest) where the pilot sits at a low console,
Castle's comms bank along the port side, a plot table and the aft hatch. The camera is
a standing eye about 1.7 m off the floor, on a 22 mm lens: first on the clear port side
of the dais, leaning over the console about 60 cm behind the forward glass, then stepping
down to Castle's station, 70 cm inboard of the port glass. The eye height is the same at both,
since leaning on the dais and standing on the floor put it level with the slits. The pilot (an
unnamed crewman, seated at the helm) and Castle are MakeHuman figures from `pilot-figure/` and
`castle-figure/` once their .blends are built, and mannequins until then (`sw_bridge_interior.figures`). The
pilot flies with a yoke on a telescoping column (owner, 2026-10-19). He sits back, forearms on
the armrests, with it stowed; leans in and draws it out to him over 8.3–9.45 s and flies sat
back; pushes it for the de-orbit's pitch-down (9.5–13.2 s, with a small turn to port in the
middle); and lets go over 13.5–14.7 s, leaving it drawn out (his package's action, on C2's
frames). His head and eyes follow the fighters past to port, glance at the console and turn
to the Legion as it comes abeam. Castle listens with her hands on her bank, looks out through
the port slits at the Legion over 7.1–9.9 s, keys the bank with her hand at her earpiece for
the read-back (9.76–13.76 s), braces a little as the deck tips, and lowers her hand by
14.6 s. `second-wind-geometry/bridge_figures_clip.py` renders both side by side as a check.

**The pass.** The Legion comes the other way, bow first, 12° off reciprocal, and
Second Wind passes it to port. Closing speed bleeds from 140 m/s at the cut to
22 m/s at abeam (8.0 s, under "Patrol has your signature"); it starts 650 m ahead,
28° off the bow, and passes with its midline 340 m out (its near flank about 195 m). From 9.6 s Second Wind's
de-orbit lifts it relative to us (9 m/s²) and it clears the top of the slits by
about 14 s. The camera's yaw follows the Legion's bearing with a 0.35 s lag, from
22° to port at the cut to 104° (just aft of the beam).

**Re-framed for the finished Legion (2026-10-19).** The pass was laid out at 265 m, and the
292 m hull (291 m in beam) overfilled it: from 7 to 11 s it ran off the bottom of the frame,
and at abeam the brow, sill and posts hid 29% of it. `c2_bridge_blockout.py --hull-fit` renders
the hull and Second Wind's own silhouette as masks and reports how much of the hull is in the
frame and in the glass. A sweep of pass distance (340, 400, 460 m) and height (crown 20 or
40 m above the eye) settled on **340 m with the chosen 20 m**: the nearest pass at which the
whole hull, bow to stern, sits inside the glass from 7.5 to 12.5 s (97–100%) with nothing off
the frame, while it still rises clear of the slits by 15 s. The opening is unchanged (the eye
is on the fighters, the carrier beyond the forward console). The flyby sound's copy of the
geometry (`arrival-audio/synth_flyby.py`) carries the new distance; its stems need re-synthesis.

**The textured asset.** The blockout and the lit render append `legion-cinematic`'s `LEGION`
collection (the lit render adds its `SWFX` effects in the same load) and move its root only, at
0.99 m per unit. Its drives idle on the kit's LOW_TECH plumes (master throttle 0.2, exhaust
haze off: vacuum), its 27 running lights blink on their own data, lens and kit lamp together,
and its mounts stay at rest. `legion.blend` is built, not committed (its `finalize.sh`).

**Banked, chosen (owner, 2026-10-18).** Of the two variants reviewed, the owner
chose (a): banked 8° toward us with its crown 20 m above our eye, so its deck and
turrets face us. The rejected (b) was level, closer and higher, a flank wall under a
deck we could not see.

**Fighters first, then the carrier (owner, 2026-10-18).** The air wing screens ahead of
the Legion: two vics of three Broadswords (the Legion XIV flies two Broadsword wings;
no Hoplon model exists yet) come off its bow toward us at 160 m/s and pass our port
slits, the first 55 m out and 7 m above our eye at 3.0 s, the second 80 m out and
9 m up at 4.2 s. The person on the bridge watches them first: the head starts almost
dead ahead on the first flight, follows it in, lets it go as it whips past (overshooting
to about 49° at 3.0 s), and settles on the carrier, which then fills the turn to the
beam. Only the head's target changes; the 8.0 s abeam beat is unchanged.

**We go down, and the ship shows it (owner, 2026-10-18).** When the de-orbit starts at
9.6 s, Second Wind pitches 16° nose-down over 3.5 s. The whole ship pitches (hull,
bridge and camera together) against a fixed sun, planet and Legion, so from inside
the Legion swings up the slits faster than its climb alone would carry it, Jangala
comes up into the bottom of the view, and the sunlight moves across the bridge. The pitch is
part of the shared motion (`ship_pitch_deg`), but only the lit render applies it; the
grey blockout's frames stay level.

**Lit render: first review pass (2026-10-19).** Everything in the shot is now final:
the Legion, the Broadswords and Second Wind with its bridge. The first lit pass is a
half-size review at 8 fps, not the final render. `c2_bridge_render.py` dresses the
blockout's motion for Cycles at 24 fps with 180° motion blur, importing it so the two
cannot drift apart:

- Second Wind is the patched state from `second-wind-states`. The .blend is built, not
  committed: copy it from Drive `builds/second-wind/<commit>/patched/`. The script
  refuses a copy whose sha256 differs from the committed manifest's. The bridge slits
  are real glass and no shot hides them, reflecting the room at the model's own strength
  (about 10% straight on). A faint version (1%, scaled to 35%) was tried and dropped: the
  slits stopped reading as windows (owner, 2026-10-20).
- The bridge is painted steel with console screens and warm ceiling light, plus a soft
  overhead fill over the forward station that the glass does not show. It is lit just
  enough for the consoles and frames to read and the fighters to be found at 3.0 s; the
  view outside stays the brightest thing in shot (owner, 2026-10-19). A
  light strip at the top of the slits was tried and dropped: 65 cm from the eye, even
  dimmed, it read as a band across the view.
- The Legion keeps its own materials, with its throats and running lights as in the cabin
  version.
- The Broadswords are the finished asset (`broadsword-cinematic`, final 2026-10-19). Each
  fighter is its own append of BROADSWORD plus SWFX, so each has its own effects
  controls. The drives run at full throttle, the guns stay cold and the running lights
  are on, with no exhaust haze or landing dust in vacuum. The gear stays up, its saved
  pose. The .blend is built, not committed: copy it from Drive
  `builds/broadsword/<commit>/`. Both scripts refuse a copy whose sha256 differs from
  the committed manifest's. The blockout loads the same model without effects, tinted
  for Workbench. `C2_BROADSWORD_ASSET` points either script at another copy.
- Jangala is a proxy sphere scaled about the eye and shaded from its own normal, so
  nothing near it can throw a shadow on it. It carries C1's maps, which are soft at
  this range, and a thin limb glow, and it turns aft at the orbital rate.
- Light is the same as the cabin version: C1's sun and the planet-shine, no world
  light.
- **Stars are in (owner, 2026-10-18), overriding open question 6.** C1 shows stars,
  so C2 does too. C1's star plate is flat and cannot follow a 100° head turn, so the
  bridge sky carries a procedural field: one point per small cell of sky, about 3.5%
  of cells lit, most of them faint, tinted warm to blue. Only camera rays see it, so
  it lights nothing. Each star is about a pixel across; sub-pixel stars came out
  invisible, averaged away and then removed by the denoiser.

A frame takes about 10 s on the GPU.

**The bridge scene is modelled in grey** (`sw_bridge_interior`, owner review
pending): the room, consoles and screens, seats, rail, plot table, hatch, ceiling
fittings and the patched state's jury-rigging, with mannequin crew. It is untextured
and its lighting is not designed. The co-pilot's seat was removed (owner) so the pan is clear,
and both stations moved closer to the glass for more view: the share of the frame showing
space rose from 9% to 14% forward and 28% to 40% at the port glass. The slits themselves are
unchanged; making them taller is an exterior change.

Review runs: `review/cinematic-c2/2026-10-18_bridge-blockout_windows/` (the two
variants) and `.../2026-10-18_bridge-fighters_windows/` (banked, with the fighters);
the interior: `review/second-wind/2026-10-18_bridge-interior_windows/`.

**Camera pass (2026-10-19, owner review pending).** The move above was played through the
blockout with the real pilot and Castle at 12 fps, pitch applied, and measured frame by frame
(`--measure`: what share of the frame is glass, posts and bridge, or each figure; how much of
the Legion a figure hides; how near the eye comes to any surface). What it found:

- The camera never comes nearer than 36 cm to anything, so it passes through nobody and nothing,
  and the pilot, yoke and Castle never hide the Legion. They are also never in frame: Castle
  stands behind the camera at her own station and the pilot is well to starboard of the dais.
- The body's position was tied to where the head points, so when the head swung after the
  first flight and back to the carrier (2.8-3.6 s) the whole person slid forward and back with
  it, and left the dais just as the fighters arrived. It read as a dolly.
- The fighters pass about 7 m above our eye. From the dais that is the top edge of the slit:
  they skim along under the visor brow and are gone behind it by 2.8 s, never more than 0.6% of
  the frame. Flying them 5 m lower did not help (the posts and the speed hide them either way).
- The view is mostly bridge: 7-15% glass for the first 4.5 s, about 37% at the port glass, where
  the head's upward tilt spent a third of the frame on the visor brow.

Refinements, each a switch in `c2_bridge_blockout.CAMERA` (`--camera`, or `C2_CAMERA` for the
lit render; `none` gives the reviewed move). **All six are the default (owner, 2026-10-19):**
**walk** (the step down is a person walking on its own clock, 4.6-6.6 s: back off the console,
down the dais step past the grab pole, four paces with a footfall in each), **duck** (leaning
lower and nearer the glass to keep the fighters in sight, 1.5-4.4 s), **breath** (a few tenths
of a degree of breathing and weight shift), **frame** (the head sits lower at the port glass so
the slit fills the frame), **lean** (leaning over the comms bank toward the glass at abeam, 70 cm
to 45 cm, straightening for the read-back), **lens** (22 mm creeping to 27 mm toward abeam and
back; an eye cannot zoom, so this is a film camera's choice, and it matters more with the Legion
at 340 m). The shot is grounded in the Legion (owner, 2026-10-19): the camera stays at the glass
and never turns onto the pilot or Castle; a glance to the pilot and a step back to bring Castle
into the frame edge were both tried and dropped. Castle is seen in passing instead: she works the
forward panel of her bank (`sw_bridge_interior.CASTLE_AT`, x 14.4), and the walk passes behind her
back on its way to the middle panel, so she crosses the frame left to right at 5.55-6.0 s, about
half a metre off (nearest 45 cm, at most 15% of the frame), looking out at the Legion. Thinner window posts were previewed (40%
width: about 3 points more glass) and declined; the posts stay as modelled. With walk, duck, breath, frame and lean: glass at abeam 37% to 50%, the fighters
up to 2% of the frame, nearest surface 41 cm. Review:
`review/cinematic-c2/2026-10-19_camera-pass_windows/`.

**Open for the owner.** Whether the bridge's glazing stays this
narrow (the model's slits make a letterbox, with posts that take a good share of the
view); and whether the entry glow and C3 keep the passenger-cabin window, with the
change of camera hidden under the entry's bloom to white as it already is.

```bash
python c2_bridge_blockout.py --fps 1 --burst 1.8:4.8:0.2  # build/cinematic/c2-bridge/blockout/
python c2_bridge_blockout.py --fps 12 --out <dir>          # every twelfth of a second, for motion
python c2_bridge_blockout.py --fps 12 --res 960x540 --pitch --camera walk,duck --out <dir>   # with the de-orbit pitch and refinements
python c2_bridge_blockout.py --fps 4 --pitch --measure --out <dir>   # measure.json: glass/post/figure shares, nearest surface
# Only once every asset is final (see above):
python c2_bridge_render.py --frames 0,72,192,300           # lit test frames (neighbours keyed for blur)
python c2_bridge_render.py --resume                        # build/cinematic/c2-bridge/lit/, about an hour
python c2_bridge_render.py --fps 8 --half --out <dir>       # review pass: 960x540, 120 frames, about 75 min
python c2_bridge_render.py --movie                         # lit/c2-bridge.mp4 with captions and cues
```

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

**Continuity.** The patrol is a Hegemony Legion (owner, 2026-10-16), not C1's
Dominator `heg-hero-v2`; the Dominator figures below are the original blockout's
stand-in. The sun is C1's sun, carried into Second Wind's frame. The window frame is
the one used for C2, the entry, C3 and C4.

## Camera

| | |
|---|---|
| Position | Passenger cabin, about 1.1 m behind the glass, looking out of a **port** window. Second Wind's bow is **screen right**, so the world flows aft from right to left. That is the established window flow: `parallax-window` measured its scenery moving right to left. |
| Lens | 26 mm on a 36 mm sensor (the forest-reveal and parallax-window cabin camera). Focal length 1386.7 px at 1920 wide; horizontal field of view 69.4°. |
| Pitch | 18.6° down. This is measured from the realism frame itself: its jambs converge to a vertical vanishing point about 4670 px below the top of the frame. The Blender cabin camera reads 19.3°. |
| Glass | Measured on `realism-lookdev/window-realism-v1.png`: left jamb at x = 157 + 0.172y, right jamb at x = 1764 − 0.172y, sill at y = 704. The opening runs out through the top of the frame. Through it the camera sees from 2.7° above the horizontal to 25.4° below it, and from 30° left to 30° right. |
| Motion | **None in C2.** No push, no truck, no shake; the frame does not move at all (storyboard). Everything that moves is outside the glass, or is a change of light on the frame. The entry shakes (below). |
| Duration | C2 runs 37.70–52.70 (15.0 s) on the hail clock. The entry runs 52.70–59.70 (7.0 s). |

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
| Entry fire | Flames stream across the glass from bottom right to top left, 27 degrees above horizontal, spreading from the bottom right | The glow card's filaments with its broad light divided out, streamed along that line at two speeds (520 and 1500 px/s), coloured through a fire ramp by a heat field fixed to the glass | Translate along the stream only. The heat field is the only mask, and it does not move with the flames. |
| Entry smoke | Dark plumes torn off the flames, trailing toward the top left | The cloud card's billows at their own scale, thresholded into plumes, streamed the same way at 820 px/s, lit from below by the fire | Same rule |
| Entry cloud | Cloud streams in along the same line from the bottom right, ragged edge first, warm then white, and whites out the glass | The cloud card carried along the stream, mirrored across it, with fast wisps streaming through | Same rule |
| Entry shake | The camera and the window shake together | `first-open-sky/c3_core.camera_shake`, applied to the whole picture | Shared with C3, so it continues across the cut |

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
| 52.70 | Entry 0.00 | — | Clear glass. The heat starts at the bottom right at 0.15; the shake begins. |
| 53.06–53.47 | Entry 0.36–0.77 | 9 (off channel, no squelch): "Well." | First heat in the bottom right corner. |
| 54.00–55.27 | Entry 1.30–2.57 | 9: "Somebody knows we're here." | Flames spread across the glass, full by 2.4; smoke from 1.6. |
| 55.58 | Entry 2.88 | Line 9 ends | Fire and smoke streaming up-left; shake building. |
| 56.80–58.10 | Entry 4.10–5.40 | — | Cloud streams in from the bottom right; the fire quenches by 5.7. Hardest shake. |
| 58.10–58.70 | Entry 5.40–6.00 | — | Whiteout. |
| 58.70–59.60 | Entry 6.00–6.90 | — | The white settles onto C3's frame 0 (same window, same camera). |
| 59.70 | Entry 7.00 | — | Cut to C3, which opens out of the white, still shaking. |

## Entry transition

**What the storyboard asks** (owner, 2026-10-19, after the first sound mix): a descent,
not a wipe. Longer than the first 3 s version, with the camera and window shaking and
flames and smoke streaming across the window from bottom right to top left. It joins C2
to C3 and is not a shot in its own right. It runs 7.0 s.

- **C3's window and camera, from the first frame** (owner, 2026-10-19). The entry first
  used C2's cabin camera (19.29 degrees down, so the window read keystoned) and swapped to
  C3's under a bloom; the owner saw the frame change at the cut. Now the entry is drawn
  through C3's own cabin (`first-open-sky/build/layers/cabin.png`) at C3's 9 degrees, and
  only the light on the frame, what is behind the glass and the shake change. The cut
  from C2 into the entry is where the place changes. C2's planet plate is lowered 250 px
  to where its horizon sits from 9 degrees.
- **The cabin's light.** C3's paint is lit by C3's daylight, so the cloud state is that
  paint as it is. Space and fire light come from the glass: a falloff from the aperture,
  cold planet-shine in space, orange and flickering in the fire, hotter on the bow side.
- **Direction.** Everything streams along one line, from bottom right to top left, 27
  degrees above horizontal (`c2_core.FLOW_DEG`). That is the air going past a port
  window of a ship coming down bow first: aft, as in every window shot, and up. C3 opens
  with the world moving the same way, sliding aft and rising as the ship comes out of
  its descent, so the flow never reverses at the cut.
- **Fire (0.15–2.4 s building, quenched by 5.7 s).** The heat is fixed to the glass,
  hottest at the bottom right, where the air meets the hull first, and spreads up-left
  from there. Through it the flames stream: dark lanes between yellow-white cores,
  deep red toward the top left. The planet fades behind the sheath. The cabin takes an
  orange, flickering relight from the glass.
- **Smoke (1.6–5.6 s).** Dark plumes tear off and trail toward the top left, their
  undersides lit by the fire.
- **Cloud (4.1–5.4 s, white 5.4–6.0 s).** It streams in along the same line, ragged edge
  first, warm while the heat is still in it and then white, and whites out the glass.
  The cabin trades the orange for the sequence's first soft fill.
- **Shake.** The camera and the window shake together: a fast buffet, a slower heave,
  and a fraction of a degree of turn, building through the heat to about 9 px in the
  cloud and still about 3 px at the cut. It is one function of time from the entry's
  start, shared with C3, which eases it away (`first-open-sky/c3_core.camera_shake`).
- **Into C3.** In the white (6.0–6.9 s) the frame settles exactly onto C3's frame 0;
  C3 picks up one frame later on the same shake.
- **Line 9 sits early in it.** "Well." plays over the first heat and "Somebody knows
  we're here." as the flames spread. There is no squelch: the missing key-up is how the
  ear knows she has closed the channel.
- **The time skip is honest about being a transition.** Going from 200 km to the cloud
  deck takes many minutes. Seven seconds of fire, smoke and cloud is an ellipsis.
- **Sources.** The glow and cloud cards in `assets/` (`c2-entry-glow-v1.png`,
  `c2-entry-cloud-v1.png`), streamed and coloured in `compose_entry.py`. No new art.

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
sh ../../../blender.sh --background --factory-startup --python patrol_guide.py
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
sh ../../../blender.sh --background --factory-startup --python-exit-code 1 --python c2_cabin_render.py
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
  per the C3 hand-off), and C3 picks up one frame later on the same shake. The entry
  is drawn through C3's window and camera throughout, so there is no camera change.
- **Audio cues** (player dicts). C2: squelch open, line 7, squelch close, line 8.
  Entry: Castle's first line to the Commander at 0.16 s (no squelch;
  `arrival-audio/castle_lines.py`), wind from 0.35 s. Neither adds a bed: C1's cabin bed
  runs under the whole hail. Hail line 9 was dropped with the owner's script of 2026-10-19.
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
