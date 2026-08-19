# 25 — Direct squad belief substrate

## Player-visible contract

A squad makes tactical decisions from enemies its own members have actually
seen. Direct line of sight records every visible hostile combatant as a
per-squad contact. Contacts remember the last observed cell and fade over the
same fourteen-second window as ENGAGED → SUSPICIOUS → UNAWARE alert decay.

This first vertical replaces two existing approximations:

- the single `lastSeenEnemy` stamp becomes a compatibility projection of the
  freshest believed contact for patrol, guard, mech, and objective behaviors
  that have not migrated yet; and
- Story 24 engagement discipline counts only nearby contacts this squad has
  observed. Unknown enemies no longer make a squad magically refuse pursuit.

The behavior is faction-neutral. Marine and defender squads use the same
observation, decay, density, and debug paths.

## Ownership and timing

`Squad` owns an id-keyed contact set. Each contact carries the hostile unit id,
last-seen cell, observation tick, and confidence. `SquadAlertSystem` is the
only runtime writer in this slice: it decays old contacts, records every direct
LOS observation at full confidence, then publishes an immutable snapshot
before the parallel GOAP/replan phase.

Confidence decays linearly from 1 to 0 over
`ENGAGED_DECAY_SECONDS + SUSPICIOUS_DECAY_SECONDS`. Expired contacts are
removed. Re-observation refreshes the cell, tick, and confidence. The freshest
contact (lowest unit id as the deterministic tie-break) supplies the legacy
`lastSeenEnemyX/Y` projection.

## Consumer boundary

- `HAS_TARGET` means the squad has at least one believed contact.
- `HAS_LOS_TO_TARGET` means at least one contact was directly refreshed on the
  current sim tick. The alert pass already performed the authoritative LOS
  test, so the planner does not rediscover enemies from live global state.
- `IN_RANGE_OF_TARGET` measures members against believed contact cells.
- Generic infantry engagement discipline uses believed contact positions for
  target-cluster density and safe-alternative screening. Existing tactical
  scoring for turrets, mechs, objective actions, and unsquadded units retains
  its live-world query until those consumers receive an explicit belief
  contract.

The legacy last-seen fields remain during the migration so existing patrol,
guard-post, overwatch, and mech actions do not all change in one slice. They
are derived from belief after direct observation; an audible shot may still
write an anonymous investigation bearing when no identified contact exists.

## Diagnostics

The selected squad's believed contact cells are always drawn by the debug
highlight overlay, with opacity scaled by confidence. The ordinary unversioned
squad dump includes each contact's id, last-seen cell, observation tick, age,
confidence, and whether it was refreshed on the dump tick.

## Acceptance coverage

- One squad member's LOS shares every visible hostile contact with its squad.
- Re-observation refreshes a moved contact without duplicating it.
- Confidence decays and expired contacts disappear at the alert-decay boundary.
- Belief-backed GOAP predicates do not discover an unobserved live enemy.
- Story 24 holds against an observed cluster for both factions, but does not
  count hidden enemies the squad never observed.
- Cluster dispersal in live world state does not instantly erase remembered
  density; the hold releases as the corresponding beliefs decay or a safe
  observed alternative appears.
- Overlay and dump fields expose exactly the contacts the tactical consumer
  reads, with no schema-version field.

## Out of scope

- Audio localization, anonymous/noise contacts, confidence by sensor type, or
  a `NoiseEventBus`.
- Commander briefings, cross-squad contact sharing, or radio delay.
- Commander influence heatmaps and strategic assignment changes.
- Migrating every live-world tactical query in one pass.
