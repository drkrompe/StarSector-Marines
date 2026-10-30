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
Equipment is owned as permanent technology specs; the Machine Shop fits and swaps
learned kit freely without reading or spending fleet cargo. The former print-stock
economy — a per-item owned count, per-soldier allocation reserved against it,
and fabrication materials to print more — was deleted once it had no live
callers left. Anything that needs to cost the player materiel must reach the
counted things: fleet cargo, the mech bay's spare components, or the boat
deck's spares.

New campaigns contain only structural organization: the non-deployable reserve
pool and an empty four-gantry support lance. Fleet Armory squad founding is one
atomic transaction, anywhere: twelve generic Marines out of cargo plus one
credit price for their baseline stores (`SquadFoundingCost`); it creates the named line
squad only after the complete bill can be consumed.
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
- **Seat kit** — what each marine carries is what the squad's two cards issue the
  seat they hold. `MarineRoster.refreshLeadership` dresses every seat from the
  cards first (`conformKit`), so a replacement, a transfer, or a casualty that
  moves everyone up a seat can never leave a marine in kit the cards do not
  describe. Never set a squad member's kit directly; change the squad's cards.
- **Enlisted rank and the squad leader** — `MarineRoster.refreshLeadership`
  re-derives both after any membership, fitness, **or issued-armour** change,
  and `readResolve` calls it too. Rank follows the billet, so a new mutator that
  adds, removes, or changes the fitness of a soldier must call it or the squad
  will keep pointing at a leader who is gone. Armour counts because it sets the
  experience band and sergeant's stripes follow that band — a refit that skips
  the refresh leaves rank and band disagreeing.

Squad **cards are authored state**, the only authored kit there is. `MarineSquad`
holds one weapon doctrine id and one armor doctrine id (plus, for the debug
company only, per-seat primary grades); every line squad holds both from
`createSquad` on. `MarineRoster.applySquadEquipment` changes both only after the
complete twelve-billet technology validation succeeds. Fitting is free; stationing
and unknown technology remain blockers. Saved squad profiles are gone: `SquadProfile`
and the fields that held it survive only so old saves load, and the roster's
`readResolve` turns an assigned profile into the squad's cards.
**The card is the unit of access.** `MarineArmory.knownLoadoutCardIds` is the
company's collection, and a squad may be given only cards in it
(`knowsWeaponCard`, `knowsArmorPlan`). Learning a card records the equipment it
names beneath it, but owning equipment opens nothing on its own: owning every suit of
a sheet does not teach the sheet. Victory milestones (`VictoryMilestones`, paid once)
and the safety net (`EquipmentCollectionCurve`) grant cards, never equipment.
**The player never authors a card, and nothing should add a designer back.**
`MarineArmory.customWeaponDoctrines` holds only developer-registered weapon cards
under fixed ids (`ensureWeaponDoctrine`: the Wayline paths, migration, debug) and
any card an old save's designer made, which keeps issuing. **There is no custom
armour catalog either.** An armour doctrine id always names a `SquadArmorPlan`, and
`armorDoctrineById` resolves it through `ArmorIssueResolver.issueSheet`: every sheet
issues exactly the patterns its card carries (`ArmorSheetBundles`), never whatever else
the armoury happens to own, so a card is the same squad for every company. A seated
sheet issues each billet the suit it names; for an unseated one, roles and tradition
decide which bundled pattern fills which billet. Legacy per-team template ids remain save input only:
`MarineRoster.readResolve` composes complete legacy weapon intent into a
deterministic custom definition without changing kits, assigns the starter tactic
sheet rather than preserving the card's twelve concrete patterns, and the first
successful squad issue clears those ids. The current per-soldier kit remains the materialized
state consumed by deployment and battle.

New companies know only the cards the Kit Ladder places on `start` (Dockside Issue and
Watchman's Kit) and the equipment they name (`MarineArmory.startCards`): the baseline is
data, so tests read it from there rather than naming its pieces.
Founding and ordinary recruits use this baseline; there is no seeded special weapon.
Other built-in definitions remain library intent and require learned technology.
Save repair restores only this baseline and preserves every previously learned spec.
The collection safety net starts from three specs, never the old broad starter library.
The campaign founding authority separately charges for personnel and baseline stores;
equipment fitting itself is free.

Equipment acquisition enters the campaign as a **loadout card**: a parameterized
special cargo item (`LoadoutCardItemPlugin`) whose data is a stable card id naming a
Weapon Loadout or Tactic Sheet. Patrons and salvage hand out only these as cargo; **markets
sell them through the company store** (`EquipmentStore`), which never stocks market cargo:
a purchase pays from the company purse and learns the card directly. Do not put a card
back on a market's submarket; `LegacyMarketCardPurge` clears old saves. The store sells
three kinds of technology (loadout cards, mech patterns, boat parts: `EquipmentStore.Kind`),
each learned straight into its owner (`MarineArmory`, `MechBay`, `BoatDeck`). A boat part the
yard does not build a boat with must be learned before `BoatWorkshop` builds another.
`LoadoutCardLearning` teaches the whole loadout (its templates, and for a sheet the
sheet itself) and writes only to `MarineArmory`. The older single-template item
(`EquipmentTemplateCardItemPlugin`) survives for story rewards and held saves. Do not implement `BlueprintProviderItem` or call
player-faction known-hull, known-fighter, known-weapon, or known-industry APIs
for infantry templates, because those are ship-production authorities. Invalid
cards, duplicates, and an unavailable roster are non-destructive and must not
consume cargo.

When adding new persistent gameplay state, prefer this pattern: a thin
`EveryFrameScript` holding POJOs, registered once in `onGameLoad` (idempotent —
check via `getInstance()` first). Don't reach for `MemoryAPI` unless the data
is genuinely just key/value primitives.
