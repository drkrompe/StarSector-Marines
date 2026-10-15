# Managed-land approach: realism motion study

A four-second, silent study of shot 03: cultivated land, river and distant freight
seen through Second Wind's worn cabin window. It follows the forest reveal and
precedes the working-port window view. The camera and elapsed time come from the
[approach blockout](../layered-next-scenes/README.md); the surface style
comes from the [naturalistic look study](../realism-lookdev/README.md).
This is a review of surfacing and motion, not a finished arrival sequence.

## Review and source

- [1920×1080 motion proof](review/jangala-managed-land-realism.mp4)
- [Midpoint still](review/poster.jpg), [start](review/start.jpg), [end](review/end.jpg)
- [Blockout comparison](review/contact-sheet.jpg)
- [Editable scene](managed-land-realism.blend) and [scene manifest](scene-manifest.json)

The output contains 96 actual evaluations at 24 fps. Output index `i=0..95` maps
to source frame `240 + 0.5*i` at the original 12 fps, preserving four seconds of
motion. The H.264 review is approximately 2.3 MB, with 96 distinct source frames
and a successful full decode. Camera `03 LAND / side window`, ship geometry and inverse source-flight
transforms are retained. The viewpoint is a crew cabin; no player identity or
spoken dialogue is assigned. The saved scene keeps the original timeline rate.

## Surface and depth choices

Use bright, humid daylight, natural vegetation, atmospheric distance and worn
industrial materials. Fine surface detail should read as physical scenery at
review size, without conspicuous brush strokes, exaggerated sharpening or a dark
grade. Generated guides are layout constraints; the approved naturalistic study
is the style reference.

Nearby fields remain actual ground and clearing surfaces with orthographic
textures. A single map covering over five kilometres cannot provide enough detail
near the window: the broad map supplies surrounding land while a separate,
denser plot texture supplies each clearing. Crop-box proxies are hidden; roads
and clearings follow the ground instead of appearing as raised planter walls.
Small variation between plots reduces exact visual repetition; this remains a
limited set of crop artwork rather than a complete plantation asset library.

Distant sky, hills and woodland use separate cards with overlap for the whole
move. Hills use master RGB and extracted alpha. Woodland uses the master's color
above its original shoreline and blends into generated forest completion below,
where the master would otherwise introduce a second, baked river. The river
retains its geometric footprint and a dedicated water material sampled from a
clean water patch in the orthographic master, with small ripple variation.
Painting tree crowns onto the old coarse ground produced stretching during
motion; distant canopy therefore belongs to the woodland layer. Individual trees
within that layer do not have independent parallax.

The coarse shoreline reads quite straight over this short span, crop motifs still
repeat, and the lower woodland completion is softer than the naturalistic target.
These are visible surfacing limits of this proof; shoreline vegetation and a
broader crop texture set remain work for the final shot.

The cabin finish is projected onto the retained cabin geometry. The second guide
contains only geometry and the real window opening; material direction is given
in text. This prevented the style reference from changing the window's proportions.
This fixed-view cabin treatment does not demonstrate a rotating, fully textured
Second Wind hull or coverage of unseen faces.

A small, separate Atlas reuses the [earlier traffic artwork](../parallax-window/README.md).
Its aspect and bow/engine identity remain fixed. Layer distances, apparent size,
speed and haze are shot composition choices, not canonical ship dimensions or
flight performance. The sky includes clouds; the extra generated cloud-bank pass
is retained but omitted because its shape and colored edges did not fit this shot.

## Reproduce

Run from this directory with Blender 5.2, Python with Pillow, and FFmpeg/FFprobe.
Replace executable placeholders with installed paths. Background factory-startup
keeps the user's open Blender scene untouched.

```powershell
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python build_scene.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python verify_scene.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_motion.py -- --preview
& '<Python.exe>' assemble_review.py --preview
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_motion.py
& '<Python.exe>' assemble_review.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'
```

`export_guides.py` records camera/overscan and ground coverage in
[guide-manifest.json](guide-manifest.json). The separate plot guide's crop is in
[single-field-guide.json](single-field-guide.json). Retain the exact guides used
for generation: changing guides after generation invalidates their provenance.
The scene uses relative external textures, avoiding a second packed copy of the
masters. Keep this directory and its referenced sibling directories together.
Transient rendered PNG sequences, logs and Blender backups are ignored.

## Provenance and verification

[generation-inputs.json](generation-inputs.json) records every generated asset,
exact prompt, input role and original tool output. [generation.json](generation.json)
adds dimensions, hashes, alpha statistics and selection decisions. Files under
`assets/` retain the tool's original bytes and alpha, including rejected variants.
The scene performs projection, cropping and alpha handling without replacing those
masters. The approximately 1.5–1.7 megapixel generated sources are not claimed to
be native 1080p detail simply because the review is rendered at 1920×1080.

[Scene validation](review/scene-validation.json) compares retained geometry and
mapped transforms, samples the camera's visible texture coverage, and checks
external image paths. All 171 ship mesh comparisons and 96 transform samples pass;
48,960 sampled rays find no uncovered pixels or visible UV bounds failures.
The small Atlas misses this sampling grid, so its visibility was checked in the
rendered frames instead. [Media validation](review/media-validation.json) verifies
96 distinct source frames, duration, resolution, codec and full video decoding.
Those checks do not establish artistic acceptance, collision engineering, or
whether foreground crop detail is sufficient for a final cinematic. Review the
rendered motion for layer seams, texture drift, repetition and compression.

All files are authoring assets outside `mod/`. This study adds no audio, game
playback, campaign state or runtime UI behavior.
