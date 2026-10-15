# Aircraft engine effects

`build.ps1` generates the two code-owned analytic alpha masks used by the shared
aircraft engine renderer. They are white for runtime engine-style tinting:

- `aircraft-engine-glow.png`: 64x64 compact radial falloff, transparent at the border.
- `aircraft-engine-flame.png`: 64x128 softened taper; nozzle at the bottom, tail at the top.

Both are RGBA PNGs under `mod/graphics/battle/fx`, less than 8 KiB combined.
No source game textures are edited. Flight physics and the smoothed per-engine
thrust demand remain authoritative; the renderer changes only effect dimensions
and opacity. Nozzle offsets still follow each aircraft's registered appearance.
The same tight alpha masks paint once above environmental shade, keeping the
engines self-lit at night while the hull remains shaded. They do not illuminate
the ground or change fog visibility, and need no additional runtime textures.

The opt-in `createSnapshots -Psnapshot=aircraft-thrusters` suite produces a
1920x1080, 15 fps, ten-second controlled flight preview of the six existing
aircraft. Each panel uses the same 60 pixels per cell and tracks an ordinary
simulation body. The existing worked-orbit steering supplies turns, changing
pace and altitude; this is a visual demonstration, not an aircraft combat test.
Frames are written to `build/snapshots/aircraft-thrusters/frames`.

The retained `flight.mp4` is encoded from those frames by `encode-preview.ps1`.
It preserves the original 1920x1080 rendering at 15 fps. The source frames remain
build outputs, and the video does not ship with the mod.

The separate opt-in `createSnapshots -Psnapshot=aircraft-thrusters-night` suite
produces a 16-second, 1920x1080 throttle demonstration at 15 fps. Six existing
airframes retain native handling and the same 60 pixels per cell. The controlled
input holds for two seconds, applies cruise thrust through second eleven, then
brakes to a stop. Each body is integrated by `AirSteeringSystem` at 30 Hz and its
actual realized motion feeds `ThrusterFxSystem`; neither velocity nor demand is
scripted. Phase captions distinguish accelerating from steady cruise by actual
forward acceleration, and show native speed percentage. Cameras follow the body.
This is a controlled airborne throttle study rather than a complete sortie.

`NightLighting` applies darkness 0.78 through the production lighting pass over
textured stone/dirt ground. Aircraft lights and exhaust use the production layers;
no dark overlay or painted VFX is added by the presentation. Frames live under
`build/snapshots/aircraft-thrusters-night/frames`, with `representative.png` alongside.

Run `./encode-preview.ps1 -Preview night` to retain `night-flight.mp4`, or
`-Preview both` to encode both suites. The default `-Preview flight` preserves the
original command behavior. Encoding one preview does not delete or overwrite the
other. Both MP4s preserve 1920x1080 and real-time 15 fps; neither ships in the mod.
