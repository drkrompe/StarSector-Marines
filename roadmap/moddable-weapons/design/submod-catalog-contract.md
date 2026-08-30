# Submod catalog contract

Status: ACTIVE

Written: 2026-08-25

Updated: 2026-08-30 — documented optional projectile-bound tracer tails; armour
reaches the field through a tactic sheet rather than a picker, and a contributed
pattern is no longer shadowed by the core one sharing its cell.

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
  "equipmentTemplates": ["data/my_faction/templates.template.json"],
  "factionEquipment": ["data/my_faction/faction-equipment.json"]
}
```

Every list is optional. Absolute paths and parent-directory traversal are
rejected. Catalog resources are loaded from the declaring mod exactly, so a
same-named file in another mod cannot replace it invisibly.

## Merge and ownership rules

Enabled-mod order determines ingestion and stable iteration order, but never
override priority. Contributions are add-only: one stable definition id and one
ground-roster campaign faction id have one provider. Faction equipment pools
are deliberately more open: several providers may add offers to one campaign
faction, but one faction/template/channel claim has one provider. A collision
stops application load and reports the first and second provider mod ids and
resource paths. Authors must namespace contributed ids; changing an established
id is a compatibility break.

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
6. faction equipment sources;
7. faction ground rosters.

References therefore resolve eagerly and fail at application load. A primary
template must name a `marine-primary` weapon. A special must reference a
`marine-secondary` weapon when its activation is weapon-like. Armor, template,
and roster references must exist before their consumers are installed.

A `marine-primary` weapon also declares `render.heldSpriteFamily`. The supported
modular actor families are `RIFLE`, `LASER_GUN`, `SMG`, and `DMR`; several weapon
definitions may intentionally share one family. The family controls the weapon held
by the layered marine actor in battle, Armory portraits, and embedded scenes. It is
required and validated so a contributed id cannot quietly fall back to generic art.

## Weapon effects

Every weapon definition declares an `fx` object with at least an ordered
`impact` layer list. Optional independent slots are `launch`, `muzzle`,
`tracer`, `trail`, and `aftermath`. Turret weapons additionally require a
`muzzle` slot; large area-effect turrets require `aftermath`, and traveling
interceptable turret rounds require `trail`.

```json
"fx": {
  "muzzle": [
    { "kind": "glow", "radius": 0.30, "lifetime": 0.06, "color": "FFF0C0" }
  ],
  "impact": [
    { "kind": "glow", "radius": 0.70, "lifetime": 0.16, "color": "FFE080" },
    { "kind": "fire", "radius": 0.55, "lifetime": 0.45 },
    { "kind": "smoke", "radius": [0.55, 0.80],
      "lifetime": [1.10, 1.50], "count": [2, 3], "jitter": 0.45 },
    { "kind": "dust", "radius": 0.55, "lifetime": 0.32 }
  ]
}
```

Available primitives are `glow`, `dust`, `smoke`, `fire`, `explosion`, and
`ring`. Positive `radius` and `lifetime` are required. A number is a fixed
value; `[minimum, maximum]` authors a deterministic seeded range. Optional
`count`, `jitter`, `delay`, local-frame offsets and velocities, and bounded
`emissionDuration` plus `emissionInterval` compose variation and lingering
effects without a new Java recipe. Unknown fields and unbounded schedules fail
catalog loading.

The same seeded composer supplies battle particles and catalog previews, so
layer order is visible behavior and does not depend on provider order. Retired
`render.impact`, `render.smokeTrail`, and `render.engineTrail` fields are errors;
author the corresponding effect slot instead. Effect layers are presentation
only and cannot change damage, penetration, hearing, or projectile simulation.

A weapon with a projectile sprite may also declare a positive
`render.tracerTailCells`. It draws a short line of that world-space length
behind the traveling sprite, tinted by `render.tracerColor`; omission or zero
disables the tail. A tail without `render.projectileSprite` is rejected. This
is presentation over the resolved flight and does not change round velocity,
contact, or arrival time.

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

Template catalogs declare campaign access tier and ordinary cargo issue cost. A
player-facing primary family declares all four grades; a partial grade matrix is
an authoring error rather than an accidental progression hole. Armor and special
entries each name one equipment id, access tier, and cost. Missing cost resources
mean zero. The closed access tiers are `common`, `advanced`, and `prestige`.

```json
{
  "primaries": [
    {
      "equipmentId": "my_faction.weapon-needle-rifle",
      "grades": {
        "surplus": { "accessTier": "common", "supplies": 2 },
        "service": { "accessTier": "common", "supplies": 3, "heavyArmaments": 1 },
        "milspec": { "accessTier": "advanced", "supplies": 5, "heavyArmaments": 2 },
        "masterwork": { "accessTier": "prestige", "supplies": 7, "heavyArmaments": 3 }
      }
    }
  ],
  "armor": [
    {
      "equipmentId": "my_faction.armor-ceramic",
      "accessTier": "advanced",
      "issueCost": { "supplies": 3, "heavyMachinery": 1 }
    }
  ],
  "specialEquipment": [
    {
      "equipmentId": "my_faction.special-breacher",
      "accessTier": "advanced",
      "issueCost": { "supplies": 2, "heavyArmaments": 1 }
    }
  ],
  "nonPlayerEquipment": [
    {
      "kind": "primary",
      "equipmentId": "my_faction.weapon-integrated-drone-gun",
      "reason": "Integrated drone armament cannot be issued to human infantry."
    }
  ]
}
```

Every authored `marine-primary` weapon must therefore have all four cards or
one non-empty `nonPlayerEquipment` reason. Every authored armor and special item
must have its card or the same explicit exclusion using kind `armor` or
`special`. An equipment identity cannot be both collectible and excluded.
Application loading audits the merged catalogs after every provider has
contributed, so a provider may keep its definitions and cards in separate
listed files without depending on file adjacency.

The derived card ids remain stable:

- `equipment-template:<weapon-id>:<grade>`
- `equipment-template:<armor-id>`
- `equipment-template:<special-id>`

Learning one through the Starsector special-item interaction adds it only to
the Marine Armory. It never enters vanilla ship-production knowledge.

Access tier is explicit card data, not inferred from weapon grade, armor role,
faction, issue price, or collectible presentation. This lets a provider author
a rare low-tech curiosity or widely circulated advanced tool without changing
Java. Every faction consumer applies the same tier vocabulary: unrestricted
open markets admit only `common`; licensed and patron offers admit `advanced`
at MRB 5 and `prestige` at MRB 20; high-risk recovery admits those bands after
5 and 15 company victories respectively. Relationship standing remains an
additional requirement for licensed market stock. Starter issue and the
collection-breadth safety net are direct Armory grants and do not consult a
faction channel.

## Faction equipment sources

Faction equipment catalogs reference collectible template ids after those
cards have loaded. Each offer assigns a positive relative weight to one or more
closed campaign channels: `market`, `license`, `patron`, and `recovery`.
Weights compare eligible candidates within a channel; loading a catalog never
creates cargo, bypasses access-tier or relationship checks, or grants a card.

```json
{
  "factions": [
    {
      "factionId": "my_faction",
      "offers": [
        {
          "templateId": "equipment-template:my_faction.weapon-needle-rifle:service",
          "sources": { "market": 8, "patron": 3 }
        },
        {
          "templateId": "equipment-template:my_faction.armor-ceramic",
          "sources": { "license": 6, "recovery": 2 }
        }
      ]
    }
  ]
}
```

The core file alone declares `fallbackFactionId: "independent"`. An unknown
campaign faction resolves to that pool. Providers normally contribute their OC
faction, but may also append a new card/channel claim to an existing faction.
Repeating an existing claim is an error rather than a weight override. A faction
with no human-compatible player templates may replace `offers` with one
non-empty `noPlayerEquipmentReason`; an excluded faction cannot also receive
offers.

After every faction contribution has merged, each collectible card must be
present in starter issue or in at least one faction offer through a channel that
can eventually admit its access tier. In particular, an Advanced or Prestige
card with only a `market` claim is still stranded. This is a global reachability
rule, not a requirement that every faction offer every card. A provider that
contributes a collectible therefore contributes a viable source claim too;
otherwise application loading names the stranded card and stops.

## Runtime consumption

Contributed marine-primary weapons, armor, and special equipment are valid
faction-roster and player issue. Once a weapon or special card is learned it
joins the equipment designer's choices in manifest order.

**Armour is not authored a billet at a time and has no picker.** A contributed
pattern reaches the field by winning a billet in a tactic sheet: the sheet names
a role and a tradition, and `ArmorIssueResolver` fills each billet from the best
owned pattern for that job (`role-and-access.md`). A contribution is therefore
reachable when it declares a role and tradition somebody's sheet asks for, and
its authored `catalog.role`, `catalog.tradition` and `catalog.tier` are the only
things that decide whether it is ever worn.

Because the contract is add-only, a contributed pattern cannot replace a core id
— and a role whose billets all took that role's single best pattern meant a
contribution sharing a cell with a core one could never be issued either. There
was no way in from outside. A role's billets now spread across every pattern at
the top of its band, so a contributed suit in an occupied cell is worn alongside
the core one rather than instead of it. Contributing a *strictly better* suit in
an occupied cell is still not a way to retire the core pattern; that remains what
add-only means.

Saved doctrines, squad billets, and marines retain the catalog equipment id;
issuing changed kit charges the contributed card's authored cargo cost, and
deployment resolves the same definitions without enum constants. A special's closed activation and AI
policy select the built-in typed executor while its referenced weapon and
presentation data supply ballistics, audio, sprites, and carrier layers.

Removing the provider repairs unresolved player primaries to
`weapon.field-rifle`, armor to `armor.field-fatigues`, and unresolved special
equipment to an empty slot, with a warning rather than a corrupt roster.

## Compatibility acceptance

Headless registry tests prove manifest validation, deterministic merge, and
provenance diagnostics. A live compatibility pass additionally proves the
boundary those tests cannot replace: Starsector's enabled-mod discovery order
and provider-scoped resource loading. That pass uses two separately rooted test
mods and must demonstrate both an additive union and a duplicate-id failure that
names the first and second provider ids and paths.

The live pass launches the installed game executable and is therefore explicit,
isolated acceptance work rather than part of the ordinary build. It redirects
all writable game paths into build output. One accepted pass is sufficient until
the supported Starsector version or the discovery/resource-loading boundary
changes.
