"""Structural checks on the exported Legion, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Appends `LEGION` into a factory-empty scene and checks, writing
validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- ten engine anchors pointing aft and running;
- every turret's muzzle anchors point along its slot angle at rest; turning
  its pivot 30 degrees swings every muzzle 30 degrees about the pivot axis and
  leaves its barbette fixed; elevating it 15 degrees (its elevation empty,
  negative local Y) lifts every muzzle about the trunnion and swings its line
  of fire up by the same angle; launchers carry tube anchors, guns barrels;
- the four flight decks' mouths and launch points are there, and each launch
  point's +X is its deck's launch direction;
- all 27 running lights carry their blink;
- every control knob works after the append (each moves the part it drives
  by the travel geometry.json gives), its driver is a simple expression, and
  all knobs back at 0 restore the rest pose exactly;
- moving the root (translate, yaw, pitch, scale) moves every part rigidly;
- appended together with `SWFX`, every effect rides the root.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
man = json.loads((HERE / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection']]
coll = dst.collections[0]
scene.collection.children.link(coll)
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def settle():
    # A knob set from Python does not tag the depsgraph; without the tag the
    # drivers are not re-run on the same frame (a keyframed knob needs nothing).
    for o in bpy.data.objects:
        if o.name.startswith(man['control']['object']):
            o.update_tag()
    scene.frame_set(scene.frame_current)
    bpy.context.view_layer.update()


def mdiff(a, b):
    return max(abs(x - y) for ra, rb in zip(a, b) for x, y in zip(ra, rb))


def rot_about(c, axis, ang, p):
    return c + Matrix.Rotation(ang, 3, axis) @ (p - c)


root = bpy.data.objects.get(man['root'])
check('root present', root is not None)
check('root carries metres_per_unit', root is not None and abs(root.get('metres_per_unit', 0) - man['metres_per_unit']) < 1e-9,
      root.get('metres_per_unit') if root else None)
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
objs = list(coll.all_objects)


def under_root(o):
    while o.parent:
        o = o.parent
    return o == root


check('all under root', all(under_root(o) for o in objs), [o.name for o in objs if not under_root(o)][:5])
linked = [f'{c}:{i.name}' for c in ('objects', 'meshes', 'materials', 'images', 'node_groups', 'collections')
          for i in getattr(bpy.data, c) if i.library is not None]
check('nothing linked (all appended)', not linked, linked[:5])
unpacked = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
check('images packed', not unpacked, unpacked)
meshes = [o for o in objs if o.type == 'MESH']
check('object count', len(meshes) == man['objects'], [len(meshes), man['objects']])
check('polygon count', sum(len(o.data.polygons) for o in meshes) == man['polygons'])
noattr = [o.name for o in meshes if 'hull_rest' not in o.data.attributes or 'hull_normal' not in o.data.attributes]
check('hull attributes', not noattr, noattr[:5])
settle()
worst = 0.0
for o in meshes[::5]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}
eng = [anchors.get(e['anchor']) for e in man['engines']]
check('engine anchors', all(eng) and len(eng) == 10)
check('engine anchors point aft', all((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized().dot(Vector((-1, 0, 0))) > .999
                                      for a in eng if a) and all(a.get('running') for a in eng if a))

# Turrets: train and elevate, each muzzle following.
for t in man['turrets']:
    piv, elv = anchors[t['pivot']], anchors[t['elevation']]
    muz = [anchors[m['anchor']] for m in t['muzzles']]
    fire = Vector((math.cos(math.radians(t['rest_angle_deg'])), math.sin(math.radians(t['rest_angle_deg'])), 0))
    aim = min((m.matrix_world.to_3x3().col[0].normalized().dot(fire) for m in muz), default=0)
    want = 'tube' if t['kind'] == 'launcher' else 'barrel'
    tags_ok = bool(muz) and all(m['tag'] == f'{want} {k}' for k, m in enumerate(t['muzzles'], 1))
    fixed = next(o for o in meshes if o.name == f"Weapon {t['slot']} barbette")
    before_fixed = fixed.matrix_world.copy()
    before = [m.matrix_world.translation.copy() for m in muz]
    axis = piv.matrix_world.to_3x3().col[2].normalized()
    piv.rotation_euler.z += math.radians(30)
    bpy.context.view_layer.update()
    c = piv.matrix_world.translation
    err = max((m.matrix_world.translation - rot_about(c, axis, math.radians(30), b)).length for m, b in zip(muz, before))
    err = max(err, mdiff(fixed.matrix_world, before_fixed))
    piv.rotation_euler.z -= math.radians(30)
    bpy.context.view_layer.update()
    check(f"turret {t['index']} ({t['slot']} {t['weapon']}) trains", err < 1e-3 and aim > .999 and tags_ok,
          {'err': round(err, 6), 'rest_aim_dot': round(aim, 6), 'muzzles': len(muz)})
    # Elevation: negative local Y lifts.
    trunnion = elv.matrix_world.translation.copy()
    eaxis = elv.matrix_world.to_3x3().col[1].normalized()
    fires = [m.matrix_world.to_3x3().col[0].normalized() for m in muz]
    elv.rotation_euler.y -= math.radians(15)
    bpy.context.view_layer.update()
    eerr = max((m.matrix_world.translation - rot_about(trunnion, eaxis, -math.radians(15), b)).length
               for m, b in zip(muz, before))
    lift = min(m.matrix_world.translation.z - b.z for m, b in zip(muz, before))
    pitch = min(math.degrees(math.asin(max(-1, min(1, m.matrix_world.to_3x3().col[0].normalized().z)))
                             - math.asin(max(-1, min(1, f.z)))) for m, f in zip(muz, fires))
    elv.rotation_euler.y += math.radians(15)
    bpy.context.view_layer.update()
    back = max((m.matrix_world.translation - b).length for m, b in zip(muz, before))
    check(f"turret {t['index']} ({t['slot']}) elevates", eerr < 1e-3 and lift > 0 and abs(pitch - 15) < .05 and back < 1e-4,
          {'err': round(eerr, 6), 'min_lift': round(lift, 3), 'pitch_deg': round(pitch, 3)})

# Flight decks and running lights.
bad_lp = []
for d in man['flight_decks']:
    if d['mouth']['anchor'] not in anchors:
        bad_lp.append(d['mouth']['anchor'])
    for lp in d['launch_points']:
        a = anchors.get(lp['anchor'])
        if a is None or a.matrix_world.to_3x3().col[0].normalized().dot(Vector(d['launch_direction'])) < .999:
            bad_lp.append(lp['anchor'])
check('flight-deck mouths and launch points', len(man['flight_decks']) == 4 and not bad_lp, bad_lp[:5])
lamp_obs = [bpy.data.objects.get(l['object']) for l in man['lamps']]
check('running lights carry blink', len(lamp_obs) == 27 and all(o and 'blink_period_s' in o.keys() for o in lamp_obs))

# Control knobs, driven after the append.
ctl = anchors[man['control']['object']]
rest_mw = {o.name: o.matrix_world.copy() for o in objs}
rest_basis = {o.name: (o.location.copy(), o.rotation_euler.copy()) for o in objs}
drv_bad = []
for o in objs:
    for fc in (o.animation_data.drivers if o.animation_data else ()):
        if not fc.driver.is_valid or not fc.driver.is_simple_expression:
            drv_bad.append(f'{o.name}:{fc.data_path}')
check('control drivers are valid simple expressions', not drv_bad and len(man['control']['drives']) == 20, drv_bad[:5])
moved = {}
for dv in man['control']['drives']:
    k, ob = dv['knob'], anchors[dv['object']]
    lo_, hi_ = next(kn['range'] for kn in man['control']['knobs'] if kn['name'] == k)
    for val in sorted({hi_, lo_} - {0.0}):
        ctl[k] = val
        settle()
        loc0, rot0 = rest_basis[ob.name]
        dl = ob.location - loc0
        dr = Vector(ob.rotation_euler) - Vector(rot0)
        ch = dv['channel']
        if ch.startswith('rotation'):
            want = math.radians(float(str(dv['at_1']).split()[0])) * val
            got = dr.y
        elif ch.startswith('location ('):
            want, got = Vector(dv['at_1']).length * val, dl.length * (1 if val > 0 else -1)
        else:
            ax = 'XYZ'.index(ch.split()[1])
            want = dv['at_1'] if val > 0 else dv['at_minus_1']
            got = dl[ax]
        child_moved = max((((c.matrix_world @ Vector(b)) - (rest_mw[c.name] @ Vector(b))).length
                           for c in ob.children for b in c.bound_box), default=0.0)
        moved[f'{k}={val:g}'] = {'want': round(want, 4), 'got': round(got, 4), 'child_moved': round(child_moved, 3)}
        ctl[k] = 0.0
settle()
knob_bad = {k: v for k, v in moved.items() if abs(v['want'] - v['got']) > 1e-4 or v['child_moved'] < .5}
check('control knobs drive their parts', len(moved) == 28 and not knob_bad, knob_bad or len(moved))
back = max(mdiff(o.matrix_world, rest_mw[o.name]) for o in objs)
check('knobs at 0 restore the rest pose', back < 1e-4, round(back, 6))

ref = {o.name: o.matrix_world.copy() for o in meshes}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(mdiff(o.matrix_world, M @ ref[o.name]) for o in meshes)
root.matrix_world = M.inverted() @ root.matrix_world
bpy.context.view_layer.update()
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

# The optional effects collection, appended into a fresh scene with the ship.
if man.get('effects'):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
        dst.collections = [man['append_collection'], man['effects']['collection']]
    for c in dst.collections:
        scene.collection.children.link(c)
    bpy.context.view_layer.update()
    root = bpy.data.objects[man['root']]
    fx = [o for o in dst.collections[1].all_objects if o.name != 'SWFX controls']
    loose = [o.name for o in fx if not under_root(o)]
    plume = next(o for o in fx if o.name.startswith('SWFX thrusters plume e1'))
    p0 = plume.matrix_world.translation.copy()
    root.location += Vector((100, 0, 0))
    bpy.context.view_layer.update()
    shift = (plume.matrix_world.translation - p0 - Vector((100, 0, 0))).length
    check('effects ride the root', not loose and shift < 1e-3, {'effects': len(fx), 'loose': loose[:5]})

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'objects': len(objs), 'meshes': len(meshes), 'images': len(bpy.data.images),
          'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
