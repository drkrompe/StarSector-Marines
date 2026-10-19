"""Kite (P) weapon slots, exactly as kite.ship declares them, fitted as the
vanilla kite_pirates_Raider variant arms them.

WS 001  HARDPOINT  SMALL MISSILE    at (6, 22),  angle 0, arc 6    Hammer torpedo
WS 002  HARDPOINT  SMALL MISSILE    at (6, -22), angle 0, arc 6    Swarmer SRM
WS 003  TURRET     SMALL BALLISTIC  at (-7, 0),  angle 0, arc 200  light dual machine gun

The hardpoints sit in the slot between each missile sponson and the
fuselage (the sprite's gap at Y 20..23, open forward of X 10.5) and fire
forward through it. Both are pirate add-ons on a civilian hull: bolted to a
steel sled on the wing deck, strapped across into the sponson's inner wall,
welded where they meet, and not from the same yard.

- Hammer (port): torpedo_mount_2x_lowtech, two tubes side by side
  (hammer.wpn hardpointOffsets y -4/+4, ALTERNATING; the slot is too narrow
  for the game's spacing, so the tubes sit hard together). A long gunmetal
  breech housing, a hinged loading hatch on top, two round breech doors
  aft, amber hazard bars as on the sprite, a blast shield standing between
  the tubes and the cabin, a scorch plate under the mouths. Torpedoes are
  drawn loaded (RENDER_LOADED_MISSILES): the warheads show in the bores.
- Swarmer (starboard): the red SRM box of swarmer_srm_launcher with its four
  cells (swarmer.wpn offsets, four barrels) as a 2x2 block, a white hazard
  triangle as on the sprite, on a grey salvaged magazine with a rear loading
  door, a blast deflector under the cells and a cable run into the hull.

Muzzle anchors 'Weapon WS 00x muzzle anchor k' sit at each tube mouth, local
+Z along the line of fire (+X).

The turret stands on the aft hump inside the sprite's dark ring (radius
about 5): a barbette and the shared turret kit's light machine gun, twinned
here (the kit has no Light Dual Machine Gun, and its lightmg builder ignores
barrels=): the gun group is duplicated either side of the mount's centre at
lightdualmg.wpn's turretOffsets (y -2/+2 sprite px), the two receivers
bridged by a welded saddle and top strap, on a raised pedestal so both
barrels clear the frame. Only the parts above the traverse gap train, on
the empty 'Weapon WS 003 pivot', which rests at the slot angle.
"""
import math
import sys
from pathlib import Path
import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from kp_kit import bx, blocks, boolean, material, parent_to

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 001', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, 22], 'angle': 0,
     'arc': 6, 'weapon': 'hammer'},
    {'id': 'WS 002', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, -22], 'angle': 0,
     'arc': 6, 'weapon': 'swarmer'},
    {'id': 'WS 003', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [-7, 0], 'angle': 0,
     'arc': 200, 'weapon': 'lightdualmg'},
]
RING = 4.2            # turret ring radius, units (sprite ring about 5 with its coaming)
LIFT = 1.2            # barbette drum above the hump deck
MPU = .80             # the hull's metres_per_unit (build_kite.METRES_PER_UNIT): the kit sizes its fittings from it
FACE = 10.2           # launcher face, X: just inside the mouth of the sprite's gap (open from X 10.5)
TWIN = 2.0            # lightdualmg.wpn turretOffsets y, sprite px either side of the mount's centre
GUN_RAISE = .8        # the twin cradle rides this many sprite px higher than the single gun
CLASH_ANGLES = (0, 100, -100)


def _mats(kit):
    if not hasattr(kit, 'hazard'):
        kit.hazard = material('Launcher hazard amber', (.72, .46, .07), .1, .6)
        kit.srm_red = material('Swarmer launcher red', (.38, .06, .045), .15, .55)
        kit.marking = material('Launcher marking white', (.78, .76, .70), 0, .6)
        kit.scorch = material('Launcher scorch plate', (.09, .085, .08), .4, .7)
    return kit


def _seat(kit, x0, x1, y0, y1):
    """(lowest, highest) deck under a footprint: the launcher stands on the
    highest, so no deck shows inside a bore, and its sled runs down to the
    lowest, so it never floats."""
    zs = []
    for x in (x0, (x0+x1)/2, x1):
        for y in (y0, (y0+y1)/2, y1):
            hit = kit.surface_any(x, y)
            if hit:
                zs.append(hit[0].z)
    return (min(zs), max(zs)) if zs else (.8, .8)


def _tube(kit, name, x0, x1, y, z, r, bore, mat, floor_depth):
    """A launch tube along X with a real bore: outer shell minus the bore to
    floor_depth behind the mouth, a dark floor at the bottom."""
    t = kit.cylinder(name, ((x0+x1)/2, y, z), r, x1-x0, mat, 'X', 24, .04)
    cutter = kit.cylinder('Temporary bore', (x1-floor_depth/2+.3, y, z), bore, floor_depth+.6, mat, 'X', 24, 0)
    boolean(t, cutter, 'DIFFERENCE')
    kit.cylinder(name+' bore floor', (x1-floor_depth+.03, y, z), bore*1.02, .06, kit.dark, 'X', 24, 0)
    return t


def _warhead(kit, name, x_tip, y, z, r, length, mat):
    """A loaded round's nose, seated inside its bore."""
    bpy.ops.mesh.primitive_cone_add(vertices=20, radius1=r, radius2=r*.18, depth=length,
                                    location=(x_tip-length/2, y, z), rotation=(0, math.pi/2, 0))
    ob = bpy.context.object
    return kit.finish(ob, name, mat, .03)


def _anchor(name, loc, size):
    a = bpy.data.objects.new(name, None)
    a.empty_display_size = size
    a.location = loc
    a.rotation_euler = (0, math.pi/2, 0)     # local +Z along +X, the line of fire
    bpy.context.collection.objects.link(a)
    return a.name


def _plate(kit, name, quad_lo, quad_hi, mat, bevel=.04):
    """A plate solid between two quads (four points each, the same winding)."""
    verts = list(quad_lo)+list(quad_hi)
    faces = [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    ob = kit.mesh(name, verts, faces, mat, bevel)
    # Winding may be either way round from the callers; let Blender sort the normals out.
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    return ob


def _welds(x0, x1, y, z, axis='X', step=.32, size=.13):
    """A weld bead as a row of small lumps along a seam."""
    out = []
    a, b = (x0, x1)
    n = max(2, int(abs(b-a)/step))
    for i in range(n+1):
        t = a+(b-a)*i/n
        if axis == 'X':
            out.append((t-size*.6, t+size*.6, y-size/2, y+size/2, z-size/2, z+size/2))
        elif axis == 'Y':
            out.append((y-size/2, y+size/2, t-size*.6, t+size*.6, z-size/2, z+size/2))
        else:
            out.append((y[0]-size/2, y[0]+size/2, y[1]-size/2, y[1]+size/2, t-size*.6, t+size*.6))
    return out


def _bolts_x(x0, x1, y, z, n, h=.12, w=.16):
    """Bolt heads in a row along X on a top face (z) at y."""
    return [(x0+(x1-x0)*i/(n-1)-w/2, x0+(x1-x0)*i/(n-1)+w/2, y-w/2, y+w/2, z, z+h) for i in range(n)]


def _cable(kit, sid, x, y, s, z):
    """A firing cable out of the launcher's inboard flank through a gland,
    over the gap and down into the hull through a deck gland."""
    yd = y-s*3.0
    hit = kit.surface_any(x, yd)
    zd = hit[0].z if hit else z
    kit.cylinder(f'Weapon {sid} cable gland', (x, y-s*2.05, z), .34, .3, kit.steel, 'Y', 12, .03)
    kit.cylinder(f'Weapon {sid} firing cable', (x, y-s*2.55, z), .2, 1.0, kit.dark, 'Y', 10, 0)
    kit.cylinder(f'Weapon {sid} firing cable elbow', (x, yd+s*.05, z), .26, .55, kit.dark, 'Y', 10, 0)
    kit.cylinder(f'Weapon {sid} firing cable drop', (x, yd, (z+zd)/2), .2, max(z-zd, .2)+.2, kit.dark, 'Z', 10, 0)
    kit.cylinder(f'Weapon {sid} deck gland', (x, yd, zd+.06), .45, .3, kit.steel, 'Z', 12, .04)


def _mount_common(kit, sid, y, s, z, zlo, x0, x1, top):
    """What both pirate mounts share: the steel sled on the wing deck, an
    I-beam rail along its inboard foot, three straps across into the
    sponson's inner wall with their gussets and bolts, weld beads."""
    def Y(a, b):
        return sorted((y+s*a, y+s*b))
    # Sled: a heavy plate bedded into the wing, wider than the launcher, its
    # outboard edge run into the sponson.
    bx(kit, f'Weapon {sid} sled', x0-.5, x1-.2, *Y(-2.35, 2.9), min(z-.5, zlo-.3), z+.35, kit.steel, .08)
    # Inboard I-beam rail on the sled, its flanges proud of the web.
    rail = [(x0-.4, x1-.3, *Y(-2.35, -2.0), z+.35, z+.95)]
    rail += [(x0-.4, x1-.3, *Y(-2.5, -1.9), z+.95, z+1.1), (x0-.4, x1-.3, *Y(-2.5, -1.9), z+.35, z+.48)]
    blocks(kit, f'Weapon {sid} mounting rail', rail, kit.steel, .02)
    # Straps across the top into the sponson wall: flat bar, a gusset under
    # each where it meets the sponson, two bolts a strap on the launcher.
    straps, gussets, bolts = [], [], []
    for xs in (x0+.9, (x0+x1)/2, x1-1.2):
        straps.append((xs-.3, xs+.3, *Y(.6, 3.6), top+.0, top+.22))
        straps.append((xs-.3, xs+.3, *Y(1.9, 2.15), z+.6, top+.22))           # drop down the launcher flank
        gussets.append((xs-.08, xs+.08, *Y(2.0, 2.9), top-1.0, top))
        for yb in (1.1, 3.1):
            bolts.append((xs-.09, xs+.09, *Y(yb-.09, yb+.09), top+.22, top+.32))
    blocks(kit, f'Weapon {sid} sponson straps', straps, kit.steel, .03)
    blocks(kit, f'Weapon {sid} strap gussets', gussets, kit.steel, 0)
    bolts += _bolts_x(x0, x1-.6, y+s*2.55, z+.35, 7)
    bolts += _bolts_x(x0, x1-.6, y-s*2.2, z+1.1, 6, .1, .14)
    blocks(kit, f'Weapon {sid} bolts', bolts, kit.dark, 0)
    welds = _welds(x0-.4, x1-.3, y-s*1.98, z+.4)                   # rail web to sled
    welds += _welds(x0, x1-.4, y+s*1.98, z+.4)                     # housing foot to sled, outboard
    blocks(kit, f'Weapon {sid} weld beads', welds, kit.steel, 0)


def _hammer(kit, slot):
    """Port: the Hammer's two-tube torpedo mount in a long breech housing."""
    sid, (x, y) = slot['id'], slot['location']
    s = 1 if y > 0 else -1

    def Y(a, b):
        return sorted((y+s*a, y+s*b))
    x0, x1 = 3.0, FACE
    zlo, z = _seat(kit, x0, x1, *Y(-1.8, 1.8))
    zc = z+1.75                      # tube axis
    top = z+3.2
    _mount_common(kit, sid, y, s, z, zlo, x0, x1, top)
    # Breech housing: a long chamfered box, its front corners cut back so the
    # tubes stand proud of it.
    house = bx(kit, f'Weapon {sid} breech housing', x0, x1-.9, *Y(-1.95, 1.95), z+.35, top, kit.steel, .14)
    # Tubes: two shells side by side, bored to the floor with the warhead seated.
    anchors = []
    for k, dy in enumerate((-.97, .97)):
        ty = y+s*dy
        _tube(kit, f'Weapon {sid} tube {k+1}', x1-2.4, x1+.55, ty, zc, .93, .7, kit.steel, 1.6)
        collar = kit.cylinder(f'Weapon {sid} tube {k+1} muzzle collar', (x1+.4, ty, zc), 1.02, .3, kit.steel, 'X',
                              24, .03)
        boolean(collar, kit.cylinder('Temporary collar bore', (x1+.4, ty, zc), .7, .6, kit.steel, 'X', 24, 0),
                'DIFFERENCE')
        band = kit.cylinder(f'Weapon {sid} tube {k+1} collar band', (x1-.6, ty, zc), .98, .22, kit.hazard, 'X', 24, .02)
        boolean(band, kit.cylinder('Temporary band bore', (x1-.6, ty, zc), .8, .5, kit.steel, 'X', 24, 0), 'DIFFERENCE')
        _warhead(kit, f'Weapon {sid} torpedo {k+1} warhead', x1-.25, ty, zc, .6, 1.0, kit.cream)
        anchors.append(_anchor(f'Weapon {sid} muzzle anchor {k+1}', (x1+.6, ty, zc), .7))
    # Saddle between the tubes and the housing face.
    bx(kit, f'Weapon {sid} tube saddle', x1-1.0, x1-.4, *Y(-1.9, 1.9), z+.35, zc+.15, kit.steel, .05)
    # Loading hatch on top: a raised hinged plate, hinge aft, dogs and a handle.
    hx0, hx1 = x0+1.0, x0+3.6
    bx(kit, f'Weapon {sid} loading hatch', hx0, hx1, *Y(-1.45, 1.45), top, top+.2, kit.panel, .05)
    kit.cylinder(f'Weapon {sid} loading hatch hinge', (hx0-.12, y, top+.14), .14, 2.6, kit.steel, 'Y', 12, 0)
    dogs = [(hx1-.05, hx1+.2, *Y(dy-.15, dy+.15), top+.05, top+.25) for dy in (-1.0, 0, 1.0)]
    dogs += [(hx1-.6, hx1-.45, *Y(-.5, .5), top+.2, top+.45)]                  # handle
    blocks(kit, f'Weapon {sid} hatch dogs', dogs, kit.steel, .02)
    # Breech doors aft, one per tube, with hazard bars as on the sprite.
    for k, dy in enumerate((-.97, .97)):
        kit.cylinder(f'Weapon {sid} breech door {k+1}', (x0-.12, y+s*dy, zc), .78, .26, kit.panel, 'X', 20, .04)
        kit.cylinder(f'Weapon {sid} breech door {k+1} lock', (x0-.3, y+s*dy, zc), .2, .2, kit.steel, 'X', 8, 0)
    bars = [(x0+.4+.75*i, x0+.4+.75*i+.38, *Y(1.96, 2.0), z+.6, z+1.1) for i in range(4)]
    bars += [(x0+.4+.75*i, x0+.4+.75*i+.38, *Y(-2.0, -1.96), top-.7, top-.2) for i in range(5)]
    blocks(kit, f'Weapon {sid} hazard bars', bars, kit.hazard, 0)
    # Blast shield between the tubes and the cabin: a plate leaning over the
    # shoulder, braced to the sled.
    yi = y-s*2.15
    lo = [(x1-4.2, yi, z+.4), (x1+.2, yi, z+.4), (x1+.2, yi-s*.25, z+.4), (x1-4.2, yi-s*.25, z+.4)]
    hi = [(x1-3.6, yi-s*.55, top+.6), (x1+.2, yi-s*.55, top+.6), (x1+.2, yi-s*.8, top+.6),
          (x1-3.6, yi-s*.8, top+.6)]
    _plate(kit, f'Weapon {sid} blast shield', lo, hi, kit.steel)
    braces = [(xb-.1, xb+.1, *Y(-2.2, -1.95), z+.4, top-.3) for xb in (x1-3.0, x1-1.0)]
    blocks(kit, f'Weapon {sid} blast shield braces', braces, kit.steel, 0)
    # Scorch plate under the mouths, laid on the wing, and a mismatched
    # patch welded over the housing's inboard flank.
    bx(kit, f'Weapon {sid} scorch plate', x1-1.0, x1+.75, *Y(-1.9, 1.9), z-.1, z+.38, kit.scorch, .04)
    patch = bx(kit, f'Weapon {sid} patch plate', x0+3.8, x0+5.6, *Y(-2.04, -1.92), z+.9, top-.45, kit.cream, .03)
    patch.rotation_euler = (s*math.radians(4), 0, 0)
    blocks(kit, f'Weapon {sid} patch welds',
           _welds(x0+3.8, x0+5.6, y-s*2.0, top-.45)+_welds(x0+3.8, x0+5.6, y-s*2.0, z+.9), kit.steel, 0)
    # Feed and firing cable from the breech into the hull shoulder.
    _cable(kit, sid, x0+.5, y, s, z+1.4)
    slot['muzzles'] = anchors
    return house


def _swarmer(kit, slot):
    """Starboard: the Swarmer's red four-cell SRM box on a salvaged magazine."""
    sid, (x, y) = slot['id'], slot['location']
    s = 1 if y > 0 else -1

    def Y(a, b):
        return sorted((y+s*a, y+s*b))
    x0, x1 = 3.0, FACE
    zlo, z = _seat(kit, x0, x1, *Y(-1.8, 1.8))
    top = z+3.5
    _mount_common(kit, sid, y, s, z, zlo, x0, x1, top)
    # Magazine: a grey salvaged box aft, lower than the launcher, a rear door.
    xm = x1-4.0
    bx(kit, f'Weapon {sid} magazine', x0, xm+.1, *Y(-1.8, 1.8), z+.35, top-.6, kit.panel, .12)
    bx(kit, f'Weapon {sid} magazine rear door', x0-.15, x0+.02, *Y(-1.4, 1.4), z+.7, top-.95, kit.steel, .04)
    kit.cylinder(f'Weapon {sid} rear door hinge', (x0-.12, y+s*1.5, (z+top)/2-.1), .12, top-z-1.7,
                 kit.steel, 'Z', 10, 0)
    handles = [(x0-.4, x0-.15, *Y(-.9, -.75), z+1.4, z+2.2), (x0-.4, x0-.15, *Y(-.9, .2), z+1.4, z+1.55),
               (x0-.4, x0-.15, *Y(-.9, .2), z+2.05, z+2.2)]
    blocks(kit, f'Weapon {sid} rear door handle', handles, kit.steel, 0)
    # The launcher box, red as the sprite, with its four cells bored through
    # the face: a 2x2 block (the slot is too narrow for the sprite's row).
    box = bx(kit, f'Weapon {sid} launcher box', xm, x1, *Y(-1.95, 1.95), z+.35, top, kit.srm_red, .14)
    zc = (z+.35+top)/2
    cells = [(dy, dz) for dz in (.82, -.82) for dy in (-.92, .92)]
    for dy, dz in cells:
        cutter = kit.cylinder('Temporary cell', (x1-.9, y+s*dy, zc+dz), .62, 2.0, kit.dark, 'X', 20, 0)
        boolean(box, cutter, 'DIFFERENCE')
    anchors = []
    for k, (dy, dz) in enumerate(cells):
        cy, cz = y+s*dy, zc+dz
        kit.cylinder(f'Weapon {sid} cell {k+1} floor', (x1-1.86, cy, cz), .63, .06, kit.dark, 'X', 20, 0)
        liner = kit.cylinder(f'Weapon {sid} cell {k+1} liner', (x1-.98, cy, cz), .615, 1.8, kit.dark, 'X', 20, 0)
        boolean(liner, kit.cylinder('Temporary liner bore', (x1-.9, cy, cz), .55, 2.2, kit.dark, 'X', 20, 0),
                'DIFFERENCE')
        _warhead(kit, f'Weapon {sid} missile {k+1} nose', x1-.45, cy, cz, .44, .8, kit.marking)
        anchors.append(_anchor(f'Weapon {sid} muzzle anchor {k+1}', (x1+.12, cy, cz), .45))
    # Face frame between the cells, bolted on, and a lifting lug each side.
    frame = [(x1-.02, x1+.1, *Y(-.12, .12), z+.45, top-.1), (x1-.02, x1+.1, *Y(-1.85, 1.85), zc-.11, zc+.11)]
    frame += [(xm+1.0, xm+1.6, *Y(-.25, .25), top, top+.5)]
    blocks(kit, f'Weapon {sid} face frame', frame, kit.steel, .02)
    # Hazard triangle on the box's roof, as on the sprite.
    tx, tw = xm+2.6, 1.1
    tri = [(tx+tw, y, top+.01), (tx-tw*.7, y+s*1.1, top+.01), (tx-tw*.7, y-s*1.1, top+.01)]
    kit.mesh(f'Weapon {sid} hazard triangle', tri+[(px, py, pz+.05) for px, py, pz in tri],
             [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)], kit.marking)
    # Blast deflector under the cells: a plate sloping down and forward over
    # the wing so the exhaust kicks up rather than into the deck.
    yl, yr = y-s*1.95, y+s*1.95
    lo = [(x1-.6, yl, z+.1), (x1+.75, yl, z-.15), (x1+.75, yr, z-.15), (x1-.6, yr, z+.1)]
    hi = [(x1-.6, yl, z+.4), (x1+.75, yl, z+.12), (x1+.75, yr, z+.12), (x1-.6, yr, z+.4)]
    _plate(kit, f'Weapon {sid} blast deflector', lo, hi, kit.scorch)
    # Mismatch: a band of bare steel where the red box was cut and re-welded
    # onto the magazine, and a cable run into the hull.
    bx(kit, f'Weapon {sid} adapter band', xm-.15, xm+.25, *Y(-2.02, 2.02), z+.35, top+.07, kit.steel, .03)
    blocks(kit, f'Weapon {sid} adapter welds', _welds(z+.5, top-.1, (xm-.17, y-s*2.02), 0, axis='Z')
           + _welds(z+.5, top-.7, (x0+.1, y+s*1.82), 0, axis='Z'), kit.steel, 0)
    _cable(kit, sid, x0+1.6, y, s, z+1.3)
    slot['muzzles'] = anchors
    return box


def _twin(kit, t, prefix, k, mats):
    """Make the kit's single light MG a twin: shift its gun group to one side,
    duplicate it on the other at lightdualmg's offsets, bridge the two."""
    elev = t['elevation']
    off = TWIN*k
    raise_ = GUN_RAISE*k
    elev.location.z += raise_
    children = [c for c in elev.children]
    anchor = next(c for c in children if c.name.endswith('muzzle anchor 1'))
    for c in children:
        c.location.y -= off
    for c in children:
        if c is anchor:
            a2 = c.copy()
            a2.name = f'{prefix} muzzle anchor 2'
            bpy.context.collection.objects.link(a2)
            a2.parent = elev
            a2.location.y += 2*off
            continue
        dup = c.copy()
        if c.type == 'MESH':
            dup.name = c.name.replace(f'{prefix} gun', f'{prefix} gun 2')
        else:
            dup.name = c.name.replace('recoil 1', 'recoil 2')
        bpy.context.collection.objects.link(dup)
        dup.parent = elev
        dup.location.y += 2*off
        for g in c.children:
            gd = g.copy()
            gd.name = g.name.replace('recoil 1', 'recoil 2')
            bpy.context.collection.objects.link(gd)
            gd.parent = dup
    bpy.context.view_layer.update()
    # Saddle under both pintles and a strap over both receivers, welded: in
    # elevation-empty space (its origin is the trunnion: x 1.0k, z za).
    w = elev.matrix_world

    def at(lx, ly, lz):
        return w @ Vector((lx, ly, lz))
    parts = []
    c = at(-2.0*k, 0, -1.65*k)
    parts.append(kit.box(f'{prefix} twin saddle', c, (2.4*k, 2*off+1.8*k, .5*k), mats['steel'], .06))
    c = at(-2.4*k, 0, 1.0*k)
    parts.append(kit.box(f'{prefix} twin top strap', c, (1.0*k, 2*off+2.4*k, .2*k), mats['steel'], .04))
    c = at(.6*k, 0, -.15*k)
    parts.append(kit.box(f'{prefix} twin spacer', c, (2.6*k, 2*off-2.0*k, 1.0*k), mats['dark'], .03))
    welds = []
    for sy in (1, -1):
        p = at(-2.4*k, sy*(off+1.3*k), .95*k)
        welds += [(p.x-.4*k, p.x+.4*k, p.y-.1, p.y+.1, p.z-.12, p.z+.05)]
    parts.append(blocks(kit, f'{prefix} twin welds', welds, mats['steel'], 0))
    parent_to(parts, elev)
    # Pedestal on the house deck under the raised saddle (trains, does not elevate).
    pivot = elev.parent
    pw = pivot.matrix_world
    z_deck = tk.GAP*RING*.8+.6*k
    z_top = (elev.location.z-1.9*k)
    ped = kit.cylinder(f'{prefix} twin pedestal', pw @ Vector((-2.0*k, 0, (z_deck+z_top)/2)), .75*k,
                       z_top-z_deck, mats['steel'], 'Z', 16, .03)
    parent_to([ped], pivot)


def _turret(kit, slot):
    sid, (x, y) = slot['id'], slot['location']
    hit = kit.surface_any(x, y)
    seat = hit[0].z if hit else 7.5
    mats = {'armour': kit.cream, 'plate': kit.panel, 'steel': kit.steel, 'dark': kit.dark}
    tk.barbette(f'Weapon {sid} barbette', RING, LIFT, mats, MPU, location=(x, y, seat))
    pivot = bpy.data.objects.new(f'Weapon {sid} pivot', None)
    pivot.empty_display_type = 'ARROWS'
    pivot.location = (x, y, seat + LIFT)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'], pivot['arc_deg'], pivot['rest_angle_deg'] = sid, slot['arc'], slot['angle']
    bpy.context.collection.objects.link(pivot)
    ring = RING*.8
    t = tk.build('lightmg', f'Weapon {sid}', pivot, ring, mats, MPU)
    k = ring/tk.NOMINAL_RING['SMALL']
    _twin(kit, t, f'Weapon {sid}', k, mats)
    slot['muzzles'] = sorted(a.name for a in pivot.children_recursive
                             if a.type == 'EMPTY' and ' muzzle anchor ' in a.name)
    slot['pivot'] = pivot.name
    slot['seat_z'] = round(seat, 2)
    _clash_check(slot, pivot)


def _tree(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    vs = [ev.matrix_world @ v.co for v in me.vertices]
    tree = BVHTree.FromPolygons(vs, [p.vertices[:] for p in me.polygons])
    ev.to_mesh_clear()
    return tree


def _clash_check(slot, pivot):
    """BVH overlap of every trained part against everything that does not
    train (the barbette reported apart: the roller rides on its lip), at the
    rest angle and at +-100 degrees, the edges of the 200-degree arc."""
    trained = [o for o in pivot.children_recursive if o.type == 'MESH']
    tset = set(trained)
    fixed = [o for o in bpy.data.objects if o.type == 'MESH' and o not in tset]
    rest = pivot.rotation_euler.z
    fixed_trees = [(o.name, _tree(o)) for o in fixed]
    for a in CLASH_ANGLES:
        pivot.rotation_euler.z = rest+math.radians(a)
        bpy.context.view_layer.update()
        hits = []
        for o in trained:
            to = _tree(o)
            for n, tf in fixed_trees:
                if to.overlap(tf):
                    hits.append((o.name, n))
        bar = [h for h in hits if 'barbette' in h[1]]
        hull = [h for h in hits if 'barbette' not in h[1]]
        print(f'CLASH {slot["id"]} at {a:+d} deg: {len(hull)} against hull/other, {len(bar)} against barbette')
        for h in hull+bar:
            print('   ', h[0], 'x', h[1])
    pivot.rotation_euler.z = rest
    bpy.context.view_layer.update()


def build(kit):
    _mats(kit)
    for slot in SLOTS:
        if slot['weapon'] == 'hammer':
            _hammer(kit, slot)
        elif slot['weapon'] == 'swarmer':
            _swarmer(kit, slot)
        else:
            _turret(kit, slot)
