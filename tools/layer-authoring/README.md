# Marine / Mech Layer Authoring

This is a standalone Java2D/Swing workbench. It does not launch Starsector or
create an OpenGL context.

Launch it from the repository root:

```powershell
.\gradlew.bat layerAuthoring
```

The workbench opens `mod/data/appearance/unit-layer-layouts.appearance.json`.
Select a unit, equipment variant, animation, keyframe, and layer, then:

- drag the selected layer to change its normalized actor-local offset;
- use the wheel to scale both axes, Shift-wheel for X only, or Alt-wheel for Y only;
- use Ctrl-wheel to rotate, or enter exact values in the inspector;
- adjust pivot, visibility, z-order, source sprite, and frame duration;
- duplicate animations or keyframes, set transition timing and looping, and use Play
  to inspect the selected clip without crossing into another equipment variant;
- inspect or export the combined sheet; and
- use Ctrl+Z to undo and Ctrl+Shift+Z to redo the last edit; and
- use Save JSON or Ctrl+S to validate, confirm, and atomically replace the mod data file.

Playback smoothsteps matching layers between adjacent keyframes, including offsets,
independent scale, angle, and pivot. This makes articulated mech linkages directly
authorable: a walk keyframe can move a foot while changing the connected thigh's
angle and Y scale, and the preview shows the continuous stretch between both poses.

The editor refuses duplicate unit/variant/animation/keyframe/layer ids, non-positive
sizes or durations,
out-of-range pivots, missing sprites, and sprite paths outside `mod/`. Reload and
window close both guard unsaved changes. Frame deletion, JSON replacement, and
overwriting an exported PNG require an explicit confirmation.

For a non-interactive evidence pass:

```powershell
.\gradlew.bat renderLayerAuthoringSheets
```

This writes one deterministic combined PNG per unit under
`build/layer-authoring/`.

## Data contract

Offsets are measured in one unit's `referencePixels` scale. Positive X is right,
positive Y is forward/up, and positive angles are counter-clockwise. Pivots are
normalized from the source image's top-left. A unit owns equipment variants; each
variant owns named animation clips; and each clip owns complete ordered layer
keyframes. A keyframe's duration is the transition time to the next keyframe. Looping
clips blend their last keyframe back to their first, keeping playback deterministic.

The document is deliberately independent of game enums and Java class names. A
live composer can consume the same source paths and transforms, while this tool
remains a small offline authoring application.
