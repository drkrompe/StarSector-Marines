# Marine package

## Roster persistence

`MarineRosterScript` is an `EveryFrameScript` registered on the sector via
`Global.getSector().addScript(...)`. Starsector's xstream save format walks
the script graph, so any plain `Serializable` POJO held by a registered
script — including the captain list — round-trips through save/load with no
custom serialization. `MarineRosterScript.getInstance()` finds the registered
instance by scanning `sector.getScripts()`.

The same persisted graph owns `MarineArmory` and `MarineSoldier`. Permanent
equipment-template ownership, custom squad definitions, soldier
identity/aptitude/status/enlisted rank, materialized equipment, and the
per-soldier `SoldierCareer` service record must remain plain serializable data.
A marine persists no experience number: their band is resolved from the armour
they are wearing (`SquadExperienceStandard`), so there is nothing to migrate.
Legacy recipe ids are migration input only. `readResolve` backfills new
collections/objects and maps them to stable equipment-template ids without
stripping capabilities from old saves.

**There is no counted armoury inventory, and nothing should add one back.**
Equipment is owned as permanent template cards; issuing kit against a card
spends fleet cargo through `EquipmentTemplateCost`. The former print-stock
economy — a per-item owned count, per-soldier allocation reserved against it,
and fabrication materials to print more — was deleted once it had no live
callers left. Anything that needs to cost the player materiel must reach the
counted things: fleet cargo, or the mech bay's spare components.

New campaigns contain only structural organization: the non-deployable reserve
pool and an empty four-gantry support lance. Fleet Armory squad founding is one
atomic cargo transaction for twelve generic Marines plus their baseline stores;
it creates the named line squad only after the complete bill can be consumed.
Mech chassis are likewise player-fabricated in the Mech Lab. Do not restore a
free campaign complement or constructor-seeded mech. `bootstrapInitialComplement`
and `MechBay.legacyStarterFixture()` exist for detached fixtures and save
compatibility, not ordinary campaign setup.

## Derived organizational state

Two pieces of squad structure are **derived, not authored**, and nothing should
write them directly:

- **Fire-team membership** — `MarineSquad.teamIndexOf` is roster position over
  `TEAM_SIZE`. Storing it would be a second source of truth to keep in sync on
  every transfer; deriving it also means an under-strength squad consolidates
  into fewer full teams instead of keeping hollow ones.
- **Enlisted rank and the squad leader** — `MarineRoster.refreshLeadership`
  re-derives both after any membership, fitness, **or issued-armour** change,
  and `readResolve` calls it too. Rank follows the billet, so a new mutator that
  adds, removes, or changes the fitness of a soldier must call it or the squad
  will keep pointing at a leader who is gone. Armour counts because it sets the
  experience band and sergeant's stripes follow that band — a refit that skips
  the refresh leaves rank and band disagreeing.

Squad **equipment doctrine assignment is authored state**, not another derived
rollup. `MarineSquad` holds one weapon doctrine id and one armor doctrine id;
`MarineRoster.applySquadEquipment` changes both only after the complete
twelve-billet template and cargo transaction succeeds. It prices changed incoming
kit, never refunds removed kit, and spends nothing until the full issue can commit.
`MarineArmory` persists a custom **weapon** definition catalog while built-ins
remain immutable fixtures; authoring consumes nothing but may reference only
collected templates. **There is no custom armour catalog and nothing should add
one back.** An armour doctrine id always names a `SquadArmorPlan`, and
`armorDoctrineById` resolves it through `ArmorIssueResolver` against what the
armoury owns — a stored twelve is frozen kit, which is the artifact the plan model
exists to remove. Legacy per-team template ids remain save input only:
`MarineRoster.readResolve` composes complete legacy weapon intent into a
deterministic custom definition without changing kits, assigns the starter tactic
sheet rather than preserving the card's twelve concrete patterns, and the first
successful squad issue clears those ids. The current per-soldier kit remains the materialized
state consumed by deployment and battle.

Built-in starter definitions are exact distributions, not best-fit suggestions.
Their first billet in each four-person team receives the leader's scarce weapon and
armor issue. `MarineArmory` must seed or repair every matching template card on new
and migrated saves; the campaign resource authority separately decides whether the
fleet has enough cargo to materialize that baseline pair.

Equipment-template acquisition enters the campaign through a parameterized
special cargo item. Its data is the stable equipment-template id; learning it
writes only to `MarineArmory`. Do not implement `BlueprintProviderItem` or call
player-faction known-hull, known-fighter, known-weapon, or known-industry APIs
for infantry templates, because those are ship-production authorities. Invalid
cards, duplicates, and an unavailable roster are non-destructive and must not
consume cargo.

When adding new persistent gameplay state, prefer this pattern: a thin
`EveryFrameScript` holding POJOs, registered once in `onGameLoad` (idempotent —
check via `getInstance()` first). Don't reach for `MemoryAPI` unless the data
is genuinely just key/value primitives.
