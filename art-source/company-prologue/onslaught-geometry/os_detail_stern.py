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
from os_machinery import Acc, tube, ring
from os_stern import blade_inner_y, ROOT_X, WELL_FLOOR
from os_engines import ENGINES, MOUTH_R

F = WELL_FLOOR
BLADE_DIR = Vector((-72.0, 20.0, 0)).normalized()      # port blade: aft and outboard
_TREES = []
SKIPPED = []


def _trees(families=('Hull', 'Armour', 'Engine', 'Bridge')):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.split(' ')[0] not in families:
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        _TREES.append((ob.name, BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                     [tuple(p.vertices) for p in me.polygons])))
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
        cs.append((h[0]+h[1]*(r+stand), h[0], h[1]))
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
            'rod': get('Detail actuator rod', (.42, .41, .4), .85, .3)}


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
    for x0, x1, n in ((-52.6, -62.0, 5), (-63.6, -76.0, 6), (-85.0, -91.0, 3)):
        louvre(vents, beds, _blade_pt((x0+x1)/2, 11.0, s), down, along, _len(x0, x1), 2.4, n,
               tag=f'{side} blade vent {x0}')
    # Armour doublers either side of the vents, in courses with a seam between each, following
    # the sprite's plate step at X -83 and its seam at X -96; the after course is full width.
    courses = ((-52.6, -62.0), (-63.6, -76.0), (-77.6, -83.0), (-84.6, -95.4), (-97.0, -108.0))
    for x0, x1 in courses:
        full = x0 in (-77.6, -97.0)
        for off, hb in (((11.0, 3.9),) if full else ((7.6, .75), (14.6, 1.4))):
            if x0 == -52.6 and off > 12:
                continue              # the junction box for the glacis conduits stands here
            xa = -54.6 if (x0 == -52.6 and off < 9) else x0
            patch(plates, _blade_pt((xa+x1)/2, off, s), down, along, _len(xa, x1), hb, .4,
                  tag=f'{side} blade doubler {x0} {off}')
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
    # The outboard glacis: armour plates in the same courses, a hatch on the middle one.
    for x0, x1 in ((-62.0, -72.0), (-73.6, -83.0), (-84.6, -95.4)):
        xc = (x0+x1)/2
        hit = cast(_blade_pt(xc, 21.0, s), down)
        if hit is None or hit[1].z > .75:
            SKIPPED.append((f'{side} glacis {x0}', 'not on the glacis'))
            continue
        p, n, _ = hit
        if x0 == -73.6:
            hatch(hatches, trims, tuple(p), tuple(-n), along, 1.5, 1.2, tag=f'{side} glacis hatch')
        else:
            patch(plates, tuple(p), tuple(-n), along, _len(x0, x1), 2.2, .4, tag=f'{side} blade glacis {x0}')
    # The transom (the raked after face over the drives' wash) and the blade's inner face aft of
    # the drive housing, over the mains: heat-shield arrays of overlapping slats.
    a = Vector((-100.0, s*69.5, 0))
    b = Vector((-126.0, s*55.0, 0))
    tdir = (b-a).normalized()
    nrm = Vector((-tdir.y, tdir.x, 0))
    if nrm.x > 0:
        nrm = -nrm
    cands = [tuple(a+(b-a)*f+nrm*10.0+Vector((0, 0, z))) for f in (.42, .36, .48, .3, .54) for z in (8, 6, 10, 4, 12)]
    c = fit(cands, tuple(-nrm), tdir, 5.2, 4.6)
    if c is not None:
        shield_array(shields, trims, c, tuple(-nrm), tdir, 5.2, 4.6, 4, tag=f'{side} transom shield')
    else:
        SKIPPED.append((f'{side} transom shield', 'no fit'))
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
    """Pipes from the inner well to the drive housing along the blade's inner wall, above the
    banks, and conduits from the outer well up the blade's outer glacis to a junction box."""
    pale, bronze, clamps, fit, boxes = Acc(), Acc(), Acc(), Acc(), Acc()
    # Inner wall runs: out of the root face through a flange, aft along the blade's inner wall
    # (cast toward it at each station), then an elbow off the wall and down into an inlet box
    # on the drive housing's top.
    box_c = Vector((-87.5, s*37.0, 0))
    for (z, r, acc, x_end) in ((4.4, .7, pale, -84.5), (6.2, .55, bronze, -86.5), (7.9, .62, pale, -88.5)):
        guides = []
        x = ROOT_X-.6
        while x >= x_end:
            guides.append(((x, s*(blade_inner_y(x)-5.0), z), (0, s, 0)))
            x -= 1.8
        cs = surface_pipe(acc, clamps, guides, r, clamp_every=4)
        if not cs:
            SKIPPED.append((f'{side} wall run {z}', 'miss'))
            continue
        p0 = cs[0][0]
        fit.add(*ring(tuple(p0+Vector((.3, 0, 0))), (1, 0, 0), r-.05, r+.6, .3, n=10))     # root-face flange
        acc.add(*tube(tuple(p0), tuple(p0+Vector((.9, 0, 0))), r, n=8))
        pe = cs[-1][0]
        drop = Vector((pe.x-1.4, box_c.y+s*.6, pe.z))
        pipe_line(acc, fit, [pe, drop, Vector((drop.x, drop.y, 2.0))], r)
    h = cast((box_c.x, box_c.y, 20), (0, 0, -1))
    if h is not None and h[1].z > .8:
        top = h[0].z
        r = patch(boxes, (box_c.x, box_c.y, 20), (0, 0, -1), (1, 0, 0), 2.9, 1.5, 1.5, maxang=12, tag=f'{side} inlet box')
        if r is not None:
            patch(fit, on(r, 0, 0, 1.6), (0, 0, -1), (1, 0, 0), 3.2, 1.8, .35, lift=1.5, sink=.1, maxang=40,
                  tag=f'{side} inlet box cap')
    else:
        SKIPPED.append((f'{side} inlet box', 'not on the housing top'))
    # Conduits up the outer glacis: out of the outer well's bundles (layer B), up the blade's
    # outer face, over the chine and into a junction box on the forward course.
    jb = _blade_pt(-56.0, 14.4, s)
    hj = cast(jb, (0, 0, -1))
    jr = None
    if hj is not None and hj[1].z > .8:
        jr = patch(boxes, jb, (0, 0, -1), Vector((BLADE_DIR.x, s*BLADE_DIR.y, 0)), 3.6, 1.5, 1.4, maxang=16,
                   tag=f'{side} glacis junction box')
    if jr is not None:
        patch(fit, on(jr, 0, 0, 1.4), -jr[1], jr[2], 3.9, 1.8, .3, lift=1.4, sink=.1, maxang=40,
              tag=f'{side} junction box cap')
        for k, x in enumerate((-45.0, -47.2, -49.4)):
            y_out = blade_inner_y(x)+40.0
            climb = []
            z = F+6.6
            while z < 30:
                hz = cast((x, s*(y_out+8.0), z), (0, -s, 0), 40.0)
                if hz is None or hz[1].z > .6:
                    break
                climb.append(((x, s*y_out, z), (0, -s, 0)))
                z += 1.4
            cs = surface_pipe(bronze if k != 1 else pale, clamps, climb, .5, clamp_every=3)
            if len(cs) < 2:
                SKIPPED.append((f'{side} glacis conduit {x}', 'no climb'))
                continue
            top = cs[-1][0]
            # over the chine and into the box's outboard side
            tgt = on(jr, -2.4+2.4*k, 0, .7)
            pipe_line(bronze if k != 1 else pale, fit,
                      [top, Vector((top.x, top.y, max(top.z, tgt.z)+.4)),
                       Vector((tgt.x, tgt.y+s*1.9, max(top.z, tgt.z)+.4)), Vector((tgt.x, tgt.y+s*1.2, tgt.z))], .5)
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
    # The outer quarter's flare (the plate leaning inboard below the glacis chine): a course of
    # armour doublers with seams between them, and a hatch in it.
    fa, fb = Vector((-10.0, s*120.5, 0)), Vector((-72.0, s*99.8, 0))
    fdir = (fb-fa).normalized()
    out = Vector((-fdir.y, fdir.x, 0))
    if out.y*s < 0:
        out = -out
    nfl = Vector((out.x*math.cos(math.radians(30)), out.y*math.cos(math.radians(30)), -math.sin(math.radians(30))))
    for k, f in enumerate((.2, .36, .52, .68, .84)):
        q = fa+(fb-fa)*f
        cands = [tuple(q-out*dz*math.tan(math.radians(30))+Vector((0, 0, -2.0-dz))+nfl*6) for dz in (5.0, 4.0, 6.0, 3.5, 7.0)]
        c = fit(cands, tuple(-nfl), fdir, 4.6, 2.4)
        if c is None:
            SKIPPED.append((f'{side} flare plate {f}', 'no fit'))
            continue
        if k == 2:
            hatch(hatches, trims, c, tuple(-nfl), fdir, 1.5, 1.3, tag=f'{side} flare hatch')
        else:
            patch(plates, c, tuple(-nfl), fdir, 4.6, 2.4, .4, tag=f'{side} flare plate {f}')
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
            if y < 39:
                patch(strips, Vector((-21.0, s*y, 21.0))-d*4, tuple(d), (0, 0, 1), 2.0, .32, .28,
                      maxang=10, step=.8, tag=f'glacis strap {s*y} upper')
            patch(strips, (-30.0, s*y, 15.2), (1, 0, 0), (0, 0, 1), 2.6, .32, .28,
                  maxang=10, step=.8, tag=f'glacis strap {s*y} lap')
    strips.build(kit, 'Detail aft glacis butt straps', kit.grey)


def build(kit):
    SKIPPED.clear()
    m = materials()
    _trees()
    for side, s in (('port', 1), ('starboard', -1)):
        build_blade(kit, s, side, m)
        build_conduits(kit, s, side, m)
        build_housing(kit, s, side, m)
        build_aft_quarter(kit, s, side, m)
    for i in (1, 4):
        build_pod(kit, i, m)
    build_tail(kit, m)
    build_glacis(kit)
    for tag, why in SKIPPED:
        print('DETAIL STERN SKIPPED', tag, why)
