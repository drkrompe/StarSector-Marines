"""Tarsus drives: two main drive units and four auxiliaries at the stern.

tarsus.ship gives six LOW_TECH slots at angle 180: mains (-78, 19) and
(-78, -18), width 20; auxiliaries (-76, 26) w6, (-76, 11) w8, (-76, -11) w8,
(-76, -25) w6. The auxiliaries' widths overlap the mains' in plan, so they
are hung in a row beneath them (the sprite shows two big copper cans only
from above). Each drive is a whole unit -- a can sunk into the drive block
and a bell at the slot -- never a nozzle poking out of the hull.

Pass 2 detail, read off the sprite's stern rows (X -50..-80): the two copper
cans lie half-sunk in the block's deck with banded rings and thrust yokes
strapping them down; each main bell hangs on a gimbal ring with actuators,
cooling hoops and ribs; a grey machinery spine with radiator slats runs
between the cans and overhangs the stern face as a service housing (ladder,
gantry, bolted covers); turbopumps and propellant manifolds flank the spine
and feed each can; pale radiator banks stand on the outboard deck with the
red-oxide auxiliary drive housings aft of them; RCS quads at the four aft
corners; small stern running lamps and the orange deck marker (X -53, Y -8).
Every drive part is its own named object so the boarded state can cut or scar
it ('Engine <i> ...' per engine, 'Drive ...' shared machinery)."""
import math
import bmesh
from mathutils import Vector
from ts_kit import revolve_x, prism, section, inset, Parts, cut, boolean, material
import ts_hull as H

ENGINES = [(-78, 19, 20, 180), (-76, 26, 6, 180), (-76, 11, 8, 180),
           (-76, -11, 8, 180), (-76, -25, 6, 180), (-78, -18, 20, 180)]
MAIN_Z = 1.5
AUX_Z = -10.5
ANCHORS = {}   # index -> ((x, y, z) of the exhaust anchor, exhaust direction)
SIDES = (('port', 1), ('starboard', -1))
STERN_X = H.DRIVE_BACK          # the block's aft face plane
DECK_Z = H.DRIVE_DECK
BLOCK_PLAN = [(H.DRIVE_FRONT, 38.0), (-66.0, 35.0), (H.DRIVE_BACK, 33.0)]


def _z(w):
    return MAIN_Z if w >= 20 else AUX_Z


# --- local helpers (ts_kit is shared and not edited) ---

def _frame(axis):
    a = Vector(axis).normalized()
    u = a.cross(Vector((0, 0, 1)) if abs(a.z) < .9 else Vector((1, 0, 0))).normalized()
    return a, u, a.cross(u)


def _rod(parts, p0, p1, r, n=8):
    """A closed n-gon prism from p0 to p1 added to a Parts batch."""
    p0, p1 = Vector(p0), Vector(p1)
    a, u, w = _frame(p1-p0)
    ring = [tuple(p0+u*(r*math.cos(2*math.pi*(i+.5)/n))+w*(r*math.sin(2*math.pi*(i+.5)/n))) for i in range(n)]
    return parts.slab(ring, tuple(p1-p0))


def _rev(parts, profile, origin, axis, n=16):
    """Closed (t, r) outline revolved about origin + t*axis, added to a Parts batch."""
    o = Vector(origin)
    a, u, w = _frame(axis)
    verts = []
    for t, r in profile:
        for i in range(n):
            ang = 2*math.pi*(i+.5)/n
            verts.append(tuple(o+a*t+u*(r*math.cos(ang))+w*(r*math.sin(ang))))
    m = len(profile)
    faces = []
    for p in range(m):
        q = (p+1) % m
        faces += [(p*n+i, p*n+(i+1) % n, q*n+(i+1) % n, q*n+i) for i in range(n)]
    return parts._add(verts, faces)


def _ring_x(parts, x0, x1, r0, r1, cy, cz, n=32):
    """Annular band about an X line from x0 to x1, radii r0 (inner) to r1."""
    return _rev(parts, [(x0, r0), (x0, r1), (x1, r1), (x1, r0)], (0, cy, cz), (1, 0, 0), n)


def _deck(kit, x, y):
    hit = kit.surface(x, y)
    return hit[0].z if hit else DECK_Z


def _volume(ob):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    v = bm.calc_volume()
    bm.free()
    return v


def _bolts(parts, pts, axis, r=.14, h=.16, n=6):
    for p in pts:
        _rod(parts, p, tuple(c+a*h for c, a in zip(p, axis)), r, n)


def _rcs_quad(kit, name, x, y, z, out, up, mats):
    """Low-tech RCS quad: pedestal, bolted housing and four bells (aft, outboard, vertical, slant)."""
    body, bells = Parts(), Parts()
    _rod(body, (x, y, z-up*.4), (x, y, z+up*.4), 1.15, 10)
    body.box(x-1.2, x+1.2, y-1.2, y+1.2, z+up*.3, z+up*2.3)
    _bolts(body, [(x+dx, y+dy, z+up*2.3) for dx in (-.85, .85) for dy in (-.85, .85)], (0, 0, up))
    hz = z+up*1.3
    for d in [(-1, 0, 0), (0, out, 0), (0, 0, up), (0, out*.7, -up*.7)]:
        dv = Vector(d).normalized()
        base = Vector((x, y, hz))+dv*1.15
        _rev(bells, [(0, 0), (0, .32), (.25, .3), (.85, .55), (.85, .45), (.3, .2), (.3, 0)], base, dv, 12)
    body.build(kit, name+' housing', mats['pale'])
    bells.build(kit, name+' bells', mats['bright'])


def _lamp(kit, name, p, aim, mats, lens='lamp', r=.42):
    """A small running lamp: housing and an emissive lens facing along aim."""
    h, l = Parts(), Parts()
    a = Vector(aim).normalized()
    p = Vector(p)
    _rev(h, [(-.5, 0), (-.5, r*.9), (-.15, r+.12), (.2, r+.12), (.2, 0)], p, a, 16)
    _rev(l, [(.15, 0), (.15, r), (.3, r*.85), (.36, 0)], p, a, 16)
    h.build(kit, name+' housing', kit.steel)
    l.build(kit, name+' lens', mats[lens])


def _shell_grooves(kit, target, plan, keel, deck, kc, sc, boxes, depth, mat):
    """Cut boxes into target only within `depth` of its prism skin: shallow seams that follow
    walls, chamfers, deck and stern face alike."""
    outer = prism(kit, 'Temporary skin outer', plan, [(-1.5, keel-1.5), (-1.5, deck+1.5)], mat, 0)
    inner = prism(kit, 'Temporary skin inner', plan,
                  [(d+depth, z) for d, z in section(keel+depth, deck-depth, kc, sc)], mat, 0)
    boolean(outer, inner)
    for b in boxes:
        p = Parts()
        p.box(*b)
        c = p.build(kit, 'Temporary groove', mat)
        boolean(c, outer, 'INTERSECT', keep=True)
        boolean(target, c)
    import bpy
    bpy.data.objects.remove(outer, do_unlink=True)


# --- the drive units ---

def _bell_r(r, u):
    return r*(.62+.32*u**1.7)


def _main(kit, i, x, yy, z, r, mats):
    xt = x+4.0
    L = x-xt                                          # negative: the bell runs aft
    bx = lambda u: xt+L*u
    wall = .35
    # Gimbal ring at the bell's root, against the can's thrust plate, with four trunnion blocks.
    g = Parts()
    _ring_x(g, xt-.15, xt-.75, r*.6, r*.78, yy, z, 40)
    for k in range(4):
        a = math.radians(45+90*k)
        cy, cz = yy+r*.8*math.cos(a), z+r*.8*math.sin(a)
        _rod(g, (xt+.05, cy, cz), (xt-.7, cy, cz), .7, 8)
    g.build(kit, f'Engine {i} gimbal ring', kit.steel)
    hoops, ribs = Parts(), Parts()
    for u in (.2, .52, .84):
        rr = _bell_r(r, u)+wall
        _ring_x(hoops, bx(u)+.25, bx(u)-.25, rr-.1, rr+.28, yy, z, 40)
    for k in range(12):
        a = math.radians(15+30*k)
        pts = [(bx(u), yy+(_bell_r(r, u)+wall+.06)*math.cos(a), z+(_bell_r(r, u)+wall+.06)*math.sin(a))
               for u in (.2, .36, .52, .68, .84)]
        for p, q in zip(pts, pts[1:]):
            _rod(ribs, p, q, .14, 6)
    hoops.build(kit, f'Engine {i} cooling hoops', mats['bright'])
    ribs.build(kit, f'Engine {i} stiffener ribs', kit.steel)
    # Liner: a dark heat skin just inside the bell, and the injector plate at the throat.
    liner = [(bx(u), _bell_r(r, u)-.05) for u in (1, .75, .5, .25, 0)]+[(xt+.1, r*.5)]
    liner += [(bx(u), _bell_r(r, u)-.18) for u in (0, .25, .5, .75, 1)]
    revolve_x(kit, f'Engine {i} bell liner', liner, yy, z, mats['liner'], n=40)
    inj = Parts()
    _ring_x(inj, xt-.35, xt-.55, .01, r*.42, yy, z, 24)
    for k in range(6):
        a = math.radians(60*k)
        _rod(inj, (xt-.5, yy+r*.28*math.cos(a), z+r*.28*math.sin(a)),
             (xt-.85, yy+r*.28*math.cos(a), z+r*.28*math.sin(a)), .45, 8)
    _rod(inj, (xt-.5, yy, z), (xt-1.0, yy, z), .7, 8)
    inj.build(kit, f'Engine {i} injector plate', mats['chamber'])
    # Gimbal actuators: two splayed over the top, one under, from the thrust plate to the middle hoop.
    cyl, rod = Parts(), Parts()
    for ang in (60, 120, 270):
        a = math.radians(ang)
        r0, r1 = r*.9, _bell_r(r, .52)+wall+.35
        root = (xt-.05, yy+r0*math.cos(a), z+r0*math.sin(a))
        end = (bx(.52)+.3, yy+r1*math.cos(a), z+r1*math.sin(a))
        mid = tuple(p+(q-p)*.55 for p, q in zip(root, end))
        _rod(cyl, root, mid, .38, 8)
        _rod(rod, mid, end, .2, 8)
    cyl.build(kit, f'Engine {i} actuator cylinders', kit.steel)
    rod.build(kit, f'Engine {i} actuator rods', mats['bright'])
    # Thrust collar: an armoured octagonal frame on the stern face round the can's exit.
    c = Parts()
    _ring_x(c, STERN_X+.05, STERN_X-.45, r*.93, r*1.06, yy, z, 8)
    _bolts(c, [(STERN_X-.45, yy+r*1.0*math.cos(math.radians(22.5+45*k)),
                z+r*1.0*math.sin(math.radians(22.5+45*k))) for k in range(8)], (-1, 0, 0), .18, .2)
    c.build(kit, f'Engine {i} thrust collar', mats['oxide'])
    # The can's banded rings (the sprite's dark bands) and the yokes that strap it into the block.
    bands = Parts()
    for xb in (-60.5, -65.5, -70.5):
        _ring_x(bands, xb+.5, xb-.5, r*.9, r*.97, yy, z, 48)
    bands.build(kit, f'Engine {i} can bands', kit.steel)
    rr = r*.92+.05
    th0 = math.asin(min(.99, (DECK_Z-z-.3)/rr))
    for k, xy in enumerate((-63.0, -68.0), 1):
        yk = Parts()
        n = 9
        pts = [(xy, yy+(rr+.35)*math.cos(th0+(math.pi-2*th0)*j/n), z+(rr+.35)*math.sin(th0+(math.pi-2*th0)*j/n))
               for j in range(n+1)]
        for p, q in zip(pts, pts[1:]):
            yk.beam(p, q, 1.4, .7)
        for p in (pts[0], pts[-1]):
            out = 1 if p[1] > yy else -1
            ln = .95 if out*(yy-H.YC) > 0 else 1.2        # outboard feet stay clear of the aux housings
            yk.box(xy-1.1, xy+1.1, p[1]-out*.3, p[1]+out*ln, DECK_Z-.2, DECK_Z+.6)
            _bolts(yk, [(xy+dx, p[1]+out*ln*.6, DECK_Z+.6) for dx in (-.65, .65)], (0, 0, 1))
        yk.build(kit, f'Engine {i} thrust yoke {k}', kit.grey)


def _aux(kit, i, x, yy, z, r, mats):
    xt = x+4.0
    hoops = Parts()
    for u in (.35, .75):
        xx = xt+(x-xt)*u
        rr = _bell_r(r, u)+.2
        _ring_x(hoops, xx+.18, xx-.18, rr-.05, rr+.22, yy, z, 20)
    hoops.build(kit, f'Engine {i} cooling hoops', mats['bright'])
    # Octagonal housing collar on the skirt's aft face round the can.
    c = Parts()
    _ring_x(c, -72.4, -73.0, r*.74, r*1.08, yy, z, 8)
    _bolts(c, [(-73.0, yy+r*.96*math.cos(math.radians(22.5+90*k)), z+r*.96*math.sin(math.radians(22.5+90*k)))
               for k in range(4)], (-1, 0, 0), .12, .14)
    c.build(kit, f'Engine {i} housing collar', mats['oxide'])


def build(kit):
    ANCHORS.clear()
    mats = {
        'liner': material('Drive bell liner', (.07, .07, .075), .75, .45),
        'chamber': material('Drive chamber dark metal', (.1, .095, .09), .7, .5),
        'bright': material('Drive bright metal', (.42, .41, .40), .8, .35),
        'oxide': material('Drive housing red oxide', (.50, .19, .12), .2, .6),
        'pale': material('Drive pale radiator panel', (.55, .57, .58), .35, .45),
        'fin': material('Drive radiator fin', (.12, .12, .125), .6, .5),
        'lamp': kit.lamp,
        'marker': material('Drive marker lamp', (.4, .15, .02), 0, .4, emission=(1.0, .42, .08), strength=8.0),
        'red': material('Drive stern lamp red', (.3, .03, .03), 0, .4, emission=(1.0, .08, .05), strength=6.0),
        'green': material('Drive stern lamp green', (.03, .3, .06), 0, .4, emission=(.1, 1.0, .25), strength=6.0),
    }
    # Drive block: the stern machinery the drive units are sunk into (owned here, not by ts_hull,
    # so the whole drive section the boarded state shoots out lives in one module).
    plan = H.plan(BLOCK_PLAN)
    drive = prism(kit, 'Hull drive block', plan, section(H.DRIVE_KEEL, H.DRIVE_DECK, 3, 3), kit.grey)
    v0 = _volume(drive)
    # Plating seams: two transverse joints that girdle the block, and a stratum round its walls
    # and stern face, all 0.3 units deep.
    seams = [(xs-.2, xs+.2, -50, 50, -20, 20) for xs in (-59.0, -66.5)]
    seams.append((-40, -80, -50, 50, -.1, .3))
    _shell_grooves(kit, drive, plan, H.DRIVE_KEEL, H.DRIVE_DECK, 3, 3, seams, .3, kit.dark)
    print('DRIVE BLOCK volume change', round((_volume(drive)/v0-1)*100, 3), '%')
    kit.hull.append(drive)

    for i, (x, yy, w, a) in enumerate(ENGINES, 1):
        z = _z(w)
        r = w/2
        main = w >= 20
        # Drive can: from inside the block out to the bell's throat.
        x0 = H.DRIVE_FRONT-6 if main else -64.0
        xt = x+4.0                       # throat plane, ahead of the slot
        revolve_x(kit, f'Engine {i} drive can', [(x0, r*.92), (xt-2, r*.92), (xt-.5, r), (xt, r*.95),
                                               (xt, .01), (x0, .01)], yy, z, kit.copper if main else kit.rust,
                  n=48 if main else 20)
        # Aft-facing bell: a flared wall from the throat to the slot's mouth.
        wl = .35 if main else .2
        us = [k/8 for k in range(9)]
        bell = [(xt+(x-xt)*u, _bell_r(r, u)+wl) for u in reversed(us)]+[(xt+(x-xt)*u, _bell_r(r, u)) for u in us]
        revolve_x(kit, f'Engine {i} aft-facing bell', bell, yy, z, kit.steel, n=40 if main else 20)
        revolve_x(kit, f'Engine {i} mouth lip', [(x+.25, r*.9), (x+.25, r), (x-.35, r), (x-.35, r*.9)],
                  yy, z, mats['bright'], n=40 if main else 20)
        revolve_x(kit, f'Engine {i} dark throat', [(xt-.4, r*.6), (xt-.4, .01), (xt+.5, .01), (xt+.5, r*.6)],
                  yy, z, kit.dark, n=40 if main else 20)
        (_main if main else _aux)(kit, i, x, yy, z, r, mats)
        kit.empty(f'Engine {i} exhaust anchor', (x-.4, yy, z), a)
        ANCHORS[i] = ((x-.4, yy, z), (-1.0, 0.0, 0.0))
    # The auxiliaries hang in a skirt under the mains' cans.
    splan = H.plan([(-62.0, 31.0), (-70.0, 30.0), (-72.5, 28.5)])
    skirt = prism(kit, 'Hull drive skirt', splan, section(AUX_Z-5.0, H.DRIVE_KEEL+1, 2, .5), kit.grey)
    sseams = [(-80, -71.0, ys-.2, ys+.2, -30, 0) for ys in (H.YC, H.y(18.5, 1), H.y(18.5, -1))]
    sseams += [(-66.8, -67.2, -50, 50, -30, 0)]
    _shell_grooves(kit, skirt, splan, AUX_Z-5.0, H.DRIVE_KEEL+1, 2, .5, sseams, .3, kit.dark)
    _skirt(kit, mats)
    _spine(kit, mats)
    _deck_machinery(kit, mats)
    _stern_face(kit, mats)


def _skirt(kit, mats):
    """Skirt flank access panels and the keel shoe under the auxiliaries."""
    for side, s in SIDES:
        p = Parts()
        yw = H.y(30.55, s)
        p.box(-63.0, -66.3, yw-s*.1, yw+s*.3, AUX_Z-3.4, AUX_Z+.4)
        p.box(-67.7, -69.6, yw-s*.1, yw+s*.3, AUX_Z-3.4, AUX_Z+.4)
        _bolts(p, [(xx, yw+s*.3, zz) for xx in (-63.4, -65.9, -68.1, -69.2) for zz in (AUX_Z-3.0, AUX_Z)],
               (0, s, 0), .13, .14)
        p.build(kit, f'Drive skirt {side} access panel', kit.steel)
    k = Parts()
    k.box(-64, -72.0, H.YC-14, H.YC+14, AUX_Z-5.6, AUX_Z-4.9)
    for ys in (-12.0, -4.0, 6.0, 14.0):
        k.box(-64.5, -71.5, ys-.4, ys+.4, AUX_Z-6.0, AUX_Z-5.5)
    k.build(kit, 'Drive skirt keel shoe', kit.steel)


def _spine(kit, mats):
    """The grey machinery spine between the cans, its radiator slats, and the stern service housing."""
    yc = H.YC
    sp = Parts()
    sp.slab([(-59.5, yc-5.0, DECK_Z-.3), (-59.5, yc+5.5, DECK_Z-.3), (-59.5, yc+4.6, DECK_Z+2.2),
             (-59.5, yc-4.1, DECK_Z+2.2)], (-11.0, 0, 0))
    sp.build(kit, 'Drive spine casing', kit.grey, .08)
    fins = Parts()
    for k in range(9):
        xf = -60.6-k*1.05
        fins.box(xf-.18, xf+.18, yc-3.6, yc+3.9, DECK_Z+2.1, DECK_Z+3.2)
    fins.box(-60.2, -69.6, yc-3.9, yc-3.5, DECK_Z+2.1, DECK_Z+3.3)
    fins.box(-60.2, -69.6, yc+3.8, yc+4.2, DECK_Z+2.1, DECK_Z+3.3)
    fins.build(kit, 'Drive spine radiator slats', mats['fin'])
    # Stern service housing: the spine's aft end, overhanging the stern face between the bells.
    hs = Parts()
    hs.slab([(-70.0, yc-4.5, -6.5), (-70.0, yc+5.0, -6.5), (-70.0, yc+5.0, 7.5), (-70.0, yc+3.8, 8.8),
             (-70.0, yc-3.3, 8.8), (-70.0, yc-4.5, 7.5)], (-4.4, 0, 0))
    hs.build(kit, 'Drive stern service housing', kit.grey, .1)
    xa = -74.4
    cv = Parts()
    cv.box(xa, xa-.3, yc-3.6, yc+1.6, 1.0, 6.8)
    _bolts(cv, [(xa-.3, yc+dy, zz) for dy in (-3.2, 1.2) for zz in (1.4, 4.0, 6.4)], (-1, 0, 0))
    cv.build(kit, 'Drive stern service cover upper', mats['pale'])
    cv = Parts()
    cv.box(xa, xa-.3, yc-3.6, yc+1.6, -5.8, -.2)
    _bolts(cv, [(xa-.3, yc+dy, zz) for dy in (-3.2, 1.2) for zz in (-5.4, -.6)], (-1, 0, 0))
    cv.build(kit, 'Drive stern service cover lower', kit.steel)
    # Ladder up the housing's aft face to a gantry on its top, with a hand-rail round the gantry.
    ld = Parts()
    ly = yc+3.3
    for dy in (-.9, .9):
        _rod(ld, (xa-.9, ly+dy, -6.2), (xa-.9, ly+dy, 10.4), .1, 6)
        for zz in (-6.0, 0.0, 6.0):
            _rod(ld, (xa+.05, ly+dy, zz), (xa-.9, ly+dy, zz), .08, 6)
    zr = -5.6
    while zr < 9.0:
        _rod(ld, (xa-.9, ly-.9, zr), (xa-.9, ly+.9, zr), .07, 6)
        zr += .6
    ld.build(kit, 'Drive stern ladder', mats['bright'])
    gt = Parts()
    gt.box(-71.0, -75.0, yc-4.0, yc+4.5, 8.8, 9.1)
    for k in range(9):
        gt.box(-71.2, -74.8, yc-3.8+k*1.0, yc-3.6+k*1.0, 8.6, 8.8)
    for px, py in [(-74.8, yc-3.8), (-74.8, yc+1.4), (-71.2, yc-3.8)]:
        _rod(gt, (px, py, 9.1), (px, py, 11.0), .08, 6)
    for zz, rr in ((11.0, .08), (10.1, .06)):
        _rod(gt, (-71.2, yc-3.8, zz), (-74.8, yc-3.8, zz), rr, 6)
        _rod(gt, (-74.8, yc-3.8, zz), (-74.8, yc+1.4, zz), rr, 6)
    gt.build(kit, 'Drive stern gantry', mats['bright'])


def _deck_machinery(kit, mats):
    """Turbopumps, manifolds and feed lines flanking the spine; radiator banks and auxiliary
    drive housings on the outboard deck; RCS quads; the intake grille and the orange marker lamp."""
    yc = H.YC
    for side, s in SIDES:
        main_y = 19 if s > 0 else -18
        # Turbopump: an upright pump casing on a plinth between the spine and the can.
        py = yc+s*8.6
        z0 = _deck(kit, -63.0, py)-.2
        tp = Parts()
        tp.box(-60.0, -66.5, py-s*1.8, py+s*1.8, z0, z0+.8)
        _rod(tp, (-61.6, py, z0+.6), (-61.6, py, z0+3.4), 1.45, 12)
        _rod(tp, (-61.6, py, z0+3.4), (-61.6, py, z0+3.9), 1.0, 12)
        _rod(tp, (-64.9, py, z0+.6), (-64.9, py, z0+2.6), 1.1, 12)
        tp.box(-60.3, -66.2, py-s*.6, py+s*.6, z0+.6, z0+1.6)
        tp.build(kit, f'Drive turbopump {side}', mats['oxide' if s > 0 else 'pale'])
        # Manifold: a fat header along the can's inboard flank, raised over the yokes' feet.
        my = main_y-s*6.6
        mz = DECK_Z+1.6
        mf = Parts()
        _rod(mf, (-58.6, my, mz), (-70.0, my, mz), .85, 12)
        for xs in (-59.5, -65.3, -70.0):
            mf.box(xs-.4, xs+.4, my-.7, my+.7, DECK_Z-.2, mz)
        mf.build(kit, f'Drive manifold {side}', kit.steel)
        fl = Parts()
        for xs, zz in ((-61.0, 3.0), (-64.9, 2.0)):
            pts = [(xs, py, z0+zz), (xs, py+s*1.6, z0+zz), (xs, my-s*.6, mz+.6), (xs, my, mz)]
            for p, q in zip(pts, pts[1:]):
                _rod(fl, p, q, .32, 8)
        for xs in (-64.5, -69.3):                    # manifold into the can top
            cz = math.sqrt(max(0, 9.4**2-(my+s*2.2-main_y)**2))+MAIN_Z
            _rod(fl, (xs, my, mz), (xs, my+s*2.2, cz-.2), .3, 8)
        fz = _deck(kit, -53.5, py)                    # propellant feed from the body onto the pump
        for dy in (-.7, .7):
            _rod(fl, (-53.5, py+dy, fz+.4), (-60.4, py+dy, z0+1.9), .26, 8)
        fl.build(kit, f'Drive feed lines {side}', mats['bright'])
        # Radiator bank: the sprite's pale grey panels on the outboard deck.
        y0, y1 = H.y(24.5, s), H.y(32.0, s)
        rz = DECK_Z
        rb = Parts()
        rb.box(-56.8, -62.2, y0, y1, rz-.3, rz+.7)
        rb.build(kit, f'Drive radiator bank {side}', mats['pale'], .06)
        rf = Parts()
        n = 9
        for k in range(n):
            yf = y0+(y1-y0)*(k+.5)/n
            rf.box(-57.1, -61.9, yf-.12, yf+.12, rz+.6, rz+2.0)
        rf.box(-56.9, -57.3, y0, y1, rz+.6, rz+2.1)
        rf.box(-61.7, -62.1, y0, y1, rz+.6, rz+2.1)
        rf.build(kit, f'Drive radiator bank {side} fins', mats['fin'])
        # Auxiliary drive housing: the sprite's red-oxide box outboard of the can, aft of the radiators.
        a0, a1 = H.y(26.2, s), H.y(31.0, s)
        az = DECK_Z
        ah = Parts()
        ah.slab([(-63.0, a0, az-.3), (-63.0, a1, az-.3), (-63.0, a1, az+2.4), (-63.0, a1-s*.8, az+3.0),
                 (-63.0, a0, az+3.0)], (-6.6, 0, 0))
        ah.build(kit, f'Drive auxiliary housing {side}', mats['oxide'], .08)
        hc = Parts()
        hc.box(-64.0, -68.8, a0+s*.5, a1-s*1.3, az+3.0, az+3.25)
        _bolts(hc, [(xx, yy, az+3.25) for xx in (-64.4, -68.4) for yy in (a0+s*.85, a1-s*1.65)], (0, 0, 1))
        for k in range(4):                            # louvre slots on the outboard face
            zz = az+.4+k*.5
            hc.box(-64.0, -68.6, a1, a1+s*.18, zz, zz+.22)
        hc.build(kit, f'Drive auxiliary housing {side} cover', kit.steel)
        # RCS quads on the deck's aft outboard corners and under the skirt's aft corners.
        cy = H.y(29.0, s)
        _rcs_quad(kit, f'Drive RCS {side} dorsal', -71.4, cy, _deck(kit, -71.4, cy)-.25, s, 1, mats)
        ky = H.y(26.5, s)
        _rcs_quad(kit, f'Drive RCS {side} ventral', -70.2, ky, AUX_Z-5.0+.2, s, -1, mats)
    # Intake grille: dark slats across the deck forward of the spine (the sprite's grilles).
    gr = Parts()
    for k in range(5):
        xg = -56.9-k*.55
        gr.box(xg-.16, xg+.16, yc-4.5, yc+5.0, DECK_Z-.2, DECK_Z+.45)
    gr.box(-56.6, -59.3, yc-4.9, yc-4.5, DECK_Z-.2, DECK_Z+.55)
    gr.box(-56.6, -59.3, yc+5.0, yc+5.4, DECK_Z-.2, DECK_Z+.55)
    gr.build(kit, 'Drive intake grille', mats['fin'])
    # The sprite's orange marker at X -53, Y -8.
    mz = _deck(kit, -53.3, -8.0)
    _lamp(kit, 'Drive deck marker lamp', (-53.3, -8.0, mz+.45), (0, 0, 1), mats, 'marker', .55)


def _stern_face(kit, mats):
    """Armour plates and bolted covers on the stern face outboard of the bells, and running lamps."""
    xs = STERN_X
    for side, s in SIDES:
        main_y = 19 if s > 0 else -18
        y0 = main_y+s*10.9
        y1 = H.y(32.4, s)
        pl = Parts()
        pl.box(xs+.05, xs-.4, y0, y1, 0.5, 5.6)
        pl.box(xs+.05, xs-.4, y0, y1, -6.6, -.2)
        _bolts(pl, [(xs-.4, yy, zz) for yy in (y0+s*.45, y1-s*.45) for zz in (.9, 5.2, -6.2, -.6)], (-1, 0, 0))
        pl.build(kit, f'Hull drive block stern plate {side}', kit.grey)
        # Running lamps: red to port, green to starboard.
        _lamp(kit, f'Drive stern lamp {side}', (xs-.6, (y0+y1)/2, -3.4), (-1, 0, 0), mats,
              'red' if s > 0 else 'green', .38)
    _lamp(kit, 'Drive stern lamp white', (-74.9, H.YC-1.0, 7.7), (-1, 0, 0), mats, 'lamp', .34)
    _lamp(kit, 'Drive skirt stern lamp', (-73.0, H.YC, AUX_Z-3.4), (-1, 0, 0), mats, 'lamp', .3)
