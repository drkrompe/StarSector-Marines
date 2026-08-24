# W7 — Turret and Emplacement Authoring

Status: IN PROGRESS

Written: 2026-08-24

## Outcome

The standalone authoring workbench can edit a turret as one cross-catalog
object and can compose multi-turret defense-post layouts on a grid. Runtime,
the six-state preview, snapshots, and map generation consume the same saved
definitions.

## Authority split

- The weapon catalog owns ballistics, contact and area payloads, projectile
  presentation, sound, and composed muzzle/trail/impact/aftermath effects.
- The turret catalog owns mount capacity, traverse, body/recoil art, muzzle
  offset, and structure durability, armor, collision, and force value.
- An emplacement-layout catalog owns a bounded grid stamp: barrier/pad cells,
  installed structure ids, and turret offsets relative to its anchor.
- `DefensePostKind` continues to own strategic placement policy such as biome
  budgets, garrison size, priority, and patrol radius. Those values are not
  incidental stamp geometry.

## Scope

1. Add a discoverable authoring-page seam to the existing workbench. A page is
   responsible for its own dirty state, confirmation, and disposal; the host
   mounts it without importing mod-domain classes into the tool module.
2. Add a Turrets page with three coordinated views:
   - weapon and mount/structure scalar fields;
   - authored FX slot/layer inspection and editing;
   - a live deterministic six-state preview of the selected mount.
3. Add an Emplacements view that can select or create a layout, paint its
   bounded cells, place multiple turret structures, move/remove placements,
   and preview the resulting stamp around its anchor.
4. Replace the hard-coded `DefensePostShape` geometry branches with validated
   data-authored layouts. Existing LIGHT, MEDIUM, LARGE variants, ARTILLERY,
   and DRONE_HUB output must retain their established footprints and turret
   compositions.
5. Validate all three catalogs together before saving. Prepare every changed
   file before replacement, preserve unchanged source text where practical,
   and never leave a partially validated catalog set.
6. Keep all authoring and preview dependencies out of the shipped mod jar.

## Interaction laws

- Selecting a turret resolves structure → mount → weapon and edits that chain;
  changing one authority must not create a second copy of another authority's
  fields.
- The preview is a projection of the current in-memory document. It must not
  require saving merely to show an edit.
- Emplacement coordinates are integer cells relative to one explicit anchor.
  A cell cannot simultaneously be a barrier and a turret pad, turret
  structures cannot overlap, and every layout must retain at least one usable
  occupied cell.
- Undo/redo operates on a complete turret-authoring snapshot so cross-file
  edits cannot drift apart.
- Reload, tab disposal, and window close guard unsaved turret or emplacement
  changes independently of marine/mech layer edits.

## Acceptance

- The workbench visibly exposes a **Turrets** tab, not merely a turret entry in
  the Snapshots tab.
- Editing Hephaestus damage/penetration, mount presentation, structure armor,
  and an existing FX layer updates the live preview or inspector immediately
  and round-trips through the authoritative JSON files.
- At least one three-turret layout can be rearranged in the emplacement canvas,
  saved, reloaded, and consumed by `DefensePostStamper`.
- Catalog validation rejects unknown weapon/mount/structure references,
  duplicate ids or offsets, invalid numeric values, and malformed layouts.
- Existing defense-post footprint, connectivity, deterministic map-generation,
  turret runtime, snapshot, and authoring tests pass.
- The root and tool READMEs explain turret and emplacement authoring controls
  and the data/extension boundaries.
