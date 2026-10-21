# Ship hold kit

Plate deck, passage-wall fixtures and stored cargo for company ship decks, after
the deck look-dev (`art-source/ship-deck-lookdev`).

## Geometry and generation

`art-source/templates/ship-hold-guide.py` draws a 1536x1536 transparent guide,
6x6 slots of 256 px, one world cell each. ImageGen reskinned it with the
barracks and bridge look-dev masters and the cargo-billets still as material
references; the exact brief is `ship-hold.prompt.txt` and the unmodified result
`ship-hold.raw.png` (1254x1254 RGBA), whose slots hold the guide's layout on a
209 px pitch. The cuts are in `ship-hold.tileset-authoring.json`.

- Row 1: plate deck x3 (block `ship-hold.plate`), rail plate x3.
- Row 2: worn plate x3 (block `ship-hold.plate-worn`), worn rail plate x3.
- Row 3, 1x1 wall-edge strips, wall at the top of the frame: `cable`,
  `cable-slack`, `lamp`, `lamp-dead`, `wall-patch`, `wall-grime`.
- Rows 4-5, 2x1 storage against a wall: `crate-stack`, `pallet`, `duffels`,
  `ammo-cans`, `netted-cargo`, `bedroll-camp`.
- Row 6, 1x1 loose items: `crate`, `ammo-pair`, `duffel`, `drum`, `cable-coil`, `toolbox`.

Floor tiles are cut 2 px inside their slots. No painting or post-export patches.

## Use

`ShipMaterialStage` lays `ship-hold.plate` in passages and lived-in rooms. The deck
lighting strings `lamp`/`lamp-dead` and `cable`/`cable-slack` along straight
passage walls; the Deficit dressing places the storage, wall patches and grime.
The worn plate and the rail plate are cut and exported but not yet placed.

```powershell
python art-source/templates/ship-hold-guide.py
'{"name":"ship-hold"}' | .\tools\authoring.cmd tileset_export -
.\gradlew.bat createSnapshots '-Psnapshot=second-wind-dressing'
```
