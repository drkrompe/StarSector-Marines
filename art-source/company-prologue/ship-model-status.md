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
| Second Wind (Cerberus) | `second-wind-geometry/`, `-surface/`, `-vfx/` | done | done | done | done, 0.65 m/u (66 m) | done: derelict, patched, refit, restored (78-row scar table, incl. drive and RCS condition; dorsal rips with exposed frames and piping, Engine 1 crushed and cocked inside a stove-in port engine bay, turrets 1 and 3 wrecked; patched and refit keep the derelict paint, refit plates more but stays beaten up; live conduit spark anchors in patched; restored dents faired and repainted per part) | done: all four states (base skin + base wear + state overlay: restored paint-over, scar scorch, running-engine soot) | VFX kit review (`second-wind-vfx/`: state-aware drives with RCS on the geometry anchors, haze, landing dust, seal wisps, welds, intermittent conduit sparks on the scar spark anchors, gun effects, ship lamps, four lighting presets; integrated review in `review/kit/`); per-state exports done on the round-4 damage and the deepened port bay: derelict, patched, refit, restored (`second-wind-cinematic/second-wind-states/`, verified, stills review, scar continuity; seized turrets 1 and 3, spark anchors and Engine 1's crushed mouth in the manifests; optional `SWFX` effects collection per state from the kit, with the patched conduit sparks; blends and renders built locally, not tracked) | Owner review of the four states and the VFX kit; the Jangala C1/C5 and Corvus scripts now append `SECOND WIND \| patched`; the superseded full-surface folder has no remaining users and can be deleted. |
| Hound (A) | `hound-geometry/`, `hound-surface/`, `hound-export/` | done | done | done | done, 0.88 m/u (61 m) | not needed yet (only the pirate Hound (P) is fought over in the campaign) | done: Hegemony auxiliary livery, light service wear, 97-100% textured | done: appendable `HOUND (A) \| clean` with engine, WS 001/002 muzzle, turret pivot and ramp hinge anchors; optional SWFX plumes, haze and gun rigs (owner accepted 2026-10-18) | Damaged / pirate states only when a shot needs a Hound (P) |
| Lasher (Hegemony escort, C1 v2) | `lasher-geometry/` | done (IoU 0.953) | done, light: glazing at the lights layer, heat vents, seams, running lights; built for 1.5-3 km formation shots | done (accepted as built, 2026-10-18) | done, 0.95 m/u (90 m) via ship_scale.py (option C); viewports sized in true metres | not needed (undamaged escort) | one Hegemony base material per class with light procedural wear | done, effects accepted 2026-10-18: the shared VFX kit via `lasher-geometry/vfx_hull.py`, five orange LOW_TECH drives, three twin turrets, four running lights, optional `SWFX` collection beside the appendable `LASHER` collection (root `LASHER \| root`, all baked, ~8.9k tris); drive, glow, muzzle, turret-pivot and running-light anchors; built .blend on Drive `builds/lasher/` | None for C1 v2. Texture only if a shot brings her closer than mid-distance |
| Mudskipper | `mudskipper-geometry/`, `-surface/`, `-cinematic/` | done (landing kit added after pass 6: telescoping legs with load compression, plug cargo doors, freight ramps solved to the ground) | done | done (accepted after round 1: waist junction broken up; IoU 0.943) | done, 0.85 m/u (67 m) | not needed yet: Talon Two, Wayline's contracted gunship, flies working; Thunder Run can shoot it down, so a damaged / downed state may follow if a cinematic shows that | done (accepted): 16 Codex part sources + 10 reuses and 7 class swatches; 97-100% textured (8 generations); landing kit swatch only | done (accepted): `MUDSKIPPER` collection, root, landing knobs, engine / jet / turret / bay-door / ramp / sole anchors, verified; optional `SWFX` effects via the shared kit's hull profile; flight, landed, effects and 96-frame landed turn reviewed | None: all passes done; a damaged / downed state (pass 5) only if a cinematic shows Talon Two shot down |
| Hermes (the company's boat) | `hermes-geometry/`, `hermes-surface/`, `hermes-cinematic/` | done (IoU 0.969) | done, refitted in pass 3 (IoU 0.956; side boarding doors, gear, troop bay as pivoted parts) | done (accepted: stubby legs, side boarding doors, spine fairing and gun sponsons reshaped from the sprite; horseshoe arms kept) | done, 0.11 m/u (9.0 m); fits the Second Wind's deepened port bay with a door closed (0.45 m each side) | deferred until a cinematic needs them; reserved: wrecked, patched (`Scar H<nn>`) | done (accepted round 1: `hermes-surface/`, Midline courier livery, 3 part sheets + swatches, 96-99% textured, light service wear) | done (accepted): `hermes-cinematic/` appendable `HERMES` collection (root, `Hermes control` knobs gear_up / door_port / door_starboard, three turret pivots with twin muzzle anchors, drive, jet, door and gear-sole anchors; verify passes) plus the optional `SWFX` effects from the shared kit (MIDLINE plumes, jet puffs, six muzzles, nose running and work lights) | Pass 5 states (wrecked, patched) when a cinematic needs them |
| Legion (XIV) battlecarrier | `legion-geometry/` | done (reworked after first review) | done: every module detailed; level fly-through hangars with transfer to the central hold, detailed belly (IoU 0.974) | — | done, 0.99 m/u from ship_scale.py option C (292 m long, 87 m deep); C2 frames overfill, re-frame owed by the cinematic session | not needed (damage-free patrol) | — | Flight-deck, launch-point and engine anchors exported for C2 | Owner review of pass 2 (pass 3) |
| Shepherd (Morrow) | `shepherd-geometry/` | done (IoU 0.932; thickened per owner) | done: open drone workbay, every module detailed | rounds 1-3 done: rounded bow, long curved bridge hull with a raked forward windscreen and brow (replaces the invented cab and habitat pod), gantry cage replaced by the sprite's machinery drum, drone bay fully open, WS 008 recessed in a coamed well with a lined trough; round 3: chin lands on the bow, survey head on a conformal plinth, bay rim rounded with a tan lip | done, 0.78 m/u kept by the owner (67 m long, 31.5 m tall); on ship_scale.py, solves 0.74 | named, not built (grounded/damaged, recovered) | — | Drone-bay launch stations, mouths and launch points exported | Owner review of pass 3 round 3 |
| Onslaught (XIV), Hegemony picket flagship for C1 v2 | `onslaught-geometry/` | done (IoU 0.964) | done: armour tiers, stern machinery wells, terraced bridge, 24 turrets, 6 drives | review: round 3 (silhouette: chined forearm arms with armour read off the sprite, V belly and ram bow, stepped skyline to a command tower, raked stern blades and open wells; joins audited; IoU 0.960) | done, final: 0.86 m/u (301 m) from ship_scale.py, a capital like the Legion, no override (owner) | not needed (undamaged) | — | appendable `ONSLAUGHT \| clean` on Drive (builds/onslaught/) | Round 4: silhouette (tower, side profile) |
| Paragon (Tri-Tachyon battleship) | `paragon-geometry/` | review (round 2: deep main ring, armour belt, prow with two recessed capital mounts, bridge arch over the ring; IoU 0.963) | — | — | — | not needed yet | — | — | Owner review of pass 1, then pass 2 detail and pass 4 scale |

## Fighters

Each fighter at its own scale, sized from the cockpit; all six are at pass 2 detail.

| Asset | Package | 1 | 2 | 3 | 4 scale | 6 texture | Next |
|---|---|---|---|---|---|---|---|
| Broadsword | `broadsword-geometry/` | done | done | review | done, 0.50 m/u (15.5 m) | — | Owner review |
| Talon | `fighters-geometry/talon/`, `fighters-surface/talon/` | done (signed off for now: rescaled below the Broadsword, smaller canopy) | done (IoU 0.977, unchanged) | done (pass 2 accepted by the owner, 2026-10-18) | done, 0.55 m/u (12.1 m) | review: red service livery, 9 part skins + 4 swatches, 96-100% textured | Owner review of the texture (pass 6) |
| Wasp (drone) | `fighters-geometry/wasp/`, `fighters-surface/wasp/` | done (signed off for now: straight arm edges, hooked claws, nose-down crouch) | done: arm-hinge and claw articulation (IoU 0.908, unchanged) | done (pass 2 accepted by the owner, 2026-10-18) | done, 0.50 m/u (9.8 m) | review: grey-blue service livery, 13 parts (8 generated) + 4 swatches, 98% textured | Owner review of the texture (pass 6) |
| Dagger | `fighters-geometry/dagger/`, `fighters-surface/dagger/` | done | done (IoU 0.959, unchanged) | done (pass 2 accepted by the owner, 2026-10-18) | done, 0.50 m/u (16.4 m) | review: pale blue-grey livery with cream torpedo, 10 parts + 5 swatches, 97% textured | Owner review of the texture (pass 6) |
| Longbow | `fighters-geometry/longbow/`, `fighters-surface/longbow/` | done | done (IoU 0.951, unchanged) | done (pass 2 accepted by the owner, 2026-10-18) | done, 0.45 m/u (15.0 m) | review: light blue-grey livery, 12 parts + 4 swatches, 97-98% textured | Owner review of the texture (pass 6) |
| Thunder | `fighters-geometry/thunder/`, `fighters-surface/thunder/` | done (signed off for now: follows the asymmetric sprite) | done (IoU 0.956, unchanged) | done (pass 2 accepted by the owner, 2026-10-18) | done, 0.45 m/u (18.5 m) | review: sand khaki livery, 10 parts + 4 swatches, 96-97% textured | Owner review of the texture (pass 6) |

The common-scale lineup is `fighters-geometry/lineup.png`. Hoplon is out of
scope unless the owner asks for it.

## Fleet scale

| Asset | Package | State | Next |
|---|---|---|---|
| Fleet scale lineup | `fleet-scale/` | review: `fleet_lineup.py` renders every hull at its published option C scale, with real-world references (container, 747, ISS, Eiffel Tower, Nimitz, football pitch): broadside, top-down, the Hound at and in a Legion hangar lane, walkway eye level, fighters in the lane, Legion and Onslaught side by side, plus a sizes table | Owner judges the final C sizes; rerun after any rescale or new hull |

## Standing decisions

- Every hull has its own real-life scale (`metres_per_unit` in its
  `geometry.json`). Shots derive lengths from it and never borrow another
  hull's.
- Object names are identifiers: damage, surfacing, VFX and cinematics key off
  them. Engines are always `Engine {i} aft-facing bell / mouth lip / dark
  throat`.
- The Second Wind's lifecycle contract is `second-wind-pipeline.md`.
