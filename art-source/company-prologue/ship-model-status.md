# Cinematic ship models: pipeline status

Status: ACTIVE — the board of where every cinematic hull stands.
Written: 2026-10-16

One row per asset, against the passes of the `ship-model-pipeline` skill:
1 blockout, 2 plausible detail, 3 owner review loop, 4 real-world scale,
5 condition states, 6 texturing, 7 VFX, lighting and cinematic export.
A session that merges a pass updates its row **in the same commit**. Merge
commits and history live in Git, not here.

Legend: **done** = merged to main; **review** = merged, waiting on the owner;
**wip** = a session is working on it; **—** = not started or not needed yet.

## Ships

| Asset | Package | 1 | 2 | 3 | 4 scale | 5 states | 6 texture | 7 VFX / export | Next |
|---|---|---|---|---|---|---|---|---|---|
| Second Wind (Cerberus) | `second-wind-geometry/`, `-surface/`, `-vfx/` | done | done | done | done, 0.65 m/u (66 m) | done: derelict, patched, refit, restored (73-row scar table, incl. drive and RCS condition; restored dents faired and repainted per part) | done: all four states (base skin + base wear + state overlay: restored paint-over, scar scorch, running-engine soot) | VFX kit review (`second-wind-vfx/`: state-aware drives with RCS on the geometry anchors, haze, landing dust, seal wisps, welds, gun effects, ship lamps, four lighting presets; integrated review in `review/kit/`); per-state exports done: derelict, patched, refit, restored (`second-wind-cinematic/second-wind-states/`, verified, turn reviews, scar continuity, optional `SWFX` effects collection per state from the kit) | Owner review of the four states and the VFX kit; the Jangala C1/C5 and Corvus scripts now append `SECOND WIND \| patched`; the superseded full-surface folder has no remaining users and can be deleted. |
| Hound (A) | `hound-geometry/` | done | done | review (round 1: glazed bridge, aft cargo ramp) | done, 0.88 m/u (61 m) | — | — | — | Owner review of round 1 |
| Mudskipper | `mudskipper-geometry/`, `-surface/` | done | done | done (accepted after round 1: waist junction broken up; IoU 0.943) | done, 0.85 m/u (67 m) | not needed yet: Talon Two, Wayline's contracted gunship, flies working; Thunder Run can shoot it down, so a damaged / downed state may follow if a cinematic shows that | wip: 16 Codex part sources + 10 reuses and 7 class swatches graded on the shared harness (8 generations); apply and review next | — | Apply the skin, review coverage at 1920x1080, then owner review of the textured look |
| Hermes (the company's boat) | `hermes-geometry/` | wip (IoU 0.969) | wip | — | provisional | reserved: wrecked, patched | — | — | Pass 2 detailing, then pass 4 scale and Second Wind bay fit |
| Legion (XIV) battlecarrier | `legion-geometry/` | done (reworked after first review) | done: every module detailed, fly-through wing hangars (IoU 0.974) | — | done, 0.61 m/u (180 m long, 46 m deep) | not needed (damage-free patrol) | — | Flight-deck, launch-point and engine anchors exported for C2 | Owner review of pass 2 (pass 3) |
| Shepherd (Morrow) | `shepherd-geometry/` | done (IoU 0.932) | wip | — | wip | named, not built (grounded/damaged, recovered) | — | Drone-bay openings and launch points exported | Pass 2 detail, then pass 4 scale |

## Fighters

Each fighter at its own scale, sized from the cockpit; all six are at pass 2 detail.

| Asset | Package | 1 | 2 | 3 | 4 scale | 6 texture | Next |
|---|---|---|---|---|---|---|---|
| Broadsword | `broadsword-geometry/` | done | done | review | done, 0.50 m/u (15.5 m) | — | Owner review |
| Talon | `fighters-geometry/talon/` | done (signed off for now: rescaled below the Broadsword, smaller canopy) | done (IoU 0.977, unchanged) | review: pass 2 | done, 0.55 m/u (12.1 m) | — | Owner review of pass 2 (pass 3) |
| Wasp (drone) | `fighters-geometry/wasp/` | done (signed off for now: straight arm edges, hooked claws, nose-down crouch) | done: arm-hinge and claw articulation (IoU 0.908, unchanged) | review: pass 2 | done, 0.50 m/u (9.8 m) | — | Owner review of pass 2 (pass 3) |
| Dagger | `fighters-geometry/dagger/` | done | done (IoU 0.959, unchanged) | review: pass 2 | done, 0.50 m/u (16.4 m) | — | Owner review of pass 2 (pass 3) |
| Longbow | `fighters-geometry/longbow/` | done | done (IoU 0.951, unchanged) | review: pass 2 | done, 0.45 m/u (15.0 m) | — | Owner review of pass 2 (pass 3) |
| Thunder | `fighters-geometry/thunder/` | done (signed off for now: follows the asymmetric sprite) | done (IoU 0.956, unchanged) | review: pass 2 | done, 0.45 m/u (18.5 m) | — | Owner review of pass 2 (pass 3) |

The common-scale lineup is `fighters-geometry/lineup.png`. Hoplon is out of
scope unless the owner asks for it.

## Standing decisions

- Every hull has its own real-life scale (`metres_per_unit` in its
  `geometry.json`). Shots derive lengths from it and never borrow another
  hull's.
- Object names are identifiers: damage, surfacing, VFX and cinematics key off
  them. Engines are always `Engine {i} aft-facing bell / mouth lip / dark
  throat`.
- The Second Wind's lifecycle contract is `second-wind-pipeline.md`.
