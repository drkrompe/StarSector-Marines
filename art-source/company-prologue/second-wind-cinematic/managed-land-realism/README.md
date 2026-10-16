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
- [Preceding Atlas lift study](../parallax-window/review/atlas-lift/README.md), retained separately in the earlier painted style
- [Forest reveal in the matching realistic style](../forest-reveal-realism/README.md) and [three-shot continuity review](../sequence-review/README.md)

The output contains 96 actual evaluations at 24 fps. Output index `i=0..95` maps
to source frame `240 + 0.5*i` at the original 12 fps, preserving four seconds of
motion. The H.264 review has 96 distinct source frames; its measured size and full
decode result are recorded in `review/media-validation.json`. Camera `03 LAND / side window`, ship geometry and inverse source-flight
transforms are retained. The viewpoint is a crew cabin; no player identity or
spoken dialogue is assigned. The saved scene keeps the original timeline rate.

## Surface and depth choices

Use bright, humid daylight, natural vegetation, atmospheric distance and worn
industrial materials. Fine surface detail should read as physical scenery at
review size, without conspicuous brush strokes, exaggerated sharpening or a dark
grade. Generated guides are layout constraints; the approved naturalistic study
is the style reference.

Nearby fields and roads share one continuous terrain mesh. The broad orthographic
map supplies surrounding land and roads; source-space XY masks apply denser crop
art to ten parcel footprints on that same surface. Small variations between plots
reduce exact repetition. This remains a limited crop-art set rather than a complete
plantation library.

The earlier moving triangular field patches were real surface intersections:
fitting only the corners of separate clearing faces left their interiors crossing
the terrain below. The original crop, clearing and road proxies are now hidden.
`review/ground-overlap-before.json` retains the diagnosis; the validator samples
face interiors and terrain contact as well as visible texture coverage. Raising
the old faces or adding motion blur would not address the underlying topology.

The compound apron exposed another depth conflict around output frame 11
(0.417 seconds): even a positive half-metre gap from the ground was insufficient
at this camera distance and near-clip setting. `compound_pad.py` gives each
footprint one visible owner: terrain is cut out under the apron, and apron under
the landing slab. The original terrain triangles are preserved before clipping
their covered faces, and the original terrain shader remains intact. Landing
markings are material masks on the slab. Changing the whole terrain's transparency
graph instead caused a renderer interaction that hid nearer machinery.
[Pad comparison](review/pad-depth/comparison-1080p.png) retains the diagnosis.

On the right, a low hovering crop tender has four lift pods, transverse spray
booms, a reagent tank and a weathered control cabin. A small survey drone works
farther out; two service sheds, three silos and a utility pad establish an outlying
farm compound. These are geometry-based, provisionally sized props anchored to
the actual source terrain. The tender's soft shadow uses terrain triangles with
a moving material mask, avoiding a coarse floating ground plane.

The tender moves steadily along the field at 12 source metres per second,
covering 47.5 metres during the retained sample span. Its shadow follows the same
path. Eight nozzle-attached, softly blended spray fans fall toward the crop with
a short trailing wake; their procedural density changes gently over time. They
are separate effects geometry, excluded from the projected machinery finish.

The agriculture finish comes from a tight camera guide and one retained RGBA
ImageGen output. Each rigid assembly carries its own midpoint projector, with
physical materials on backward faces and outside the artwork's alpha. ImageGen
changed the sizes and positions of the three assemblies: per-assembly bounds
registration restores their placement in the shader, but their internal panels
are only approximately registered. Geometry still owns every silhouette. This
small-prop proof does not establish a multiangle hero-ship texture workflow.

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
Its aspect and bow/engine identity remain fixed. Independent motion gives it a
measured 50.28-pixel rightward passage over four seconds, with nearly level screen
height. Its previous 19 m/s source movement appeared leftward because the camera
overtook it; the revised provisional source velocity is 300 m/s. Screen motion,
rather than that isolated velocity, controls the intended distant freight pace.
Layer distances, apparent size,
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
`export_agriculture_guides.py` and `agriculture-guide-manifest.json` retain the
agriculture geometry guide, native camera crop and provisional object dimensions;
`agriculture-reskin-registration.json` records the measured component bounds.
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
mapped transforms, samples visible texture coverage, and checks external images.
It also evaluates agriculture meshes, terrain clearance, broad thin-surface
intersections, assembly bounds and attached-projector stability at all 96 times.
All 171 ship meshes and 96 camera/rebase samples remain unchanged. Ground
coverage probes hit the terrain/apron/landing union in all ten parcels. Across
96 times, 15,840 camera rays and 15,840 vertical rays confirm one ground owner
at each sampled compound footprint. The tender clears
the ground by at least 2.199 m; the compound apron top clears it by 0.458 m,
the spray stays at least 0.624 m above the terrain, and the soft shadow stays
0.160 m above matching terrain triangles. No sampled
terrain crossings, assembly overlaps or attached-projector drift were found.
The report records sampling limits; these checks supplement rendered motion review.
The small Atlas misses this sampling grid, so its visibility was checked in the
rendered frames instead. [Atlas motion validation](review/atlas-motion-validation.json)
checks its direction and projected travel at all 96 times; reproduce it with
Blender's background Python runner and `diagnose_atlas_motion.py`.
[Media validation](review/media-validation.json) verifies
96 distinct source frames, duration, resolution, codec and full video decoding.
[Motion review](review/motion-review.json) records consecutive and spaced frame
inspection plus a complete browser playback: 96 presented frames, zero dropped.
Those checks do not establish artistic acceptance, collision engineering, or
whether foreground crop detail is sufficient for a final cinematic. Review the
rendered motion for layer seams, texture drift, repetition and compression.

All files are authoring assets outside `mod/`. This study adds no audio, game
playback, campaign state or runtime UI behavior.
