# Fog of War — Open Stories

Status: ACTIVE — 1 deferred story, 2 proposed cleanups

Written: 2026-08-23

Read `fog-of-war-nouns.md` before changing a fog-of-war story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `time-of-day.md` | Deferred | No time-of-day or lightmap implementation is installed. Revival waits for a concrete night-raid mission and depends on observation-footprint invalidation. |
| `fog-dense-slot-lifecycle-cleanup.md` | Proposed | Dense roster swap-pop can transfer a released unit's visibility/fade row to the tail occupant; make release an explicit fog lifecycle handoff before further visibility work. |
| `fog-observation-footprint-invalidation.md` | Proposed | Cached contributor footprints refresh only on cell movement, so a stationary unit does not react to changed sight inputs or map opacity. Preserve the cadence while adding a focused invalidation path. |

Last-known-position ghosts and projectile visibility are direction, not
contracted stories. Author the proposed cleanup story before implementation.
