# Jangala forest material study

A 16-second foliage lookdev study derived from the retained
[arrival greybox](../animatic/README.md). The original
[scene](../animatic/jangala-blockout.blend) remains untouched. The study retains
the actual high-window camera for frames 120–191 (6 seconds) and low-window
camera for 396–467 (6 seconds). Frames 801–848 add a separately labelled close
forest inspection camera (4 seconds), outside the original cinematic timeline.
Sources render at 1920×1080 and 12 fps; the silent review movie duplicates those
frames to 24 fps.

[plants.py](plants.py) builds woody 3D trunks and branches with layered foliage
cards. Near trees retain that depth; distant trees use fixed side and top
impostors baked from the same 3D prototypes, rather than camera-facing billboards.
Lower understory, procedural forest-floor and water materials, and distance haze
complete the forest study. Cabin, city, weather and hull remain greybox. These
forms and materials are proposals, not canonical Jangala botany or final art.

This is a technique study: aerial crown repetition, exposed card edges and sparse
branch/understory structure remain visible. The next art pass should break up
forest boundaries and crown spacing, improve sparse frond silhouettes and add
ground cover. The plants are static; the movie tests camera parallax, not wind.

The [foliage atlas](assets/foliage-atlas.png) is the unmodified 1254×1254 RGBA
output of built-in ImageGen, with its [exact prompt](assets/atlas.prompt.txt)
retained. Its quadrants contain broad oval leaf clusters, a divided narrow frond,
a ribbed radiating fan and dense smaller branchlets. The material uses an alpha
cutout to suppress faint edge pixels; generated padding is narrower than the
requested 15% quadrant margin.

## Rebuild and review

Run from this directory with Blender 5.2 (`blender` denotes its executable):

```powershell
blender --background --factory-startup --python-exit-code 1 --python build_forest.py -- --preview
python assemble.py --preview
blender --background --factory-startup --python-exit-code 1 --python render_study.py
blender --background --factory-startup --python-exit-code 1 --python verify_scene.py
python assemble.py
```

[build_forest.py](build_forest.py) opens the base scene, rebuilds plant prototypes
and derived bakes, then saves `jangala-forest-lookdev.blend` and
[forest-manifest.json](forest-manifest.json). Preview mode renders representative
frames. `render_study.py` renders the selected motion ranges from the saved
study; [assemble.py](assemble.py) requires those 192 frames for full encoding.
It accepts `--frames`, `--output` and `--ffmpeg`, with a `FFMPEG` environment
variable override. Pillow and FFmpeg provide the review composition and encode.

The [review directory](review/) contains the
[motion study](review/jangala-forest-motion-study.mp4),
[contact sheet](review/contact-sheet.jpg) and [poster](review/poster.jpg).
The movie is 14.5 MB (13.8 MiB); FFprobe reports 384 frames at 24 fps and a
complete FFmpeg decode succeeds. [Media validation](review/media-validation.json)
records the delivery dimensions and frame count.
Review foliage depth and parallax through the actual windows, distant canopy
silhouettes, understory coverage, cutout edges and atmospheric separation.

[verify_scene.py](verify_scene.py) compares original camera animation and sampled
ship/camera/landing-support transforms, checks ship mesh geometry and texture
paths, and raycasts near-forest roots against the terrain. The retained
[verification report](review/verification.json) records its limits, including
unverified individual roots in the batched distant canopy. Five hidden entry
cloud proxies intentionally lose their timing animation for this study.

All assets here are authoring sources, outside shipped `mod/` content. The final
movie adds disk usage independently of the Blender assets; a current folder size
is not a final storage estimate. This study changes no game runtime behavior.
