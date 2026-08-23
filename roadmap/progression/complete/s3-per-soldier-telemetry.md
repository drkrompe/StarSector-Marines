# S3 — Per-soldier combat telemetry

> Track what each marine actually did, so progression can be earned and
> balance can be measured instead of guessed.

**Status:** shipped. All three slices landed: in-battle recording and
attribution, the crossing onto `MissionOutcome`, and the persisted
`SoldierCareer`. One field of the designed record, `daysInService`, is
deliberately deferred (see below). No dependencies. **Unblocks S4, S8, and
S9** — the
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

## What shipped — slice 1

Everything in-battle: recording, attribution, and the debug readout. Nothing
crosses to the campaign yet.

**Storage is a lifecycle-stable component, not a service-side map.**
`BattleComponents.TELEMETRY` registers seven columns — `roundsFired`,
`roundsHit`, `damageDealt`, `friendlyFireDamage`, `damageTaken`, `kills`,
`secondaryUsed` — added at spawn on the same gate as `COMBAT` (so presence
means "this entity's fighting is being recorded") and deliberately **absent
from `DeadBodySystem`'s corpse-remove mask**, so the record rides the death
transmute. `CombatTelemetryService` is its data owner, in the same shape as
`HubStateService` and reached as `roster.telemetry()` / `sim.telemetry()`.

Its one deviation from the sibling services: the **mutators are
presence-tolerant while the readers stay fail-loud**. The write sites are the
damage and firing pipelines, which are already reached by civilians,
wall-only detonations, and the no-attacker sentinel; making each re-derive
"is this a combatant" would put the same guard at seven call sites for
nothing. A gather that walks the wrong set still says so.

**The attacker is now threaded through the damage pipeline.** This was the
real work. `DamageService`'s SoA queue grew a fifth parallel array
(`dmgAttackerId`), `DamageApplier` grew a positional arg, and
`DamageResolver.resolve` takes an attacker id. `PendingDetonation` grew a
`shooterId` so splash damage is attributable too. The id is carried for
telemetry only and never changes what a hit does.

That choice is what makes the numbers mean anything. **Attribution has to
happen at `DamageResolver.resolve`**, because that is the only point in the
sim that knows what a hit actually cost after cover reduction, the
`damageTakenMult` armor term, and the hardened-class multiplier — the queued
damage value is a raw request, not an outcome. Two consequences fell out of
putting it there:

- **Overkill is not credited.** `applied` is clamped to the pool that was
  left, so a rocket that does 900 to a 30 HP militiaman reports 30. Without
  the clamp, "damage dealt" would have measured how much a weapon overshoots
  rather than what it produced.
- **AoE, melee, turret fire and ballistic rounds all attribute through one
  seam**, since they all end at `resolve`. No per-weapon bookkeeping.

**`roundsHit` is counted at a different seam on purpose** — the arriving
`PendingImpact`, not the damage event. One round is one landed round even
when its detonation damages six units; counting at the damage seam would
have made a rocketeer's accuracy scale with how crowded the target was.

**Uncredited damage is not silently dropped from the victim's side.** A
strafing run through `applyExternalDamage`, or the vanilla-combat bridge's
mirrored hull damage, passes `CombatTelemetryService.NO_ATTACKER` (`0L`,
safe because entity ids start at 1). Nobody is credited, but the target's
`damageTaken` still records what it absorbed.

**`CombatTelemetryReport`** gathers the columns into immutable
`CombatTelemetryRow` snapshots — a plain-data carrier with no entity handles,
which is what slice 2 will put on `MissionOutcome`. The gather is a column
walk over a `{TELEMETRY, IDENTITY}` query with no exclusion mask, so the
fallen come back with `survived = false`; it is not exposed to the dense
registry's swap-and-pop, and rows sort by entity id so two runs of the same
scenario can be diffed. `MissionResolver.compute` logs the formatted table
for every mission played, covering defenders and employer militia — the
balance artifact the acceptance asks for.

### Open questions, resolved as the story recommended

- **AoE kills count per victim.** One detonation that kills three credits
  three. The splash loop already visits each victim separately.
- **Friendly fire is a separate counter, never netted into `damageDealt`,
  and never a kill.** Netting it in would hide it, which is the opposite of
  what it is for.
- **Structural damage is deferred, not dropped.** Walls are not entities, so
  wall damage never reaches `resolve` and cannot inflate anti-personnel
  output today. Damage to *hardened entities* (turrets, hubs, mechs) does
  land in `damageDealt`. If anti-materiel work needs to be visible on its
  own, that is a column to add, not a semantic to change.

### Two things worth carrying into slice 2

- **The corpse transmute is buffered**, so a gather taken between the killing
  blow and the next tick still sees the dead as live rows. Production is
  fine — `MissionResolver` runs long after — but a test that kills and
  gathers in the same breath must advance one tick first. Same shape as
  [[battle_death_path_live_only_readable]].
- **`MissionResolver` already walks live-plus-corpses keyed by
  `IDENTITY_CAMPAIGN_SOLDIER_ID`**, for the survivor / casualty tally. Slice
  2's gather is the same walk, so it should join that one rather than open a
  second.

## What shipped — slices 2 and 3

**Slice 2 — crossing the seam.** `MissionResolver.compute` gathers once and
feeds two consumers: the whole set goes to the log as the balance artifact,
and the rows whose `campaignSoldierId` is non-null are frozen onto
`MissionOutcome.soldierTelemetry`. The boundary invariant holds by
construction rather than by a filter anyone has to maintain — only
`CampaignMarineDeployment` ever supplies a `campaignSoldierId`, so employer
militia and defenders cannot acquire a career record even though they are
recorded in battle and appear in the debug table.

`MissionOutcome` gained a canonical constructor with the telemetry map; the
previous canonical signature is now a one-line delegate passing an empty map,
so none of the other five overloads or their callers changed. The map is
defensively copied and unmodifiable, for the same replay-determinism reason
as the rest of the class.

**Correction to the slice-1 handoff.** It said slice 2's gather should join
the casualty-tally walk instead of opening a second one. On inspection that
is wrong and the two are deliberately separate: the tally defines survival as
"in the live roster", the telemetry gather as "not yet transmuted to a
corpse". They agree at mission end, but folding them together would silently
put the second definition behind the first, and one extra column walk over a
hundred entities costs nothing.

**Slice 3 — the career record.** `SoldierCareer` is a plain serializable held
by `MarineSoldier`, backfilled in `readResolve` like every other persisted
marine field, so a save written before it existed loads with a zeroed record.
`MarineRoster.applySoldierOutcome` gained an overload taking the telemetry map
and the victory flag; the old four-argument one delegates with an empty map
and `victory = false`, so existing callers keep working.

The distinction that shapes the accumulation: **`outcomes` is the deployment
manifest, telemetry is the evidence.** Every marine in the manifest gets a
deployment counted, whether or not the battle recorded anything for them — a
marine who never got a shot off was still there. Only the ones with a row get
counters.

### Deferred deliberately

- **`daysInService` is not shipped.** Nothing records an enlistment date:
  `MarineRoster.createRecruit` has no campaign day in hand, and adding one is
  its own change. The field would have had to be faked or left permanently
  zero. Follow-up: put an enlisted-day stamp on `MarineSoldier` at
  recruitment, then derive service length from `CampaignClock`.
- **Per-mission history.** Lifetime totals only, as the design says. If the
  debrief ever wants a timeline, that is a new shape, not a widening of this
  one.

### Follow-up worth recording — RESOLVED

`MissionOutcome` came out of this story with **six constructors and
thirty-six positional parameters** on the canonical one. Adding the telemetry
map kept the blast radius to two edits, but the class was past the point where
a positional constructor is readable.

Closed immediately after S3: all six constructors are gone, replaced by
`MissionOutcome.builder()` and one private constructor taking the builder.
Defaults are the "nothing to report" sentinels the outcome already normalized
to, so a caller names only the fields it knows — the two campaign-resolution
fixtures dropped from twenty-eight and thirty-five positional arguments to
thirteen and eighteen named ones, and the no-battle fixture in
`MissionOutcomeTelemetryTest` went from thirty-five arguments to six.
`Builder.mission(Mission)` copies the fourteen fields the outcome inherits
verbatim from the contract; `Builder.captain(MarineCaptain)` is null-safe.

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

All three are answered above under "Open questions, resolved as the story
recommended" — AoE kills count per victim, friendly fire stays a separate
counter and never a kill, and a structural-damage counter is deferred
because walls are not entities and never reach the damage resolver.
