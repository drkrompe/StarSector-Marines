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
| Second Wind (Cerberus) | `second-wind-geometry/`, `-surface/`, `-vfx/` | done | done | done | done, 0.65 m/u (66 m) | done: derelict, patched, refit, restored (78-row scar table, incl. drive and RCS condition; dorsal rips with exposed frames and piping, Engine 1 crushed and cocked inside a stove-in port engine bay, turrets 1 and 3 wrecked; patched and refit keep the derelict paint, refit plates more but stays beaten up; live conduit spark anchors in patched; restored dents faired and repainted per part) | done: all four states (base skin + base wear + state overlay: restored paint-over, scar scorch, running-engine soot) | VFX kit review (`second-wind-vfx/`: state-aware drives with RCS on the geometry anchors, haze, landing dust, seal wisps, welds, intermittent conduit sparks on the scar spark anchors, gun effects, ship lamps, four lighting presets; integrated review in `review/kit/`); per-state exports done on the round-3 damage: derelict, patched, refit, restored (`second-wind-cinematic/second-wind-states/`, verified, turn reviews, scar continuity; seized turrets 1 and 3, spark anchors and Engine 1's crushed mouth in the manifests; optional `SWFX` effects collection per state from the kit, with the patched conduit sparks; blends and renders built locally, not tracked) | Owner review of the four states and the VFX kit; the Jangala C1/C5 and Corvus scripts now append `SECOND WIND \| patched`; the superseded full-surface folder has no remaining users and can be deleted. |
| Hound (A) | `hound-geometry/`, `hound-surface/`, `hound-export/` | done | done | done | done, 0.88 m/u (61 m) | not needed yet (only the pirate Hound (P) is fought over in the campaign) | done: Hegemony auxiliary livery, light service wear, 97-100% textured | review: appendable `HOUND (A) \| clean` with engine, WS 001/002 muzzle, turret pivot and ramp hinge anchors; optional SWFX plumes, haze and gun rigs; 1920x1080 turn | Owner review of the export turn |
| Lasher (Hegemony escort, C1 v2) | `lasher-geometry/` | done (IoU 0.953) | done, light: glazing at the lights layer, heat vents, seams, running lights; built for 1.5-3 km formation shots | review | done, 0.65 m/u (62 m) via ship_scale.py; viewports sized in true metres | not needed (undamaged escort) | one Hegemony base material per class with light procedural wear | appendable `LASHER` collection (root `LASHER \| root`, all baked, ~8.9k tris); drive, glow, muzzle, turret-pivot and running-light anchors; built .blend on Drive `builds/lasher/` | Owner review of the blockout and scale |
| Mudskipper | `mudskipper-geometry/`, `-surface/`, `-cinematic/` | done (landing kit added after pass 6: telescoping legs with load compression, plug cargo doors, freight ramps solved to the ground) | done | done (accepted after round 1: waist junction broken up; IoU 0.943) | done, 0.85 m/u (67 m) | not needed yet: Talon Two, Wayline's contracted gunship, flies working; Thunder Run can shoot it down, so a damaged / downed state may follow if a cinematic shows that | done (accepted): 16 Codex part sources + 10 reuses and 7 class swatches; 97-100% textured (8 generations); landing kit swatch only | done (accepted): `MUDSKIPPER` collection, root, landing knobs, engine / jet / turret / bay-door / ramp / sole anchors, verified; optional `SWFX` effects via the shared kit's hull profile; flight, landed, effects and 96-frame landed turn reviewed | None: all passes done; a damaged / downed state (pass 5) only if a cinematic shows Talon Two shot down |
| Hermes (the company's boat) | `hermes-geometry/` | done (IoU 0.969) | done (IoU 0.966; ramp, gear, troop bay, side hatches as pivoted parts) | review | done, 0.11 m/u (9.0 m); fits the Second Wind's open port bay with 4 cm each side, not with a door closed | reserved: wrecked, patched (`Scar H<nn>`) | — | — | Owner review of pass 2: ramp exit clearance and the Second Wind bay depth |
| Legion (XIV) battlecarrier | `legion-geometry/` | done (reworked after first review) | done: every module detailed; level fly-through hangars with transfer to the central hold, detailed belly (IoU 0.974) | — | done, 0.99 m/u from ship_scale.py option C (292 m long, 87 m deep); C2 frames overfill, re-frame owed by the cinematic session | not needed (damage-free patrol) | — | Flight-deck, launch-point and engine anchors exported for C2 | Owner review of pass 2 (pass 3) |
| Shepherd (Morrow) | `shepherd-geometry/` | done (IoU 0.932; thickened per owner) | done: open drone workbay, every module detailed (IoU 0.935) | — | done, 0.78 m/u (67 m long, 31.5 m tall) | named, not built (grounded/damaged, recovered) | — | Drone-bay launch stations, mouths and launch points exported | Owner review of pass 2 (pass 3) |
| Onslaught (XIV), Hegemony picket flagship for C1 v2 | `onslaught-geometry/` | done (IoU 0.964; thick, deep curved hull per owner) | done: plated flanks, armour bands, stern machinery wells, terraced bridge, 24 turrets, 6 drives (IoU 0.964) | review | done, 0.77 m/u (270 m) from ship_scale.py (flagged like the Legion: 3.0x the Lasher's length) | not needed (undamaged) | — | appendable `ONSLAUGHT \| clean` on Drive (builds/onslaught/) | Owner review of pass 2 (pass 3) |

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
