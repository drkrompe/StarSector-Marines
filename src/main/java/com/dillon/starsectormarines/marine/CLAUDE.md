# Marine package

## Roster persistence

`MarineRosterScript` is an `EveryFrameScript` registered on the sector via
`Global.getSector().addScript(...)`. Starsector's xstream save format walks
the script graph, so any plain `Serializable` POJO held by a registered
script — including the captain list — round-trips through save/load with no
custom serialization. `MarineRosterScript.getInstance()` finds the registered
instance by scanning `sector.getScripts()`.

The same persisted graph owns `MarineArmory` and `MarineSoldier`. Recipes, the shared
fabrication-material resource, printed inventory, soldier identity/aptitude/XP/status/enlisted
rank, equipment allocations and the per-soldier `SoldierCareer` service record must remain
plain serializable data. `readResolve` backfills new
collections/objects for legacy saves.

## Derived organizational state

Two pieces of squad structure are **derived, not authored**, and nothing should
write them directly:

- **Fire-team membership** — `MarineSquad.teamIndexOf` is roster position over
  `TEAM_SIZE`. Storing it would be a second source of truth to keep in sync on
  every transfer; deriving it also means an under-strength squad consolidates
  into fewer full teams instead of keeping hollow ones.
- **Enlisted rank and the squad leader** — `MarineRoster.refreshLeadership`
  re-derives both after any membership or fitness change, and `readResolve`
  calls it too. Rank follows the billet, so a new mutator that adds, removes,
  or changes the fitness of a soldier must call it or the squad will keep
  pointing at a leader who is gone.

Fire-team **template assignment is authored state**, not another derived
rollup. `MarineSquad` holds one template-card id for each of its three team
slots; `MarineRoster.applyFireTeamTemplate` changes that id only after the
complete four-billet inventory transaction succeeds. The current per-soldier
kit remains the materialized state consumed by deployment and battle.

When adding new persistent gameplay state, prefer this pattern: a thin
`EveryFrameScript` holding POJOs, registered once in `onGameLoad` (idempotent —
check via `getInstance()` first). Don't reach for `MemoryAPI` unless the data
is genuinely just key/value primitives.
