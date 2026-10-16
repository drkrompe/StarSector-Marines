"""Build one fighter blockout with Blender's Python.

Run from this folder:
  blender --background --factory-startup --python build_fighter.py -- --fighter talon [options]
Each fighter is one module, ft_<id>.py, so agents can work on different
fighters in parallel; fg_kit.py holds the shared helpers. Coordinates are
hull-local, +X nose, +Y port, +Z dorsal; one sprite pixel is one unit
about the .ship centre, so slot and engine locations from the .ship file
are model XY unchanged. Heights are proposed art.

A module defines:
  NAME, HULL_ID, SHIP, SPRITE, MASTER     identity and source files
  KIT                                     fg_kit.Kit keyword arguments (armour colour)
  SLOTS, ENGINES                          copied exactly from the .ship (checked here)
  METRES_PER_UNIT, SCALE                  its own real-world scale and how it was found
  PILOTS                                  [(x, y)] seat positions: one per crew seat
  CLOSEUPS                                [(name, direction, target, frame_m)] pass-2 detail cameras
  build(kit)                              geometry plus every slot and engine anchor; pass-2
                                          detail only when kit.detail (off under --blockout)
Everything goes into the collection "<NAME> | geometry" under the empty
"<Name> root" at the .ship centre (custom props metres_per_unit, axes).
Cameras, lights, the 1.8 m scale figure and the seated pilot proxies live
in "<NAME> | review rig" and are not part of the asset.

Options after a literal "--":
  --fighter ID   module ft_<ID>.py (required)
  --out DIR      renders, .blend and geometry.json (default ./<ID>/)
  --views a,b    top, port-side, front, three-quarter, cockpit-check, parked,
                 and the module's CLOSEUPS ('details' names them all)
                 (default all; 'none' renders nothing)
  --samples N    Cycles samples (default 24)
  --no-save      skip the .blend and geometry.json
  --blockout     the pass-1 build (no detail), for the before half of a
                 before/after: --blockout --out <id>/reference-history/v1-blockout
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   an ad-hoc ortho camera (units), rendered too
Then: python compose_fighter.py --fighter ID   (overlay, IoU, review sheet)
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import re
import sys
import bmesh
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')

parser = argparse.ArgumentParser()
parser.add_argument('--fighter', required=True)
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,cockpit-check,parked,details')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--blockout', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

import fg_kit
importlib.reload(fg_kit)
ft = importlib.import_module('ft_'+args.fighter)
importlib.reload(ft)
OUT = Path(args.out or HERE/args.fighter).resolve()
OUT.mkdir(parents=True, exist_ok=True)
NAME = ft.NAME
COLLECTION = f'{NAME.upper()} | geometry'
RIG = f'{NAME.upper()} | review rig'
ROOT = f'{NAME} root'
M = ft.METRES_PER_UNIT


def read_ship():
    return json.loads(re.sub(r'#.*', '', (GAME/ft.SHIP).read_text(encoding='utf-8')))


def check_against_install():
    if not (GAME/ft.SHIP).exists():
        return None, 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    if len(slots) != len(ft.SLOTS):
        raise RuntimeError(f'{ft.SHIP} weapon slot count changed')
    for mine in ft.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (tuple(mine['location']), mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from {ft.SHIP}: {theirs} vs {ours}")
    engines = [(e['location'][0], e['location'][1], e['width']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in ft.ENGINES]:
        raise RuntimeError(f'engine slots differ from {ft.SHIP}: {engines}')
    return ship, f'verified against the installed {ft.SHIP}'


ship, provenance = check_against_install()
SW, SH = ship['width'], ship['height']
CX, CY = ship['center']  # from the sprite's left and bottom edges

for ob in list(bpy.data.objects):
    bpy.data.objects.remove(ob, do_unlink=True)
scene = bpy.context.scene
model = bpy.data.collections.new(COLLECTION)
rig = bpy.data.collections.new(RIG)
scene.collection.children.link(model)
scene.collection.children.link(rig)
layers = bpy.context.view_layer.layer_collection.children
bpy.context.view_layer.active_layer_collection = layers[COLLECTION]

kit = fg_kit.Kit(**getattr(ft, 'KIT', {}))
kit.m = M
kit.detail = not args.blockout
ft.build(kit)
for ob in list(bpy.data.objects):  # a helper's scratch solid must never reach the asset
    if ob.name.startswith('Temporary'):
        raise RuntimeError(f'{NAME}: scratch object left behind: {ob.name}')
pass1 = getattr(kit, 'pass1', None)
for ob in bpy.data.objects:  # pass-2 parts carry detail_pass, for check_clashes.py
    if pass1 is not None and ob.name not in pass1:
        ob['detail_pass'] = 2
names = {o.name for o in model.objects}
missing = [f"Weapon {s['id']} anchor" for s in ft.SLOTS if f"Weapon {s['id']} anchor" not in names]
missing += [f'Engine {i} exhaust anchor' for i in range(1, len(ft.ENGINES)+1)
            if not any(n.startswith(f'Engine {i} ') and n.endswith('exhaust anchor') for n in names)]
for i in range(1, len(ft.ENGINES)+1):
    missing += [f'Engine {i} {p}' for p in ('mouth lip', 'aft-facing bell', 'dark throat') if f'Engine {i} {p}' not in names]
if missing:
    raise RuntimeError(f'{NAME}: missing named parts {missing}')

root = bpy.data.objects.new(ROOT, None)
root.empty_display_type = 'PLAIN_AXES'
root.empty_display_size = 3
model.objects.link(root)
root['metres_per_unit'] = M
root['axes'] = f'+X nose, +Y port, +Z dorsal; origin at the {ft.HULL_ID}.ship centre'
for ob in model.objects:
    if ob is not root and ob.parent is None:
        ob.parent = root
    if ob.type == 'MESH':
        for poly in ob.data.polygons:
            poly.use_smooth = False

# Model bounds, canopy and pilot fit, all measured in units.
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()


def bounds(objs):
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    for ob in objs:
        for c in ob.evaluated_get(dg).bound_box:
            w = ob.matrix_world @ Vector(c)
            lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    return lo, hi


meshes = [o for o in model.objects if o.type == 'MESH']
lo, hi = bounds(meshes)
size = hi-lo
glass = [o for o in meshes if o.name.startswith('Canopy glass')]
canopy = None
if glass:
    glo, ghi = bounds(glass)
    canopy = {'length_m': round((ghi.x-glo.x)*M, 2), 'width_m': round((ghi.y-glo.y)*M, 2),
              'top_z_units': round(ghi.z, 2)}

bpy.context.view_layer.active_layer_collection = layers[RIG]
pilot_mat = fg_kit.material('Scale proxy orange', (.9, .35, .05), 0, .5)


def proxy_box(name, centre, dims):
    bpy.ops.mesh.primitive_cube_add(size=1, location=centre)
    ob = bpy.context.object
    ob.name = name
    ob.dimensions = dims
    ob.data.materials.append(pilot_mat)
    return ob


# A seated 1.8 m pilot: seat pan to crown 0.92 m, shoulders 0.48 m, torso
# 0.32 m deep, thighs and shins reaching 1.0 m forward of the seat back.
fit = []
for k, (px, py) in enumerate(getattr(ft, 'PILOTS', []), 1):
    hit = None
    for ob in kit.hull:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        h, p, n, _ = ev.ray_cast(inv @ Vector((px, py, 60)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if h:
            z = (ev.matrix_world @ p).z
            hit = z if hit is None or z > hit else hit
    seat = (hit if hit is not None else 0) - .55/M  # seat pan sunk 0.55 m below the deck skin
    head_top = seat+.92/M
    proxy_box(f'Pilot proxy {k} torso', (px-.16/M, py, seat+.46/M), (.32/M, .48/M, .92/M))
    proxy_box(f'Pilot proxy {k} legs', (px+.5/M, py, seat+.18/M), (1.0/M, .40/M, .30/M))
    clear = None
    for g in glass:
        ev = g.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        h, p, n, _ = ev.ray_cast(inv @ Vector((px-.16/M, py, 60)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if h:
            clear = ((ev.matrix_world @ p).z-head_top)*M
    fit.append({'seat': [px, py], 'head_clearance_under_canopy_m': None if clear is None else round(clear, 2)})

# 1.8 m standing figure beside the nose for the side view.
fig_x = hi.x+1.2/M
proxy_box('Scale figure 1.8 m', (fig_x, 0, lo.z+.9/M), (.5/M, .3/M, 1.8/M))


def world_verts(ob):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in me.vertices]
    ev.to_mesh_clear()
    return pts


# Parked ground check, for a hull that stands on named feet (FEET). The
# deck is the plane through the feet's soles, fitted in the hull frame, so
# a hull may crouch pitched or rolled on it: the soles must all lie on it,
# and nothing else may reach it. The parked view turns the hull so the deck
# lies flat; the hull's own axes stay the flight frame.
ground = None
feet = getattr(ft, 'FEET', [])
if feet:
    by_name = {o.name: o for o in meshes}
    absent = [n for n in feet if n not in by_name]
    if absent:
        raise RuntimeError(f'{NAME}: FEET names missing {absent}')
    soles = []
    for n in feet:
        pts = world_verts(by_name[n])
        zmin = min(p.z for p in pts)
        low = [p for p in pts if p.z < zmin+.02]
        soles.append(sum(low, Vector())/len(low))
    # z = a + b x + c y through the soles (least squares)
    A = [[1, p.x, p.y] for p in soles]
    ata = [[sum(r[i]*r[j] for r in A) for j in range(3)] for i in range(3)]
    atz = [sum(r[i]*p.z for r, p in zip(A, soles)) for i in range(3)]
    a0, b0, c0 = Matrix(ata).inverted() @ Vector(atz)
    norm = math.sqrt(1+b0*b0+c0*c0)

    def height(p):
        return (p.z-a0-b0*p.x-c0*p.y)/norm

    resid = [height(p) for p in soles]
    others = sorted((min(height(p) for p in world_verts(o)), o.name) for o in meshes if o.name not in feet)
    legs = tuple(getattr(ft, 'LEG_PARTS', ()))  # parts that carry the feet (arms, struts)
    body = [(h, n) for h, n in others if not (legs and n.startswith(legs))] or others
    ground = {'feet': feet, 'deck_plane_units': {'z0': round(a0, 3), 'dz_dx': round(b0, 4), 'dz_dy': round(c0, 4)},
              'stance_pitch_deg_nose_down': round(math.degrees(math.atan(b0)), 2),
              'stance_roll_deg': round(math.degrees(math.atan(c0)), 2),
              'foot_level_spread_m': round((max(resid)-min(resid))*M, 3),
              'lowest_other_part': others[0][1], 'deck_clearance_m': round(others[0][0]*M, 3),
              'lowest_body_part': body[0][1], 'body_clearance_m': round(body[0][0]*M, 3)}
    if ground['foot_level_spread_m'] > .01 or ground['deck_clearance_m'] <= 0:
        raise RuntimeError(f'{NAME}: parked check failed {ground}')
    bpy.ops.mesh.primitive_plane_add(size=60*max(SW, SH), location=(0, 0, a0))
    deck = bpy.context.object
    deck.name = 'Parked deck plane'
    deck.rotation_euler = (math.atan(c0), -math.atan(b0), 0)  # its normal along (-b, -c, 1)
    deck.parent = root
    deck.data.materials.append(fg_kit.material('Deck grey', (.42, .43, .44), 0, .8))

scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'AgX'
if scene.world is None:
    scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value = (.74, .76, .78, 1)
bg.inputs[1].default_value = .55
span = max(SW, SH)
for name, d, energy in [('Key', Vector((.8, 1, 1.3)), 18000), ('Fill', Vector((-.7, -.9, .8)), 9000)]:
    bpy.ops.object.light_add(type='AREA', location=d*span*1.3)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy*(span/33)**2
    obj.data.shape = 'DISK'
    obj.data.size = span
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

# Cameras framed on the sprite's own extent.
sx0, sx1 = -CY, SH-CY       # X range of the sprite frame
sy0, sy1 = CX-SW, CX        # Y range
mid = Vector(((sx0+sx1)/2, (sy0+sy1)/2, (lo.z+hi.z)/2 if ground else 0))
ASPECT = 16/9
top_scale = max(SH, SW*ASPECT)*1.12
CAMERAS = {
    'top': (mid+Vector((0, 0, 4*span)), top_scale, (0, 0, math.pi)),  # nose left, port below
    'port-side': (mid+Vector((0, 4*span, 0)), max(SH, (fig_x-lo.x))*1.25, None),
    'front': (mid+Vector((4*span, 0, 0)), SW*1.3, None),
    'three-quarter': (mid+Vector((2.6*span, 2.9*span, 2.0*span)), span*1.45, None),
}
if ground:  # low port-bow view of the hull standing on its feet
    CAMERAS['parked'] = (mid+Vector((2.4*span, 3.4*span, .55*span)), span*2.0, None)  # sheet panel is 560x430
    # The feet hang below the hull, and the front shares the bottom row three ways: widen both frames.
    CAMERAS['front'] = (CAMERAS['front'][0], SW*1.9, None)
    CAMERAS['three-quarter'] = (CAMERAS['three-quarter'][0], span*1.7, None)
# Pass-2 close-ups: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = {}
for name, direction, target, frame_m in getattr(ft, 'CLOSEUPS', []):
    t = Vector(target)
    CLOSEUPS[name] = (t+Vector(direction).normalized()*3*span, frame_m/M, t)
for spec in args.cam:
    name, pos, target, scale = spec.split(':')
    t = Vector(map(float, target.split(',')))
    CLOSEUPS[name] = (Vector(map(float, pos.split(','))), float(scale), t)
for name, (pos, scale, t) in CLOSEUPS.items():
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 20*span
    cam.rotation_euler = (t-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, (pos, scale, rot) in CAMERAS.items():
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 20*span
    if name == 'port-side':
        cam.location.x = (lo.x+fig_x)/2
    cam.rotation_euler = rot if rot else (mid-cam.location if name != 'port-side'
                                         else Vector((cam.location.x, 0, 0))-cam.location).to_track_quat('-Z', 'Y').to_euler()
pil = getattr(ft, 'PILOTS', [])
if pil:
    px, py = pil[0]
    # Side-on cutaway: the model is ghosted for this view only, so the
    # seated pilot shows against the canopy and the deck line.
    tgt = Vector((px+.3/M, py, (canopy or {}).get('top_z_units', 2)-.8/M))
    bpy.ops.object.camera_add(location=tgt+Vector((0, 4*span, 0)))
    cam = bpy.context.object
    cam.name = 'Camera cockpit-check'
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = 7/M
    cam.data.clip_end = 20*span
    cam.rotation_euler = (tgt-cam.location).to_track_quat('-Z', 'Y').to_euler()
ghost = bpy.data.materials.new('Cutaway ghost')
ghost.use_nodes = True
nt = ghost.node_tree
nt.nodes.clear()
out = nt.nodes.new('ShaderNodeOutputMaterial')
mix = nt.nodes.new('ShaderNodeMixShader')
mix.inputs[0].default_value = .7
diffuse = nt.nodes.new('ShaderNodeBsdfDiffuse')
diffuse.inputs[0].default_value = (.35, .4, .45, 1)
nt.links.new(nt.nodes.new('ShaderNodeBsdfTransparent').outputs[0], mix.inputs[2])
nt.links.new(diffuse.outputs[0], mix.inputs[1])
nt.links.new(mix.outputs[0], out.inputs[0])

proxies = [o for o in rig.objects if o.name.startswith(('Pilot proxy', 'Scale figure'))]
wanted = []
for v in args.views.split(','):
    wanted += list(CLOSEUPS) if v == 'details' else ([v] if v and v != 'none' else [])
wanted += [spec.split(':')[0] for spec in args.cam if spec.split(':')[0] not in wanted]
for name in wanted:
    cam = bpy.data.objects.get('Camera '+name)
    if cam is None:
        continue
    scene.camera = cam
    scene.render.film_transparent = name == 'top'
    for o in proxies:  # the top view is the silhouette: nothing but the model
        o.hide_render = (name != 'cockpit-check') if o.name.startswith('Pilot') else (name != 'port-side')
    if ground:
        deck.hide_render = name != 'parked'
        root.rotation_euler = (-math.atan(c0), math.atan(b0), 0) if name == 'parked' else (0, 0, 0)
        bpy.context.view_layer.update()
    for ob in meshes:  # cutaway: see the seated pilot through hull and canopy
        if name == 'cockpit-check':
            ob['_mats'] = [s.material.name if s.material else '' for s in ob.material_slots]
            for s in ob.material_slots:
                s.material = ghost
        elif '_mats' in ob:
            for s, mn in zip(ob.material_slots, ob['_mats']):
                s.material = bpy.data.materials.get(mn)
            del ob['_mats']
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)
for o in proxies:
    o.hide_render = False
if ground:
    deck.hide_render = True
    root.rotation_euler = (0, 0, 0)
for ob in meshes:
    if '_mats' in ob:
        for s, mn in zip(ob.material_slots, ob['_mats']):
            s.material = bpy.data.materials.get(mn)
        del ob['_mats']
scene.render.film_transparent = False

if not args.no_save:
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/f'{args.fighter}-geometry.blend'))
    anchors = {o.name: [round(v, 3) for v in o.matrix_world.translation] for o in kit.anchors}
    manifest = {
        'status': ('Blockout (pass 1): silhouette, sections, mass, slots and engines. No detail, damage or textures.'
                   if args.blockout else
                   'Pass 2: plausible detail (panel lines, access plates, intakes, drive, gun and canopy detail, '
                   'sensors) on the pass-1 silhouette. No damage or textures.'),
        'pass': 1 if args.blockout else 2,
        'hull': {'name': NAME, 'hullId': ft.HULL_ID, 'size': ship.get('hullSize'), 'style': ship.get('style'),
                 'source': {'ship': ft.SHIP, 'sprite': ft.SPRITE, 'shape_reference': ft.MASTER,
                            **getattr(ft, 'SOURCE_NOTES', {})}},
        'provenance': provenance,
        'axes': {'+X': 'nose', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': M,
        'scale': dict(ft.SCALE, size_m=[round(v*M, 1) for v in size], canopy=canopy, cockpit_fit=fit),
        **({'parked_check': ground} if ground else {}),
        'sprite': {'size': [SW, SH], 'center': [CX, CY],
                   'pixel_to_model': f'col c, row r (from top) covers X {SH-CY-1}-r..{SH-CY}-r, Y {CX-1}-c..{CX}-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(v, 2) for v in size]},
        'root': {'name': ROOT, 'location': [0, 0, 0], 'note': 'the .ship centre: the game\'s rotation pivot'},
        'collections': {'model': COLLECTION, 'review_rig': RIG},
        'weapon_slots': [dict(s, location=list(s['location']), anchor=f"Weapon {s['id']} anchor") for s in ft.SLOTS],
        'engines': [{'index': i, 'slot_location': [x, y], 'width': w, 'angle': 180,
                     'parts': [f'Engine {i} mouth lip', f'Engine {i} aft-facing bell', f'Engine {i} dark throat'],
                     'exhaust_direction': [-1, 0, 0]} for i, (x, y, w) in enumerate(ft.ENGINES, 1)],
        'anchors': anchors,
        'anchor_convention': 'ARROWS empties; local +X points along the slot angle (0 = nose, 180 = aft)',
        'notes': getattr(ft, 'NOTES', []),
        'cameras': {n: {'position': list(p), 'orthographic_scale': s} for n, (p, s, _) in CAMERAS.items()},
        'closeup_cameras': {n: {'position': [round(v, 3) for v in p], 'target': [round(v, 3) for v in t],
                                'orthographic_scale': round(s, 3)} for n, (p, s, t) in CLOSEUPS.items()},
        'object_count': {'meshes': len(meshes), 'empties': sum(1 for o in model.objects if o.type == 'EMPTY'),
                         'pass2_detail_meshes': sum(1 for o in meshes if o.get('detail_pass') == 2)},
    }
    path = OUT/'geometry.json'
    if path.exists():
        old = json.loads(path.read_text(encoding='utf-8'))
        if 'silhouette' in old:
            manifest['silhouette'] = old['silhouette']
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('FIGHTER', NAME, 'size units', [round(v, 2) for v in size], 'metres', manifest['scale']['size_m'],
          'canopy', canopy, 'fit', fit, 'parked', ground)
