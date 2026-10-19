"""Hound (P): the pirate state of the Hound, built over the clean ship.

The vanilla Hound (P) is the skin hound_d_pirates (data/hulls/skins/
hound_d_pirates.skin): the same hull and slots as hound.ship, sprite
graphics/ships/hound/hound_pirates.png, "heavily modified - and poorly
maintained - by its pirate crew". Its outline is the Hegemony sprite's (8
alpha pixels differ); what changes is paint, patchy red broken by large dark
areas. The dark areas over formerly-orange armour are read here as crude
salvaged armour welded on, and the Overdriven variant's guns are fitted:

- ARMOUR: thick, irregular, slightly skewed slabs of scrap plate where the
  sprite is dark over what was orange (sprite col c, row r covers X 44-r..
  45-r, Y 30-c..31-c). Each is bedded on the hull by ray-cast (the deck ones
  through Kit.surface, the pod-side ones the same way along Y), so no Z is
  hard-coded; a later slab cast over an earlier one sits on it. Each slab is
  one object 'Pirate armour <area> <k>' with its own '... weld' beads round
  the edge it meets the hull with, '... bolts', and on two of them '... lugs';
  two have a cut-out showing the original plate.
- GUNS (hound_d_pirates_Overdriven): WS 001 a chaingun (six barrels round a
  spindle under the empty 'Weapon WS 001 rotor', local X the spin axis) on the
  clean gun's cradle, breech, mantlet and feed; WS 002 a twin light machine
  gun in the clean turret house. hd_mounts builds them through its gun hooks
  (GUNS), so the clean mounts' clash checks run on them too.
- SCARS: light battle wear, the SCARS table. Ids are stable (HD01...). Pocks
  and gouges are live Boolean modifiers 'Scar cuts' on the base object they
  hit (cutter 'Scar cutter - <object>', hidden, material transfer), as the
  Second Wind does; everything added is 'Scar HD<nn> <what>'.

Base objects are never renamed or deleted; the only base parts absent are the
two guns' barrel groups, which the pirate weapons replace.
"""
import math
import random
import bmesh
import bpy
from mathutils import Matrix, Vector
from hd_kit import material
from hd_fork import bolts

METRES_PER_UNIT = .88
M = lambda metres: metres/METRES_PER_UNIT


def srgb(*c):
    """Linear Principled colour from 0-255 sRGB."""
    f = lambda v: (v/255/12.92) if v/255 <= .04045 else ((v/255+.055)/1.055)**2.4
    return tuple(f(v) for v in c)


class Mats:
    def __init__(self, kit):
        self.armour = material('Pirate scrap armour', srgb(70, 58, 50), .45, .78)
        self.weld = kit.dark            # 'Recess and machinery'
        self.steel = kit.steel
        self.scorch = material('Scar scorch', (.012, .011, .010), .1, .38)
        self.bare = material('Scar bare metal', srgb(168, 166, 160), .9, .32)


# ---------------------------------------------------------------- casting

def _bounds(ob):
    bb = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector([min(v[i] for v in bb) for i in range(3)]), Vector([max(v[i] for v in bb) for i in range(3)])


SKIP = ('glow', 'Temporary', 'Camera', 'figure', 'bolts', 'handles', 'Scar cutter', ' weld', ' lugs')


def candidates(lo, hi, extra=()):
    """Mesh objects whose bounds touch the box lo..hi (slab footprint swept along its cast)."""
    out = []
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.hide_render or any(k in ob.name for k in SKIP):
            continue
        a, b = _bounds(ob)
        if all(a[i] <= hi[i]+.1 and b[i] >= lo[i]-.1 for i in range(3)):
            out.append(ob)
    return out + [o for o in extra if o not in out]


def cast(objs, origin, d):
    """First (point, normal, object) a ray from origin along d meets among objs."""
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in objs:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        dl = (inv.to_3x3() @ d).normalized()
        hit, p, n, _ = ev.ray_cast(inv @ origin, dl)
        if hit:
            p = ev.matrix_world @ p
            dist = (p-origin).dot(d)
            if best is None or dist < best[0]:
                best = (dist, p, (ev.matrix_world.to_3x3().inverted().transposed() @ n).normalized(), ob)
    return None if best is None else best[1:]


class Frame:
    """A slab's casting frame: plane coordinates (u, v), ray direction d into the hull."""
    def __init__(self, kind):
        self.kind = kind
        self.d = {'top': Vector((0, 0, -1)), 'port': Vector((0, -1, 0)), 'starboard': Vector((0, 1, 0))}[kind]

    def origin(self, u, v):
        return {'top': Vector((u, v, 60)), 'port': Vector((u, 60, v)), 'starboard': Vector((u, -60, v))}[self.kind]

    def box(self, u0, u1, v0, v1):
        if self.kind == 'top':
            return Vector((u0, v0, -20)), Vector((u1, v1, 60))
        y = (0, 60) if self.kind == 'port' else (-60, 0)
        return Vector((u0, y[0], v0)), Vector((u1, y[1], v1))


# ---------------------------------------------------------------- armour slabs

# area, frame, (u0, u1, v0, v1), thickness m, mode, skew deg, corner cuts
# (fractions of a cell: u0v0, u1v0, u1v1, u0v1), options. Order matters: a
# slab cast after another sits on it where they overlap.
SLABS = [
    # Starboard / centre deck either side of the deckhouse (the sprite's
    # largest dark field, cols 36-44, rows 29-62).
    ('starboard deck', 'top', (-9.0, -2.7, -12.3, -5.7), .62, 'flat', 3.5, (.8, .3, .9, 0), {'cutout': (-7.4, -5.7, -10.3, -8.2)}),
    ('starboard deck', 'top', (-3.5, 2.5, -11.3, -6.0), .5, 'flat', -2.5, (0, .9, .4, .7), {'lugs': True}),
    ('starboard deck', 'top', (7.75, 13.5, -9.0, -5.45), .55, 'flat', 1.8, (.6, 0, .8, .5), {}),
    ('starboard deck', 'top', (8.4, 11.2, -8.5, -6.6), .35, 'flat', 9, (.5, .8, .3, .6), {'only': ('Pirate armour starboard deck 3',)}),
    ('starboard deck', 'top', (-14.4, -9.9, -13.95, -10.95), .45, 'drape', -4, (.5, .7, 0, .9), {}),
    # Aft deck by the stern, port of the cargo hatch (cols 19-30, rows 56-62),
    # either side of the aft deck vent, which is left open.
    ('aft deck', 'top', (-14.6, -10.6, 2.0, 6.8), .55, 'flat', -3, (.9, 0, .6, .4), {'lugs': True}),
    ('aft deck', 'top', (-13.9, -11.6, 2.6, 4.9), .35, 'flat', -11, (.7, .3, .6, .8), {'only': ('Pirate armour aft deck 1',)}),
    ('aft deck', 'top', (-17.85, -14.9, .8, 12.4), .5, 'drape', 2, (.5, .4, .9, 0), {'bend': 'u'}),
    # Port drive pod, outboard top and side (cols 0-6, rows 45-53).
    ('port pod', 'top', (-7.9, -5.0, 20.6, 29.5), .6, 'drape', 2.5, (.6, .3, .8, .2), {}),
    ('port pod', 'port', (-7.3, -1.1, -3.9, 2.9), .55, 'flat', -3, (.7, .2, .9, .4), {'cutout': (-4.9, -3.3, -1.6, .3)}),
    ('port pod', 'top', (-4.4, 1.0, 26.6, 29.9), .45, 'drape', -2, (.3, .8, 0, .6), {}),
    # Starboard drive pod, the scattered dark on its outboard face and top.
    ('starboard pod', 'starboard', (-8.0, -2.5, -4.3, 3.3), .5, 'flat', 2.5, (.4, .9, .2, .7), {}),
    ('starboard pod', 'top', (-7.9, -4.9, -29.5, -23.2), .55, 'drape', -2, (.7, 0, .4, .8), {}),
    # Bow neck: the bridge capsule's aft sleeve between the cage rings, on its
    # outboard upper quarter between the cage bars; the inboard quarter holds
    # the airlock (cols 17-25, rows 19-24).
    ('bow neck', 'top', (22.5, 25.0, 10.1, 13.4), .4, 'drape', 0, (.5, .2, .6, .3),
     {'only': ('Bridge capsule hull', 'Bridge armour sleeve 1')}),
    # Between the body and the port pod over the spar (cols 10-19, rows 34-50).
    ('spar', 'top', (-9.3, -3.0, 10.4, 13.75), .55, 'drape', 2, (.4, .8, .2, 0), {}),
    ('spar', 'top', (-3.7, 2.6, 10.6, 12.9), .5, 'drape', -3, (.7, 0, .6, .3), {}),
    ('spar', 'top', (2.9, 8.0, 14.4, 17.55), .5, 'drape', 1.5, (0, .5, .9, .4), {'lugs': True}),
]
CELL = .75
EMBED = .18


def _grid(rect, skew, cuts, rng):
    u0, u1, v0, v1 = rect
    nu = max(2, round((u1-u0)/CELL))
    nv = max(2, round((v1-v0)/CELL))
    cu, cv = (u0+u1)/2, (v0+v1)/2
    a = math.radians(skew)
    ca, sa = math.cos(a), math.sin(a)
    P = {}
    for i in range(nu+1):
        for j in range(nv+1):
            u, v = u0+(u1-u0)*i/nu, v0+(v1-v0)*j/nv
            if i in (0, nu) or j in (0, nv):  # torch-cut edge: ragged in plane
                u += rng.uniform(-.12, .12)*(i not in (0, nu))
                v += rng.uniform(-.12, .12)*(j not in (0, nv))
                if i in (0, nu):
                    u += rng.uniform(-.16, .16)
                if j in (0, nv):
                    v += rng.uniform(-.16, .16)
            du, dv = u-cu, v-cv
            P[i, j] = Vector((cu+du*ca-dv*sa, cv+du*sa+dv*ca))
    # Cut corners: pull each corner toward its inner diagonal neighbour.
    for (i, j, di, dj), f in zip([(0, 0, 1, 1), (nu, 0, -1, 1), (nu, nv, -1, -1), (0, nv, 1, -1)], cuts):
        if f:
            P[i, j] = P[i, j].lerp(P[i+di, j+dj], f*.9)
    # One torch notch bitten into a long edge.
    if nu >= 4:
        i, j = rng.randrange(2, nu-1), rng.choice((0, nv))
        P[i, j] = P[i, j].lerp(P[i, 1 if j == 0 else nv-1], rng.uniform(.45, .6))
    return nu, nv, P


def _boundary(nu, nv):
    loop = [(i, 0) for i in range(nu)] + [(nu, j) for j in range(nv)]
    loop += [(i, nv) for i in range(nu, 0, -1)] + [(0, j) for j in range(nv, 0, -1)]
    return loop


def _hit(kit, frame, objs, u, v):
    """Surface along the frame's ray at (u, v). Four neighbour samples lift a
    vertex that falls into a seam gap toward the plates either side, by no
    more than a seam's depth, so on a slope it still sits on its own point."""
    best, centre = None, None
    for du, dv in ((0, 0), (.3, 0), (-.3, 0), (0, .3), (0, -.3)):
        if frame.kind == 'top':
            kit.hull, keep = objs, kit.hull
            h = kit.surface(u+du, v+dv)
            kit.hull = keep
            h = None if h is None else (Vector((u+du, v+dv, h[0].z)), h[1])
        else:
            h = cast(objs, frame.origin(u+du, v+dv), frame.d)
        if h is None:
            continue
        height = -h[0].dot(frame.d)
        if du == dv == 0:
            centre = height
        if best is None or height > best[0]:
            best = (height, h[1])
    if best is not None and centre is not None:
        best = (min(best[0], centre+.12), best[1])
    return best


def _point(frame, u, v, height):
    o = frame.origin(u, v)
    # the point on the ray whose height (-p.d) is `height`
    return o + frame.d*((-o.dot(frame.d))-height)


def slab(kit, mats, name, area, frame, rect, t, mode, skew, cuts, opts, rng, built):
    u0, u1, v0, v1 = rect
    lo, hi = frame.box(u0-1, u1+1, v0-1, v1+1)
    objs = candidates(lo, hi, built)
    if 'only' in opts:
        objs = [o for o in objs if o.name.startswith(opts['only'])]
    nu, nv, P = _grid(rect, skew, cuts, rng)
    H, N = {}, {}
    mid = sum(P.values(), Vector((0, 0)))/len(P)
    for k, p in P.items():
        h = _hit(kit, frame, objs, p.x, p.y)
        f = 0
        while h is None and f < .5:  # an edge vertex past a chamfered end: pull it in
            f += .1
            P[k] = p.lerp(mid, f)
            h = _hit(kit, frame, objs, P[k].x, P[k].y)
        if h is None:
            raise RuntimeError(f'{name}: no hull under ({p.x:.1f}, {p.y:.1f})')
        H[k], N[k] = h
    if mode == 'flat':
        # A flat block bedded on whatever is under it: top plane fitted to
        # the surface, raised to clear its highest point by the thickness,
        # with a slight crude tilt of its own.
        ks = list(P)
        A = [(1, P[k].x, P[k].y) for k in ks]
        import numpy as np
        coef = np.linalg.lstsq(np.array(A), np.array([H[k] for k in ks]), rcond=None)[0]
        coef[1] += rng.uniform(-.012, .012)
        coef[2] += rng.uniform(-.012, .012)
        fit = lambda p: coef[0]+coef[1]*p.x+coef[2]*p.y
        lift = max(H[k]-fit(P[k]) for k in ks)+t
        top = {k: fit(P[k])+lift for k in ks}
        topf = lambda p: fit(p)+lift
    else:
        # A bent plate: constant across the bend axis, resting on the highest
        # point of each line, so it folds once or twice instead of following
        # every seam.
        top = {}
        if opts.get('bend', 'v') == 'v':
            for j in range(nv+1):
                h = max(H[i, j] for i in range(nu+1))
                for i in range(nu+1):
                    top[i, j] = h+t
        else:
            for i in range(nu+1):
                h = max(H[i, j] for j in range(nv+1))
                for j in range(nv+1):
                    top[i, j] = h+t
        topf = lambda p: max(top.values())
    idx, verts = {}, []
    for k in P:
        idx[k, 0] = len(verts)
        verts.append(tuple(_point(frame, P[k].x, P[k].y, H[k]-EMBED)))
        idx[k, 1] = len(verts)
        verts.append(tuple(_point(frame, P[k].x, P[k].y, top[k])))
    faces = []
    for i in range(nu):
        for j in range(nv):
            q = [(i, j), (i+1, j), (i+1, j+1), (i, j+1)]
            faces.append(tuple(idx[c, 1] for c in q))
            faces.append(tuple(idx[c, 0] for c in reversed(q)))
    loop = _boundary(nu, nv)
    for a, b in zip(loop, loop[1:]+loop[:1]):
        faces.append((idx[a, 0], idx[b, 0], idx[b, 1], idx[a, 1]))
    ob = kit.mesh(name, verts, faces, mats.armour, .06)
    ob['pirate_area'] = area
    ob['thickness_m'] = round(t*METRES_PER_UNIT, 3)

    centre = sum(P.values(), Vector((0, 0)))/len(P)
    out = -frame.d
    if 'cutout' in opts:
        # A window torched through the slab over the original plate.
        a0, a1, b0, b1 = opts['cutout']
        ring = [(a0+.2, b0), (a1, b0+.25), (a1-.15, b1), (a0, b1-.3)]
        hmin = min(H.values())-1
        hmax = max(top.values())+1
        lower = [_point(frame, u, v, hmin) for u, v in ring]
        upper = [_point(frame, u, v, hmax) for u, v in ring]
        cutter = kit.mesh('Temporary cutout', [tuple(p) for p in lower+upper],
                          [(3, 2, 1, 0), (4, 5, 6, 7)]+[(i, (i+1) % 4, 4+(i+1) % 4, 4+i) for i in range(4)], mats.weld)
        apply_cut(ob, cutter)
        # Weld round the window's rim on the slab top.
        cut_rim = [(Vector(r), topf(Vector(r))) for r in ring]
    else:
        cut_rim = []

    # A lumpy fillet weld all round, where the slab's wall meets what it sits on.
    to3 = (lambda w: Vector((w.x, w.y, 0))) if frame.kind == 'top' else (lambda w: Vector((w.x, 0, w.y)))
    base, outs = [], []
    for a, b in zip(loop, loop[1:]+loop[:1]):
        pa, pb = P[a], P[b]
        e = (pb-pa).normalized()
        o = Vector((e.y, -e.x))
        if o.dot(pa.lerp(pb, .5)-centre) < 0:
            o = -o
        n = max(1, round((pb-pa).length/.2))
        for k in range(n):
            f = k/n
            p2 = pa.lerp(pb, f)
            base.append(_point(frame, p2.x, p2.y, H[a]+(H[b]-H[a])*f))
            outs.append(to3(o).normalized())
    fillet(kit, name+' weld', base, outs, out, mats.weld, rng)

    # Bolt heads along the inner ring of the top; welded slabs still got some.
    pts = []
    for i in range(1, nu):
        for j in (1, nv-1):
            if rng.random() < .55:
                pts.append(tuple(_point(frame, P[i, j].x, P[i, j].y, top[i, j])))
    for j in range(2, nv-1):
        for i in (1, nu-1):
            if rng.random() < .45:
                pts.append(tuple(_point(frame, P[i, j].x, P[i, j].y, top[i, j])))
    if pts:
        bolts(kit, name+' bolts', pts, tuple(out), mats.steel, .14, .1)

    if opts.get('lugs'):
        # Two lifting lugs left on from the yard crane: holed tabs on the top.
        lug_parts = []
        for i, j in ((1, nv//2), (nu-1, nv//2)):
            p = _point(frame, P[i, j].x, P[i, j].y, top[i, j])
            lug = kit.box('Temporary lug', tuple(p+out*.28), (.6, .14, .62) if frame.kind == 'top' else (.6, .62, .14), mats.armour, 0)
            hole = kit.cylinder('Temporary lug hole', tuple(p+out*.38), .13, 1, mats.weld, 'Y', 8, 0)
            apply_cut(lug, hole)
            lug_parts.append(lug)
        join(lug_parts, name+' lugs', .02)
    return ob


def fillet(kit, name, base, outs, up, mat, rng, size=1.0):
    """A closed, lumpy fillet weld along a loop of seam points: a triangular
    section standing against the wall (up) and running out over the surface
    (outs), its size varying bead to bead."""
    verts, faces = [], []
    for b, o in zip(base, outs):
        s = size*rng.uniform(.75, 1.25)
        verts += [tuple(b+up*.17*s-o*.02), tuple(b+o*.2*s+up*.015), tuple(b-o*.06-up*.07)]
    n = len(base)
    for i in range(n):
        j = (i+1) % n
        for k in range(3):
            kk = (k+1) % 3
            faces.append((3*i+k, 3*j+k, 3*j+kk, 3*i+kk))
    return kit.mesh(name, verts, faces, mat)


def _bead(verts, faces, c, out, along, frame, rng):
    """One lumpy weld bead: a squashed, slightly twisted octahedron on the seam."""
    side = out.cross(Vector((along.x, along.y, 0)) if frame.kind == 'top' else
                     Vector((along.x, 0, along.y))).normalized()
    a3 = (Vector((along.x, along.y, 0)) if frame.kind == 'top' else Vector((along.x, 0, along.y))).normalized()
    s = rng.uniform(.85, 1.2)
    ax = [a3*.17*s, side*.12*s, out*.1*s]
    b = len(verts)
    for k, sign in ((0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)):
        verts.append(tuple(c+ax[k]*sign))
    faces += [(b+0, b+2, b+4), (b+2, b+1, b+4), (b+1, b+3, b+4), (b+3, b+0, b+4),
              (b+2, b+0, b+5), (b+1, b+2, b+5), (b+3, b+1, b+5), (b+0, b+3, b+5)]


def apply_cut(target, cutter, mode='TRANSFER'):
    mod = target.modifiers.new('Pirate cut', 'BOOLEAN')
    mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'MANIFOLD'
    if hasattr(mod, 'material_mode'):
        mod.material_mode = mode
    bpy.context.view_layer.objects.active = target
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)


def join(parts, name, bevel=0):
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    ob = parts[0]
    ob.name = ob.data.name = name
    return ob


def armour(kit, mats, rng):
    built, record, counts = [], [], {}
    for area, kind, rect, tm, mode, skew, cuts, opts in SLABS:
        counts[area] = counts.get(area, 0)+1
        name = f'Pirate armour {area} {counts[area]}'
        ob = slab(kit, mats, name, area, Frame(kind), rect, M(tm), mode, skew, cuts, opts, rng, built)
        built.append(ob)
        record.append({'name': name, 'area': area, 'face': kind, 'rect': list(rect), 'thickness_m': tm,
                       'mode': mode, 'skew_deg': skew, **{k: True for k in opts if k != 'only'}})
    return built, record


# ---------------------------------------------------------------- guns

def chaingun(kit, slot, x, y, z, light):
    """WS 001: a six-barrel rotary cannon on the clean gun's cradle and mantlet.

    Fixed: the motor/drum housing against the mantlet, its drive motor and the
    belt feed over the mantlet from the breech. Rotating, under the empty
    'Weapon WS 001 rotor' (local X = spin axis): spindle, six barrels, rear
    and mid clamp rings, and the muzzle plate (the 'muzzle brake', whose
    forward-most vertex is the muzzle).
    """
    t = slot['id']
    kit.cylinder(f'Weapon {t} motor housing', (x-1.75, y, z), .95, 1.7, kit.steel, 'X', 10, .06)
    kit.cylinder(f'Weapon {t} motor housing band', (x-1.2, y, z), 1.02, .22, light, 'X', 10, .02)
    kit.box(f'Weapon {t} drive motor', (x-1.95, y-.75, z+.72), (1.1, .5, .5), kit.dark, .05)
    # Belt feed: out of the breech top, over the mantlet, down into the drum.
    a, m, b = Vector((x-6.0, y+.55, z+1.65)), Vector((x-3.0, y+.55, z+1.5)), Vector((x-1.9, y+.55, z+.98))

    def run(name, p, q):
        d = (q-p).normalized()
        n = Vector((d.z, 0, -d.x))
        sec = lambda c: [c+Vector((0, -.28, 0))-n*.16, c+Vector((0, .28, 0))-n*.16,
                         c+Vector((0, .28, 0))+n*.16, c+Vector((0, -.28, 0))+n*.16]
        lo, up = sec(p), sec(q)
        kit.mesh(name, [tuple(v) for v in lo+up], [(3, 2, 1, 0), (4, 5, 6, 7)]+[(i, (i+1) % 4, 4+(i+1) % 4, 4+i) for i in range(4)],
                 light, .02)
    run(f'Weapon {t} ammo feed', a, m+(m-a).normalized()*.15)
    run(f'Weapon {t} ammo feed drop', m, b)
    # Rotor.
    x0, x1 = x-.9, x+4.75
    rotor = bpy.data.objects.new(f'Weapon {t} rotor', None)
    rotor.empty_display_type = 'ARROWS'
    rotor.empty_display_size = 1.5
    rotor.location = (x0, y, z)
    bpy.context.collection.objects.link(rotor)
    rotor['spin_axis'] = 'local X'
    rotor['barrels'] = 6
    parts = [kit.cylinder(f'Weapon {t} spindle', ((x0+x1+.4)/2, y, z), .17, x1+.4-x0, light, 'X', 8, .02)]
    for k in range(6):
        a = k*math.tau/6+math.pi/6
        parts.append(kit.cylinder(f'Weapon {t} barrel {k+1}', ((x0+x1)/2, y+.42*math.cos(a), z+.42*math.sin(a)),
                                  .155, x1-x0, kit.steel, 'X', 8, .015))
    parts.append(kit.cylinder(f'Weapon {t} rear barrel clamp', (x0+.25, y, z), .72, .35, light, 'X', 12, .03))
    parts.append(kit.cylinder(f'Weapon {t} barrel clamp', (x+2.0, y, z), .68, .32, light, 'X', 12, .03))
    brake = kit.cylinder(f'Weapon {t} muzzle brake', (x1+.17, y, z), .68, .36, kit.steel, 'X', 12, .03)
    for k in range(6):
        a = k*math.tau/6+math.pi/6
        bore = kit.cylinder('Temporary bore', (x1+.2, y+.42*math.cos(a), z+.42*math.sin(a)), .095, 1.0, kit.dark, 'X', 8, 0)
        apply_cut(brake, bore)
    parts.append(brake)
    bv, bf = [], []
    for k in range(6):
        a = k*math.tau/6+math.pi/6
        cy, cz = y+.42*math.cos(a), z+.42*math.sin(a)
        b = len(bv)
        ring = [(cy+.1*math.cos(j*math.pi/4), cz+.1*math.sin(j*math.pi/4)) for j in range(8)]
        bv += [(x1-.02, yy, zz) for yy, zz in ring]+[(x1+.04, yy, zz) for yy, zz in ring]
        bf += [tuple(b+j for j in range(8)), tuple(b+8+j for j in reversed(range(8)))]
        bf += [(b+j, b+8+j, b+8+(j+1) % 8, b+(j+1) % 8) for j in range(8)]
    parts.append(kit.mesh(f'Weapon {t} muzzle bores', bv, bf, kit.dark))
    bpy.context.view_layer.update()
    for p in parts:
        p.parent = rotor
        p.matrix_parent_inverse = rotor.matrix_world.inverted()


def twin_mg(kit, slot, x, y, gz):
    """WS 002: twin light machine guns side by side in front of the mantlet,
    perforated cooling jackets, one muzzle each ('... muzzle brake 1/2')."""
    t = slot['id']
    kit.box(f'Weapon {t} twin cradle', (x+3.75, y, gz), (.7, 1.55, .78), kit.steel, .05)
    for i, s in ((1, 1), (2, -1)):
        yy = y+s*.42
        kit.cylinder(f'Weapon {t} barrel {i}', (x+6.0, yy, gz), .13, 4.1, kit.steel, 'X', 8, .015)
        jacket = kit.cylinder(f'Weapon {t} barrel jacket {i}', (x+5.35, yy, gz), .25, 2.7, kit.steel, 'X', 12, .02)
        holes = []
        for c in range(6):
            hx = x+4.3+c*.42
            for a in (0, 60, 120, 180, 240, 300):
                r = math.radians(a+(30 if c % 2 else 0))
                holes.append(kit.box('Temporary hole', (hx, yy+.25*math.cos(r), gz+.25*math.sin(r)), (.2, .14, .14), kit.dark, 0))
                holes[-1].rotation_euler[0] = r
        cutter = join(holes, 'Temporary jacket holes')
        apply_cut(jacket, cutter)
        brake = kit.cylinder(f'Weapon {t} muzzle brake {i}', (x+8.33, yy, gz), .18, .56, kit.steel, 'X', 8, .02)
        apply_cut(brake, kit.box('Temporary brake port', (x+8.33, yy, gz), (.13, 1, .12), kit.dark, 0))
        kit.cylinder(f'Weapon {t} muzzle bore {i}', (x+8.62, yy, gz), .08, .03, kit.dark, 'X', 8, 0)


GUNS = {'WS 001': chaingun, 'WS 002': twin_mg}


# ---------------------------------------------------------------- scars

# id, kind, location, ray origin and direction (cast onto the clean ship plus
# the armour), size (radius in units; gouges: half-length, width).
SCARS = [
    ('HD01', 'scorch', 'port pod side skirt plate 4', (5.25, 60, .9), (0, -1, 0), .85),
    ('HD02', 'pock', 'port pod side skirt plate 3', (1.0, 60, -1.9), (0, -1, 0), .55),
    ('HD03', 'pock', 'port pod intake cap, outboard face', (13.6, 60, .6), (0, -1, 0), .5),
    ('HD04', 'gouge', 'port pod armour collar, outboard shoulder', (5.2, 47, 24), (0, -1, -1), (1.5, .38)),
    ('HD05', 'scorch', 'deckhouse forward face, feed-port armour starboard of the windows', (60, -8.0, 7.3), (-1, 0, 0), .9),
    ('HD06', 'pock', 'deckhouse forward face under the window band', (60, -2.0, 7.1), (-1, 0, 0), .42),
    ('HD07', 'scorch', 'deck plate forward of the turret, port', (1.4, 7.4, 60), (0, 0, -1), .9),
    ('HD08', 'patch', 'port pod reinforcing band, outboard face (old hole)', (-12.5, 60, -1.6), (0, -1, 0), (1.05, .9)),
    ('HD09', 'scorch', 'bridge capsule aft sleeve, outboard', (23.75, 60, 3.2), (0, -1, 0), .95),
    ('HD10', 'pock', 'port pod armour collar top plate', (5.6, 19.4, 60), (0, 0, -1), .45),
    ('HD11', 'scorch', 'starboard pod armour collar, outboard face', (2.6, -60, 3.4), (0, 1, 0), 1.3),
]


def _basis(n, hint=None):
    hint = Vector(hint) if hint else (Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((1, 0, 0)))
    u = (hint-n*hint.dot(n)).normalized()
    return u, n.cross(u)


def _scar_targets():
    return [o for o in bpy.context.scene.objects if o.type == 'MESH' and not o.hide_render
            and not any(k in o.name for k in SKIP) and not o.name.startswith(('Weapon', 'Scar'))]


def _decal(kit, mats, name, p, n, objs, r, rng, hole=0.0):
    """A thin soot patch following the surface: an irregular blob (an annulus
    round a pock), each vertex cast back onto the hull along -n."""
    u, v = _basis(n)
    k = 16
    ph = [rng.uniform(0, math.tau) for _ in range(3)]
    outer = [r*(1+.2*math.sin(2*a+ph[0])+.12*math.sin(3*a+ph[1])+.07*math.sin(5*a+ph[2]))
             for a in (i*math.tau/k for i in range(k))]

    def seat(q):
        h = cast(objs, q+n*.8, -n)
        return (h[0] if h and (h[0]-q).length < .7 else q)+n*.05
    fracs = [1.0, .75, .5, .25] if not hole else [1.0, (1+hole)/2, hole]
    rings = [[seat(p+(u*math.cos(a)+v*math.sin(a))*outer[i]*f) for i, a in enumerate(i*math.tau/k for i in range(k))]
             for f in fracs]
    verts, faces = [], []
    for ring in rings:
        verts += [tuple(q) for q in ring]+[tuple(q-n*.035) for q in ring]
    R = lambda r: 2*k*r   # ring r: top k, then bottom k
    for r in range(len(rings)-1):
        for i in range(k):
            j = (i+1) % k
            faces.append((R(r)+i, R(r)+j, R(r+1)+j, R(r+1)+i))
            faces.append((R(r)+k+j, R(r)+k+i, R(r+1)+k+i, R(r+1)+k+j))
    last = len(rings)-1
    for i in range(k):  # outer rim
        j = (i+1) % k
        faces.append((i, k+i, k+j, j))
    if hole:
        for i in range(k):
            j = (i+1) % k
            faces.append((R(last)+j, R(last)+k+j, R(last)+k+i, R(last)+i))
    else:
        c = len(verts)
        verts += [tuple(seat(p)), tuple(seat(p)-n*.035)]
        for i in range(k):
            j = (i+1) % k
            faces.append((R(last)+i, R(last)+j, c))
            faces.append((R(last)+k+j, R(last)+k+i, c+1))
    return kit.mesh(name, verts, faces, mats.scorch)


def _crater(p, n, r, depth, rng, elong=1.0, axis=None):
    """Closed cutter for a shallow pock (or, elongated, a gouge): wide above
    the surface, narrowing to a ragged floor."""
    u, v = _basis(n, axis)
    k = 9
    verts, faces = [], []
    rings = [(1.25, .45), (1.0, 0.0), (.45, -depth)]
    jit = [rng.uniform(.8, 1.15) for _ in range(k)]
    for f, h in rings:
        for i in range(k):
            a = i*math.tau/k
            q = p+n*h+(u*math.cos(a)*elong+v*math.sin(a))*r*f*jit[i]
            verts.append(tuple(q))
    for rr in range(len(rings)-1):
        for i in range(k):
            j = (i+1) % k
            faces.append((rr*k+i, rr*k+j, (rr+1)*k+j, (rr+1)*k+i))
    faces.append(tuple(range(k)))
    faces.append(tuple(reversed(range((len(rings)-1)*k, len(rings)*k))))
    return verts, faces


def scars(kit, mats, rng, armour_objs):
    objs = _scar_targets()
    cuts, record = {}, []
    for sid, kind, where, origin, d, size in SCARS:
        d = Vector(d).normalized()
        h = cast(objs, Vector(origin), d)
        if h is None:
            raise RuntimeError(f'scar {sid}: ray missed the hull')
        p, n, ob = h
        if n.dot(d) > 0:
            n = -n
        row = {'id': sid, 'kind': kind, 'location': where, 'object': ob.name,
               'point': [round(c, 2) for c in p], 'normal': [round(c, 3) for c in n]}
        # Soot seats on the struck part and its siblings (same name stem), so a
        # cage bar or rail over it cannot catch a vertex.
        stem = ' '.join(ob.name.split()[:2])
        near = [o for o in objs if (o is ob or o.name.startswith(stem))
                and all(_bounds(o)[0][i]-3 <= p[i] <= _bounds(o)[1][i]+3 for i in range(3))]
        if kind == 'scorch':
            _decal(kit, mats, f'Scar {sid} scorch', p, n, near, size, rng)
            row['radius'] = size
        elif kind in ('pock', 'gouge'):
            if kind == 'pock':
                r, depth, el, ax = size, size*.6, 1.0, None
                row['radius'] = size
            else:
                r, depth, el, ax = size[1], size[1]*.8, size[0]/size[1], (1, 0, 0)
                row['length'] = round(2*size[0], 2)
                row['width'] = round(2*size[1], 2)
            cuts.setdefault(ob.name, []).append(_crater(p, n, r, depth, rng, el, ax))
            ring = r*1.9 if kind == 'pock' else r*1.2
            if kind == 'pock':
                _decal(kit, mats, f'Scar {sid} scorch', p, n, near, ring, rng, hole=r*1.15/ring)
            row['depth'] = round(depth, 2)
        elif kind == 'patch':
            # The old hole, then a crude plate over most of it: a crescent of
            # the hole still shows at one corner.
            hw, hh = size
            u, v = _basis(n, (1, 0, 0))
            cuts.setdefault(ob.name, []).append(_crater(p-u*.45-v*.35, n, .62, .45, rng))
            q = p+u*.18+v*.1
            corners = [(-1, -1, .25), (1, -1, 0), (1, 1, .3), (-1, 1, 0)]
            ring = []
            for cu, cv, cut in corners:
                c = q+u*cu*hw+v*cv*hh
                if cut:
                    ring += [c-v*cv*cut, c-u*cu*cut]
                else:
                    ring.append(c)
            th = M(.18)
            lower = [c-n*.05 for c in ring]
            upper = [c+n*th for c in ring]
            m = len(ring)
            kit.mesh(f'Scar {sid} patch plate', [tuple(c) for c in lower+upper],
                     [tuple(reversed(range(m))), tuple(range(m, 2*m))]+[(i, (i+1) % m, m+(i+1) % m, m+i) for i in range(m)],
                     mats.armour, .03)
            seam, outs = [], []
            for a, b in zip(ring, ring[1:]+ring[:1]):
                steps = max(1, round((b-a).length/.2))
                o = n.cross(b-a).normalized()
                if o.dot((a+b)/2-q) < 0:
                    o = -o
                for k in range(steps):
                    seam.append(a.lerp(b, k/steps))
                    outs.append(o)
            fillet(kit, f'Scar {sid} patch weld', seam, outs, n, mats.weld, rng, .8)
            bolts(kit, f'Scar {sid} patch bolts', [tuple(q+u*a*(hw-.25)+v*b*(hh-.22)+n*th) for a, b in ((-1, 1), (1, -1), (1, 1))],
                  tuple(n), mats.steel, .12, .08)
            row['plate'] = [round(2*hw*METRES_PER_UNIT, 2), round(2*hh*METRES_PER_UNIT, 2)]
        record.append(row)
    # One live Boolean per base object, as the Second Wind's damage does.
    for name, pieces in cuts.items():
        target = bpy.data.objects[name]
        verts, faces = [], []
        for v_, f_ in pieces:
            b = len(verts)
            verts += v_
            faces += [tuple(b+i for i in f) for f in f_]
        cutter = kit.mesh('Scar cutter - '+name, verts, faces, mats.bare)
        cutter.hide_render = True
        cutter.display_type = 'WIRE'
        mod = target.modifiers.new('Scar cuts', 'BOOLEAN')
        mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'EXACT'
        mod.material_mode = 'TRANSFER'
        mod.use_self = True
        mod.use_hole_tolerant = True
    return record, sorted(cuts)


# ---------------------------------------------------------------- entry

def build(kit, seed=1709):
    """Armour and scars over the built clean ship (guns: hd_mounts.build(kit, GUNS))."""
    rng = random.Random(seed)
    mats = Mats(kit)
    slabs, slab_record = armour(kit, mats, rng)
    scar_record, cut_objects = scars(kit, mats, rng, slabs)
    from hd_mounts import overlaps
    guns = [o for o in bpy.context.scene.objects if o.type == 'MESH' and o.name.startswith('Weapon')]
    gun_hits = overlaps(slabs, guns)
    print(f'CHECK pirate armour against the guns: {gun_hits if gun_hits else "clear"}')
    base = [o for o in bpy.context.scene.objects if o.type == 'MESH' and not o.name.startswith(('Pirate', 'Scar', 'Weapon'))
            and not o.hide_render]
    bedded = {}
    for ob in slabs:
        bedded[ob.name] = overlaps([ob], base)
        print(f'CHECK {ob.name} bedded in: {", ".join(bedded[ob.name]) or "nothing"}')
    return {
        'name': 'Hound (P)', 'skinHullId': 'hound_d_pirates', 'variant': 'hound_d_pirates_Overdriven',
        'sprite': 'graphics/ships/hound/hound_pirates.png',
        'armour': slab_record,
        'guns': {'WS 001': 'chaingun: rotor empty "Weapon WS 001 rotor" (local X spin), six barrels, '
                           'muzzle "Weapon WS 001 muzzle brake"',
                 'WS 002': 'lightdualmg: twin barrels, muzzles "Weapon WS 002 muzzle brake 1" and "... 2"',
                 'WS 003': 'flare launcher, unchanged'},
        'replaced_base_objects': REPLACED,
        'scars': scar_record, 'scar_cut_objects': cut_objects,
        'checks': {'armour_against_guns': gun_hits or 'clear', 'armour_bedded_in': bedded},
        'materials': ['Pirate scrap armour', 'Scar scorch', 'Scar bare metal'],
    }


REPLACED = ['Weapon WS 001 recoil sleeve', 'Weapon WS 001 recoil cylinder upper', 'Weapon WS 001 recoil cylinder lower',
            'Weapon WS 001 barrel', 'Weapon WS 001 barrel jacket', 'Weapon WS 001 jacket band 1',
            'Weapon WS 001 jacket band 2', 'Weapon WS 001 muzzle brake', 'Weapon WS 001 muzzle bore',
            'Weapon WS 002 barrel', 'Weapon WS 002 barrel jacket', 'Weapon WS 002 muzzle brake']
