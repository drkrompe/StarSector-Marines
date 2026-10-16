"""The fifteen turrets at legion.ship's weapon slots, as the XIV skin types them.

Every turret is a fixed barbette seated on the hull with `surface`, and a
trainable part parented to an empty named 'Weapon <slot> pivot'. The pivot
sits at the slot on top of the barbette and rests at the slot angle; rotating
it about its local Z trains the whole turret, so any turret can be keyed (the
cinematic's scrutiny beat uses WS 007). Only what stands above the traverse
gap trains: the barbette, with its foot flange and chamfered lip, is fixed.

Ring radii are read off the sprite's turret rings. Fittings follow the
legion_xiv_Elite variant, shaped after the game's own turret sprites:
  squall     WS 000  armoured box launcher, five cells side by side under ribbed
                     cell covers, on a trunnioned yoke, a director on its flank
  hurricane  WS 001  squarer box launcher, one large cell door, forked guide rails
  heavyac    WS 004/005/007  naval gunhouse, three barrels in mantlets
  flak       WS 002/003  lower gunhouse with tall side frames (the sprite's
                     prongs) carrying sights, twin long barrels
  vulcan     small gatling mounts: faceted dome, gatling shroud, ammo drum
  none       WS 014/015 are empty in Elite: a plain small single-gun mount
Detail is sized for C2 (1 unit = 0.65 m, ~8-9 px at 105 m): nothing under
~0.4 unit is modelled. Repeated parts share one mesh (linked duplicates).
"""
import math
import bpy
import lg_kit
from mathutils import Vector
from mathutils.bvhtree import BVHTree

SLOTS = [  # legion.ship, with the legion_xiv.skin type changes
    {'id': 'WS 000', 'location': (42, 56), 'angle': 30, 'arc': 160, 'size': 'LARGE', 'type': 'MISSILE', 'weapon': 'squall'},
    {'id': 'WS 001', 'location': (42, -56), 'angle': -30, 'arc': 160, 'size': 'LARGE', 'type': 'MISSILE', 'weapon': 'hurricane'},
    {'id': 'WS 002', 'location': (13, 107), 'angle': 55, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'flak'},
    {'id': 'WS 003', 'location': (13, -107), 'angle': -55, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'flak'},
    {'id': 'WS 004', 'location': (80, 34), 'angle': 20, 'arc': 160, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 005', 'location': (104, 0), 'angle': 0, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 007', 'location': (80, -34), 'angle': -20, 'arc': 160, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 008', 'location': (-58, 130), 'angle': 90, 'arc': 180, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 009', 'location': (-58, -130), 'angle': -90, 'arc': 180, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 014', 'location': (-8, 118), 'angle': 82, 'arc': 156, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'none in Elite'},
    {'id': 'WS 015', 'location': (-8, -118), 'angle': -82, 'arc': 156, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'none in Elite'},
    {'id': 'WS 018', 'location': (36, 91), 'angle': 24, 'arc': 100, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 019', 'location': (36, -91), 'angle': -24, 'arc': 100, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 020', 'location': (76, 60), 'angle': 39, 'arc': 160, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 021', 'location': (76, -60), 'angle': -39, 'arc': 160, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
]
# Slot types before the skin, for checking against legion.ship.
BASE_TYPE = {'WS 000': 'BALLISTIC', 'WS 001': 'BALLISTIC', 'WS 002': 'COMPOSITE', 'WS 003': 'COMPOSITE',
             'WS 004': 'COMPOSITE', 'WS 005': 'COMPOSITE', 'WS 007': 'COMPOSITE'}
RING = {'LARGE': 12.5, 'MEDIUM': 9.5, 'SMALL': 5.5}
KEYABLE = 'WS 007'           # starboard dorsal heavy autocannon: trains on Second Wind in C2
KEY_TEST_ANGLES = (-90, -100, 60)   # laid on Second Wind abeam to starboard, and both arc limits
MIRRORED = {'WS 004': KEYABLE}      # its port twin is tested at the mirrored angles, so the pair matches
PIVOT_Z = {}                 # slot id -> pivot height, filled by build()
ARC_REPORT = {}              # slot id -> lift and the swept angles that clash (report only)
GAP = .08                    # traverse gap: the trainable part starts this far (x r) above the barbette

_MESH = {}                   # shared mesh data for linked duplicates


# --- primitives -------------------------------------------------------------

def _bake(ob):
    bpy.context.view_layer.objects.active = ob
    for m in list(ob.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)
    return ob


def _lathe(kit, name, profile, n, mat, bevel=0, phase=0.0):
    """Faceted solid of revolution about local Z from [(radius, z), ...], capped flat."""
    verts, faces = [], []
    for rad, z in profile:
        verts += [(rad*math.cos(phase+2*math.pi*i/n), rad*math.sin(phase+2*math.pi*i/n), z) for i in range(n)]
    faces.append(tuple(reversed(range(n))))
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1) % n, (r+1)*n+(i+1) % n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat, bevel)


def _place(ob, pivot, loc, rot=(0, 0, 0)):
    ob.parent = pivot     # loc is in the pivot's frame: +X along the barrels
    ob.location = loc
    ob.rotation_euler = rot
    return ob


def _shared(key, make, name, pivot, loc, rot=(0, 0, 0)):
    """A linked duplicate of the mesh `make()` builds once per key (modifiers baked)."""
    mesh = _MESH.get(key)
    if mesh is None:
        ob = _bake(make())
        ob.name = name
        ob.data.name = 'Weapon shared '+key
        _MESH[key] = ob.data
    else:
        ob = bpy.data.objects.new(name, mesh)
        bpy.context.collection.objects.link(ob)
    return _place(ob, pivot, loc, rot)


def _box(kit, name, pivot, loc, dims, mat, bevel=.12, share=False):
    dims = tuple(round(d, 3) for d in dims)
    make = lambda: kit.box(name, (0, 0, 0), dims, mat, bevel=bevel)
    if share:
        return _shared(f'box {mat.name} {dims} {bevel}', make, name, pivot, loc)
    return _place(make(), pivot, loc)


def _cyl(kit, name, pivot, loc, radius, depth, mat, axis='X', vertices=12, bevel=.05, share=True):
    rot = {'X': (0, math.pi/2, 0), 'Y': (math.pi/2, 0, 0), 'Z': (0, 0, 0)}[axis]
    make = lambda: kit.cylinder(name, (0, 0, 0), radius, depth, mat, axis='Z', vertices=vertices, bevel=bevel)
    if share:
        return _shared(f'cyl {mat.name} {radius:.3f} {depth:.3f} {vertices} {bevel}', make, name, pivot, loc, rot)
    return _place(make(), pivot, loc, rot)


# --- the trainable parts ----------------------------------------------------

def _traverse_ring(kit, sid, pivot, r):
    """Dark roller-path ring between barbette and house: the traverse gap's shadow line."""
    return [_cyl(kit, f'Weapon {sid} traverse ring', pivot, (0, 0, GAP*r/2), .86*r, GAP*r+.04, kit.dark,
                 axis='Z', vertices=24, bevel=0, share=False)]


def _gunhouse(kit, sid, pivot, r, mat, roof):
    """Naval gunhouse lofted along X: narrow rear bustle, full cheeks, angled cheeks
    into a near-vertical gun face, with a roof chamfer that catches top light."""
    z0 = GAP*r
    oc = lg_kit.octagon
    stations = [
        (-.8*r, oc(-.48*r, .48*r, z0, (roof-.06)*r, .12*r, .05*r)),
        (-.55*r, oc(-.78*r, .78*r, z0, roof*r, .16*r, .05*r)),
        (.32*r, oc(-.78*r, .78*r, z0, roof*r, .16*r, .05*r)),
        (.62*r, oc(-.6*r, .6*r, z0, (roof-.1)*r, .1*r, .05*r)),
    ]
    house = _place(lg_kit.loft_x(kit, f'Weapon {sid} turret house', stations, mat, bevel=.1), pivot, (0, 0, 0))
    # Raised roof armour plate: one more lit ledge above the chamfer line.
    parts = [house,
             _box(kit, f'Weapon {sid} roof plate', pivot, (-.12*r, 0, (roof+.025)*r), (.76*r, 1.05*r, .05*r), mat, .06),
             _box(kit, f'Weapon {sid} roof hatch', pivot, (.05*r, -.3*r, (roof+.06)*r), (.2*r, .2*r, .05*r),
                  kit.plate, .04)]
    return parts, z0


def _gun(kit, slot, pivot, r):
    sid = slot['id']
    weapon = slot['weapon']
    parts = _traverse_ring(kit, sid, pivot, r)
    if slot['size'] == 'SMALL':
        return parts + _small_mount(kit, slot, pivot, r)
    flak = weapon == 'flak'
    roof = (.52 if flak else .62)*r
    house, z0 = _gunhouse(kit, sid, pivot, r, kit.orange, roof/r)
    parts += house
    face = .62*r
    bz = (.27 if flak else .32)*r           # bore axis height
    # Armoured face: a plate proud of the gun face, wider than it, chamfered.
    sh = (.42 if flak else .5)*r
    parts.append(_box(kit, f'Weapon {sid} turret shield', pivot, (face+.03*r, 0, z0+sh/2), (.1*r, 1.24*r, sh),
                      kit.plate, .12))
    shield_front = face+.08*r
    n, length, rad, spacing = {'heavyac': (3, 1.28, .07, .27), 'flak': (2, 1.35, .055, .34)}[weapon]
    for k in range(n):
        y = (k-(n-1)/2)*spacing*r
        parts.append(_box(kit, f'Weapon {sid} embrasure {k+1}', pivot, (shield_front, y, bz),
                          (.04*r, .17*r, .3*r), kit.dark, 0, share=True))
        parts.append(_box(kit, f'Weapon {sid} mantlet {k+1}', pivot, (shield_front+.05*r, y, bz),
                          (.1*r, .14*r, .18*r), kit.steel, .08, share=True))
        parts.append(_cyl(kit, f'Weapon {sid} blast bag {k+1}', pivot, (shield_front+.135*r, y, bz),
                          rad*1.55*r, .08*r, kit.dark, vertices=10, bevel=.03))
        x0 = shield_front+.17*r
        L = length*r
        parts.append(_cyl(kit, f'Weapon {sid} barrel {k+1}', pivot, (x0+L/2, y, bz), rad*r, L, kit.steel,
                          vertices=12, bevel=.03))
        parts.append(_cyl(kit, f'Weapon {sid} barrel jacket {k+1}', pivot, (x0+.16*L, y, bz), rad*1.38*r, .32*L,
                          kit.steel, vertices=12, bevel=.04))
        parts.append(_box(kit, f'Weapon {sid} muzzle brake {k+1}', pivot, (x0+L+.04*r, y, bz),
                          (.13*r, rad*2.7*r, rad*2.3*r), kit.steel, .06, share=True))
    # Trunnion bosses on the cheeks, where the elevating mass rides.
    for side, s in (('port', 1), ('starboard', -1)):
        parts.append(_cyl(kit, f'Weapon {sid} trunnion {side}', pivot, (.36*r, s*.8*r, bz+.02*r), .13*r, .1*r,
                          kit.steel, axis='Y', vertices=12, bevel=.04))
    if flak:
        # The sprite's twin prongs: tall side frames carrying the fire-control sights.
        for side, s in (('port', 1), ('starboard', -1)):
            parts.append(_box(kit, f'Weapon {sid} flak frame {side}', pivot, (.3*r, s*.66*r, roof+.12*r),
                              (.62*r, .1*r, .3*r), kit.plate, .08, share=True))
            parts.append(_box(kit, f'Weapon {sid} sight hood {side}', pivot, (.5*r, s*.66*r, roof+.32*r),
                              (.24*r, .14*r, .12*r), kit.steel, .05, share=True))
    else:
        for side, s in (('port', 1), ('starboard', -1)):
            parts.append(_box(kit, f'Weapon {sid} sight hood {side}', pivot, (.28*r, s*.42*r, roof+.07*r),
                              (.22*r, .15*r, .12*r), kit.steel, .05, share=True))
    # Rangefinder across the rear roof with armoured ears beyond the cheeks.
    rf_x, rf_z = -.38*r, roof+.12*r
    parts.append(_box(kit, f'Weapon {sid} rangefinder pedestal', pivot, (rf_x, 0, roof+.05*r),
                      (.22*r, .3*r, .1*r), kit.plate, .04))
    parts.append(_cyl(kit, f'Weapon {sid} rangefinder', pivot, (rf_x, 0, rf_z), .075*r, 1.72*r, kit.steel,
                      axis='Y', vertices=10, bevel=.03))
    for side, s in (('port', 1), ('starboard', -1)):
        parts.append(_box(kit, f'Weapon {sid} rangefinder ear {side}', pivot, (rf_x, s*.88*r, rf_z),
                          (.24*r, .1*r, .22*r), kit.plate, .05, share=True))
    parts.append(_box(kit, f'Weapon {sid} bustle hatch', pivot, (-.81*r, 0, (z0+roof)/2),
                      (.04*r, .4*r, .28*r), kit.plate, .03))
    return parts


def _small_mount(kit, slot, pivot, r):
    """Compact dome mount: a Vulcan gatling, or a plain single gun on the empty slots."""
    sid = slot['id']
    z0 = GAP*r
    dome = [(.78*r, z0), (.78*r, z0+.2*r), (.58*r, z0+.42*r), (.3*r, z0+.5*r)]
    parts = [_place(_lathe(kit, f'Weapon {sid} turret house', dome, 10, kit.plate, bevel=.06, phase=math.pi/10),
                    pivot, (0, 0, 0))]
    bz = z0+.25*r
    parts.append(_box(kit, f'Weapon {sid} turret shield', pivot, (.6*r, 0, bz), (.26*r, .62*r, .36*r), kit.steel, .1,
                      share=True))
    x0 = .73*r
    if slot['weapon'] == 'vulcan':
        L = 1.05*r
        parts.append(_cyl(kit, f'Weapon {sid} barrel 1', pivot, (x0+L/2, 0, bz), .13*r, L, kit.steel,
                          vertices=10, bevel=.03))
        parts.append(_cyl(kit, f'Weapon {sid} gatling clamp', pivot, (x0+.82*L, 0, bz), .17*r, .1*r, kit.steel,
                          vertices=10, bevel=.02))
        parts.append(_cyl(kit, f'Weapon {sid} gatling muzzle', pivot, (x0+L+.01*r, 0, bz), .1*r, .04*r, kit.dark,
                          vertices=10, bevel=0))
        parts.append(_box(kit, f'Weapon {sid} ammo drum', pivot, (-.05*r, .62*r, z0+.22*r), (.42*r, .24*r, .3*r),
                          kit.steel, .08, share=True))
        parts.append(_box(kit, f'Weapon {sid} sight', pivot, (.15*r, -.28*r, z0+.53*r), (.2*r, .14*r, .12*r),
                          kit.steel, .04, share=True))
    else:
        L = 1.2*r
        parts.append(_box(kit, f'Weapon {sid} mantlet 1', pivot, (x0+.03*r, 0, bz), (.12*r, .2*r, .2*r), kit.steel, .06,
                          share=True))
        parts.append(_cyl(kit, f'Weapon {sid} barrel 1', pivot, (x0+.09*r+L/2, 0, bz), .08*r, L, kit.steel,
                          vertices=10, bevel=.02))
        parts.append(_box(kit, f'Weapon {sid} muzzle brake 1', pivot, (x0+.09*r+L+.05*r, 0, bz),
                          (.14*r, .22*r, .19*r), kit.steel, .04, share=True))
    return parts


def _launcher(kit, slot, pivot, r):
    sid = slot['id']
    parts = _traverse_ring(kit, sid, pivot, r)
    z0 = GAP*r
    squall = slot['weapon'] == 'squall'
    # Trainable deck and the yoke whose arms carry the elevating box on trunnions.
    parts.append(_place(_lathe(kit, f'Weapon {sid} launcher deck', [(.9*r, z0), (.9*r, z0+.1*r), (.84*r, z0+.15*r)],
                               16, kit.plate, bevel=0, phase=math.pi/16), pivot, (0, 0, 0)))
    deck_top = z0+.15*r
    half = .58*r if squall else .5*r
    length = 1.42*r if squall else 1.25*r
    height = .48*r if squall else .56*r
    hz = deck_top+.1*r+height/2
    for side, s in (('port', 1), ('starboard', -1)):
        parts.append(_box(kit, f'Weapon {sid} launcher yoke arm {side}', pivot, (0, s*(half+.07*r), deck_top+.2*r),
                          (.5*r, .12*r, .42*r), kit.orange, .1, share=True))
        parts.append(_cyl(kit, f'Weapon {sid} trunnion {side}', pivot, (0, s*(half+.15*r), hz), .12*r, .06*r,
                          kit.steel, axis='Y', vertices=12, bevel=.04))
    # Armoured box lofted along X: a sloped glacis over the cell face catches the top light.
    rear, front = -.04*r-length/2, -.04*r+length/2
    zb, zt = hz-height/2, hz+height/2
    glacis = .16*r
    oc = lg_kit.octagon
    stations = [(rear, oc(-half, half, zb, zt, .1*r, .05*r)),
                (front-.24*r, oc(-half, half, zb, zt, .1*r, .05*r)),
                (front, oc(-half+.03*r, half-.03*r, zb, zt-glacis, .06*r, .05*r))]
    parts.append(_place(lg_kit.loft_x(kit, f'Weapon {sid} launcher house', stations, kit.plate, bevel=.1),
                        pivot, (0, 0, 0)))
    fz = (zb+zt-glacis)/2                   # cell face centre
    fh = zt-glacis-zb
    parts.append(_box(kit, f'Weapon {sid} launcher cell face', pivot, (front+.02*r, 0, fz),
                      (.06*r, 2*half-.14*r, fh-.1*r), kit.dark, 0))
    cover_len = front-.24*r-rear-.12*r
    cover_x = rear+.06*r+cover_len/2
    if squall:
        cells = 5
        pitch = (2*half-.16*r)/cells
        for k in range(cells):
            y = (k-(cells-1)/2)*pitch
            parts.append(_box(kit, f'Weapon {sid} cell door {k+1}', pivot, (front+.06*r, y, fz),
                              (.05*r, pitch-.06*r, fh-.18*r), kit.steel, .04, share=True))
            parts.append(_box(kit, f'Weapon {sid} cell cover {k+1}', pivot, (cover_x, y, zt+.03*r),
                              (cover_len, pitch-.07*r, .06*r), kit.orange, .04, share=True))
        # The director on the starboard flank of the box (the sprite's red-tipped mast).
        parts.append(_box(kit, f'Weapon {sid} director', pivot, (.45*r, -(half+.2*r), hz+.1*r), (.26*r, .16*r, .26*r),
                          kit.steel, .05))
        parts.append(_cyl(kit, f'Weapon {sid} director mast', pivot, (.45*r, -(half+.2*r), hz+.33*r), .035*r, .26*r,
                          kit.steel, axis='Z', vertices=6, bevel=0))
    else:
        parts.append(_box(kit, f'Weapon {sid} cell door 1', pivot, (front+.06*r, 0, fz),
                          (.05*r, .62*r, fh-.16*r), kit.steel, .05))
        parts.append(_cyl(kit, f'Weapon {sid} cell door ring', pivot, (front+.1*r, 0, fz), .2*r, .04*r,
                          kit.dark, vertices=14, bevel=0))
        for k, s in ((1, 1), (2, -1)):
            side = 'port' if s > 0 else 'starboard'
            # The sprite's fork: guide rails reaching forward either side of the cell door.
            parts.append(_box(kit, f'Weapon {sid} launcher rail {side}', pivot, (front+.2*r, s*.4*r, fz+.04*r),
                              (.46*r, .1*r, .14*r), kit.steel, .03, share=True))
            parts.append(_box(kit, f'Weapon {sid} cell cover {k}', pivot, (cover_x, s*.24*r, zt+.03*r),
                              (cover_len, .4*r, .06*r), kit.orange, .04, share=True))
    parts.append(_box(kit, f'Weapon {sid} magazine hatch', pivot, (rear-.02*r, 0, hz),
                      (.05*r, .6*r, height*.6), kit.steel, .04))
    return parts


def _trainable(kit, slot, pivot, r):
    if slot['type'] == 'MISSILE':
        return _launcher(kit, slot, pivot, r)
    return _gun(kit, slot, pivot, r)


def _barbette(kit, name, x, y, seat, lift, r):
    """Fixed ring: foot flange sunk into the deck, wall, chamfered lip at the pivot.
    A tall barbette steps in once halfway up: a lit ledge on an otherwise plain drum."""
    lip = min(.35, lift*.25)
    prof = [(1.08*r, -2), (1.08*r, .3), (r, .45)]
    if lift >= 3:
        m = .3+(lift-.3)*.45
        prof += [(r, m), (.955*r, m)]
        r = .955*r
    prof += [(r, lift-lip), (r-lip, lift)]
    ob = _lathe(kit, name, prof, 28, kit.steel, bevel=.1, phase=math.pi/28)
    ob.location = (x, y, seat)
    return ob


# --- clash checking ---------------------------------------------------------

def _tree(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in me.vertices]
    t = BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    lo = Vector(map(min, *pts)) if len(pts) > 1 else pts[0]
    hi = Vector(map(max, *pts)) if len(pts) > 1 else pts[0]
    return t, lo, hi


def _clashes(parts, pivot, angle, others):
    pivot.rotation_euler = (0, 0, math.radians(angle))
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    hits = set()
    for p in parts:
        t, lo, hi = _tree(p, dg)
        for name, (o, olo, ohi) in others.items():
            if all(lo[i] <= ohi[i] and olo[i] <= hi[i] for i in range(3)) and t.overlap(o):
                hits.add(name)
    return hits


def build(kit):
    cache = {}
    # The keyed pair goes last, so its training test also sees the small mounts beside it
    # (WS 007 laid abeam passes its muzzles over WS 021).
    for slot in sorted(SLOTS, key=lambda s: s['id'] == KEYABLE or s['id'] in MIRRORED):
        sid = slot['id']
        x, y = slot['location']
        r = RING[slot['size']]
        # Seat on the highest surface under the ring, not only at its centre: the
        # sprite puts several small mounts on the orange belts, and seating them
        # on the deck beside a belt made the clash check lift them 5-7 units.
        hits = [kit.surface(x+.6*r*math.cos(k*math.pi/4), y+.6*r*math.sin(k*math.pi/4), top=120) for k in range(8)]
        hits = [h for h in hits+[kit.surface(x, y, top=120)] if h]
        seat = max(h[0].z for h in hits) if hits else 10.0
        lift = .22*r
        tests = [slot['angle']] + (list(KEY_TEST_ANGLES) if sid == KEYABLE else
                                   [-a for a in KEY_TEST_ANGLES] if sid in MIRRORED else [])
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        mine = f'Weapon {sid} '
        for ob in bpy.context.scene.objects:   # every other solid: hull, other modules, earlier turrets
            if ob.type == 'MESH' and not ob.name.startswith(mine) and ob.name not in cache:
                cache[ob.name] = _tree(ob, dg)
        reach = 3.2*r
        others = {n: v for n, v in cache.items() if not n.startswith(mine)
                  and v[1].x <= x+reach and v[2].x >= x-reach and v[1].y <= y+reach and v[2].y >= y-reach}
        for _ in range(10):
            barbette = _barbette(kit, f'Weapon {sid} barbette', x, y, seat, lift, r)
            pivot = kit.empty(f'Weapon {sid} pivot', (x, y, seat+lift), slot['angle'])
            parts = _trainable(kit, slot, pivot, r)
            bad = {a: h for a in tests if (h := _clashes(parts, pivot, a, others))}
            pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
            if not bad:
                break
            print(f'turret {sid}: lift {lift:.2f} clashes {bad}')
            for ob in [barbette, pivot] + parts:   # clash: lift the barbette, never move the slot
                bpy.data.objects.remove(ob, do_unlink=True)
            lift += 1.5
        # Arc sweep (report only): which trained angles would put the turret into something.
        a0 = slot['angle']-slot['arc']/2
        sweep = [a0+slot['arc']*i/16 for i in range(17)]
        ARC_REPORT[sid] = {'lift': round(lift, 2), 'tests': tests,
                           'clash_angles': {round(a): sorted(h) for a in sweep if (h := _clashes(parts, pivot, a, others))}}
        pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
        bpy.context.view_layer.update()
        PIVOT_Z[sid] = seat+lift
        kit.hull.append(barbette)
        print(f"turret {sid}: seat {seat:.2f} lift {lift:.2f} parts {len(parts)} "
              f"arc clashes {ARC_REPORT[sid]['clash_angles']}")


def records():
    return [dict({k: v for k, v in s.items() if k != 'location'}, location=[*s['location'], round(PIVOT_Z[s['id']], 2)],
                 mount='TURRET', ring_radius=RING[s['size']], pivot=f"Weapon {s['id']} pivot",
                 objects=f"Weapon {s['id']} ...", trains='rotate the pivot about its local Z; rest is the slot angle',
                 keyable_for_c2=s['id'] == KEYABLE)
            for s in SLOTS]
