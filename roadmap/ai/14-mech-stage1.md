# 14 — Mech GOAP Stage 1

**Active.** First implementation slice of the mech-side planner migration
sketched in [`13-mech-goap.md`](13-mech-goap.md). Replaces
`MechCombatantBehavior` with a `GoapMechBehavior` that runs through the
same squad-level planner infantry uses, with a two-role doctrine split as
the visible payoff.

## What Stage 1 ships

1. `MechRole` enum on `Unit.mech` (or alongside `MechLoadoutState`) —
   `LR_SUPPORT`, `ARMORED_SUPPORT`. Two only. Recon + Assault park for
   Stage 2.
2. Mech-only squads at defender spawn — one squad per mech cluster,
   members tagged with distinct `MechRole` values.
3. `GoapMechBehavior` replaces `MechCombatantBehavior` in
   `CombatantBehavior.dispatch`. The old class stays on disk as
   reference; no A/B flag.
4. Two role-anchored mission goals + one ambient fallback (see Goals).
5. Two role-anchored actions sharing `tryFireMechWeapons` for the actual
   firing pass (see Actions).
6. Mech squads enter the existing per-squad replan loop — the
   `target.mech == null` filter in `BattleSimulation` (~line 458) goes
   away so mechs get the same WorldState build + Planner pass infantry
   gets.
7. **Retire the `rollFallbackOnHit` "flinch" for mechs.** Same gate
   change at `BattleSimulation:458` — mechs join infantry in opting out
   of the legacy per-hit retreat roll. No morale replacement in Stage 1;
   see "Mech survival" below.

## Re-imagining license

`MechCombatantBehavior` is *retired*, not preserved. The hand-authored
"close engagement = chaingun + SRM, else stand off and lob LRMs" loop is
not load-bearing. The Stage 1 GOAP actions are free to position
differently, fire differently, prioritize differently — the only thing
they preserve is the *weapon firing primitive* (`tryFireMechWeapons`)
because that's the chassis hardpoint contract, not behavior.

## Core design call — roles are *doctrine*, not *loadout*

Every mech carries all three weapons (LRM + SRM + chaingun) by design —
they're all-arounder heavy-hitters that punish the player at range *and*
up close. Roles do NOT change the weapon set.

Roles change three things:

1. **Preferred engagement band.** Where the mech *wants to be* relative
   to its target — LR Support holds 28–40 cells, Armored Support paces a
   friendly squad at 12–22.
2. **Cooldown discipline.** LR Support actively *withholds* SRMs and
   chaingun even when in-band targets exist, because firing them would
   reveal position and break the overwatch read. Armored Support fires
   whatever's hot at the closest threat.
3. **Movement anchor.** LR Support anchors to a cover cell with LoS over
   a kill-corridor. Armored Support anchors to a friendly squad's
   centroid.

This means `tryFireMechWeapons` doesn't need to be rewritten — it gets
*wrapped* by the action, which decides which of the three weapon tracks
to call into (`maybeFireLrm` / `maybeFireSrm` / `maybeFireChaingun`) per
tick. The wrapping is the role expression.

## Actions

### `OverwatchKillZone` (LR Support default)

**Picks:** the best cover cell at LR band (~30–35 cells) with LoS along
the squad's threat-axis. Threat axis = direction from squad centroid to
known contact / objective.

**Movement:** path to the picked cell. Stay there. If the threat axis
flips (squad alert spreads, new contact comes from a different angle),
the action's precondition stops holding and the planner re-evaluates.

**Firing:** every tick, prefer LRM. Fire SRM only if a target enters
SRM band *and* the mech is `MORALE_PRESSURED` (an analog "they're on
top of me" predicate — gated below). Fire chaingun only if a target
enters chaingun band — last-ditch.

**Effect:** `KILL_ZONE_COVERED` (analogous to infantry's
`ENEMY_SUPPRESSED`).

### `BackstopAssignedSquad` (Armored Support default)

**Picks:** a cell N cells behind a designated friendly squad's centroid,
biased to cover when available. "Behind" = away from threat axis.

**Movement:** pace the squad — if centroid moves > M cells, replan
position. Don't outrun the squad; don't fall behind by more than 2× the
follow distance.

**Firing:** every tick, fire whichever weapon has an in-band target with
LoS. No withholding. Targets are picked by *threat to backed squad* (the
enemy currently shooting at our marines), falling back to nearest enemy
in any band.

**Effect:** `SQUAD_BACKED` (predicate the marine squad can read for
Story E's mech-screened advance later).

### `EngageAtCurrentBand` (ambient fallback)

Parity with current `MechCombatantBehavior` — pick the best target by
`TacticalScoring.findBestTarget`, path to a firing position, fire
whatever's in-band with LoS. Used when no role-anchored goal has higher
relevance (alert level low, no friendly squad nearby, no kill-corridor
to anchor).

This is the *floor* — every mech without a meaningful role-task at the
moment runs this. Preserves the "mech is dangerous everywhere" feel.

## Goals

### `OverwatchKillZone` (MISSION-priority)

- Relevance ~1.0 when `role == LR_SUPPORT` AND a kill-corridor exists
  (squad has an assigned tactical node or known contact at LR band).
- Desired state: `KILL_ZONE_COVERED`.
- Plan: single-step `OverwatchKillZone` action.

### `BackstopAssignedSquad` (MISSION-priority)

- Relevance ~1.0 when `role == ARMORED_SUPPORT` AND a designated friendly
  squad exists within range to back.
- "Designated" — Stage 1 picks at spawn: nearest infantry squad on the
  same side. Commander tier later overrides this with explicit
  assignments via `ObjectiveAssignment`.
- Desired state: `SQUAD_BACKED`.
- Plan: single-step `BackstopAssignedSquad` action.

### `MechEliminateEnemies` (ENGAGEMENT-priority, fallback)

- Relevance 0.3 always, scaling on contact distance.
- Outranked by either mission goal when their preconditions hold.
- Plan: single-step `EngageAtCurrentBand` action.

## Predicates (new)

- `KILL_ZONE_COVERED` — bucketed: true when an `OverwatchKillZone` action
  is currently in steady-state at its picked cell.
- `SQUAD_BACKED` — bucketed: true when a `BackstopAssignedSquad` action
  is within range of its designated squad.
- `ROLE_IS_LR_SUPPORT` / `ROLE_IS_ARMORED_SUPPORT` — read from
  `Unit.mech.role`. Stage 2 expands when Recon + Assault land.

Most of the existing mech-relevant predicates carry over from infantry
(`HAS_TARGET`, `HAS_LOS_TO_TARGET`, `IN_RANGE_OF_TARGET`, etc.) — the
`WorldStateBuilder` extensions for mech squads are mostly *role flags*
+ the two new state predicates.

## Squad shape — mech-only, single-cell-per-mech for now

Confirmed: mech-only squads. Marines don't join mech squads, mechs don't
join marine squads. Adjacency / screening (Story E) is the integration
point, not membership.

**Multi-cell mechs are a planned future.** When mech chassis scale to
2×2 or 3×3 cells:

- `Unit.cellX`/`cellY` becomes the anchor cell; an `Occupancy.footprint`
  query expands to the full set.
- `GridPathfinder` needs a `footprintFits` check at each candidate cell.
- Cover model: a mech-sized chassis blocks LoS for the whole footprint
  (already true conceptually for the soft-cover Story E primitive — Mech
  GOAP doesn't need to be the one to introduce it).

Stage 1 stays single-cell — no code should assume mechs are 1×1 *load-
bearingly* (no `footprint = 1` hardcodes; no "mech is just a unit with
extra HP" shortcuts in pathfinding). The lift to multi-cell stays a
future occupancy-map change, not a planner rewrite.

## Mech survival — retire flinch, defer morale

Today mechs roll `rollFallbackOnHit` on every successful hit
(`BattleSimulation:444`) — the legacy per-unit "got shot, sidestep a
cell" reflex. Infantry already opts out at the
`target.squadId != NO_SQUAD && target.mech == null` gate (Story B's
followup); mechs are explicitly *not* opted out, with a comment that
they keep the legacy roll "until their own substitute lands."

Stage 1 retires the flinch outright. The gate flips to
`target.squadId != NO_SQUAD` — both infantry-squad members AND mech-
squad members skip the roll. Net effect: **mechs don't flinch.** They
hold their planned cell and keep firing through incoming damage until
the chassis dies. The "all-arounder challenge punisher" read benefits —
mechs feel *implacable*, not twitchy.

### Mech morale as a future replacement (Stage 2-ish)

We could later model mech morale the same shape infantry uses (a
`mech.morale` float on `MechLoadoutState`, drain on hits, recover when
out of LoS, hysteresis between broken/clear, a `MORALE_BROKEN` predicate
gating a mech `BreakContact`). **But tougher.** Suggested differences
from the infantry tuning:

| Parameter | Infantry (current) | Mech (proposed) |
| --- | --- | --- |
| Drain trigger | per casualty + per hit | per chassis-HP threshold crossed |
| Broken threshold | below 0.3 | below 0.15 |
| Clear threshold (hysteresis) | above 0.5 | above 0.4 |
| Recovery rate (out of contact) | normal | ~1.5× faster |
| Cap when armor is gone | n/a | hard cap at 0.5 (a damaged mech *can* be rattled) |

Rationale: a mech is supposed to be the thing that doesn't break easily,
not another infantry-shaped morale subject. The morale state exists to
support a *late-battle "wounded mech withdraws"* moment — not a "mech
flinches under fire" moment.

**Stage 1 ships without morale.** A mech in Stage 1 fights until it
dies. The morale system queues for a later slice once we see how the
no-flinch baseline plays — playtest may show mechs feel too unkillable
without *any* "back off when hurt" pressure, in which case the morale
work gets pulled forward.

## Player-unlockable mechs (out of scope)

Defender-only spawn today. The player-controlled mech path is sketched
in session log `2026-05-17.md` (fourth pass) — a separate axis.
Stage 1's planner doesn't need to care whether the mech is AI or
player-controlled; the `assignedObjective` field is what differentiates.
A player-issued order would write the same field a commander would.

## Commander-tier tie-in

`MechRole` is assigned at spawn-time by `BattleSimulation` mech-cluster
mint logic. **The role is stable for the duration of the battle.** The
commander tier ([`12-squad-of-squads.md`](12-squad-of-squads.md))
arrives later and writes `squad.assignedObjective` with
`AssignmentKind.OVERWATCH_KILL_ZONE` / `BACKSTOP_SQUAD` / etc. —
upgrading what is currently a *role default* into a *commander-issued
assignment*.

Concretely: today `OverwatchKillZone` goal's relevance is
`role == LR_SUPPORT ? 1.0 : 0.0`. Post-commander, it becomes
`assignedObjective.kind == OVERWATCH_KILL_ZONE ? 1.0 : (role == LR_SUPPORT ? 0.6 : 0)`
— the commander's explicit order trumps the role default, but the role
remains as the fallback when no order is issued.

This way the commander tier doesn't have to ship at the same time as
mech GOAP, and mech GOAP doesn't force-couple to the commander.

## Implementation plan

Sequential — each step is small and testable on its own. No subagent
fanout for Stage 1; the surface is too coupled for parallel work to be
worth it.

1. **Add `MechRole` + assign at spawn.** New enum,
   `Unit.mech.role` field, defender mech-cluster mint stamps roles
   (round-robin or "first is LR, rest are Armored" — playtest will tell
   us). No behavior change yet; `MechCombatantBehavior` still runs.
2. **Mech squads through GOAP pipeline.** Remove the `mech == null`
   filter at `BattleSimulation:458` so mech squads enter the per-tick
   replan loop. They'll plan against infantry goals (which won't be
   relevant) and fall through to `EliminateEnemies` — no visible change
   yet, but the plumbing is live. *Important:* `CombatantBehavior` still
   routes to `MechCombatantBehavior` per-unit at this step; the
   planner's plan is *ignored* by the dispatch.
3. **Stand up `GoapMechBehavior` + `EngageAtCurrentBand` parity action.**
   `CombatantBehavior` dispatch flips to `GoapMechBehavior` for
   `u.mech != null`. The planner picks `MechEliminateEnemies` as the
   only relevant goal; the action calls `tryFireMechWeapons` with no
   role gating. **This is the parity point** — visible behavior should
   match `MechCombatantBehavior` modulo planner latency.
4. **Add `OverwatchKillZone` goal + action.** LR Support mechs flip to
   this goal when they're not already engaged at close range. Visible
   payoff: LR mechs hold position at LR band and lob LRMs without
   chasing into chaingun range.
5. **Add `BackstopAssignedSquad` goal + action.** Armored Support mechs
   pace the nearest friendly infantry squad. Visible payoff: Armored
   mechs visibly tag along behind a marine squad instead of bee-lining
   to the nearest enemy.
6. **Delete the `MechCombatantBehavior` dispatch reference.** File
   itself stays on disk for reference; no longer wired in. Sweep
   `CombatantBehavior` + any test fixtures that mention it.

Each step is a commit. Steps 1–3 are "make it work in parity"; 4–5 are
"make the roles visibly distinct"; 6 is cleanup.

## Open questions for playtest tuning

These are tuning parameters that don't need design pre-work — playtest
the first version and iterate.

- **LR band bounds for `OverwatchKillZone`.** Doc says 28–40 cells.
  LRM range is 40, chaingun is ~22. The lower bound is the discipline
  question: how close does a contact get before LR Support gives up the
  overwatch and switches to closing? Probably ~25 cells (LRM minimum
  effective range, where SRMs start being more efficient).
- **Backstop distance for Armored Support.** Doc says N cells behind the
  squad. 5? 8? Far enough that the mech's chaingun outranges marine
  rifles by ~10 cells, close enough that LoS to the squad's targets
  exists.
- **Role assignment policy per cluster.** First-mech-LR, rest-Armored?
  Always-1-LR? Random? Cluster size dependent? Playtest in mid-Conquest
  runs.

## Cross-references

- Parent design: [`13-mech-goap.md`](13-mech-goap.md)
- Commander tier (future tie-in): [`12-squad-of-squads.md`](12-squad-of-squads.md)
- Infantry story bank (Story E pairs with mech GOAP for screened
  advance): [`10-tactical-stories.md`](10-tactical-stories.md)
- Mech weapons: `MechWeapon.java`, `MechLoadoutState.java`
- Current behavior (retiring): `MechCombatantBehavior.java`
- Memory: `[[mech_weapon_aggro_distinction]]`,
  `[[long_term_vision_sub_game]]`
