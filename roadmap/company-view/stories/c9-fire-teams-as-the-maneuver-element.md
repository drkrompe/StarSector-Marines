# C9 — Fire teams as the maneuver element

> Every squad tactic we ship invents its own sub-grouping from scratch, out
> of arbitrary members, and throws it away at the next replan. Give the AI
> a real element to maneuver.

**Status:** not started. Depends on
C7 (`c7-organization-and-ranks.md`) for the structure.

## Decision this story implements

Fire teams are modelled and **behind the scenes**: the player commands
squads, the AI maneuvers teams. Teams surface in the UI only as pip
grouping on a squad row — never as a card level, never as an order target.

## Problem

The squad tier already ships real tactics, and every one of them fabricates
its own sub-groups:

- **Bounding overwatch** (`EnterZone`, story 20) declares `TEAM_A` /
  `TEAM_B` role slots and fills them with a **zero scorer**
  (`member -> 0f`) — an arbitrary halving, re-derived on each plan. The
  same marines never bound together twice, and nothing about the split
  reflects who is carrying what or who leads.
- **Flanking** (`FlankApproach`, story D) converges the *entire* squad on
  one flank waypoint. There is no fix-and-flank because there is no
  element to leave behind fixing.
- **Mech-screen advance** picks per-member cells ad hoc into
  `mechScreenMemberIds` / `mechScreenTargetXs`.
- **Cordons** (`HoldPortalCordon`, `CordonForPlant`) draw individuals into
  portal slots.

At six marines that was tolerable. At twelve (C7 (`c7-organization-and-ranks.md`))
a squad converging as one blob is worse, not better — and the 9× lethality
scale means a blob crossing open ground is a wipe.

`RoleAssigner`'s own javadoc says "squads cap around 8" while justifying
greedy + swap over Hungarian. Twelve does not invalidate that choice, but
the comment stops being true.

## Design

### A persistent partition, not a per-plan one

- The fire team is a property of the **squad's organization** (C7's team
  index per billet), carried into battle on C1's seat data, not a thing the
  planner invents. Three teams of four, each with a leader.
- Role assignment prefers **intact teams**: give `RoleAssigner` a
  team-cohesion scorer so a slot wanting "half the squad" fills with whole
  teams rather than the first N members. Slots keep their existing
  semantics; only the ranking changes.
- **Casualty consolidation:** when a team falls below two effectives, fold
  its survivors into a sibling team rather than maneuvering a one-marine
  element. Under 9× lethality this will fire often — it is the difference
  between a squad degrading and a squad fragmenting.

### What the teams then do

- **Bounding overwatch** becomes team-based. With three teams the natural
  form is one bounding while two overwatch, alternating — more robust than
  today's alternating halves, and it degrades to today's behavior at two
  teams and to a simple advance at one.
- **Fix and flank** becomes expressible: one team holds and suppresses on
  the engagement axis while another arcs to the flank waypoint
  `ReinforceContact` already computes. That is `FlankApproach` scoped to a
  team instead of a squad — the existing waypoint math is reusable as is.
- **Screens and cordons** draw a team for the job and leave the rest of the
  squad on its objective, instead of committing every member to a posture.

### What stays out of the player's way

No new selection level, no team-level orders, no extra HUD panel. The
squad row's twelve pips group 4 / 4 / 4 so a player who looks closely can
see a team has been shot away; the battle detail panel may label rows by
team. That is the whole player-facing surface.

## Slices

1. **Team identity in battle.** Team index on the spawned marine, exposed
   on `Squad` as a partition; consolidation rule when a team is gutted.
2. **Team-aware role assignment.** Cohesion scorer in `RoleAssigner`;
   `EnterZone`'s `TEAM_A`/`TEAM_B` fill with whole teams.
3. **Three-team bounding.** One moves, two overwatch, with graceful
   degradation as teams are consolidated.
4. **Fix and flank.** Team-scoped `FlankApproach` with a suppressing
   element left on the axis.

## Acceptance

- The same marines maneuver together across replans; a bounding split is
  stable unless casualties force a consolidation.
- A twelve-marine squad crossing open ground never moves as one body.
- A squad reduced to five effectives still bounds, as two teams, without
  special-casing.
- No new player-facing hierarchy level, no new order target, no change to
  `Selection`.
- Determinism holds: team assignment is derived from persisted billet
  order, not from spawn race order or a random draw.
- Parallel-replan safety: the partition is read-only during the replan
  window, same contract the rest of the planner keeps.

## Files touched

- `battle/squad/Squad.java` — team partition (read-only during replan).
- `battle/decision/goap/scoring/RoleAssigner.java` +
  `Scorers.java` — team-cohesion scorer; fix the stale "squads cap around
  8" note.
- `battle/decision/goap/action/EnterZone.java` — team-based bounding.
- `battle/infantry/FlankApproach.java`, `ReinforceContact.java` —
  team-scoped flank.
- `battle/air/InfantryPayload.java` — carry the team index in from C1's
  seats.

## Out of scope

- Team-level player orders. The whole point is that this tier is the AI's.
- Per-team loadout composition (automatic rifleman / grenadier / anti-armor
  billets) — noted in C7 (`c7-organization-and-ranks.md`)'s open questions
  and closer to progression's equipment work.
- Reworking cohesion itself. `InfantryCohesion` stays the one cohesion
  layer; teams change *who* is grouped, not how grouping is enforced.

## Open questions

- Should a team that loses its leader be consolidated immediately, or
  promote within the team first? Promoting matches C7's rank model;
  consolidating matches the lethality reality. Probably promote while ≥ 2
  effectives remain, consolidate below that.
- Does the commander tier ever want to address a team directly — a
  `SECURE_COMPOUND` assignment that peels one team rather than a squad?
  It would make small-objective capture cheaper. Deferred: it puts an
  element the player cannot see under the commander's control, and the
  commander already has squad-granularity levers.
