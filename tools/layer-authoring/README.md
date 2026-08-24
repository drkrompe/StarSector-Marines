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
- choose whether a clip is driven by time, locomotion phase, or action phase, and
  scrub the normalized driver phase to preview the exact pose sampled in-game;
- inspect or export the combined sheet; and
- create deterministic layer, Armory, retained-UI, and turret snapshots from
  the Snapshots tab without launching Starsector;
- use Ctrl+Z to undo and Ctrl+Shift+Z to redo the last edit; and
- use Save JSON or Ctrl+S to validate, confirm, and atomically replace the mod data file.

Playback smoothsteps matching layers between adjacent keyframes, including offsets,
independent scale, angle, and pivot. This makes articulated mech linkages directly
authorable: a walk keyframe can move a foot while changing the connected thigh's
angle and Y scale, and the preview shows the continuous stretch between both poses.
Frame durations weight each segment of a normalized procedural phase; they do not
force movement speed. The simulation advances locomotion phase from distance traveled
and action phase from the active use, while this document owns the sampled pose.
Secondary-use variants can author a `special` layer alongside the soldier. The
equipment definition selects the live sprite, ordinary carry/occlusion, and its
using/firing clip references; the active clip overrides the transform so AMR
recoil, smoke throws, and satchel plants can be posed as one composition.

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

| Suite | Evidence |
|-------|----------|
| `armory` | Individual loadout previews and a contact sheet |
| `layers` | One combined composition sheet per authored unit |
| `turrets` | Mount-state strips with projectile and impact effects |
| `ui` | Retained Marine Ops screens at authored viewport sizes |

Run without `-Psnapshot` to create every discovered suite, or select a
comma-separated set such as `'-Psnapshot=layers,turrets'` in PowerShell.
Outputs live beneath `build/snapshots/<suite>/`; `-PsnapshotDir=<path>`
redirects the shared output root. The layer suite writes one deterministic
combined PNG per unit under `build/snapshots/layers/`. The command replaces
matching PNGs without prompting, while the editor asks first. Neither front end
removes obsolete PNGs left by an earlier run.

### Adding a snapshot suite

Implement the shared `SnapshotSuite` contract and register the provider through
`META-INF/services`. Keep rendering deterministic, headless, and independent of
a Starsector process or OpenGL context. The existing `createSnapshots` task and
Snapshots tab discover the provider automatically; do not add a domain-specific
Gradle task or standalone preview CLI.

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
