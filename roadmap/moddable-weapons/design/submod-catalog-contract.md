# Submod catalog contract

Status: ACTIVE

Written: 2026-08-25

Updated: 2026-08-25 — clarified optional manifest discovery through the game resource API.

## Provider entry point

An enabled provider opts into Starsector Marines content discovery by shipping
this exact path inside its own mod:

`data/marines/starsector-marines.catalog.json`

Discovery is performed through Starsector's provider-scoped resource API. An
enabled mod without this file contributes nothing; direct filesystem access is
not part of the provider contract.

The manifest is a small index, not a content catalog. Paths are relative to the
provider mod and explicitly ordered:

```json
{
  "schemaVersion": 1,
  "weapons": ["data/my_faction/infantry.weapon.json"],
  "specialEquipment": ["data/my_faction/specials.equipment.json"],
  "armor": ["data/my_faction/armor.armor.json"],
  "groundRosters": ["data/my_faction/ground.roster.json"],
  "equipmentTemplates": ["data/my_faction/templates.template.json"]
}
```

Every list is optional. Absolute paths and parent-directory traversal are
rejected. Catalog resources are loaded from the declaring mod exactly, so a
same-named file in another mod cannot replace it invisibly.

## Merge and ownership rules

Enabled-mod order determines ingestion and stable iteration order, but never
override priority. Contributions are add-only: one stable definition id and one
campaign faction id have one provider. A collision stops application load and
reports the first and second provider mod ids and resource paths. Authors must
namespace contributed ids; changing an established id is a compatibility break.

The core ground-roster catalog declares the global fallback. Provider roster
files normally omit `fallbackProfile` and contribute only profiles. An unknown
campaign faction still resolves to the core fallback; a faction id explicitly
listed by a provider resolves to that provider's profile.

## Dependency order

The runtime resolves catalogs in this order:

1. weapons;
2. special equipment and armor;
3. collectible equipment templates;
4. faction ground rosters.

References therefore resolve eagerly and fail at application load. A primary
template must name a `marine-primary` weapon. A special must reference a
`marine-secondary` weapon when its activation is weapon-like. Armor, template,
and roster references must exist before their consumers are installed.

## Equipment templates

Template catalogs declare eligibility and ordinary cargo issue cost. Primary
families list whichever grades are collectible; an omitted grade does not gain
a card. Armor and special entries each name one equipment id and one cost.
Missing cost resources mean zero.

```json
{
  "primaries": [
    {
      "equipmentId": "my_faction.weapon-needle-rifle",
      "grades": {
        "service": { "supplies": 3, "heavyArmaments": 1 },
        "milspec": { "supplies": 5, "heavyArmaments": 2 }
      }
    }
  ],
  "armor": [
    {
      "equipmentId": "my_faction.armor-ceramic",
      "issueCost": { "supplies": 3, "heavyMachinery": 1 }
    }
  ],
  "specialEquipment": [
    {
      "equipmentId": "my_faction.special-breacher",
      "issueCost": { "supplies": 2, "heavyArmaments": 1 }
    }
  ]
}
```

The derived card ids remain stable:

- `equipment-template:<weapon-id>:<grade>`
- `equipment-template:<armor-id>`
- `equipment-template:<special-id>`

Learning one through the Starsector special-item interaction adds it only to
the Marine Armory. It never enters vanilla ship-production knowledge.

## Current runtime edge

Contributed marine-primary weapons and armor are valid faction-roster and player
issue. Once their cards are learned, they join the doctrine editor's choices in
manifest order. Saved doctrines, squad billets, and marines retain the catalog
equipment id; issuing changed kit charges the contributed card's authored cargo
cost, and deployment resolves the same weapon and armor definitions without enum
constants. Removing the provider repairs unresolved player primaries to
`weapon.field-rifle` and armor to `armor.field-fatigues` with a warning rather
than corrupting the roster.

Special-equipment catalogs and cards are additive too, but generated and player
battle execution still requires one of the current built-in special ids until
the remaining `MarineSecondary` compatibility handle is removed in
`w5-submod-merge.md`. Unsupported special issue is fail-loud and must not be
silently omitted.
