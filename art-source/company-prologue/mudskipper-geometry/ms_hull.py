"""Mudskipper pressure hull: forebody, waist, aft hull, sponsons and flanks.

Planform is measured off the vanilla sprite (graphics/ships/mudskipper/
mudskipper.png, 64x84, hull centre at sprite pixel 32,39 from bottom-left):
a broad forebody with sponsons (+-28.5 over x 21..33), a narrower cargo
waist (+-22.5 over x 0..19), and a broad aft hull (+-28.5) carrying the
drive units built by ms_engines. Sections are one keel chamfer, one vertical
wall and one shoulder chamfer; decks are flat and step between blocks.
Heights are art interpretation: the sprite supplies only the plan.
The cockpit is ms_bridge; dorsal fittings on the decks are ms_deck.
"""
import math
from ms_kit import prism, section, boolean, facet, plane_cut, crisp, material

SIDES = ((1, 'Port'), (-1, 'Starboard'))

def clip(poly, a, b, c):
    """Keep the part of a plan polygon where a*x+b*y <= c (Sutherland-Hodgman)."""
    out = []
    for i in range(len(poly)):
        p, q = poly[i-1], poly[i]
        fp, fq = a*p[0]+b*p[1]-c, a*q[0]+b*q[1]-c
        if fq <= 0:
            if fp > 0:
                t = fp/(fp-fq)
                out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
            out.append(q)
        elif fp <= 0:
            t = fp/(fp-fq)
            out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
    return [p for i,p in enumerate(out) if abs(p[0]-out[i-1][0])+abs(p[1]-out[i-1][1]) > 1e-6]


KEEL = -8.5
FORE_DECK, WAIST_DECK, AFT_DECK = 9.0, 8.5, 9.5
AFT_FACE = -16.0     # aft hull ends here; the drive units bolt on behind it

FORE_PLAN = [(18.5,-22.5),(21,-27.5),(33,-28.5),(39,-24),(39.5,-20),
             (39.5,20),(39,24),(33,28.5),(21,27.5),(18.5,22.5)]
WAIST_PLAN = [(-2,-22.5),(19.5,-22.5),(19.5,22.5),(-2,22.5)]
AFT_PLAN = [(AFT_FACE,-28.5),(-4,-28.5),(-1,-27),(-1,27),(-4,28.5),(AFT_FACE,28.5)]
# Sponson plan (port; mirrored), the forebody outline outboard of y 21.5
# with no acute corners, so its chamfer insets cannot cross over.
SPONSON_PLAN = [(19,21.5),(19.5,23.5),(21,27.5),(33,28.5),(39,24),(39.3,21.5)]
# Forward end of each stripe (port; mirrored): from y 16..19.5 at x 28 it
# angles in to y 8.5..12 at x 35, beside the cockpit (y +-8).
STRIPE_BEND = [(28,16),(28,19.5),(35,12),(35,8.5)]
# The forebody core is the waist width carried forward; the sponsons outboard
# of it (ms_hull.build_forebody) make up the sprite's full +-28.5 shoulder.
FORE_CORE = clip(clip(FORE_PLAN, 0, 1, 22.5), 0, -1, 22.5)
COCKPIT_PLAN = [(26,-8),(39,-8),(42.5,-4.5),(42.5,4.5),(39,8),(26,8)]
HOOD_PLAN = [(6,-8.5),(25,-8.5),(25,8.5),(6,8.5)]

# Deck plateaus aft to fore for the manifest: (x0, x1, deck z).
DECKS = [(AFT_FACE,-1,AFT_DECK),(-2,19.5,WAIST_DECK),(18.5,39.5,FORE_DECK),
         (6,25,13.0),(26,42.5,12.0)]


def build_shell(kit):
    hull = prism(kit, 'Main hull - level datum Z0', AFT_PLAN, section(KEEL, 5, AFT_DECK), kit.plate)
    for name, plan, prof in [('Cargo waist block', WAIST_PLAN, section(KEEL, 4.5, WAIST_DECK, 2.5, 2)),
                             ('Forebody core block', FORE_CORE, [(2.5,KEEL),(0,KEEL+2.5),(0,FORE_DECK)])]:
        boolean(hull, prism(kit, name, plan, prof, kit.plate), 'UNION')
    # The forebody rakes at the bow: a glacis over the nose and a chin under it.
    for name, point, normal in [facet('Bow glacis', (37,0,FORE_DECK), (1,0), 30),
                                facet('Chin rake', (36,0,KEEL), (1,0), 145)]:
        plane_cut(hull, name, point, normal)
    crisp(hull, .25)
    kit.hull = hull
    return hull


def build_forebody(kit):
    # Sponsons: the grey-green outer band of the forebody, hung on the core
    # and stepped below its deck so the shoulder reads as a separate stratum.
    for s, label in ((1,'Port'),(-1,'Starboard')):
        plan = [(x,s*y) for x,y in SPONSON_PLAN]
        sp = prism(kit, f'{label} forebody sponson', plan, section(KEEL+1.5, 5, 7, 1.5, 1.5, proud=.3), kit.sponson)
        plane_cut(sp, 'Sponson chin', *facet('Sponson chin', (36,0,KEEL+1.5), (1,0), 145)[1:])
        crisp(sp)


def _materials(kit):
    """Hull-detail materials, created once per build and cached on the kit."""
    if getattr(kit, 'hull_detail_ready', False):
        return
    kit.hull_detail_ready = True
    kit.hull_port_light = material('Hull port running light', (.55,.03,.02), .2, .3, emission=(1,.06,.04))
    kit.hull_starboard_light = material('Hull starboard running light', (.03,.45,.1), .2, .3, emission=(.08,1,.2))
    kit.hull_hazard = material('Bay sill hazard tread', (.52,.40,.07), .25)


def _frame(p0, p1, t):
    """Point at t along a plan edge, its outward normal and its bearing.
    The edge is reversed when needed so its left normal faces outboard."""
    (x0,y0),(x1,y1) = p0, p1
    dx, dy = x1-x0, y1-y0
    length = math.hypot(dx, dy)
    nx, ny = -dy/length, dx/length
    px, py = x0+dx*t, y0+dy*t
    if (px-10)*nx + py*ny < 0:
        nx, ny, dx, dy = -nx, -ny, -dx, -dy
    return px, py, nx, ny, math.atan2(dy, dx), length


def plate(kit, name, p0, p1, t, out, z, along, across, thick, mat, elev=0, lift=None, bevel=0):
    """A plate lying on a hull face. The face runs along plan edge p0-p1 and
    its normal rises `elev` degrees above the outboard horizontal (0 a wall,
    -45 a keel chamfer, 90 a deck). (out, z) is the surface point, `out`
    measured outboard of the edge; the plate stands `lift` off it."""
    px, py, nx, ny, ang, _ = _frame(p0, p1, t)
    e = math.radians(elev)
    lift = thick/2 if lift is None else lift
    h = out+lift*math.cos(e)
    ob = kit.box(name, (px+nx*h, py+ny*h, z+lift*math.sin(e)), (along, across, thick), mat, 0)
    ob.rotation_euler = (e-math.pi/2, 0, ang)
    if bevel:
        crisp(ob, bevel)
    return ob


def _wall(kit, s, wall_y):
    """Plate placer for a straight flank wall at |y| = wall_y, indexed by x."""
    def place(name, x, z, along, height, thick, mat, out=0, lift=None, bevel=0, elev=0):
        return plate(kit, name, (x-1,s*wall_y), (x+1,s*wall_y), .5, out, z,
                     along, height, thick, mat, elev, lift, bevel)
    return place


def _running_light(kit, label, s, p0, p1, t, out, z):
    lens = kit.hull_port_light if s > 0 else kit.hull_starboard_light
    plate(kit, f'{label} running light housing', p0, p1, t, out, z, .9, .8, .18, kit.steel, bevel=.04)
    plate(kit, f'{label} running light', p0, p1, t, out+.18, z, .65, .55, .05, lens)


def build_bay_doors(kit):
    """The codex's generous bay doors: on each waist flank a two-leaf sliding
    cargo door hung from an overhead track, between plate jambs on a steel
    coaming, with a crew hatch aft of it, work lights on the track, and the
    sprite's dark slots (y +-21) as skylights in the shoulder above."""
    for s, label in SIDES:
        w = _wall(kit, s, 22.5)
        w(f'{label} cargo bay door frame', 10, -.55, 16.8, 8.4, .25, kit.dark, out=-.1)
        for x, leaf, handle in ((6.175,'aft',9.35),(13.825,'forward',10.65)):
            w(f'{label} cargo bay door {leaf} leaf', x, -.55, 7.35, 7.7, .3, kit.plate, out=.15, bevel=.08)
            for z in (-2.4, 1.1):
                w(f'{label} cargo bay door stiffener', x, z, 6.7, .4, .12, kit.plate, out=.45, bevel=.04)
            w(f'{label} cargo bay door handle', handle, -.6, .22, 2.2, .12, kit.steel, out=.45)
            for dx in (-2.6, 2.6):
                kit.cylinder(f'{label} cargo bay door roller', (x+dx, s*23.05, 3.35), .36, .2,
                             kit.steel, axis='Y', vertices=12, bevel=0)
        for x in (1.95, 18.05):
            w(f'{label} cargo bay door jamb', x, -.5, .9, 9.6, .65, kit.plate, bevel=.1)
        w(f'{label} cargo bay door sill', 10, -4.85, 17.0, .9, .65, kit.steel, bevel=.08)
        w(f'{label} cargo bay sill hazard', 10, -4.85, 15.2, .45, .04, kit.hull_hazard, out=.65)
        w(f'{label} cargo bay door track', 9.15, 3.85, 19.3, .8, .65, kit.steel, bevel=.06)
        for x in (2.2, 17.0):
            w(f'{label} bay work light housing', x, 4.55, 1.0, .6, .55, kit.steel, out=.05, bevel=.05)
            w(f'{label} bay work light', x, 4.5, .8, .4, .05, kit.lamp, out=.6)
        # Crew hatch aft of the cargo door, at the bay floor.
        w(f'{label} crew hatch frame', .25, -3.05, 1.7, 3.1, .25, kit.dark, out=-.1)
        w(f'{label} crew hatch', .25, -3.05, 1.35, 2.75, .25, kit.plate, out=.1, bevel=.05)
        w(f'{label} crew hatch port', .25, -2.3, .55, .55, .04, kit.glass, out=.35)
        w(f'{label} crew hatch handle', .7, -3.2, .15, .7, .1, kit.steel, out=.35)
        w(f'{label} crew hatch step', .25, -4.85, 1.7, .3, .6, kit.steel, bevel=.05)
        w(f'{label} crew hatch lamp', .25, -1.15, .7, .3, .25, kit.lamp)
        # Bay skylights on the shoulder chamfer (the sprite's dark slots).
        for x in (4, 8, 12, 16):
            w(f'{label} bay skylight frame', x, 6.5, 1.3, 1.9, .14, kit.dark, out=-1, elev=26.57)
            w(f'{label} bay skylight', x, 6.5, .9, 1.4, .06, kit.glass, out=-1, elev=26.57, lift=.17)
        # Keel strake along the waist's keel chamfer.
        w(f'{label} waist keel strake', 9.0, -7.25, 19.6, 1.6, .3, kit.steel, out=-1.25, elev=-45, bevel=.06)


def build_aft_flanks(kit):
    """Aft hull flanks: the tan flank lockers (sprite x -13.5..-4.5, with red
    marks) as two-door bins with latches and hinges, segmented armour
    modules beneath them, a keel strake and a running light."""
    for s, label in SIDES:
        kit.box(f'{label} flank locker', (-9,s*27.4,2), (9,3.2,8), kit.tan, .2)
        lk = _wall(kit, s, 29.0)
        lk(f'{label} flank locker door seam', -9, 2, 8.6, 7.4, .08, kit.dark, out=-.02)
        for x, latch, hinge in ((-11.2,-9.6,-13.05),(-6.8,-8.4,-4.95)):
            lk(f'{label} flank locker door', x, 2, 4.1, 7.0, .15, kit.tan, out=.03, bevel=.04)
            lk(f'{label} flank locker latch', latch, 2.2, .3, .9, .2, kit.steel, out=.18)
            for z in (-.3, 4.3):
                lk(f'{label} flank locker hinge', hinge, z, .3, .8, .25, kit.steel, out=.1)
            lk(f'{label} flank locker placard', x, 4.6, 2.2, .5, .04, kit.stripe, out=.18)
            lk(f'{label} flank locker placard', x, 6.0, 2.2, .7, .05, kit.stripe, out=-.5, elev=90)
        w = _wall(kit, s, 28.5)
        w(f'{label} aft flank seam backing', -9.8, -4.0, 11.2, 3.8, .1, kit.dark, out=-.02)
        for x, length in ((-12.65,5.3),(-6.95,5.3)):
            w(f'{label} aft flank armour module', x, -4.0, length, 3.4, .25, kit.plate, out=.05, bevel=.06)
        w(f'{label} aft keel strake', -9.8, -7.25, 11.2, 1.6, .3, kit.steel, out=-1.25, elev=-45, bevel=.06)
        _running_light(kit, f'{label} aft', s, (-4,s*28.5), (-1,s*27), .5, 0, 2.0)


def build_sponson_detail(kit):
    """Sponsons (sprite x 20..38, grey-green, dark square ports, light inner
    trim): armour modules on the outer face over a dark seam, a steel belt,
    a row of passenger ports (the hull is a liner), dorsal vents, the trim
    edge and the navigation light at the widest point."""
    for s, label in SIDES:
        C, D, E = (21,s*27.5), (33,s*28.5), (39,s*24)
        cd = math.hypot(12, 1)
        plate(kit, f'{label} sponson seam backing', C, D, .5, .3, -2.25, 11.4, 5.3, .08, kit.dark, lift=-.02)
        for a, b in ((.05,.35),(.375,.645),(.67,.96)):
            plate(kit, f'{label} sponson armour module', C, D, (a+b)/2, .3, -2.3, (b-a)*cd, 5.0, .2,
                  kit.sponson, bevel=.05)
        plate(kit, f'{label} sponson belt', C, D, .5, .3, .85, 11.4, .9, .2, kit.steel, bevel=.05)
        plate(kit, f'{label} sponson belt', D, E, .45, .3, .85, 6.4, .9, .2, kit.steel, bevel=.05)
        plate(kit, f'{label} sponson seam', D, E, .55, .3, -.5, .25, 10.0, .06, kit.dark)
        for t in (.22, .42, .62, .82):
            plate(kit, f'{label} sponson passenger port frame', C, D, t, .3, 2.9, 1.3, 1.2, .1, kit.dark)
            plate(kit, f'{label} sponson passenger port', C, D, t, .4, 2.9, .95, .85, .04, kit.glass)
        plate(kit, f'{label} sponson keel strake', C, D, .5, -.45, -6.25, 11.0, 1.5, .25, kit.steel,
              elev=-45, bevel=.05)
        _running_light(kit, f'{label} sponson', s, D, E, .1, .3, 3.2)
        # Dorsal face of the sponson, one stratum below the deck.
        kit.box(f'{label} sponson trim', (29,s*22.9,7.05), (17,.8,.15), kit.plate, .03)
        kit.box(f'{label} sponson top seam', (29.5,s*24.7,7.02), (.3,3.8,.06), kit.dark, 0)
        for x in (25.5, 33.5):
            kit.box(f'{label} sponson dorsal vent', (x,s*25,7.0), (2.6,2.4,.2), kit.dark, .03)
            for dy in (-.7, 0, .7):
                kit.box(f'{label} sponson dorsal vent slat', (x,s*(25+dy),7.12), (2.2,.25,.12), kit.steel, 0)
        # The forebody core's flank above the sponson: deck-edge lip and seams.
        kit.box(f'{label} forebody deck-edge lip', (28,s*22.6,9.05), (17,.8,.5), kit.plate, .06)
        for x in (24, 32):
            kit.box(f'{label} forebody flank seam', (x,s*22.54,8.0), (.25,.1,1.6), kit.dark, 0)
        # Bow face of the forebody core above the fenders (x 39.5, y 8..20).
        bf = (39.5,s*21), (39.5,s*7)
        plate(kit, f'{label} bow face seam backing', *bf, .5, 0, 5.1, 11.6, 3.8, .08, kit.dark, lift=-.02)
        for t, length in ((.25,5.3),(.72,5.6)):
            plate(kit, f'{label} bow face armour module', *bf, t, 0, 5.1, length, 3.4, .22, kit.plate, bevel=.05)


def build_underside(kit):
    """Keel plating over dark seams, a spine, four landing pads for a ferry
    that sets down on backwater fields, drain grilles and the bow chin."""
    for x0, x1, ymax, n in ((-13.2,-4.3,25.0,2), (.9,16.6,19.4,3), (21.4,35.6,19.4,3)):
        cx = (x0+x1)/2
        kit.box('Keel plate seam backing', (cx,0,KEEL-.04), (x1-x0,2*ymax,.08), kit.dark, 0)
        kit.box('Keel spine', (cx,0,KEEL-.4), (x1-x0+.4,3.0,.7), kit.steel, .1)
        step = (x1-x0)/n
        mid = (1.7+ymax)/2
        for i in range(n):
            px = x0+step*(i+.5)
            for ya, yb in ((1.7,mid),(mid,ymax)):
                for s in (1,-1):
                    kit.box('Keel armour plate', (px,s*(ya+yb)/2,KEEL-.17),
                            (step-.35,yb-ya-.35,.18), kit.plate, .05)
    for x, y in ((29,12),(29,-12),(-9,17),(-9,-17)):
        kit.box('Ventral landing pad well', (x,y,KEEL-.32), (4.2,4.2,.1), kit.dark, 0)
        kit.box('Ventral landing pad housing', (x,y,KEEL-.75), (2.6,2.6,.9), kit.steel, .1)
        kit.cylinder('Ventral landing pad strut', (x,y,KEEL-1.5), .6, .8, kit.steel, vertices=12, bevel=0)
        kit.box('Ventral landing pad foot', (x,y,KEEL-2.15), (4.4,3.6,.5), kit.steel, .15)
    for x, y in ((8.5,6),(8.5,-6),(-6.5,8),(-6.5,-8),(25,6),(25,-6)):
        kit.box('Ventral drain grille', (x,y,KEEL-.32), (3.0,1.4,.12), kit.dark, 0)
        for dy in (-.4, 0, .4):
            kit.box('Ventral drain grille slat', (x,y+dy,KEEL-.4), (2.6,.22,.1), kit.steel, 0)
    # Bow chin: the 35 degree rake under the nose (x 36..39.5).
    chin_z = KEEL+1.75*math.tan(math.radians(35))
    a, b = (37.75,18), (37.75,-18)
    for y in (4.75, -4.75):
        plate(kit, 'Bow chin armour plate', a, b, (18-y)/36, 0, chin_z, 8.5, 3.0, .25, kit.steel,
              elev=-55, bevel=.06)
    for y in (13, -13):
        plate(kit, 'Bow chin landing light housing', a, b, (18-y)/36, 0, chin_z, 1.6, 1.2, .25, kit.steel,
              elev=-55, bevel=.04)
        plate(kit, 'Bow chin landing light', a, b, (18-y)/36, 0, chin_z, 1.2, .8, .06, kit.lamp,
              elev=-55, lift=.28)


def build_flanks(kit):
    _materials(kit)
    build_bay_doors(kit)
    build_aft_flanks(kit)
    build_sponson_detail(kit)
    build_underside(kit)


def build(kit):
    build_shell(kit)
    build_forebody(kit)
    build_flanks(kit)
