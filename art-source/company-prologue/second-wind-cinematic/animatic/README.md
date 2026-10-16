# Jangala surface greybox animatic

Editable, silent, untextured staging study for the Second Wind arrival. The
rejected orbital movie is not the reference for this surface arrival. The
accepted V3 ship's 135 meshes are reused from
`../../second-wind-geometry/second-wind-geometry.blend`.

The sequence is 65 seconds: 780 motion frames at 12 fps, delivered as
1920×1080 H.264 at 24 fps by duplicating frames. Six shots cover entry,
window reveal, managed land, port frontage, exterior approach and landing.
Frames 217, 361 and 505 are intentional time ellipses, not continuous travel.
Editorial burn-ins reserve the optional Castle / existing Nessa memory cue
for shots 03–04. There is no audio synthesis or game UI change.

## Scale and proposed geometry

The roughly 150 m ship uses 1.5 metres per V3 hull unit as a provisional review
scale, not canonical dimensions. A separate roughly 12 m boat proxy is a scale
study: it does not establish a vanilla Hermes ratio or show the company's boat
repaired. Protected 1.8 m workers beside the truck provide human scale.

The crew POV sits inside the actual shell behind a proposed 1.8 × 1.1 m
reinforced window. Clear glazing is omitted from the render for sightline review.
Four belly lift modules and four keel shoes are proposed landing hardware;
the four aft engines remain fixed. Surface damage is deferred: this greybox
shows the clean V3 shape and makes no claim that arrival damage was repaired.

The ship stops fore/aft at frame 624, holds through 648, lowers from 649 and
contacts the cradle at 744. Pads and shoe bottoms meet at Z=5 m with the ship
root at Z=21 m. Clamps engage during 751–774. Belly lift fades after contact
and is off at 768; this is lift shutdown, not a takeoff. The final hold shows
weight on the cradle. Contact pads and exhaust trenches occupy separate footprints.

## Build and review

Run from this directory using background Blender 5.2 (`blender` below denotes
that executable). `build_animatic.py` builds the editable scene and writes
`jangala-blockout.blend` plus `scene-manifest.json`; the manifest records scale,
axes, window placement, landing timing and editorial cuts.

```powershell
blender --background --factory-startup --python build_animatic.py -- --preview
python assemble.py --preview
blender --background --factory-startup jangala-blockout.blend --python-exit-code 1 --python validate_scene.py
blender --background --factory-startup jangala-blockout.blend --python render_frames.py -- --all
python assemble.py
```

`--preview` renders the nine representative frames. `render_frames.py` renders
the existing saved scene; `--start` / `--end` can bound a rerender. `assemble.py`
requires all 780 frames for the movie and accepts `--frames` / `--output`.
It preserves source images and uses Pillow plus the configured FFmpeg executable.
Review assets live in this directory's `review/`: the movie, `poster.jpg` and
a 1920×1080 contact sheet. Saved-scene validation writes geometry evidence under
`review/geometry-review.json`, `review/contact-detail.jpg` and
`review/yard-top-plan.jpg`; its measurements are construction checks, not approval
of the design. The JSON distinguishes original ship outer dimensions from the
main hull and records actual evaluated contact and clamp clearances.

Review the window's location within the hull, the provisional 150 m scale,
the low exterior landing camera, and the proposed belly lift / shoe / clamp
arrangement before adding textures, damage surfaces or final sound.
