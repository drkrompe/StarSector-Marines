# Story — Ship on Station power

Status: PLANNED

Written: 2026-10-13

Slice 1 of `ship-support.md`. Read it, `command-powers-nouns.md` and `air-nouns.md`
(**Overhead ships**) first.

## Goal

A committed ship is called over the battle through the command deck, by one mechanism
that a campaign mission, a debug mission and an employer offer all feed.

## Scope

1. **Catalog.** `PowerCatalog` maps eligible committed hulls to a new `ship_on_station`
   power: cruisers and capitals with at least one weapon the warship body can fire at
   the ground, and the Shepherd as a tender. Each eligible member is its own source and
   its own charge; the deck shows one card whose charge count is the number of
   committed ships.
2. **Frozen loadout.** The detachment carries, per source, a frozen loadout (hull id,
   variant or slot-to-weapon map, ship name, whether it is a tender), built from the
   member at launch through the existing `WarshipLoadout` seam. No campaign object
   enters the battle.
3. **Activation.** Targets a seen cell. Commits supplies (campaign) or nothing (debug)
   as other powers do. Fits the next unused source, places it at the company's map
   edge (`DebugWarship.place`'s rule), stations it near the target cell, sets the
   stance from the company's mission side (ATTACK when the company attacks, DEFEND when
   it defends), turns on the director and doctrine, `stayFor` the station time, and
   adds it to the battle. A tender is added through the tender system and builds by
   its stance director.
4. **Debug and employer sources.** The briefing's WARSHIP DEBUG picker stops installing
   at launch and instead contributes a stock source for this power on debug missions.
   An employer may offer the power with a named stock hull. Cold Account's scripted
   Onslaught is untouched in this slice (it is an authored intervention, not a call).
5. **Readouts.** The power card shows the source ship names and remaining charges;
   the ship's existing debug status line and direct control are unchanged.

## Constraints

- Command-power laws hold: commitment before slotting, the roster frozen at launch,
  activation owned by the simulation, no spend on a refused request.
- No damage or balance change to any ship. No writeback (slice 2).
- Deterministic: activation is a queued simulation request like every other power.

## Acceptance

- Unit tests: the catalog's source mapping (cruiser and capital yes, frigate no,
  Shepherd as tender, held-back member removed); the frozen loadout round-trips a
  member's slot weapons; activation spends one charge, refuses an unseen cell without
  spending, and fits the next source.
- A behaviour scene `ship-support-call`: the same world with the power activated on a
  defended compound and with no activation; verdicts that the ship arrives from the
  company's edge, stations near the target, fires under the right stance, departs when
  its station time is spent; and a tender loop that builds.
- `sceneEvidence` and targeted tests green.

## Open

The owner questions in `ship-support.md` (source hulls, station time, loss severity)
use the drafted answers until decided.
