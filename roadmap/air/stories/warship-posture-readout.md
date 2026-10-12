# Story — Warship posture and readout (slice 1)

Status: PLANNED

Written: 2026-10-11

Read `warship-behaviours.md` (the whole design), `air-nouns.md` (**Overhead ships**) and `warship-on-station.md` first.

## Problem

A directed warship has one behaviour, whatever is happening on the map. The behaviour design needs a ship to have a *current job* that can be chosen, read and tested, before any new air rule exists. Nothing today records what a ship is trying to do, and nothing reads the air above the battle on a ship's behalf.

## Goal

The minimum that makes a ship's posture selectable and observable, reading what the simulation already has:

- a **posture** on `Warship` (AIR_SUPERIORITY, SUPPORT, INTERCEPT, BOMBARD, WITHDRAW) with a reason string and time in posture;
- a **doctrine** that assesses a snapshot and picks one at a fixed cadence with hysteresis;
- a **threat picture** built only from what exists: hostile warships, hostile armed air entities (`getAirEntityIds`, by faction and armament), and a threat a mission or scene **declares** on the ship;
- a **profile** per posture that only sets parameters of the existing machinery;
- a **readout** in the warship debug status line;
- one `BehaviorScene`.

A ship with no doctrine installed behaves exactly as today (BOMBARD).

## Scope

1. `WarshipPosture` enum and `Warship.posture()`, `postureReason()`, `postureSeconds()`; a mission may pin a posture (`posture(...)`), which stops the doctrine.
2. `AirThreatPicture`: a read-only snapshot taken once per doctrine pass: hostile ships (with hull fraction), hostile airborne armed craft (count, kind), own airborne craft (cover to protect), and declared threats. Hostile shuttles are listed separately (inbound drops), not as threats. No new simulation rule; no new air registry.
3. `WarshipDoctrine.assess(PostureInputs)`: pure function; order is WITHDRAW, AIR_SUPERIORITY, then stance (SUPPORT/INTERCEPT/BOMBARD). In this slice the stance is a plain field the fitter sets; its default is BOMBARD. SUPPORT and INTERCEPT are selectable and carry profiles but their distinguishing behaviour lands in later slices (they run the BOMBARD director until then, and the readout says so).
4. Cadence once per second, staggered by ship index; minimum dwell 8 s; enter/exit thresholds 2:1 on the air score; WITHDRAW enters below 0.35 hull and exits above 0.5 with a dwell.
5. `PostureProfile`: director area count and radius, `opportunityRange`, flux soft-limit pair, a station policy flag (AIR_SUPERIORITY holds its station and does not swing the heading for ground). Applied before `direct` in `WarshipSystem.tick`. Flux pair goes through the existing `WarshipFlux` thresholds; shield policy is a documented slot that the shields work fills.
6. Readout: `DebugWarship.statusLine` gains posture and reason; the F6/F7 manual order still takes precedence and the line says "manual".
7. `Warship.declareAirThreat(strength, untilSeconds)` for scripts and scenes; also usable by a campaign hook later ("the fleet is busy with hostile fleets").

## Out of scope

Ship weapons vs aircraft, aircraft hurting ships, ground hazard fields, ships as targets, objective bias, friendly weighting, intercept, withdraw movement, carriers, escorts, shields, command powers. All are named gaps in `warship-behaviours.md`.

## Acceptance

- `warship-posture` `BehaviorScene` with an Onslaught over a block of defenders, loops: **control** (no doctrine); **declared-threat** (threat raised at t1, cleared at t2); **hostile-ship** (a hostile warship present). Verdicts: posture reaches AIR_SUPERIORITY within a second and a dwell of the threat appearing; returns to BOMBARD after the exit dwell; transition count is bounded (no flapping); under AIR_SUPERIORITY the peak flux fraction is below the control's and area-fire rounds are fewer and none beyond `opportunityRange`; after recovery the round rate matches the control to tolerance; `postureReason` is non-empty on every transition.
- Determinism: two plays of a loop give identical transition ticks.
- Unit tests on `WarshipDoctrine.assess` only (no world): threshold hysteresis, dwell, ordering (WITHDRAW outranks air), pinned posture ignored by the doctrine.
- No change to any shipped scene's verdicts (`onslaught-flux`, `warship-point-defence`, `warship-budget`, `onslaught-district`) when no doctrine is installed.
- `AirThreatPicture` is covered by a test that proves it reads, and does not mutate, the air entity lists, and that a hostile shuttle is not counted as a threat.

## Plan

1. `WarshipPosture`, `PostureProfile`, `PostureInputs`, `WarshipDoctrine` in `battle/warship/`, with the pure-function tests.
2. `AirThreatPicture` over `getAirEntityIds`, `WarshipSystem.ships()` and declared threats.
3. Wire the profile into `WarshipSystem.tick` ahead of `direct`; add the opportunity range gate on the director's candidate filter, and the flux pair override.
4. `Warship` accessors, `declareAirThreat`, `posture(...)` pin.
5. Status line.
6. The scene, its registration (`META-INF/services` for the scene and its snapshot suite), run under `sceneEvidence`.
7. Fold into `air-nouns.md` (a short **posture** paragraph in the warship section) and update this story's row; delete this story when shipped per the roadmap rules.
