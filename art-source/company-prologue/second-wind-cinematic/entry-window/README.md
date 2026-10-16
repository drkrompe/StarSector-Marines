# Atmospheric entry window tail

Four-second contained entry tail using the accepted forest reveal's actual cabin,
camera, world animation and materials. The sealed window remains stable while a
warm exterior glow fades into luminous clouds and then the accepted daylight
forest. No new ship geometry, failures, camera shake or artwork was introduced.

Source timing is independent of sequence timing: output i=0â€¦95 samples source
73+(47/95)*i, then encodes at 24fps. This uses the valid rebased 73â€¦120 source
range, using a slight independent-clock retime to fill four seconds. The last
sample matches the accepted forest film's first sample, source120. Assembly may
keep that single shared endpoint or skip forest output0; there is no extra hold.

Four exterior cards at 800, 900, 1600 and 2300 metres separate soft amber/white
heating filaments, warm boundary glow, retained cloud filaments and a detailed
luminous cloud body. The heating filaments scroll aft and fade over the first
1.36 seconds. They are comfortably
outside the cabin and nearer than the forest's 5000m foreground. Shader noise and
opacity envelopes use the same source clock. Original cloud art is reused from
managed-land-realism/assets/clouds-realism-v1.png. These are authored 2.5D effects,
not fluid, plasma or heat-refraction simulations. Cabin light is the retained
projected daylight material throughout; the exterior glow does not relight it.

build_scene.py writes entry-window.blend and scene-manifest.json. With --preview
it renders output0,48,95 at 1920Ã—1080; render_scene.py reopens the saved blend and
renders all96 frames, or the same three with --preview. Both scripts require
background Blender5.2 with --factory-startup, and do not touch live Blender UI.
The original forest package remains unchanged. verify_scene.py reopens both
saved scenes, compares all96 camera/root/Atlas matrices and ship mesh topology,
and checks image paths. scene-manifest.json retains source hash and sample map.

Run from this directory:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python build_scene.py -- --preview
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python verify_scene.py
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python render_scene.py
```

Visual review inspected output0,20,48,95 at full1080p: clear sealed cabin rims,
exterior-only soft heating filaments, luminous cloudy midpoint and the bright
matching forest endpoint. This is a textured motion proof. EEVEE reported shadow
buffer capacity warnings during warm-layer frames; inspected cabin/sky pixels
remain readable. This effect uses emission/transparency surfaces rather than
new shadow-lit cabin materials. Final movie: review/jangala-entry-window.mp4.
Normal-speed cut playback remains with the coordinating reviewer.

Numerical verification is separate from that visual review: all96 retained
camera/root/Atlas matrices and all171 ship mesh vertex/topology hashes match the
forest source, texture paths resolve, and the shared boundary endpoint matches
the forest start in decoded PNG RGB pixels. assemble_review.py records full
FFmpeg strict decode, exact96 frames, 24fps, four seconds, dimensions, unique
pixel hashes, scene/movie hashes and the final LF-normalized script hashes.
This first run has an after-render scene hash and frame inventory; no scene
edits occurred between the accepted build and render, but no in-process starting
hash was captured. It is not a measured before/after scene-to-frame binding.
Future render_scene.py runs now capture scene hashes before/after rendering and
each PNG hash as rendered; the encoder validates that manifest when present.
The final assembly deliberately retains one repeated 1/24s boundary endpoint,
keeping both excerpts exactly four seconds long.
