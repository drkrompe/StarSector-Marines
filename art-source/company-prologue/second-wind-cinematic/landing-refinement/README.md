# Jangala opposite-side landing

> Historical study: superseded encoded movies were retired during the 2026-10-16 cleanup.
> Source art, prompts, Blender scenes and reference stills remain. Movie sizes,
> playback observations and media receipts below describe the original exports;
> rebuild or recover them before running these historical media checks. See the
> [current cinematic index](../README.md) and [cleanup record](../../cleanup-record.json).

A bounded, textured eight-second landing study built from the accepted Second Wind daylight proof and the previous working-port scene. The latest user direction selects the opposite, starboard camera: bow right and a left-to-right approach. This supersedes the storyboard's earlier same-side direction for this study; the storyboard itself is unchanged. Nothing under `mod/` is changed.

The fixed perspective camera is at `(20,-235,67)`, looking at `(0,0,32)`, with a 32 mm lens and 36 mm sensor. Ship axes remain +X bow, +Y port, +Z up. This is an actual -Y view, not a mirrored image; the horizontal service pod remains on the far port side. The 171 original hull meshes and source flight/support actions are retained.

## Sources and surface coverage

`../second-wind-surface-proof/second-wind-daylight-landing-proof.blend` supplies the accepted ship, damage geometry, port finish and landing mechanics. `../port-refinement/port-refinement.blend` supplies 756 physical industrial and road-traffic objects. Its animated inverse-world root is discarded; source-local assets are translated by `(850,240,-0.4)` metres. The reserved landing/approach rectangle is X[-300,150], Y[-110,110]. The imported geometry's nearest Y is 171.5 m, giving 61.5 m clearance beyond that boundary. The full motion verifier separately samples the edited poses.

The original apron owns its footprint; neighboring tessellation excludes it and joins its Z=0 edge. Pad caps own their contact footprints, with body-top rims replacing the previous coplanar center. Original support/clamp object transforms are retained. Eight moving clamp slides/wedges use restrained oxide/gunmetal BOX textures in each component's own coordinates, so their weathering travels with their original actions. Retained sky and distant forest art provide a low horizon beyond the physical district.

The OpenAI built-in image-generation tool produced two native 1672×941 assets: `assets/starboard-surface-v1.png` and `assets/yard-finish-v1.png`. Exact submitted prompts are retained under `prompts/`; ordered image roles, dimensions and SHA-256 hashes are in `provenance.json`. The guides are native 1920×1080 renders from the actual scene.

The generated starboard image changed its framing. An affine fit from 11 structural landmarks corrects the stored mesh-corner UVs: RMS 4.19 native pixels, maximum 7.34 pixels. See `guides/starboard-image-registration.json` and `guides/starboard-surface-registration.json`. This measures global structural correspondence; it does not prove exact alignment of every invented scratch, panel or fastener. The accepted port finish and neutral fallback remain on uncovered faces. This is single-aspect coverage, not a complete multiangle ship atlas.

`yard_finish.py` projects the new yard finish from the immutable actual camera onto static scenery. Buildings, pavement and near contact structures remain 3D; moving clamps, ship, traffic and effects are excluded from that static projection. Some generated fine details exist only in the surface art. A changed camera requires a new guide/registration review.

## Motion and evidence

The edit deliberately changes timing. At 24 fps, 192 distinct 3D scene evaluations use these output-second/source-frame knots:

| Output seconds | Source frame | Beat |
|---:|---:|---|
| 0 | 580 | Forward approach |
| 2 | 624 | Forward motion arrested |
| 2.5 | 648 | Hold ends; vertical descent begins |
| 6.5 | 744 | Shoes contact supports |
| 8 | 774 | Settled editorial endpoint |

The last encoded sample is output t=191/24 seconds, source 773.1667; the separate `review/settled.png` is source 774. Forward arrest precedes vertical lowering. Lift shuts down at source 758 after contact. This is an explicit editorial retime of original actions, not original elapsed time or interpolated/duplicated video.

Thrusters and ground dust use bounded procedural volumes. They are visual effects, not fluid dynamics, exhaust engineering or a heat simulation; heat refraction is not implemented. The final review target is native 1920×1080, Cycles 32 samples with denoising and OptiX where available, 24 fps, H.264 CRF18, eight seconds, no audio. The render manifest records the actual device, scene hash and individual frame hashes; media validation binds the encoded movie to that manifest. Visual review is recorded separately rather than inferred from construction checks.

## Regeneration

Run from this directory with Blender 5.2, ordinary Python with Pillow, and FFmpeg/FFprobe on PATH. The retained source blends and referenced sibling assets must remain available. These commands intentionally rebuild derived outputs; do not run them against an unsaved interactive Blender scene.

```powershell
sh ../../../blender.sh -b --factory-startup --python build_scene.py -- --yard-finish --preview
sh ../../../blender.sh -b --factory-startup --python verify_scene.py
sh ../../../blender.sh -b --factory-startup --python render_motion.py
python encode_review.py
```

`build_scene.py` loads the saved source proof, configures the starboard view, applies registered ship art, reuses the district, adds effects and the fixed yard finish, then saves `landing-refinement.blend` and `scene-manifest.json`. `--preview` renders source 580/648/744/774 review stills. `verify_scene.py` reads saved scenes without saving or rendering and writes construction evidence. `render_motion.py` produces the 192 native frames and `render-manifest.json`; `encode_review.py` verifies their hashes and dimensions, encodes the movie, checks its stream and full decode, and copies selected native review stills.

The submitted guides are retained inputs. For a deliberately new starboard guide, `ship_view.py` can be invoked with the source blend supplied to Blender before `--python`; it loads the current scene and exports its frame-744 guide. The yard guide is generated by `build_scene.py -- --yard-guide` before applying the optional finish. Regenerating either guide changes provenance: preserve the existing guides for this accepted generation, and remeasure/review registration before using changed guides. Image generation is not an automatic deterministic build step; retain the current native outputs and exact prompts.

`provenance.json` binds source blends, modules, native artwork, exact prompts, guides, references and registration inputs. `scene-manifest.json`, `render-manifest.json`, and the review validation files own the final scene/render/movie bindings and verification results.
