# Tactical military compounds

**Shipped `fa0533ee`** — military bases now read as one coordinated installation
rather than four generic urban buildings inside a wall.

## Player-visible result

- COMMAND buildings use purpose-aware command consoles and a central tactical
  planning table instead of the generic shop/shelf recipe.
- BARRACKS use paired bunk rows with a broad center lane.
- ARMORY buildings alternate weapon racks and heavy crate stacks along the long
  walls, creating readable cover lanes rather than perimeter clutter.
- VEHICLE_BAY buildings keep an open 3x3-or-larger service floor while a
  mandatory generator bank and secondary utility props hug the walls.
- Every role building faces its primary door toward shared compound circulation
  and gains an opposed secondary exit.
- One armored radar dish is placed outdoors near COMMAND. It prefers bridged
  parade-ground space, stays at least two cells from doors, never occupies the
  convoy road reservation, and only uses cells with at least three open cardinal
  neighbors.

## Tactical and sizing contract

Military furniture is a see-through `FIXTURE`: it blocks a body-sized movement
cell and supplies cover without becoming a structural wall or blocking lines of
fire. Bunk/rack rows preserve at least a two-cell lane. With marine radius 0.3
cells, that lane admits two marine diameters abreast (1.2 cells) with clearance.
Vehicle bays additionally retain a 3x3 open working area.

`RoomPurpose` gained append-only `BARRACKS`, `ARMORY`, and `VEHICLE_BAY` labels.
The command post retains the more detailed `KEEP_THRONE` / `KEEP_INNER` /
`KEEP_ENTRY` chamber labels used by its garrison logic.

## New art

The generated doodad atlas gained a third row containing:

- `doodad.military-radar-dish` — heavy cover
- `doodad.military-command-console` — medium cover
- `doodad.military-tactical-table` — medium cover
- `doodad.military-bunk` — light cover

Untouched RGBA ImageGen outputs live under
`mod/graphics/doodads/imagegen-raw/`; production sources and exact prompt
subjects are recorded in `sources/` and `IMAGEGEN-PROMPTS.md`.

## Verification

- `python mod/graphics/doodads/test_stitch_atlas.py`
- `gradlew.bat :test --tests "*MilitaryCompoundLayoutTest" --tests "*CompoundFillerOverlayTest"`
- `gradlew.bat :test`

The full suite's conquest validation batch remained a single connected
component for all four 240x160 seeds, with all 52–56 garrison nodes per seed
deployable and reachable. Sprite previews at
`build/map-previews/sprite-seed-{0001,0042,0777}.png` were inspected for final
atlas scale and contrast.
