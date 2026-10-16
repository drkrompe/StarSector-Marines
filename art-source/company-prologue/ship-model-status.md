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
| Second Wind (Cerberus) | `second-wind-geometry/`, `-surface/`, `-vfx/` | done | done | done | done, 0.65 m/u (66 m) | done: derelict, patched, refit, restored (73-row scar table, incl. drive and RCS condition) | done: all four states (base skin + base wear + state overlay: restored paint-over, scar scorch, running-engine soot) | VFX kit wip; per-state exports: patched landed (`second-wind-cinematic/second-wind-states/`), derelict, refit, restored finalizing | Switch Jangala C1 and the Corvus approach from the old full-surface asset to `SECOND WIND \| patched`; per-state effects collection once the VFX kit lands. |
| Hound (A) | `hound-geometry/` | done | done | review (round 1: glazed bridge, aft cargo ramp) | done, 0.88 m/u (61 m) | — | — | — | Owner review of round 1 |
| Mudskipper | `mudskipper-geometry/` | done | done | review (round 1: waist junction broken up; IoU 0.943) | done, 0.85 m/u (67 m) | — | — | — | Owner review of round 1 |
| Legion (XIV) battlecarrier | `legion-geometry/` | done (reworked after first review) | done: every module detailed, fly-through wing hangars (IoU 0.974) | — | done, 0.61 m/u (180 m long, 46 m deep) | not needed (damage-free patrol) | — | Flight-deck, launch-point and engine anchors exported for C2 | Owner review of pass 2 (pass 3) |

## Fighters

Blockouts at each fighter's own scale, sized from the cockpit.

| Asset | Package | 1 | 2 | 3 | 4 scale | 6 texture | Next |
|---|---|---|---|---|---|---|---|
| Broadsword | `broadsword-geometry/` | done | done | review | done, 0.50 m/u (15.5 m) | — | Owner review |
| Talon | `fighters-geometry/talon/` | done | — | review (round 1: rescaled below the Broadsword, smaller canopy) | done, 0.55 m/u (12.1 m) | — | Owner review of round 1 |
| Wasp (drone) | `fighters-geometry/wasp/` | done | — | review (round 1: straight arm edges; lands on hooked claws in a nose-down crouch) | done, 0.50 m/u (9.8 m) | — | Owner review of round 1 |
| Dagger | `fighters-geometry/dagger/` | done | — | review | done, 0.50 m/u (16.4 m) | — | Owner review |
| Longbow | `fighters-geometry/longbow/` | done | — | review | done, 0.45 m/u (15.0 m) | — | Owner review |
| Thunder | `fighters-geometry/thunder/` | done | — | review (round 1: follows the asymmetric sprite) | done, 0.45 m/u (18.5 m) | — | Owner review of round 1 |

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
