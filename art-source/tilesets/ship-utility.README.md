# Ship utility compartments

Built-in ImageGen reskin of `ShipUtilityTemplate`, with the existing armory
sheet used only as the muted material reference. No API/CLI generation was used.

- Guide: `art-source/templates/ship-utility.png`, 1024-square RGBA.
- Accepted raw: `ship-utility.raw.png`, 1254-square RGBA.
- Exact prompt: `ship-utility.prompt.txt`.
- Measured cuts: `ship-utility.tileset-authoring.json`.
- Runtime: `mod/graphics/tilesets/ship-utility.png` and matching tileset JSON.

Nine slots, in reading order: server cabinet, readout console, electrical board;
2x1 shelf bed, separate stock trays, open retaining frame; pump skid, supply
locker, issue/repair counter. The six standalone modules occupy one cell each.
The three shelf layers share a two-cell footprint, with the stock's transparent
placement canvas retaining the inset from the frame. Both frame apertures were
checked as transparent in the accepted source. Only the shelf bed has collision
and cover; the stock and frame have none. All pipework closes within the pump
skid. There are no height or normal companion sheets.

```powershell
$env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr'
& "$env:JAVA_HOME/bin/java.exe" src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/ShipUtilityTemplate.java art-source/templates/ship-utility.png
'{"name":"ship-utility"}' | .\tools\authoring.cmd tileset_export -
```

The 5x4 server room has shallow cabinet banks around a two-cell service aisle,
with one READOUT console and contained power/cooling equipment. The 6x4 parts
cage has layered shelf runs, a REPAIR counter and READOUT console; shelf and
locker faces publish STOW. Single-cell spaces beside interrupted shelves accept
closed supply lockers. The 3x3 closet has pump, electrical and cleaning equipment
around a one-cell aisle and publishes no new staff jobs. The ordinary fill stage
still adds rounds. All equipment yields to complete hatch approaches, even if
multiple wide entrances reduce the number of fixtures that fit.

The ship dispatcher preserves authored layouts as first choice. Small ship
parts rooms use the compact kit through the machine-shop fitting; the full
14x10 machine shop and all ground fittings remain separate. Recipes, packing
dimensions and pose choices are unchanged. Art does not assert campaign stock.

`ShipUtilityFittingTest` checks all eight poses, every perimeter hatch, wide and
simultaneous entrances, layer assembly, physical footprints, reachable exclusive
work and scenery-only closets. The full machine-shop checks cover its unchanged
stations and the new small-room dispatch.

`createSnapshots -Psnapshot=ship-materials` includes the actual Valkyrie seed-42
small parts cage. That hull need not contain all utility kinds, so an explicit
80x32 utility-program fixture also runs all three through the production ship
generator, with 24/64px room views. Four posed fitting views cover each kind.
All previews use production render collectors drained by Java2D, not a live
OpenGL capture.
