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
| `station` | 1 | The derelict platform itself, built part for part from `battle_blockout.platform_parts()` at each part's exact size: whole trusses for the spine, ties and mast (the mast torn off), plated bulk-carrier segments closed by gappy bulkheads, emptied fuel drums (true circles of plates, some holed through), plated hangar floors and walls. |

**Damage follows a field, not the plating** (coordinator, look 1: the torn edges read as missing
square tiles). Every tear and hole is a noise field over the skin in metres; each plate is cut
along its contour (`wk_geom.field_plate`), so tears are jagged and uneven, the lip curls back
off the hull, frames stand on past the skin by random lengths and stringers run out bent. The
plate grid stays as surface detail only. On the hulks the tear is cut on a fine grid and its
edge then snapped onto the tear surface, and each hole's rim onto its ragged radius, so no
stair-step survives.

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
metres, varies the bleach and overall value per wreck, keeps patches of old paint and scorches
others dark (so the field has value contrast against the desert), and lays two ImageGen detail
tiles over it at metre scale (`textures/generated/`, with
their prompts in `textures/prompts/`): sun-bleached pitted plating flaking to primer, and dry
oxidised structural steel. Faces seen from behind are dark. No emission anywhere.

## The station

`wreck_kit.station()` names the station object and its source (`parts_sha`, part counts). It is
in the platform's own metres, so the builder places it with the platform frame alone
(`T(PLATFORM) @ Rz(PLATFORM_HD)`), where the blockout drew `platform_parts()` as boxes and drums.
`wreck_kit.station_current(platform_parts())` is false once the footprint has changed since the
kit was built: rebuild and republish the kit then.

## Look still

`look_wrecks.py` draws the real field at S4's time with the kit swapped into every box and the
station in place, from S4's camera position under the battle's sun (no pirates): `wide` (28 mm),
`close` (110 mm onto the Falcon (P)'s cover) and `proposal` (as wide, with the hulk-sized boxes
proposed to the Asharu builder applied in that view only). Light-lane job:
`look-job.template.json`.
