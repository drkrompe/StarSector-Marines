# Ruins expedition battle

Status: PLANNED — owner direction; nothing built. Slice 3 of
`ruins-expedition-fork.md`.

Written: 2026-11-09

Read `ruins-expedition.md` in full first (Shape of the battle, Occupants,
Briefing), then `extraction-command.md` (Generic recovery law) and
`loot-nouns.md` (Optional field caches).

## Goal

The fork's "send the company" plays a real expedition: land, delve to the vault,
hold it through the breach time, walk the haul out to the lift. It plays on an
existing map until `ruins-ground.md` lands, against a scavenger crew sized by the
ruins grade. The fork story's commit path reads its won or lost.

## Slices

1. **The variant.** A Ruins Expedition is an Extraction mission with its own
   setup, not a new `MissionType`: the vault is the recovery source, the egress
   is the first landing area, the breach time is the grade's (30, 45, 60, 90 s),
   and the haul is the generic abstract package. It has no employer, no allies
   and no employer support. The payload projection names the source "the vault"
   and the package "the haul". Unit-test the grade-to-setup mapping and the
   terminal outcomes on the recovery rule's own inputs.
2. **Stand-in ground.** Pick the existing map nearest a ruin (a decivilized or
   low-density grown settlement, `SettlementLink.NONE`) with the vault placed at
   the far end from the landing by route length, and size it by grade as far as
   the existing generator allows. A ruins map that cannot place a reachable vault
   is a setup error, not a fallback.
3. **Deeper caches.** The grade's cache count (2 to 5) on the existing field-cache
   placement, weighted to the far half of the route. Field finds settle as today.
4. **Scavengers.** The pirate ground roster under the generic conventional
   defenders: posted squads at a camp near the landing end and at the vault, a
   patrol pool, the alarm. Strength by grade (light to heavy), set as a budget
   this slice tunes. Their stash is one extra cache at the camp.
5. **Briefing.** The expedition's briefing shows the grade, the occupant line
   for scavengers and the grade's strength band (`ruins-expedition.md`,
   Briefing), with understrength launch as any operation.
6. **Evidence.** A `commanderEvidence` fixture per grade on the stand-in ground,
   headless: the haul boards, or the expedition loses by the recovery law's own
   terminal. That proves the mission works, not that it is hard enough; balance
   is a played battle (`playBattle -Pbattle=<source>`).

## Constraints

- No change to the generic recovery law. Anything the expedition needs that the
  law lacks is raised as a change to `extraction-command.md` first.
- The vault's haul stays vanilla's. The battle reports won or lost and field
  finds, nothing else.
- Do not touch the fork's rules or `MarineRuinsCMD` beyond reading the outcome
  the commit path already expects.

## Done

All six slices pass their tests and evidence and a played expedition on each
grade has been seen by the owner. Fold the lasting rules into
`ruins-expedition.md` and `extraction-command.md`, add a row to `shipped.md`,
and delete this story.
