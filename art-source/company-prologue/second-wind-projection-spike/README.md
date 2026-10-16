# Second Wind: Codex reskin → mesh projection spike

Status: SPIKE, findings for a go/no-go on surfacing. Nothing here ships or
changes geometry, campaign art or gameplay.
Written: 2026-10-16
Updated: 2026-10-16 — added the part-by-part arm the owner suggested.

**Question.** Can Codex CLI reskin a render of the clean Second Wind model well
enough that the result can be projected back onto the mesh as texture? A
second arm asks whether doing it **part by part** works better: square-on
renders of single parts with large flat faces.

**Verdict.** Yes, and registration is the easy part either way.

- **Whole-ship view.** One port-side reskin kept every feature. Once a global
  scale was removed it registered within 1–2 px (silhouette IoU 0.994), and
  projected back it reads as a properly surfaced hull at its own angle. But
  one view textures only what it faces (55% of the visible hull at 30°, 27%
  at 60°), lighting and grime arrive baked into the RGB, and two views agree
  on paint layout but not on individual chips and rivets.
- **Part by part.** One sheet of four parts (deck, turret, engine pod, bow)
  came back with no global drift at all (every tile within 1.1% scale,
  interior within 2 px). It reached faces no whole-ship view sees, the deck
  tops and the turret roof. Parts are rendered alone, so nothing occludes
  them, and their seams fall on real part edges rather than across a plate.
  Its two costs were measured too: tone drifts between generations (the parts
  sheet is 6–14/255 brighter than the whole-ship reskins, and the turret was
  repainted from grey to orange and cream), and sharing one generation between
  four parts halves resolution for the big ones.

Scale **part by part** for the major armour parts, ahead of more whole-ship
views (see Recommendation):

- **Whole-ship projection** only for faces a story camera holds near one angle.
- **Codex swatches** with the full-surface material pipeline for small parts
  and anything unseen.

![review sheet](review-sheet.png)
![scaling sheet](review-sheet-scaling.png)
![parts sheet](review-sheet-parts.png)

`review-sheet.png` (1920×1080) compares guide / generated / registered, then
the projection from the guide angle, 30° and 60°. `review-sheet-scaling.png`
covers the second projector, the two-view blend, where the views disagree,
and the swatch sheet. `review-sheet-parts.png` covers the part-by-part arm:
the guide and generated part sheets, the coverage gained from above, and
where projectors hand over.

## Driving Codex image generation from the CLI

- **Since this spike, `tools/codex_imagegen.py` and the `codex-imagegen`
  skill on main are the maintained way to do this.** They feed the prompt on
  stdin, run in an empty read-only scratch directory, find the image by thread
  id and write provenance. `run_codex.sh` here is the spike's own record of
  how the five images were made.
- `codex exec` (0.159) has the built-in `image_gen` tool on by default
  (`codex features list` → `image_generation stable true`). No flag and no
  `OPENAI_API_KEY` are needed: it runs on the signed-in Codex account (the
  imagegen skill's API fallback is the only path that needs a key).
  `codex exec` reports agent tokens but no money or quota figure for the
  image itself.
- `-i <file>` attaches images. The agent passes the paths to the tool as
  `referenced_image_paths`, which is how an edit/reference request is made.
  The tool also takes `transparent_background` (unused here).
- **Output lands in `~/.codex/generated_images/<thread-id>/exec-<id>.png`.**
  The tool takes no destination argument, so `run_codex.sh` tells the agent to
  copy the file to a named path and print the source on a `SOURCE:` line.
- **Codex rewrites prompts unless told not to.** In the minimal test it
  paraphrased "a flat grey wrench" into its own prompt. `run_codex.sh` wraps
  the prompt file in markers with "pass VERBATIM, call EXACTLY ONCE, do not
  retry". The session log (`~/.codex/sessions/…/rollout-*.jsonl`, the
  `image_gen.generation` item's `revisedPrompt`) confirms that it then passed
  the prompt unchanged. Check that log, not the console, which does not print
  the tool call.
- Run from inside the repo, the agent also read the imagegen skill and
  `CLAUDE.md` (about 20k tokens of context). The wrapper now forbids reading
  any other file.
- Canvas sizes: 16:9 requests came back 1672×941, square ones 1254×1254. The
  aspect matches a 1920×1080 guide to 0.05%, so normalised coordinates carry
  over directly.

| Generation | Wall time | Agent tokens | Result |
|---|---|---|---|
| minimal test (wrench icon) | 46 s | 13.2k | worked; showed prompt paraphrasing |
| `reskin-v1` port side | 70 s (tool itself 28.6 s) | 29.7k (read skill + CLAUDE.md) | kept features; hull scaled ×1.119 |
| `reskin-quarter30` | 57 s | 23.2k | kept features; not scaled |
| `swatches` 2×2 sheet | 77 s | 10.7k | four believable tiles; seams hidden on plate joints |
| `parts-sheet` 2×2 parts | 68 s | 14.4k | no global drift; turret recoloured |

Five generations in all, one of them a 46-second throwaway test. Expect about
a minute per image, sequential.

## Pipeline (all reproducible: `./rebuild.sh`, about 6 minutes, Codex not re-run)

1. `../second-wind-geometry/build_reference.py -- --out build/geom` builds the
   clean model. The geometry modules are run, never edited.
2. `render_guide.py -- --view NAME`: applies modifiers to a copy
   (`build/base.blend`) and renders the view with no lamps under a uniform
   white world (even, shadowless light that still reads the form). It writes
   `guide/<view>-guide.png` on flat grey, an antialiased `-mask.png`, a
   `-depth.png` preview and an unfiltered float view-Z
   `build/depth-<view>.exr`. Cameras live in `views.py`: `port-side` is
   build_reference's own; `quarter30` and `quarter60` orbit the same target at
   30°/60° toward the bow and 12°/24° up (`high45`, 45° and 50° up, is a
   review view only). Part views (`part-*`) render one selection of objects
   alone, square-on along one direction, at 912×513. `parts_sheet.py compose`
   lays four of them out as one 1920×1080 sheet with gutters, and
   `parts_sheet.py split` cuts the generated sheet back into tiles.
3. `run_codex.sh prompts/X.prompt.txt generated/X.png <guide> [<reference>]`.
4. `measure_drift.py generated/X.png --view NAME` segments the hull from the
   background (self-check on the guide: IoU 0.995). It then fits identity,
   moments similarity, bounds affine and an IoU-refined axis-aligned affine,
   and measures interior drift by patch phase correlation of gradient
   magnitude. Output: `registration/X.json`.
5. `project.py -- --tag T --proj VIEW:IMAGE:REG [...]` stores, per projector,
   guide UVs, guide depth and facing cosine on the mesh corners it may paint
   (a part projector only on its own objects inside its region), so the data
   is object-attached and travels with the hull. Registration is applied in
   the shader, which keeps one UV layer per projector within Blender's limit
   of eight. The shader then tests visibility **per pixel** against the depth
   map, like a shadow map, and blends projectors by `(cos − 0.25)²`. It
   renders with a key sun the guides never had, then measures from flat
   per-projector passes: coverage, texel stretch, seams at hand-overs between
   projectors, and pairwise disagreement.
6. `swatch_check.py`, then `compose_review.py`.

## Findings

### Drift and registration

| | port side (`reskin-v1`) | 30° quarter (`reskin-quarter30`) |
|---|---|---|
| raw silhouette IoU | 0.792 | 0.995 |
| bounds offset (L/T/R/B px) | −88 / −22 / +88 / +36 | −1 / −1 / +1 / +1 |
| bounds scale x / y | 1.119 / 1.117 | 1.001 / 1.004 |
| best fit | axis-aligned affine | (same, near identity) |
| IoU after fit | 0.994 | 0.997 |
| boundary distance after fit, mean / p95 | 1.9 / 3.2 px | 1.0 / 2.2 px |
| interior drift after fit, median / p90 / max | 1.0 / 2.0 / 2.2 px | 1.0 / 1.4 / 3.6 px |
| interior drift with no fit, median | 17 px (all patches > 12 px) | 0 px |

- The port-side image was **uniformly enlarged** about the canvas centre,
  despite an explicit "same pixels" instruction. That repeats what the landing
  refinement found. The quarter view, made with the same instruction, was not
  enlarged. **Drift varies per image, so measure every image**, never assume
  a constant.
- Once the global scale is removed, *interior* features did not move. Every
  confident patch sits within 4 px, at 0.069 model units per pixel. Rotation
  and shear were not needed. No feature was added, removed or relocated, and
  all four turrets, the bay, the hatches and the grapple stayed put.
- So registration is **solved by a silhouette fit**; landmarks were not
  needed. The 54 px maximum boundary error is one small see-through gap under
  the folded grapple arm (guide pixel 353, 351), which Codex painted shut.
  Projected, that gap only shows the hull behind it, so it costs nothing here.
  It is the one place Codex changed the shape.

### Coverage

Share of each camera's visible hull that receives projected texture:

| projectors | guide angle | 30° | 60° |
|---|---|---|---|
| port side only | 97% | 55% | 27% |
| port side + 30° | 98% | 86% | 59% |

- The 3% the guide angle misses are faces within about 75° of edge-on
  (cos < 0.25). These are deck tops seen almost flat, and they would smear if
  forced.
- At 60° the whole bow glacis, the deck and the far engine pod are unseen.
  The fallback is honest grey (magenta in sheet cell 6). Moving the camera
  only redistributes what a near-side projector leaves out.
- **Use per-pixel visibility, not per-face.** A first per-face version
  (covered if half its samples were visible) left 23% of the guide angle
  bare: large faces partly behind a pipe, and whole bevel strips.
- **Do not trust this model's face winding.** 1,619 faces whose normal points
  away from the camera are still the frontmost surface. The facing test uses
  |cos|, and the depth test rejects anything hidden.

### Cross-view consistency (the real scaling risk)

The 30° reskin was given the port-side reskin as its finish reference. Where
both projectors see the same surface (71% of the visible hull at the guide
angle):

- **Paint layout and tone agree.** The mean luminance offset is −0.9/255, and
  cream, orange and grey plates sit on the same plates.
- **Fine detail does not.** The mean per-pixel difference is 16/255 and the
  p90 is 47/255. The heat map (sheet cell 10) is dark on large flats and
  bright along plate edges, seams and the chip/rivet noise. Each image invents
  its own chips; each also draws edges a pixel or two differently.
- Blending therefore softens the crossfade zones. That is acceptable for
  broad paint, and wrong for a weld or scar that has to read identically from
  two cameras. **Best-view-per-face with hard switching would show the
  disagreement as visible seams instead of blur.**

### Part by part (the owner's suggestion)

Four parts were rendered alone and square-on, laid out as one sheet, and
reskinned in **one** generation with the port-side reskin as the finish to
match:

- the forward deck (main hull faces, from above);
- turret 1, from above;
- the port engine-pod assembly, from port;
- the bow armour, from 45° to bow and port.

Each tile was registered on its own and projected only onto its own part.
Measured against the whole-ship views:

| | whole-ship port side | part tiles (deck / turret / pod / bow) |
|---|---|---|
| raw silhouette IoU | 0.792 | 0.998 / 0.992 / 0.994 / 0.995 |
| global scale | ×1.119 | within 1.1% on every tile |
| interior drift after fit, p90 | 2.0 px | – / 1.0 / 1.4 / 2.0 px |
| generated texels per model unit | 12.7 (30° view 11.1) | 6.6 / 24.1 / 9.4 / 8.7 |
| texel stretch > 1.5× (share of what it paints, at the guide angle) | 21% | 26% (parts alone) |

**What part by part does better**

- **Drift is a non-issue.** This sheet was not rescaled. The deck tile had no
  interior detail to correlate, because the guide is a plain flat plate, and
  that is the point: on a flat part Codex invents the panel layout (seams,
  hatches, access plates; sheet cell 14), and only the outline has to
  register.
- **It reaches faces whole-ship views cannot.** Adding the four parts to the
  two whole-ship views raises coverage from 86% to 89% at 30°, 59% to 66% at
  60°, and 39% to 49% from high 45°. That last gain is the deck and turret
  roof (sheet cells 15–16). The deck was textured under the spine and turrets
  too, because a part render has no occluders.
- **Seams fall on real edges.** Projectors hand over on one plane (same true
  normal and depth) only where two projections meet across a flat surface.
  Parts alone produce **none**: every part boundary is a crease or an object
  edge, where paint is allowed to change. Whole-ship views hand over across
  plates. There the colour step is 16–20/255 against 4.5–6 between
  neighbouring pixels of one projector, a visible seam about three times the
  texture's own variation.

**What it costs**

- **Tone drifts between generations.** Where a part tile and a whole-ship
  view see the same surface, the part is 6–14/255 brighter on average, while
  the two whole-ship views agreed to within 1/255. Codex also repainted the
  grey turret orange and cream, a colour-layout change that no whole-ship
  reskin made. With many independent parts this becomes a patchwork, unless
  each part is graded to a shared palette after generation.
- **Sharing one generation shares resolution.** Four parts in one 1672-pixel
  image give each tile about 794 pixels. The small turret gained (24 texels
  per unit, about twice the whole-ship view). The deck lost (6.6, about half),
  and the pod and bow came in below the whole-ship view too. One part per
  generation would give each of these about 2.1× the figures above.
- **Square-on is per face, not per part.** "Square-on" is only true of each
  part's dominant face. The pod's faceted cans, the deck's chamfers and the
  bow's two facet directions sit at an angle to their own camera. So stretch
  is no better than a whole-ship view's on average (26% of texels over 1.5×
  against 21%), although adding parts to the views lowers it where they
  overlap (52% → 39% from high 45°).
- **Region boxes cut planes.** The deck part's region (x 11–51) ends in the
  middle of the deck plate. Combined with the whole-ship views, that cut is
  where on-plane seams appear. Parts should be cut at geometric boundaries
  (objects, or face groups split at creases), never by a box.

### Baked lighting

The prompt asked for flat albedo. The result is flatter than ordinary concept
art, but not flat:

- The engine can, evenly lit in the guide (148 / 147 / 152 top / middle /
  bottom), carries a 121 → 92 → 92 gradient in the reskin.
- A forward flank plate runs 102 / 127 / 105.

That is roughly 25–30/255 of top-light or soot shading. It cannot be
separated from the grime the prompt also asked for. Plate edges carry bright
"chipped edge" lines that act like baked bevel highlights. Under the review's
new key light these mostly pass at the guide angle. They start to fight the
real shading on surfaces the sun hits from another side (the engine pods
at 60°). A de-lighting pass, or restricting projected art to camera ranges
near its own angle, would be needed for hero use.

### Quality

- Codex's finish suits the brief: chalky cream, faded oxide, gunmetal, welded
  seams, mismatched plates.
- It is uniform, though. Rivet-and-chip noise is spread at the same density
  over every plate, with none of the localised, meaningful wear the earlier
  references asked for (soot at the drives, a few deep scars).
- At the review footprint it reads as a convincing working hull. Close up it
  would look procedurally busy.

### Tileable swatches (the alternative)

One request returned four believable 627 px albedo tiles (cream, oxide orange,
gunmetal, soot-stained steel) at plate scale:

- **Not mathematically seamless.** The wrap seam is 1.4–2.8× the tile's
  interior column-to-column step.
- **But the wraps are hidden.** Codex put plate joints on the tile edges, so a
  repeat mostly reads as another panel line (sheet cell 12).
- **The soot tile repeats visibly**, from one large diagonal blotch.
- **The orange is too saturated** for the hull family (mean sRGB 187/102/54);
  it needs grading toward the faded oxide.
- **Usable after light work:** an offset-and-heal pass and a colour grade.
  These are exactly the inputs the existing all-face surface asset
  (`second-wind-full-surface`) consumes: neutral tile albedo on mesh UVs, lit
  by the scene.

## Recommendation

1. **Scale part by part for the major armour parts**, rather than adding
   whole-ship views. It has no meaningful drift, it reaches decks and roofs,
   it is not occluded, and its seams land on real edges. To make it work at
   scale:
   - **Define parts geometrically**: whole objects, or face groups split at
     creases, never region boxes.
   - **Give big parts a generation of their own**, or share a sheet only
     among parts of similar size, so resolution stays at or above the
     whole-ship view's ~12 texels per unit.
   - **Attach one fixed finish reference to every request**, and grade each
     result to the shared palette per material region before projection;
     tone drift is the main defect.
   - **Generate once per identical part**: the two turret pairs and the
     mirrored engine pods.
2. **Use whole-ship projection only for camera-held hero faces.** For a shot
   that holds a known angle (the port-side landing, the bay approach), one
   reskin of a guide from the shot's own camera gives the most coherent
   finish across many parts at once, in one tone. Keep the camera within
   about ±30° of the projector.
3. **Surface small parts and anything unseen from Codex swatches.** Ask for
   single-material tiles with "seamless, plate joints on the edges", grade
   them and heal the wrap, then use the full-surface material pipeline. Wear
   placement (soot at drives, scars) stays a mask decision under our control
   rather than Codex's uniform noise.
4. **Do not build a six-view whole-ship atlas.** Every overlap carries the
   uncorrelated-detail problem, every view carries its own baked light, and
   the hand-overs fall across plates.

Whichever route, budget a de-lighting or grading pass (generated RGB carries
25–30/255 of baked shading) and measure every generation's registration; it
cannot be assumed.

## Files

| Path | What |
|---|---|
| `run_codex.sh` | one verbatim `image_gen` call; copies the output out of `~/.codex` |
| `views.py`, `render_guide.py` | guide cameras; guide / mask / depth renders |
| `measure_drift.py` | segmentation, fits, interior drift → `registration/*.json` |
| `project.py` | object-attached multi-projector projection; coverage, stretch, seams and disagreement → `review/*-projection.json` |
| `parts_sheet.py` | four part guides into one sheet, and the generated sheet back into tiles |
| `swatch_check.py` | tile wrap-seam metric → `review/swatches.json` |
| `compose_review.py`, `rebuild.sh` | review sheets; full replay without Codex |
| `prompts/` | the exact prompt files sent |
| `generated/` | the four Codex outputs, untouched |
| `guide/` | the exact guides sent to Codex, plus masks, depth previews and camera matrices |
| `build/` (ignored) | applied-modifier scene, depth EXRs, registered images, overlays, every review still, projected `.blend`s |
