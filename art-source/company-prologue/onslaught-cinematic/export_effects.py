"""The Onslaught's cinematic asset: the geometry export plus the shared VFX kit's effects.

  blender --background --factory-startup --python export_effects.py -- [--base PATH] [--out DIR]

Input is the appendable export `../onslaught-geometry/export_onslaught.py`
makes from the textured build (finalize.sh runs it on
../onslaught-surface/build/skinned.blend into build/export/): one collection
`ONSLAUGHT | clean`, its root `ONSLAUGHT | clean | root` (metres_per_unit
0.86), anchors child collection, images packed. This script does not
re-export the ship; it adds to that file and saves `onslaught.blend` beside
itself, with manifest.json:

1. kit gun anchors: for every barrel of every rig in vfx_hull.RIGS, an empty
   `Anchor turret <rig> <tag> muzzle` parented to the turret kit's own muzzle
   empty with an identity offset (+X along the line of fire), so it trains
   with the pivot and elevates with the elevation empty. `tag` is `gun` for a
   single barrel and `port gun` / `starboard gun` for a twin, by which side
   of the line of fire the barrel sits. These go into the anchors collection;
   nothing of the export is renamed or moved;
2. `swfx.apply(state='clean', hull=<this folder>)` with this folder's
   vfx_hull.py: plumes, cores, bell glow and glow lights on all six drives,
   exhaust haze, gun rigs, the running lights as lamps; no RCS (the Onslaught
   has none), no landing dust, no lighting-rig preset (that is the shot's);
3. the asset's defaults for space: `smk_atmosphere` 0 (vacuum: the haze is
   the kit's faint vacuum haze) and `lit_beacon_period` 48 frames (the red
   running lights' 2 s blink at 24 fps).

Append `ONSLAUGHT | clean` alone for the bare ship, or with `SWFX` in the
same load for the effects (they ride the ship's anchors).
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
VFX = PROLOGUE / 'second-wind-vfx'
sys.path.insert(0, str(VFX))
sys.path.insert(0, str(HERE))
import swfx  # noqa: E402
import swfx_core  # noqa: E402
import vfx_hull  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--base', default=str(HERE / 'build' / 'export' / 'onslaught.blend'))
ap.add_argument('--out', default=str(HERE))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
BASE, OUT = Path(args.base).resolve(), Path(args.out).resolve()
base_man = json.loads((BASE.parent / 'manifest.json').read_text(encoding='utf-8'))
GROUPS = ('thrusters', 'smoke', 'guns', 'lighting')
ASSET = 'onslaught.blend'

bpy.ops.wm.open_mainfile(filepath=str(BASE))
anchors_coll = bpy.data.collections[base_man['anchors_collection']]
bpy.context.view_layer.update()

# 1. Kit gun anchors under the turret kit's muzzle empties.
rigs = []
for r in vfx_hull.RIGS:
    src = [bpy.data.objects[n] for n in r['barrels']]
    fire = (src[0].matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    left = Vector((0, 0, 1)).cross(fire).normalized()
    if r['twin']:
        a, b = src
        port_first = (a.matrix_world.translation - b.matrix_world.translation).dot(left) > 0
        tags = ['port gun', 'starboard gun'] if port_first else ['starboard gun', 'port gun']
    else:
        tags = ['gun']
    barrels = []
    for s, tag in zip(src, tags):
        e = bpy.data.objects.new(f"Anchor turret {r['rig']} {tag} muzzle", None)
        e.empty_display_type, e.empty_display_size = 'SINGLE_ARROW', 1.5 * r['scale']
        anchors_coll.objects.link(e)
        e.parent = s
        e.matrix_parent_inverse = Matrix.Identity(4)
        e.matrix_basis = Matrix.Identity(4)
        e['slot'] = r['slot']
        e['barrel'] = s.name
        e['weapon'] = r['weapon']
        e['scale'] = r['scale']
        barrels.append({'tag': tag, 'anchor': e.name, 'rides': s.name,
                        'at': [round(c, 2) for c in s.matrix_world.translation],
                        'axis': [round(c, 3) for c in fire]})
    rigs.append({'rig': r['rig'], 'slot': r['slot'], 'weapon': r['weapon'], 'size': r['size'],
                 'scale': r['scale'], 'twin': r['twin'], 'barrels': barrels})
bpy.context.view_layer.update()

# 2. The kit.
ctx = swfx.apply(state='clean', groups=GROUPS, hull=HERE, rcs=False, dust=False)
# 3. Space defaults.
ctx.set('smk_atmosphere', 0.0)
ctx.set('lit_beacon_period', 48.0)
swfx_core.refresh_drivers(bpy.context.scene)
coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
unpacked = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not unpacked, unpacked
OUT.mkdir(parents=True, exist_ok=True)
bpy.context.preferences.filepaths.save_version = 0
blend = OUT / ASSET
bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


eng = ctx.anchors.engines
man = dict(base_man)
man['asset'] = ASSET
man['state'] = 'clean'
man['engines_running'] = [e.index for e in eng]
man['engine_sides'] = {'port': list(vfx_hull.PORT_ENGINES), 'starboard': list(vfx_hull.STARBOARD_ENGINES)}
man['gun_rigs'] = rigs
man['gun_rigs_note'] = ('Kit gun rig numbers (swfx_guns.fire(ctx, rig, ...)), not slot numbers. A triple mount is '
                        'two rigs: its outer barrels as a twin and its centre barrel alone. The Typhoon missile '
                        'racks WS 021-024 have no rig.')
man['lamps'] = [{'object': n, 'role': role, 'rgb': list(c)} for n, role, c in vfx_hull.LAMPS]
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, hull profile onslaught-cinematic/vfx_hull.py, state clean, '
           'groups thrusters, smoke, guns, lighting; rcs=False, dust=False; no rig preset)',
    'defaults': {'smk_atmosphere': 0.0, 'lit_beacon_period': 48.0},
    'objects': len(objs),
    'anchors': {k: (len(v) if isinstance(v, list) else v) for k, v in ctx.records['anchors'].items()},
    'engines': [{'index': e.index, 'side': e.side, 'radius': round(e.radius, 3), 'width': e.width} for e in eng],
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'append': f"load both '{base_man['append_collection']}' and '{swfx_core.ROOT}' in one call; "
              "the ship alone works without it",
}
man['sources'] = dict(base_man.get('sources', {}),
                      base_export=str(BASE.relative_to(PROLOGUE)).replace('\\', '/') if BASE.is_relative_to(PROLOGUE)
                      else str(BASE),
                      vfx_hull_sha256=sha(HERE / 'vfx_hull.py'))
man['asset_sha256'] = sha(blend)
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', encoding='utf-8')
print('EFFECTS', len(objs), 'objects; engines', len(eng), [(e.index, e.side, round(e.radius, 2)) for e in eng],
      'guns', len(ctx.anchors.guns), 'rigs', len(rigs), 'lamps', len(ctx.anchors.lamps),
      'lights', len(ctx.records['lighting']['ship']['lights']), 'dark', len(ctx.records['lighting']['ship']['dark']))
