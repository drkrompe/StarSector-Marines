# Ship flooring and architectural trim

Geometry-first material kit for connected ship passages, lounge seating areas,
clinical wards and engineering spaces. Muted values distinguish functions;
large authored insets carry room organization without adding obstacles.

## Sources and export

- `ShipFloorTrimTemplate` draws `art-source/templates/ship-floor-trim.png`:
  1024 x 768, four columns by three rows, 224-square canvases on 256px pitch
  starting at (16,16).
- `ship-floor-trim.prompt.txt` is the exact built-in ImageGen prompt. The guide
  was the edit target; `mod/graphics/tilesets/ship-engineering.png` was the
  material reference. The accepted output is `ship-floor-trim.raw.png`,
  1448 x 1086 RGBA.
- First two rows, in reading order: passage, lounge, clinical, engineering,
  lounge inset, clinical aisle, service pad, service aisle. Four quiet interior
  crops per material avoid the generated outer rims; the exporter mirrors a
  2px sprite border. No ad hoc recoloring or painting follows generation.
- Last row: floor edge, bulkhead rib, flush access cover and inset binding.
  Full 334 x 332 canvases at x=17,377,737,1097 and y=730 preserve edge
  registration and transparent floor. An image-top strip faces world +Y;
  placement quarter-turns supply the other edges.
- `ship-floor-trim.tileset-authoring.json` records every cut and runtime id.
  Export with `tools/authoring.cmd tileset_export -`, supplying
  `{"name":"ship-floor-trim"}` on stdin. Only the packed PNG, tileset JSON
  and catalog card belong in `mod/`. This sheet has no relief companions.

## Assembly contract

The eight floor blocks pair with the existing neutral ship openings. Room
fittings author their inset rectangles in canonical coordinates and transform
them through the same pose as their furniture. The ship material stage supplies
the passage and room defaults while retaining explicit authored surfaces.

Skirting follows real adjacent walls, and bindings follow inset boundaries.
Ribs stay inside shared bulkhead cells and hatch jambs, avoiding isolated
stiffeners against the unbroken outer hull; they never
extend onto walkable deck. Flush access covers are sparse and stay off hatches
and fixtures. Trim draws before furniture and is replaced, not duplicated,
when its presentation stage is reapplied. Every overlay has zero cover and
zero ballistic height; topology, capacity and work sites are unchanged.

## Evidence

`createSnapshots -Psnapshot=ship-floor-trim,ship-materials` produces a connected
construction study at 24 and 40 pixels per cell, a threshold close-up, and the
actual generated Valkyrie deck plus room views and quarter-turns. The study
uses production fittings and material selection. Its before view restores only
the old floor materials and removes trim, preserving the same furniture.
It is deliberately arranged to put all three rooms on one passage, not a claim
that these rooms are neighbors on the generated hull. Both suites use production
battle collectors with a Java2D drain; these are not live OpenGL captures.

`ShipDeckTrimTest` checks edge orientation, open hatches, authored materials,
vacuum, idempotence, draw order and neutral physical properties, and samples the
exported strips at both ends and through their transparent interior. Existing
room fitting tests retain their circulation, work-site and pose coverage.
