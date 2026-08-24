# Marine / Mech Layer Authoring

This is a standalone Java2D/Swing workbench. It does not launch Starsector or
create an OpenGL context.

Launch it from the repository root:

```powershell
.\gradlew.bat layerAuthoring
```

The workbench opens `mod/data/appearance/unit-layer-layouts.appearance.json`.
Select a unit, frame, and layer, then:

- drag the selected layer to change its normalized actor-local offset;
- use the wheel to scale both axes, Shift-wheel for X only, or Alt-wheel for Y only;
- use Ctrl-wheel to rotate, or enter exact values in the inspector;
- adjust pivot, visibility, z-order, source sprite, and frame duration;
- duplicate frames to create additional animation keys and use Play to inspect timing;
- inspect or export the combined sheet; and
- use Save JSON or Ctrl+S to validate and atomically replace the mod data file.

The editor refuses duplicate unit/frame/layer ids, non-positive sizes or durations,
out-of-range pivots, missing sprites, and sprite paths outside `mod/`. Reload and
window close both guard unsaved changes.

For a non-interactive evidence pass:

```powershell
.\gradlew.bat renderLayerAuthoringSheets
```

This writes one deterministic combined PNG per unit under
`build/layer-authoring/`.

## Data contract

Offsets are measured in one unit's `referencePixels` scale. Positive X is right,
positive Y is forward/up, and positive angles are counter-clockwise. Pivots are
normalized from the source image's top-left. Each frame owns a complete ordered
layer list and a duration, keeping playback and sheet output deterministic.

The document is deliberately independent of game enums and Java class names. A
live composer can consume the same source paths and transforms, while this tool
remains a small offline authoring application.
