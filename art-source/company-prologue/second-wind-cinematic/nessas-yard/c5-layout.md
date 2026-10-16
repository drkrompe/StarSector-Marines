# C5 NESSA'S YARD: shared layout for every layer

Shot C5 of `storyboard-v3.md`: about 8 seconds at 1920x1080 and 24 fps, outside
the window for the first time since C1. Every painted layer follows this sheet.
`build_c5_guides.py` owns the camera and the additions, and writes
`guides/c5-camera.json`, the measured screen positions. `compose_c5.py` owns the
push. If this sheet and the code disagree, the code is right.

## Purpose and payoff

They made it. Second Wind is down, standing level in a private cradle, and it is
plainly a wreck: craters, sheared plates and salvage straps, with vapour coming
off a hull that should not have survived the trip. People come out to meet it.
Nessa stands apart with her arms folded, too small to have a face, and that is
the whole introduction the picture gives her. Castle tells the meeting in Y1.

The camera repeats C1's angle on purpose. C1 met Second Wind from astern, engine
bells toward the camera, as it pushed in toward Jangala. C5 ends the arrival on
the same view, starboard stern quarter, engines toward the camera, now cold and
venting on the ground. The bookend is the payoff: the same ship from the same
side, but down.

This is **Greywater Down, private cradle Kilo Nine** (the hail, line 2).

## Camera

- **Position.** A person's view from the yard's perimeter walkway, 7 m above the
  hardstand, at (−128, −178, 7) m in the settled landing scene's coordinates
  (+X is the ship's bow, +Y is its port side, +Z is up; the hull's root is at the
  origin). It aims at (10, 0, 17). The view is starboard stern quarter: the
  engine bells are at the left and nearest, and the bow recedes to the right.
- **Lens.** 40 mm on a 36 mm sensor (f = 2133 px at 1920), so the horizontal
  field of view is 48°. That is the same field as C4's window within 2°, so the
  cut from C4 to C5 does not jump in lens character.
- **Motion.** A slow push-in, scale 1.00 to 1.08 with ease in and out over 8 s,
  centred on (1000, 600). That is just aft of midships, between the hull and
  Nessa, so she stays near the centre and the gantry stays in frame. Nearer
  layers take more of the push: each layer scales by the camera scale raised to a
  weight of 0.35 for the sky and tree line, 0.6 for the yard, 0.85 for the ship,
  cradle, crew and steam, and 1.0 for the foreground. Nothing translates except
  through the push, and nothing rotates.
- **Duration.** 8 s, then a straight cut into Y1.

## Sun and atmosphere

- **The same sun as C4** and every other shot: (−0.61, −0.09, 0.78) toward the
  sun, which is astern, slightly to starboard, at 51°. From this camera it is
  behind the viewer and to the left. The stern faces and the dorsal plates take
  full light. The starboard flank takes the light at a grazing angle along its
  length, and that raking light is what makes the craters read. Shadows fall
  forward along the hull to the right and away. The hardstand under the hull is
  in deep shade.
- **Time.** Late morning, a few minutes after C4.
- **Air.** Humid haze, with gentle falloff over a few hundred metres. The far
  jungle line goes blue-green, and the yard halls pale by about 15%. The steam is
  lit from behind the camera, so it is bright, sunlit and translucent. The heat
  shimmer over the engine bells is the only distortion.
- **Ground.** Dry, stained concrete. The look test came back with wet, reflective
  puddles, which `landing-wreck-pass/yard-style-contract.json` already ruled out
  for this yard. Ask for dry ground explicitly.

## Scale and distance

The ship's scale is the landing scene's provisional 1.5 m per hull unit. In
the measured scene Second Wind is 150 m long and 48 m tall. Sizes are in output
pixels at 1920x1080, before the push. The push adds up to 8% by the end.

| Subject | Distance | Real size | On screen |
|---|---:|---:|---:|
| Second Wind, stern bell to bow | 180–270 m | 150 m long | about 1,170 px (x 256–1426) |
| Second Wind, keel to dorsal | 226 m | 48 m tall | about 450 px |
| Largest engine bell (mid-hull pod) | about 175 m | about 13 m across | about 155 px |
| Cradle contact pad | about 200 m | 10 × 10 × 5 m | about 53 px tall |
| Sheltered crew gantry | 197 m | 16 × 8.5 × 10.6 m | 115 px tall |
| Yard crew member | 197 m | 1.8 m | **19–20 px tall** |
| Nessa | 191 m | 1.72 m | 19 px tall |
| Walking pace | 197 m | 1.3 m/s | 14 px/s, 112 px over 8 s |
| Repair hall (district, left) | about 400 m | 20 m tall | about 105 px |
| Far jungle line | 2.3–2.9 km | 25 m canopy | about 20 px |
| Foreground barrier | 52 m | 0.9 m | about 37 px |

At 19 px a person reads as a person but has no face, which is what the
storyboard asks for. The look test painted the crew three to four times too
large (see *Look test*), so people are never left to the plate pass.

## Layers, back to front

Six render passes, of which five are depth layers. Steam is an effect sheet on
the ship layer, not a sixth depth.

| Layer | Contents | Depth | Push weight | Guide | Painting method |
|---|---|---|---:|---|---|
| L1 sky | Humid sky and cumulus, the far jungle line and low hills | 2.3 km → ∞ | 0.35 | `guides/c5-L1-sky-guide.png` (opaque) | One Codex plate at 3840 × 2160 from the guide (horizon and tree line placement), reskinned; no ship, no yard |
| L2 yard | The port-refinement district: repair halls, sawtooth stores, gantries, tanks, light masts; yard ground beyond the seam at Y = 190 m | 300–1,900 m | 0.6 | `guides/c5-L2-yard-guide.png` | Reskin of the guide with alpha; the seam line sits just behind the cradle and is hidden by the hull and the hardstand edge |
| L3 ship | **Second Wind** settled in the cradle; contact pads, rails and the dark blue-grey berth hardstand; apron ground out to the foreground; the sheltered gantry with its jib hoist | 150–270 m | 0.85 | `guides/c5-L3-ship-guide.png` | **Two passes.** The hull as a Blender render of the damaged master (`second-wind-full-surface/`, appended and placed at the settled transform), lit by the shared sun, as a cut-out card. Then the cradle, apron and gantry as a Codex reskin of the guide with the hull area held out. The hull render may take a light Codex surface pass only if overlaid and registered as in `landing-refinement/` (landmark affine fit) |
| L3s steam | Vapour from the two cradle cooling vents and from the hull beside the engine bells | on L3 | 0.85 | `guides/c5-L3s-steam-guide.png` (placement blockers only) | **Sheets**, not paint: 24–32 frame loops of rising, sunlit vapour (two vent loops, one hull loop), drifting up and right at about 6–9 px/s with slow opacity breathing. A heat-shimmer sheet over the engine bells applies a small screen-space refraction (see *Open questions*) |
| L3e crew | Five yard crew; Nessa | 191–200 m | 0.85 | `guides/c5-L3e-crew-guide.png` | **Cards**, never part of the plate: walk-cycle sheets (8 frames, side three-quarter, at 4x painted size, about 80 px tall, then scaled to 19–20 px); Nessa a still card with a slow 2-frame weight shift |
| L4 fore | A low yard barrier, a cable reel and a service cart along the bottom edge | 45–55 m | 1.0 | `guides/c5-L4-fore-guide.png` | Codex reskin with alpha. These props also hide the apron's differential-scale seam, up to about 15 px by the end of the push |

`guides/c5-clay-all.png` is the whole clay frame for reference. Every guide is
3840 × 2160, rendered in Cycles from `landing-wreck-pass.blend` at its settled
frame 774. The source blend is opened read-only and never saved.

**Ship card consistency.** The damage must match C1's entrance card and C2's
glimpses (storyboard: "No new damage appears during the descent"). C1's
`render_ship_card.py` renders the same master, so C5's hull card should come
from that script with a `yard` view added, using this camera's transform from
`guides/c5-camera.json`. That way both shots use one lighting setup and one set
of materials.

## Moving elements

| Element | Layer | Motion | Method |
|---|---|---|---|
| Yard crew, five, in grey coveralls and biofilter masks | L3e | Leave the gantry and walk toward the ship along (−0.79, 0.61), parallel to the picture plane, at 1.3 m/s: 14 px/s on screen, plus the push | Walk-cycle sheet at one fixed angle. The path stays parallel to the picture plane, so the size does not change (under 1% over the shot) and the angle never does. Each walker is staggered by 2–5 frames |
| Nessa | L3e | Stands, arms folded, facing the ship and the arriving crew | Still card, optional 2-frame weight shift. She does not walk: the stillness is the characterisation |
| Steam from the vents and hull | L3s | Rises and drifts right, breathing | Loop sheets (above) |
| Heat shimmer over the engine bells | L3s | Constant | Shimmer sheet as a masked refraction offset, 1–2 px amplitude |
| Gantry jib hoist | L3 | Lowers and raises a parts crate about 2 m, a 4 s loop: about 20 px | A 24-frame loop sheet of the hoist line and load at a fixed angle |
| Yard light mast beacons | L2 | Slow blink | Additive dots, as in C4 |

The camera only scales, and the walkers walk parallel to the picture plane, so
every element is safe under the rules in `storyboard-v3.md`. Nothing walks
toward or away from the camera.

## Audio and dialog sync

- **No dialog.** Per the storyboard, the flight noise falls away to ordinary yard
  sound. Use cabin rumble from C4 ducking out over the first second, then the
  open yard: steam hiss from the vents, ticking hull metal cooling, a distant
  crane horn, the hoist's winch, faint radio chatter from the gantry, boots on
  concrete as the crew arrive (late, quiet), and jungle insects under it all.
- Cut into Y1, where Castle reports the meeting, on the last frame of the push.
  Do not let a line start over C5. The silence after C4's "Let me talk first."
  is the beat.
- Steam bursts in the sound should follow the steam sheets' loop phases.
- Asset candidates already in `asset-pipeline/src/tool/resources/audio/`:
  `ambient-machinery/` and `tender/`.

## Salvage

| Source | Used for |
|---|---|
| `landing-wreck-pass/landing-wreck-pass.blend`, frame 774 | Settled hull pose, cradle pads and rails, berth hardstand and the port-refinement district: the whole guide geometry |
| `second-wind-full-surface/second-wind-full-surface.blend` | The hull card: all-face relightable material on the same 196 meshes and damage |
| `landing-wreck-pass/review/settled.png` | Identity and damage material reference for any Codex pass on the hull |
| `landing-refinement/` | Starboard registration method (11-landmark affine fit) for reprojecting generated hull detail; yard finish as a material reference |
| `port-refinement/assets/secondary-materials-v1.png` | Yard concrete, slate and teal surfaces, matching C4's port |
| `corvus-approach/render_ship_card.py` | The card renderer, extended with this camera, so C1 and C5 share one ship look |

## Build

```bash
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup ../landing-wreck-pass/landing-wreck-pass.blend --python build_c5_guides.py -- --samples 24
python compose_c5.py                  # review/c5-blockout-t0.0, 2.5, 5.0, 8.0 .png
python compose_c5.py --frames         # every frame to build/cinematic/c5-nessas-yard/frames
```

## Look test (one generation)

`assets/c5-looktest-v1.png` (1672 × 941) with brief `prompts/c5-looktest-v1.txt`
and its `.provenance.json`. It reskins `guides/c5-looktest-guide.png` (the
whole clay frame), with `landing-wreck-pass/review/settled.png` as the hull's
identity and damage reference. Review copies at 1920x1080:
`review/c5-looktest-1080.png` and `review/c5-looktest-registration.png`
(a 50% overlay on the guide).

What it settles:

- **The angle pays off.** The stern-quarter view of the cratered flank in raking
  sun, with steam off the engines and the cradle under the hull, reads at once as
  "down, and a wreck". The hull's silhouette, bells, pods, bay and pads register
  to the guide almost exactly.
- **People must be cards.** The plate pass drew the crew at three to four times
  their true size, about 70–80 px against the measured 19–20. It also moved the
  file left and put Nessa under the hull. Scale and placement of people belong to
  the cards. The plate pass should be told "no people".
- **It invents what it is not told about.** It added jungle hills behind the
  yard and wet puddles. The hills are welcome and belong on L1. The puddles go
  against the yard style contract. Ask for dry, stained concrete.

## Open questions

1. **Second Wind's size.** In the landing scene, 1.5 m per hull unit makes the
   ship 150 m long and 48 m tall, so a person is 19 px against a 450 px hull.
   The image model's instinct was a smaller ship (or larger people). Is 150 m the
   intended scale for a converted mining escort? The answer changes the crew size
   in C5 and the ship's size against the station in C1.
2. **Heat shimmer.** The storyboard asks for shimmer "from sheets". Done
   honestly, shimmer offsets the pixels behind it, which is a local distortion,
   not a layer that translates. Proposal: allow a masked screen-space refraction
   (1–2 px) over the engine bells as the one exception to "no layer deforms", or
   fake it with a translucent shimmer sheet only.
3. **Kilo Nine marking.** A stencilled "K9" on the cradle hardstand or the
   gantry would tie the hail to the place without dialog. The C1 layers carry no
   text. Is diegetic stencil text on a surface acceptable?
4. **Nessa's mark.** At 19 px she is a silhouette. Should she get one cue to set
   her apart from the crew, such as no mask, or a coat instead of coveralls? A
   bare face in a biofilter-mask yard says she has been on Jangala long enough
   not to bother. Or is that against the biosphere rule?
5. **Where the crew come from.** The blockout puts the sheltered gantry at the
   right, by the bow pad, so the crew walk left toward Nessa, across the frame
   and parallel to the picture plane. An alternative is a gantry at the left by
   the engines, with the crew walking right toward the bow, at the cost of
   crossing the heat-shimmer area.
6. **C4 → C5 cut.** C4 ends on the port and "Let me talk first.", and C5 opens
   with the ship already down. Removing the descent is a storyboard decision.
   Does the cut want a one-second sound bridge (landing-gear thump and engine
   spool-down over black or white) to make the time jump legible?
