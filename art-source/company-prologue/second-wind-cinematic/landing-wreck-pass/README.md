# Jangala damaged landing and port continuity

This revision addresses the requested heavier arrival: the ship should look battered and recover unevenly during its descent, while the physical docks should match the preceding forest/farm/port sequence. It derives from `../landing-refinement/landing-refinement.blend` and preserves that earlier scene and review as separate artifacts. The existing ship, industrial atlas and distant scenery textures are reused. One new localized ImageGen finish supplies only the modeled impact scars; ordinary hull panels and physical docks retain their existing finishes. Nothing under `mod/` changes.

## Scope and visual decisions

`damage_geometry.py` adds localized cratering, sacrificial armor loss, sheared/buckled edges and salvage restraints. The newly authorized geometry change includes actual concavity in selected outer-skin regions, rather than relying entirely on painted damage. The continuous occupied pressure boundary remains intact; this is not an open inhabited breach. Bays, engines, turret positions, support hardpoints and overall Second Wind identity remain the source authority. `damage-contract.json` records the affected regions, construction checks and limits.

`impact_surface.py` applies `assets/impact-surfaces-v1.png` only to the newly modeled scar meshes after damage construction and before motion. The OpenAI built-in image-generation tool used `guides/impact-surface-guide.png` as the immutable camera/layout authority and `../../second-wind-skins/arrival-heavy.reference.png` for damage material direction only. The exact prompt is `prompts/impact-surfaces-v1.txt`. Its native 1672x941 output, `exec-5b5c1565-8376-41fd-95fb-bc5a2dcfe618.png`, was copied losslessly into the retained asset; no upscale is claimed. New scar RGB does not replace ordinary hull or yard surfaces. `provenance.json` records exact input/output hashes.

`unstable_arrival.py` adds a bounded sag, uneven attitude and delayed lift recovery before the original support contact. It keeps forward arrest before vertical lowering and returns the hull level and stable on the berth. Exact roll, pitch, vertical offsets, thrust strengths and timing are owned by the current motion module and `motion-contract.json`; preview tuning must update that contract. Engine/FX anchors follow the actual moving hull. This remains authored motion with procedural volume effects, not fluid, load, thrust-control or thermal simulation.

`yard_style.py` removes the latest camera-projected photographic finish from physical yard surfaces. The buildings, roofs, gantries and utility geometry return to the preceding port's existing physical atlas materials. Berth and outer hardstand use the same concrete atlas cell and 20 m repeat as that port, with restrained roughness and aggregate. The existing safety marking, road partitions, continuous ground joins, original supports and moving component-local clamp materials remain. No photographic puddles, extra micro-fixtures or full-frame blur are used to hide the mismatch.

Only these exact distant cards retain the previous landing camera finish:

- `LANDING YARD | far forest and hills`
- `LANDING YARD | far daylight sky`

Those two exceptions preserve the already composed lush canopy and sky rather than exposing the stretched source-card appearance. They do not authorize projected photographic detail on physical docks. `yard-style-contract.json` records the exceptions, restored materials, source/reference hashes and style preview. The prior port comparison images are `../port-refinement/review/start.jpg`, `poster.jpg` and `end.jpg`.

The starboard camera and existing ship finish remain inherited: actual -Y viewpoint, bow +X projecting right, no image mirroring. The earlier 11-landmark affine surface registration and its limitations still apply; see `../landing-refinement/README.md` and its registration files. Existing RGB cannot supply all hidden faces or perfect correspondence after local deformation. This is a bounded-view damaged-arrival study, not a newly authored complete multiangle UV atlas.

## Review outputs and timing

The verified native 1920x1080 outputs are:

- `review/jangala-damaged-landing.mp4`: revised eight-second landing, 192 frames at 24 fps.
- `review/port-to-damaged-landing.mp4`: twelve-second continuity cut, using the actual previous four-second `../port-refinement/review/jangala-port-refined.mp4` followed by the revised landing.

Both encode as H.264 CRF18, yuv420p, without audio. Rendering uses Cycles, 32 samples and denoising; the render manifest records the actual device. The landing is 2.59 MB and the continuity cut is 5.19 MB. Full decode and frame counts pass. The continuity cut played through at 1x with no reported dropped frames; sampled browser frames and native approach, descent, recovery, contact and settled images were inspected. Findings and limits are retained in `review/motion-review.json`. This is a textured motion revision for user review, not runtime integration or final creative acceptance.

The explicit editorial retime is unchanged from the prior landing:

| Output seconds | Source frame | Beat |
|---:|---:|---|
| 0 | 580 | Approach |
| 2 | 624 | Forward arrest |
| 2.5 | 648 | Vertical descent begins |
| 6.5 | 744 | Support contact |
| 8 | 774 | Settled endpoint |

The unstable recovery is evaluated inside that source clock. Each encoded frame is a genuine Blender evaluation, not video interpolation. The final encoded sample is source 773.1667 at output t=191/24; `review/settled.png` separately shows source 774. The stationary camera and settled support geometry make the recovery/contact readable without camera drift.

## Regeneration and verification

Run from this directory with Blender 5.2, Python with Pillow, and FFmpeg/FFprobe on PATH. Retain the sibling source scenes, textures, earlier registration metadata and preceding port movie.

```powershell
$blender = 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe'
& $blender -b --factory-startup --python build_scene.py -- --preview
& $blender -b --factory-startup --python verify_scene.py
& $blender -b --factory-startup --python render_motion.py
python encode_review.py
```

The builder loads the saved prior landing, applies yard continuity, damage geometry, localized impact finish and unstable motion, then saves this directory's `landing-wreck-pass.blend` and `scene-manifest.json`. `--preview` renders source 580/686/744/774 stills. It does not overwrite the sibling source blend.

The combined verifier reads saved scenes and checks allowed geometry changes, retained UVs, trajectory, finite shoe/pad contacts, nozzle attachment, level settlement, stationary camera, corridor clearance, ground ownership/joins and the exact two landscape exceptions. Its report is `review/scene-validation.json`. Finite samples and conservative bounds do not establish continuous collision, structural engineering or visual acceptance.

The renderer writes 192 native PNGs and scene-bound frame hashes to `render-manifest.json`. The encoder verifies those frames, writes both movies, checks stream dimensions/count/duration and full decode, and writes `review/media-validation.json`. Review the encoded continuity cut at playback speed as well as the approach, recovery, contact and settled images. Keep visual findings separate from numerical construction/media checks.
