# Atlas lift continuity proof

This derivative preserves the earlier **painted** forest/window scene, its camera,
cabin, scenery and raw artwork. It demonstrates Atlas lifting before the
[current managed-land realism shot](../../../managed-land-realism/README.md).
It is a motion continuity proof, not a replacement for the approved realism style.
The retained `jangala-parallax-window.blend` and original review movie are inputs
and remain unchanged.

The Atlas travels bow-right/engines-left: its projected centre moves 32.22 pixels
right and 52.29 pixels upward over four seconds. All 96 full-canvas bounds stay
within the frame. Source motion combines 265 m/s along normalized reference-camera
right with 25 m/s vertical lift; the initial placement is 50 pixels lower for sky
clearance. These velocities, depth and canvas size are provisional compositing
choices, not canonical ship dimensions or performance. Observer motion contributes
to apparent screen speed. The next shot continues right by 50.28 pixels.

Run these PowerShell commands from the `parallax-window` directory:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python lift_motion.py -- --render
python assemble_lift.py
```

`lift_motion.py` without flags prepares the private
`jangala-parallax-atlas-lift.blend` and all 96 projected tracks without rendering.
`--preview` renders five sample frames. `--render` writes
`build/atlas-lift-frames/frame_0000.png` through `frame_0095.png`, using genuine
`120 + 0.5*i` source evaluations at 12 fps. The encoder presents them at 24 fps for
exactly four seconds, native 1920×1080, silent H.264 CRF19. Optional encoder paths:
`python assemble_lift.py --ffmpeg '<ffmpeg.exe>' --ffprobe '<ffprobe.exe>'`.

The assembler reuses `assemble.py` with fixed derivative destinations, checks all
96 source dimensions and unique pixel hashes, probes the movie and performs a
complete error-checked decode. It writes this directory's movie, poster, contact
sheet and `media-validation.json`. `motion-validation.json` contains the source
velocity and all 96 projected centre/canvas samples. Browser playback completed
four seconds with 96 presented frames and zero dropped frames; start/end
screenshots were checked. The assembler retains that observation only when the
encoded movie hash is unchanged. A changed movie needs a fresh playback review.
