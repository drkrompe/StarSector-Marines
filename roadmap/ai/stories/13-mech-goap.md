# 13 — Mech GOAP Tree

**High-level design doc.** Captures the shape of the GOAP migration for
the mech side of the battle sim. Mechs currently run on
`MechCombatantBehavior` — a per-unit, hand-authored ad-hoc combat loop —
while infantry runs through the squad-level planner (`GoapInfantryBehavior`).
This doc is the long-arc plan; the active implementation slice lives in
[`14-mech-stage1.md`](../complete/14-mech-stage1.md).

**Commander-gate lifted.** The original "wait for commander tier first"
recommendation has been overridden — Stage 1 ships with *spawn-time role
assignment* as a stub the commander will later replace, so we get
visible MechCommander-style doctrine differentiation without blocking on
the commander layer. See `14-mech-stage1.md`'s "Commander-tier tie-in"
section for the upgrade path.

## Why this exists

Three things converging make a "mech-side planner" the right next layer
once infantry GOAP settles:

1. **Story B's morale + retreat machinery is infantry-only.** The
   followup that gated `rollFallbackOnHit` to non-squad / non-mech units
   left mechs on the legacy per-unit fall-back roll. That's a temporary
   bridge: the long-term shape is "mechs also break contact and re-engage
   via a planner-driven posture," not "mechs reroll a per-hit retreat
   chance forever."
2. **Mech role differentiation.** A heavy walker doing fire support
   plays *very* differently than one doing close assault. The current
   single behavior class can't express that distinction without growing
   role-flag branches everywhere; a planner with role-specific goals is
   the cleaner way to slice it.
3. **Combined-arms missions.** Conquest + future Assault put mechs
   alongside marine squads. The commander tier
   ([12-squad-of-squads.md](12-squad-of-squads.md)) needs a coherent
   *thing* to assign to mech units — "send the LR support mech to
   overwatch this approach" requires the mech to actually understand
   what "LR support" means at the planner level.

## Squad structure

The decision point: are mechs **solo combatants** (each one is its own
squad) or **own-squad** (mechs form mech-only squads), or **mixed-arms
squad members** (a mech joins a marine squad)?

Current state: mechs spawn in defender clusters and carry a `squadId`
from the cluster they were assigned to — so they're already "squad
members" structurally, but their behavior dispatch ignores it.

Recommended shape going forward:

- **Mech squad** — one squad of mechs (1–3) on each side that has them.
  Squad members can be assigned to different roles via the same
  `RoleAssigner` infantry already uses.
- **Mixed-arms squads stay infantry-only.** A marine fireteam doesn't
  pick up a mech as the fourth member. Mech-screened-advance (Story E)
  in the infantry story bank works via *adjacency between squads*, not
  membership — marines path behind a friendly mech's centroid without
  being in the same squad.

Tradeoffs:

- Mech-only squad is cleanest for the role assigner — a `SuppressFromRange`
  role and a `BreachAssault` role can both pull from the same pool.
- Mixed-arms is closer to MechWarrior / MechCommander tropes but is hard
  to author (one squad with vastly different unit kinematics, ranges,
  and engagement bands).
- The commander tier hands missions to *squads*, not units. Mech-only
  squads give the commander a clean "send this squad to overwatch" verb.

## Roles

Four target roles, derived from MechCommander-style doctrines and from
what the current mech weapon mix can express:

| Role | Engagement band | Primary weapon | Posture |
|---|---|---|---|
| **LR Support** | 28–40 cells (LRM band) | LRM | Overwatch from cover, lobs LRMs at clustered targets, holds position |
| **Armored Support** | 12–22 cells (SRM + chaingun band) | SRM + chaingun | Backstops a marine squad, soaks fire, suppresses on contact |
| **Recon** | (variable) | chaingun (cheap firing) | Scout — pushes into unknown zones, retreats fast on contact, paints targets for LR support |
| **Assault** | 0–18 cells (close-in) | all weapons hot | Closes on objectives, breaches doors, uses the chassis as a battering ram |

Same as marine roles in `MarineLoadout` / `UnitRole`, these become a
role enum the planner reads when scoring goals + actions per mech.

**Doctrine, not loadout.** Every mech chassis carries all three weapons
(LRM + SRM + chaingun) by design — they're all-arounder heavy-hitters
that punish the player at any range. Roles do NOT change the weapon
set. Roles change *which weapons the mech is willing to fire in a given
posture*, *which engagement band the mech prefers*, and *what the mech
anchors its movement to* (cover cell / friendly squad centroid / probe
target / objective). LR Support actively withholds SRMs and chaingun
even when in-band targets exist; Armored Support fires whatever's hot
at the closest threat. Same chassis, different planner.

## Predicates the planner needs (mech-specific)

The story bank's existing predicates carry most of the load (HAS_TARGET,
HAS_LOS_TO_TARGET, IN_RANGE_OF_TARGET, etc.). Mech-specific additions
the existing list doesn't cover:

- **`MECH_HEAT_NEAR_CAP`** — if/when the heat mechanic lands (parked,
  see session log 2026-05-17 mech rebalance section). Gates whether
  this mech can fire its hot weapons.
- **`SR_AMMO_DEPLETED`** / **`LR_AMMO_DEPLETED`** — bucketed ammo flags
  per weapon. Drives the "switch to chaingun + advance" posture when
  rockets run dry.
- **`ALLIED_INFANTRY_AHEAD`** — true when a friendly marine squad's
  centroid is between this mech and the nearest known threat. Story E
  (mech-screened advance) uses the inverse from the infantry side; the
  mech-side equivalent is "I'm running point, position accordingly."
- **`THREAT_LOS_BLOCKED_BY_WALL`** — distinct from no-target. The LRM
  indirect-fire path (session 2026-05-17 sixth pass) already supports
  blind-firing through walls; the planner needs to know "we can't see
  this target but the LRM can still try."

## Actions

The mech action library leans on what `MechWeapon` + `MechLoadoutState`
+ the AoE detonation pipeline already provide. New actions:

- **`OverwatchFromRange`** — LR Support default. Find a cover cell at
  LR band with LoS to a kill-corridor. Hold and fire LRMs at clustered
  targets. Custom-plan-style synthesis from the assignment.
- **`SuppressAdvance`** — Armored Support default. Pace a friendly
  squad's centroid, fire SRMs + chaingun at targets in their LoS arc.
- **`ProbeZone`** — Recon default. Path into an unknown zone, fire
  opportunistically, retreat at first sustained contact (heat / hit
  threshold).
- **`BreachAndAssault`** — Assault default. Path through a portal,
  fire everything close-in, push past doodad cover with the chassis.
- **`BreakContactMech`** — mech analog of the infantry `BreakContact`
  (story B). Distinct because the mech is bigger / slower / has different
  cover semantics (it can be its own cover for nearby marines but
  receives less from doodads).

## Goals

Each role gets its own goal at the corresponding priority bucket:

- **MISSION** — `OverwatchAssignedKillZone`, `BackstopAssignedSquad`,
  `ScoutAssignedZone`, `AssaultAssignedObjective`. Driven by commander
  assignments via `ObjectiveAssignment` (per the commander doc).
- **SURVIVAL** — `MechSurviveContact` analog. Different threshold than
  infantry — a mech at 30% HP is in real trouble; a marine squad at
  50% strength is the analog. Heat lockouts feed in here too (a mech
  that can't fire is much less safe than one that can).
- **ENGAGEMENT** — `EliminateEnemies` ambient default like infantry's,
  but the relevance picks the role-appropriate engagement band.

## Relationship with the commander tier

This is **gated on the commander tier landing first.** Why:

- Mech roles only matter if something assigns them. A standalone mech
  squad with no commander reduces to "all mechs do the default action"
  — which is what `MechCombatantBehavior` already does, just
  rearchitected. Cost without benefit.
- The commander's job is exactly "decide which mech goes where, doing
  what." A mech planner without that input layer is the wrong
  abstraction — it'd have to fake the assignments via local heuristics
  ("nearest contact" / "lowest-HP friend") which is what the existing
  single-behavior class already does.

Implementation ordering:

1. Commander tier lands (`MissionCommand`, `ObjectiveAssignment`,
   `ClearAssignedZoneGoal`, etc. per `12-squad-of-squads.md`).
2. Commander gains awareness of mech squads — distinct from infantry
   squads in the assignment scorer (a mech assigned to overwatch
   isn't the same as a marine squad assigned to clear the room).
3. Mech GOAP infrastructure lands (this doc's actions / goals /
   role-predicate evaluators).
4. `MechCombatantBehavior` retires in favor of the dispatch through
   `GoapMechBehavior` (or however we name it). Existing per-mech
   weapon firing (`tryFireMechWeapons`, `advanceMechWeapons`) stays —
   actions reuse those primitives the same way infantry actions reuse
   `TacticalScoring.findFiringPosition` etc.

## What this doc is NOT

- **A spec for the role-tuning numbers** — engagement bands, hit
  thresholds for posture flips, heat thresholds. Those need playtest.
- **A commitment to mech-only squads vs. mixed-arms.** This doc
  recommends mech-only-squads; the choice is reversible at squad-spawn
  time and the planner shape works either way.
- **A heat-mechanic decision.** Heat is parked separately (session log
  2026-05-17 sixth pass). The mech planner can land without heat; heat
  can land later as a new predicate + drain hook.

## Out of scope

- **Mech-vs-mech tactics.** A mech fighting another mech is treated as
  "engage a high-HP combatant" — no special predicates for it. If
  mech-vs-mech duels become a thing, that's its own slice.
- **Player-controlled mechs.** No "issue mech orders" UI is implied. A
  player-fielded mech path (sketched in session log 2026-05-17 fourth
  pass) is a separate axis from the AI mech planner.
- **Damage feedback to commander.** The commander tier's re-assignment
  pass watches squad health; mechs feed into that the same way infantry
  squads do.

## Status

**Active — Stage 1 unparked 2026-05-19.** Implementation slice in
[`14-mech-stage1.md`](../complete/14-mech-stage1.md). Stage 1 covers two roles
(LR Support + Armored Support) via spawn-time assignment; Stage 2
adds Recon + Assault and any dynamic re-assignment the commander
tier hands down.

## Cross-references

- [Story bank](10-tactical-stories.md) — per-squad infantry tactical moments
- [Squad-of-squads commander](12-squad-of-squads.md) — the layer this
  one depends on
- Memory: `[[mech_weapon_aggro_distinction]]`, `[[long_term_vision_sub_game]]`
- Mech weapon system: `MechWeapon.java`, `MechLoadoutState.java`,
  `MechCombatantBehavior.java` (current behavior, slated for retirement)
- Session logs: `2026-05-17.md` passes 3–7 cover the mech weapon
  system as it stands today
