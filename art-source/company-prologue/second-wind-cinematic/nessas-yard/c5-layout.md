# C5 NESSA'S YARD: shared layout for every layer

Status: ACTIVE (final frames delivered)
Written: 2026-10-16
Updated: 2026-10-16 — surfaced patched hull with wear and drive detail; packaged as mod/cinematic/jangala-arrival/c5.mp4.

Shot C5 of `storyboard-v3.md`: 8 seconds at 1920x1080 and 24 fps, outside the window for
the first time since C1, delivered per `runtime-contract.md`. `build_c5_guides.py` owns the
camera and the set, and writes `guides/c5-camera.json`, the measured screen positions.
`render_c5_hull.py` renders the hull card. `compose_c5.py` owns the push, the moving
elements and the contract data. If this sheet and the code disagree, the code is right.

## Purpose and payoff

They made it. Second Wind is down, standing level in cradle Kilo Nine, and it is plainly a
wreck: craters, sheared plates and grey repair patches, with vapour coming off a hull that
should not have survived the trip. The yard crew walk out to meet it. Nessa stands apart
with her arms folded, too small to have a face, and that is the whole introduction the
picture gives her. Castle tells the meeting in Y1.

The camera repeats C1's angle on purpose: starboard stern quarter, engine bells toward the
camera, now cold and venting on the ground. The bookend is the payoff: the same ship from
the same side, but down.

## Scale and the set

Scale is the shared `metres_per_unit` in `second-wind-geometry/states/patched/geometry.json`
(0.65), so Second Wind is about 66 m (`c1_ship.SW_LENGTH_M`). The landing scene the guides
are built from still carries the old provisional 1.5 m per unit, so `build_c5_guides.py`
shrinks the ship and its cradle about the hull root by K = 0.65 / 1.5 and brings the camera
in by the same factor. That keeps the ship's framing from the look test while everything
human-sized (people, the gantry, props, the eye height) stays its true size. The district
is left where it stands, which reads as a smaller ship in the same yard.

At this scale a 1.8 m person is about 47 px tall and Nessa about 56 px: people read as
people, with no faces. (The look test's "too large" crew were nearly right for a 66 m hull.)

## Camera

- **Position.** The yard's perimeter walkway, 4 m up, at (−55.5, −77.1, 4.0) m in the C5
  set (+X bow, +Y port, +Z up, hull root at the origin), aimed at (4.3, 0, 7.4).
- **Lens.** 40 mm on a 36 mm sensor, 48° across: within 2° of C4's window, so the cut does
  not jump in lens character.
- **Motion.** A slow push-in, scale 1.00 to 1.08 with ease in and out over 8 s, centred on
  (1000, 600). Each layer scales by the camera scale raised to its weight: 0.35 for the sky,
  0.6 for the district, 0.85 for the yard, hull, cards and steam. Nothing else translates
  and nothing rotates. Then a straight cut into Y1.

## Sun and atmosphere

The shared sun of `landing-wreck-pass.blend` (`Shared sun | all shots`), read from that file
by `render_c5_hull.py` together with its world colour and AgX exposure: astern, slightly to
starboard, 51° up, behind the camera and to the left. The stern and the starboard flank take
the light; the hardstand under the belly is in deep shade. Humid haze pales the far halls
and turns the far jungle line blue-green. Ground is dry, stained concrete: the plate's
highlight sheen is pulled down below the horizon so it never reads as wet
(`landing-wreck-pass/yard-style-contract.json`).

## Layers, back to front

| Layer | Contents | Weight | Source |
|---|---|---:|---|
| L1 sky | Humid sky, towering cumulus right, far jungle hills and tree line | 0.35 | `assets/c5-sky-v1.png`, stretched so its tree line stands just under the yard's horizon |
| L2 district | Repair hall, control tower, offices | 0.6 | `assets/c5-yard-v1.png`, split by `guides/c5-L3-front-guide.png` (the near set rendered with the district as holdout), carried 10 px below the building bases |
| L3 yard | Hardstand, cradle pads and rails, gantry with jib, apron, barriers, cable reel, service cart | 0.85 | the same plate; sky keyed by colour in a band around the guide's shapes |
| L3 shade | The hull's cast shadow and the deep shade under the belly | 0.85 | `layers/c5-hull-shadow.png` plus a contact shade from the belly edge to the cradle footing |
| L3 hull | Second Wind, settled | 0.85 | `layers/c5-hull.png` |
| L3 moving | Hoist crate, steam, heat shimmer, crew, Nessa | 0.85 | cards and procedural sheets (below) |

The yard plate came back with every x right but stretched unevenly in y, so
`compose_c5.YARD_ROWS` remaps its rows onto the guide through matched landmarks (tower cab,
hall eave, jib, gantry roof and feet, hall base, pad tops). The foreground props were
re-spaced by the generator; they touch nothing that registers, so they stand as painted.
The near ground and the props share the yard layer, so nothing slides against its own
shadow under the push.

**Hull card.** `render_c5_hull.py` renders the surfaced patched Second Wind
(`second-wind-surface/build/skinned-patched.blend`, a gitignored build output; build it per
that folder's README) from the C5 camera at 3840x2160. The model has no root object, so the
script parents everything to a new empty; it is scaled so its X extent is `SW_LENGTH_M`
(exactly `metres_per_unit` for this model), centred where the shrunk landing hull stands,
with the hull's underside on the cradle's pad tops (the ventral RCS pod hangs below and is
left out of that seat). The engine and RCS parts are shade-smoothed (sharp above 35°), since
the model is flat-shaded and the bells read as faceted at this range. Everything in the near set that can stand in front
of it (`guides/c5-occluders.glb`) holds it out. All four engines are cold in the yard, so no
throat glows; bell liners and throats take a sooty matte finish (seen nearly head-on, the
glossy liner caught the open sky and read as a blue lens). Swap contract as C1:
`SHIP_BLEND`, `SHIP_ROOT` (None for a rootless model), `THROAT_PATTERN`.

## Moving elements

| Element | Motion | Method |
|---|---|---|
| Five yard crew, grey coveralls, biofilter masks | Leave the gantry and walk left toward the ship and Nessa at 1.3 m/s, parallel to the picture plane, staggered starts and cycle phases | `assets/c5-crew-walk-v1.png`: two walkers (empty hands, tool case) x 8-frame side-view cycle, one stride per 1.12 s, scaled to the guide's measured height; soft contact shadows |
| Nessa | Stands, arms folded, facing the ship; one slow weight shift at about 4 s | `assets/c5-nessa-crate-v1.png`, two near-identical frames crossfaded |
| Hoist crate | Rides 1.9–3.5 m under the painted jib tip on a 4 s loop, at the top while the crew pass under it | Crate card from the same sheet on a drawn cable; the plate's painted cable and hook are painted out |
| Vent and hull steam | Rises and drifts right, breathing on a 2.9 s cycle; vents under the belly start in shade and brighten as they rise | Procedural translucent sheets from tileable fractal noise (`steam_sheet`) |
| Heat shimmer | Pale wavering streaks rising over the near bell mouths | A translucent sheet (`shimmer_sheet`), not a refraction |

The camera only scales and every mover travels parallel to the picture plane, so each is
safe under `storyboard-v3.md`'s rules.

## Audio and dialogue

No dialogue: the silence after C4's "Let me talk first." is the beat, and Y1 starts on the
cut. `audio_cues()` lets C4's cabin bed (`jangala_arrival_cabin_bed`) run out within the
first second, then lays original DSP masters from
`asset-pipeline/src/tool/resources/audio/jangala-yard/` (README there): the ship winding
down as the sound bridge from C4, the jungle bed, vent steam breathing in phase with the
steam sheets, cooling hull ticks, the jib winch on the crate's loop, a faint unintelligible
gantry radio, a distant crane horn and the crew's boots arriving late. The review mp4 lays
the same cues for listening; the player schedules them for real.

## Build

```bash
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup ../landing-wreck-pass/landing-wreck-pass.blend --python build_c5_guides.py -- --samples 16
python render_c5_hull.py --samples 96         # layers/c5-hull.png, layers/c5-hull-shadow.png (needs skinned-patched.blend)
python compose_c5.py                          # review/c5-t*.png and review/c5-contact.png
python compose_c5.py --frames --mp4           # build/cinematic/c5-nessas-yard/{frames,c5-nessas-yard.mp4,timeline.json}
python ../../../../asset-pipeline/src/tool/resources/audio/jangala-yard/generate.py   # then :asset-pipeline:processAudio
```

Generated art (prompts in `prompts/`, provenance beside each master): `c5-sky-v1`,
`c5-yard-v1`, `c5-crew-walk-v1`, `c5-nessa-crate-v1`. `c5-looktest-v1` was the earlier
whole-frame look test.

## Decisions on the earlier open questions

1. **Ship size.** Settled by the owner: 0.65 m per unit, about 66 m.
2. **Heat shimmer.** A translucent sheet only. No layer deforms.
3. **"K9" marking.** No diegetic text. Generated lettering garbles, and the place is named
   by the hail; the player can caption it if it is ever wanted.
4. **Nessa's mark.** A long ochre work coat over dark trousers, grey hair, no cap. She
   keeps the biofilter mask, because the storyboard's biosphere rule says ground crew wear
   them; the coat alone sets her apart from the grey coveralls.
5. **Where the crew come from.** The gantry by the bow pad at right, walking left toward
   Nessa and the ship, clear of the shimmer.
6. **C4 → C5 cut.** A sound bridge, not a picture one: `c5_yard_spool_down` opens C5 with
   the ship's drive winding down, heard from outside.
