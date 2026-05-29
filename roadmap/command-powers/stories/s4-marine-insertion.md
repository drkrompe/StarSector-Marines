# S4 — Marine Insertion power

> Player-invoked squad drop. Ships at a *fixed safe LZ* first — the geography
> layer (LZ choice + air defence) is deliberately deferred to S6.

## Goal

A drop power: invoke → an inbound shuttle flies in → deboards a fresh squad at a
fixed LZ → the squad joins the battle. Closes the convoy overview's open
question ("does the player ever call a friendly drop?" — yes, via this power).

## Scope

**In:**
- Reuse the shuttle / air delivery vector (`battle/air/`, `ShuttleType` /
  `AirSystem`) to fly an inbound manned shuttle to a fixed LZ and deboard a
  squad.
- The deboarded squad enters the existing **free-agent / commander pool** (same
  path convoy-spawned squads use today).
- Cost: CP + crew-at-risk if the shuttle is contested (basic — full AA
  counterplay is S6).

**Out:**
- LZ *choice*, air defence, craft-class risk/reward (S6).
- Forward operating base (S7), fighter/light-craft insertion variant.

## Design notes

- **Manned** shuttle → crew is on the line (overview § cost layer 3); the
  automated/drone alternative and the heavy-vs-light craft trade arrive with S6.
- Sync the shuttle's render position from its `AirBody` each tick or shots fire
  from spawn while the sprite orbits ([[air_unit_render_sync]]).
- Commander-loop wiring for vehicle/air-spawned squads is noted as open in the
  convoy overview — this story either reuses the free-agent pool as-is or
  threads explicit wiring; pick the lighter path that works.

## Dependencies

- S1 (framework), S2 (gating: requires a troop-transport source ship, e.g.
  Valkyrie).
- The air/shuttle system and the reinforcement free-agent pool.

## Acceptance

With a Valkyrie committed, the drop power is slottable; firing it sends a shuttle
to the fixed LZ, which deboards a squad that then fights under the commander
loop; CP is deducted and the cooldown starts.
