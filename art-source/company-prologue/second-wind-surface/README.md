# Second Wind: whole-hull surfacing

Status: ACTIVE. Base skin on every face of the hull; source art only, nothing here ships.
Written: 2026-10-16
Updated: 2026-10-16 — rolled out from the port-flank pilot to the whole hull (seven groups, swatches, patched state).

The base-skin package under `second-wind-pipeline.md` (Surfacing). Every base
face of the Second Wind now renders either a **part skin** (a Codex reskin of
that face group, rendered alone and square-on, registered, graded to one
palette and stored on the part keyed by object name) or a **class swatch** (a
graded, periodic Codex material sample of its paint class, box-projected).
The skin goes onto the **clean** build and serves every condition state; the
`patched` damage build is skinned by the same phase.

![port views](review/views-port.png)
![starboard views](review/views-starboard.png)
![coverage](review/coverage.png)

## Where it stands

Share of the ship's on-screen pixels at 1920×1080 (`review/review.json`; the pilot had 13–37% part skin on port views and flat grey elsewhere):

| View | Part skin | Swatch | Textured |
|---|---|---|---|
| port-side | 54% | 45% | 99% |
| port-quarter | 45% | 54% | 100% |
| high-quarter | 40% | 60% | 100% |
| top | 34% | 65% | 99% |
| starboard-side | 52% | 47% | 99% |
| starboard-quarter | 44% | 56% | 100% |
| high-quarter-starboard | 39% | 61% | 100% |
| bow-quarter | 48% | 51% | 100% |
| stern | 5% | 94% | 99% |
| under-quarter | 34% | 66% | 100% |
| port-flank-detail | 58% | 42% | 100% |
| port-aft-detail | 33% | 67% | 100% |
| bow-detail | 59% | 40% | 100% |
| bridge-detail | 34% | 65% | 100% |
| turret-detail | 52% | 47% | 100% |
| starboard-aft-detail | 19% | 81% | 100% |
| stern-detail | 2% | 98% | 100% |
| bay-detail | 75% | 24% | 100% |
| patched port-quarter | 42% | 50% | 92% (rest: scar and repair materials) |
| patched starboard-quarter | 42% | 52% | 94% (rest: scar and repair materials) |
| patched port-flank-detail | 55% | 39% | 94% (rest: scar and repair materials) |
| patched stern-detail | 2% | 92% | 95% (rest: scar and repair materials) |

- **62 parts** (40 generated sources, 22 free reuses: mirrors, identical
  fittings and turrets turned to their own barrel) cover the large armour,
  the bow, decks and spine, keel, turrets, grapple arm and the open bay.
  815 base faces carry a part skin; the other 9,062 base-material faces carry
  a class swatch, so no base face is flat colour any more.
- **11 Codex generations in all:** the pilot's four, one sheet per rollout
  group (six), and one swatch sheet. No retry was needed. Every tile registered
  at fitted IoU 0.954–0.999 (the pilot's pod bands 0.919), held its declared
  paint family on 86–100% of every region, and raised no colour-layout swap.
- **Tone:** before grading, part means of one class differed by up to dE 15.6
  (gunmetal, 26 parts) and 20.6 (dark); after grading they sit on the palette
  (cream 0.6, the rest 0), and the owned variation table then offsets each
  part on purpose.
- **Texel density:** every source is at least 15 texels per hull unit except
  three single-polygon faces whose extent fixes it: the keel plate (10.1), the
  forward deck (13.1) and the deck-edge lip (14.9).

## The pilot's three changes, applied

1. **Oblique faces no longer streak.** A part's material blends to its class
   swatch on faces turned more than about 30° from the part's view direction
   (`parts.SQUARE` 0.86, fully swatch by 45°), computed per pixel from the true
   normal. Chamfers and the bevel modifier's strips therefore take a graded
   swatch rather than a stretched projection. A part can move its own
   threshold (`square`), as the turret tops do to keep the gun-house glacis
   on the top art. Unclaimed faces next to a part continue that part's
   projection in the UV layer, so a bevel strip between them interpolates
   sensible coordinates rather than running to the tile corner.
2. **Variety is owned.** `variation.json` holds one CIELAB offset per part
   (unit values scaled by `variation.SPAN` per class: about ±4 L on paint,
   ±2–3.5 L on metal, small hue drift on paint only). `apply_skin.py` applies
   it as a linear-light gain in the part's material, so two parts sharing a
   generation (mirrors, turret reuses) still differ. `variation.py` only adds
   rows for new parts, from a hash of the id; edit any row by hand.
3. **The 3 px chipped-edge trim stays** (`grade.EDGE_TRIM`).

## Groups

The part table is split by group so one session per group could author in
parallel without touching another's rows: `groups/<group>.py` defines PARTS
and SHEETS, `parts.py` aggregates them, and every script takes `--group`.
Sheet names start with the group; scratch output goes to `build/<group>/`.

| Group | Sources / reuses | What | Notes |
|---|---|---|---|
| `port` | 16 / 2 | the pilot: waist, forebody modules, bow flank and cheek, belt, lip, port pod casing, sponson boxes | unchanged textures |
| `starboard` | 4 / 10 | mirrors of every port part that mirrors exactly (`mirror_u`, flipped-mask IoU 0.997–1.000), plus own tiles for the sealed bay door, the starboard belt plates and the bow flank/belt | the port bow parts also take the centreline faces (ties go to port), so their starboard twins frame differently and are not mirrors |
| `bow` | 4 / 1 | nose block rake, front, crown and sides | the shield glacis and stem stay with the pilot's port parts |
| `decks` | 4 / 1 | forward deck, the whole spine top (11 objects, 4 classes), spine sides (port, starboard mirrored), keel plate | the waist deck is one 51×61-unit face group (8.5 t/u), left to swatch |
| `turrets` | 4 / 8 | small-turret top and side, turret 4 top and side | turrets 1–3 are one design at three yaws: the reuse camera turns with the barrel; far sides are mirrors. Multi-material class maps (gunmetal house, dark bands and traverse gap), graded per region |
| `arm` | 4 / 0 | boom and forearm tops and sides, slew casing and winch crown | new `pale` class (derived: cream lightness, gunmetal chroma) |
| `bay` | 4 / 0 | back wall, frame (coaming, jambs, buttresses), floor, davit head | `ray` starts visibility rays inside the opening |

Swatches (`swatches.py`): one Codex sheet of four flat squares (cream, oxide,
gunmetal, dark) on the fixed finish reference; each square is cropped,
de-lit, graded to its palette class (within 0.8 dE), and made truly periodic
by a half-roll cross-fade (seam step / interior step 0.94–1.38, from 5–7);
`pale` is the gunmetal square regraded. Box-projected in object space at
`parts.SWATCH_UNITS` = 8 hull units (5.2 m) per image, blend 0.3,
`REPEAT` (mirror extension showed mirrored rust motifs about object origins).
They carry the pod ends, nozzles, engine cans, heat-sink fins, small
fittings, recesses and every chamfer.

## Pipeline

`./rebuild.sh` replays every step except Codex, for both builds.

1. **Builds.** `../second-wind-geometry/build_reference.py -- --out build/geom`
   (clean) and `-- --state patched --out build/patched-geom`. Imported, never
   edited.
2. **Parts by geometry** (`parts.py`, `groups/`, `render_parts.py`). A part is
   one or more (object, seed) members; its faces are the connected group,
   grown from the seed, of faces inside its cone about its view direction that
   the view sees first. Boundaries are creases, object edges or occlusion
   edges, never boxes. Overlaps go to the squarer view, ties to the group
   listed earlier in `parts.GROUPS`. Selection always runs over every group,
   so a group's guides match the integrated build (checked: re-selecting all
   62 parts together left every guide mask identical). Output
   `part-list/<group>.json` and `guide/`.
3. **Sheets** (`sheets.py`): 2×2 tiles, one Codex call per sheet. `skin_from`
   reuses a generation; `mirror_u` flips it for a mirror-image twin.
4. **Codex** (`tools/codex_imagegen.py`): the sheet guide (layout authority)
   and `reference/finish-reference.png` (material only) on every request.
   Prompts in `prompts/`, masters and provenance in `generated/`.
5. **Register** (`register.py`): silhouette fits per tile;
   `registration/<part>.json`, `summary-<group>.json`.
6. **Palette** (`palette.py`): per class off the finish reference; `pale` is
   derived.
7. **Check, de-light, grade** (`grade.py`): swap check per declared region;
   one robust linear light ramp per plane, fitted with **one intercept per
   material class** on the plane (a two-material plane's colour split is not
   light: the bow rake's cream/oxide split had been removed as an 18 L ramp,
   now 0.7); grade per region; 3 px trim and 24 px pad. `textures/<part>.png`,
   `grading/`. Swatches: `swatches.py`.
8. **Apply** (`apply_skin.py`). One UV layer per object, `skin` (Blender
   allows eight per mesh and the main hull alone carries more parts than
   that; every face belongs to one part). Materials `Skin <part> | <base>`
   (texture × variation gain, blended to the swatch on oblique faces) and
   `Swatch <class> | <base>` on every other base-material face. `--fallback
   neutral` restores flat grey for an honest part-only coverage read.
9. **Review** (`review.py`, `compose_review.py`): lit beauty, rendered albedo
   and an id pass (each part its own colour, swatch blue) at 1920×1080 over
   ten whole-ship views and eight close-ups; coverage and seams to
   `review/review.json`.

## Condition states

The skin belongs to the clean build and is generated once. It keys off base
object names and base-mesh face indices, which `sw_damage` never changes
(the patched build's 656 base meshes have identical topology; damage cuts by
modifier, shape-keys, hides or overrides).

- `build_reference.py -- --state patched`, then `apply_skin.py -- --blend
  build/patched-geom/second-wind-geometry.blend --out build/skinned-patched.blend`.
  Craters cut through skinned faces (surviving faces keep their UVs and skin;
  cut faces take the cutter's material); `Scar ...` objects keep their own
  damage and repair materials.
- **`scar_override` is respected.** A slot sw_damage links at object level
  keeps its override: those faces get neither skin nor swatch. On `patched`
  that is turrets 1 and 3's primer gun houses (44 faces), so the patched build
  skins 771 faces to the clean build's 815. `scar_hidden` objects stay hidden.
- Repair plates, patches and welds are their own objects with their own
  materials, never this skin. The class swatches are free for the damage
  package to use on them.

![patched](review/patched-views.png)

## Wear: what the state-driven mask layer still needs

Unchanged from the pilot: hull-space coordinates so soot and streaks run
across part boundaries; engine anchors, scar ids, cavity/AO and gravity as
inputs; per-state grime, soot, scorch, fade and chip density; and a
paint-over channel for `restored`. Per-part variation is now in place
(above); the mask layer adds on top of it.

## Known problems

- **Visibility ignores other objects.** Selection tests occlusion only within
  a part's own objects, so a part can claim faces another object hides (the
  bay frame behind the hull wall, pivot discs under the boom). Harmless for
  appearance, but it wastes tile area; casting against the whole scene is the
  fix.
- **Single-polygon faces cap density** (keel, forward deck, lip above). The
  waist deck is left to swatch for the same reason.
- **Swatch repetition.** Star-shaped chipped fasteners in the swatches repeat
  every 5.2 m on large unclaimed faces; the swatch source is soft (about
  380 px per square upscaled to 1024).
- **Next art if wanted:** the turret mount platforms (one face reused 16×)
  and the waist deck split into smaller groups.
- **Seam outliers.** The bow nose block's rake and side tiles step 24–33 dE at
  their crease (a visible but plausible plate change), and the multi-material
  spine top reports large tone steps against its sides because its boundary
  runs along oxide trim (`review/seams.png`).
- **The bay reads dark** under the build's lamps: the back wall's panelling
  shows in `bay-detail` but only faintly from `port-side`. Lighting is the
  cinematic's, not this package's.

## Files

| Path | What |
|---|---|
| `parts.py`, `parts_common.py`, `groups/<group>.py` | part definitions per group, sheets, selection, projection |
| `part-list/<group>.json` | every part: objects, face indices, extent, tile camera, texels per unit |
| `render_parts.py`, `sheets.py` | guides and sheets |
| `render_reference_classes.py`, `palette.py`, `palette.json` | the shared palette |
| `variation.py`, `variation.json` | the owned per-part tone offsets |
| `prompts/`, `generated/` | exact briefs; untouched Codex masters with provenance |
| `reference/` | the fixed finish reference and its registration |
| `register.py`, `registration/` | silhouette fits per part |
| `grade.py`, `grading/`, `textures/` | swap check, de-light, grade; part images |
| `swatches.py`, `textures/swatch-*.png`, `grading/swatches.json` | the class swatches |
| `apply_skin.py`, `skin-manifest.json` | the surfacing phase (clean build manifest) |
| `review.py`, `compose_review.py`, `review/` | renders, coverage and seams, sheets (1920×1080) |
| `imgops.py` | shared numpy/Pillow helpers |
| `build/` (ignored) | builds, tiles, `skinned.blend`, `skinned-patched.blend`, renders |
