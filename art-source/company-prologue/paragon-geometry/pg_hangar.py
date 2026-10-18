"""The forward hangar: a boat bay let into the bore's forward wall, opening across the bore at the command deck.

Owner (pass 3): "place a hangar in the central ring along one of the wall
faces". The forward face of the bore looks aft across the open centre at the
command deck; craft leave the bay into the bore and clear the ship through its
open top or bottom, inside the shield's centre. The bay runs forward under the
bow module, between the main ring's crown and its belly, clear of every
turret's well and arc.

Sizes are true metres (HANGAR_M), converted with kit.mpu. Parts:
- the bay cut through every hull and detail object it meets, lined dark
  (`Hangar floor / roof / back wall / port wall / starboard wall`), with deck
  guide lights and roof light strips;
- the mouth frame (`Hangar frame ...`) with a seam light round the opening;
- the door, built closed: two leaves that slide sideways into the walls, each
  parented to its own empty (`Hangar door <side> leaf slide`) carrying
  open_axis, open_sign and travel; `--hangar-open` poses them for review;
- anchors: `Hangar mouth` (+X out of the bay, into the bore) and `Hangar launch
  point` (the bore centre at the mouth's height);
- `Hull forward hangar`: the bay's air as a closed solid, unrendered, counted
  in ship_scale.SOLIDS (hangars are enclosed volume).
"""
import math
import bpy
from mathutils import Matrix, Vector
import pg_kit as K
import pg_hull

HANGAR_M = dict(w=21.0, h=14.0, d=35.0)
FLOOR_Z = -6.0
RECORD = {}


def _cut_box(kit, x0, x1, y0, y1, z0, z1):
    for ob in [o for o in kit.hull+kit.detail if o.name in bpy.data.objects and o.type == 'MESH']:
        lo = [min((ob.matrix_world @ Vector(c))[i] for c in ob.bound_box) for i in range(3)]
        hi = [max((ob.matrix_world @ Vector(c))[i] for c in ob.bound_box) for i in range(3)]
        if hi[0] < x0 or lo[0] > x1 or hi[1] < y0 or lo[1] > y1 or hi[2] < z0 or lo[2] > z1:
            continue
        bpy.ops.mesh.primitive_cube_add(size=1, location=((x0+x1)/2, (y0+y1)/2, (z0+z1)/2))
        cut = bpy.context.object
        cut.dimensions = (x1-x0, y1-y0, z1-z0)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        cut.data.materials.append(ob.material_slots[0].material if ob.material_slots else kit.tech)
        K.boolean(ob, cut)


def build(kit, open_door=False):
    m = kit.mpu
    w, h, d = HANGAR_M['w']/m, HANGAR_M['h']/m, HANGAR_M['d']/m
    cx, cy = pg_hull.BORE_C
    # The mouth sits where the bore wall crosses the bay's edges.
    mouth_x = cx+math.sqrt(pg_hull.BORE_R**2-(w/2)**2)
    back_x = mouth_x+d
    z0, z1 = FLOOR_Z, FLOOR_Z+h
    _cut_box(kit, mouth_x-6.0, back_x, -w/2, w/2, z0, z1)
    t = .8
    L = back_x-mouth_x
    xm = (mouth_x+back_x)/2
    parts = [
        ('Hangar floor', (xm, 0, z0-t/2), (L, w, t)),
        ('Hangar roof', (xm, 0, z1+t/2), (L, w, t)),
        ('Hangar back wall', (back_x+t/2, 0, (z0+z1)/2), (t, w+2*t, h+2*t)),
        ('Hangar port wall', (xm, w/2+t/2, (z0+z1)/2), (L, t, h+2*t)),
        ('Hangar starboard wall', (xm, -w/2-t/2, (z0+z1)/2), (L, t, h+2*t)),
    ]
    for name, loc, dims in parts:
        kit.detail.append(kit.box(name, loc, dims, kit.tech, 0))
    # Deck guide lights down the centreline and both edges, roof light strips.
    n = 9
    for i in range(n):
        x = mouth_x+2+(L-4)*i/(n-1)
        for j, y in enumerate((0.0, w/2-1.2, -w/2+1.2)):
            kit.detail.append(kit.box(f'Hangar deck light {i+1}.{j+1}', (x, y, z0+.08), (.9, .5, .16), kit.glow, 0))
    for j, y in enumerate((-w/4, w/4)):
        kit.detail.append(kit.box(f'Hangar roof light {j+1}', (xm, y, z1-.12), (L-4, .7, .2), kit.glow_white, 0))
    # Mouth frame: jambs and lintel and sill, standing a little proud into the bore, with a seam light.
    fx0, fx1 = mouth_x-2.0, mouth_x+1.5
    fw = 2.2
    frame = [
        ('Hangar frame lintel', ((fx0+fx1)/2, 0, z1+fw/2), (fx1-fx0, w+2*fw, fw)),
        ('Hangar frame sill', ((fx0+fx1)/2, 0, z0-fw/2), (fx1-fx0, w+2*fw, fw)),
        ('Hangar frame port jamb', ((fx0+fx1)/2, w/2+fw/2, (z0+z1)/2), (fx1-fx0, fw, h)),
        ('Hangar frame starboard jamb', ((fx0+fx1)/2, -w/2-fw/2, (z0+z1)/2), (fx1-fx0, fw, h)),
    ]
    for name, loc, dims in frame:
        kit.detail.append(kit.box(name, loc, dims, kit.pale, .25))
    for name, loc, dims in (('Hangar frame light lintel', (fx0-.05, 0, z1+.35), (.2, w, .35)),
                            ('Hangar frame light sill', (fx0-.05, 0, z0-.35), (.2, w, .35))):
        kit.detail.append(kit.box(name, loc, dims, kit.glow, 0))
    # The door: two leaves sliding sideways into the walls; built closed.
    dx = mouth_x+2.6
    leaves = []
    for side, label in ((1, 'port'), (-1, 'starboard')):
        pivot = kit.empty(f'Hangar door {label} leaf slide', (dx, side*w/2, (z0+z1)/2), 0, 'ARROWS', 3)
        pivot['open_axis'] = 'Y'
        pivot['open_sign'] = side
        pivot['travel_units'] = round(w/2, 3)
        pivot['travel_m'] = round(w/2*m, 2)
        leaf = kit.box(f'Hangar door {label} leaf', (dx, side*w/4, (z0+z1)/2), (1.0, w/2-.1, h-.1), kit.slate, .15)
        stripe = kit.box(f'Hangar door {label} leaf stripe', (dx-.55, side*w/4, (z0+z1)/2), (.1, w/2-1.6, .5),
                         kit.glow, 0)
        for o in (leaf, stripe):
            mw = o.matrix_world.copy()
            o.parent = pivot
            o.matrix_parent_inverse = pivot.matrix_world.inverted()
            o.matrix_world = mw
        if open_door:
            pivot.location.y += side*w/2
        leaves.append(pivot.name)
    mouth = kit.empty('Hangar mouth', (fx0, 0, (z0+z1)/2), 180, 'SINGLE_ARROW', 8)
    mouth['width_m'], mouth['height_m'], mouth['depth_m'] = HANGAR_M['w'], HANGAR_M['h'], HANGAR_M['d']
    launch = kit.empty('Hangar launch point', (cx, cy, (z0+z1)/2), 180, 'SPHERE', 4)
    # The bay's air, for the enclosed-volume count; never rendered.
    air = kit.box('Hull forward hangar', (xm, 0, (z0+z1)/2), (L, w, h), kit.tech, 0)
    air.hide_render = True
    air.display_type = 'BOUNDS'
    RECORD.update({'mouth': mouth.name, 'launch_point': launch.name, 'door_slides': leaves,
                   'size_m': [HANGAR_M['w'], HANGAR_M['h'], HANGAR_M['d']],
                   'size_units': [round(w, 2), round(h, 2), round(L, 2)],
                   'mouth_x': round(mouth_x, 2), 'floor_z': FLOOR_Z, 'door': 'open' if open_door else 'closed',
                   'face': 'the bore\'s forward wall, opening aft across the bore at the command deck'})
