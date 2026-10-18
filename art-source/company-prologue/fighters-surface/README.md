# Fighters: surfacing (pass 6)

Status: ACTIVE — all five fighters surfaced (service livery), waiting on owner review.
Written: 2026-10-18

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
- **Glass keeps its own material.** The swatch grader scales a coloured
  class's chroma about its source square, and no square is cyan, so a graded
  glass swatch came back neutral grey. Canopies, sensor windows and lamps keep
  the geometry's own glass material, like the Mudskipper's chrome.

## Talon

Livery off `fighter-upres/talon/master-v3.png`: red enamel armour over mid grey
worn steel structure, polished steel rings and lips, near-black recesses and
grilles. Light `service` wear (`wear.json`), heavier soot at the drive drums.

- **Finish reference**: one Codex reskin of the three-quarter camera,
  registered at IoU 0.993. Red and steel are measured off it; the steel's
  spread is owned (the measured one is shading across the drums), bright and
  dark are owned (63 and 938 px on the reference, edge-contaminated).
- **Parts**: one group, two sheets, 8 sources and 1 reuse (the starboard wing
  mirrors the port wing; the model is symmetric about Y = 1 there). Fitted
  IoU 0.986-1.000; every declared region held its family (red 99%+, the stern
  steel 100% neutral). 18-75 texels per unit.
- **Swatches**: one sheet of four squares (red, steel, bright, dark), graded
  within 0.6 dE, 4 hull units (2.2 m) per image.
- 4 Codex generations in all, no retries.

Coverage at 1920x1080 (part skin / swatch / textured):

| View | Skin | Swatch | Textured |
|---|---|---|---|
| three-quarter | 58% | 40% | 98% |
| top | 69% | 29% | 98% |
| port-side | 31% | 66% | 97% |
| front | 15% | 83% | 98% |
| starboard-quarter | 58% | 40% | 98% |
| port-aft-quarter | 53% | 45% | 98% |
| under-quarter | 59% | 41% | 100% |
| starboard-side | 24% | 72% | 96% |
| gun-detail | 30% | 70% | 100% |
| cockpit-detail | 50% | 36% | 87% |
| aft-detail | 72% | 28% | 100% |
| engine-detail | 30% | 70% | 100% |

The untextured remainder is the canopy glass, which keeps its own material.

## Wasp, Dagger, Longbow, Thunder

The Talon's recipe, per fighter: one finish reference from the three-quarter
camera with the upres master as colour key, one group of two part sheets, one
swatch sheet; 4 Codex generations each, no retries. Mirror reuses wherever the
model is symmetric (arms, cans, pods, prongs, tip fins, flanks).

| Fighter | Livery (master) | Reference IoU | Sources / reuses | Tile IoU | Lowest family share | Min texels/unit |
|---|---|---|---|---|---|---|
| Wasp | grey-blue over dark gunmetal (`master-v2`) | 0.944 | 8 / 5 | 0.970-0.997 | 0.92 | 24.4 (belly) |
| Dagger | pale blue-grey, cream torpedo (`master-v2`) | 0.987 | 8 / 2 | 0.956-0.999 | 0.82 | 16.2 (belly) |
| Longbow | light blue-grey over dark webs (`master-v2`) | 0.987 | 8 / 4 | 0.959-0.999 | 0.95 | 12.2 (belly) |
| Thunder | sand khaki over dark steel (`master-v1`) | 0.984 | 8 / 2 | 0.980-0.997 | 0.89 | 16.3 (belly) |

The Wasp's reference registered lower because Codex framed the drone slightly
larger; the measured blue and steel are taken from eroded masks and held.
The Dagger's cream class (the torpedo store) is owned, not measured. The
Longbow's belly is one 29 x 31 unit part (12 texels per unit); split it if a
shot looks up at it closely.

Coverage at 1920x1080 (part skin / swatch / textured), quick review pass:

| View | Wasp | Dagger | Longbow | Thunder |
|---|---|---|---|---|
| three-quarter | 42 / 56 / 98% | 51 / 46 / 97% | 40 / 57 / 98% | 49 / 47 / 96% |
| starboard-quarter | 44 / 54 / 98% | 53 / 44 / 97% | 42 / 55 / 98% | 53 / 44 / 97% |
| port-aft-quarter | 40 / 58 / 98% | 49 / 48 / 97% | 40 / 57 / 98% | 47 / 50 / 97% |
| top | 51 / 47 / 98% | 64 / 33 / 97% | 57 / 41 / 97% | 62 / 35 / 97% |

The untextured remainder is glass (canopies, lamps, sensor windows), which
keeps its own material.

## Review renders

`review.py` renders every view three times (lit, albedo, part id) in Cycles at
1920x1080: at 48 samples on this workstation's CPU that is about 6 minutes a
fighter. For a quick check pass `--samples 12 --views
three-quarter,starboard-quarter,port-aft-quarter,top,under-quarter` (about
2 minutes); the full set is better rendered on the Mac's GPU.
