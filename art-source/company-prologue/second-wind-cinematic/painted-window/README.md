# Painted Jangala window test

> Historical study: superseded encoded movies were retired during the 2026-10-16 cleanup.
> Source art, prompts, Blender scenes and reference stills remain. Movie sizes,
> playback observations and media receipts below describe the original exports;
> rebuild or recover them before running these historical media checks. See the
> [current cinematic index](../README.md) and [cleanup record](../../cleanup-record.json).

The follow-up [layered parallax study](../parallax-window/README.md) retains this
painting treatment while adding independent landscape layers, moving cloud and
Atlas traffic, with a precision correction for the earlier cabin jitter.

A four-second 2.5D painting test for the retained high-window shot, using the
original ship and camera animation. It studies a painted campaign-art treatment
through the actual crew viewport. The render and projection checks passed;
the painted style remains a proposal for review. This does not establish final
cinematic art, canonical geography or ship dimensions.

## Repeatable flow

1. Block the shot in Blender and keep the approved camera and ship motion.
2. Render a wider landscape guide at the middle of the move. Paint it once with
   ImageGen, keeping the composition and using a retained image for style.
3. Put that painting on a world-fixed background for distant views. Keep nearby
   objects such as the cabin in 3D so they move differently from the landscape.
4. Render a wider cabin guide against the painted background. Paint its surfaces
   in the same style, then project that image from a camera fixed to the ship.
5. Render the entire move from these fixed paintings. Check the endpoints,
   projection coverage and encoded video before expanding to another shot.

Keep raw images and exact prompts. Generate no new artwork per animation frame:
the same brush strokes move with their assigned surface throughout the shot.
For low port passes, nearby trees or structures need separate painted layers or
geometry, with artwork covering surfaces revealed by the camera move.

## Sources and projection

[prepare_guide.py](prepare_guide.py) reads the retained
[forest study](../forest-lookdev/README.md) at source frame 144 and exports
1920×1080 composition guides, camera metadata and a binary opening mask under
[guides/](guides/). The mask describes the original viewport opening; it is not
a replacement for the scene's physical cabin geometry.

The final selected [landscape v2 painting](assets/landscape-painted-v2.png)
uses the authoritative [corrected wider guide](guides/landscape-guide-overscan-corrected.png),
with [v1](assets/landscape-painted-v1.png) supplied only as a painting-style reference.
Its [exact prompt](prompts/landscape-v2.txt) and
[generation metadata](assets/landscape-painted-v2.metadata.json) are retained.
Both landscape outputs are raw 1672×941 RGB, unchanged from built-in ImageGen,
despite the requested 1920×1080. Painted river banks and wooded silhouettes can
differ from the guide; projection correspondence requires scene review.

The original [landscape guide](guides/landscape-guide.png) and
[v1 prompt](prompts/landscape-v1.txt) remain provenance for the earlier painting.
That guide accidentally rendered through the 26 mm shot camera because Blender's
timeline camera marker overrode the selected guide camera. The corrected guide
uses the intended approximately 19.259 mm overscan camera. For off-timeline guide
renders, clear marker camera bindings or otherwise ensure they cannot override
the intended camera during frame evaluation; verify the active camera and lens
at render time. Do not use the old guide as the final projection authority.

[build_painted.py](build_painted.py) opens the original
[arrival scene](../animatic/jangala-blockout.blend), leaving that source file
untouched. A fixed world-space landscape projector uses 1.35× overscan; a separate
ship-local cabin projector uses 1.12× overscan. Both reference source frame 144.
The selected method places a continuous painted panorama 14 km away in world
space behind the painted 3D cabin. Old forest instances, separate ridge domes and
weather proxies are hidden to prevent conflicting silhouettes. The original
flight and camera animation supplies relative cabin-versus-landscape parallax;
this method does not supply individual-tree or river-bank depth parallax.

An earlier coarse-terrain projection stretched painted ridges across mismatched
surfaces. `--terrain-projection` retains that experiment as a diagnostic only,
including its continuous far-ground extension; it is not the selected delivery
method.

Projection divides camera-space X and Y by −Z in the material shader per pixel.
This avoids the warping caused by interpolated vertex UVs on large nearby cabin
faces. Emission materials and Standard color management preserve the painting's
brushwork and baked lighting. They do not provide physical relighting of painted
surfaces. The cabin material uses [cabin v2](assets/cabin-painted-v2.png); the earlier
v1 remains a separate style source. The exact retained input for cabin v2 is
[cabin-paint-guide-v2.png](guides/cabin-paint-guide-v2.png). It contains landscape
v1 because of generation order, but only its interior pixels are projected onto
the cabin; the final background independently uses landscape v2. The intermediate
cabin v1 guide was overwritten, as its metadata records. Rebuilding with
`--landscape-only` writes `guides/cabin-paint-guide-current.png`, an ignored
scratch guide, and does not replace the retained exact v2 input. Final motion and
projection evidence must be checked against the built scene.

## Rebuild and review

Run from this directory. Replace `<Blender-5.2.exe>` and `<Python.exe>`
with the installed executables. ImageGen edits are separate authoring steps using the retained guides
and prompts; the build commands do not regenerate paintings.

```powershell
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python prepare_guide.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python build_painted.py -- --landscape-only
# Optional scratch guide: guides/cabin-paint-guide-current.png.
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python build_painted.py -- --preview
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python verify_projection.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_motion.py
& '<Python.exe>' assemble.py
& '<Python.exe>' validate_media.py
```

[render_motion.py](render_motion.py) opens the saved painted scene. The native
24 fps delivery samples original source time `120 + 0.5 × i` for
`i = 0…95`: 96 images, four seconds, 1920×1080. Frames are named
`build/frames/frame_0000.png` through `frame_0095.png`. This preserves the original
12 fps timeline's duration without duplicating output frames. The saved scene's
source frame numbering is distinct from the delivery image indices.

[assemble.py](assemble.py) encodes a clean silent H.264 movie at CRF 19 with
faststart and no burn-ins. It also makes the midpoint poster and a 1920×1080
comparison sheet containing the previous 3D foliage view, painted midpoint,
start and end. `--preview` makes stills only; `--frames`, `--output` and `--ffmpeg`
are available, with `FFMPEG` as an executable environment override.
Python needs Pillow; Blender supplies its own Python and NumPy. FFmpeg and
FFprobe must be on PATH, or supplied explicitly with `--ffmpeg` / `--ffprobe`
to the relevant scripts. Keep the 12 fps source timeline unchanged: the renderer
samples half frames, and only the encoder sets the delivery rate to 24 fps.

Review outputs belong in [review/](review/):
`review/jangala-painted-window-test.mp4` (retired export),
[poster](review/poster.jpg) and [contact sheet](review/contact-sheet.jpg).
[projection-manifest.json](projection-manifest.json) records the built scene's
projectors and scope; it is not itself proof of visual acceptance.

The retained [projection report](review/projection-validation.json) checks five
source frames from 120 through 168. All 171 ship meshes and sampled camera/root
matrices match the source. Texture paths resolve, and visible cabin/panorama
samples stay inside their paintings with no missed rays or negative projection
depth. This is a 64-pixel sampling grid, not an exhaustive pixel proof.

The [media report](review/media-validation.json) records a successful full decode:
96 distinct source frames, 1920×1080, 24 fps, four seconds, silent H.264. The movie
is 1,536,429 bytes (about 1.54 MB). Endpoint and midpoint stills were visually
reviewed for frame-edge streaking, cabin alignment and horizon distortion.

Review painting continuity through the viewport, cabin edge alignment, river-bank
alignment and newly exposed areas over the complete move. A fixed painting cannot
supply unseen surface detail; larger camera moves, other shots, lighting changes
and close approaches require separate review or additional artwork. This test's
painted panorama cannot establish individual-tree depth or unrestricted flythroughs.

All files here are authoring sources outside shipped `mod/` content. There are no
game runtime or UI changes. The raw 1672×941 paintings suffice for this style test;
1080p delivery does not make them native 1080p artwork. Choose future painting
resolution from the largest crop visible in the shot, including motion margins,
and review at delivery size. Retain raw masters here, keep frame sequences under
ignored `build/`, and budget a separately compressed delivery asset per shot.
This experiment adds no bytes to the deployed mod.
