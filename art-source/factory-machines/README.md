# Factory production machinery

Three substantial overhead machines replace the one-cell symbolic machinery
where complete working bays fit on procedural factory production floors.

| Family | Footprint | Function |
| --- | --- | --- |
| `factory-machines.fabricator` | 3x3 | Enclosed tooling gantry / precision fabrication |
| `factory-machines.assembly` | 3x2 | Roller-bed machining and assembly |
| `factory-machines.coolant` | 2x2 | Vessel, pump and heat exchanger |

Each has intact, `-damaged` and `-destroyed` entries. Standing machines are
opaque fixtures with heavy cover and 1.1-cell ballistic half-height. Wrecks
have no cover or height and release their entire obstruction through ordinary
prop damage. One blast spends damage once per machine, irrespective of area.
The canonical operator face is south (image bottom). Fitting reserves two
cells in front and a two-cell cross aisle, rotated with the complete bay.

## Retained production inputs

- Geometry producer: `../templates/factory-machines-guide.ps1`; output beside it.
- Built-in ImageGen prompt: `reskin.prompt.txt`. Main-stores atlas was the material reference.
- Accepted native-alpha output: `../tilesets/factory-machines.raw.png` (1254x1254).
- Full placement cuts and export metadata: `../tilesets/factory-machines.tileset-authoring.json`.
- Normal exporter: `tools/authoring.cmd tileset_export -` with `{"name":"factory-machines"}`.

The guide has three machine columns and intact/damaged/destroyed rows. ImageGen
changed output size and horizontal spacing, so cuts use inspected output bounds,
not naive thirds. Standing pairs retain matching placement frames. Low fragments
are registered into the corresponding full footprint; no alpha keying, painted
patches, manual atlas edits, or procedural replacements of the generated art.

`console-rooms` renders the furnished production floor and checks connectivity,
the service spine, opaque footprints and operating aisles across four frontages.
`composable-damage` renders all three states through actual structural damage
at all four rotations. Retained review PNGs use the production Java2D renderer,
1920x1080 at 100%; they are not live OpenGL captures.
