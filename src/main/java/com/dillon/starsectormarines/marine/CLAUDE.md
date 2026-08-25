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

Squad **equipment doctrine assignment is authored state**, not another derived
rollup. `MarineSquad` holds one weapon doctrine id and one armor doctrine id;
`MarineRoster.applySquadEquipment` changes both only after the complete
twelve-billet inventory transaction succeeds. `MarineArmory` persists custom
Weapon and Armor definition catalogs while built-ins remain immutable fixtures;
authoring never checks or consumes inventory. Legacy per-team template ids remain
save input only: `MarineRoster.readResolve` composes complete legacy intent into
deterministic custom definitions without changing kits, and the first successful
squad issue clears those ids. The current per-soldier kit remains the materialized
state consumed by deployment and battle.

Built-in starter definitions are exact distributions, not best-fit suggestions.
Their first billet in each four-person team receives the leader's scarce weapon and
armor issue, and `MarineArmory` must seed or repair enough matching stock for that
baseline pair to remain immediately issuable on both new and migrated saves.

When adding new persistent gameplay state, prefer this pattern: a thin
`EveryFrameScript` holding POJOs, registered once in `onGameLoad` (idempotent —
check via `getInstance()` first). Don't reach for `MemoryAPI` unless the data
is genuinely just key/value primitives.
