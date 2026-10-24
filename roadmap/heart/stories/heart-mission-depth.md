# Heart mission depth: from brawl to a thought-through battle

Status: ACTIVE. Beats, allied forces and the allied commander shipped; the maps' last pieces are left, then tuning. Seeds: the pooled 2, 3 and 5 on every biome (`story-map-pools.md`).

Written: 2026-10-23
Updated: 2026-10-24 — a pool per density, a city's streets as its supply road; the company wipe and gunship risk fixed; the hollow and knoll remain.

## Why

Tuning the heart to win most seeds made it work as a brawl, not as a mission
worth playing (owner, 2026-10-23: "This is how we make this mission fun and not
just tune it to work as a brawl"). The owner set the order: maps first, then
the beats as optional objectives, then the allied forces, then the commander
using them. The beats, the forces and the commander are built.

## Reading (2026-10-24, the whole pool, main at 0ff5e8d43)

Broad Front: none of 12 won. Five lost, the company wiped between ticks 9,500
and 12,500 (seed 5 on every biome, seed 2 arid and frozen) with one or two
satellites down. Seven timed out at 27,000 with four or five satellites down and
at most one mother. The Burn: four of 12 won, one lost, seven timed out, most
with one mother down. On Broad Front every tool was called (118 armour calls, 36
sorties, 70 shoots, 25 road cuts, 67 re-aims, 52 gaps closed).

After the company fix and the owner's gunship answer (main at cebbdc3e3):
Broad Front none won, one lost, eleven timed out, most with the company alive
(10 to 22 marines) but no mother down; 44 sorties, 38 shot down
(`gunship_lost_N`). The Burn unchanged.

## What is left

Ranked by what it unblocks:

1. **Marines never shoot the spitters.** Frozen seed 2 loses the company at tick
   ~8,000, before its own satellite falls: 22 of 23 marines are killed by satellite
   3's spitters, 13 to 20 cells off, while every one of the marines' 96 hits in
   that half-minute lands on the runners and brutes charging them. Infantry target
   choice, not the heart's: shared by every mission, so its own piece of work.
2. **Maps with intent** (`heart-map-intent.md`). Built: the cordon ridge, the
   brook valley, the hollow's rim, the cordon's works, every beat's set piece
   that needs ground (F6's road satellite with it), the town and city densities,
   and a pool for each density (three seeds per biome). Open:
   - the cordon ridge and pieces of the forward line, which fail on most city
     maps (a city's supply road is its streets now);
   - a full hollow;
   - the knoll.
3. **Tuning win rates**, only after the map work (owner: no overtuning yet).

## Constraints

- `heart-nouns.md` stays the model: new nouns amend it rather than live here.
- Owner choices go to the picks page as pictures, relayed through the
  coordinator.
