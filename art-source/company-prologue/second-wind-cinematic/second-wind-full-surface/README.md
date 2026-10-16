# Second Wind all-face surface asset

Reusable relightable Blender material pass on the retained damaged Second Wind.
This package does not update an actual space shot. The accepted structure and
damage come from `../landing-wreck-pass/landing-wreck-pass.blend`; the saved asset
contains the ship alone, with its inherited provisional scale of 1.5 metres per
hull unit. Axes remain +X bow, +Y port, +Z up.
Append the single `SECOND WIND | full surface asset` collection to reuse the
complete hierarchy and packed surface art.

All 196 physical meshes retain their exact local geometry and root-relative
placement. Every mesh corner receives explicit overlapping tiled `SurfaceTileUV`
coordinates and `SurfaceRestPosition`, `SurfacePlateVariation`, `SurfacePatina`
and `SurfacePanelGrid` attributes. Four existing 627×627 neutral ImageGen material
tiles are copied into `assets/` and packed into the asset. They repeat every six
hull units, or nine provisional metres. Stored coordinates keep coating variation,
grime, abrasion, bump, roughness and metal response attached to the ship during
translation and rotation. Material panel joints apply only to eligible broad
shell parts; existing physical armor supplies its own silhouette.
Bare oxide shell surfaces receive UV-cell cream and gunmetal repair batches,
a dark underside keel strip and a stern service core through color and metallic
response only. Original outer armor material roles are retained.

Previous port, starboard and impact camera-RGB material chains are removed. This
is an all-face Blender material asset with intentionally reused tile UVs. It is
not a unique UV-packed PBR texture atlas. Tile RGB approximates neutral albedo;
roughness, metallic and normal response are authored rather than measured. Bow,
stern and belly retain simpler source geometry, and the deep modeled craters
remain concentrated on starboard. This is not a complete close-up modeling pass.

No new ImageGen generation was performed for this package. The exact inherited
prompt is `../second-wind-surface-proof/prompts/arrival-material-atlas-v1.txt`;
its original material/damage reference and native image provenance are recorded
in that package's `provenance.json`. The native atlas is 1254×1254, yielding four
lossless 627×627 quadrant crops. Its prompt's requested resolution and physical
size do not establish the delivered dimensions or this package's repeat scale.
`provenance.json` binds the reused inputs and current asset; `asset-manifest.json`
records every retained mesh and material assignment.

Run these commands from this package directory using Blender on PATH (or replace
`blender` with the installed executable). Run render processes sequentially.

```powershell
blender --background --factory-startup --python-exit-code 1 --python build_asset.py
blender --background --factory-startup --python-exit-code 1 --python verify_asset.py
blender --background --factory-startup --python-exit-code 1 --python review_asset.py -- --stills
blender --background --factory-startup --python-exit-code 1 --python review_asset.py -- --motion
python encode_review.py
```

`verify_asset.py` checks saved geometry hashes, hardpoint/pivot placement, all
corner coordinates, packed image bytes and the material graph. It exercises ten
finite translations/rotations and checks stored-coordinate invariance. The
complete collection is also appended into a factory-clean scene, verifying all
geometry, coordinates, material roles and packed images without linked external
datablocks. The
current structural result is in `review/asset-validation.json`; it does not
constitute rendered art acceptance, measured PBR fidelity or seamlessness proof.

Review uses an independent dark neutral studio, an orthographic camera and four
large area lamps, with Cycles OPTIX, 24 samples and denoising at 1920×1080. Six
stills show starboard, port, bow-quarter, stern-quarter, top and underside. The
eight-second motion sequence renders 192 distinct samples at 24 fps, turning the
actual ship through a continuous 360° yaw with ±15° pitch and ±4° roll against a
static camera and lighting. Bounds are fitted across the complete turn. Authored
child `hide_render` flags are preserved; no yard scenery or labels enter renders.
The 360° endpoint is frame 193 and is excluded from the encoded loop.

For a small internal selection, add `--preview` to either review command; stills
also accept `--views starboard,bow-quarter`. Preview manifests cannot be encoded
as final evidence. `python encode_review.py --stills` validates the six final
stills and assembles the 1920×1080 contact sheet without encoding motion.

Each render immediately records ordered frame hashes and before/after source
asset and review-scene hashes in `review/`. The encoder verifies those bindings
and current frame bytes before and after encoding, requires unique frames, then
checks silent H.264 1080p/24 fps, 192 frames, eight seconds, faststart atom order
and strict full decode. `review/media-validation.json` records the completed
4.74 MB movie: 192 frames, eight seconds, 1080p/24 fps and a passing strict decode.
`review/motion-review.json` records separate inspection of the six native stills,
four motion-frame samples and complete browser playback at 1x with no reported
dropped frames or playback error. No missing coverage or obvious projected-detail
mismatch was observed in those samples. This is a first all-face material pass:
regular cells on the stern/belly and simple housings remain visibly coarser than
the armored flanks. It is not final cinematic style acceptance or an exhaustive
frame-by-frame visual review.

The packed asset, source scripts, input tiles and manifests are retained for
reproduction. Rig snapshots and frame sequences under ignored `build/` are
ephemeral render-run evidence; their hashes bind the corresponding manifests
but are not portable replacements for the saved source and scripts. Final review
outputs and receipts live under `review/`.
