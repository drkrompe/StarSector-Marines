# S6 — Drop geography: dynamic LZs + air defence

> Design-forward stub. Turns the LZ into the limited/incentivized player choice
> the playtest feedback asked for. Full design in overview § "Landing zones &
> drop geography".

## Goal

Make landing zones a constrained player choice, gated by **air defence** and
unlocked by advancing. Establish the reach loop:

> push forward → clear enemy air defence → a closer LZ lights up → reinforce
> deeper/faster → push further.

## Scope sketch

**In:**
- AA coverage on the map that contests insertions, discriminating by craft
  class: **heavy shuttle** (big squad, needs a safe/cleared LZ) vs **light
  craft / fighters** (nimble, can punch a hot LZ, deliver less, risk crew).
- LZ unlock as territory is cleared / AA neutralized.
- Per-drop risk/reward dial reusing the manned-vs-automated + CR/crew cost
  model (overview § cost layers, § counterplay).

**Out:**
- Forward operating base (S7).

## Open

- Do unlocked LZs persist for the battle, or can the enemy re-contest them
  (re-establishing AA, retaking a node)? Pairs with conquest tug-of-war v2.

## Dependencies

- S4 (basic insertion).
- **flyby → `AirBody` real-air-entity promotion** (currently a parked backlog
  item) so shuttles/fighters can actually be shot down — this story is a prime
  reason to land that refactor.
- Fog-of-war, conquest node/territory state.
