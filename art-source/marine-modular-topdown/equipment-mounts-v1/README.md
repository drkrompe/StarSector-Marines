# Infantry shoulder equipment mounts

The retained guides specify two independent north-forward, overhead accessories.
ImageGen supplies their finished pixels. `make_guides.py` draws intermediate
geometry only. Exact ImageGen prompts are retained beside their guide files.

Retain accepted raw outputs as `missile-pod-source.png` and
`screen-emitter-source.png`. Inspect both source images before exporting.
`export_assets.py` measures generated alpha, normalizes the actual opaque bounds
with uniform scaling, and writes the new `variants/equipment/` sprites plus measured source
registration and sprite canvas metadata in `export-measurements.json`.
If raw art includes another visible item, register its explicit measured crop
under that asset's `crop` field in `source-regions.json`. Never derive a crop
from requested dimensions without measuring the generated output.

The visible missile footprint fits within .24 shoulder-width wide by .38 long.
The screen emitter fits within .18 by .24. Export canvases include transparent
padding; consume the recorded `export_canvas_size_sw` to preserve those visible
sizes. Both pivots are the canvas center and both sprites point north.
No existing armor sprite, primary weapon, or palette is replaced by this recipe.

## Accepted generation and registration

Both retained sources were produced with the built-in ImageGen tool on
2026-10-13, using the retained prompts, geometric guides and separate Specter
and Aegis material references. The returned masters are 1254x1254 RGBA,
despite the 1024x1024 guide canvases. Each contains one overhead north-forward
device with opaque hardware and a transparent exterior. Sources retain every
generated pixel and alpha value.

Very faint alpha 1..7 specks extend far outside the visible hardware. The
measured registration crops in `source-regions.json` surround the alpha >= 8
hardware bounds with two source pixels of fringe. Cropping excludes those
remote specks without keying or repainting the hardware alpha.

| Asset | Raw measured crop | Export canvas | Export alpha bounds | Canvas dimensions in shoulder widths |
| --- | --- | --- | --- | --- |
| Missile pod | 394,217 to 860,1031 | 128x192 | 20,20 to 107,172 | .32 x .48 |
| Screen emitter | 412,318 to 841,945 | 128x160 | 31,32 to 97,128 | .32 x .40 |

Uniform normalization preserves each generated aspect ratio. Final visible
extents are .2175 x .38 shoulder widths for the missile pod and .165 x .24
for the emitter, slightly slimmer than their .24/.18 width ceilings.
Exporter measurements retain SHA-256 hashes, raw dimensions, measured cuts,
full canvas dimensions and final alpha bounds. Both sources and exports were
visually inspected: three missile channels remain legible, the small cyan
emitter aperture stays inside its casing, and neither sprite carries a ground
shadow or front/isometric silhouette. Assembly at game scale remains owned by
the production equipment renderer's snapshot evidence.

## Assembled review

`preview.png` and `preview.mp4` come from the registered `equipment-mounts`
snapshot suite at 1920x1080 and 100% UI scale. They use the actual catalog
systems, armor mastering, and production infantry compositor through the
headless graphics backend. They are pose reviews, not a recording of a live
Starsector session. The video repeats twelve lossless source frames five times
at 10 fps; its input is the PNG sequence, not the quantized GIF.

The review includes enlarged idle equipment, walking and firing, independent
helmet look, and a Specter bracing its separate handheld launcher. The footer
uses the existing Specter battle's normal fit of 17.142857 pixels per cell;
these tiny samples expose the limit of fine equipment recognition at full-map
zoom. Aegis mounts at its torso-derived hinge; Arbalest offsets outward .10
shoulder widths and forward .01; Specter offsets outward .13 and forward .01
on its articulated left shell. Those placements live in the equipment catalog,
not in the review scene or source sprites.

Regenerate through `gradlew.bat createSnapshots -Psnapshot=equipment-mounts`.
Frames are emitted to `build/snapshots/equipment-mounts/frames/`.
