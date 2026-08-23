# Fog dense-slot lifecycle cleanup

Status: PROPOSED

Written: 2026-08-23

Read `fog-of-war-nouns.md` before implementing this story.

## Problem

Fog visibility and fade state are keyed by dense roster slot. Releasing a unit
swap-pops the roster tail into the vacated slot, but fog receives no matching
lifecycle handoff. An unseen tail unit can therefore inherit the released
occupant's visible/fading state until a later sweep. Contributor removal is
also discovered lazily on its cohort turn, leaving its old reveal footprint in
the union longer than the entity lifecycle requires.

## Scope

- Give fog one explicit ground-roster release handoff, whether state remains
  slot-keyed or gains an entity-identity guard.
- Move or reset visibility/fade ownership when the roster compacts.
- Remove a released contributor's footprint immediately and exactly once.
- Cover direct, deferred, and duplicate-safe release paths through the same
  lifecycle contract.

## Constraints

- Keep dense reads in rendering allocation-free.
- Preserve ref-count correctness when other sources overlap the released
  contributor.
- Do not change the visible-to-fading presentation rule for a live entity.
- A duplicate release/removal must remain a no-op.

## Acceptance

- [ ] After a non-tail release, the moved tail entity retains only its own
  visibility/fade state and the vacated tail state is cleared.
- [ ] A hidden moved entity cannot draw with the released entity's state or
  alpha between release and the next visibility sweep.
- [ ] Releasing a contributor decrements its footprint immediately without
  hiding cells still covered by another source.
- [ ] Tail release, non-tail release, deferred spawn/release, and duplicate
  release behavior have focused lifecycle coverage.

## Plan

1. Define the release result/handoff needed by roster and fog.
2. Route every production release seam through it.
3. Transfer or identity-guard dense visibility state and remove contributor
   footprints.
4. Add focused swap-pop and overlap tests.
