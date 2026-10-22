# Asharu wreck kit

Status: ACTIVE — production wrecks for the Asharu battle's debris field (S3, S4).
Written: 2026-10-22

The debris field round the derelict Asharu platform (`asharu-battle-shot.md`, The debris
field) is measured as boxes (`asharu-battle/debris_field.py`): `--check` measures the pirates'
hiding against them. The preview drew blockout shapes in them (`wreck_meshes.py`). This kit is
what the finished field draws instead: a graveyard of old ships, every piece at real size, built
from plates, frames and members, bleached and pitted, nothing lit.

## What is in it

| Family | Variants | What it is |
|---|---|---|
| `hulk` | 6 | Derived from accepted hull builds at their settled scale: the Onslaught (fore section; whole hull, stern torn off), the Legion (fore and aft halves), the Colossus (stripped hull; hold section). Turrets, drives, lamps and effects stripped, a share of fittings, armour and cargo modules taken, torn at a ragged station with frames and bent stringers showing, plating holed with frames behind. |
| `hullfrag` | 5 | The bow of a broken frigate or destroyer, 22 to 120 m: plated over ring frames, tapering to a closed bow, torn off aft, gashes, decks, a gutted deckhouse. |
| `section` | 7 | A length of hull torn off at both ends, 48 to 230 m, a bulkhead half standing inside. |
| `spar` | 14 | Angle-iron trusses 22 to 250 m, kinked, torn and bent at one end; stubby box girders with lightening holes. |
| `plate` | 10 | Armour slabs 22 to 85 m: tiles on a ribbed backing, bent, torn outline, tiles gone, a corner peeled. |
| `chunk` | 3 | Cargo or machinery modules torn out of a hull: heavily chamfered, bulkheads, one end half torn away, a shoulder crushed, drums inside. Never a cube. |

`wk_catalogue.py` lists every variant; `catalogue.json` (written by the build, committed) is
their measured half extents, which is what the contract reads.

## The contract: box to wreck

```python
import wreck_kit                      # art-source/company-prologue/asharu-wrecks/
p = wreck_kit.pick(half, seed)        # half: the box's half extents; seed: the builder's i * 31 + j
# instance kit object p.object (collection 'ASHARU WRECKS' in asharu-wrecks.blend) with
world = T(centre) @ R(box) @ Matrix(p.local).to_4x4()      # in place of ... @ Diagonal(half)
```

`p.local` maps the kit mesh (metres, centred on its bounds) onto the box exactly, so the box is
still the wreck's bounds and `--check` stays valid without change. It is a proper rotation
times a per-axis stretch (`p.fill`, box over native size). A procedural wreck is the variant
nearest the box in size and proportion; a hulk is used only when every axis of the box is
within `HULK_FIT` (0.86 to 1.16) of the hulk's own size, so a recognisable class is never drawn
at a size it does not have. `python wreck_kit.py` prints the swap for the current field.

Meshes carry two face attributes the kit's materials read: `wk_base` (the colour it was painted)
and `wk_tone` (plate-to-plate variety). Instancing the kit object's mesh keeps them.

## Building

```bash
python fetch_sources.py                     # the accepted hull builds, off Drive, into build/
python make_tiles.py                        # ImageGen masters -> seamless detail tiles, into build/
sh ../../blender.sh --background --factory-startup --python build_wrecks.py
python publish_build.py                     # after committing: Drive builds/asharu-wrecks/<commit>/
```

The build writes `build/asharu-wrecks/kit/asharu-wrecks.blend` (tiles packed), `manifest.json`
and `validation.json`, and rewrites `catalogue.json`. `qa_sheet.py` makes a Workbench contact
sheet for checking shapes (never an owner review image).

## Materials

`wk_mat.py`: four materials (plating, structure, interior, bare metal) on one group. It bleaches
each face's painted colour toward a dusty grey-tan, varies it plate to plate and over tens of
metres, and lays two ImageGen detail tiles over it at metre scale (`textures/generated/`, with
their prompts in `textures/prompts/`): sun-bleached pitted plating flaking to primer, and dry
oxidised structural steel. Faces seen from behind are dark. No emission anywhere.

## Look still

`look_wrecks.py` draws the real field at S4's time with the kit swapped into every box, from
S4's camera position under the battle's sun (no pirates, no platform blockout): `wide` (28 mm)
and `close` (110 mm onto the Falcon (P)'s cover). Light-lane job: `look-job.template.json`.
