"""The command deck in detail: the bridge above, and the machinery under it.

Read off paragon.png at 7x, the white command core carries, bow to stern:
- a rounded nose with a dark sensor slot and crossbar (`Bridge nose sensor ...`);
- rows of lit windows along its flanks (pg_detail.deck_windows);
- a forward tier with longitudinal ribs and a circular boss on the centreline
  (`Bridge boss ...`, `Bridge rib ...`);
- the tallest tier, the bridge tower, with its vent cluster (`Bridge vent ...`),
  a wraparound glazing band on its vertical wall (`Bridge glazing`, with
  `Bridge mullion ...`), a comms mast and a sensor dish;
- chevron fins on the neck behind it (`Bridge chevron ...`) and a vent array on
  the spine aft (`Bridge spine vent ...`).

Under it (owner, pass 3: the "under bridge"), the deck's nose cantilevers over
the bore from the gantry's front (pg_hull.GANTRY_FRONT_X), raised above the
loop. Its underside carries a keel beam, transverse frames, pipe runs,
downlights and a glazed observation blister looking down into the bore
(`Under bridge ...`). The gantry's bulkhead facing the bore has ribs, an airlock
in a lit frame and vent grilles (`Gantry face ...`); its flanks carry diagonal
struts, a pipe bundle and machinery (`Gantry ...`), the sprite's dark band of
struts and pipes round the white.
"""
import math
import random
import bpy
from mathutils import Matrix, Vector
import pg_kit as K
import pg_hull
from pg_detail import _flank_point

SEED = 4517


def _half(x, scale=1.0, pad=0.0):
    """The core's half-width at X (pg_hull.CORE), scaled and padded."""
    pts = pg_hull.CORE
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if x1 <= x <= x0:
            return (y0+(y1-y0)*(x-x0)/(x1-x0))*scale+pad
    return 0.0


def _top(kit, x, y):
    hit = kit.surface(x, y)
    return hit[0].z if hit else None


def under_nose(kit):
    """The cantilevered nose's underside, over the bore."""
    z = pg_hull.DECK_Z
    x0, x1 = -23.0, pg_hull.GANTRY_FRONT_X
    made = [kit.box('Under bridge keel beam', ((x0+x1)/2, 0, z-.9), (x0-x1, 3.2, 1.8), kit.slate, .15)]
    for i, x in enumerate(range(-27, int(x1), -6), start=1):
        w = _half(x, .86)*2
        if w < 4:
            continue
        made.append(kit.box(f'Under bridge frame {i}', (x, 0, z-.7), (1.3, w, 1.4), kit.slate, .1))
        for side in (1, -1):
            made.append(kit.box(f'Under bridge downlight {i}.{"p" if side > 0 else "s"}', (x, side*(w/2-1.0), z-1.5),
                                (.6, .6, .2), kit.glow_white, 0))
    for side in (1, -1):
        y = side*7.5
        L = x0-x1-4
        made.append(kit.cylinder(f'Under bridge pipe {"port" if side > 0 else "starboard"}', ((x0+x1)/2-2, y, z-1.1),
                                 .55, L, kit.steel, axis='X', vertices=12, bevel=0))
    # The observation blister: a glazed dome hanging under the nose, looking into the bore.
    xb = -40.0
    made.append(K.revolve(kit, 'Under bridge blister collar', [(0, 0), (5.2, 0), (5.2, -.8), (0, -.8)], kit.pale,
                          center=(xb, 0, z), segments=40, smooth=False))
    dome = [(0, 0)]+[(4.4*math.cos(math.radians(a)), -.8-3.4*math.sin(math.radians(a))) for a in range(0, 91, 10)]
    made.append(K.revolve(kit, 'Under bridge blister glazing', dome+[(0, -4.2)], kit.glass, center=(xb, 0, z),
                          segments=40, smooth=40))
    return made


def gantry_face(kit, parts):
    """The gantry's bulkhead facing the bore, standing on the lining's flat top up to the deck's underside.

    Full-height ribs (grounded on the lining, carrying the deck), plating panels
    between them, an observation gallery's window band looking into the bore, and
    an airlock at the foot."""
    x = pg_hull.GANTRY_FRONT_X
    w = _half(x, 1.0, pg_hull.TRENCH_PAD)
    z0, z1 = pg_hull.LINING_TOP, pg_hull.DECK_Z
    zm, hgt = (z0+z1)/2, z1-z0
    made = []
    ys = [-w+1.5+k*(2*w-3)/8 for k in range(9)]
    for i, y in enumerate(ys, start=1):
        made.append(kit.box(f'Gantry face rib {i}', (x+.6, y, zm), (1.2, 1.0, hgt), kit.slate, .08))
    for i, (ya, yb) in enumerate(zip(ys, ys[1:]), start=1):
        made.append(kit.box(f'Gantry face panel {i}', (x+.2, (ya+yb)/2, zm+1.0), (.4, yb-ya-1.2, hgt-2.5),
                            kit.bore_mid, .05))
    # The observation gallery: a window band across the bulkhead, framed.
    zg, hg = z1-5.5, 2.2
    made.append(kit.box('Gantry face gallery frame', (x+.45, 0, zg), (.3, 2*w-4, hg+.6), kit.tech, 0))
    made.append(kit.box('Gantry face gallery glazing', (x+.62, 0, zg), (.12, 2*w-4.6, hg), kit.glass, 0))
    # The airlock at the foot, on the lining.
    made.append(kit.box('Gantry face airlock frame', (x+.9, 0, z0+3.6), (1.0, 7.0, 7.2), kit.pale, .2))
    made.append(kit.box('Gantry face airlock door', (x+1.45, 0, z0+3.3), (.3, 5.0, 5.8), kit.slate, 0))
    made.append(kit.box('Gantry face airlock light', (x+1.6, 0, z0+6.6), (.2, 4.4, .35), kit.glow, 0))
    for side in (1, -1):
        for j in range(3):
            made.append(kit.box(f'Gantry face vent {"p" if side > 0 else "s"}{j+1}', (x+.55, side*(6.5+j*1.5), z0+3.0),
                                (.3, 1.0, 4.0), kit.tech, 0))
    return made


def gantry_flanks(kit, parts):
    """Diagonal struts, a pipe bundle and machinery boxes along the gantry's flanks."""
    rng = random.Random(SEED)
    ob = parts['trench']
    made = []
    z_lo, z_hi = 30.0, pg_hull.DECK_Z-1.0
    for side, label in ((1, 'port'), (-1, 'starboard')):
        pts = []
        for i, x in enumerate(range(int(pg_hull.GANTRY_FRONT_X)-4, int(pg_hull.DECK_AFT_X)+2, -7), start=1):
            hit = _flank_point(ob, x, side, (z_lo+z_hi)/2)
            if not hit:
                continue
            p, n = hit
            pts.append(p)
            yaw = math.atan2(n.y, n.x)
            # A strut leaning aft at 40 degrees from the deck's edge down to the ring.
            strut = kit.box(f'Gantry {label} strut {i}', (0, 0, 0), (1.3, 1.3, (z_hi-z_lo)/math.cos(math.radians(40))),
                            kit.slate, .1)
            strut.matrix_world = (Matrix.Translation(p+n*.7) @ Matrix.Rotation(yaw, 4, 'Z') @
                                  Matrix.Rotation(math.radians(-40), 4, 'Y'))
            made.append(strut)
            if rng.random() < .6:
                box = kit.box(f'Gantry {label} unit {i}', (0, 0, 0), (1.6, 3.2, 2.0+rng.random()*1.5), kit.bore_mid, .12)
                box.matrix_world = Matrix.Translation(p+n*1.0+Vector((-3.5, 0, -2.0))) @ Matrix.Rotation(yaw, 4, 'Z')
                made.append(box)
        if len(pts) > 1:
            a, b = pts[0], pts[-1]
            mid = (a+b)/2
            for j, dz in enumerate((-4.5, -2.6, -.7), start=1):
                made.append(kit.cylinder(f'Gantry {label} pipe {j}', (mid.x, mid.y+side*1.7, (z_lo+z_hi)/2+dz),
                                         .55 if j != 2 else .8, (a-b).length+6, kit.steel if j != 2 else kit.slate,
                                         axis='X', vertices=12, bevel=0))
    return made


def bridge(kit, parts):
    made = []
    z = pg_hull.DECK_Z
    # Nose sensor slot and crossbar.
    zt = _top(kit, -27.0, 0.0)
    if zt is not None:
        made.append(kit.box('Bridge nose sensor slot', (-27.5, 0, zt+.1), (7.0, 2.6, .5), kit.tech, 0))
        made.append(kit.box('Bridge nose sensor bar', (-29.0, 0, zt+.35), (1.0, 7.5, .5), kit.slate, .1))
        for j, y in enumerate((-0.7, 0.7)):
            made.append(kit.box(f'Bridge nose sensor lens {j+1}', (-26.0, y, zt+.4), (.6, .6, .3), kit.glow_white, 0))
    # Longitudinal ribs on the forward tier.
    for side in (1, -1):
        for j, y in enumerate((5.5, 10.0)):
            for x0, x1 in ((-31, -45),):
                zr = _top(kit, (x0+x1)/2, side*y)
                if zr is not None:
                    made.append(kit.box(f'Bridge rib {"p" if side > 0 else "s"}{j+1}', ((x0+x1)/2, side*y, zr+.3),
                                        (abs(x1-x0), .8, .7), kit.pale, .1))
    # The centreline boss on the forward tier.
    zb = _top(kit, -57.0, 0.0)
    if zb is not None:
        made.append(K.revolve(kit, 'Bridge boss ring', [(2.6, 0), (4.0, 0), (3.8, .9), (2.6, .9)], kit.pale,
                              center=(-57.0, 0, zb-.2), segments=32, smooth=False))
        made.append(K.revolve(kit, 'Bridge boss dome', [(0, 0), (2.5, 0), (2.2, .9), (1.2, 1.5), (0, 1.7)], kit.slate,
                              center=(-57.0, 0, zb-.2), segments=24, smooth=40))
    # Bridge tower: wraparound glazing on its flanks, vent cluster, mast and dish.
    # The bridge glazing: one band round the tower's vertical wall, with dark mullions.
    tower_plan = K.ccw([(-46, 0.0), (-48, 7), (-54, 10), (-80, 10), (-86, 6), (-86, 0.0)] +
                       [(x, -y) for x, y in reversed([(-46, 0.0), (-48, 7), (-54, 10), (-80, 10), (-86, 6), (-86, 0.0)])
                        if y > 0])
    band = K.inset(tower_plan, -.12)
    zg0, zg1 = z+19.8, z+19.8+2.1/kit.mpu   # on the tower's vertical wall, above the upper tier's crown
    n = len(band)
    verts = [(px, py, zg0) for px, py in band]+[(px, py, zg1) for px, py in band]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]+[(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    made.append(kit.mesh('Bridge glazing', verts, faces, kit.glass, 0, False))
    tower = parts['bridge']
    for side in (1, -1):
        for i, x in enumerate(range(-50, -84, -3), start=1):
            hit = _flank_point(tower, x, side, (zg0+zg1)/2)
            if hit:
                p, nrm = hit
                m = kit.box(f'Bridge mullion {"p" if side > 0 else "s"}{i}', tuple(p+nrm*.14), (.3, .2, zg1-zg0+.2),
                            kit.tech, 0)
                m.rotation_euler = (0, 0, math.atan2(nrm.y, nrm.x)+math.pi/2)
                made.append(m)
    for i, y in enumerate((-6, -3, 0, 3, 6), start=1):
        made.append(kit.box(f'Bridge mullion front {i}', (-46.2+abs(y)*-.3, y, (zg0+zg1)/2), (.2, .3, zg1-zg0+.2),
                            kit.tech, 0))
    zt = _top(kit, -66.0, 0.0)
    if zt is not None:
        for side in (1, -1):
            for j in range(3):
                made.append(kit.box(f'Bridge vent {"p" if side > 0 else "s"}{j+1}', (-63.0-j*2.2, side*4.6, zt+.1),
                                    (1.4, 2.4, .35), kit.tech, 0))
        made.append(kit.cylinder('Bridge comms mast', (-76.0, 0, zt+5.5), .35, 11.0, kit.steel, vertices=10, bevel=0))
        for k, (h, w) in enumerate(((9.0, 5.0), (6.5, 3.4)), start=1):
            made.append(kit.box(f'Bridge comms mast yard {k}', (-76.0, 0, zt+h), (.3, w, .3), kit.steel, 0))
        made.append(kit.box('Bridge comms mast light', (-76.0, 0, zt+11.2), (.5, .5, .5), kit.glow_white, 0))
        made.append(K.revolve(kit, 'Bridge sensor dish', [(0, 0), (2.8, .9), (2.6, 1.1), (0, .25)], kit.pale,
                              center=(-70.0, -4.5, zt+1.4), segments=28, smooth=40))
        made.append(kit.cylinder('Bridge sensor dish post', (-70.0, -4.5, zt+.7), .3, 1.4, kit.slate, vertices=8, bevel=0))
    # Chevron fins on the neck behind the tower.
    for side in (1, -1):
        zc = _top(kit, -90.0, side*7.0)
        if zc is not None:
            fin = kit.box(f'Bridge chevron {"port" if side > 0 else "starboard"}', (-90.0, side*7.0, zc+.6),
                          (11.0, 1.2, 1.4), kit.pale, .2)
            fin.rotation_euler = (0, 0, math.radians(-35*side))
            made.append(fin)
    # Vent array on the spine aft.
    for i in range(3):
        for side in (1, -1):
            x, y = -104.0-i*3.0, side*3.2
            zv = _top(kit, x, y)
            if zv is not None:
                made.append(kit.box(f'Bridge spine vent {i+1}{"p" if side > 0 else "s"}', (x, y, zv+.1), (2.0, 2.2, .35),
                                    kit.tech, 0))
    return made


def build(kit, parts):
    made = under_nose(kit)+gantry_face(kit, parts)+gantry_flanks(kit, parts)+bridge(kit, parts)
    kit.detail += made
    return made
