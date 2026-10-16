# Space approach

Four seconds / 96 frames / 24fps / 1920×1080 / silent. Restrained fixed-aspect
starboard side view of the accepted battered Second Wind, drifting bow-first
screen right toward Jangala. This package precedes the existing entry/forest,
managed land, port and damaged landing sequence.

`build_scene.py` derives `space-approach.blend` from the retained damaged landing
scene without modifying its source. The rigid hull geometry, UVs and materials
are retained. Yard geometry and previous flight effects are removed or hidden.
The occupied pressure backing remains sealed. The four aft engines receive short
soft transparent volumes directed along local −X. No lift exhaust in space.

Camera uses the exact accepted starboard camera rotation, with orthographic
projection to preserve that bounded surface aspect through translation. Geometry
bounds are approximately 449 pixels wide throughout; start x172–621/y188–352,
end x355–804/y191–354. There is no ship or camera rotation. Ship root remains at
the inherited provisional 1.5 scale and moves from X−20 to X45.

`planet_guide.py` makes `guides/planet-layout.png`, a procedural composition guide
only. The planet circle center is (1530,1070), radius1170px. The generated native
1672×941 master is retained losslessly at `assets/planet-plate.png`, mapped over
the normalized full frame without creating an upscaled image copy. The plate
is placed well behind the geometry. Independent sparse stars occupy open space.
`provenance.json` records references, hashes and exact prompt location.

Run Blender in background with `--factory-startup --python build_scene.py`.
`render_preview.py` renders start/mid/end at output resolution after GPU
coordination; `render_motion.py` is prepared for the later full sequence.
All three output-resolution previews with the generated planet were inspected.
This is a textured motion study; full movie playback review remains separate
from the finite geometry/UV and framing checks in `verification.json`.

`review/jangala-space-approach.mp4` contains the complete silent four-second
review, 96 unique source frames at 24 fps, 1920×1080, H.264 CRF 17. Actual size is
821,312 bytes. `render-manifest.json` pins the immutable scene hash and every
source-frame hash; `media-manifest.json` records those checks and strict decode.
Parent production review owns the complete 28-second cut playback observations.
`planet-registration.json` measures broad limb agreement: sampled threshold
crossings differ by 1.46–9.46 output pixels. Atmospheric glow and brightness mean
this is not exact pixel or geography correspondence.
