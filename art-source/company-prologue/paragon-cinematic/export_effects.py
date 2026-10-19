"""The Paragon's cinematic asset: the geometry export plus the shared VFX kit's effects.

  blender --background --factory-startup --python export_effects.py -- [--base PATH] [--out DIR]

Input is the appendable export `../paragon-geometry/export_paragon.py` makes
from the textured build (finalize.sh runs it on
../paragon-surface/build/skinned.blend into build/export/): one collection
`PARAGON | clean`, its root `PARAGON | clean | root` (metres_per_unit 0.87),
anchors child collection, images packed. This script does not re-export the
ship; it adds to that file and saves `paragon.blend` beside itself, with
manifest.json:

1. engine anchors: `Engine <i> exhaust anchor` under each `Engine <i> exhaust`
   (+X down the exhaust, radius_m the mouth radius), moved forward from the
   slot point onto the nozzle face (geometry.json mouth_x), the name the kit
   reads first (vfx_hull.py says why);
2. kit gun anchors: for both barrels of the two heavy needlers, an empty
   `Anchor turret <rig> <port|starboard> gun muzzle` under the turret kit's own
   muzzle empty (identity offset), so it trains and elevates with the gun;
3. `swfx.apply(state='clean', hull=<this folder>)` with groups thrusters,
   smoke, guns, beams and shield: plumes, cores, bell glow and glow lights on
   all ten drives, exhaust haze, the needler rigs, a beam rig on every energy
   weapon (swfx_beams, dark until fired), and the Fortress Shield bubble on
   the `Shield emitter` with its ten emitter nodes (swfx_shield, dark until
   raised); no RCS, no landing dust, no lamps, no lighting-rig preset (that is
   the shot's);
4. the asset's default for space: `smk_atmosphere` 0 (vacuum).

Append `PARAGON | clean` alone for the bare ship, or with `SWFX` in the same
load for the effects (they ride the ship's anchors).
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
ap.add_argument('--base', default=str(HERE / 'build' / 'export' / 'paragon.blend'))
ap.add_argument('--out', default=str(HERE))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
BASE, OUT = Path(args.base).resolve(), Path(args.out).resolve()
base_man = json.loads((BASE.parent / 'manifest.json').read_text(encoding='utf-8'))
GROUPS = ('thrusters', 'smoke', 'guns', 'beams', 'shield')
ASSET = 'paragon.blend'

bpy.ops.wm.open_mainfile(filepath=str(BASE))
anchors_coll = bpy.data.collections[base_man['anchors_collection']]
bpy.context.view_layer.update()

# 1. Engine anchors under the geometry's own exhaust empties.
geom = json.loads(vfx_hull.GEOMETRY_JSON.read_text(encoding='utf-8'))
for eng in geom['engines']:
    src = bpy.data.objects[f"Engine {eng['index']} exhaust"]
    e = bpy.data.objects.new(f"Engine {eng['index']} exhaust anchor", None)
    e.empty_display_type, e.empty_display_size = 'SINGLE_ARROW', eng['mouth_radius']
    anchors_coll.objects.link(e)
    e.parent = src
    e.matrix_parent_inverse = Matrix.Identity(4)
    # The geometry's empty sits at the slot point; the nozzle face is mouth_x, forward of it (+X is aft).
    e.matrix_basis = Matrix.Translation((-(eng['mouth_x'] - eng['slot_location'][0]), 0, 0))
    e['radius_m'] = eng['mouth_radius'] * base_man['metres_per_unit']
    e['slot_width'] = eng['width']
    e['exhaust_direction'] = eng['exhaust_direction']
bpy.context.view_layer.update()

# 2. Kit gun anchors under the turret kit's muzzle empties.
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

# 3. The kit.
ctx = swfx.apply(state='clean', groups=GROUPS, hull=HERE, rcs=False, dust=False)
# 4. Space default.
ctx.set('smk_atmosphere', 0.0)
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
man['gun_rigs_note'] = ('Kit gun rig numbers (swfx_guns.fire(ctx, rig, ...)), not slot numbers: the two heavy '
                        'needlers. The Reaper torpedo racks have no rig.')
man['beams'] = [{k: b[k] for k in ('id', 'slot', 'mount', 'weapon', 'muzzle', 'width', 'range', 'chargeup',
                                   'chargedown', 'burst', 'pd')} for b in vfx_hull.BEAMS]
man['beams_note'] = ('swfx_beams.fire_beam(ctx, id, start_frame, hold_s) keys a shot with the weapon\'s own timing; '
                     'beam_len_<id> is the length in hull units (the distance to the target).')
sh = vfx_hull.SHIELD
man['shield_fx'] = {'bubble': ctx.records['shield']['bubble'], 'anchor': sh['anchor'], 'radius': sh['radius'],
                    'arc': sh['arc'], 'nodes': ctx.records['shield']['nodes'],
                    'node_lights': ctx.records['shield']['lights'], 'hits': ctx.records['shield']['hits'],
                    'colours': {k: sh[k] for k in ('ring', 'inner', 'inner_alpha', 'fortress_ring',
                                                   'fortress_inner', 'fortress_alpha')},
                    'timing_s': {k: sh[k] for k in ('raise_s', 'lower_s', 'fortress_up_s', 'fortress_down_s')}}
man['shield_fx_note'] = ('Knobs shield_t (raise), shield_fortress, shield_facing, shield_hit_<k> with '
                         'shield_hit_az_<k>/shield_hit_el_<k>; swfx_shield.raise_shield / fortress / hit key them. '
                         'The game forbids firing while the Fortress Shield is on.')
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, hull profile paragon-cinematic/vfx_hull.py, state clean, '
           'groups thrusters, smoke, guns, beams, shield; rcs=False, dust=False; no lamps, no rig preset)',
    'defaults': {'smk_atmosphere': 0.0},
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
      'guns', len(ctx.anchors.guns), 'rigs', len(rigs), 'beams', len(ctx.records['beams']['beams']),
      'missing beams', ctx.records['beams']['missing'],
      'shield nodes', len(ctx.records['shield']['nodes']), 'missing shield', ctx.records['shield']['missing'])
