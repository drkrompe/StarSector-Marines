# Port approach realism / shot 04

The [refined working port](../port-refinement/README.md) supersedes this early
architecture pass with a varied district, road traffic, Hound/Venture parallax
cards and a joined terrain/pavement boundary. This source proof remains retained.

A first textured motion study derived from `layered-next-scenes.blend`, using
camera `04 PORT / side window`. The world remains rebased around the ship.
Output index `i=0..95` samples source frame `400 + 0.5*i` at the original
12 fps and encodes at 24 fps, preserving four seconds of travel.

The nearby district contains 27 sheds. Roofs, portals, canopies, ribs, ventilators,
gantry legs, braces, trolleys, cables and cargo stacks are meshes that retain
perspective and occlusion through the move. A generated industrial material atlas
supplies neutral roof metal, wall cladding, concrete and orange steel. Physically
scaled face UVs stay inside each quadrant with a 2% guard margin. The atlas is
surface art, not a registered painting of the buildings or a seamless texture set.
Raw images, exact prompts, input roles, native dimensions and hashes are retained
in [generation.json](generation.json) and [turf-generation.json](turf-generation.json).

The accepted cabin material and ship-local projector are reused. Distant sky,
hills and woodland use separate cards and retained realism art. Their arrangement
is a compositing choice, not canonical geography.

The concrete slab owns its footprint. Original terrain triangles are frozen
before cuts remove the lower surface beneath that rectangle. Low turf uses a
native 1254×1254 texture at an 80 m conceptual period. It replaces terrain color
while preserving the analytic near-bank transparency. Woodland geometry is clipped
behind source Y=3850 with its UVs preserved. The far-estuary overlay is hidden
because it crossed the distant forest as a thin line. The source main river mesh
remains unchanged, although the river is effectively obscured in this view.

The source sun retains shadows. A 2048 MB EEVEE shadow pool prevents overflow.
The saved scene has 32 TAA samples; `render_motion.py` uses 64 for the final
review, as recorded in [scene-manifest.json](scene-manifest.json).

## Review and evidence

The [movie](review/jangala-port-approach-realism.mp4) is silent H.264/yuv420p,
1920×1080 at 24 fps, four seconds and 3,126,913 bytes. It contains 96 distinct
rendered frames. [Media validation](review/media-validation.json) records its
hash, frame count, cadence and successful full FFmpeg decode.

[Scene validation](review/scene-validation.json) compares all 96 camera and
inverse scenery transforms against the saved source with zero numerical error.
All 171 ship meshes retain their vertex/topology hashes. External textures exist
and use relative paths. [Proxy visibility](review/proxy-visibility.json) confirms
that retained plantation proxy bounds remain outside this shot over all samples.

Start, midpoint and end were inspected at 1920×1080. Normal-speed browser playback
presented all 96 frames without drops; [motion review](review/motion-review.json)
binds those findings and limitations to the scene and movie hashes. Review stills
include [start](review/start.jpg), [midpoint](review/poster.jpg),
[end](review/end.jpg) and a [blockout comparison](review/contact-sheet.jpg).
The [three-shot cut](../sequence-review/README.md) places the port after the
forest reveal and accepted farmland passage.

Broad cleared ground, repeated sheds, plain cargo props and the abrupt vegetation
strip along the pavement perimeter remain refinements for the next art pass.
This is an authoring study with no sound or game integration.

## Regeneration

From this directory, run background Blender 5.2 with `--factory-startup` and
`--python-exit-code 1`, in this order:

1. `--python build_scene.py` rebuilds the scene and automatically applies
   `finish_scene.py` and `finalize_environment.py`.
2. `--python verify_scene.py` checks the saved source and derived scene.
3. `--python render_motion.py` renders the 96 half-frame evaluations.
4. Run `assemble_review.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'`
   with Python/Pillow to encode and verify the movie.

Geometry guides and group masks are retained in `guides/`; the cabin is already
textured in these guides while the nearby port is neutral geometry. The original
guide is `guides/port-midpoint.png`. Intermediate frames and diagnostics remain
under ignored `build/`. All authoring assets remain outside shipped `mod/`.
