# Ruins works

Status: IN PROGRESS — slices 1 to 5 built, with the noise hook, the vault's
seal and the scattered bunker's door (built on the recommended pick while the
owner decides); whether the chain plays well is the owner's playtest. Part of
slice 5 of
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
   The vault's inner door is the vault seal, shut at setup and fed with the
   way on at the last layer: powered, it opens and the breach takes the
   powered time; forced, `RuinsVaultObjective.forceSeal` keeps the unpowered
   time for good. On scattered ground the bunker's one door is a seal door on
   the same branch. Once the vault is breached, no way-on door shuts again, so
   the haul is never shut in. Built on the recommended option (a) of the owner
   pick `ruins-vault-seal` (`ruins-vault-seal-options` suite); option (b) would
   drop the vault and bunker doors and keep the seal as the breach's length.
3. **The power hall and junctions.** Mission-owned interactions on the main
   path, drawn with the side objective's world prompt: infantry hold the hall's
   console to restart it, and work a junction either way. Restarting the hall
   is loud: it is a noise event the occupants' stories may read.
   *Built*: the hall's console (hold 6 s) and each junction's box (hold 2 s,
   one verb for the other branch) answer through the side objective's world
   prompt within 10 cells. A junction is worked by company infantry within 2.5
   cells; the hall (and the records office) by anyone in the building or within
   3 cells of its walls, since a squad sent into a building holds it from its
   tactical places, which need not be inside; the
   objectives rail carries a Site power row with the hall's load, each
   junction's branch and each door's state. Each store is a field cache behind
   its door (part of slice 5). Its find lies just inside the door and is
   recovered by holding the room the same way (`SalvageCache.heldFromRoom`):
   a squad sent to the open door stands two or three cells short of it.
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
8. **Look shots.** The rules are proven by unit tests where they are decided
   (`PowerGraphTest`, `RuinsWorksTest`, `RuinsVaultObjectiveTest`,
   `SalvageCacheTest`, `RuinsSleepersTest`, `SecurityNetworkTest`); no mission
   run or scene judges whether the company gets through (owner rule).
   `createSnapshots -Psnapshot=ruins-works-look` shows a store sealed and
   powered open; `ruins-vault-seal-options` frames the vault seal's pick.

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
