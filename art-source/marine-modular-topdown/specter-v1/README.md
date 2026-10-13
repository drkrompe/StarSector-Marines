# Specter first-pass retained layers

`make_guide.py` creates the transparent 1920x1080 geometry plate and
`geometry.json`. North is the top of the canvas. The body socket is forward;
the compact power pack is behind it at the bottom. The single symmetric boot
is instantiated independently for the left and right feet by runtime authoring.

ImageGen edits `guide.png` using the Specter concept as material/design reference
only. `source.png` is the retained raw generated source with its original alpha,
not an alpha-cleaned derivative. Retain its exact prompt. A second classification-only ImageGen edit marks
paintable casing uniform magenta, preserving protected hardware, lamps, joints,
vents, cavities and outlines. Retain it as `material-id.png` with its prompt.
The classification never supplies shipped geometry or RGB.

The material-ID pass also changed each component's placement and dimensions.
Each `material_bounds` is independently affine-registered onto that part's
measured source content bounds. Registration scale and translation, input
SHA-256 fingerprints, paint coverage and protection counts are recorded in
`export-measurements.json`. Original saturated cyan pixels and dark neutral
hardware are protected from mask spill. Measured `protected_regions` exclude
the rear vent mesh interiors while retaining paintable outer housings. The source
alpha remains authoritative; the raw source itself is never repainted or cleaned.

Run `python art-source/marine-modular-topdown/specter-v1/export_assets.py`.
Optional `--source` and `--material` select explicitly retained alternative inputs.
`source-regions.json` records measured regions in the actual accepted 1672x941
source canvas. ImageGen changed its placement and dimensions from the guide;
`geometry.json` retains the original guide contract honestly. The exporter
requires the recorded source dimensions and crops substantive alpha bounds at
threshold 24, preserving original alpha inside those crops. If geometry moves,
measure new regions and update `source-regions.json` first.
The exporter refuses empty parts and parts touching a cut boundary.

Exports target only `variants/armor/specter-heavy/`: body width 150, head width
72, symmetric foot 38x52. Default files use slate; `paints/olive`,
`paints/oxide` and `paints/sand` hold alternate palettes. `masks/` retains aligned grayscale export masks;
`export-measurements.json` records actual cuts, content bounds and output sizes.
`render_inspection.py` reproduces a disposable 1920x1080 mask-overlay and paint
inspection plate under `build/sprite-previews/infantry/`.
Nearest-neighbor sampling follows the established marine normalization.
OKLab ramps preserve source luminance variation inside the casing mask; all
protected pixels and every alpha value remain unchanged across palettes.

`build_variants.py` delegates Specter to this exporter so historical faction
masters cannot overwrite the new pass. Other family exports remain unchanged.
The historical Specter source masters remain available under `sources/faction-armor/`.

Validate independent head rotation, socket coverage, carry/aim/fire weapon
occlusion and alternating foot reveals through the layer authoring composition.
Inspect beside a regular marine at battlefield scale and at multiple bearings.
User-visible evidence uses 1920x1080 and 100% UI scaling.

`preview.png` and `preview.gif` retain the production-compositor review from
`gradlew.bat createSnapshots -Psnapshot=specter-layers`. The sheet includes
separate parts, shared weapons, independent helmet look, both walking strides,
four casing paints, a Legionary comparison, and a 24px shoulder-unit sample.
Most examples are explicitly magnified; this is not crowded-field acceptance.
Live Specter uses slate; the alternate paints are assets awaiting a campaign
cosmetic-selection design.

Armor mastering uses a 1.12 body scale at Y=-0.06sw and a 1.0 helmet scale at
Y=0.046375sw with centered pivots. The socket is 60.5/637 of body width ahead
of its center; multiplying by 1.12 preserves that registration. Moving the
pair forward clears the shared walking-foot trajectories beneath the rear pack.
These cosmetic transforms do not change collision, targeting, or armor values.

## Moving battle comparison

`gradlew.bat createSnapshots -Psnapshot=specter-battle` records the real
`SpecterBattleScene` at normal camera zoom and four wheel notches closer.
`gradlew.bat sceneEvidence -Pscene=specter-battle` verifies actual movement,
catalog identities, and shots without rendering. Both operate on disposable
battle state; no campaign save is loaded.

Retained videos, full-color stills, and verdicts are under
`roadmap/progression/assets/specter-battle-*` and `specter-battle.json`.
The source GIFs remain in `build/snapshots/specter-battle/`; the compact MP4s
preserve their 1920x1080 dimensions, 151 frames, and 100ms frame cadence.
Encoding uses FFmpeg with `-an -c:v libx264 -preset slow -crf 12 -pix_fmt yuv420p
-movflags +faststart`, with no resizing or playback-speed adjustment.
The default view's 17.14px/cell corresponds to a Relief-sized 112x64 map, not
every campaign map: normal zoom fits each map to the viewport. The lane labels
identify the Hound above, Legionaries in the middle, and Specters below.
