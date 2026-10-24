# Heart mission depth: from brawl to a thought-through battle

Status: ACTIVE. Beats, allied forces and the allied commander shipped; the maps' last pieces are left, then tuning. Seeds: the pooled 2, 3 and 5 on every biome (`story-map-pools.md`).

Written: 2026-10-23
Updated: 2026-10-24 — lanes no longer stall (stragglers, columns joining at the front, neighbour-only re-aims); the whole-pool reading added; the company wipe and gunship losses join the open list.

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
sorties, 70 shoots, 25 road cuts, 67 re-aims, 52 gaps closed). The evidence does
not record aircraft lost, so how many sorties were shot down is unread.

## What is left

Ranked by what it unblocks:

1. **Broad Front's company wipe.** Five seeds lose the company at about the same
   time, on every biome for seed 5: a consistent cause (landing, a surge, the
   lanes leaving it alone), to find with `battleProbe` before any tuning.
2. **Gunship losses in the evidence.** A beat when an allied aircraft is lost, so
   the owner's gunship question can be read across the pool.
3. **Maps with intent** (`heart-map-intent.md`). Built: the cordon ridge, the
   brook valley, the hollow's rim, the cordon's works, every beat's set piece
   that needs ground (F6's road satellite with it), the town and city densities,
   and a pool for each density (three seeds per biome). Open:
   - a city's supply road: no city map lays it, so F6 has no road to cut there
     (the Cordon takes a city's streets as its supply road); the cordon ridge and
     pieces of the forward line fail on most city maps too;
   - a full hollow;
   - the knoll.
4. **Tuning win rates**, only after the map work (owner: no overtuning yet).

## Constraints

- `heart-nouns.md` stays the model: new nouns amend it rather than live here.
- Owner choices go to the picks page as pictures, relayed through the
  coordinator.
