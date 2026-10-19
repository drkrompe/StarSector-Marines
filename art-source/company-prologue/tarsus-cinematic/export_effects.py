"""Add the shared VFX kit's effects to an exported Tarsus as an optional collection.

  blender --background --factory-startup --python export_effects.py -- --state clean|boarded

Opens <state>/tarsus.blend, runs the kit's own `swfx.apply` with this folder's
hull profile (vfx_hull.py; ../second-wind-vfx/swfx_hull.py) without a lighting
rig preset (that is the shot's), and saves in place. The kit's `SWFX` collection
then sits beside `TARSUS`; append both in one load for the effects, which ride
the ship's own anchors.

- clean: LOW_TECH orange plumes, cores, bell glow and haze on all six drives;
  muzzle effects on the four turrets' eight barrels; the lamps as lights.
- boarded: no drive runs. The kit's conduit sparks fire at the craters'
  `Scar <id> spark anchor`s, and this script adds a smoke wisp at every
  `Scar <id> smoke anchor` with the kit's own wisp builder
  (`swfx_smoke.build_wisp`), sized from the anchor's radius_m: darker, wider and
  longer than a seal leak, breathing intermittently. Knob `smk_damage_smoke`
  (0 = off) scales them all; `smk_sparks` the sparks; `smk_atmosphere` (0 vacuum,
  1 atmosphere) shapes both, as for every kit volume. The export sets it to 0:
  the Tarsus is taken in low orbit. Landing dust is off; she never nears the ground.

Records what was built in <state>/manifest.json under `effects`.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import sys
import bpy

HERE = Path(__file__).resolve().parent
VFX = HERE.parent / 'second-wind-vfx'
sys.path.insert(0, str(VFX))
ap = argparse.ArgumentParser()
ap.add_argument('--state', choices=('clean', 'boarded'), default='clean')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
STATE = args.state
os.environ['TARSUS_STATE'] = STATE          # vfx_hull reads it for the lamp list
import swfx  # noqa: E402
import swfx_core  # noqa: E402
import swfx_smoke  # noqa: E402

BLEND = HERE / STATE / 'tarsus.blend'
GROUPS = ('thrusters', 'smoke', 'guns', 'lighting') if STATE == 'clean' else ('thrusters', 'smoke', 'lighting')
bpy.ops.wm.open_mainfile(filepath=str(BLEND))
# No landing dust: the Tarsus is never near the ground in the battle (a shot can add it).
ctx = swfx.apply(state=STATE, groups=GROUPS, hull=HERE, dust=False)
# Her scenes are in low orbit (asharu-battle-shot.md: 250 km): vacuum is the default.
# A shot of the fall into the atmosphere keys smk_atmosphere up.
ctx.set('smk_atmosphere', 0.0)

damage_smoke = []
if STATE != 'clean':
    ctx.prop('smk_damage_smoke', .8, 0, 1, 'Smoke from the boarded Tarsus\'s shot-out drive section (0 = off)')
    coll = ctx.collection(swfx_smoke.GROUP)
    for k, a in enumerate(sorted((o for o in bpy.data.objects if o.type == 'EMPTY' and o.name.startswith('Scar ')
                                  and o.name.endswith(' smoke anchor')), key=lambda o: o.name)):
        mw = a.matrix_world
        r = float(a['radius_m'])
        normal = (mw.to_3x3().col[0]).normalized()
        ob = swfx_smoke.build_wisp(
            ctx, f"{swfx_smoke.PREFIX} damage smoke {a['scar_id']}", coll, mw.translation.copy(), normal, a,
            'smk_damage_smoke', ('smk_damage_smoke',), seed=101 + 7 * k,
            r0=swfx_core.units(r * .45), length_vac=swfx_core.units(r * 8.0), length_atm=swfx_core.units(r * 16.0),
            color=(.30, .29, .28, 1), absorption=(.55, .55, .56, 1), base=10.0, intermittent=True, rise=1.0)
        damage_smoke.append({'scar': a['scar_id'], 'anchor': a.name, 'object': ob.name, 'radius_m': r})
    swfx_core.refresh_drivers(ctx.scene)

coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND), compress=True)


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


man_path = HERE / STATE / 'manifest.json'
man = json.loads(man_path.read_text())
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': f'second-wind-vfx (swfx.apply, hull profile tarsus-cinematic/vfx_hull.py, state {STATE}, '
           f'groups {", ".join(GROUPS)}; no landing dust; smk_atmosphere 0 (vacuum, low orbit); no rig preset)',
    'objects': len(objs),
    'anchors': ctx.records.get('anchors'),
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'damage_smoke': damage_smoke,
    'append': f"load both '{man['append_collection']}' and '{swfx_core.ROOT}' in one call; the ship alone works without it",
}
man['asset_sha256'] = hashlib.sha256(BLEND.read_bytes()).hexdigest()
man_path.write_text(json.dumps(man, indent=1) + '\n')
sparks = len((ctx.records.get('smoke') or {}).get('sparks', []))
print('EFFECTS', STATE, len(objs), 'objects; engines', len(ctx.anchors.engines), 'guns', len(ctx.anchors.guns),
      'lamps', len(ctx.anchors.lamps), 'sparks', sparks, 'damage smoke', len(damage_smoke))
