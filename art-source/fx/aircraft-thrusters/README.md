# Aircraft engine effects

`build.ps1` generates the two code-owned analytic alpha masks used by the shared
aircraft engine renderer. They are white for runtime engine-style tinting:

- `aircraft-engine-glow.png`: 64x64 compact radial falloff, transparent at the border.
- `aircraft-engine-flame.png`: 64x128 softened taper; nozzle at the bottom, tail at the top.

Both are RGBA PNGs under `mod/graphics/battle/fx`, less than 8 KiB combined.
No source game textures are edited. Flight physics and the smoothed per-engine
thrust demand remain authoritative; the renderer changes only effect dimensions
and opacity. Nozzle offsets still follow each aircraft's registered appearance.

The opt-in `createSnapshots -Psnapshot=aircraft-thrusters` suite produces a
1920x1080, 15 fps, ten-second controlled flight preview of the six existing
aircraft. Each panel uses the same 60 pixels per cell and tracks an ordinary
simulation body. The existing worked-orbit steering supplies turns, changing
pace and altitude; this is a visual demonstration, not an aircraft combat test.
Frames are written to `build/snapshots/aircraft-thrusters/frames`.

The retained `flight.mp4` is encoded from those frames by `encode-preview.ps1`.
It preserves the original 1920x1080 rendering at 15 fps. The source frames remain
build outputs, and the video does not ship with the mod.
