# C8 — Lift capacity in fire teams, and multi-pass drops

> Capacities are 3, 4, 5, 6, 7, 8 — a set of numbers that divides into no
> organizational unit at all. Every lift splits a squad by arithmetic, and
> every reinforcement wave mints a brand-new one.

**Status:** slices 1-3 shipped 2026-08-22 (`00ace1b0`, `6e3908b0`).
**Slice 4 — the rejoin state for late arrivals — is all that remains.**
Pairs with C1 (`c1-fireteam-identity-through-the-drop.md`), now complete:
C1 gave the seat an identity, C8 made the lift respect it.

## Shipped

- **Slice 1 — team-denominated capacity** (`00ace1b0`). `ShuttleType` now
  declares `teams` and derives `capacity = teams * Squad.FIRE_TEAM_SIZE`, so
  the table reads in the unit it means and a partial team is unrepresentable
  rather than merely absent. 1 team for the small hulls, 2 for the
  freighters, 3 for the Valkyrie, exactly as designed.
- **Slice 2 — needed no code** (`00ace1b0`). The plan was to scope
  `AirSystem`'s per-cycle `squadId` reset. Once campaign personnel resolve
  their squad from C1's `(campaign squad, LZ)` index instead of from
  `mission.squadId`, the reset stops mattering: the next deboard looks the
  same squad up again. The mission id is still written back each deboard so
  the shuttle keeps its rear-overwatch hover on the squad it delivered, and
  generated waves keep today's behaviour with no branch added for them.
- **Slice 3 — form-up gate** (`6e3908b0`). `SquadFormUpSystem` clears the
  advancing assignment of a squad that is still arriving, running once after
  the commander pass so all six `MissionCommand` implementations are covered
  at one seam.

### What slice 3 found

The story budgeted for a new posture — "arriving teams rally at the LZ" — and
none was needed. Two facts about the shipped AI make clearing the assignment
sufficient:

- `RoutinePatrol` is **DEFENDER-only**, so an unassigned marine squad does not
  wander off looking for a patrol route.
- `EliminateEnemiesGoal` needs enemies the squad can actually see, so an
  unassigned squad with nothing in sight simply stays where it landed, and one
  that *is* engaged still fights.

So "hold at the LZ" is the ambient behaviour already, and the gate only has to
stop the commander from overriding it.

The other question the story left open — how long the timeout should be, and
whether it scales with lifts still inbound — is answered with a flat
`FORM_UP_TIMEOUT = 60f` and a note. A scan of live shuttle missions for
"anything still inbound for this squad" would need no constant at all and is
the better answer if 60s proves wrong in play.

### Known gap, deliberate

A squad split across two landing zones sets `expectedSize` to the whole
squad's manifest strength at **both** zones, because the manifest cannot know
which seats will be assigned where. Neither half can reach it, so both wait
out the timeout before stepping off. The timeout keeps it from deadlocking,
and single-LZ is the shipped case, but this is the reason a split drop feels
sluggish if it ever comes up.

## Decision this story implements

*Revised 2026-08-22 alongside C7 (`c7-organization-and-ranks.md`)'s
twelve-marine squad.* Transport capacity is denominated in **fire teams of
four**, with a floor of one whole team per lift. A squad (three teams)
arrives in one to three passes depending on the hull, later arrivals join
the same battle squad, and the squad forms up before it is committed
forward.

The earlier version of this story used a floor of one whole *squad* per
lift. At twelve that would put a rifle squad inside a Kite, which the
fiction will not carry. Denominating in teams keeps the lift believable,
keeps the numbers close to today's, and makes C1's join-and-catch-up
machinery load-bearing rather than an edge case.

## Problem

### The capacity table divides into nothing

`ShuttleType` capacities today, against a twelve-marine squad of three
four-marine teams:

| Hull | Seats | Hull | Seats |
| --- | --- | --- | --- |
| Hermes | 3 | Buffalo | 6 |
| Aeroshuttle | 4 | Mule | 6 |
| Kite | 4 | Nebula | 7 |
| Mudskipper | 4 | Valkyrie | 8 |
| Shepherd | 4 | | |
| Wayfarer | 4 | Tarsus | 5 |

Not one of 3, 5, 6, 7 divides into a fire team. A 6-seat Buffalo delivers a
team and a half; a 7-seat Nebula delivers a team and three strangers. The
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

### Capacity in team-slots

Every hull carries a whole number of four-marine teams:

| Hull | Seats now | Teams | Seats |
| --- | ---: | ---: | ---: |
| Hermes | 3 | 1 | 4 |
| Aeroshuttle, Kite, Mudskipper, Shepherd, Wayfarer | 4 | 1 | 4 |
| Tarsus, Buffalo, Mule, Nebula | 5–7 | 2 | 8 |
| Valkyrie | 8 | 3 | **12** |

Only a Valkyrie puts a whole squad on the ground in one pass — which is a
good piece of fiction: the dedicated assault transport is the one that
delivers a squad intact, and everything else trickles.

- **Differentiation moves off seat count.** The small end already differs
  on turn rate, acceleration, lateral damping, hardpoints, HP, and loiter
  time (`AirHandling` + the turret kit). Flattening 3 → 4 and 5/6/7 → 8
  costs nothing the player was reading; a Hermes stays a fast, fragile,
  one-hardpoint courier that happens to fit a team.
- **Equipment instead of marines** is already a modelled concept: the
  `AirDeliveryPayload` seam has `InfantryPayload` and `MechSupportPayload`
  side by side. A team-slot spent on equipment is that seam, not a new
  mechanism.
- The change is also *smaller* than the earlier squad-floor plan: only
  Hermes moves at the bottom of the table, where the squad floor would have
  raised six hulls by 50%.

### One squad, however many passes

- With C1's `(campaign squad, LZ)` minting key, later arrivals **join** the
  existing battle squad instead of minting a new one. That requires scoping
  `AirSystem`'s per-cycle `squadId` reset so it applies to generated
  personnel (militia waves, walk-in reinforcements) but not to campaign
  squads with an identity.
- `Squad.originalSize` increments per deboard today and morale caps on
  `aliveMembers / originalSize` — a joining wave must keep incrementing it
  rather than resetting, so the squad's morale ceiling rises as it
  assembles instead of reading as a half-strength unit.

### Form up before committing — the trickle trap

A twelve-marine squad arriving four at a time is **defeat in detail** if
the commander pushes each team forward as it lands, and under the 9×
lethality scale that is a wipe rather than a setback. The catch-up rule
alone does not prevent it: catching up means walking to a squad that is
already in contact.

So a squad that is still assembling holds at its landing zone. Concretely:
its commander assignment does not advance until either the squad is at
full landed strength or a timeout expires (a mission that never gets its
third lift must not deadlock). Arriving teams rally at the LZ, and the
squad steps off as a squad.

This is a commander-tier gate, not a new behavior: it is a condition on
when `MissionCommand` issues the squad's first advancing assignment. The
LZ-guard posture the rescue missions already use is the nearest shipped
precedent.

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

1. ~~**Team-denominated capacity.**~~ Shipped.
2. ~~**Scope the per-cycle reset.**~~ Shipped — as nothing; see above.
3. ~~**Form-up gate** before the commander advances an assembling squad.~~
   Shipped.
4. **Rejoin state for late arrivals.** *Remaining.* A marine who lands while
   their squad is forty cells forward must path to it without soloing into
   contact, and must not be treated as a full member of an ENGAGED squad's
   plan the instant they spawn. The machinery exists — `RegroupPosture` plus
   `InfantryCohesion.cohesionOverride`, selected whenever a downstream
   posture needs `WITHIN_COHESION_RADIUS`. What is missing is the *intent*: a
   fresh arrival should be explicitly rejoining rather than inheriting the
   squad's current plan from spawn. Build it on that cohesion layer; a second
   cohesion mechanism would fight the first. Note the form-up gate has
   already removed the worst case (the whole squad advancing while a third of
   it is still in the air), so what is left is the genuinely-late arrival
   after a timeout or a replacement wave.

## Acceptance

- No lift in a player manifest carries a partial fire team.
- A shuttle flying three cycles for one squad produces **one** battle
  squad, with `originalSize` equal to the marines actually landed.
- An assembling squad holds at its LZ and steps off as a squad; a lift that
  never arrives times out instead of deadlocking the mission.
- A late arrival paths to its squad without soloing into contact, and
  behaves normally once it arrives.
- Militia, walk-in reinforcement, and employer spawns are unchanged.
- **Balance re-tune, decided up front.** *Settled 2026-08-22: take the
  capacity change now and re-tune around it.* The two Independent opening
  jobs are mid-playtest against today's seats, so their force ratios get
  re-derived after this lands, not before — tuning against numbers we
  intend to replace is wasted work. Note the team-denominated table is a
  much smaller disturbance than the earlier squad-floor plan (only Hermes
  moves at the bottom), but the *squad* the player fields is now twelve
  marines rather than six, which moves the ratios regardless. Flagged in
  [`campaign/early-operations/next-session.md`](../../campaign/early-operations/next-session.md).

## Files touched

- `battle/air/ShuttleType.java` — capacities.
- `battle/air/AirSystem.java` — scoped cycle reset (line ~471).
- `battle/air/InfantryPayload.java` — join path (shared with C1).
- `ops/detachment/DetachmentResolver.java`, `ops/MarineOpsContext.java` —
  seat derivation.
- `battle/infantry/` — the rejoin state, on the existing cohesion layer.
- `battle/command/` — the form-up gate, as a condition on the first
  advancing assignment.

## Out of scope

- Building the equipment payload itself. The team-slot model reserves the
  room; `MechSupportPayload` already proves the seam. Wiring a chooseable
  "this slot carries a vehicle" is a separate story, and probably belongs
  to the convoy or command-powers track rather than here.
- Changing how the player's fleet maps to transports
  (`PlayerFleetShuttles` scanning) beyond the capacity numbers.
- Drop-zone selection, AA, or wave timing — the drop-ship invasion ladder
  in [`vanilla-combat-bridge/`](../../vanilla-combat-bridge/overview.md)
  owns those.

## Open questions

- Does the four-seat floor make the small hulls interchangeable in the
  player's mental model, even if they differ on handling? If it does, the
  answer is probably to differentiate on **cycles** — a light hull flies
  more, faster sorties for the same total delivery — rather than to
  reintroduce partial teams.
- Should a heavy hull's spare team-slot be selectable (marines vs.
  equipment) at briefing, or resolved automatically from what the player
  brought? Selectable is better play; automatic is shippable sooner.
- How long should the form-up timeout be, and should it scale with the
  number of lifts still inbound? Too short and the gate does nothing; too
  long and a squad stands at the LZ while the mission burns.
