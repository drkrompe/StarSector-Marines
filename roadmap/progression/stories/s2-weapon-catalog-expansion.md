# S2 — Weapon catalog expansion

> Four primaries and one secondary is not enough surface for an armory
> screen, a preset system, and a tier ladder to sit on.

Status: PLANNED — depends on the shipped lethality scale in `progression-nouns.md`.
Written: 2026-08-22
Updated: 2026-08-23 — migrated dependencies and references to the canonical model.

## Problem

The shipped catalog is:

- **Primaries** — `FIELD_RIFLE` (recruit issue), `PULSE_RIFLE` (burst
  rifle), `SMG` (light machine gun), `DMR` (railgun). Plus `DRONE_PULSE`,
  which is not marine-issue.
- **Secondaries** — `ROCKET_LAUNCHER`. One.

That is a clean DMR / BR / AR triad plus a recruit rifle, and it is a
reasonable *foundation* — but there is no close-quarters option in a mod
whose mapgen ships two-cell combat aisles and apartment interiors, no
thrown weapon, no way to make a hole in a wall on purpose, and nothing to
unlock once the ladder in `s6-unlock-ladder-expansion.md` grows.

## Goal

Give every fireteam preset a distinct reason to exist, and give the unlock
ladder real things to hand out. Each new entry needs a **tactical identity
statable in one sentence** — not a stat block sitting between two existing
ones.

## Slice 1 — Close-quarters and suppression primaries

Candidate families, each reusing vanilla art and audio the way the existing
catalog does:

| Family | Identity | Notes |
| --- | --- | --- |
| **Combat shotgun** | Devastating inside ~8 cells, useless past 14 | The interior/aisle answer. Pairs with the mapgen commercial and apartment work. Wide `hitSpread`, steep `accuracyFalloff`, high per-round damage, multi-pellet burst |
| **Squad automatic** | Real suppression: long belt, wide cone, poor precision | Distinct from `SMG`, which is currently a fast-cycling carbine wearing an LMG name. Long sustained bursts, high `hitSpread`, mediocre damage |
| **Anti-materiel rifle** | Single heavy round, high `vsTurretMult`, long aim | The primary-slot answer to emplacements and light armor, without spending a rocket tube |

Naming note: `SMG`'s display name is already "Light Machine Gun" while its
stats read as a carbine. Reconcile the enum name and display name in this
slice rather than adding a third overlapping entry on top of the confusion.

## Slice 2 — Secondaries

The secondary slot is the biggest single gap: it is a shipped, allocated,
UI-visible slot with exactly one occupant.

| Secondary | Identity |
| --- | --- |
| **Frag grenade** | Short arcing throw, small AoE, friendly fire on. The infantry staple. Ammo 2-3 |
| **Breaching charge** | Placed, timed, large wall damage, negligible anti-personnel. Makes a door where there wasn't one — direct mapgen and tactical-AI payoff |
| **Smoke grenade** | Blocks line of sight for a duration. Reads directly against the shipped `fog-of-war.md` vision system and cover model |

Smoke is the highest-value of the three because it is the first *utility*
item — it changes the fight without dealing damage, which is a category the
mod does not have yet. It is also the one with real system cost: it needs a
transient LoS blocker the shadowcast pass respects.

## Out of scope

- **Marine role specialization.** Everyone still deboards
  `UnitRole.COMBATANT` (bar scripted `PLANTER` slots). A grenadier/medic/
  designated-marksman role system is a real feature and wants its own story
  — carrying it here would double the slice.
- Weapon *modding* or attachments. Grade is the modifier axis; a second
  orthogonal one is not warranted.
- Any change to `MarineSecondary`'s AI preference logic beyond extending it
  to the new entries. `CombatantBehavior` currently prefers the secondary
  against `MapTurret` targets; smoke and breaching charges need explicit
  use conditions rather than an extension of that rule.

## Acceptance

- Each new family has a one-sentence identity recorded in its enum Javadoc,
  matching the pattern the existing entries set.
- Every entry is reachable: it has a starter unlock, a ladder unlock in
  `s6-unlock-ladder-expansion.md`, or an explicit note saying which.
  **Nothing ships stranded** — that is the exact failure the audit found
  with four armor patterns.
- Presets in `SquadEquipmentPreset` are revisited so the new families feed
  at least one team archetype (an ASSAULT/BREACH preset is the obvious
  addition).
- Defender rosters (`InfantryLoadoutRolls`, `DefenderRoster`) get an
  explicit decision per family: enemy-available or player-only. Shotguns in
  defender hands change interior assaults substantially.
- Existing scenario force ratios re-verified, as in S1.

## Open questions

- Should the secondary slot become **two** slots (one weapon, one utility)?
  Smoke competing with a rocket tube for the same slot may be the wrong
  trade. Leaning: keep one slot for now, revisit if smoke is never chosen.
- Does the anti-materiel rifle overlap the `DMR` too much once S1 widens
  damage? Possible answer: make `DMR` the precision/anti-personnel option
  and the AMR the slow anti-hard-target one, with `vsTurretMult` and aim
  time as the separators.
