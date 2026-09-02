# Story — Mech Lab equipment doll and inventory UX polish

Status: PROPOSED

Written: 2026-09-02

Read `mechs-nouns.md`, `ui-nouns.md`, and `mech-lab-data-authored-dolls.md` before changing this story.

## Problem

The Mech Lab's 3×2 rectangular capacity grid upgrade established a solid
mechanical foundation for equipment footprints and socket validation, but the
visual presentation and interaction surfaces remain unpolished:

1. **Center Equipment Doll & Leader Geometry**:
   - Sockets project direct 1px diagonal lines connecting hull anchors to dock
     centers. These lines slice across the chassis sprite and crowd the gantry.
   - Drop targets appear as plain floating translucent boxes rather than
     integrated technical gantry telemetry brackets.
   - Factory-locked internal modules (`ENGINE CORE`, `AMMO RESERVE`) command the
     same visual footprint and dock prominence as active, swappable weapon mounts.
2. **Left Component Catalog & Inventory**:
   - All compatible hardware is dumped into a single unsorted list without
     category or ownership filtering, causing heavy vertical scrolling on OMNI
     sockets.
   - Cards are 198px tall and display a full four-line material bill even when an
     item is already owned in fleet storage (`FREE >= 1`).
   - Cards display only range and grid dimensions, omitting critical combat
     performance: damage per shot, firing profile (triggers / burst), and ammo
     capacity. There is no delta comparison against the currently equipped item.
3. **Right Equipped Locations Rack**:
   - Sockets display only the component name and grid, without weapon damage,
     range, or ammo contribution.
   - There is no direct "Strip / Unequip" action on an occupied hardpoint; the
     player must navigate empty states to remove gear.
4. **Top Performance Bar & System Feedback**:
   - Top meters (`ARMOR`, `MOBILITY`, `MAX RANGE`, `MISSILES`) are static and do
     not reflect prospective changes when hovering candidate equipment.

## Player Contract

### 1. Equipment Doll & Gantry Stage

- **CAD-Style Leader Geometry**:
  - Leaders use orthogonal / 45° dog-leg routing from the hull anchor to the
    gantry dock rather than direct diagonal chords.
  - Leaders terminate with distinct mechanical ticks at the hull anchor and
    bracket corners at the dock, keeping the central chassis silhouette clear.
- **Visual Bracket Treatment for Docks**:
  - Sockets render chamfered corner brackets, technical mount callouts
    (e.g. `R. SHLDR [1×1]`), and clear state styling:
    - *Empty*: Muted dashed/open bracket with ambient warning accent.
    - *Armed*: Solid bracket with socket type color and filled footprint cells.
    - *Selected*: High-contrast highlight with glowing bracket corners.
    - *Factory-Locked*: De-emphasized steel-blue bracket, smaller dock offset, or
      recessed internal chassis callout.
- **Interactive Hover Ghosting**:
  - Hovering a weapon in the left catalog highlights all compatible docks on the
    doll with green alignment corners, while graying out or crossing incompatible
    and oversized docks.
  - Clicking a dock directly on the doll selects that socket and filters the
    catalog to compatible hardware.

### 2. Left Component Catalog & Inventory

- **Category Filter Tabs**:
  - Filter pills at the top of the catalog: `ALL`, `BALLISTIC`, `MISSILE`,
    `ENERGY`, and `IN STOCK`.
  - Filters immediately narrow the list and preserve the active socket context.
- **Stock vs. Fabrication Separation**:
  - When free stock exists in fleet cargo (`FREE >= 1`), the card displays an
    `IN CARGO` badge and an immediate, prominent `INSTALL SPARE` button; the
    material cost block is collapsed or omitted.
  - When fabrication is required (`FREE == 0`), the card displays a streamlined
    material bill (`Supplies`, `Heavy Machinery`, `Metals`, `Transplutonics`) and
    a `FABRICATE + INSTALL` action with clear affordability coloring.
- **Combat Telemetry & Comparison Deltas**:
  - Each weapon card displays:
    - Damage per projectile / burst.
    - Effective range.
    - Trigger pulls / burst count.
    - Ammo reserve / trigger capacity.
  - Shows delta indicators against the weapon currently installed in the selected
    socket (e.g. `RNG +22`, `DMG +8.0`, `TRIGGERS -2`).
- **Compact Card Density**:
  - Tightened card layout and refined typography to display 3–4 items per scroll
    view, reducing friction when browsing extensive arsenals.

### 3. Right Equipped Locations Rack

- **Installed Weapon Telemetry**:
  - Equipped socket rows show installed weapon stats: damage, range, and
    trigger capacity alongside the existing 3×2 footprint grid.
- **Direct Strip Action**:
  - Occupied, non-locked hardpoints expose a direct `STRIP` button to unequip
    the weapon and return it to fleet cargo in a single click.
- **Status Badges**:
  - Clear micro-badges indicating socket state: `ARMED`, `EMPTY`, `LOCKED`.

### 4. Dynamic Feedback & Performance Bar

- **Ghosted Stat Previews**:
  - Hovering a candidate weapon in the catalog renders ghosted preview segments
    on the top performance meters (e.g., green extension for increased range,
    red inset for reduced missile triggers).
- **Doctrine Context**:
  - Displays tactical fit indicators (e.g. whether a short-range ballistic
    weapon suits a Brawler, or an LRM rack aligns with Long Range Support).

## Authority Boundary

- `MechBay`, `CampaignFabricationResources`, and `MechWorkshop` remain the sole
  authorities for installed loadouts, fleet cargo consumption, and atomic
  refit validation.
- Hover previews, ghosted stat meters, and dock alignment highlights have no
  mutation authority; they are pure presentation over current state and candidate
  hardware.
- The external fitting doll catalog boundary (`mech-lab-data-authored-dolls.md`)
  remains the future authority for dock and anchor coordinates; this story
  refines the rendering, projection, and interaction layers consuming those
  definitions.

## Deterministic Evidence & Acceptance

- Headless UI snapshot suite (`gradlew createSnapshots -Psnapshot=ui`) validates
  all four standard snapshots:
  - `mech-lab-wide.png`: Full gantry overview with refined dock brackets.
  - `mech-lab-hound-empty-socket-wide.png`: Selected empty shoulder with dog-leg
    leaders, weapon stat comparison cards, and equipped rack telemetry.
  - `mech-lab-low-resolution.png`: Responsive compact layout at 1163×625.
  - `mech-lab-ui-scale-150.png`: HiDPI scaling at 1.5× without text clipping.
- Unit and view-model tests cover:
  - Catalog filtering by hardpoint category and cargo stock.
  - Correct weapon comparison delta calculations against installed hardware.
  - Direct `stripWeapon` command validation, cargo return, and reactive refresh.
  - Dock hover hit-testing and dog-leg leader geometry math.
  - Ghosted performance meter value projections.

## Out of Scope

- Drag-and-drop pointer physics (direct click-to-equip and click-to-strip
  provide full functionality).
- Modifying underlying projectile combat balance or weapon registry stats.
- Authoring new chassis or vehicle variants.
- Replacing the top-down garage room generator or technician ambient service jobs.
