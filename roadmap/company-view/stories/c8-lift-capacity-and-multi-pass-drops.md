# C8 — Lift capacity in squads, and multi-pass drops

> A squad is six. Seven of eleven transports carry fewer than six. Every
> small hull splits a squad by arithmetic, and every reinforcement wave
> mints a brand-new one.

**Status:** not started. Pairs with
[C1](c1-fireteam-identity-through-the-drop.md) — C1 gives the seat an
identity, C8 makes the lift respect it.

## Decision this story implements

Transport capacity is expressed in **squads**, with a floor of one whole
squad per lift. Bigger hulls carry multiple squads or a squad plus
equipment. Where a squad still cannot arrive in one pass, later arrivals
join the same squad and catch up.

## Problem

### The capacity table fights the squad size

`ShuttleType` capacities today, against `MarineSquad.CAPACITY = 6`:

| Hull | Seats | Hull | Seats |
| --- | --- | --- | --- |
| Hermes | 3 | Buffalo | 6 |
| Aeroshuttle | 4 | Mule | 6 |
| Kite | 4 | Nebula | 7 |
| Mudskipper | 4 | Valkyrie | 8 |
| Shepherd | 4 | | |
| Wayfarer | 4 | Tarsus | 5 |

Seven of eleven cannot carry a squad. The most common early-game lift
(Aeroshuttle / Kite, 4 seats) delivers two-thirds of a squad, so the
organizational unit the player selected on the deployment screen never
exists as such on the ground.

### Waves fragment what does arrive

`AirSystem` resets `mission.squadId = Squad.NO_SQUAD` when a shuttle begins
its next cycle (`AirSystem.java:471`), and `InfantryPayload` mints a fresh
squad on the next deboard. A three-cycle shuttle carrying one squad's worth
of marines produces **three unrelated battle squads**, each with its own
morale baseline, alert state, and GOAP plan.

### The residue is invisible

Nothing tells the player that "2nd Squad" is actually 4 marines here and 2
marines somewhere else. It reads as attrition.

## Design

### Capacity in squad-slots, with a floor

- **Floor:** no transport in a marine manifest carries fewer than
  `MarineSquad.CAPACITY`. Hermes 3, the five 4-seat hulls, and Tarsus 5 all
  rise to 6.
- **Above the floor:** capacity is squads plus payload, not arbitrary
  seats. A heavy hull carries 2–3 squads; a medium hull carries one squad
  plus an equipment slot.
- **Differentiation moves off seat count.** The small end already differs
  on turn rate, acceleration, lateral damping, hardpoints, HP, and loiter
  time (`AirHandling` + the turret kit). Flattening 3/4/5 → 6 costs
  nothing the player was reading anyway; a Hermes stays a fast, fragile,
  one-hardpoint courier that happens to fit a squad.
- **Equipment instead of marines** is already a modelled concept: the
  `AirDeliveryPayload` seam has `InfantryPayload` and `MechSupportPayload`
  side by side. A squad-slot spent on equipment is that seam, not a new
  mechanism.

### One squad, however many passes

- With C1's `(fireteam, LZ)` minting key, later arrivals **join** the
  existing battle squad instead of minting a new one. That requires scoping
  `AirSystem`'s per-cycle `squadId` reset so it applies to generated
  personnel (militia waves, walk-in reinforcements) but not to campaign
  squads with an identity.
- `Squad.originalSize` increments per deboard today and morale caps on
  `aliveMembers / originalSize` — a joining wave must keep incrementing it
  rather than resetting, so the squad's morale ceiling rises as it
  assembles instead of reading as a half-strength unit.

### Late arrivals catch up

A marine who lands at the LZ while their squad is forty cells forward must
not walk into a firefight alone, and must not be treated as a full member
of an ENGAGED squad's plan the instant they spawn.

The machinery already exists: `RegroupPosture` paths a member toward the
squad using `InfantryCohesion.cohesionOverride`, and the planner selects it
whenever a downstream posture needs `WITHIN_COHESION_RADIUS`. What is
missing is the *intent*: a fresh arrival should be explicitly in a rejoin
state — move to the squad, avoid initiating, fall into normal dispatch on
arrival — rather than inheriting the squad's current plan from spawn.

Build this on the existing cohesion layer. Do not add a second cohesion
mechanism; the two would fight.

## Slices

1. **Capacity floor + squad-slot expression.** `ShuttleType` values, and
   whatever in `DetachmentResolver` / `getMarineDeploymentCapacity` derives
   seats from them.
2. **Scope the per-cycle reset.** Campaign squads persist across cycles;
   generated waves keep today's behavior.
3. **Rejoin state for late arrivals.**

## Acceptance

- No lift in a player manifest carries a partial squad.
- A shuttle flying three cycles for one squad produces **one** battle
  squad, with `originalSize` equal to the marines actually landed.
- A late arrival paths to its squad without soloing into contact, and
  behaves normally once it arrives.
- Militia, walk-in reinforcement, and employer spawns are unchanged.
- **Balance check:** raising the floor increases every early-game lift by
  50–100%. Re-derive the opening missions' seat math and deployment
  capacity, and verify the two Independent opening jobs still field the
  intended force. This is a live balance surface — see
  [`campaign/early-operations/next-session.md`](../../campaign/early-operations/next-session.md).

## Files touched

- `battle/air/ShuttleType.java` — capacities.
- `battle/air/AirSystem.java` — scoped cycle reset (line ~471).
- `battle/air/InfantryPayload.java` — join path (shared with C1).
- `ops/detachment/DetachmentResolver.java`, `ops/MarineOpsContext.java` —
  seat derivation.
- `battle/infantry/` — the rejoin state, on the existing cohesion layer.

## Out of scope

- Building the equipment payload itself. The squad-slot model reserves the
  room; `MechSupportPayload` already proves the seam. Wiring a chooseable
  "this slot carries a vehicle" is a separate story, and probably belongs
  to the convoy or command-powers track rather than here.
- Changing how the player's fleet maps to transports
  (`PlayerFleetShuttles` scanning) beyond the capacity numbers.
- Drop-zone selection, AA, or wave timing — the drop-ship invasion ladder
  in [`vanilla-combat-bridge/`](../../vanilla-combat-bridge/overview.md)
  owns those.

## Open questions

- Does the floor make the small hulls interchangeable in the player's
  mental model, even if they differ on handling? If it does, the answer is
  probably to differentiate on **cycles** — a light hull flies more, faster
  sorties for the same total delivery — rather than to reintroduce partial
  squads.
- Should a heavy hull's second squad-slot be selectable (squad vs.
  equipment) at briefing, or resolved automatically from what the player
  brought? Selectable is better play; automatic is shippable sooner.
