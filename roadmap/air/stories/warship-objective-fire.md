# Story — Warship objective-aware, friendly-aware fire (slice 2)

Status: PLANNED

Written: 2026-10-11

Read `warship-behaviours.md`, `air-nouns.md` (**Overhead ships**), `warship-posture-readout.md` (this builds on its posture and cadence) and `mission-command-nouns.md` (how objectives and squads are assigned) first.

## Problem

The director fires at the densest hostile cluster in reach, wherever it is and whoever is standing in it. In a real mission that is the wrong ground at the right time: the assault is somewhere else, the dense cluster may be a decoy, and a cluster with marines in it is shelled without a second thought. The owner accepts friendly fire as a fact of the weapon and wants a ship that can still avoid the avoidable.

## Goal

Under SUPPORT (and the defending half of INTERCEPT), area choice is scored by objective proximity and penalised by friendlies, using only data the simulation already holds. A ship still issues ordinary `FireOnArea` orders.

## Scope

1. **Stance.** A ship's stance (attack, defend, none) is set by its fitter. Default for a fitted ship is the side's: `MARINE` and `ALLY` attack, `DEFENDER` defends; a mission may override. No new mission-type data is added to the simulation.
2. **Objective weight.** An `ObjectiveBias` read from `BattleView.getObjectives()` and the tactical map: for an attacker, the live objective's ground and the nearest friendly assault squad's lead; for a defender, `mustHold` nodes, the command post and the approaches to them. Distance-decayed weight; zero when the ship has no stance or no objective.
3. **Friendly weighting.** `WarshipDirector.select` receives friendly positions beside hostile ones. A candidate's score is `hostiles + objectiveWeight - friendlyPenalty * friendlies`, with a hard refusal when friendlies inside the area plus its blast margin exceed a stated share of hostiles. Friendly warship shells in flight count as hazard at their landing point. When every candidate is refused the ship holds fire on ground and the readout says it is holding for friendlies.
4. **Danger-close.** A mission-set flag on the ship lifts the refusal (penalty remains). Default off.
5. **Determinism.** Ties by distance then id; no new random source.
6. **Posture integration.** BOMBARD is unchanged and is the control. SUPPORT uses the weighted director; the posture readout carries the reason ("objective bias", "held for friendlies").

## Out of scope

Intercepting shuttles, ships as targets, aircraft, carriers, withdraw. Friendly *ship* avoidance beyond shells already in flight. A command-power request for fire.

## Acceptance

- `warship-objective-fire` `BehaviorScene`: a block near the objective and a denser block away from it; own marines inside a third block. Loops: **control** (BOMBARD), **attacker** (SUPPORT), **defender** (flipped stance), **danger-close**. Verdicts: the attacker picks the objective-side cluster over the denser decoy; no round lands within the blast margin of a friendly when a clean alternative exists; with only dirty candidates the ship holds fire and says so; danger-close fires and the friendly casualties are non-zero (proving the flag is the only difference); the control still picks the densest regardless.
- Unit tests on `WarshipDirector.select` with friendlies on bare positions: penalty, refusal share, margin, tie order.
- No change to `onslaught-district` verdicts with BOMBARD.
- Rounds held for friendlies are counted and reported, so a ship starved in a crowded fight is visible, not mistaken for broken.

## Plan

1. Extend `select` to take friendly positions and a weight function; keep the old signature as the BOMBARD path.
2. `ObjectiveBias` from objectives and the tactical map; stance on `Warship`.
3. Hazard from friendly shells in flight (`WarshipSystem.shells()`).
4. Posture profile switch: SUPPORT uses the weighted path.
5. The scene, registration and `sceneEvidence` row.
6. Fold the standing behaviour into `air-nouns.md` and `warship-behaviours.md`; delete this story when shipped.
