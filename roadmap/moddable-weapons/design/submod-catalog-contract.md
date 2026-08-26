# Submod catalog contract

Status: ACTIVE

Written: 2026-08-25

Updated: 2026-08-25 — documented the required marine-primary held-sprite family.

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
  "tilesets": ["data/my_faction/colony.tileset.json"],
  "tileMappings": ["data/my_faction/colony.mapping.json"],
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

1. tilesets;
2. tile mappings;
3. weapons;
4. special equipment and armor;
5. collectible equipment templates;
6. faction ground rosters.

References therefore resolve eagerly and fail at application load. A primary
template must name a `marine-primary` weapon. A special must reference a
`marine-secondary` weapon when its activation is weapon-like. Armor, template,
and roster references must exist before their consumers are installed.

A `marine-primary` weapon also declares `render.heldSpriteFamily`. The supported
modular actor families are `RIFLE`, `LASER_GUN`, `SMG`, and `DMR`; several weapon
definitions may intentionally share one family. The family controls the weapon held
by the layered marine actor in battle, Armory portraits, and embedded scenes. It is
required and validated so a contributed id cannot quietly fall back to generic art.

## Tilesets and mappings

`tilesets` files add stable tile, grid-block, and doodad definitions to one
shared id namespace. `tileMappings` files add named doodad pools and unclaimed
ground-render, filler, or macro-height keys. Every mapping reference must
resolve after all tilesets load. A contributed mapping cannot replace a core
pool or policy key; such a collision reports both providers and stops load.

Definition ids and mapping names should use the provider's namespace. Sheet
and auxiliary texture paths should also be namespaced because Starsector art
resources inhabit a shared path namespace even though catalog JSON is read
from its declaring mod exactly.

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

## Runtime consumption

Contributed marine-primary weapons, armor, and special equipment are valid
faction-roster and player issue. Once their cards are learned, they join the
doctrine editor's choices in manifest order. Saved doctrines, squad billets,
and marines retain the catalog equipment id; issuing changed kit charges the
contributed card's authored cargo cost, and deployment resolves the same
definitions without enum constants. A special's closed activation and AI
policy select the built-in typed executor while its referenced weapon and
presentation data supply ballistics, audio, sprites, and carrier layers.

Removing the provider repairs unresolved player primaries to
`weapon.field-rifle`, armor to `armor.field-fatigues`, and unresolved special
equipment to an empty slot, with a warning rather than a corrupt roster.
