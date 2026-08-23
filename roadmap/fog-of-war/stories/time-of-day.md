# Time of day

Status: DEFERRED

Written: 2026-06-01

Updated: 2026-08-23 — migrated after the dormant lighting experiment was removed; waits for a concrete night-raid mission.

Read `fog-of-war-nouns.md` before implementing this story.

## Intent

A night raid should make the player want to finish before dawn. Darkness
tightens player vision; the visible approach of dawn is a diegetic clock; and
crossing the threshold authorizes defender reinforcements. A mishandled ambush
therefore turns into a harder daylight fight without relying on an arbitrary
HUD countdown.

Time of day is a gameplay value with presentation, not a renderer effect that
other systems infer. Mission policy owns the start state and dawn consequence;
fog consumes a sight multiplier; reinforcement owns delivery.

## Current state and dependencies

No time-of-day or lightmap implementation is installed. The former ambient
lightmap experiment was deleted because every shipped battle bypassed it at
DAY. Its code is recoverable from Git, but it is not the default design.

Reinforcement trigger/means orchestration now exists, so that old blocker is
gone. Revival instead waits for a concrete night-raid mission that fixes the
cycle length, start phase, dawn consequence, and required presentation. It also
depends on `fog-observation-footprint-invalidation.md`, because a stationary
observer must react when the time multiplier changes its sight range.

## Scope

- Add one simulation-time battle clock with authored day, dusk, night, and dawn
  phases suitable for a selected mission.
- Expose the current phase and transition events to mission, fog, and
  presentation consumers without duplicating clocks.
- Apply the phase's player-vision multiplier through the normal sight-input and
  fog invalidation seam.
- Present the cycle in both battle hosts using the narrowest viable ambient
  treatment; do not assume the deleted lightmap architecture must return.
- Post one dawn reinforcement request through the existing reinforcement
  service when the selected mission authorizes it.

## Constraints

- Advance on deterministic simulation time: pause freezes the clock and battle
  speed scales it with the rest of the simulation.
- Keep AI perception independent unless a separate gameplay decision changes
  it; this story modifies player-visible fog.
- A disabled or day-only configuration preserves current rendering, vision,
  and reinforcement behavior.
- Dawn is an edge-triggered event, not a per-tick condition that can post
  duplicate waves.
- Both render hosts read the same clock and ambient state.

## Acceptance

- [ ] The chosen mission supplies its start phase, cycle timing, and dawn
  policy; battles without that policy behave exactly as they do now.
- [ ] The current phase is simulation-owned and observable without reading
  renderer state.
- [ ] Night changes player vision through the normal sight inputs, and a
  stationary contributor's footprint updates by the next vision cadence.
- [ ] The phase transition is legible in both presentation hosts.
- [ ] Crossing dawn posts at most one authorized reinforcement request through
  the existing reinforcement service.
- [ ] Pause/speed behavior and deterministic transition timing are covered by
  focused tests.

## Plan

1. Select the mission and lock its time curve, start phase, ambient treatment,
   vision multiplier, and dawn wave policy.
2. Add the simulation-owned clock and phase-transition contract.
3. Wire shared presentation in both hosts.
4. Apply fog-range changes through the invalidation seam.
5. Add the one-shot dawn reinforcement trigger and focused acceptance coverage.
