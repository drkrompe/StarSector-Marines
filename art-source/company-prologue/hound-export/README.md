# Hound (A) cinematic export

Status: SHIPPED (owner accepted 2026-10-18). Pass 7 of the `ship-model-pipeline` skill for the Hound (A).
It follows `second-wind-cinematic/second-wind-states/` conventions.
Written: 2026-10-18

`build.sh` runs the whole pass from `hound-surface/build/skinned.blend` (see
`hound-surface/rebuild.sh`):

| Step | Script | Writes |
|------|--------|--------|
| export | `export_hound.py` | `clean/hound-a-clean.blend` (ignored), `clean/manifest.json` |
| verify | `verify_hound.py` | `clean/validation.json` (exit 1 on a failed check) |
| review | `review_hound.py` | `clean/review/*.png`, `clean/review/turn-fx/` (ignored) |
| encode | `encode_turn.py` | `clean/review/turn.mp4`, `turn-sheet.png` (ignored) |

The blend is about 11 MB with every image packed. It is regenerated, never
committed. The manifest and validation are committed, so the asset's contract
can be read without building it.

## Using it in a shot

Append `HOUND (A) | clean`. That gives the bare ship. To get the effects as
well, append `HOUND (A) | clean` and `SWFX` in one load.

- **Root.** Every part and anchor descends from `HOUND (A) | clean | root`. Move,
  rotate or scale that root only.
  - The root carries `metres_per_unit` (0.88), `hull_object` and `engines_running`.
  - Everything in the asset is in hull units: +X bow, +Y port, +Z dorsal.
- **Engines.**
  - Empties `Engine 1 port exhaust anchor` and `Engine 2 starboard exhaust anchor` sit at the bell mouths.
  - +X points aft.
  - Each carries `running`, `bell_radius` (5.2 units) and `radius_m` (4.58 m).
- **WS 001.**
  - Medium ballistic hardpoint, fixed, slot angle 0.
  - `Anchor weapon WS 001 muzzle` sits at the brake mouth, with +X on the line of fire.
- **WS 002.**
  - Small turret. `Weapon WS 002 pivot` turns about its local Z and rests at the slot angle, 145 degrees (arc 290).
  - Everything above the traverse gap rides the pivot, along with the slot anchor and `Anchor weapon WS 002 muzzle`.
  - The barbette, ring and armour plates stay with the hull.
  - Rotating the pivot by -145 degrees trains the turret forward.
- **WS 003.** The hidden flare system has its slot anchor (+X aft) and no muzzle.
- **Ramp.** `Hull aft ventral ramp hinge` turns about its local Y.
  - Negative angles lower the aft end.
  - `ground_contact_deg` (6.4) puts the toe on the ground the landing pads stand on.
  - The leaf, treads, stiffeners and toe ride the hinge.
  - The frame, hazard bars, hinge barrel, actuators and lights stay with the hull.
  - The actuators do not extend.
- **Wear.** Light service wear (`hound-surface/wear.json` row `service`).
  - It reads Object coordinates and a face attribute, so it rides any part however it is parented.
  - A trained turret, an opened ramp or a moved root keeps its wear.

## Effects

The optional `SWFX` collection is the VFX kit's own `swfx.apply` run on the
export (`second-wind-vfx/`).

- **Groups.**
  - **Thrusters:** two clean plumes.
  - **Smoke:** exhaust haze behind both engines.
  - **Guns:** flash rigs on both muzzles. They fire nothing until keyed (see the kit README).
- **Left out.**
  - **RCS:** the Hound has no RCS quads.
  - **Landing dust:** it is the Second Wind's.
  - **Lighting:** the kit's lamp table is the Second Wind's.
- **Controls.** Effects are driven from the `SWFX controls` empty, the same as on the Second Wind.

The kit finds the Hound's declared engine and muzzle empties, and the scale on
its root. It does this through a generalisation that is a no-op for the Second
Wind: on its patched export, the kit's records and objects are byte-identical
with and without the change (see the kit README, Anchors).

The haze used to end at a straight edge in the turn. The two engines' haze
boxes were identical and shared face planes, and Cycles does not add
overlapping volumes entered through coplanar faces. The kit now pads each
engine's boxes differently, and sizes the haze box past its falloff (see the
kit README, Smoke and atmosphere). Re-export to pick up the fix.

## Not built

Damaged states are reserved and not built: `--state battle-damaged|wrecked`,
scar ids `Scar HD<nn>`, and a pirate wear row (see `hound-surface/README.md`,
pass 5). `export_hound.py --state` accepts only `clean` until one exists.
