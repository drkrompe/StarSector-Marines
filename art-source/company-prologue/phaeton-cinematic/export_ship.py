"""Export the textured Phaeton, intact or wrecked, as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- --state clean|destroyed [--out DIR] [--no-save]

Inputs: the surfacing builds (../phaeton-surface/build/skinned.blend from
apply_skin.py on the clean geometry; skinned-destroyed.blend from
apply_damage.py on it) and the geometry's geometry.json. Output, beside this
script:
  clean      phaeton.blend            collection `PHAETON`, root `PHAETON | root`
             manifest.json
  destroyed  phaeton-destroyed.blend  collection `PHAETON WRECK`, root `PHAETON WRECK | root`
             manifest-destroyed.json
Images packed in both.

What the export does to the build (the Colossus's export is the pattern):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name;
3. writes `hull_rest` / `hull_normal` (hull space, at rest);
4. parents every top-level object to the root;
5. clean: `Turret <i> pivot` (WS 001-004) on each traverse axis, every part the
   geometry marks `trained` riding it; `Anchor turret <i> port|starboard gun
   muzzle` on the twin muzzle brakes; `Anchor launcher <i> flare muzzle` on
   the two fixed flare packs (WS 005-006). The wreck keeps its guns where the
   blast left them: no pivots, no muzzles;
6. `Engine 1-6 exhaust anchor` at the six bell mouths (running when clean,
   dead in the wreck);
7. destroyed: the break-up. `PHAETON WRECK | controls` carries `break_frame`
   (the detonation frame) and `break_rate`; each `Scar S0x <section> section`
   empty is driven from the intact pose (before break_frame) to ph_damage's
   wreck pose at break_s = 4 s and keeps drifting at that rate, and the S04
   shell fragments and S06 debris fly out from the strike point on the same
   clock. `Scar S04 explosion anchor main` (on the root, at the strike) and two
   secondary tank blasts (on their sections) are the kit's explosion anchors;
   the effects export links the kit's fir_exp_frame to break_frame.

Staging intact -> destroyed in a shot: append both collections at the same
root transform, set break_frame, show PHAETON until that frame and PHAETON
WRECK from it. At break_frame the wreck is in the intact pose, so the swap is
seamless under the flash.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys
import zlib
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'phaeton-surface'
GEOMETRY = PROLOGUE / 'phaeton-geometry'
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(GEOMETRY))
import vfx_hull  # noqa: E402
import ph_damage  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--state', default='clean', choices=('clean', 'destroyed'))
ap.add_argument('--skinned')
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
STATE = args.state
WRECK = STATE == 'destroyed'
SKINNED = Path(args.skinned or SURFACE / 'build' / ('skinned-destroyed.blend' if WRECK else 'skinned.blend')).resolve()
GEOM_JSON, OUT = Path(args.geometry).resolve(), Path(args.out).resolve()
COLL = 'PHAETON WRECK' if WRECK else 'PHAETON'
ROOT, CONTROLS = f'{COLL} | root', f'{COLL} | controls'
ASSET = 'phaeton-destroyed.blend' if WRECK else 'phaeton.blend'
MANIFEST = 'manifest-destroyed.json' if WRECK else 'manifest.json'
BREAK_S = 4.0                       # seconds from detonation to ph_damage's wreck pose
STRIKE = (44.0, 4.0, 4.0)           # S04: the missile strike on tank 2 (ph_damage.CRATER)
# (name, at, parent section or None for the root, size_m, delay_s): the main blast and the
# two ruptured tanks going up a beat later.
BLASTS = [('Scar S04 explosion anchor main', STRIKE, None, 20.0, 0.0),
          ('Scar S03 explosion anchor tank 1', (8.0, 6.0, 14.0), 'stern', 10.0, .35),
          ('Scar S05 explosion anchor tank 3', (80.0, -3.0, 16.0), 'bow', 11.0, .6)]

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
FPS = scene.render.fps / scene.render.fps_base
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.endswith(' exhaust anchor'):
        bpy.data.objects.remove(ob)        # re-declared below with the export's own names and keys
scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
for name in sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render):
    bpy.data.objects.remove(bpy.data.objects[name])

# 2. Bake modifiers under the same object names.
dg = bpy.context.evaluated_depsgraph_get()
baked = {}
for ob in ship:
    if ob.modifiers:
        baked[ob.name] = bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True,
                                                         depsgraph=dg)
for ob in ship:
    me = baked.get(ob.name)
    if me is None:
        continue
    overrides = [(s.link, s.material) for s in ob.material_slots]
    ob.modifiers.clear()
    old = ob.data
    ob.data = me
    me.name = ob.name
    for k, (link, mat) in enumerate(overrides[:len(ob.material_slots)]):
        if link == 'OBJECT':
            ob.material_slots[k].link = 'OBJECT'
            ob.material_slots[k].material = mat
    if old.users == 0:
        bpy.data.meshes.remove(old)
bpy.context.view_layer.update()

# 3. Hull-space attributes at rest (the wreck's are in its posed state).
for ob in ship:
    me, mw = ob.data, ob.matrix_world
    nm = mw.to_3x3().inverted_safe().transposed()
    rest = me.attributes.get('hull_rest') or me.attributes.new('hull_rest', 'FLOAT_VECTOR', 'POINT')
    rest.data.foreach_set('vector', [c for v in me.vertices for c in (mw @ v.co)])
    hn = me.attributes.get('hull_normal') or me.attributes.new('hull_normal', 'FLOAT_VECTOR', 'FACE')
    hn.data.foreach_set('vector', [c for p in me.polygons for c in (nm @ p.normal).normalized()])

# 4. Collection and root.
coll = bpy.data.collections.new(COLL)
scene.collection.children.link(coll)
anchors_coll = bpy.data.collections.new(f'{COLL} | anchors')
coll.children.link(anchors_coll)


def empty(name, where, size=1.0, kind='PLAIN_AXES', parent=None, matrix=None):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type, ob.empty_display_size = kind, size
    where.objects.link(ob)
    ob.matrix_world = matrix if matrix is not None else Matrix.Identity(4)
    if parent is not None:
        reparent(ob, parent)
    return ob


def reparent(ob, parent):
    w = ob.matrix_world.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = parent.matrix_world.inverted()
    ob.matrix_world = w


def frame(axis, up=Vector((0, 0, 1))):
    """+X along axis, +Z as near hull-dorsal as that allows (second-wind-vfx convention)."""
    x = Vector(axis).normalized()
    z = up - x * x.dot(up)
    if z.length < 1e-6:
        z = Vector((1, 0, 0)) - x * x.dot(Vector((1, 0, 0)))
    z = z.normalized()
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # slot pivots, muzzles, scar anchors, sections
root = empty(ROOT, coll, 40, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5. Turret pivots and muzzles; the flare launchers' muzzles (clean only).
turrets, launchers, trained_total = [], [], 0
for i, slot in enumerate((s for s in geom['weapon_slots'] if s['mount'] == 'TURRET'), 1):
    if WRECK:
        break
    pre = f"Turret {i} {slot['id']} "
    parts = [o for o in ship if o.name.startswith(pre)]
    geo_pivot = bpy.data.objects[slot['pivot']]
    pc = geo_pivot.matrix_world.translation.copy()
    pivot = empty(f'Turret {i} pivot', anchors_coll, 3.0, 'SINGLE_ARROW', root, Matrix.Translation(pc))
    pivot['rest_angle_deg'] = slot['angle']
    pivot['slot'] = slot['id']
    pivot['train_axis'] = 'hull +Z'
    pivot['arc_deg'] = slot['arc']
    trained = [o for o in parts if o.get('trained')]
    assert trained, f'no trained parts on turret {i} (ph_mounts marks them)'
    for o in trained:
        reparent(o, pivot)
    for e in (slot['pivot'], slot['muzzle']):
        reparent(bpy.data.objects[e], pivot)
    bpy.context.view_layer.update()
    fire = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
    left = Vector((-fire.y, fire.x, 0))
    muzzles = []
    for k in (1, 2):
        brake = bpy.data.objects[f'{pre}muzzle brake {k}']
        pts = [brake.matrix_world @ v.co for v in brake.data.vertices]
        far = max(p.dot(fire) for p in pts)
        ring = [p for p in pts if far - p.dot(fire) < .05]
        tip = sum(ring, Vector()) / len(ring) + fire * .02
        tag = 'port gun' if (tip - pc).dot(left) > 0 else 'starboard gun'
        a = empty(f'Anchor turret {i} {tag} muzzle', anchors_coll, .8, 'SINGLE_ARROW', pivot,
                  Matrix.Translation(tip) @ frame(fire).to_4x4())
        muzzles.append({'tag': tag, 'brake': brake.name, 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                        'axis': [round(c, 3) for c in fire]})
    assert {m['tag'] for m in muzzles} == {'port gun', 'starboard gun'}, muzzles
    trained_total += len(trained)
    turrets.append({'index': i, 'slot': slot['id'], 'size': slot['size'], 'pivot': pivot.name,
                    'pivot_at': [round(c, 3) for c in pc], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained), 'muzzles': muzzles})

for i, slot in enumerate((s for s in geom['weapon_slots'] if s['type'] == 'SYSTEM'), 1):
    if WRECK:
        break
    lm = bpy.data.objects[slot['muzzle']]
    lfire = lm.matrix_world.to_3x3() @ Vector((1, 0, 0))
    la = empty(f'Anchor launcher {i} flare muzzle', anchors_coll, .8, 'SINGLE_ARROW', root,
               Matrix.Translation(lm.matrix_world.translation) @ frame(lfire).to_4x4())
    launchers.append({'index': i, 'slot': slot['id'], 'mount': slot['mount'], 'fixed': True,
                      'angle_deg': slot['angle'], 'anchor': la.name,
                      'at': [round(c, 3) for c in lm.matrix_world.translation],
                      'axis': [round(c, 3) for c in lfire.normalized()]})

# 6. Drive anchors at the bell mouths. In the wreck the drive block rides the
# stern section, so its anchors do too.
stern = bpy.data.objects.get('Scar S02 stern section')
engines = []
for e in geom['engines']:
    i = e['index']
    r = vfx_hull.BELL_R[e['width']]
    mouth, axis = Vector(e['anchor_location']), Vector(e['exhaust_direction'])
    m = Matrix.Translation(mouth) @ frame(axis).to_4x4()
    if WRECK:
        m = stern.matrix_world @ m       # the build's wreck pose
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, r, 'SINGLE_ARROW', stern if WRECK else root, m)
    a['running'] = not WRECK
    a['radius_m'] = r * MPU
    engines.append({'index': i, 'role': 'main drive', 'anchor': a.name, 'mouth': list(mouth),
                    'axis': list(axis), 'radius': round(r, 3), 'width': e['width'], 'style': e['style'],
                    'running': not WRECK})
root['engines_running'] = [e['index'] for e in engines if e['running']]
root['metres_per_unit'] = MPU
root['state'] = STATE
root['hull_object'] = 'Hull main body'

# 7. The break-up (destroyed only).
sections, flyers, blasts = {}, [], []
if WRECK:
    ctrl = empty(CONTROLS, anchors_coll, 12, 'CUBE', root)
    ctrl['break_frame'] = 1.0
    ctrl['break_rate'] = 1.0
    ui = ctrl.id_properties_ui('break_frame')
    ui.update(description='Frame of the detonation: before it the wreck sits in the intact pose', min=-100000,
              max=100000)
    ctrl.id_properties_ui('break_rate').update(description='Drift speed multiplier (1 = the wreck pose at 4 s)',
                                               min=0, max=5)

    def drive(target, path, index, expr):
        d = target.driver_add(path, index).driver
        d.type = 'SCRIPTED'
        for n, p in (('bf', '["break_frame"]'), ('br', '["break_rate"]')):
            v = d.variables.new()
            v.name, v.type = n, 'SINGLE_PROP'
            v.targets[0].id, v.targets[0].data_path = ctrl, p
        d.expression = expr

    s_expr = f'max(frame-bf,0)/{FPS:.3f}/{BREAK_S}*br'
    for name in ('stern', 'mid', 'bow'):
        e = bpy.data.objects[ph_damage.REST.get(name) or f"Scar {ph_damage.SECTION_SCAR[name]} {name} section"]
        pivot, move, rot = ph_damage.POSE[name]
        e.rotation_mode = 'XYZ'
        for k in range(3):
            drive(e, 'location', k, f'{pivot[k]}+{move[k]}*{s_expr}')
            drive(e, 'rotation_euler', k, f'{math.radians(rot[k]):.6f}*{s_expr}')
        sections[name] = {'empty': e.name, 'pivot': list(pivot), 'translate_at_break_s': list(move),
                          'rotate_deg_at_break_s': list(rot)}
    scene.frame_set(scene.frame_start)
    bpy.context.view_layer.update()      # break_frame 1: every section now in the intact pose
    # Fragments and debris: from near the strike point out to where the build flung them.
    # Their local space is the intact hull frame offset by the section pivot.
    mid_pivot = Vector(ph_damage.POSE['mid'][0])
    for ob in ship:
        if not ob.name.startswith(('Scar S04 shell fragment', 'Scar S06 debris')) or ob.parent is None:
            continue
        c_local = (Matrix.Translation(mid_pivot) @ ob.matrix_parent_inverse).inverted() @ Vector(STRIKE)
        p = ob.location.copy()
        p0 = c_local + (p - c_local) * .08
        for k in range(3):
            drive(ob, 'location', k, f'{p0[k]:.4f}+({p[k] - p0[k]:.4f})*{s_expr}')
        flyers.append(ob.name)
    for name, at, section, size, delay in BLASTS:
        par = root if section is None else bpy.data.objects[sections[section]['empty']]
        a = empty(name, anchors_coll, 6, 'SPHERE', par, Matrix.Translation(at))
        a['size_m'] = size
        a['delay_s'] = delay
        blasts.append({'anchor': a.name, 'at': list(at), 'section': section, 'size_m': size, 'delay_s': delay})
    # Fire and vent sizes vary by anchor (stable per name): small burning breaches
    # to big fires at the break faces; vents of different reach.
    for ob in movers:
        h = zlib.crc32(ob.name.encode()) % 1000 / 1000
        if 'fire anchor' in ob.name:
            ob['size_m'] = round((1.8 if ' face ' in ob.name else 1.0) + 1.6 * h, 2)
        elif 'vent anchor' in ob.name:
            ob['length_m'] = round(18.0 + 17.0 * h, 1)
    root['break_frame_control'] = ctrl.name

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and not bsdf.inputs['Emission Color'].is_linked and bsdf.inputs['Emission Strength'].default_value > 0 \
                and any(bsdf.inputs['Emission Color'].default_value[:3]) and 'glazing' not in m.name.lower() \
                and not m.name.startswith('Scar'):
            lamps.append({'object': ob.name, 'material': m.name, 'lit': not WRECK,
                          'at': [round(c, 2) for c in (sum(bbox(ob), Vector()) / 2)]})
            if WRECK:      # dead ship: lamps and windows go dark
                bsdf.inputs['Emission Strength'].default_value = 0.0
            break

# Strip everything outside the collection, pack, save.
keep = set(coll.all_objects)
for ob in list(bpy.data.objects):
    if ob not in keep:
        bpy.data.objects.remove(ob)
bpy.data.orphans_purge(do_recursive=True)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
external = [img.name for img in bpy.data.images if img.source == 'FILE' and not img.packed_file]
assert not external, external
bpy.context.view_layer.update()

lo = Vector((1e9,) * 3)
hi = Vector((-1e9,) * 3)
for ob in ship:
    a, b = bbox(ob)
    lo, hi = Vector(map(min, lo, a)), Vector(map(max, hi, b))
polys = sum(len(o.data.polygons) for o in ship)


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


manifest = {
    'asset': ASSET,
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': STATE,
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units' + ('_frame0' if WRECK else '_rest'): [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres' + ('_frame0' if WRECK else '_rest'): [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'turrets': turrets,
    'launchers': launchers,
    'lamps_lit': sum(1 for x in lamps if x['lit']),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE)).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON)},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft, line of fire, vent direction); +Z as near '
                   'hull-dorsal as that allows.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
if WRECK:
    manifest['break_up'] = {
        'controls': CONTROLS, 'break_frame': 'frame of the detonation; before it every section is in the intact pose',
        'break_rate': 'drift speed multiplier', 'break_s': BREAK_S, 'sections': sections,
        'flying_pieces': len(flyers), 'explosion_anchors': blasts,
        'fire_anchors': sorted(o.name for o in coll.all_objects if 'fire anchor' in o.name),
        'vent_anchors': sorted(o.name for o in coll.all_objects if 'vent anchor' in o.name),
        'staging': 'Append PHAETON and PHAETON WRECK at the same root transform; set break_frame; show PHAETON '
                   'before it and PHAETON WRECK from it. The swap is seamless: the wreck is in the intact pose '
                   'at break_frame, under the flash.',
    }
    manifest['conventions']['wreck'] = ('Base objects keep their names; damage adds Scar <id> objects '
                                        '(phaeton-geometry/ph_damage.py scar table). Lamps are dark.')
else:
    manifest['conventions']['turrets'] = ('Rotate Turret <i> pivot about its local Z to train within its arc '
                                          'about the slot angle; the rest pose is the slot angle. The two flare '
                                          'launchers are fixed.')
print('EXPORT', STATE, len(ship), 'objects', polys, 'polys; engines', len(engines), 'turret parts trained',
      trained_total, 'turrets', len(turrets), 'launchers', len(launchers), 'lamps', len(lamps),
      'sections', len(sections), 'flyers', len(flyers), 'blasts', len(blasts))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / ASSET), compress=True)
    manifest['asset_sha256'] = sha(OUT / ASSET)
    (OUT / MANIFEST).write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT / ASSET)
