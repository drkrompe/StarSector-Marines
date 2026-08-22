# S3 — Per-soldier combat telemetry

> Track what each marine actually did, so progression can be earned and
> balance can be measured instead of guessed.

**Status:** not started. No dependencies. **Unblocks S4, S8, and S9** — the
highest-leverage story in the track after S1.

## Problem

Nothing a marine does during a mission is recorded. Post-mission XP is a
flat per-survivor payout by risk level, identical for the marine who
cleared a building and the one who never had line of sight. And because no
history exists, every tuning decision — including S1's lethality pass — is
made against intuition rather than data.

## Goal

Two products from one mechanism:

1. **A reward input** — per-soldier performance that S4 converts into
   experience.
2. **A balance artifact** — retained career statistics that make questions
   like "what is the real landed-round rate at Milspec versus Service"
   answerable from a save rather than from a stopwatch.

## Design

### In battle

A `CombatTelemetryService` in the battle tier, following the shipped
convention: **Services own state, Systems are stateless consumers**
(see [[battle_services_systems]]).

Counters per entity, v1 set — keep it small and defensible:

| Metric | Why |
| --- | --- |
| `roundsFired` | Burst-aware: count rounds, not trigger pulls |
| `roundsHit` | With `roundsFired`, gives the real landed rate |
| `damageDealt` | Separates "hits a lot for nothing" from real output |
| `damageTaken` | Exposure proxy |
| `kills` | The legible number a player wants to read |
| `secondaryUsed` | Rocket/grenade expenditure, for economy tuning |

Deliberately deferred to a later slice: assists, range-bucketed accuracy,
time-under-fire, cover uptime, distance moved. All are valuable for
balance; none are needed for S4 or S8, and each costs a hook.

### The lifecycle trap

Telemetry is a **lifecycle-stable capability**, not a live-only one — a
marine's stats matter *most* when they are killed. Per
[[feedback_components_by_capability_not_store]], it must survive the
alive-to-dead transition and be readable through `DamageResolver.resolve()`
and the death-dispatcher drain. Do not attach it to a live-only component
that gets dropped on release.

Note also [[dense_registry_swap_pop_trap]]: any end-of-battle gather over
telemetry must snapshot before kills, or sort by id to recover order.

### Crossing the seam

The battle world is ephemeral and never serializes ([[battle_transient_no_save_load]]).
Telemetry crosses to the campaign as **plain data**:

1. At mission end, gather telemetry keyed by `MarineLoadout.campaignSoldierId`.
   Entities with a null id — defenders, employer militia, civilians — are
   collected for the debug readout and then **discarded**, never written
   back.
2. Carry the map on `MissionOutcome`, alongside the existing frozen
   disposition and fireteam context. It must be frozen at outcome time for
   the same replay-determinism reason the rest of `MissionOutcome` is.
3. `MarineRoster` accumulates it into a persisted per-soldier career
   record.

### Career record

A new serializable `SoldierCareer` on `MarineSoldier`, following the
`readResolve` legacy-repair pattern every other persisted marine type uses:

- `missionsDeployed`, `missionsWon`
- `roundsFired`, `roundsHit`, `damageDealt`, `damageTaken`
- `kills`
- `timesWounded` (WIA count), `daysInService`

Lifetime totals only. Per-mission history is a bigger commitment (save
size, UI surface) and is not needed for the consumers in this track — flag
it as a follow-up if the debrief ever wants a timeline.

## Out of scope

- Converting any of this into XP. That is
  [S4](s4-performance-derived-experience.md).
- Any player-facing presentation. That is
  [S8](s8-roster-legibility.md) and [S9](s9-in-battle-quality-conveyance.md).
  This story ships a **debug readout only**.
- Captain-level telemetry. Captains do not fight as entities today.

## Acceptance

- Counters are correct under burst fire, secondary fire, AoE, and the
  ballistics catch/transfer paths — a round that misses its target and
  catches another body must attribute to the shooter.
- Telemetry survives the death path: a KIA marine's stats reach the
  campaign intact.
- Employer and defender personnel never acquire campaign career records —
  the same boundary invariant `campaign/personnel/` already enforces for
  identity.
- Replay-safe: computing an outcome twice yields identical telemetry.
- A **debug telemetry readout** exists (battle debug panel and/or a
  post-battle log dump) covering *all* entities including defenders. This
  is the balance artifact and is a deliverable.
- Legacy saves load with empty career records and repair cleanly.

## Open questions

- Attribution for AoE and friendly fire: does a rocket that kills three
  count three kills, and does a friendly-fire kill count at all? Leaning:
  yes to AoE kills, and friendly-fire damage tracked separately rather than
  netted into `damageDealt`, so it stays visible.
- Does `damageDealt` count damage to walls and emplacements? Leaning: track
  structural damage as its own counter so anti-materiel work is visible and
  does not inflate anti-personnel output.
