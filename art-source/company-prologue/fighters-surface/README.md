# Fighters: surfacing (pass 6)

Status: ACTIVE — all six fighters surfaced (service livery); the Broadsword accepted, the other five waiting on owner review.
Written: 2026-10-18
Updated: 2026-10-19 — the Broadsword surfaced for C2 (its geometry is its own package).

One folder per fighter for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull <folder>`; its README holds the
method). Each folder holds the fighter's inputs and Codex generations; textures,
grading, review renders and builds are script outputs and are not committed.
`<id>/rebuild.sh` replays everything except Codex.

Shared here:

- `strip_review_props.py` removes the geometry build's 1.8 m scale figure and
  pilot proxy from the surfacing copy of the build, so the figure is not
  painted into the finish reference and no unclassed review material is left.
  The geometry package itself is never edited.
- **Livery source.** A fighter sprite is 24-40 px, too small to read paint
  from, so each finish reference takes the fighter's upres master
  (`art-source/aircraft/fighter-upres/<id>/`) as its colour key instead.
- **Panel lines and seeds.** Pass 2 engraved panel lines as constant-depth
  grooves. A groove's walls turn out of every part's cone, so a single seed
  claims only the panel it lands in; each part's members are a grid of seeds
  over the object's face toward the view (`groups/hull.py`, `_grid`). Groove
  floors are claimed with their panels and Codex paints them as fine seams.
- **Bent after build.** The pass-3 rework bends wings and folds per vertex
  after detail, so a face that crosses a bend is out of plane. Its fighter
  sets `SHEARED_FACES` in `surface_hull.py`, and part selection then tests
  visibility against the very triangles it samples; along the other diagonal a
  whole folded Longbow arm read as hidden. Planar hulls keep the old test.
- **Landing gear takes swatches.** Legs, runners, wheels and pads are built in
  the kit's steel, bright and dark materials and belong to no part, so they
  take the graded class swatches. Since the gear retracts (2026-10-20,
  `../fighters-geometry/fg_gear.py`), its bay doors and the Talon's skid
  fairings are built in the armour material and take the armour class
  swatch, and the bay liners take the dark one. The hull skins did not move:
  part faces are selected per object, so the new objects change no
  selection, and re-registering the committed generations reproduced every
  registration file. The surfacing build is made with the gear down
  (`build_fighter.py --gear-knob 1`), as the parts were authored; the
  packaged build is saved flying (`package_build.py`).
- **Glass keeps its own material.** The swatch grader scales a coloured
  class's chroma about its source square, and no square is cyan, so a graded
  glass swatch came back neutral grey. Canopies, sensor windows and lamps keep
  the geometry's own glass material, like the Mudskipper's chrome.

## Talon

Livery off `fighter-upres/talon/master-v3.png`: red enamel armour over mid grey
worn steel structure, polished steel rings and lips, near-black recesses and
grilles. Light `service` wear (`wear.json`), heavier soot at the drive drums.

- **Finish reference**: one Codex reskin of the three-quarter camera (the
  reshaped hull on its sled runners), registered at IoU 0.995. Red and steel are measured off it; the steel's
  spread is owned (the measured one is shading across the drums), bright and
  dark are owned (63 and 938 px on the reference, edge-contaminated).
- **Parts**: one group, two sheets, 8 sources and 1 reuse (the starboard wing
  mirrors the port wing; the model is symmetric about Y = 1 there). Fitted
  IoU 0.981-1.000; the red held its family at 99%+ and the stern steel at
  100%. One 763 px dark strip on the belly read 58% in family and was graded
  back to its class. 18-72 texels per unit.
- **Swatches**: one sheet of four squares (red, steel, bright, dark), graded
  within 0.5 dE, 4 hull units (2.2 m) per image.
- 4 Codex generations in all, no retries.

Coverage at 1920x1080 (part skin / swatch / textured), full review set:

| View | Skin | Swatch | Textured |
|---|---|---|---|
| three-quarter | 53% | 45% | 98% |
| top | 66% | 32% | 98% |
| port-side | 31% | 66% | 97% |
| front | 20% | 78% | 98% |
| starboard-quarter | 54% | 44% | 98% |
| port-aft-quarter | 50% | 48% | 98% |
| under-quarter | 53% | 47% | 100% |
| starboard-side | 26% | 71% | 96% |
| gun-detail | 28% | 71% | 100% |
| cockpit-detail | 50% | 37% | 87% |
| aft-detail | 69% | 31% | 100% |
| engine-detail | 27% | 72% | 100% |

The untextured remainder is the canopy glass, which keeps its own material.

## Wasp, Dagger, Longbow, Thunder

The Talon's recipe, per fighter: one finish reference from the three-quarter
camera with the upres master as colour key, one group of two part sheets, one
swatch sheet; 4 Codex generations each, no retries. Mirror reuses wherever the
model is symmetric (arms, cans, pods, prongs, tip fins, flanks).

| Fighter | Livery (master) | Reference IoU | Sources / reuses | Tile IoU | Lowest family share | Min texels/unit |
|---|---|---|---|---|---|---|
| Wasp | grey-blue over dark gunmetal (`master-v2`) | 0.944 | 8 / 5 | 0.970-0.997 | 0.92 | 24.4 (belly) |
| Dagger | pale blue-grey, cream torpedo (`master-v2`) | 0.986 | 8 / 2 | 0.928-0.999 | 0.74 | 16.2 (belly) |
| Longbow | light blue-grey over dark webs (`master-v2`) | 0.994 | 8 / 4 | 0.969-0.998 | 0.94 | 12.0 (belly) |
| Thunder | sand khaki over dark steel (`master-v1`) | 0.991 | 8 / 2 | 0.981-0.993 | 0.84 | 16.3 (belly) |

The Dagger, Longbow and Thunder rows are the pass-3 rework (re-surfaced
2026-10-18); the Wasp's shape did not change. The Dagger's lowest share is the
dark grilles on its drive block (74%); its armour held 93%+. Its two flank
tiles are thin strips Codex drew slightly fuller, fitted at 0.928.

The Wasp's reference registered lower because Codex framed the drone slightly
larger; the measured blue and steel are taken from eroded masks and held.
The Dagger's cream class (the torpedo store) is owned, not measured. The
Longbow's belly is one 29 x 31 unit part (12 texels per unit); split it if a
shot looks up at it closely.

Coverage at 1920x1080 (part skin / swatch / textured): the Wasp from its quick
pass, the others from the full review set on the reshaped hulls (close-ups in
the review run's NOTES.md on Drive).

| View | Wasp | Dagger | Longbow | Thunder |
|---|---|---|---|---|
| three-quarter | 42 / 56 / 98% | 49 / 48 / 97% | 41 / 57 / 98% | 48 / 48 / 96% |
| starboard-quarter | 44 / 54 / 98% | 53 / 44 / 97% | 41 / 56 / 98% | 52 / 44 / 97% |
| port-aft-quarter | 40 / 58 / 98% | 48 / 49 / 97% | 40 / 57 / 98% | 45 / 51 / 97% |
| top | 51 / 47 / 98% | 63 / 34 / 97% | 57 / 41 / 97% | 62 / 35 / 97% |
| under-quarter |  | 66 / 34 / 99% | 42 / 57 / 99% | 65 / 34 / 99% |
| port-side |  | 28 / 68 / 96% | 17 / 79 / 96% | 17 / 78 / 94% |
| starboard-side |  | 39 / 57 / 95% | 14 / 83 / 96% | 23 / 71 / 94% |
| front |  | 24 / 73 / 96% | 14 / 83 / 97% | 28 / 67 / 95% |

The untextured remainder is glass (canopies, lamps, sensor windows), which
keeps its own material.

## Review renders

`review.py` renders every view three times (lit, albedo, part id) in Cycles at
1920x1080: at 48 samples on this workstation's CPU that is about 6 minutes a
fighter. For a quick check pass `--samples 12 --views
three-quarter,starboard-quarter,port-aft-quarter,top,under-quarter` (about
2 minutes); the full set is better rendered on the Mac's GPU.

## Broadsword

The Broadsword's geometry is its own package (`../broadsword-geometry/`), so
its `rebuild.sh` builds it from there, gear up: the closed gear doors are
painted with the belly and the legs stay out of every view. It has no review
props to strip but the parked deck plane. Livery off
`fighter-upres/broadsword/master-v1.png`: yellowed khaki armour plates over
dark gunmetal structure, polished steel barrels, bands and exhaust lips,
near-black grilles and bells. Light `service` wear, soot at the nacelle sterns.

- **Finish reference** registered at IoU 0.970. Khaki and steel are measured
  off it; the steel's spread, bright and dark are owned as on the Thunder.
- **Parts**: one group, two sheets, 8 sources and 4 mirror reuses (nacelle
  top, wing top, fuselage side and wing side; the model is symmetric about
  Y = 0). Plates are their own objects, so each part is the structure and the
  plates on it seen from one side. Fitted IoU 0.969-1.000, khaki in family
  96%+, steel 99%+, no swaps. 14 texels per unit on the belly (one 30 x 28
  unit part), 28-137 elsewhere.
- **Swatches**: one sheet of four squares, 4 hull units (2.0 m) per image.
- 4 Codex generations in all, no retries.

Coverage at 1920x1080 (part skin / swatch / textured): three-quarter 48 / 48 /
96%, top 56 / 40 / 96%, port side 17 / 79 / 96%, front 28 / 69 / 97%,
starboard quarter 49 / 48 / 96%, port-aft quarter 43 / 54 / 97%, under
quarter 56 / 43 / 100%. The rest is the canopy glass.

## Borer

The Shepherd's built-in mining drone (`../fighters-geometry/ft_borer.py`, 0.25 m/unit).
Livery is the Shepherd's own Ko Combine family, not a sprite of its own: burnt orange
armour (the tender's drive-pod orange), light grey machinery (its drum paint), gunmetal
structure, polished steel rings, lips and tines, near-black recesses. `derive_palette`
grades every shared class onto the Shepherd's graded means, so a drone on its cradle
reads as the tender's own; its wear colours are copied from the Shepherd's palette too.
Light `service` wear with more grime than a warship's fighter, soot at the three drives.

- **Renders went through the render queue** (`guides.job.json`, light lane:
  `queue_guides.py` builds, strips and renders the reference guide and every part's
  guide at 16 samples); `rebuild.sh` skips those steps with `BORER_SKIP_RENDERS=1`.
- **Finish reference** registered at IoU 0.993, with the upscaled sprite
  (`reference/sprite-livery.png`) as colour key and the Shepherd's finish reference as
  the material standard.
- **Parts**: one group, two sheets, 8 sources and 4 mirror reuses (pod top, drive top,
  drive side, cheek). Fitted IoU 0.981-1.000, except the spine top at 0.963 (identity
  fit, a few pixels short along its lower edge; chamfer faces take the swatch there).
  Orange in family 99%, grey 100%, no swaps.
- **Swatches**: one sheet of five squares (3x2), 6 hull units (1.5 m) per image.
- 4 Codex generations in all, no retries.
