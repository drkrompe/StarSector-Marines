# Compound spread across biome districts

> Conquest compounds currently cluster in the fortress district.
> Spreading them across PORT → CITY → FORTRESS creates a natural
> progression: outer compounds fall first, inner ones last, and the
> player reads the map as a tiered push rather than a sprint-to-the-end.

## Why

The `CompoundClaim` pass seeds compounds from `MILITARY_BASE` BlockKind
rolls. Only the MILITARY_FORT theme (fortress district, back 25% of the
map) carries meaningful MILITARY_BASE weight. Result: all three compound
types (COMMAND_POST, BARRACKS, ARMORY) spawn in the same district, and
the player encounters them as a single cluster instead of a layered
siege.

The compound-as-supply model was designed around tiered progression:
BARRACKS (walk-in) falls first, ARMORY (convoy) falls mid-push, and
COMMAND_POST (shuttle + keep) falls last. That sequence only reads if
the compounds are **physically spread along the traversal axis**, one
per biome band.

## Design

### Forced seeding pass

After `labelLeaves` assigns BlockKinds via weighted theme rolls, a new
**forced seeding pass** guarantees one compound seed per target biome:

1. Walk the leaf list. For each target biome (PORT, CITY, FORTRESS),
   check if any leaf in that biome was already labeled a compound seed
   (`MILITARY_BASE`, `GATED_HOUSING`, or `DENSE_QUARTER`) by the
   natural roll.
2. If the biome already has a seed — skip (natural roll wins).
3. If not — pick the largest eligible leaf in that biome (by area,
   must meet `ClaimSpec.seedMinDim`) and force-label it
   `MILITARY_BASE`.
4. Then `CompoundClaim.claim()` runs as before — BFS-grows compounds
   from all seeds, assigns roles, marks members.

The forced seeder runs **before** `CompoundClaim` and **after**
`labelLeaves`, slotting into the existing pipeline between steps 3
and 4 of `BspCityGenerator`.

### Biome → compound role mapping

The forced seed's biome position determines which supply-chain role
the compound's COMMAND leaf gets. Role assignment inside `CompoundClaim`
already gives COMMAND to the seed and distributes BARRACKS/ARMORY/
VEHICLE_BAY to neighbors by area. What changes: the **kind of
tactical node** emitted by `MilitaryBaseFiller.emitTacticalNodes` is
biome-aware so the supply gate maps to the progression.

| Biome    | Compound role emphasis | Supply gate disabled on capture |
|----------|----------------------|--------------------------------|
| PORT     | ARMORY-anchored      | Convoy (`ConvoyMeans`)          |
| CITY     | BARRACKS-anchored    | Walk-in (`WalkInMeans`)         |
| FORTRESS | COMMAND_POST-anchored| Shuttle (`ShuttleMeans`) + keep |

"Emphasis" means the seed leaf emits that node kind; non-seed members
still get the standard area-sorted role assignment. The fortress
compound keeps the ternary-partitioned keep (COMMAND_CONFIG) as today.

### Max-per-map adjustment

`CompoundClaim.DEFAULT_SPECS` currently caps `MILITARY_BASE` at 1 per
map. The forced seeder needs to produce up to 3 (one per biome). Two
options:

- **Raise `maxPerMap` to 3** on the MILITARY_BASE spec. Simple, but
  the natural roll could also produce 3 — unlikely given the weight
  distribution, but possible. Risk: 4+ seeds if natural + forced
  overlap across biomes.
- **Forced seeder increments a separate counter** independent of
  `ClaimSpec.maxPerMap`. The existing cap governs natural rolls only;
  forced seeds are guaranteed and don't compete. Cleaner separation.

Recommend the second option — the forced seeder owns its own count
and `CompoundClaim` sees the already-labeled seeds as if they were
natural.

### BEACH biome excluded

No compound in the BEACH band. It's the marine landing zone — no
defender supply structures at the beachhead. The traversal reads:
land → take the port armory → push through the city barracks →
storm the fortress keep.

## Implementation slices

### Slice 1: BiomeCompoundSeeder

New class in `battle.mapgen.bsp`. Takes the leaf list + `BiomeMap`,
ensures one `MILITARY_BASE` seed per target biome (PORT, CITY,
FORTRESS). Picks the largest eligible leaf whose center falls in the
target biome. Runs between `labelLeaves` and `CompoundClaim.claim()`
in `BspCityGenerator`.

**Files:**
- New: `BiomeCompoundSeeder.java` in `battle.mapgen.bsp`
- Modify: `BspCityGenerator.java` — call seeder after labelLeaves
- Test: synthetic leaf list with biome map, assert one seed per biome

### Slice 2: Biome-aware role emission

`MilitaryBaseFiller.emitTacticalNodes` gains a biome parameter (or
reads it from the compound/leaf) to emit the biome-appropriate
node kind on the COMMAND leaf:

- PORT seed → ARMORY node (not COMMAND_POST)
- CITY seed → BARRACKS node
- FORTRESS seed → COMMAND_POST (unchanged)

Non-seed members keep the standard area-sorted role assignment.

**Files:**
- Modify: `MilitaryBaseFiller.java` — biome-driven COMMAND leaf kind
- Modify: `Compound.java` or `CompoundClaim.java` — carry biome tag
- Test: assert node kinds match biome

## Cross-refs

- [`central-keep.md`](../central-keep.md) — compound-as-supply model;
  the progression this story enforces.
- [`tug-of-war-v2.md`](../tug-of-war-v2.md) — garrison drops at
  captured compounds; spread affects where drops land across the map.
- `BiomeMap` — biome band layout along the traversal axis.
- `CompoundClaim` — BFS compound seeding + role assignment.
- `MapDistrictTheme` — weight tables that drive natural BlockKind rolls.
