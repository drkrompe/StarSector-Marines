"""Surface detail on the stern (os_stern, os_engines, os_machinery): exposed machinery and piping round the aft body and blades, engine housing detail.

Owner, after round 11: "I'm liking the silhouette of this ship. The next passes
need to focus on that detail on the hull, exposed machinery, piping, hatches,
and armor." The silhouette is locked: detail stands at most a little proud of
the surface it sits on, never changes the outline, and keeps clear of the
turret rings (os_turrets.SLOTS, RING) and the running lights. Every object
built here is named 'Detail <area> ...'.

Read off onslaught_hegemony.png at 8x (rows 240-372; X = 244 - row, Y = 144 - col):

  blade top    a long raised panel framing dark vent slots runs down the middle of
               each blade's top plate from X -44 to -76; a plate step crosses the
               blade at X -83 with a shorter row of slots behind it (-84..-90) and
               a seam at X -96; the after end is plain plate. A round boss sits on
               the blade's root.
  inner well   pale runs leave the root face and run aft along the blade's inner
               flank toward the drive housings (os_machinery fills the floor; here
               they ride the wall above the banks and dive into the housing top).
  tail         the red-brown radiator channel between pale coamings (os_machinery);
               its manifolds run along the coamings' outer edges and down the
               tail's after face into the mains' thrust collars.

Everything is seated by ray casts against the built hull, armour, bridge and engine
meshes and oriented to the surface it sits on; plates conform to that surface (a
grid of casts), so nothing floats and nothing is buried more than its foot. A part
whose footprint would straddle a chine or run off an edge is skipped and reported
(DETAIL STERN SKIPPED) rather than bent over it.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from os_machinery import Acc, tube, ring, box
from os_stern import blade_inner_y, ROOT_X, WELL_FLOOR, MAINS_FACE_X
from os_engines import ENGINES, MOUTH_R

F = WELL_FLOOR
BLADE_DIR = Vector((-72.0, 20.0, 0)).normalized()      # port blade: aft and outboard
_TREES = []
SKIPPED = []


def _trees(families=('Hull', 'Armour', 'Engine', 'Bridge'), extra=()):
    """BVHs of the renderer's own triangulation (the lofts' quads are twisted, and a BVH that
    splits them on the other diagonal is up to half a unit off the drawn surface). `extra`
    names this module's big-layer objects, so the small layer seats on the plates."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or (ob.name.split(' ')[0] not in families and ob.name not in extra):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        _TREES.append((ob.name, BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                     [tuple(t.vertices) for t in me.loop_triangles])))
        ev.to_mesh_clear()


def cast(o, d, reach=80.0):
    """Nearest hit along a ray: (point, outward normal, object name) or None."""
    o, d = Vector(o), Vector(d).normalized()
    best = None
    for name, t in _TREES:
        p, n, _, dist = t.ray_cast(o, d, reach)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, (-n if n.dot(d) > 0 else n).normalized(), name)
    return None if best is None else best[1:]


# --- conforming parts -----------------------------------------------------------------------

def patch(acc, c, d, along, a, b, t, lift=0.0, sink=.35, maxang=14.0, step=1.2, tag='', normal=None):
    """A plate conforming to the surface met by rays along d around c: a, b half extents along
    `along` (projected onto the surface) and across it, t proud of the surface (+lift). The
    centre ray sets the orientation (normal), the rest of the grid follows the real surface.
    Skipped (and recorded) if any ray misses or the surface turns more than maxang under it.
    `normal` forces the frame (for a part laid on another part)."""
    d = Vector(d).normalized()
    h0 = cast(Vector(c)-d*15.0, d, 60.0)
    if h0 is None:
        if acc is not None:
            SKIPPED.append((tag, 'miss'))
        return None
    p0, n0, _ = h0
    if normal is not None:      # a part laid on another keeps its parent's frame
        n0 = Vector(normal)
    u = Vector(along)-n0*Vector(along).dot(n0)
    u.normalize()
    v = n0.cross(u)
    nu, nv = max(2, int(math.ceil(2*a/step))+1), max(2, int(math.ceil(2*b/step))+1)
    top, bot = [], []
    for i in range(nu):
        for j in range(nv):
            q = p0+u*(-a+2*a*i/(nu-1))+v*(-b+2*b*j/(nv-1))
            h = cast(q+n0*4.0, -n0, 8.0)
            if h is None or h[1].angle(n0) > math.radians(maxang):
                if acc is None:
                    return None
                SKIPPED.append((tag, 'edge: centre %s on %s n %s; fails at %s: %s' % (
                    tuple(round(c_, 1) for c_ in p0), h0[2], tuple(round(c_, 2) for c_ in n0),
                    tuple(round(c_, 1) for c_ in q), 'miss' if h is None else '%s %.0f deg' % (h[2], math.degrees(h[1].angle(n0))))))
                return None
            top.append(h[0]+n0*(t+lift))
            bot.append(h[0]-n0*sink+n0*lift)
    if acc is not None:          # acc None: a dry run, asking only whether the plate fits here
        _solid_grid(acc, top, bot, nu, nv)
    return p0, n0, u, v


def fit(c_list, d, along, a, b, maxang=14.0):
    """The first candidate centre at which an a x b plate fits the surface (dry runs), or None."""
    for c in c_list:
        if patch(None, c, d, along, a, b, 0.0, maxang=maxang) is not None:
            return c
    return None


def _solid_grid(acc, top, bot, nu, nv):
    n = nu*nv
    verts = [tuple(p) for p in top]+[tuple(p) for p in bot]
    k = lambda i, j: i*nv+j
    faces = []
    for i in range(nu-1):
        for j in range(nv-1):
            faces.append((k(i, j), k(i+1, j), k(i+1, j+1), k(i, j+1)))
            faces.append((n+k(i, j), n+k(i, j+1), n+k(i+1, j+1), n+k(i+1, j)))
    rim = [k(i, 0) for i in range(nu)]+[k(nu-1, j) for j in range(1, nv)] + \
        [k(i, nv-1) for i in range(nu-2, -1, -1)]+[k(0, j) for j in range(nv-2, 0, -1)]
    for e0, e1 in zip(rim, rim[1:]+rim[:1]):
        faces.append((e0, e1, n+e1, n+e0))
    acc.add(verts, faces)


def on(r, du, dv, lift=.1):
    """A point on a placed patch r = (p0, n0, u, v), offset du along and dv across it."""
    p0, n0, u, v = r
    return p0+u*du+v*dv+n0*lift


def hatch(plates, trims, c, d, along, a, b, t=.3, tag='', coam=None):
    """A service hatch: a raised coaming (four bars) round a cover plate, two hinge knuckles on
    one edge and a grab handle on the other."""
    r = patch(plates, c, d, along, a, b, t, tag=tag)
    if r is None:
        return None
    n0, u = r[1], r[2]
    coam = plates if coam is None else coam
    w = .24
    for su, sv, aa, bb in ((1, 0, w, b), (-1, 0, w, b), (0, 1, a-2*w, w), (0, -1, a-2*w, w)):
        patch(coam, on(r, su*(a-w), sv*(b-w)), -n0, u, aa, bb, t+.12, sink=.2, maxang=30, step=.8,
              tag=tag+' coaming', normal=n0)
    v = r[3]
    for su in (.5, -.5):                       # hinge knuckles down one long side
        p = on(r, su*a, b-w-.2, t+.25)
        trims.add(*tube(tuple(p-u*.45), tuple(p+u*.45), .26, n=8))
    patch(trims, on(r, 0, -b+w+.45), -n0, u, min(.8, a*.4), .16, .5, lift=t, sink=.2, maxang=30, step=.5,
          tag=tag+' handle', normal=n0)
    return r


def louvre(frames, beds, c, d, along, a, b, n_slats, t=.45, tag=''):
    """A vent: a dark bed, a raised frame of four bars round it and slats across it."""
    r = patch(beds, c, d, along, a, b, .12, tag=tag+' bed')
    if r is None:
        return None
    n0, u = r[1], r[2]
    w = .45
    for su, sv, aa, bb in ((1, 0, w, b), (-1, 0, w, b), (0, 1, a-2*w, w), (0, -1, a-2*w, w)):
        patch(frames, on(r, su*(a-w), sv*(b-w)), -n0, u, aa, bb, t, sink=.2, maxang=30, step=.8,
              tag=tag+' frame', normal=n0)
    for k in range(n_slats):
        f = -1+2*(k+.5)/n_slats
        patch(frames, on(r, f*(a-2*w-.3), 0), -n0, u, .22, b-2*w, t*.75, sink=.2, maxang=30, step=.8,
              tag=tag+' slat', normal=n0)
    return r


def shield_array(acc, trims, c, d, along, a, b, rows, tag=''):
    """A heat shield: a thin backing plate carrying `rows` overlapping slats (shingles laid
    across `along`, each a little proud of the last), with bolt strips down both ends."""
    r = patch(acc, c, d, along, a, b, .2, tag=tag)
    if r is None:
        return None
    n0, u = r[1], r[2]
    hb = b/rows
    for k in range(rows):
        dv = -b+hb*(2*k+1)
        patch(acc, on(r, 0, dv), -n0, u, a-.5, hb-.12, .3+.12*k, lift=.2, sink=.15, maxang=25, step=.9,
              tag=tag+' slat', normal=n0)
    for su in (1, -1):
        patch(trims, on(r, su*(a-.25), 0), -n0, u, .25, b-.1, .9, lift=0, sink=.15, maxang=25, step=.9,
              tag=tag+' strip', normal=n0)
    return r


def surface_pipe(pipes, clamps, guides, r, clamp_every=3, stand=.15):
    """A pipe riding a surface: each guide is (point near the surface, ray direction); the pipe's
    centre stands r+stand off the hit along its normal. Strapped to the surface with a saddle
    every few guides. Returns the centres [(centre, surface point, normal)]."""
    cs = []
    for o, d in guides:
        h = cast(Vector(o)-Vector(d).normalized()*8.0, d, 30.0)
        if h is None:
            continue
        c = h[0]+h[1]*(r+stand)
        if cs and (c-cs[-1][0]).length < .2:
            continue                    # two guides met the same spot: no zero-length segment
        cs.append((c, h[0], h[1]))
    for (p, _, _), (q, _, _) in zip(cs, cs[1:]):
        pipes.add(*tube(tuple(p), tuple(q), r, n=8))
    for k, (p, s, n) in enumerate(cs):
        if k % clamp_every == 1 and 0 < k < len(cs)-1:
            ax = (cs[k+1][0]-cs[k-1][0]).normalized()
            clamps.add(*ring(tuple(p), tuple(ax), r-.05, r+.32, .28, n=10))
            clamps.add(*tube(tuple(s-n*.2), tuple(p-n*r*.4), r*.55, n=6))
    return cs


def pipe_line(pipes, fittings, pts, r):
    """A free pipe through 3D points, with sleeves at the bends."""
    pts = [tuple(p) for p in pts]
    for p, q in zip(pts, pts[1:]):
        pipes.add(*tube(p, q, r, n=8))
    for a, p, b in zip(pts, pts[1:], pts[2:]):
        for o in (a, b):
            dv = (Vector(o)-Vector(p)).normalized()
            fittings.add(*tube(p, tuple(Vector(p)+dv*1.4*r), 1.3*r, n=8))


def ram(acc, lugs, p0, p1, r):
    """A hydraulic actuator: a barrel from p0, a polished rod to p1, clevis lugs at both ends."""
    p0, p1 = Vector(p0), Vector(p1)
    m = p0+(p1-p0)*.58
    acc.add(*tube(tuple(p0), tuple(m), r, n=8))
    acc.add(*tube(tuple(m), tuple(m+(p1-p0).normalized()*.5), r*1.15, n=8))
    lugs.add(*tube(tuple(m), tuple(p1), r*.5, n=8))
    for p in (p0, p1):
        lugs.add(*tube(tuple(p-(p1-p0).normalized()*.6), tuple(p+(p1-p0).normalized()*.6), r*1.2, n=6))


def materials():
    from os_kit import material
    get = lambda name, *a: bpy.data.materials.get(name) or material(name, *a)
    return {'hatch': get('Detail hatch plate', (.25, .165, .135), .3, .55),
            'shield': get('Detail heat shield steel', (.12, .11, .11), .65, .42),
            'pale': get('Pale pipe steel', (.27, .25, .23), .45, .45),
            'bronze': get('Conduit bronze', (.22, .12, .07), .7, .45),
            'rod': get('Detail actuator rod', (.42, .41, .4), .85, .3),
            'top': get('Top run plate steel', (.24, .225, .21), .4, .5),
            'band': get('Header band pale', (.46, .44, .40), .1, .55),
            'radiator': get('Radiator fin red-brown', (.13, .045, .03), .55, .5)}


# --- pass 2: the big layer (reads at 1.5 km) --------------------------------------------------
#
# Owner after detail pass 1: "Push the next detail pass bolder: bigger, denser machinery and
# plating breakup so it reads at 1.5 km too." At 1.5 km the hull is about 0.8 px per unit, so
# this layer works in plates 8-25 units long, wide near-black seams, alternating plate tones
# (the sprite's blades are a pale bone-brown top over a darker red-brown glacis), heavy pipe
# bundles 1.8-3.2 across and drive collars, and leaves the pass-1 parts as the close-up layer
# seated on top of it. Every plate is a dark underlay with the plate inset half a seam on each
# side, so neighbouring plates show a 0.9-wide dark seam between them.

def tones(kit):
    from os_kit import material
    get = lambda name, *a: bpy.data.materials.get(name) or material(name, *a)
    return {'bone': get('Stern plate bone-brown', (.36, .26, .2), .25, .55),
            'dark': get('Stern plate dark red-brown', (.075, .045, .036), .35, .6),
            'steel': get('Stern plate blue-grey steel', (.06, .066, .08), .55, .45),
            'plate': kit.plate,
            'seam': get('Stern seam black', (.012, .011, .011), .3, .8)}


def cast_hull(o, d, reach=80.0, prefix='Hull'):
    """cast() restricted to the hull's own solids (frames for plates must not ride on plates)."""
    o, d = Vector(o), Vector(d).normalized()
    best = None
    for name, t in _TREES:
        if not name.startswith(prefix):
            continue
        p, n, _, dist = t.ray_cast(o, d, reach)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, (-n if n.dot(d) > 0 else n).normalized(), name)
    return None if best is None else best[1:]


def glacis_frame(x, s):
    """The blade's outboard glacis at X, just below its chine: (point, outward normal, unit
    vector down the slope), or None where the drop at that station is not on the glacis."""
    h = cast_hull((x, s*(blade_inner_y(x)+19.0), 60), (0, 0, -1))
    if h is None or not (.3 < h[1].z < .72) or 'aft leg' not in h[2]:
        return None
    p, n, _ = h
    along = Vector((BLADE_DIR.x, s*BLADE_DIR.y, 0))
    w = n.cross(along).normalized()
    if w.z > 0:
        w = -w
    return p, n, w


def glacis_pt(x, s, sl):
    g = glacis_frame(x, s)
    return None if g is None else (g[0]+g[2]*sl, g[1])


# Glacis bundle: (slant below the frame point, radius, kind), outermost (highest) first.
# Glacis bundle: (slant from glacis_frame's point, radius, kind), highest first. The glacis
# runs from its chine (slant -4.5) to a foot 7-14 down the slope, where it meets the quarter
# pod or the blade's vertical side; the bundle rides just under the chine, well inboard of the
# foot, so it never stands outboard of the plan outline.
BUNDLE = [(-1.9, .8, 'pale'), (-.2, .8, 'bronze'), (1.5, .8, 'pale')]
GL_TOP = 2.8                    # the glacis plates' upper edge (slant), under the bundle
GL_BAND = 1.2                   # and their lower edge this far above the foot, so nothing
                                # stands outboard of the blade's side (the plan outline)


_FEET = {}


def glacis_foot(x, s):
    """The glacis's foot at X (slant from glacis_frame's point), smoothed over +-6 units of X
    so a run laid along it is straight; None off the glacis."""
    key = (round(x, 1), s)
    if key not in _FEET:
        vals = [v for v in (_raw_foot(x+dx, s) for dx in (-6, -4, -2, 0, 2, 4, 6)) if v is not None]
        _FEET[key] = None if _raw_foot(x, s) is None or not vals else sum(vals)/len(vals)
    return _FEET[key]


def glacis_along(s):
    """Along the glacis's chine: the blade's direction, rising aft with the crest."""
    return Vector((BLADE_DIR.x, s*BLADE_DIR.y, -BLADE_DIR.x*.158))


def _raw_foot(x, s):
    g = glacis_frame(x, s)
    if g is None:
        return None
    p, n, w = g
    sl = 0.0
    while sl < 30:
        h = cast_hull(tuple(p+w*(sl+.5)+n*5), tuple(-n), 12.0)
        if h is None or h[1].angle(n) > math.radians(10):
            return sl
        sl += .5
    return sl


class Plates:
    """Per-tone accumulators plus the seam underlay."""
    def __init__(self):
        self.acc = {k: Acc() for k in ('bone', 'dark', 'steel', 'plate')}
        self.under = Acc()

    def panel(self, tone, c, d, along, a, b, t=.8, gap=.9, tag='', maxang=14.0, raised=None, step=1.6):
        """A big plate: a dark underlay a x b and the plate inset gap/2 all round, t proud.
        `raised` adds a stepped doubler of that tone 1.5 inside the plate's edge."""
        if patch(None, c, d, along, a, b, 0.0, maxang=maxang, step=step) is None:
            SKIPPED.append((tag, 'plate does not fit'))
            return None
        patch(self.under, c, d, along, a, b, .3, maxang=maxang, step=step, tag=tag+' under')
        r = patch(self.acc[tone], c, d, along, a-gap/2, b-gap/2, t, maxang=maxang, step=step, tag=tag)
        if r is not None and raised and a-gap/2 > 2.5 and b-gap/2 > 2.5:
            patch(self.acc[raised], on(r, 0, 0), -r[1], r[2], a-gap/2-1.5, b-gap/2-1.5, .5, lift=t-.05,
                  sink=.1, maxang=30, step=step, tag=tag+' doubler', normal=r[1])
        return r

    def build(self, kit, M, prefix):
        names = []
        for k, acc in self.acc.items():
            ob = acc.build(kit, f'{prefix} {k} plates', M[k], bevel=.15)
            if ob is not None:
                names.append(ob.name)
        ob = self.under.build(kit, f'{prefix} plate seams', M['seam'])
        if ob is not None:
            names.append(ob.name)
        return names


# Blade top courses: (x0, x1, tone, raised doubler). Seams follow the sprite's plate step at
# X -83 and its seam at X -96; the tones alternate so the blade reads as a run of plates.
TOP_COURSES = [(-54.5, -64.5, 'bone', None), (-65.5, -76.5, 'dark', None), (-77.5, -84.5, 'bone', 'dark'),
               (-85.5, -96.5, 'dark', None), (-97.5, -108.5, 'bone', None)]
# Glacis courses, staggered against the top's so the seams do not line up across the chine.
GL_UP = [(-55.0, -61.0, 'bone', None), (-62.0, -71.0, 'dark', 'steel'), (-72.0, -80.0, 'bone', None),
         (-81.0, -90.0, 'dark', None), (-91.0, -99.5, 'bone', None)]


def big_blade(kit, s, side, M):
    pl = Plates()
    along = Vector((BLADE_DIR.x, s*BLADE_DIR.y, 0))
    down = (0, 0, -1)
    for x0, x1, tone, raised in TOP_COURSES:
        a = _len(x0, x1)
        xc = (x0+x1)/2
        done = False
        for b in (4.4, 4.0, 3.6, 3.2):
            for off in (11.4, 11.0, 11.8, 10.6, 12.2, 10.2):
                c = _blade_pt(xc, off, s)
                if patch(None, c, down, along, a, b, 0.0) is not None:
                    pl.panel(tone, c, down, along, a, b, t=.7, tag=f'{side} blade top {x0}', raised=raised)
                    done = True
                    break
            if done:
                break
        if not done:
            SKIPPED.append((f'{side} blade top {x0}', 'no fit'))
    for x0, x1, tone, raised in GL_UP:
        feet = [glacis_foot(x, s) for x in (x0-.5, (x0+x1)/2, x1+.5)]
        g = glacis_frame((x0+x1)/2, s)
        if g is None or None in feet:
            SKIPPED.append((f'{side} glacis {x0}', 'no frame'))
            continue
        lo = min(feet)-GL_BAND
        bb = (lo-GL_TOP)/2
        if bb < 1.5:
            SKIPPED.append((f'{side} glacis {x0}', f'too short {bb:.1f}'))
            continue
        p, n, w = g
        pl.panel(tone, tuple(p+w*(GL_TOP+bb)), tuple(-n), glacis_along(s), _len(x0, x1), bb, t=.8,
                 tag=f'{side} glacis {x0}', raised=raised, maxang=16)
    # The blade's vertical outer side aft of the quarter pod (lit, and broad, from abeam and
    # astern): two courses of two rows, staggered tones.
    wall = Vector((BLADE_DIR.x, s*BLADE_DIR.y, 0))
    for j, (x0, x1) in enumerate(((-88.8, -97.4),)):
        xc = (x0+x1)/2
        for i, zs in enumerate(((-4.8, -4.2, -5.4, -3.6),)):
            placed = False
            for b in (5.2, 4.6, 4.0, 3.4):
                for zc in zs:
                    h = cast_hull((xc, s*130.0, zc), (0, -s, 0), 120.0)
                    if h is None or abs(h[1].z) > .2:
                        continue
                    if patch(None, tuple(h[0]), tuple(-h[1]), wall, _len(x0, x1), b, 0.0) is not None:
                        pl.panel(('bone', 'dark')[(i+j) % 2], tuple(h[0]), tuple(-h[1]), wall, _len(x0, x1), b,
                                 t=.6, tag=f'{side} blade side {x0} {i}')
                        placed = True
                        break
                if placed:
                    break
            if not placed:
                SKIPPED.append((f'{side} blade side {x0} {i}', 'no fit'))
    return pl.build(kit, M, f'Detail {side} blade')


def big_quarter(kit, s, side, M):
    """The aft quarter's outer side: big plates on the flare (the plate leaning inboard below the
    chine) and on the tumble above it, staggered, alternating tones."""
    pl = Plates()
    fa, fb = Vector((-10.0, s*120.5, 0)), Vector((-72.0, s*99.8, 0))
    fdir = (fb-fa).normalized()
    out = Vector((-fdir.y, fdir.x, 0))
    if out.y*s < 0:
        out = -out
    nfl = Vector((out.x*math.cos(math.radians(30)), out.y*math.cos(math.radians(30)), -math.sin(math.radians(30))))
    L = (fb-fa).length
    for k, f in enumerate((.11, .32, .53, .74)):
        q = fa+(fb-fa)*f
        cands = [tuple(q-out*dz*math.tan(math.radians(30))+Vector((0, 0, -2.0-dz))+nfl*6)
                 for dz in (5.2, 4.6, 5.8, 4.0, 6.4, 3.4, 7.0)]
        a = (L*.2)/2-.1 if f < .9 else L*.07
        for b in (3.0, 2.6, 2.2):
            c = fit(cands, tuple(-nfl), fdir, a, b)
            if c is not None:
                pl.panel(('dark', 'bone')[k % 2], c, tuple(-nfl), fdir, a, b, t=.9,
                         tag=f'{side} flare {f}', raised='steel' if k == 2 else None)
                break
        else:
            SKIPPED.append((f'{side} flare {f}', 'no fit'))
    # The tumble (the steep plate from the deck edge down to the flare chine): found by
    # horizontal casts from outboard.
    for k, f in enumerate((.2,)):            # aft of this the tumble is cut away by the rake
        q = fa+(fb-fa)*f
        placed = False
        for z in (3.0, 4.0, 2.0, 5.0, 1.0):
            h = cast_hull(tuple(q+out*30+Vector((0, 0, z))), tuple(-out), 60.0)
            if h is None or 'aft body' not in h[2] or not (.2 < h[1].z < .75):
                continue
            for b in (3.0, 2.5, 2.0, 1.6):
                c = tuple(h[0])
                if patch(None, c, tuple(-h[1]), fdir, 5.8, b, 0.0) is not None:
                    pl.panel(('bone', 'dark')[k % 2], c, tuple(-h[1]), fdir, 5.8, b, t=.8,
                             tag=f'{side} tumble {f}')
                    placed = True
                    break
            if placed:
                break
        if not placed:
            SKIPPED.append((f'{side} tumble {f}', 'no fit'))
    return pl.build(kit, M, f'Detail {side} aft quarter')


def big_aft_glacis(kit, M):
    """The aft glacis against the core: three plates a side on the raked upper plate and the lap,
    their seams under the pass-1 butt straps (Y 28 and 40)."""
    pl = Plates()
    g = math.radians(20.0)
    d = Vector((math.cos(g), 0, -math.sin(g)))
    for s in (1, -1):
        for k, (y0, y1) in enumerate(((16.0, 27.5), (28.5, 39.5))):
            yc = (y0+y1)/2
            for z in (20.5, 20.0, 21.0, 19.5):
                c = Vector((-21.0, s*yc, z))-d*4
                if pl.panel(('bone', 'dark')[k], tuple(c), tuple(d), (0, s, 0), (y1-y0)/2, 2.0, t=.7,
                            tag=f'aft glacis upper {s*yc}', maxang=10) is not None:
                    break
            pl.panel(('dark', 'bone')[k], (-30.0, s*yc, 15.2), (1, 0, 0), (0, s, 0), (y1-y0)/2, 2.5, t=.7,
                     tag=f'aft glacis lap {s*yc}', maxang=10)
    return pl.build(kit, M, 'Detail aft glacis')


def big_drives(kit, M, m):
    """Bold drive collars: a steel flange ring on the tail's after face round each main, a pale
    heat-shield band and a steel band on each main's shroud, and a pale band on the face of the
    Engine 2/3 armoured collars, so each drive reads as a heavy machine at range."""
    steel, pale, dark = Acc(), Acc(), Acc()
    for i, (x, y, w, _, _, angle) in enumerate(ENGINES, 1):
        z = -6.0 if w == 30 else -8.0
        if w == 30:
            steel.add(*ring((MAINS_FACE_X-.4, y, z), (1, 0, 0), 9.9, 10.95, .55, n=24))
            pale.add(*ring((-109.0, y, z), (1, 0, 0), 8.9, 9.75, .75, n=24))
            steel.add(*ring((-111.9, y, z), (1, 0, 0), 8.9, 9.7, .55, n=24))
            dark.add(*ring((-110.45, y, z), (1, 0, 0), 8.6, 9.6, .6, n=24))
        elif i in (2, 3):
            pale.add(*ring((-105.45, y, z), (1, 0, 0), 5.5, 8.1, .25, n=24))
    names = []
    for acc, mat, nm in ((steel, M['steel'], 'steel collars'), (pale, m['band'], 'heat-shield bands'),
                         (dark, M['seam'], 'band seams')):
        ob = acc.build(kit, f'Detail drive {nm}', mat, bevel=.08)
        if ob is not None:
            names.append(ob.name)
    return names


def big_radiator(kit, s, side, M, m):
    """A radiator bank on the aft quarter's sloped deck strip: a dark bed, pale coamings along
    both sides (the inboard one framing the outer well's rim) and transverse fins."""
    bed, fins, coam, ends = Acc(), Acc(), Acc(), Acc()
    spans = {}
    for x in range(-26, -57, -2):
        spans[x] = deck_span(float(x), s, 96.0, 124.0)
    print('DETAIL STERN deck spans', side, {k: (None if v is None else (round(v[0], 1), round(v[1], 1)))
                                            for k, v in spans.items()})
    # The strip runs aft and inboard; lay the bank along its centre line.
    xs = [x for x in spans if spans[x] is not None and spans[x][1]-spans[x][0] > 5.0]
    if len(xs) < 6:
        SKIPPED.append((f'{side} radiator', 'no deck strip'))
        return []
    x0, x1 = max(xs)-1.0, min(xs)+1.0
    m0 = (spans[max(xs)][0]+spans[max(xs)][1])/2
    m1 = (spans[min(xs)][0]+spans[min(xs)][1])/2
    hw = min(spans[x][1]-spans[x][0] for x in xs)/2
    cdir = Vector((x1-x0, s*(m1-m0), 0)).normalized()
    a = Vector((x1-x0, m1-m0, 0)).length/2-.4
    b = hw-1.3                  # held inboard of the strip's outer edge (the outline from below)
    c = ((x0+x1)/2, s*((m0+m1)/2-.8), 40)
    r = patch(bed, c, (0, 0, -1), tuple(cdir), a, b, .35, maxang=14, tag=f'{side} radiator bed')
    if r is None:
        return []
    n0, u = r[1], r[2]
    k = 0
    du = -a+1.4
    while du < a-1.2:
        patch(fins, on(r, du, 0), -n0, u, .28, b-1.0, 1.1, lift=.3, sink=.1, maxang=30, step=1.2,
              tag=f'{side} radiator fin', normal=n0)
        du += 1.5
        k += 1
    for sv in (1, -1):
        patch(coam, on(r, 0, sv*(b-.45)), -n0, u, a, .5, 1.5, maxang=30, step=1.2, tag=f'{side} radiator coaming',
              normal=n0)
    for su in (1, -1):
        patch(ends, on(r, su*(a-.5), 0), -n0, u, .5, b-.9, 1.3, maxang=30, step=1.2, tag=f'{side} radiator end',
              normal=n0)
    for du in (-a/3, a/3):
        patch(ends, on(r, du, 0), -n0, u, .35, b-.9, 1.4, maxang=30, step=1.2,
              tag=f'{side} radiator tie', normal=n0)
    names = []
    for acc, mat, nm in ((bed, M['seam'], 'bed'), (fins, m['radiator'], 'fins'), (coam, M['bone'], 'coamings'),
                         (ends, kit.grey, 'end bars')):
        ob = acc.build(kit, f'Detail {side} aft quarter radiator {nm}', mat, bevel=.06)
        if ob is not None:
            names.append(ob.name)
    return names


def build_wells(kit, s, side, m):
    """Big machinery in the open wells, sunlit from above: two trunk mains (3.2 across) from the
    header's upper tier out over the diagonal bundles to the outer well's two pumps, with heavy
    flanges, and a heat-exchanger shell over the inner well's banks at the root face."""
    trunks, flanges, shells = Acc(), Acc(), Acc()
    from os_machinery import HDR_X, HDR_HI, OUTER_PUMPS
    Z = 5.6
    R = 1.6
    for (y0, (px, py, pr)) in ((73.0, OUTER_PUMPS[0]), (63.4, OUTER_PUMPS[1])):
        a = Vector((HDR_X, s*y0, Z))
        b = Vector((px, s*py, Z))
        pts = [Vector((HDR_X, s*y0, HDR_HI+1.6)), a, b, Vector((px, s*py, F+13.5))]
        pipe_line(trunks, flanges, pts, R)
        for t in (.22, .44, .66, .88):
            q = a+(b-a)*t
            flanges.add(*ring(tuple(q), tuple((b-a).normalized()), R-.1, R+.75, .5, n=14))
        flanges.add(*ring((px, s*py, F+13.9), (0, 0, 1), R-.1, R+.9, .45, n=14))
        flanges.add(*ring((HDR_X, s*y0, HDR_HI+2.2), (0, 0, 1), R-.1, R+.9, .45, n=14))
    # Inner well: a heat-exchanger shell lying fore and aft over the banks at the root face.
    y, z, r = 27.0, 4.1, 2.4
    shells.add(*tube((-47.6, s*y, z), (-53.4, s*y, z), r, n=14))
    for x0, x1 in ((-47.6, -46.6), (-53.4, -54.4)):
        shells.add(*tube((x0, s*y, z), (x1, s*y, z), r, r*.55, n=14))
    for x in (-48.6, -52.4):
        flanges.add(*ring((x, s*y, z), (1, 0, 0), r-.1, r+.6, .4, n=14))
        flanges.add(*box(x-.5, x+.5, s*(y-1.6), s*(y+1.6), F+11.0, z-r+.4))     # saddles onto the mains
    trunks.build(kit, f'Detail {side} well trunk mains', m['top'])
    flanges.build(kit, f'Detail {side} well trunk flanges', kit.grey, bevel=.05)
    shells.build(kit, f'Detail {side} inner well exchanger', m['pale'], bevel=.1)


# --- the blades -----------------------------------------------------------------------------

def _blade_pt(x, off, s, z=40.0):
    """Point above the port blade at X, `off` outboard of its inner chine (mirrored by s)."""
    return (x, s*(blade_inner_y(x)+off), z)


def _len(x0, x1):
    """Half length along the blade of the stretch between X x0 and x1."""
    return abs(x1-x0)/2/abs(BLADE_DIR.x)


def build_blade(kit, s, side, m):
    plates, shields, trims, vents, beds, hatches = Acc(), Acc(), Acc(), Acc(), Acc(), Acc()
    along = Vector((BLADE_DIR.x, s*BLADE_DIR.y, 0))
    down = (0, 0, -1)
    # The sprite's long vent panel down the middle of the top plate (two groups of slots either
    # side of a cross bar, X -44..-76) and a shorter vent behind the plate step (X -84..-90).
    for x0, x1, n in ((-56.0, -63.0, 4), (-67.0, -75.0, 5), (-87.0, -95.0, 5)):
        louvre(vents, beds, _blade_pt((x0+x1)/2, 11.0, s), down, along, _len(x0, x1), 1.8, n,
               tag=f'{side} blade vent {x0}')
    # (Pass 2: the doublers that stood either side of the vents are now the big plate courses
    # of big_blade, on which these vents, the hatch and the boss seat.)
    # A service hatch on the after course (between the leg tip's light and the seam) and a round
    # access boss on the blade's root, where the sprite has one.
    hatch(hatches, trims, _blade_pt(-101.0, 11.0, s), down, along, 1.5, 1.3, t=.3, tag=f'{side} blade after hatch')
    h = cast(_blade_pt(-46.5, 8.0, s, 60), down)
    if h is not None and h[1].z > .8:
        p, n, _ = h
        hatches.add(*ring(tuple(p-n*.2), tuple(n), 0.0, 2.1, .5, n=16))
        trims.add(*ring(tuple(p+n*.45), tuple(n), 1.25, 1.75, .2, n=16))
        trims.add(*tube(tuple(p+n*.3), tuple(p+n*.9), .55, n=8))
    else:
        SKIPPED.append((f'{side} root boss', 'not on the root ramp'))
    # A hatch on the glacis's upper course (the plates themselves are big_blade's).
    gp = glacis_pt(-76.0, s, 5.0)
    if gp is not None:
        hatch(hatches, trims, tuple(gp[0]), tuple(-gp[1]), along, 1.5, 1.2, tag=f'{side} glacis hatch')
    # The transom (the raked after face over the drives' wash) and the blade's inner face aft of
    # the drive housing, over the mains: heat-shield arrays of overlapping slats.
    a = Vector((-100.0, s*69.5, 0))
    b = Vector((-126.0, s*55.0, 0))
    tdir = (b-a).normalized()
    nrm = Vector((-tdir.y, tdir.x, 0))
    if nrm.x > 0:
        nrm = -nrm
    cands = [tuple(a+(b-a)*f+nrm*10.0+Vector((0, 0, z))) for f in (.42, .36, .48, .3, .54) for z in (8, 6, 10, 4, 12)]
    saved = list(_TREES)            # the transom is bare hull: keep the top course's overhang out of the fit
    _TREES[:] = [t for t in saved if not t[0].startswith('Detail')]
    c = fit(cands, tuple(-nrm), tdir, 5.2, 4.6)
    if c is not None:
        shield_array(shields, trims, c, tuple(-nrm), tdir, 5.2, 4.6, 4, tag=f'{side} transom shield')
    else:
        SKIPPED.append((f'{side} transom shield', 'no fit'))
    _TREES[:] = saved
    din = Vector((BLADE_DIR.y, -BLADE_DIR.x, 0))          # port: into the blade's inner face (+Y)
    din = Vector((din.x, s*din.y, 0))
    for x, z in ((-108.0, 6.5), (-114.6, 10.5)):
        c = Vector((x, s*(blade_inner_y(x)-6.0), z))
        shield_array(shields, trims, c, tuple(din), along, 3.0, 3.6, 3, tag=f'{side} inner face {x}')
    plates.build(kit, f'Detail {side} blade armour plates', kit.plate, bevel=.12)
    shields.build(kit, f'Detail {side} blade heat shields', m['shield'], bevel=.1)
    vents.build(kit, f'Detail {side} blade vent frames', kit.grey, bevel=.06)
    beds.build(kit, f'Detail {side} blade vent beds', kit.dark)
    hatches.build(kit, f'Detail {side} blade hatches', m['hatch'], bevel=.08)
    trims.build(kit, f'Detail {side} blade hatch trim', kit.steel)


def build_conduits(kit, s, side, m):
    """Pass 2 (read at range): two big pipe bundles per side. The inner-well bundle leaves the
    root face, rides the blade's inner wall above the turbopumps and dives into an inlet box on
    the Engine 2/3 drive housing; the outer-well bundle climbs out of the outer well onto the
    blade's outboard glacis and runs aft along it, between the plate courses, to a junction box
    at the glacis's after end. Pipes 1.8-2 across, strapped in heavy clamp blocks."""
    pale, bronze, clamps, fit, boxes = Acc(), Acc(), Acc(), Acc(), Acc()
    # --- inner wall bundle ---
    box_c = Vector((-87.4, s*37.0, 0))
    for k, (z, r, acc, x_end) in enumerate(((6.8, 1.0, pale, -83.6), (8.7, .9, bronze, -86.0), (10.6, 1.0, pale, -88.4))):
        guides = []
        x = ROOT_X-.6
        while x >= x_end:
            guides.append(((x, s*(blade_inner_y(x)-5.0), z), (0, s, 0)))
            x -= 1.8
        cs = surface_pipe(acc, clamps, guides, r, clamp_every=4, stand=.2)
        if not cs:
            SKIPPED.append((f'{side} wall run {z}', 'miss'))
            continue
        p0 = cs[0][0]
        fit.add(*ring(tuple(p0+Vector((.3, 0, 0))), (1, 0, 0), r-.05, r+.7, .4, n=12))     # root-face flange
        acc.add(*tube(tuple(p0), tuple(p0+Vector((.9, 0, 0))), r, n=10))
        pe = cs[-1][0]
        drop = Vector((pe.x-1.4, box_c.y+s*(.8-.0*k), pe.z))
        pipe_line(acc, fit, [pe, drop, Vector((drop.x, drop.y, 2.6))], r)
    for x in (-58.0, -70.0, -80.0):                    # clamp blocks over the bundle
        h = cast((x, s*(blade_inner_y(x)-5.0), 8.7), (0, s, 0), 30.0)
        if h is not None:
            patch(clamps, (x, s*(blade_inner_y(x)-5.0), 8.7), (0, s, 0), (0, 0, 1), 3.3, .55, 2.5,
                  maxang=40, step=.8, tag=f'{side} wall bundle clamp {x}')
    h = cast((box_c.x, box_c.y, 20), (0, 0, -1))
    if h is not None and h[1].z > .8:
        r = patch(boxes, (box_c.x, box_c.y, 20), (0, 0, -1), (1, 0, 0), 3.7, 1.7, 1.8, maxang=12, tag=f'{side} inlet box')
        if r is not None:
            patch(fit, on(r, 0, 0, 1.9), (0, 0, -1), (1, 0, 0), 3.75, 1.75, .4, lift=1.8, sink=.1, maxang=40,
                  tag=f'{side} inlet box cap')
    else:
        SKIPPED.append((f'{side} inlet box', 'not on the housing top'))
    # --- outer glacis bundle ---
    # Three pipes lying along the glacis just above its foot, over the quarter pod; at the
    # forward end each turns down the blade's side into the outer well (the highest furthest
    # forward, so they nest); at the after end they enter a junction box on the glacis.
    along = glacis_along(s)
    X_AFT = -86.0
    for k, (slant, r, kind) in enumerate(BUNDLE):
        acc = pale if kind == 'pale' else bronze
        x_turn = -47.0-1.7*k
        guides = []
        g = glacis_frame(x_turn, s)
        ft = glacis_foot(x_turn, s)
        if g is None or ft is None:
            SKIPPED.append((f'{side} glacis bundle {k}', 'no turn'))
            continue
        p, n, w = g
        foot = p+w*ft
        for z in (F+1.0, F+3.0, F+5.0, foot.z-2.0, foot.z-.6):
            if z < foot.z-.5:
                guides.append(((x_turn, s*(foot.y*s+30.0), z), (0, -s, 0)))
        sl = ft-.6                      # up the glacis to the run's own slant
        while sl > slant:
            guides.append((tuple(p+w*sl), tuple(-n)))
            sl -= 1.8
        x = x_turn
        while x >= X_AFT:
            g = glacis_frame(x, s)
            ft = glacis_foot(x, s)
            if g is not None and ft is not None:
                p, n, w = g
                guides.append((tuple(p+w*slant), tuple(-n)))
            x -= 2.0
        cs = surface_pipe(acc, clamps, guides, r, clamp_every=99, stand=.1)
        if len(cs) < 4:
            SKIPPED.append((f'{side} glacis bundle {k}', 'no run'))
            continue
        p0, s0, n0 = cs[0]
        fit.add(*ring(tuple(p0), tuple(n0), r-.05, r+.75, .4, n=12))
        # the after end: a flanged elbow down into a bulkhead fitting on the glacis
        pe, se, ne = cs[-1]
        ax = (pe-cs[-2][0]).normalized()
        fit.add(*ring(tuple(pe-ax*.6), tuple(ax), r-.05, r+.6, .45, n=12))
        acc.add(*tube(tuple(pe), tuple(se-ne*.3), r, n=10))
        fit.add(*ring(tuple(se+ne*.15), tuple(ne), r-.05, r+.9, .3, n=12))
    mid_above = (BUNDLE[0][0]+BUNDLE[-1][0])/2
    half = (BUNDLE[-1][0]-BUNDLE[0][0])/2+1.3
    for x in (-56.0, -65.0, -74.0, -83.0):             # clamp blocks across the bundle
        g = glacis_frame(x, s)
        ft = glacis_foot(x, s)
        if g is None or ft is None:
            continue
        p, n, w = g
        patch(clamps, tuple(p+w*mid_above), tuple(-n), along, .7, half, 1.85,
              maxang=60, step=.9, tag=f'{side} glacis bundle clamp {x}')
    pale.build(kit, f'Detail {side} blade conduits', m['pale'])
    bronze.build(kit, f'Detail {side} blade bronze conduits', m['bronze'])
    clamps.build(kit, f'Detail {side} blade conduit clamps', kit.grey)
    fit.build(kit, f'Detail {side} blade conduit fittings', kit.grey, bevel=.06)
    boxes.build(kit, f'Detail {side} blade conduit boxes', kit.steel, bevel=.1)


# --- the drives -----------------------------------------------------------------------------

def build_housing(kit, s, side, m):
    """Engine 2/3's armoured housing and its keel: a heat shield over the housing's flat top, a
    pair of gimbal rams from clevis blocks on it to the after collar's crown, and heat shields
    with a drain louvre on the keel's raked after face (its flanks are buried in the aft body)."""
    shields, rams, lugs, vents, beds = Acc(), Acc(), Acc(), Acc(), Acc()
    down = (0, 0, -1)
    shield_array(shields, lugs, (-97.2, s*38.5, 30), down, (0, 1, 0), 3.0, 2.6, 3, tag=f'{side} housing shield')
    for y in (36.2, 39.6):
        h0 = cast((-92.2, s*y, 30), down)
        h1 = cast((-102.4, s*y, 30), down)
        if h0 is None or h1 is None or h0[1].z < .9:
            SKIPPED.append((f'{side} ram {y}', 'no seat'))
            continue
        patch(lugs, (-92.2, s*y, 30), down, (1, 0, 0), .9, .75, 1.0, maxang=12, tag=f'{side} ram block')
        ram(rams, lugs, h0[0]+Vector((0, 0, 1.1)), h1[0]+Vector((0, 0, .55)), .5)
    rk = math.tan(math.radians(24.0))
    d = Vector((1.0, 0, rk)).normalized()
    for yc in (36.6, 47.0):
        shield_array(shields, lugs, Vector((-99.0, s*yc, -24.0))-d*4, tuple(d), (0, 1, 0), 3.4, 2.8, 3,
                     tag=f'{side} keel shield {yc}')
    louvre(vents, beds, Vector((-94.0, s*41.8, -31.0))-d*4, tuple(d), (0, 1, 0), 2.0, 1.4, 4,
           tag=f'{side} keel drain')
    shields.build(kit, f'Detail {side} drive housing heat shields', m['shield'], bevel=.1)
    rams.build(kit, f'Detail {side} drive housing gimbal rams', kit.grey)
    lugs.build(kit, f'Detail {side} drive housing ram rods and lugs', m['rod'])
    vents.build(kit, f'Detail {side} drive keel drain frame', kit.grey, bevel=.05)
    beds.build(kit, f'Detail {side} drive keel drain bed', kit.dark)


def build_pod(kit, i, m):
    """Quarter pod 1/4: a service hatch on its dorsal-inboard facet and a bolted collar where its
    forward end meets the hull."""
    x, y, w, _, _, angle = ENGINES[i-1]
    side = 'port' if y > 0 else 'starboard'
    so = 1 if angle < 180 else -1
    a = math.radians(angle-180)
    fwd = Vector((math.cos(a), math.sin(a), 0))
    ly = Vector((-math.sin(a), math.cos(a), 0))
    mouth = Vector((x, y, -8.0))
    hatches, trims = Acc(), Acc()
    e = (ly*(-so*math.cos(math.radians(45)))+Vector((0, 0, math.sin(math.radians(45))))).normalized()
    for px in (12.0, 20.0):
        c = mouth+fwd*px+e*8.0
        hatch(hatches, trims, c, tuple(-e), tuple(fwd), 1.5, 1.2, t=.3, tag=f'Engine {i} pod hatch {px}')
    hatches.build(kit, f'Detail {side} quarter pod hatches', m['hatch'], bevel=.08)
    trims.build(kit, f'Detail {side} quarter pod hatch trim', kit.steel)


# --- the tail -------------------------------------------------------------------------------

def build_tail(kit, m):
    """Radiator manifolds along the channel's coamings, feeding the fin bed through stub pipes
    and running down the tail's after face into the mains' thrust collars; hatches on the face."""
    pipes, stubs, clamps, fit, hatches, trims = Acc(), Acc(), Acc(), Acc(), Acc(), Acc()
    vframes, vbeds = Acc(), Acc()
    for s in (1, -1):
        guides = [((x, s*15.6, 40), (0, 0, -1)) for x in [-72.0-2.0*k for k in range(16)]]
        cs = surface_pipe(pipes, clamps, guides, .75, clamp_every=3)
        if len(cs) < 3:
            SKIPPED.append(('tail manifold', 'miss'))
            continue
        for p, sp, n in cs[1:-1:2]:
            stubs.add(*tube(tuple(p), (p.x, s*13.9, p.z), .35, n=6))
            fit.add(*ring(tuple(p), (0, 1, 0), .6, 1.05, .3, n=8))
        # Over the after edge and down the face to the collar.
        pe = cs[-1][0]
        hface = cast((-115.0, s*15.6, 9.0), (1, 0, 0))
        xf = hface[0].x-.95 if hface is not None else -106.0
        hcol = cast((xf, s*15.6, 12.0), (0, 0, -1))
        if hface is not None and hcol is not None:
            pipe_line(pipes, fit, [pe, Vector((xf, pe.y, pe.z)), Vector((xf, pe.y, hcol[0].z+.6))], .75)
            fit.add(*ring((xf, pe.y, hcol[0].z+.9), (0, 0, 1), .7, 1.3, .35, n=10))
        else:
            SKIPPED.append(('tail manifold drop', 'miss'))
        # A hatch on the face between the manifold and the stern light.
        hatch(hatches, trims, (-115.0, s*6.4, 9.0), (1, 0, 0), (0, 0, 1), 1.3, 1.5, tag='tail face hatch')
        louvre(vframes, vbeds, (-115.0, s*11.2, 8.6), (1, 0, 0), (0, 0, 1), 2.0, 1.5, 4, tag='tail face vent')
        patch(clamps, (-115.0, s*8.9, 12.0), (1, 0, 0), (0, 1, 0), 5.2, .3, .3, maxang=10, step=1.0,
              tag='tail face strap')
    pipes.build(kit, 'Detail tail radiator manifolds', m['pale'])
    stubs.build(kit, 'Detail tail radiator feed stubs', m['bronze'])
    clamps.build(kit, 'Detail tail manifold clamps', kit.grey)
    fit.build(kit, 'Detail tail manifold fittings', kit.grey)
    hatches.build(kit, 'Detail tail face hatches', m['hatch'], bevel=.08)
    vframes.build(kit, 'Detail tail face vent frames', kit.grey, bevel=.05)
    vbeds.build(kit, 'Detail tail face vent beds', kit.dark)
    trims.build(kit, 'Detail tail face hatch trim', kit.steel)


# --- the aft body ---------------------------------------------------------------------------

def deck_span(x, s, y0, y1, name='Hull aft body', step=.4):
    """The longest run of flat deck (normal within ~12 deg of up, above the wells' floor) on
    `name` across Y at X: (y_low, y_high) in port terms, or None."""
    runs, cur = [], None
    y = y0
    while y <= y1:
        h = cast((x, s*y, 60), (0, 0, -1))
        ok = h is not None and h[2] == name and h[1].z > .97*math.cos(math.radians(13)) and h[0].z > F+3
        if ok:
            cur = [y, y] if cur is None else [cur[0], y]
        elif cur is not None:
            runs.append(cur)
            cur = None
        y += step
    if cur is not None:
        runs.append(cur)
    return max(runs, key=lambda r: r[1]-r[0]) if runs else None


def build_aft_quarter(kit, s, side, m):
    """The outer quarter's sloped deck strip between the outer well's rim and the quarter's
    glacis: two hatches, a vent and the plate seams that cross it."""
    hatches, trims, vents, beds, strips, plates, shields = Acc(), Acc(), Acc(), Acc(), Acc(), Acc(), Acc()
    down = (0, 0, -1)
    along = (-1, 0, 0)

    def mid(x, inset):
        sp = deck_span(x, s, 98.0, 120.0)
        if sp is None or sp[1]-sp[0] < 2*inset+.8:
            SKIPPED.append((f'{side} quarter deck at {x}', f'span {sp}'))
            return None, 0
        return (sp[0]+sp[1])/2, (sp[1]-sp[0])/2-inset
    for x, kind in ((-28.0, 'hatch'), (-35.0, 'vent'), (-43.0, 'hatch')):
        y, hb = mid(x, .9)
        if y is None:
            continue
        if kind == 'hatch':
            hatch(hatches, trims, (x, s*y, 40), down, along, 1.6, min(1.3, hb), tag=f'{side} quarter hatch {x}')
        else:
            louvre(vents, beds, (x, s*y, 40), down, along, 2.4, min(1.4, hb), 4, tag=f'{side} quarter vent')
    for x in (-22.4, -31.6, -38.8, -48.0, -55.0):
        y, hb = mid(x, .5)
        if y is not None:
            patch(strips, (x, s*y, 40), down, (0, 1, 0), hb, .3, .28, maxang=16, step=1.0,
                  tag=f'{side} quarter seam {x}')
    # A hatch on the flare's middle plate (the plates are big_quarter's).
    fa, fb = Vector((-10.0, s*120.5, 0)), Vector((-72.0, s*99.8, 0))
    fdir = (fb-fa).normalized()
    out = Vector((-fdir.y, fdir.x, 0))
    if out.y*s < 0:
        out = -out
    nfl = Vector((out.x*math.cos(math.radians(30)), out.y*math.cos(math.radians(30)), -math.sin(math.radians(30))))
    q = fa+(fb-fa)*.6
    cands = [tuple(q-out*dz*math.tan(math.radians(30))+Vector((0, 0, -2.0-dz))+nfl*6) for dz in (5.2, 4.6, 5.8, 4.0)]
    c = fit(cands, tuple(-nfl), fdir, 1.5, 1.3)
    if c is not None:
        hatch(hatches, trims, c, tuple(-nfl), fdir, 1.5, 1.3, tag=f'{side} flare hatch')
    # The quarter's after faces under the outer well's sill and beside the pod mouth, in the
    # quarter drive's wash (seen from astern): heat-shield arrays and a hatch.
    c = fit([(-90.0, s*92.0, z) for z in (-15.0, -13.0, -17.0, -19.0, -11.0)], (1, 0, 0), (0, 1, 0), 4.6, 3.4)
    if c is not None:
        shield_array(shields, trims, c, (1, 0, 0), (0, 1, 0), 4.6, 3.4, 3, tag=f'{side} quarter after shield')
    else:
        SKIPPED.append((f'{side} quarter after shield', 'no fit'))
    cn = Vector((-.76, s*.65, 0))
    base = Vector((-76.0, s*79.2, 0))+cn*8
    c = fit([tuple(base+Vector((0, 0, z))) for z in (-16.0, -14.0, -18.0, -20.0, -12.0)], tuple(-cn), (0, 0, 1), 2.8, 2.6)
    if c is not None:
        shield_array(shields, trims, c, tuple(-cn), (0, 0, 1), 2.8, 2.6, 1, tag=f'{side} quarter corner shield')
    hatch(hatches, trims, (-90.0, s*78.0, -14.0), (1, 0, 0), (0, 0, 1), 1.4, 1.2, t=.3, tag=f'{side} quarter after hatch')
    hatches.build(kit, f'Detail {side} aft quarter hatches', m['hatch'], bevel=.08)
    trims.build(kit, f'Detail {side} aft quarter hatch trim', kit.steel)
    vents.build(kit, f'Detail {side} aft quarter vent frame', kit.grey, bevel=.06)
    beds.build(kit, f'Detail {side} aft quarter vent bed', kit.dark)
    strips.build(kit, f'Detail {side} aft quarter seams', kit.grey)
    plates.build(kit, f'Detail {side} aft quarter flare plates', kit.plate, bevel=.12)
    shields.build(kit, f'Detail {side} aft quarter heat shields', m['shield'], bevel=.1)


def build_glacis(kit):
    """The aft glacis against the core: butt straps (bolt-row strips) up the upper plate's raked
    face and the lap's vertical face, outboard of the bridge spine."""
    strips = Acc()
    g = math.radians(20.0)
    d = Vector((math.cos(g), 0, -math.sin(g)))
    for s in (1, -1):
        for y in (28.0, 34.0, 40.0):
            if y < 33:
                patch(strips, Vector((-21.0, s*y, 21.0))-d*4, tuple(d), (0, 0, 1), 2.0, .32, .28,
                      maxang=10, step=.8, tag=f'glacis strap {s*y} upper')
            patch(strips, (-30.0, s*y, 15.2), (1, 0, 0), (0, 0, 1), 2.6, .32, .28,
                  maxang=10, step=.8, tag=f'glacis strap {s*y} lap')
    strips.build(kit, 'Detail aft glacis butt straps', kit.grey)


def build(kit):
    SKIPPED.clear()
    _FEET.clear()
    m = materials()
    M = tones(kit)
    _trees()
    # The big layer first, seated on the hull; then the small layer seated on the big one.
    big = []
    for side, s in (('port', 1), ('starboard', -1)):
        big += big_blade(kit, s, side, M)
        big += big_quarter(kit, s, side, M)
        big += big_radiator(kit, s, side, M, m)
    big += big_aft_glacis(kit, M)
    big += big_drives(kit, M, m)
    _trees(extra=set(big))
    _FEET.clear()
    for side, s in (('port', 1), ('starboard', -1)):
        build_blade(kit, s, side, m)
        build_conduits(kit, s, side, m)
        build_housing(kit, s, side, m)
        build_aft_quarter(kit, s, side, m)
        build_wells(kit, s, side, m)
    for i in (1, 4):
        build_pod(kit, i, m)
    build_tail(kit, m)
    build_glacis(kit)
    for tag, why in SKIPPED:
        print('DETAIL STERN SKIPPED', tag, why)
