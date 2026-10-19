"""Build the Broadsword cinematic model with Blender's Python.

Run from this folder:
  blender --background --factory-startup --python build_broadsword.py -- [options]
Coordinates are hull-local, +X nose, +Y port, +Z dorsal; one sprite pixel
is one unit about the .ship centre (15, 13), so slot and engine locations
from broadsword.ship are model XY unchanged. Heights are proposed art.

One module per area: bw_hull (nose, fuselage, canopy, aft spine), bw_wings
(gun pod housings, blades, aft strakes), bw_guns (light machine guns and
every slot anchor), bw_engines (the two drive nacelles); bw_kit holds the
shared helpers. Everything is parented to the empty "Broadsword root" at
the .ship centre and lives in the appendable collection
"BROADSWORD | geometry"; review cameras and lights are kept apart in
"BROADSWORD | review rig".

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --blockout     pass-1 build (no detail modules)
  --views a,b    render only these cameras (top, port-side, front, stern,
                 three-quarter, space-sun, plus close-ups nose-detail,
                 cockpit-detail, gun-detail, engine-detail; 'details' names
                 all close-ups; 'none' renders nothing)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 32)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
  --gear down|up pose the landing gear (default down: parked on its
                 wheels; the .blend is saved in this pose). Views 'parked'
                 and 'under-quarter' and the close-up 'gear-detail' show it.
Then run compose_review.py (Python + Pillow + numpy) for the sprite
overlay, silhouette IoU and review sheets.
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
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bw_kit, bw_hull, bw_wings, bw_guns, bw_engines, bw_gear
for module in (bw_kit, bw_hull, bw_wings, bw_guns, bw_engines, bw_gear):
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/broadsword.ship'
SPRITE = 'graphics/ships/broadsword.png'
MASTER = 'art-source/aircraft/fighter-upres/broadsword/master-v1.png'
COLLECTION = 'BROADSWORD | geometry'
RIG = 'BROADSWORD | review rig'
ROOT = 'Broadsword root'

# Real-world scale (pipeline pass 4). Every hull has its own scale, found
# from what it physically holds (owner policy); a fighter has no deck
# program, so it is the cockpit. Her one pilot sits under a canopy 6.1
# units long; real single-seat canopies run 2.6-3.5 m, which puts her at
# 0.43-0.57 m per unit, and 3.05 m gives 0.50. At 0.50 she is 15.5 m long
# with a 14.1 m span (an F-16 is 15.1 m), a seated 1.8 m pilot clears the
# glass by about 0.23 m, and each exhaust bell is 1.8 m across, heavy-
# fighter size. The canopy is broad (1.95 m at the sill against 0.9-1.3 m
# on real aircraft): read as the sprite's wide glazed hood, not a tighter
# scale. To sit her beside a hull at another scale, scale the root by
# METRES_PER_UNIT / that hull's (0.50 / 0.99 = 0.51 for the Legion).
METRES_PER_UNIT = 0.50
SCALE_RANGE = (0.43, 0.57)
PILOT_SEAT_X = 6.3  # seat back; legs run forward under the glass

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--blockout', action='store_true')
parser.add_argument('--views', default='top,port-side,front,stern,three-quarter,space-sun')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=32)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--gear', choices=('down', 'up'), default='down')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE).resolve()
OUT.mkdir(parents=True, exist_ok=True)


def read_ship():
    text = (GAME/SHIP).read_text(encoding='utf-8')
    return json.loads(re.sub(r'#.*', '', text))


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    if len(slots) != len(bw_guns.SLOTS):
        raise RuntimeError('broadsword.ship weapon slot count changed')
    for mine in bw_guns.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from broadsword.ship: {theirs} vs {ours}")
    engines = [(e['location'][0], e['location'][1], e['width']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in bw_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from broadsword.ship: {engines}')
    if (ship['width'], ship['height'], ship['center']) != (30, 33, [15, 13]):
        raise RuntimeError('broadsword.ship sprite frame changed')
    return 'verified against the installed broadsword.ship'


provenance = check_against_install()

for ob in list(bpy.data.objects):
    bpy.data.objects.remove(ob, do_unlink=True)
scene = bpy.context.scene
model = bpy.data.collections.new(COLLECTION)
rig = bpy.data.collections.new(RIG)
scene.collection.children.link(model)
scene.collection.children.link(rig)
bpy.context.view_layer.active_layer_collection = bpy.context.view_layer.layer_collection.children[COLLECTION]

kit = bw_kit.Kit(detail=not args.blockout)
bw_hull.build(kit)
bw_wings.build(kit)
bw_engines.build(kit)
bw_guns.build(kit)
if not args.blockout:
    bw_gear.build(kit)

root = bpy.data.objects.new(ROOT, None)
root.empty_display_type = 'PLAIN_AXES'
root.empty_display_size = 4
model.objects.link(root)
root['metres_per_unit'] = METRES_PER_UNIT
root['axes'] = '+X nose, +Y port, +Z dorsal; origin at the broadsword.ship centre'
for ob in model.objects:
    if ob is not root and ob.parent is None:
        ob.parent = root
for ob in model.objects:
    if ob.type == 'MESH':
        for poly in ob.data.polygons:
            poly.use_smooth = False


# Parked check (gear down): the wheels' soles stand on one level deck at
# bw_gear.DECK_Z, and nothing else reaches it.
parked = None
if not args.blockout:
    bw_gear.pose(1.0)
    dg0 = bpy.context.evaluated_depsgraph_get()

    def lowest(ob):
        ev = ob.evaluated_get(dg0)
        me = ev.to_mesh()
        z = min((ev.matrix_world @ v.co).z for v in me.vertices)
        ev.to_mesh_clear()
        return z
    meshes = [o for o in model.objects if o.type == 'MESH']
    soles = {n: lowest(bpy.data.objects[n]) for n in bw_gear.FEET}
    others = sorted((lowest(o), o.name) for o in meshes if not o.name.startswith(tuple(f'Hull {l[0]}' for l in bw_gear.LEGS)))
    spread = max(soles.values())-min(soles.values())
    parked = {'deck_z_units': bw_gear.DECK_Z, 'feet': list(soles),
              'sole_spread_m': round(spread*METRES_PER_UNIT, 3),
              'sole_error_m': round(max(abs(z-bw_gear.DECK_Z) for z in soles.values())*METRES_PER_UNIT, 3),
              'lowest_body_part': others[0][1],
              'body_clearance_m': round((others[0][0]-bw_gear.DECK_Z)*METRES_PER_UNIT, 2)}
    if parked['sole_error_m'] > .01 or parked['body_clearance_m'] <= .3:
        raise RuntimeError(f'parked check failed {parked}')
    print('parked', parked)
    # Gear up: nothing of the gear but its doors may hang below the belly it folded into.
    bw_gear.pose(0.0)
    stowed = {}
    for leg, *_ in bw_gear.LEGS:
        n = f'Hull {leg}'
        legs = [o for o in meshes if o.name.startswith(n) and ' door ' not in o.name]
        doors = [o for o in meshes if o.name.startswith(n+' door ')]
        stowed[leg] = round((min(lowest(o) for o in legs)-min(lowest(o) for o in doors))*METRES_PER_UNIT, 3)
    if min(stowed.values()) < 0:
        raise RuntimeError(f'stowed gear hangs below its doors {stowed}')
    parked['stowed_leg_above_door_bottom_m'] = stowed
    bw_gear.pose(1.0 if args.gear == 'down' else 0.0)

bpy.context.view_layer.active_layer_collection = bpy.context.view_layer.layer_collection.children[RIG]
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = map(int, args.res.split('x'))
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'AgX'
if scene.world is None:
    scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']

STUDIO = []
for name, loc, energy, size in [('Key', (24, 30, 40), 18000, 30), ('Fill', (-20, -26, 22), 9000, 26)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()
    STUDIO.append(obj)
# Hard space sun from high behind: elevated 55 deg, from aft and a little to port.
bpy.ops.object.light_add(type='SUN', location=(0, 0, 30))
sun = bpy.context.object
sun.name = 'Space sun'
sun.data.energy = 6.0
sun.data.angle = math.radians(.53)
sun_dir = Vector((-math.cos(math.radians(55))*.94, math.cos(math.radians(55))*.34, math.sin(math.radians(55)))).normalized()
sun.rotation_euler = (-sun_dir).to_track_quat('-Z', 'Y').to_euler()

TARGET = Vector((3.5, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (3.5, 0, 60), 52),
    ('port-side', (3.5, 70, 0), 40),
    ('front', (70, 0, 0), 36),
    ('stern', (-70, 0, 0), 36),
    ('three-quarter', (45, 50, 34), 44),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('nose-detail', (40, 22, 22), (13, 0, 1), 16),
    ('cockpit-detail', (30, 20, 26), (6, 0, 2), 13),
    ('gun-detail', (30, 30, 18), (5, 9.5, .5), 12),
    ('engine-detail', (-40, 26, 18), (-9, 5, -.5), 17),
    ('gear-detail', (30, 34, -14), (2, 2, -3.5), 20),
]
if not args.blockout:
    # Parked: low port-bow view, the deck plane under the wheels.
    CAMERAS.append(('parked', (52, 46, 4), 46))
    CAMERAS.append(('under-quarter', (40, 44, -36), 46))
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, scale in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 500
    if name == 'top':
        cam.rotation_euler = (0, 0, math.pi)  # nose left, port below
    else:
        aim = TARGET+Vector((0, 0, -1.5)) if name in ('parked', 'under-quarter') else TARGET
        cam.rotation_euler = (aim-cam.location).to_track_quat('-Z', 'Y').to_euler()
# The parked deck shows only in the parked view, and only with the gear down.
deck = None
if not args.blockout:
    bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, bw_gear.DECK_Z))
    deck = bpy.context.object
    deck.name = 'Parked deck plane'
    deck.data.materials.append(bw_kit.material('Deck grey', (.42, .43, .44), 0, .8))
    deck.hide_render = True
# Perspective still under the space sun: starboard bow quarter, looking
# back and down across her toward the sun's side.
bpy.ops.object.camera_add(location=(44, -40, 26))
cam = bpy.context.object
cam.name = 'Camera space-sun'
cam.data.lens = 55
cam.rotation_euler = (Vector((2.5, 0, -.8))-cam.location).to_track_quat('-Z', 'Y').to_euler()

wanted = []
for v in [v for v in args.views.split(',') if v and v != 'none']:
    wanted += [c[0] for c in CLOSEUPS[:5]] if v == 'details' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]


def studio(on):
    for light in STUDIO:
        light.hide_render = not on
    sun.hide_render = on
    bg.inputs[0].default_value = (.74, .76, .78, 1) if on else (0, 0, 0, 1)
    bg.inputs[1].default_value = .55 if on else 0.0


for name in wanted:
    scene.camera = bpy.data.objects['Camera '+name]
    studio(name != 'space-sun')
    if deck:
        deck.hide_render = not (name == 'parked' and args.gear == 'down')
    # The top view has a transparent film so its alpha is the silhouette.
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)
studio(True)
scene.render.film_transparent = False

if not args.no_save:
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'broadsword-geometry.blend'))
    if not args.blockout:
        bw_gear.pose(0.0)  # flight dimensions: gear up
    lo = Vector((1e9,)*3)
    hi = Vector((-1e9,)*3)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in model.objects:
        if ob.type == 'MESH':
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w))
                hi = Vector(map(max, hi, w))
    size = [b-a for a, b in zip(lo, hi)]
    m = METRES_PER_UNIT

    def pilot_clearance():
        # Seat pan 0.55 m below the deck skin; seated crown 0.92 m above it,
        # measured under the canopy glass at the pilot's head.
        x = PILOT_SEAT_X-.16/m
        deck = bw_hull.at(bw_hull.FUSELAGE, x)[3]
        glass = bpy.data.objects['Canopy glass'].evaluated_get(dg)
        hit, p, n, _ = glass.ray_cast(Vector((x, 0, 60)), Vector((0, 0, -1)))
        return (p.z-(deck-.55/m+.92/m))*m
    canopy = (bw_hull.CANOPY[0][0]-bw_hull.CANOPY[-1][0], 2*max(w for _, w, _ in bw_hull.CANOPY))
    anchors = {ob.name: [round(v, 3) for v in ob.matrix_world.translation] for ob in kit.anchors}
    manifest = {
        'status': ('Blockout (pass 1)' if args.blockout else 'Pass 3: plausible detail plus retractable landing gear')
                  + ': silhouette, sections, slots and engines' + ('' if args.blockout else
                  ', armour plates, canopy frame, grilles, gun and nacelle detail, tricycle gear') + '. No damage or textures.',
        'hull': {'name': 'Broadsword', 'hullId': 'broadsword', 'size': 'FIGHTER', 'style': 'LOW_TECH',
                 'designation': 'Heavy Fighter', 'wing': 'broadsword_wing (Legion XIV Elite carries two)',
                 'variant': 'broadsword_Fighter: lightmg x2 (WS 001/002, linked); missile slots empty',
                 'ship_system': 'flarelauncher_fighter (hidden SYSTEM slot WS 005)', 'shield': 'NONE',
                 'crew': [1, 1],
                 'source': {'ship': SHIP, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id broadsword)',
                            'detail_reference': MASTER, 'lights_layer': 'none exists for this hull'}},
        'provenance': provenance,
        'axes': {'+X': 'nose', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': {
            'method': 'cockpit: one pilot under a canopy %.1f units long; real single-seat canopies are 2.6-3.5 m, '
                      'so 3.05 m gives the published value and the range follows; overall length checked against '
                      '12-22 m fighters' % canopy[0],
            'size_m': [round(v*m, 1) for v in size],
            'cross_checks_m': {'length (F-16 15.1 m, F/A-18 17.1 m)': round(size[0]*m, 1),
                               'span': round(size[1]*m, 1), 'height keel to canopy': round(size[2]*m, 1),
                               'single-seat canopy l x w': [round(canopy[0]*m, 1), round(canopy[1]*m, 1)],
                               'exhaust bell mouth diameter': round(2*bw_engines.BELL_R*m, 2),
                               'person 1.8 m in units': round(1.8/m, 2),
                               'seated 1.8 m pilot head clearance under the canopy': round(pilot_clearance(), 2)},
            'range': list(SCALE_RANGE),
            'beside_the_legion': 'scale the root by %.2f (0.50 / the Legion 0.99, legion-geometry/geometry.json); '
                                 'her %.1f m span is about %.0f Legion units, inside its hangar tunnels (being widened '
                                 'to about 40 forward and 46 aft for the Hoplon); fighters leave through the mouths '
                                 'under the wing arms' % (m/.99, size[1]*m, size[1]*m/.99),
            'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number',
        },
        'sprite': {'size': [30, 33], 'center': [15, 13],
                   'pixel_to_model': 'col c, row r (from top) covers X 19-r..20-r, Y 14-c..15-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(v, 2) for v in size]},
        'root': {'name': ROOT, 'location': [0, 0, 0], 'note': 'the .ship centre: the game\'s rotation pivot'},
        'collections': {'model': COLLECTION, 'review_rig': RIG},
        'weapon_slots': [dict(s, location=list(s['location']),
                              anchor=f"Weapon {s['id']} anchor",
                              anchor_location=[*bw_guns.ANCHOR_XY[s['id']], round(bw_guns.ANCHOR_Z[s['id']], 2)],
                              fitted=bw_guns.FITTED.get(s['id']),
                              **({'muzzle': f"Weapon {s['id']} muzzle"} if s['id'] in bw_guns.FITTED else {}))
                         for s in bw_guns.SLOTS],
        'engines': [{'index': i, 'slot_location': [x, y], 'width': w, 'angle': 180, 'style': 'LOW_TECH',
                     'objects': f'Engine {i} ...',
                     'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip', 'throat': f'Engine {i} dark throat',
                     'anchor': f"Engine {i} {'port' if y > 0 else 'starboard'} exhaust anchor",
                     'anchor_location': [bw_engines.ANCHOR_X, (1 if y > 0 else -1)*bw_engines.CY, bw_engines.AXIS_Z],
                     'exhaust_direction': [-1, 0, 0], 'bell_radius': bw_engines.BELL_R, 'lip_radius': bw_engines.LIP_R}
                    for i, (x, y, w) in enumerate(bw_engines.ENGINES, 1)],
        'slot_symmetry_note': 'Integer .ship slots straddle the centre line of an even-width sprite (11/-10, 7/-6, '
                              '6/-5); the model and its anchors are symmetric at the mean (10.5, 6.5, 5.5).',
        'anchors': anchors,
        **({} if args.blockout else {'landing_gear': {
            'legs': [{'name': f'Hull {l}', 'hinge': f'Hull {l} hinge', 'hinge_location': [x, y, z],
                      'wheel_radius': r, 'doors': [f'Hull {l} door {s} hinge' for s in ('port', 'starboard')]}
                     for l, x, y, z, r, _, _ in bw_gear.LEGS],
            'pose': 'built down. Stow: rotate each leg hinge retract_deg about its local Y (foot swings aft into '
                    'the fuselage or nacelle) and each door hinge back from open_deg to 0 about its local X. '
                    'bw_gear.pose(t) is the reference: t=1 down, 0 up; doors are open for t >= 1/3.',
            'saved_pose': args.gear,
            'dimensions_note': 'dimensions and scale sizes are measured gear up (flight)',
            'parked_check': parked}}),
        'anchor_convention': 'ARROWS empties; local +X points along the slot angle (0 = nose, 180 = aft)',
        'object_names': {'Hull ...': 'nose, forward fuselage, aft spine and their plates',
                         'Canopy ...': 'glass and frame', 'Wing <side> ...': 'gun pod housings, blades, aft strakes',
                         'Weapon <slot id> ...': 'gun geometry and slot anchors',
                         'Engine <i> ...': 'nacelle, collar, mouth lip, aft-facing bell, dark throat',
                         'Flare launcher WS 005 ...': 'the system launcher on the spine'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'nose left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
    }
    path = OUT/'geometry.json'
    if path.exists():  # keep the silhouette record compose_review.py wrote
        old = json.loads(path.read_text(encoding='utf-8'))
        if 'silhouette' in old:
            manifest['silhouette'] = old['silhouette']
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('dimensions', manifest['dimensions'], 'metres', manifest['scale']['size_m'])
