# Next arrival scenes: layered rough blocking

> Historical study: superseded encoded movies were retired during the 2026-10-16 cleanup.
> Source art, prompts, Blender scenes and reference stills remain. Movie sizes,
> playback observations and media receipts below describe the original exports;
> rebuild or recover them before running these historical media checks. See the
> [current cinematic index](../README.md) and [cleanup record](../../cleanup-record.json).

Untextured staging for the approach after the
[painted forest/window study](../parallax-window/README.md). This review keeps the
accepted ship, cabin and landing mechanics as actual geometry while separating
future painted scenery by depth and occlusion. It is rough blocking, not a final
painted cinematic or an arrival-repair claim. Scene geometry validation passes.
Start, midpoint and end frames were inspected for camera framing, visible support,
foreground occlusion and exposed scenery edges; the user has not reviewed this cut.

## Excerpts and continuity

The 24-second, silent 1920×1080 review is approximately 2.56 MB and contains four native-speed excerpts with
explicit editorial time ellipses. The saved source timeline remains 12 fps;
[render_review.py](render_review.py) evaluates half-frame times for 24 fps output.
The frame map in [scene-manifest.json](scene-manifest.json) is authoritative.

| Beat | Output frames | Source frames | Duration |
|---|---|---|---|
| 03 Managed land beside the window | 1–96 | 240–287.5 | 4 s |
| 04 Working port through the same window | 97–192 | 400–447.5 | 4 s |
| 05 Exterior aligned approach | 193–312 | 565–624.5 | 5 s |
| 06 Cradle load transfer | 313–576 | 647–778.5 | 11 s |

+X remains bow/flight, +Y port and +Z up. Window scenery travels aft; the exterior
cameras remain on the port side. Managed clearings, river, freight routes, port
frontage and the final apron belong to one retained layout. The forward berth is
not moved into a purely lateral window view. Original ship scale remains the
provisional 1.5 m per hull unit; this is not a canonical dimensional claim.

[build_scene.py](build_scene.py) derives from the saved
[arrival greybox](../animatic/jangala-blockout.blend). It preserves root rotation,
scale, camera-local animation, mesh geometry and relative placements. To avoid
large-coordinate precision jitter, it sets the ship root translation to zero and
parents other top-level objects beneath `LAYERED | inverse source flight`, whose
linear keys are the negative original root position. Unlike the earlier window
study, this rebase has no reference-origin offset. Source files remain unchanged.

Landing retains original source times: stop fore/aft at 624, hold through 648,
lower from 649, contact at 744, secure clamps during 751–774, and belly-lift
shutdown at 768. The source pad top and shoe bottom meet at Z=5 m, with source
ship root Z=21 m; rebased world coordinates differ. Rear engine geometry remains
fixed. Supports, shoes, clamps, ventral lift hardware and nearby structures stay
geometric to preserve contact and occlusion. Hatch opening, inspection, repairs,
boat restoration and company formation are outside this review.

## Layer plan

[environment_layers.py](environment_layers.py) adds nine independently grouped
roles: far hills, far forest, middle woodland, near hedgerows, exterior hills,
exterior canopy, exterior working district, drifting cloud and distant freight.
The extra exterior district has real shed, tank and open gantry perspective.
Foreground field rows, existing roads, port structures, river and yard are retained.
Older canopy/ridge proxies are hidden where the new forms replace them.

Distant hills, forest and cloud can later receive painted silhouettes or plates;
woodland and hedgerows retain shallow relief and independent depth. Near roofs,
towers, crane frames, ship and cradle need actual geometry with painted surfaces,
not a flattened image hiding structural crossings. Object-index IDs and grouping
metadata identify future paint/mask boundaries. The present scene uses flat colors,
not finished projected materials. Independent cloud and freight motion remains
separate from inverse flight. The freight body is a generic distant proxy, not
an assertion of canonical Atlas geometry.

## Build and review

Run from this directory using Blender 5.2, Python with Pillow and FFmpeg; replace
executable placeholders with installed paths.

```powershell
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python build_scene.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python verify_scene.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_review.py -- --preview
& '<Python.exe>' assemble_review.py --preview
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_review.py
& '<Python.exe>' assemble_review.py --ffmpeg '<ffmpeg.exe>' --per-shot
& '<Python.exe>' validate_media.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python export_layer_maps.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python measure_layers.py
```

[assemble_review.py](assemble_review.py) encodes the clean review plus optional
per-shot files and annotated review stills. Full assembly requires all 576 source
images; preview mode uses representative stills. Review assets live in
[review/](review/), including `review/layered-next-scenes-review.mp4` (retired export).
Media validation confirms all 576 output frames decode. There are 564 unique
images; the twelve repeats are authored holds before descent and after stopping
or securing the ship. No frames are duplicated inside the moving spans.
The [contact sheet](review/contact-sheet.jpg) and individual `shot-03-review.jpg`
through `shot-06-review.jpg` provide still previews for mobile review. Color-coded
`shot-03-layers.png` through `shot-06-layers.png` show the planned separation;
`layer-map-legend.json` maps their colors to object groups. These are antialiased
review visualizations. Export proper masks from the scene for production compositing.

## Painting handoff

Shot 03 now has a separate [managed-land realism motion study](../managed-land-realism/README.md)
using this camera and timing. It combines textured ground, layered distant canopy
and a worn cabin finish; this directory remains the unchanged blocking reference.

Use each shot's saved camera and geometry to produce its own composition guide.
Use the [naturalistic look study](../realism-lookdev/README.md) and current campaign
stills as the updated surface-style references, with bright daylight and physical
material detail. The earlier painted window study remains the motion reference. Generate a master
painted composition, then request registered sky, hills, woodland and cloud layers
with the guide and color groups. Keep their unedited masters and exact prompts.
Near buildings, field rows and all ship/landing surfaces receive materials or
controlled projections on the retained geometry. Keep hull damage consistent with
the heavy-arrival reference; the clean grey material here records shape only.

Plan coverage over the whole camera move before generating plates. The aligned
depth diagnostic in [layer-motion.json](review/layer-motion.json) measures roughly
1754 pixels of near-plane movement in shot 03 and 511 pixels in shot 04, versus
53 and 34 pixels for the distant hills. These are hypothetical points at the
layer depths, not a claim that every point is visible. A single-frame painting
cannot cover that much near travel without a wider canvas, overlapping artwork,
or textured geometry. Inspect foreground edge crossings across every output frame.
The report separately tracks actual crest vertices and records occlusions.

The landing camera remains fixed relative to the yard. Its shot gets motion from
the hull descending against the foreground crane and background buildings, then
the clamps and lift shutdown. Preserve that quiet load-transfer beat when painting.

[verify_scene.py](verify_scene.py) opens source and derived scenes without rendering
or saving either. Its [report](review/scene-validation.json) compares all 171 ship
mesh hashes, four aft-engine groups, original hardware/camera relative transforms,
and all 576 mapped root/inverse-flight samples. It checks actual evaluated shoe/pad
contact, clamp clearance and support/lift separation at landing events. Conservative
new-geometry bounding-box checks cover the final corridor at three sampled times;
they are not a continuous triangle collision or exhaust-flow simulation. Rendered
framing, damage appearance, mask quality and final artistic acceptance are not
established by those numeric checks.

All assets here are authoring sources outside shipped `mod/` content. No audio
synthesis, game playback, UI or campaign-state behavior changes are included.
