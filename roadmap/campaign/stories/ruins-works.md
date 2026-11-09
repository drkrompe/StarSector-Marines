# Ruins works

Status: IN PROGRESS — slices 1 to 5 built, with the noise hook and a
`sceneEvidence` scene; the vault's own seal (and the scattered bunker's door)
waits on an owner pick, and the full chain is played once an empty site fields
nobody. Part of slice 5 of
`ruins-expedition-fork.md`, beside `ruins-occupants.md`.

Written: 2026-11-09

Read `ruins-expedition.md` (Shape of the battle: Works) first, then
`mission-command-nouns.md` (Side objectives, for the interaction and its world
prompt) and `garrisonables.md` (Sally doors).

## Goal

The owner's rule: every expedition gives gameplay and a reward. A ruins site's
works (seal doors, a power hall, junctions, collapses, a records terminal) are
the environmental puzzle of an empty site and optional detours on an occupied
one, and the sealed stores behind them are the reward.

## Slices

1. **The power graph.** A mission-owned graph: one power hall with a capacity,
   junctions that feed one of two branches, and consumers (seal doors, the
   vault's seal, a landing court's lift). Pure state, unit-tested: a consumer
   is powered when its branch is fed and capacity allows; throwing a junction
   re-feeds at once.
   *Built* (`PowerGraph`): the wiring is fixed from the delve, one junction per
   layer at its stand; a junction feeds either its layer's first side room
   (STORE) or the way on (ONWARD: the layer's seal gate, and at the last layer
   the vault's seal). Every junction starts ONWARD, and the capacity is the
   onward load plus one, so the player can feed exactly one store at a time on
   top of the way on. An overloaded hall powers nothing until load is shed;
   shedding brings it back without a second restart. No lift consumer yet.
2. **Seal doors.** A door span that stays shut and locked until its consumer is
   powered, built on the sally door's opening span. A breaching charge forces
   it open for good. A forced vault seal keeps the unpowered breach time.
   *Built* (`SealDoor`, `RuinsWorks`): each layer's first side room is a sealed
   store and each GATE way a seal gate, shut at setup as a copy of the nearest
   wall cell through `MapEditor`. A door opens while powered and shuts again
   once nobody stands in it or the room it keeps, dropping routes through it.
   Any breach of a shut door (any cell walkable again) forces it open for good.
   Not yet: the bunker door, and a forced vault seal reading the charge rule.
3. **The power hall and junctions.** Mission-owned interactions on the main
   path, drawn with the side objective's world prompt: infantry hold the hall's
   console to restart it, and work a junction either way. Restarting the hall
   is loud: it is a noise event the occupants' stories may read.
   *Built*: the hall's console (hold 6 s) and each junction's box (hold 2 s,
   one verb for the other branch) answer through the side objective's world
   prompt within 10 cells, worked by company infantry within 2.5 cells; the
   objectives rail carries a Site power row with the hall's load, each
   junction's branch and each door's state. Each store is a field cache behind
   its door (part of slice 5).
   *Built*, the noise: a restart wakes the Rav's nearest closed bore at once
   (`RuinsSleepers.loud`) and stirs the derelict zone keeping the hall, or the
   zone whose node is nearest (`SecurityNetwork.loud`).
4. **Collapses.** A breachable run in rubble materials across a short way, with
   a long way round. A charge clears it.
   *Built*: the collapse is the delve's own (`mapgen-nouns.md`, Ruins ground:
   the choked short way's wall line). The forced way is the player's, for a
   collapse and every shut seal door alike: the prompt offers SET CHARGE, and
   the first company satchel carrier within his contact range (plus 1.5 cells)
   of one of its cells sets a charge there (`SatchelChargeService.plantAtCell`).
   The blast that cuts a cell clears the collapse or forces the door. The rail
   marks the collapse CHOKED or CLEARED.
5. **Records terminal and sealed stores.** Reading the terminal marks the
   power plan on the map and reveals the hidden stores. Each store is a field
   cache behind a seal door; an empty site has two, an occupied site at most
   one per layer.
   *Built*: holding the terminal's console 4 s reads it. Until then every
   sealed store is unmarked and offers no prompt, and a junction says what it
   feeds only once it has been thrown (FEEDS ?, verb THROW); reading marks every
   store and junction. Still one store per layer on every site; the two-store
   empty site waits on slice 6.
6. **The empty site's chain.** On a scattered site with nobody: the vault seal
   unpowered, a collapse across the short way, the power hall in a side layer,
   capacity for the vault and one other consumer, every step visible from the
   one before. Laid by hand on the stand-in ground first, then by
   the ruins ground's works sites (`mapgen-nouns.md`, Ruins ground).
7. **Occupied sites.** Fewer works, all optional. The noise hook for the Rav
   and derelict zones lands with `ruins-occupants.md`. The derelict branch
   power-down is built (`derelict-security-network.md`, slice 8): each zone is
   fed by its node's layer's junction on the way-on branch, and throwing it
   to the store darkens the zone.
8. **Evidence.** `sceneEvidence` scenes for the chain: powered and forced vault,
   store opened, collapse cleared, with look shots for the owner.
   *Built*: `sceneEvidence -Pscene=ruins-works` (`RuinsWorksScene`): powered
   (a held console restarts the hall and the fed door opens), forced (the press
   sets a charge on the door and the cut door is forced) and collapse (likewise
   cleared). `createSnapshots -Psnapshot=ruins-works-look` shows a store sealed
   and powered open. The vault loops wait on its seal.

## Constraints

- Nothing is locked for good: every seal and collapse has a forced way, so a
  wrong choice costs time and charges, never the expedition.
- Stores hold the ordinary field-cache materials and settle as field finds.
  Equipment caches stay behind `loot-nouns.md`'s gate.
- Works state is battle state only; nothing about a site's works is stored
  between expeditions.

## Done

The owner has played an empty site and found it worth the time. Fold the works
into `ruins-expedition.md`, add a row to `shipped.md`, and delete this story.
