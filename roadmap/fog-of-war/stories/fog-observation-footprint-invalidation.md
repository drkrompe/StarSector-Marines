# Fog observation-footprint invalidation

Status: PROPOSED

Written: 2026-08-23

Read `fog-of-war-nouns.md` before implementing this story.

## Problem

A cohort contributor currently skips shadowcasting whenever its cell has not
changed. Its cached footprint can therefore outlive changes to sight range,
air clearance, or relevant map opacity. The planned time-of-day multiplier
would expose this immediately: stationary units would keep their old daytime
footprints at night.

## Scope

- Detect or explicitly mark changes to contributor sight inputs.
- Provide one invalidation seam for relevant opacity changes if dynamic map
  opacity is introduced.
- Recast dirty stationary contributors on their next eligible vision cadence.
- Preserve the no-change stationary fast path.

## Constraints

- Keep cohort dispatch and its bounded per-tick work model.
- Replace the old footprint before adding the new one so reference counts stay
  correct under overlapping sources.
- Do not add per-tick allocations or make presentation code own invalidation.
- Temporary sources retain their existing rebuild-as-a-set lifecycle.

## Acceptance

- [ ] Changing a stationary contributor's vision range or air clearance
  updates its footprint by the next eligible vision cadence.
- [ ] A relevant opacity revision can invalidate affected cached footprints
  through an explicit service seam.
- [ ] An unchanged stationary contributor still skips redundant shadowcasts.
- [ ] Shrinking and growing overlapping footprints preserve the reveal union
  without underflow, stale cells, or loss of another source's reveal.
- [ ] Focused coverage pins range, air-clearance, and overlap behavior.

## Plan

1. Choose a low-cost input signature or dirty-generation contract.
2. Store the minimum contributor cache metadata needed to detect change.
3. Recast dirty entries within the existing cohort cadence.
4. Add the map-opacity invalidation seam and focused footprint tests.
