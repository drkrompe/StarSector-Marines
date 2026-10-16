# Second Wind: port-flank surfacing pilot

Status: PILOT, first real surfacing package. Source art only; nothing here ships.
Written: 2026-10-16

The first base-skin package under `second-wind-pipeline.md` (Surfacing). It
covers the port flank's major armour: the waist wall, the forebody armour
modules, the bow flank and cheek, the bow belt plate, the deck-edge lip, the
port engine pod's cowl, lower casing, intake cap, bands and thrust housing,
and the grapple sponson boxes. Each part is a Codex reskin, graded to one
palette and stored as its own UV layer and image keyed by object name. The
skin goes onto the **clean** build and serves every condition state.

![views](review/views.png)
![seams](review/seams.png)
![process](review/process.png)

## Verdict

**Good enough to roll out to the whole hull, with three changes.**

- **Registration is solved.** Sixteen tiles from four generations all fit
  within 1.2% scale. Fitted IoU is 0.986–0.999, except the thin lip (0.954)
  and the bands (0.919, identity fit). The outline lies within 1–3 px (p95)
  after the fit. The guides are flat faces, so the panel layout is Codex's own
  invention and there is nothing inside to drift.
- **Tone drift is solved by grading.** Before grading, parts of one material
  differed by up to dE 5.7 (oxide) and 13.4 (gunmetal: mean L 33 to 46,
  because the fittings sheet came back much darker). After grading they share
  the palette mean by construction. The independent check is the render: on
  the rendered albedo, the tone step across every same-material part boundary
  is 0.4–1.7 L.
- **No colour-layout swaps.** Every part kept its declared paint family on
  94–100% of its pixels. Oxide parts came back under 4% grey. As a control,
  the spike's repainted turret tile, read as all gunmetal, holds only 58%
  neutral (42% cream and oxide). That is under the 60% threshold, so it would
  be flagged, but narrowly: the turret is a multi-material part and needs its
  own class map. Every part here is single-material. A flagged region is
  graded back to its declared class.
- **Part boundaries read as plate lines, not seams.** The colour step across a
  same-material boundary is 6.7–12.8 dE, against 4.9–5.6 for the texture's
  own variation at the same spacing. The pod cowl's side/roof crease is the
  outlier at 22 dE: both tiles draw a plate seam along that edge.

Before rolling out, make these changes:

1. **Oblique faces stretch.** A part takes every face within its cone
   (cos ≥ 0.6, so up to 1.67× stretch). The chamfers and the strips the bevel
   modifier adds are projected from the part's square-on camera, so they show
   streaks (the sponson cowl corners in `review/closeup-port-flank-detail.png`).
   Give chamfer runs their own part with its own direction, or put graded
   swatches on them.
2. **Grading flattens plate-to-plate variety.** Every part now carries
   exactly the palette mean. The wear/variation layer should add deliberate
   per-plate offsets from a table we own, rather than let generations
   disagree by accident.
3. **Codex draws a light chipped-edge line round every shape.** The grade
   refills a 3 px band inside each outline from the interior. Without that,
   padding smeared the line onto bevel strips as white stripes (fixed here).
   Keep the trim.

**Cost.** Four generations covered 16 source parts and 2 reuses (0.25
generations per part). Each took 42–62 s of wall clock (about 45k Codex input
tokens). All four ran in parallel, so they finished in about 65 s. Everything
else replays in about 5 minutes without Codex (`./rebuild.sh`):

| Step | Time |
|---|---|
| Clean build | 20 s |
| Part selection and guides | 70 s |
| Registration | 35 s |
| Grade | 60 s |
| Apply | 15 s |
| Five 1920×1080 reviews with passes | 90 s |

Authoring cost per part is one row in `parts.py`: object, seed point, view
direction and cone, then a look at its guide.

Coverage of the ship's on-screen pixels (port flank only; everything else is
flat dark grey): port side 37%, port quarter 20%, high quarter 13%, the
flank and aft close-ups 31% and 22%. The whole hull at this density is
roughly 80–100 parts, or 20–25 generations.

## Pipeline

`./rebuild.sh` replays every step except Codex.

1. **Clean build.** `../second-wind-geometry/build_reference.py -- --out build/geom`.
   It is imported, never edited. `sw_*.py`, `build_reference.py` and the
   damage states belong to the geometry package.
2. **Parts by geometry** (`parts.py`, `render_parts.py`). A part is one or
   more (object, seed) members. Its faces are the connected face group, grown
   from the seed face, of faces whose normal lies within the part's cone about
   its view direction and which that view sees first. Visibility is sampled per
   triangle, because an L-shaped n-gon's centre falls in its own hole.
   Connectivity includes boolean T-junctions. So every boundary is a crease,
   an object edge or an occlusion edge, never a box. A face that two parts
   both qualify for goes to the part that sees it more squarely. Selection
   runs on the base meshes; modifiers stay live. Output is `part-list.json`,
   with face indices per object, area, extent and texels per unit, plus
   `guide/<part>-guide|mask|class.png`.
3. **Sheets** (`sheets.py`). Four 2×2 sheets of similar-sized parts. Each tile
   is framed to its part with a 7% margin. Every part has at least 15 native
   texels per model unit (lip 14.9, cheek 16.2, bands 17.4, the rest 21–60).
   Two near-identical parts reuse another part's generation through
   `skin_from`: the forward sponson lower body and cowl take the aft ones'.
4. **Codex** (`tools/codex_imagegen.py`). There is one call per sheet with
   two references: the sheet guide (layout authority) and the fixed finish
   reference `reference/finish-reference.png` (the spike's port-side reskin,
   material only). Prompts are in `prompts/`; masters and provenance are in
   `generated/`. The prompts forbid repair patches, colour changes and any
   concentrated wear (soot, scorch, stains, impacts), because those belong to
   the scar and wear layers.
5. **Register** (`register.py`, `imgops.py`). Per tile: segmentation
   (enclosed holes such as the bay opening count as background), then
   identity, bounds-affine and IoU-refined fits, then boundary distance.
   Output is `registration/<part>.json` and `summary.json`.
6. **Palette** (`palette.py`). The finish reference is registered to the
   port-side view by its measured fit and sampled per material class off the
   plates the clean geometry says carry that class (eroded 3 px). The result
   is `palette.json`, with CIELAB mean and spread for cream, oxide, gunmetal
   and dark.
7. **Check, de-light, grade** (`grade.py`):
   - **Swap check:** the share of each declared region that came back in its
     paint family.
   - **De-light:** a robust linear L ramp is removed per plane (planes come
     from the flat normal pass). Spans removed were 2.6–11.0 L, worst on the
     pod cowl side (11.0) and the sponson roofs (10.1).
   - **Grade per declared region:** L is matched, with its spread clamped to
     0.75–1.33×. Chroma is scaled about neutral on oxide and cream, because
     shifting it pushed bare-metal chips blue, and shifted on gunmetal and
     dark.
   - **Pad:** colour is pushed 24 px out from 3 px inside the outline.
   - Output is `textures/<part>.png` and `grading/*.json`.
8. **Apply** (`apply_skin.py`). This is the surfacing phase on the clean
   build. Per part it re-selects the same faces and adds UV layer
   `skin:<part>` (the ortho projection into its tile). It assigns
   `Skin <part> | <base material>`: the base material's metal and roughness,
   with base colour from the part image through that layer, packed into the
   .blend. Faces no part claims get the `neutral` fallback (flat dark grey),
   so coverage is honest; `--fallback base` keeps the build's flat colours.
   The phase writes `build/skinned.blend` and `skin-manifest.json`.
9. **Review** (`review.py`, `compose_review.py`). Lit beauty, albedo and
   part-id passes at 1920×1080 from port-side, port-quarter, high-quarter
   (45° round, 50° up) and the geometry's `port-flank-detail` and
   `port-aft-detail` close-ups. Coverage and seams go to `review/review.json`;
   sheets go to `review/`.

## Condition states

The skin belongs to the clean build and is generated once.

- Names, base faces and other UV layers are untouched. A damage phase
  (sw_damage.py's live Booleans) cuts skinned faces like any other: the
  surviving faces keep their `skin:<part>` UVs and material, and the faces
  the cut opens take the cutter's damage material.
- Checked on the `patched` state: `build_reference.py -- --state patched`,
  then `apply_skin.py -- --blend <that build> --out ...`. It skins the same
  114 faces. Craters cut through the skinned belt, and the salvage plates
  keep their own materials (`review/patched-port-flank-detail.png`). The
  skin phase does not wire up states itself; the geometry package owns the
  `--state` build.
- Repair plates, patches and welds are their own objects with their own
  materials, never this skin.

## Wear: what the state-driven mask layer will need

The skin carries only Codex's light, even chipping and grime, which the
prompts asked for. That is the floor of every state, because a mask can add
wear but cannot remove baked chips. The layer needs:

- **Its own coordinates.** Either the `skin:<part>` UVs (one mask per part
  image) or a hull-space projection. Soot and streaks must run continuously
  across part boundaries, which argues for hull space, sampled in the shader.
- **Inputs we place, keyed to objects:**
  - engine anchors (`Engine {i} ...`) for drive soot;
  - scar ids from sw_damage for scorch halos;
  - cavity and AO for grime in seams;
  - gravity direction for streaks.
- **State parameters:** grime, soot, scorch, paint fade, chip density.
- **A paint-over channel.** `restored` (repainted, scars visible) needs to
  blend the skin toward the flat palette colour, which hides the baked chips.
  "More wear" alone cannot produce it.
- **Per-plate variation offsets** from a table we own, to restore the variety
  grading removed (Verdict, change 2).

## Measured problems, for the rollout

- **Segmentation and the boundary metric.** Edge points subsampled by row
  (`[::step]`) dropped an entire side of every shape and reported 160 px
  boundary errors on perfect fits. `imgops.boundary_distance` uses every
  edge pixel. The spike's `measure_drift.py` has the same `[::3]`
  subsampling; its IoU figures stand, but its boundary percentiles may be
  inflated.
- **Fallback grey and view transform.** The build uses AgX. The flat passes
  switch to Standard and encode part ids in display values; see
  `review.part_colour`.
- **Not covered:** the bow glacis and nose block (centreline faces), the
  turrets, the grapple arm, the bay interior, pod ends and nozzles, and every
  starboard face. These are the rollout's remaining parts and swatch
  surfaces.

## Files

| Path | What |
|---|---|
| `parts.py` | part definitions, sheets, face selection, projection |
| `part-list.json` | every part: objects, face indices, area, extent, tile camera, texels per unit |
| `render_parts.py`, `sheets.py` | guides and sheets |
| `render_reference_classes.py`, `palette.py`, `palette.json` | the shared palette from the finish reference |
| `prompts/`, `generated/` | exact briefs; untouched Codex masters with provenance |
| `reference/` | the fixed finish reference and its registration (from the spike) |
| `register.py`, `registration/` | silhouette fits per part |
| `grade.py`, `grading/`, `textures/` | swap check, de-light, grade; the final part images |
| `apply_skin.py`, `skin-manifest.json` | the surfacing phase on the clean build |
| `review/patched-port-flank-detail.png` | the same skin applied to the `patched` damage build |
| `review.py`, `compose_review.py`, `review/` | renders, coverage and seams, sheets (1920×1080) |
| `imgops.py` | shared numpy/Pillow helpers |
| `build/` (ignored) | geometry build, tiles, passes, `skinned.blend` |
