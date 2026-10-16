# Second Wind: bounded 3D texture and setdown proof

> Historical study: superseded encoded movies were retired during the 2026-10-16 cleanup.
> Source art, prompts, Blender scenes and reference stills remain. Movie sizes,
> playback observations and media receipts below describe the original exports;
> rebuild or recover them before running these historical media checks. See the
> [current cinematic index](../README.md) and [cleanup record](../../cleanup-record.json).

This is a real retained-mesh ship with attached surfaces, tested in a short side
landing excerpt. It is **not a finished cinematic or a complete multiangle ship
texture**. The yard remains the original greybox. Generated side RGB retains some
baked lighting; the neutral atlas supplies other faces. The quarter view exposes
that coverage boundary deliberately.

Open `second-wind-daylight-landing-proof.blend` for the editable final proof.
Review `review/second-wind-setdown-proof.mp4`, `review/start.png`,
`review/poster.png` and `review/end.png`. `projected-port-side.png` and
`projected-port-quarter.png` show surface coverage; `daylight-contact-preview.png`
shows contact under the proof's brighter sky and apron fill.

## Geometry and surfaces

The accepted V3 identity arrives through `../animatic/jangala-blockout.blend`:
171 original mesh objects, 6,568 vertices and 5,302 polygons. Their vertex/polygon
hashes remain unchanged, including engines, pod and hardpoints. Axes remain +X
bow, +Y port, +Z up. The 1.5 metres per hull unit scale is provisional.

The added external layer has 397 armor plates, 15 exposed backing tiles and
1,716 fasteners, plus small service seams and backing splints. The pressure hull
is uncut. Dorsal and flank courses follow actual hull/raised-armor surfaces;
plate offsets are restrained and major step bridges rejected. This generalized
panel hierarchy still needs artistic refinement. Original casings, turrets and
bay interiors remain simplified; no final heavy-arrival damage claim is made.

The native four-material atlas is 1254×1254 RGB. Four lossless 627×627 crops cover
cream, oxide, gunmetal and pressure backing. Box coordinates attach to the ship
root on rigid hardware; the four locally animated lift placeholders each have
component-local coordinates. A repeat spans six hull units (nine provisional
metres). Albedo and fine procedural bump are separate; these are not measured
PBR texture maps.

The generated side finish is native 1672×941 RGB, guided by the immutable
1920×1080 `guides/side-surface-guide-v1.png` and the heavy-arrival reference as
material/damage direction. `project_side.py` records exact camera-projected UVs
on the mesh and a near-side coverage mask, with neutral atlas fallback elsewhere.
The normalized image registration is identity: no arbitrary bounds fitting.
Visibility uses facing normals and source-camera center/corner ray samples; the
pod's broad front face has an explicit hatch-center occlusion exception.
Individual generated panel correspondence is not certified. Some detail crosses
the simplified physical panels; a larger turn would need complementary coverage
and further material work. The actual mesh owns silhouette and occlusion.

## Evidence and timing

The four-second movie evaluates 96 actual Blender half-frame poses from source
720 through 767.5. Source actions remain at 12 fps; output is 24 fps. This includes
the remaining descent, shoe/pad contact at 744, and early clamp progression.
It does not include full clamp completion at 774 or the whole arrival sequence.
There are no generated in-between video frames or narration.

`verification.json` binds the source/final blend hashes and checks all 96 poses:
original ship/camera matrices agree exactly, original mesh hashes match, side UV
hashes remain static, four shoe footprints remain on their pads with no gap after
contact, and texture images are packed or readable. It also reports the four
animated lift placeholders and their component-local mapping assertion.
These are construction checks, not continuous collision or structural validation.

`provenance.json`, `assets/atlas-crops.json` and the retained prompts record native
inputs, reference roles, dimensions and hashes. `side-projection-registration.json`
records projection coverage/camera and limits. `render-manifest.json` binds each
frame to the final scene hash and rejects stale frames from interrupted runs.
`media-validation.json` records the encoded dimensions, decoded frame count,
movie size/hash and uniqueness. Playback review is separate from decoding checks.
`motion-review.json` binds the parent browser playback receipt to the movie and
final scene hashes: four seconds at normal speed, 96 presented frames and no
dropped frames. Initial/end browser views and native start/poster/end images were
sampled for attached surface features and seated shoes. This was sampled image
review, not continuous per-frame visual QA. Greybox yard surfaces and the dark
solid lift-placeholder cones remain obvious limitations.

## Regeneration

Run in this folder using Blender 5.2 background mode and Python with Pillow.
`B` below denotes `C:/Program Files/Blender Foundation/Blender 5.2/blender.exe`;
`P` denotes the configured bundled Python. FFmpeg and FFprobe must be on PATH.

```text
P prepare_assets.py
B --background --factory-startup --python-exit-code 1 --python build_proof.py
B --background --factory-startup --python-exit-code 1 --python integrate_atlas.py
B --background --factory-startup --python-exit-code 1 --python detail_armor.py
B --background --factory-startup --python-exit-code 1 --python project_side.py -- --image <absolute-path>/assets/side-surface-v1.png --render
B --background --factory-startup --python-exit-code 1 --python build_landing.py
B --background --factory-startup --python-exit-code 1 --python light_and_render.py -- --render-motion
B --background --factory-startup --python-exit-code 1 --python verify_proof.py
P encode_review.py
```

The ImageGen outputs and prompts are retained inputs, not regenerated by those
commands. Original V3/animatic sources are read only. Intermediate source scenes,
obsolete coarse studies and raw render frames are under ignored `build/`.
The immutable guide/camera and native inputs remain retained authoring assets.
All review frames are rendered natively at 1920×1080; no native ImageGen
resolution increase is claimed. Nothing here ships into `mod/`.
