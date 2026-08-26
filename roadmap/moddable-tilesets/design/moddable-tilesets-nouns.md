# Moddable Tilesets

Status: ACTIVE — additive external catalogs shipped; variant-pool cleanup remains

Written: 2026-08-23

Updated: 2026-08-25 — adopted the shared enabled-mod manifest and provenance contract for additive tilesets and mappings.

Read `stories.md` for open work.

## Purpose

Moddable tilesets separate visual content from the code that uses it. Stable
ids describe the available tile, block, and prop content; a separate mapping
describes how existing generation and rendering policies select that content.
The result removes PNG-order and coordinate-table authority from Java while
keeping terrain generation deterministic and its tactical rules explicit.

The feature makes both built-in and contributed catalogs data-driven. Enabled
mods can add tiles, blocks, doodads, and named mapping entries through the
shared marine-catalog manifest. Contributions are additive rather than an
override layer: changing core generation policy remains a deliberate core edit.

## Vocabulary and ownership

- A **tileset definition** is the authoritative description of visual assets on
  one sheet. It may describe sliced tiles, fixed-grid blocks, decorative props,
  and viewer-only cell labels.
- A **tile** is a sliced-sheet visual with semantic metadata such as layer,
  cover, passability, and permitted overlay bases.
- A **grid block** is a named fixed-grid surface. Its named layout remains a
  code algorithm; the definition supplies the asset coordinates or a stable
  variant pool.
- A **doodad** is a decorative prop with an authored source cell and intrinsic
  tactical properties. A doodad definition says what the prop is; a pool says
  where generation may choose it.
- A **mapping definition** is the selection policy between generated concepts
  and registry ids: render-surface dispatch, named doodad pools, and tunables
  for an existing filler. It does not currently select or define a filler
  algorithm.
- A **stable id** is the durable name by which any tile, block, or doodad is
  selected. These three content shapes share one namespace. A dense handle is
  only an in-memory implementation detail for a generated cell, never a
  cross-load identity or save format.

`TileRegistry` owns the merged asset catalog and `GenMappingRegistry` owns
the merged use mapping. The application lifecycle loads the asset catalog
before the mapping, because mapping references must resolve to assets.
Render systems consume surface mappings; mapgen fillers consume pools and
tunables. Fillers, layouts, autotile resolvers, and topology own algorithms
and tactical effects; JSON supplies their content parameters, not executable
generation logic.

## Data and authority flow

At application load, every enabled mod's fixed marine-catalog manifest is
discovered in game load order. Its explicit `tilesets` resources are loaded
from that exact provider into one registry and checked for duplicate ids and
resolvable overlay selectors. Explicit `tileMappings` resources then populate
the mapping registry and validate their tile, block, doodad, and filler-pool
references against the complete asset catalog.
During battle generation, code-owned fillers select mapped pools and values;
during rendering, mapped ids select most ground tiles and blocks. Primary
sliced grass/dirt variants are the current exception: code still owns their
two-item pool membership, which `nature-variant-pool-authority-cleanup.md`
contracts to move into declared content. The same registries are installed from
the bundled JSON in tests so the normal test path exercises production content
rather than a test-only catalog.

The two JSON layers deliberately have different authority:

- Tileset definitions answer **what may be drawn and what it means**.
- Mapping definitions answer **where established code may use it**.

This prevents an art rearrangement from becoming a code edit, without falsely
claiming that data can replace geometry, connectivity recovery, placement, or
other procedural decisions.

## Catalog and mapping behavior

### Id-addressed visual catalog

Both sliced sheets and fixed-grid sheets resolve by explicit ids rather than
enum order or hardcoded origins. Sliced definitions pin their frame explicitly;
fixed-grid definitions name the layout convention or the member cells of a
deterministic variant pool. Per-cell viewer labels are descriptive only and
never participate in generation or combat.

The registry preserves prior visual behavior: ids and frames are pinned to
their established output, and cell overlay handles are resolved through the
current registry. Changing a frame assignment now requires a reviewable
manifest edit rather than changing an enum declaration order in code.

### Mapping-driven use

Generation now reads data-owned doodad membership and cover, fixed-grid and
fallback ground-render dispatch, and nature-zone pools/chances. Primary sliced
grass/dirt membership remains a declared cleanup exception. This changes where
the source of truth lives, not who owns the algorithm: a nature zone still
carves wetlands, protects connectivity, and spends its seeded random stream in
code; JSON only sets the choices and rates it consumes. Doodad cover is likewise
a property of the data definition, while directional markers and embankment
resolvers remain code-owned geometry.

Nature overlays now exercise that authority boundary end to end. Small rocks
are visual only; medium rocks publish light cover while remaining walkable;
large rocks publish heavy cover and block navigation while remaining
see-through. The filler applies passability, visibility, and connectivity law;
the battle setup publishes authored cover quality into tactical scoring and
ballistic interception. Large rocks are non-structural fixtures rather than
walls: they receive neither wall art nor destructible wall HP.

Passability also selects the cover shape for doodads. A walkable doodad is a
physical feature that may intercept a ray crossing its occupied cell. A
navigation-blocking doodad instead publishes its authored level and height as
directional edge cover on adjacent standable cells; it never publishes both
shapes. Map generation must tag such props as fixtures so finalization does not
promote them to walls. Water is the complementary zero-cover blocker: it is
non-walkable and see-through but explicitly supplies no edge profile.

`GenMappingRegistry` is also the storage location for surface-relief material
overrides. The tile feature owns the mapping container and its load order;
surface relief owns what those height values mean and how rendering uses them.

## Validation and fallback laws

1. A built-in id collision, a missing sliced frame, or an unresolved tile
   overlay selector is an authored-content error and must be surfaced during
   catalog loading. Ids must never silently become last-one-wins.
2. Built-in loading is defensive at application startup: failures are logged
   and leave the prior installation (or no installation) in place. Consumers
   may use their explicit degraded path where one exists, but a mapping may not
   silently substitute an unrelated asset for an unresolved id.
3. A mapping depends on the tile catalog. Pool members and render targets are
   registry ids, never copied coordinates or enum ordinals.
4. Seeded content must retain deterministic RNG consumption unless an authored
   mapping intentionally changes behavior. The data migration itself does not
   authorize a visual or tactical reroll.
5. Tile metadata is not automatic navigation authority. A new visual property
   can affect walkability, cover, sight, or collision only through the code
   path that owns that tactical law.
6. Cross-provider ids and mapping keys are add-only. A collision reports both
   declaring mod ids and resource paths; it never becomes load-order override.
   Mapping references preflight against the complete tile catalog before the
   mapping is installed.
7. Code may choose a layout from topology, but declared content owns the
   membership of a visual variant pool. A compatibility fallback may preserve
   output only while the registry is unavailable; it is not another catalog.

## External-provider contract

Tilesets use the same provider manifest, exact-mod resource loading, stable
iteration order, and provenance diagnostics as equipment catalogs. An absent
manifest is not an error; an unreadable manifest or declared resource is.
Registry installation is atomic after all contributions and cross-references
validate. Provider ids, definition ids, mapping keys, sheet paths, and other
asset paths should be namespaced by the contributing mod. Art paths still live
in Starsector's shared asset namespace even though their catalog JSON is loaded
from an exact provider.

Mapping contributions may add named doodad pools and any still-unclaimed
closed mapping key. They may not replace a core ground-render target, filler
configuration, macro-height value, or named pool. Richer selectable terrain
themes require a future map-generation selection noun rather than implicit
load-order overrides.

## Adjacent features

Map generation owns the relationship between generated topology and playable
terrain. This feature gives it named content and parameters; it does not own
map recipes, fillers, or the tactical validity of a map. `GenRecipe` chooses
which generation stages execute, while a mapping chooses the content an
already-selected stage consumes.

Moddable weapons supplies the shared provider discovery mechanism while
remaining a different domain. The catalogs share discovery and provenance,
but their ids, schemas, validation, and gameplay authority remain independent.
