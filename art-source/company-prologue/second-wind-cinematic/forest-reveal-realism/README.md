# Forest reveal realism motion proof

Derived from the retained Atlas-lift forest scene without changing its camera,
ship transforms, or Atlas trajectory. The original scene and all accepted raw
masters stay untouched. Three independent canopy depth groups replace the earlier
painted scenery: distant wooded hills at 34,000 m, medium forest/river at 10,500 m,
and foreground crowns at 5,000 m. Independent sky/cloud layers reuse the managed
land realism art. Depths remain compositing choices rather than canonical geography.

The built-in ImageGen tool extracted/completed three transparent forest layers
from the approved landscape realism master. A separate cabin reskin uses an exact
rendered geometry guide with textual worn metal direction. Exact prompts and input
roles are in generation.json and prompts/. All four raw PNGs are retained unchanged;
their dimensions and SHA256 hashes are reported in review/media-validation.json.
The cabin is projected through the existing ship-local projector; ordinary plane
UVs and the original alpha-remap materials render the environment.
The mid layer uses approved master RGB with extracted alpha. The far layer uses
master RGB below UV y=.76 and blends to extracted skyline RGB by y=.80; the near
layer retains generated completion RGB. No raw PNG pixels are altered. The sky
asset is a separate lighting/style plate rather than a registered sky extraction.

Source time remains 12 fps. Output index i=0…95 samples source frame 120+0.5*i,
then encodes at 24 fps for exactly four seconds. scene-manifest.json asserts all 96
camera, ship-root and Atlas world matrices are identical before/after the surface
conversion and retains every sample. No duplicated source frames or timeline
retiming is used.

Run build_scene.py with background Blender 5.2 --factory-startup and --preview for
five 1080p frames, or --render for all 96. Run assemble_review.py with Python/Pillow
to encode the silent H.264 review and validate dimensions/cadence/count/duration.
Run verify_scene.py with background Blender to reopen both saved scenes and prove
all 96 evaluated camera/root/Atlas matrices match and texture paths resolve. The
verification also compares vertex/topology hashes for all 171 ship meshes and
their world transforms at every sample. Media validation asserts 96 distinct
rendered pixel frames, fully decodes the movie with FFmpeg -xerror, and records
movie/scene SHA256 hashes.
The scripts retain paths to the installed runtime; no open Blender UI is changed.

From this directory in PowerShell:

```powershell
sh ../../../blender.sh --background --factory-startup --python-exit-code 1 --python build_scene.py -- --preview
sh ../../../blender.sh --background --factory-startup --python-exit-code 1 --python build_scene.py -- --render
sh ../../../blender.sh --background --factory-startup --python-exit-code 1 --python verify_scene.py
& 'C:/Users/Dillon/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' assemble_review.py
```

The encoding arguments are `-framerate 24 -i build/frames/frame_%04d.png
-frames:v 96 -c:v libx264 -crf 18 -preset slow -pix_fmt yuv420p
-movflags +faststart -an review/jangala-forest-reveal-realism.mp4`. The movie is
2,110,838 bytes. Contact sheet and review stills are presented at 1920×1080;
raw ImageGen masters remain at their native 1672×941 without upscaling.

Review: review/jangala-forest-reveal-realism.mp4, poster.jpg, start.jpg, end.jpg,
contact-sheet.jpg. This is a textured motion proof. The new images preserve broad
geography rather than pixel registration, and individual trees have no depth within
each plane. Some generated foliage completion is smoother than the approved master
at close screen footprints. The cabin projection is validated for this small camera
move, not a coherent UV reskin for other views. Endpoint and midpoint previews were
inspected at 1080p for gaps, rims, alpha fringes and Atlas clearance. The encoded
movie completed normal-speed browser playback with 96 presented frames and zero
dropped frames. [Motion review](review/motion-review.json) records the checked
movie hash and remaining limits, including the warmer cabin palette visible in
this wider framing. The [three-shot review](../sequence-review/README.md) places
the reveal beside the accepted farmland and new port excerpts.
