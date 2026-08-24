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
- drag the gold pivot handle to rotate, use Ctrl-wheel, or enter an exact value
  in the inspector;
- adjust pivot, visibility, z-order, source sprite, and frame duration;
- duplicate animations or keyframes, set transition timing and looping, and use Play
  to inspect the selected clip without crossing into another equipment variant;
- inspect or export the combined sheet; and
- create deterministic layer, Armory, retained-UI, and turret snapshots from
  the Snapshots tab without launching Starsector;
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
Ordinary transform saves replace only changed JSON scalar values, retaining the
document's existing whitespace, compact arrays, number spelling, key order, and
Unicode text. Closing the workbench also stops its preview timer so the Gradle
launcher exits with the application.

The Snapshots tab uses saved repository data. Save pending layer edits before
creating layer snapshots; replacing existing PNGs requires confirmation in the
editor. Rendering runs in the background so animation and editing controls do
not freeze while a suite is being created.

For a non-interactive evidence pass, use the same snapshot catalog through the
repository command:

```powershell
.\gradlew.bat createSnapshots -Psnapshot=layers
```

Run without `-Psnapshot` to create every discovered suite, or select a
comma-separated set such as `'-Psnapshot=layers,turrets'` in PowerShell.
Outputs live beneath `build/snapshots/<suite>/`; `-PsnapshotDir=<path>`
redirects the shared output root. The layer suite writes one deterministic
combined PNG per unit under `build/snapshots/layers/`.

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
