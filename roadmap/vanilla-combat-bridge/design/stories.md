# Vanilla Combat Bridge stories

Status: ACTIVE — open implementation and acceptance board.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `bridge-production-launch.md` | PLANNED | Replace the debug-only launcher with a mission-bound, frozen battle configuration and production session lifecycle. |
| `s3f-units-layer.md` | READY FOR ACCEPTANCE | Verify the shipped bridge unit rendering, roof/unit fades, spectator HUD suppression, and combat-world audio in live play. |
| `ground-control-mode.md` | PLANNED | Give the player an explicit ground-command interaction mode; retain contextual ship see-through as an affordance, not the whole mode. |
| `dustoff-extraction.md` | PLANNED | Add the deliberate inverse of an invasion: board and extract ground forces under the same host/sim authority boundaries. |
| `bridge-highlights.md` | PLANNED | Add bridge highlights only with a real ground-selection source; do not pass a fabricated selection state into the renderer. |
| `s3j-fx-fbo-retarget.md` | DEFERRED | Project persistent ground decals through the combat-world camera with explicit GL-state verification. |
| `vanilla-combat-hud-time-policy.md` | PLANNED | Decide and implement the supported pause/time-control policy after validating the existing ship-info suppression. |
| `proxy-lifecycle-authority.md` | PROPOSED | Make direct vanilla proxy destruction preserve sim authority instead of silently removing a still-live sim target. |
| `skybattle-fleet-control.md` | PARKED | Turn the fleet-above layer into a real contested battle with durable standoff, command, and cross-layer pressure. |
