# Aircraft crash breakup review

The opt-in `createSnapshots -Psnapshot=aircraft-crash` suite shows the current
Talon, Wasp, Thunder, Broadsword, Longbow and Dagger in six tracking panels at
60 pixels per cell, 1920x1080, 15 fps for twelve seconds over textured ground.
This is a controlled lethal-impact study, not a combat encounter.
The same simulation also runs a Mudskipper, with separate `mudskipper-before-contact.png`,
`mudskipper-contact.png` and `mudskipper-settled.png` views at the same 60 pixels per cell.

Each ordinary sortie begins with 45% of its native cruise velocity and uses
normal flight steering until lethal production damage at one second. After
that, production fall, ground contact and skid own all motion. No crash trajectory
or effects are painted or scripted. The same `ReviewImpactFx` drains simulation
events every 30 Hz tick that the live screen passes to the shared `ImpactFx`.
The preview checks contact-snapshot lifetime and identity through transfer to
exactly one persistent ground wreck for each aircraft.
After contact the small central flame leaves the moving hull sections visible;
the impact burst and rolling fuel explosions keep their existing scale.
Original hull colors persist during the fall; charring starts at ground contact
through the same rendering path for fighters and the Mudskipper.

Each existing rolling blast gains only two short hot streaks and one small,
dark tumbling metal chip. They inherit event travel plus bounded scatter and
lose speed through drag. Existing FX keep square aspect and zero angular
velocity by default. `build.ps1` mechanically reproduces the tiny 16x16 jagged
chip alpha silhouette at `mod/graphics/battle/fx/aircraft-metal-chip.png`; runtime
tint supplies its metal color. No destruction-variant aircraft art is required.

Frames and `representative.png` are under `build/snapshots/aircraft-crash`.
Run `encode-preview.ps1` after rendering to retain `crash.mp4` at the original
1920x1080 and real-time 15 fps. Preview assets remain outside the shipped mod.
