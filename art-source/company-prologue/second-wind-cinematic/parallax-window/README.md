# Layered painted Jangala window test

A four-second extension of the [single-panorama study](../painted-window/README.md),
adding independent painted depth layers, moving cloud and Atlas industrial traffic.
The output is 1920×1080, 24 fps and silent, approximately 1.40 MB. Scene and media
validation passed. This remains a single-shot authoring study for visual review.

The [next-scenes blockout](../layered-next-scenes/README.md) extends the layering
plan through managed land, the working port, exterior approach and cradle contact.

## Motion and rendering method

[diagnose_motion.py](diagnose_motion.py) traces the earlier cabin jitter to
large-coordinate transform precision, not an authored camera bump.
[stabilize.py](stabilize.py) keeps the actual ship geometry, camera-local animation,
root rotation and root scale. In this derived scene it sets root translation to
zero and gives the scenery the inverse sampled source flight displacement, with
linear keys. The retained source scenes are unchanged. Independent cloud and
traffic motion runs beneath that scenery root.

[build_parallax.py](build_parallax.py) uses fixed planes with ordinary per-plane
UVs, unlit emission shading and Standard color management. The painted 3D cabin
retains its earlier ship-local perspective projector. Card object origins are
centered on their geometry so Blender's BLENDED transparency sorts by the intended
layer positions rather than a shared origin.

| Layer | Depth | Additional placement or motion |
|---|---:|---|
| Opaque sky | 48,000 m | Fixed relative to scenery |
| Distant hills | 34,000 m | Restrained haze tint |
| Cloud bank | 27,000 m | 12 m/s independent lateral motion; opacity 0.85 |
| Far trees | 20,000 m | Canvas Y registration −0.12 |
| Medium forest and river | 10,500 m | Fixed relative to scenery |
| Near trees | 5,000 m | Fixed relative to scenery |
| Atlas | 6,500 m | 1,000 m full-canvas width; 170 m/s independent lateral motion |

These depths, offsets, speeds and canvas widths are compositing choices, not
canonical geography, airship altitude or Atlas dimensions. Within each painted
plane, individual trees and river banks have no geometric depth.

## Art provenance and limits

Seven built-in ImageGen calls created six environment plates, including an opaque
sky, and one transparent Atlas cutout. The previous
[landscape painting](../painted-window/assets/landscape-painted-v2.png) supplies the
composition and painted style. Exact prompts and generation metadata are retained
under [prompts/](prompts/), [layers/](layers/) and [traffic/](traffic/).
The [Atlas reference](traffic/atlas-canonical-reference.png) and
[hull definition](traffic/atlas-canonical.ship) were copied from the read-only
base-game install. Its [painted side view](traffic/atlas-painted-v1.png) interprets
that canonical sprite in perspective rather than establishing canonical side geometry.

Raw masters retain ImageGen's authored alpha and pixels. The materials remap alpha
0.015…0.98 to zero…layer opacity, suppressing faint flecks and normalizing nearly
opaque interiors without editing PNGs. Generated layer registration is imperfect:
the far-tree layer needs the −0.12 canvas offset, and the medium river can drift
roughly 20–40 pixels relative to the source painting. This is an acknowledged
composition limit, not exact reconstruction. Larger moves, other angles and
arbitrary flythroughs require new artwork or actual depth geometry.

## Rebuild and review

Run from this directory with Blender 5.2, Python with Pillow, and FFmpeg/FFprobe.
Replace the executable placeholders with installed paths. Image generation is a
separate authoring step; rebuilding consumes the retained masters.

```powershell
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python build_parallax.py -- --preview
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python verify_scene.py
& '<Blender-5.2.exe>' --background --factory-startup --python-exit-code 1 --python render_motion.py
& '<Python.exe>' assemble.py --ffmpeg '<ffmpeg.exe>'
& '<Python.exe>' validate_media.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'
```

[render_motion.py](render_motion.py) renders 96 genuine half-source-frame samples:
`120 + 0.5 × i`, `i = 0…95`, ending at source frame 167.5. The source timeline
remains 12 fps; the delivery runs those images at 24 fps for exactly four seconds.
[assemble.py](assemble.py) encodes clean H.264 CRF 19 with faststart, no sound or
movie burn-ins, plus poster and comparison stills. `--preview` assembles stills
only. [scene-manifest.json](scene-manifest.json) records the built placements and
stabilization contract; numeric coverage checks do not establish artistic acceptance.

Review assets live in [review/](review/):
[movie](review/jangala-parallax-window-test.mp4),
[poster](review/poster.jpg) and [contact sheet](review/contact-sheet.jpg).
Assess cabin stability, layer boundaries, river registration, cloud motion and
traffic scale through the complete move. All masters and scenes are authoring-only
sources outside `mod/`; no game runtime or UI behavior changes. Video and retained
frames add disk usage separately from paintings and Blender scenes. The six
environment masters are 1672×941; the Atlas master is 1774×887. These are retained
raw generation sizes, not native 1080p painting resolution. The four-second
encoded movie is 1,398,103 bytes; this change adds no deployed mod assets.

## Retained evidence

[Scene validation](review/scene-validation.json) checks all 96 output times:
all 171 ship meshes and the local camera, root rotation and scale match the
source. The inverse scenery displacement differs by at most 0.49 mm. Maximum
cabin projection precision error is 0.023 pixels, compared with 3.016 pixels in
the [original diagnosis](review/jitter-diagnosis.json). No artificial re-entry
shake was added to this approach beat.

A shared reference point moves approximately 300, 142, 75 and 44 pixels on the
near, medium, far and hill layers respectively between source frames 120 and 168.
This verifies distinct depth rates, not individual-tree geometry.
[Media validation](review/media-validation.json) confirms a full decode, 96 unique
frames, four seconds, 1080p/24 fps, and native alpha on all environment cutouts.
Atlas alpha is recorded in its [generation metadata](traffic/atlas-painted-v1.metadata.json). Endpoint
and midpoint renders were visually checked for gaps, clipped traffic, transparent
ship artifacts and foreground alignment; these checks do not replace style review.
