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

## Missile muzzle registration

The pod's visible forward lip is at canvas coordinate (64, 20), normalized
to (0.5, 0.10416667). The catalog presentation uses that point for the launch
flash and sprite registration. Transparent padding is not the muzzle.

The simulation owns a separate physical mount in cells: right/forward from
the wearer, followed by muzzle reach along the pod's independent aim.
The following measurements align the neutral catalog assembly at the marine's
0.6-cell shoulder width. Aegis uses its 150x126 body at scale 1; Arbalest
uses its 150x113 body at scale 0.8. Both have a -0.12 shoulder-width body
master offset. The torso-derived hinge is (-0.234694, 0.150124) of body width
and height; Arbalest then adds its carried offset (-0.10, 0.01).

| Pattern | Right cells | Forward cells | Muzzle reach cells |
| --- | --- | --- | --- |
| Aegis | -0.1408164 | 0.0036625 | 0.114 |
| Arbalest | -0.1606531 | -0.0129148 | 0.0912 |

Re-register these measurements if the body, attachment, pod canvas or scale
changes. The physical mount must remain valid without loading sprite pixels.
`pod-battle` supplies actual launch and combat evidence, including normal and
closer camera views plus a short launch detail; `equipment-mounts` remains the
static assembly and articulation review.

The retained `roadmap/progression/assets/pod-battle-*.mp4` files use the
lossless PNG sequences from `createSnapshots -Psnapshot=pod-battle`.
Normal and closer views play 181 frames at 15 fps; the 31-frame detail plays
at 30 fps and repeats five times. All renders are native 1920x1080 at 100% UI
scale, with unchanged unit dimensions. Detail includes six frames before a
real salvo after spawn fade, followed by its launch and recovery. Companion
PNGs show that visible launch; `pod-battle-verdict.json` records the actual
simulation checks. These are headless production battle captures, not live
Starsector screen recordings or mission-balance acceptance.
