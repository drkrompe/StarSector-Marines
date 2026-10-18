"""Shepherd drone bay: the port open work bays, their three WS 003 launch
stations, the forward workshop block, and the port outrigger frame.

Read off drone_tender.png: the port third of the hull (Y 11..27) is dark
grey working space EXPOSED out of the hull - two bays end to end, a forward
one X 8..33 and an aft one X -13..6 - with a cyan-lit inboard edge and amber
lamps at the joins. A dark service block caps the forward end (X 26..34):
the workshop end. A blue ring on the forward bay floor (X ~18) is a
servicing turntable. The bay is the hidden LAUNCH_BAY slot WS 003, which the
game gives three launch locations at (-3, 19), (12, 19) and (26, 19),
facing 0 deg: those are the three launch stations, one Borer drone each out
of the wing of four (the fourth is serviced on the turntable). The station
sizes come from the Borer, not the sprite (see build_shepherd.DRONE_BAY).

Shape (thick envelope, ENVELOPE.md): each bay is a tub, the closed solid
'Drone bay aft|forward hangar pod' (an elliptic pod, z -13..+8) with a
recess cut out of it, open dorsally and above a low outboard coaming. The
recess floor is the bay deck (WELL_FLOOR); its inboard side is a tall
panelled back wall whose top chamfer carries the sprite's cyan glow. The
skin that was cut away survives as framing: arch ribs over the open top,
carried on below the coaming as exterior frame hoops. Ribs stand only
between stations, so the space above each station is clear to the sky.
The join between the bays is a solid bulkhead (the pods' necks) with a
bolted collar carrying the amber join lamps; the forward bay runs into the
service block, whose aft face is the workshop front.

Each station 'Drone bay WS 003 opening <i> ...': a darker station deck
plate ('well floor'), a low kerb round it with hazard bars ('coaming',
'coaming hazard bars'), four deck lights at its corners ('jamb light'), a
launch cradle on the bay's transfer rails, a charging contact, a servicing
arm folded against the back wall and a charging umbilical. 'mouth' is an
empty at the top of the bay opening above the station (normal +Z);
'launch point' is on the cradle deck. OPENINGS records rim_z (mouth),
floor_z (launch point) and clear_radius, the clear half-width above the
station to the nearest rib, bulkhead or wall.

Port outrigger: a four-chord box truss read off the sprite's three lattice
frames (forward X 11..31 to Y 31, mid X -6..5 with its outboard chord at
Y 29..31 behind a real gap, aft X -10..-6 at Y 28..29), joined by the two
continuous inboard chords ('upper rail' and 'lower rail'), with rubber
bumpers on the outboard face.

Names: 'Drone bay ...' and 'Frame port ...'.
"""
import math
import bpy
from mathutils import Vector
from sh_kit import pod, boolean, material

SLOT = {'id': 'WS 003', 'locations': [(-3, 19), (12, 19), (26, 19)], 'angle': 0, 'arc': 5,
        'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'LAUNCH_BAY'}
POD_Y, POD_Z, POD_R = 18.5, -2.5, 7.5
POD_RZ = 10.5
OPENING_R = 4.2      # minimum clear half-width above a station, units (the Borer's diagonal needs it)
WELL_FLOOR = -5.0    # bay deck height, units (tub crown about +7.6)
OPENINGS = []        # filled by build(): one record per station for geometry.json

N, PHASE = 12, math.pi/12          # pod facet count and phase (as sh_kit.pod)
AFT_ST = [(-13.5, 5.0, 7.0), (-11.5, POD_R, POD_RZ), (4.5, POD_R, POD_RZ), (7.0, 6.2, 8.7)]
FORE_ST = [(6.0, 6.2, 8.7), (8.5, POD_R, POD_RZ), (27.5, POD_R, POD_RZ)]
BAYS = {'aft': (-11.2, 3.8), 'forward': (7.6, 30.5)}   # recess X extents
WALL_Y = 12.4        # bay face of the inboard back wall
WALL_TOP = 8.0
COAMING_Y, COAMING_Z = 24.4, 0.0   # outboard coaming: inner face and top of the cut
RIBS = {'aft': (-10.8, -8.0, 1.8), 'forward': (17.0, 19.15, 21.3)}
RIB_W = .7
DECK = WELL_FLOOR+.12              # top of the deck plating


# --- private mesh helpers -------------------------------------------------

HEX_FACES = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def _hexes(kit, name, hexes, mat, bevel=0):
    """One mesh of closed eight-cornered solids (bottom ring then top ring)."""
    verts, faces = [], []
    for h in hexes:
        b = len(verts)
        verts += [tuple(v) for v in h]
        faces += [tuple(b+i for i in f) for f in HEX_FACES]
    return kit.mesh(name, verts, faces, mat, bevel)


def _box(c, d):
    (x, y, z), (dx, dy, dz) = c, d
    x0, x1, y0, y1, z0, z1 = x-dx/2, x+dx/2, y-dy/2, y+dy/2, z-dz/2, z+dz/2
    return [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
            (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]


def _beam(p0, p1, w, h=None):
    """Square-section member between two points."""
    p0, p1 = Vector(p0), Vector(p1)
    d = (p1-p0).normalized()
    up = Vector((0, 0, 1)) if abs(d.z) < .9 else Vector((0, 1, 0))
    s = d.cross(up).normalized()
    t = s.cross(d).normalized()
    h = w if h is None else h
    ring = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    return [p0+s*a*w/2+t*b*h/2 for a, b in ring]+[p1+s*a*w/2+t*b*h/2 for a, b in ring]


def _path(points, w, h=None):
    return [_beam(a, b, w, h) for a, b in zip(points, points[1:])]


def _prism_x(kit, name, poly, x0, x1, mat, bevel=0):
    """A YZ polygon extruded along X."""
    m = len(poly)
    verts = [(x0, y, z) for y, z in poly]+[(x1, y, z) for y, z in poly]
    faces = [tuple(range(m)), tuple(range(m, 2*m))]+[(j, (j+1) % m, m+(j+1) % m, m+j) for j in range(m)]
    return kit.mesh(name, verts, faces, mat, bevel)


def _rr(st, x):
    for (xa, ya, za), (xb, yb, zb) in zip(st, st[1:]):
        if xa <= x <= xb:
            t = (x-xa)/(xb-xa)
            return ya+(yb-ya)*t, za+(zb-za)*t
    e = st[0] if x < st[0][0] else st[-1]
    return e[1], e[2]


def _vert(st, x, a, d):
    ry, rz = _rr(st, x)
    return POD_Y+(ry+d)*math.cos(a), POD_Z+(rz+d)*math.sin(a)


def _facet(st, k, x0, x1, d_in, d_out, s0=0.0, s1=1.0):
    """A solid on pod facet k between x0 and x1, offset d_in..d_out from the skin,
    spanning the fraction s0..s1 of the facet's width."""
    a0, a1 = PHASE+2*math.pi*k/N, PHASE+2*math.pi*(k+1)/N

    def p(x, s, d):
        y0, z0 = _vert(st, x, a0, d)
        y1, z1 = _vert(st, x, a1, d)
        return (x, y0+(y1-y0)*s, z0+(z1-z0)*s)
    return [p(x0, s0, d_in), p(x1, s0, d_in), p(x1, s1, d_in), p(x0, s1, d_in),
            p(x0, s0, d_out), p(x1, s0, d_out), p(x1, s1, d_out), p(x0, s1, d_out)]


def _surface(objs, x, y, top=60):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in objs:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        hit, p, _, _ = ev.ray_cast(inv @ Vector((x, y, top)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if hit:
            z = (ev.matrix_world @ p).z
            best = z if best is None or z > best else best
    return best


# --- build ----------------------------------------------------------------

def build(kit):
    armour = material('Drone bay pod armour', (.13, .125, .115), .55, .55)
    deckmat = material('Drone bay deck plate', (.075, .075, .072), .5, .7)
    amber = material('Drone bay amber lamp', (.5, .3, .05), .1, .4, emission=(1.0, .55, .12), strength=8.0)
    lamp = material('Drone bay deck light', (.6, .55, .45), .1, .4, emission=(1.0, .82, .55), strength=6.0)
    hazard = material('Drone bay hazard yellow', (.62, .44, .04), .15, .5)
    ring_blue = material('Drone bay turntable ring blue', (.08, .17, .20), .45, .45, emission=(.2, .5, .65), strength=.6)
    rubber = material('Frame port fender rubber', (.025, .025, .025), 0, .9)

    # --- the bay tubs: closed solids with the open bay cut out of them ---
    aft = pod(kit, 'Drone bay aft hangar pod', [(x, ry, rz) for x, ry, rz in AFT_ST], POD_Y, POD_Z, kit.dark, 12)
    fore = pod(kit, 'Drone bay forward hangar pod', [(x, ry, rz) for x, ry, rz in FORE_ST], POD_Y, POD_Z, kit.dark, 12)
    block = kit.box('Drone bay forward service block', (30.75, 18.75, -2), (7.5, 15.5, 20), kit.dark, .3)
    kit.hull += [aft, fore, block]
    rims = {i: kit.surface(x, y)[0].z for i, (x, y) in enumerate(SLOT['locations'], 1)}
    recess = [(WALL_Y, WELL_FLOOR), (COAMING_Y, WELL_FLOOR), (COAMING_Y, COAMING_Z), (28.5, COAMING_Z+1.4),
              (28.5, 20), (WALL_Y, 20)]
    for bay, (x0, x1) in BAYS.items():
        for ob in ((aft,) if bay == 'aft' else (fore, block)):
            cut = _prism_x(kit, f'Temporary {bay} bay recess cutter', recess, x0, x1, kit.dark)
            boolean(ob, cut)

    # Inboard back wall: tall, panelled, its top chamfer lit cyan (the sprite's edge).
    wall_poly = [(10.8, WELL_FLOOR-.5), (WALL_Y, WELL_FLOOR-.5), (WALL_Y, WALL_TOP-1.3), (WALL_Y-.7, WALL_TOP),
                 (10.8, WALL_TOP)]
    x0w, x1w = BAYS['aft'][0], BAYS['forward'][1]
    _prism_x(kit, 'Drone bay inboard back wall', wall_poly, x0w, x1w, kit.steel, .06)
    panels = []
    for x0, x1 in BAYS.values():
        n = round((x1-x0)/3.4)
        for j in range(n):
            a, b = x0+(x1-x0)*j/n+.08, x0+(x1-x0)*(j+1)/n-.08
            for z0, z1 in ((WELL_FLOOR+.2, -.4), (-.25, 3.0), (3.15, WALL_TOP-1.5)):
                panels.append(_box(((a+b)/2, WALL_Y+.08, (z0+z1)/2), (b-a, .16, z1-z0)))
    _hexes(kit, 'Drone bay back wall armour panels', panels, armour)
    # Chamfer glow: the normal of the chamfer face points into the bay and up.
    ny, nz = 1.3/math.hypot(.7, 1.3), .7/math.hypot(.7, 1.3)

    def on_chamfer(x0, x1, s0, s1, d0, d1):
        def p(x, s, d):
            return (x, WALL_Y-.7*s+ny*d, WALL_TOP-1.3+1.3*s+nz*d)
        return [p(x0, s0, d0), p(x1, s0, d0), p(x1, s1, d0), p(x0, s1, d0),
                p(x0, s0, d1), p(x1, s0, d1), p(x1, s1, d1), p(x0, s1, d1)]
    glow, bezel = [], []
    for x0, x1 in ((-11.0, -7.0), (1.5, 4.8), (11.0, 23.6)):
        glow.append(on_chamfer(x0, x1, .2, .8, -.02, .1))
        glow.append(_box(((x0+x1)/2, WALL_Y+.2, WALL_TOP-2.2), (x1-x0, .1, .45)))      # bay-facing light band
        bezel += [on_chamfer(x0-.2, x1+.2, .06, .2, -.02, .2), on_chamfer(x0-.2, x1+.2, .8, .94, -.02, .2)]
        n = max(2, round((x1-x0)/1.6))
        bezel += [on_chamfer(x0+(x1-x0)*j/n-.12, x0+(x1-x0)*j/n+.12, .2, .8, -.02, .18) for j in range(n+1)]
    _hexes(kit, 'Drone bay inboard glow strip', glow, kit.bay_glow)
    _hexes(kit, 'Drone bay inboard glow channel bezel', bezel, kit.dark)
    # Tool and cable runs on the back wall; a crane rail along its top.
    tray = [_box(((x0w+x1w)/2, WALL_Y+.35, 3.1), (x1w-x0w-.4, .7, .15)),
            _box(((x0w+x1w)/2, WALL_Y+.66, 3.3), (x1w-x0w-.4, .08, .5))]
    _hexes(kit, 'Drone bay back wall cable tray', tray, kit.steel)
    for j, z in enumerate((3.33, 3.6)):
        kit.cylinder(f'Drone bay back wall cable run {j+1}', ((x0w+x1w)/2, WALL_Y+.3+.12*j, z), .14,
                     x1w-x0w-.6, kit.orange if j == 0 else kit.dark, 'X', 6, 0)
    kit.box('Drone bay overhead crane rail', ((x0w+x1w)/2, WALL_Y-.6, WALL_TOP+.3), (x1w-x0w, .6, .6), kit.grey, .05)
    kit.box('Drone bay overhead crane trolley', (-9.4, WALL_Y-.2, WALL_TOP+.55), (1.8, 1.6, .9), kit.orange, .08)
    racks = []
    for tx in (-9.4, 19.15):
        racks.append(_box((tx, WALL_Y+.1, -1.6), (2.2, .2, 3.4)))
        racks += [_box((tx, WALL_Y+.45, z), (2.2, .7, .1)) for z in (-2.9, -1.7, -.5)]
        racks += [_box((tx-.6+.6*k, WALL_Y+.45, -2.6+1.2*m), (.35, .5, .5)) for k in range(3) for m in range(2)]
    _hexes(kit, 'Drone bay back wall tool racks', racks, kit.grey)

    # Bay deck: plates with seams, the transfer rails the cradles ride on.
    deck, rails = [], []
    for x0, x1 in BAYS.values():
        nx = round((x1-x0)/3.0)
        ys = [WALL_Y+.05+(COAMING_Y-WALL_Y-.1)*j/4 for j in range(5)]
        for j in range(nx):
            a, b = x0+(x1-x0)*j/nx, x0+(x1-x0)*(j+1)/nx
            for ya, yb in zip(ys, ys[1:]):
                deck.append(_box(((a+b)/2, (ya+yb)/2, WELL_FLOOR+.06), (b-a-.14, yb-ya-.14, .12)))
        for ry in (17.2, 20.8):
            rails.append(_box(((x0+x1)/2, ry, DECK+.08), (x1-x0-.3, .3, .16)))
    _hexes(kit, 'Drone bay deck plating', deck, deckmat)
    _hexes(kit, 'Drone bay transfer rails', rails, kit.grey)
    # The blue ring: the fourth drone's servicing turntable on the forward bay deck.
    kit.cylinder('Drone bay forward bay servicing turntable', (19.0, 19.0, DECK+.06), 2.45, .12, kit.steel, 'Z', 24, .03)
    tring = []
    for j in range(24):
        a0, a1 = 2*math.pi*j/24, 2*math.pi*(j+1)/24
        tring.append([(19+r*math.cos(a), 19+r*math.sin(a), z) for z in (DECK, DECK+.16)
                      for r, a in ((2.45, a0), (2.8, a0), (2.8, a1), (2.45, a1))])
    _hexes(kit, 'Drone bay forward bay blue ring', [h[:4]+h[4:] for h in tring], ring_blue)

    # Exterior skin below the coaming: armour plates and frame hoops carrying the ribs round.
    plates, hoops = [], []
    for st, (x0, x1), name in ((AFT_ST, BAYS['aft'], 'aft'), (FORE_ST, (BAYS['forward'][0], 27.5), 'forward')):
        edges = [x0+(x1-x0)*j/round((x1-x0)/3.2) for j in range(round((x1-x0)/3.2)+1)]
        for a, b in zip(edges, edges[1:]):
            plates += [_facet(st, k, a+.13, b-.13, -.05, .15, .05, .95) for k in range(5, 12)]
        for x in RIBS[name]+((x0+.4,) if name == 'aft' else (x0+.4, 27.4)):
            hoops += [_facet(st, k, x-RIB_W/2, x+RIB_W/2, -.2, .4) for k in range(5, 12)]
    plates += [_facet(AFT_ST, k, -13.37, -11.33, -.05, .15, .05, .95) for k in range(N)]   # aft end cap
    _hexes(kit, 'Drone bay hangar pod armour plates', plates, armour)
    _hexes(kit, 'Drone bay hangar pod frame hoops', hoops, kit.steel)

    # Arch ribs over the open top, from the coaming up over the crown to the back wall.
    arches, floods = [], []
    for name, xs in RIBS.items():
        st = AFT_ST if name == 'aft' else FORE_ST
        for x in xs:
            pts = [(x, 25.3, COAMING_Z+.8)]
            pts += [(x, *_vert(st, x, math.radians(a), .25)) for a in (45, 75, 105)]
            pts.append((x, WALL_Y-.3, WALL_TOP+.1))
            arches += _path(pts, RIB_W, .6)
            fy, fz = _vert(st, x, math.radians(75), -.2)
            floods.append(_box((x, fy, fz-.1), (.5, .9, .25)))
    _hexes(kit, 'Drone bay arch ribs', arches, kit.steel)
    _hexes(kit, 'Drone bay rib floodlights', floods, lamp)

    # Outboard coaming cap with hazard bars and a handrail.
    cap, bars, rail = [], [], []
    for x0, x1 in BAYS.values():
        x1c = min(x1, 27.4)
        cap.append(_box(((x0+x1c)/2, 25.0, COAMING_Z+.3), (x1c-x0, 1.5, 1.0)))
        n = int((x1c-x0)/1.2)
        bars += [_box((x0+.6+1.2*j, 24.85, COAMING_Z+.82), (.6, 1.1, .06)) for j in range(n)]
        sx = [x0+.5+(x1c-x0-1.0)*j/max(1, round((x1c-x0)/2.4)) for j in range(round((x1c-x0)/2.4)+1)]
        rail += [_beam((x, 25.5, COAMING_Z+.8), (x, 25.5, COAMING_Z+2.0), .14) for x in sx]
        rail += [_beam((x0+.5, 25.5, z), (x1c-.5, 25.5, z), .12) for z in (COAMING_Z+1.4, COAMING_Z+2.0)]
    _hexes(kit, 'Drone bay outboard coaming', cap, kit.grey, .04)
    _hexes(kit, 'Drone bay outboard coaming hazard bars', bars, hazard)
    _hexes(kit, 'Drone bay outboard handrail', rail, kit.grey)

    # Join: the bulkhead between the bays (the pods' necks), a bolted collar and a pass door.
    cst = [(5.0, 7.3, 9.9), (6.6, 7.3, 9.9)]
    collar = _hexes(kit, 'Drone bay pod join collar', [_facet(cst, k, 5.0, 6.6, -1.6, 0) for k in range(N)], kit.steel)
    _hexes(kit, 'Drone bay pod join collar bolts',
           [_facet(cst, k, x-.18, x+.18, -.05, .18, .42, .58) for k in range(N) for x in (5.35, 6.25)], kit.grey)
    for side, fx, s in (('aft', BAYS['aft'][1], 1), ('forward', BAYS['forward'][0], -1)):
        kit.box(f'Drone bay join bulkhead pass door frame {side}', (fx+s*.05, 22.3, WELL_FLOOR+1.35), (.3, 1.9, 2.9),
                kit.steel, .04)
        kit.box(f'Drone bay join bulkhead pass door {side}', (fx-s*.05, 22.3, WELL_FLOOR+1.25), (.2, 1.35, 2.4),
                kit.grey, .03)

    # Amber join lamps, seated on whatever is uppermost there.
    seat = [aft, fore, block, collar, bpy.data.objects['Drone bay inboard back wall'],
            bpy.data.objects['Drone bay outboard coaming']]
    for i, (x, y) in enumerate(((7.5, 23.5), (7.5, 12.5), (27, 25), (27, 12.0)), 1):
        z = _surface(seat, x, y)
        kit.box(f'Drone bay join lamp {i} housing', (x, y, z+.15), (1.1, 1.1, .5), kit.steel, .08)
        kit.cylinder(f'Drone bay join lamp {i}', (x, y, z+.55), .32, .4, amber, 'Z', 10, .05)
        _hexes(kit, f'Drone bay join lamp {i} guard', [_box((x, y, z+.95), (1.0, .12, .12)),
                                                     _box((x, y, z+.95), (.12, 1.0, .12))], kit.steel)

    # --- the three WS 003 launch stations ---
    walls_x = sorted([BAYS['aft'][0], BAYS['aft'][1], BAYS['forward'][0], BAYS['forward'][1]])
    rib_x = [x for xs in RIBS.values() for x in xs]
    OPENINGS.clear()
    for i, (x, y) in enumerate(SLOT['locations'], 1):
        host = block if x > 25 else (fore if x > 6.5 else aft)
        pre = f'Drone bay WS 003 opening {i}'
        kit.box(f'{pre} well floor', (x, y, WELL_FLOOR+.07), (7.2, 6.6, .14), kit.dark, .02)
        kerb = [_box((x, y+s*3.45, WELL_FLOOR+.17), (7.8, .3, .34)) for s in (-1, 1)]
        kerb += [_box((x+s*3.75, y, WELL_FLOOR+.17), (.3, 6.6, .34)) for s in (-1, 1)]
        _hexes(kit, f'{pre} coaming', kerb, kit.grey)
        hb = [_box((x-3.4+.85*j, y+s*3.45, WELL_FLOOR+.36), (.42, .3, .04)) for s in (-1, 1) for j in range(9)]
        hb += [_box((x+s*3.75, y-3.0+.75*j, WELL_FLOOR+.36), (.3, .37, .04)) for s in (-1, 1) for j in range(9)]
        _hexes(kit, f'{pre} coaming hazard bars', hb, hazard)
        for j, (sx, sy) in enumerate(((1, 1), (-1, 1), (-1, -1), (1, -1)), 1):
            kit.box(f'{pre} jamb light {j}', (x+sx*3.75, y+sy*3.45, WELL_FLOOR+.48), (.42, .42, .22), lamp, .03)

        # Launch cradle on the transfer rails: bogies, deck, corner chocks.
        cdeck = DECK+.16+.3+.35
        cr = [_box((x+sx*2.2, ry, DECK+.16+.15), (1.1, .8, .3)) for sx in (-1, 1) for ry in (17.2, 20.8)]
        cr.append(_box((x, y, cdeck-.175), (6.2, 5.6, .35)))
        cr += [_box((x+sx*2.95, y+sy*2.65, cdeck+.35), (.5, .5, .7)) for sx in (-1, 1) for sy in (-1, 1)]
        _hexes(kit, f'{pre} launch cradle', cr, kit.steel, .03)
        _hexes(kit, f'{pre} launch cradle hazard edge',
               [_box((x-2.6+1.3*j, y+s*2.72, cdeck+.01), (.65, .16, .03)) for s in (-1, 1) for j in range(5)], hazard)
        _hexes(kit, f'{pre} charging contact', [_box((x, y, cdeck+.04), (1.2, 1.2, .08))], kit.bay_glow)
        # Servicing arm folded against the back wall, and the charging umbilical.
        arm = [_box((x+2.4, WALL_Y+.35, -.5), (1.0, .7, 1.4))]
        arm += _path([(x+2.4, WALL_Y+.8, -.5), (x+.9, WALL_Y+1.0, 2.6), (x-1.2, WALL_Y+1.1, .4)], .45)
        arm.append(_box((x-1.2, WALL_Y+1.1, .0), (.7, .7, .7)))
        _hexes(kit, f'{pre} servicing arm', arm, kit.grey)
        um = [_box((x-2.0, WALL_Y+.3, WELL_FLOOR+2.0), (1.0, .6, 1.0))]
        um += _path([(x-2.0, WALL_Y+.5, WELL_FLOOR+1.5), (x-2.0, WALL_Y+.9, DECK+.12), (x-2.0, y-2.85, DECK+.12),
                     (x-2.0, y-2.85, cdeck-.1)], .26)
        _hexes(kit, f'{pre} charging umbilical', um, kit.orange)

        rim = rims[i]
        hw = min([abs(x-rx)-RIB_W/2 for rx in rib_x]+[abs(x-wx) for wx in walls_x]+[y-WALL_Y])
        assert hw >= OPENING_R, f'station {i} clear half-width {hw:.2f}'
        mouth = kit.empty(f'{pre} mouth', (x, y, rim), SLOT['angle'])
        launch = kit.empty(f'Drone bay WS 003 launch point {i}', (x, y, cdeck), SLOT['angle'])
        OPENINGS.append({'index': i, 'slot_location': [x, y], 'mouth_anchor': mouth.name,
                         'launch_point_anchor': launch.name, 'rim_z': round(rim, 3),
                         'floor_z': round(cdeck, 3), 'clear_radius': round(hw, 2), 'host': host.name})

    # --- forward service block: the workshop end ---
    bt = 8.0
    kit.box('Drone bay service block charging cabinet', (32.6, 23.2, bt+.6), (3.0, 4.6, 1.2), kit.steel, .1)
    _hexes(kit, 'Drone bay service block charging cabinet fins',
           [_box((31.4+.5*j, 23.2, bt+1.35), (.16, 4.2, .3)) for j in range(6)], kit.grey)
    _hexes(kit, 'Drone bay service block charging cabinet status lamps',
           [_box((34.12, 21.6+.7*j, bt+.6), (.06, .35, .35)) for j in range(3)], kit.bay_glow)
    kit.box('Drone bay service block roof service cover', (32.6, 15.8, bt+.13), (3.2, 6.6, .26), armour, .05)
    _hexes(kit, 'Drone bay service block roof cover bolts',
           [_box((32.6+dx, 15.8+dy, bt+.3), (.22, .22, .12))
            for dx in (-1.4, 1.4) for dy in (-3.1, -1.55, 0, 1.55, 3.1)], kit.grey)
    # Workshop front (the block's aft face, x 30.5, inside the bay).
    wx = BAYS['forward'][1]
    kit.box('Drone bay workshop front shutter frame', (wx-.1, 18.0, WELL_FLOOR+3.0), (.3, 8.0, 6.2), kit.steel, .05)
    _hexes(kit, 'Drone bay workshop front shutter',
           [_box((wx-.3, 18.0, WELL_FLOOR+.4+.6*j), (.14, 7.0, .48)) for j in range(9)], kit.grey)
    kit.box('Drone bay workshop front viewport', (wx-.12, 18.0, WELL_FLOOR+7.2), (.12, 9.5, .9), kit.glass, .02)
    kit.box('Drone bay workshop front door', (wx-.15, 23.2, WELL_FLOOR+1.2), (.2, 1.35, 2.4), kit.grey, .03)
    kit.box('Drone bay workshop front door lamp', (wx-.25, 23.2, WELL_FLOOR+2.7), (.25, .5, .22), amber, .03)
    # Outboard face (Y 26.5): armour panels with seams, a workshop door and viewport.
    oy = 26.5
    panels = [_box((33.0, oy+.07, z), (2.8, .2, h)) for z, h in ((-9.0, 5.6), (3.6, 7.6))]
    panels += [_box((x, oy+.07, -9.0), (w, .2, 5.6)) for x, w in ((28.3, 2.4), (30.4, 1.6))]
    _hexes(kit, 'Drone bay service block outboard panels', panels, armour)
    kit.box('Drone bay service block workshop door frame', (33.0, oy+.12, WELL_FLOOR+1.35), (1.9, .3, 2.9), kit.steel, .05)
    kit.box('Drone bay service block workshop door', (33.0, oy+.22, WELL_FLOOR+1.25), (1.35, .2, 2.4), kit.grey, .04)
    kit.box('Drone bay service block workshop viewport', (33.0, oy+.25, WELL_FLOOR+3.6), (1.1, .1, .7), kit.glass, .02)
    kit.box('Drone bay service block workshop door lamp', (33.0, oy+.35, WELL_FLOOR+2.9), (.5, .3, .22), amber, .03)
    # Forward face (X 34.5): the maintenance shutter drones are walked in through.
    fx = 34.5
    kit.box('Drone bay service block maintenance shutter frame', (fx+.1, 18.75, -2.0), (.3, 10.4, 7.6), kit.steel, .06)
    _hexes(kit, 'Drone bay service block maintenance shutter',
           [_box((fx+.32, 18.75, -5.3+.62*j), (.16, 9.2, .5)) for j in range(11)], kit.grey)
    _hexes(kit, 'Drone bay service block forward panels',
           [_box((fx+.07, y0, z), (.2, w, h)) for y0, w in ((13.5, 4.6), (18.75, 5.4), (24.0, 4.6))
            for z, h in ((4.6, 5.6), (-9.6, 3.8))], armour)
    for j, y0 in enumerate((14.2, 23.3), 1):
        kit.box(f'Drone bay service block floodlight {j}', (fx+.35, y0, 2.1), (.4, .9, .45), lamp, .04)

    # --- port outrigger: a lattice box truss in three frames ---
    TOP, BOT = 1.8, -8.4
    YI = 26.9                                  # inboard chords
    kit.box('Frame port outrigger upper rail', (9.0, YI, TOP), (44.0, 1.2, 1.2), kit.orange, .1)
    kit.box('Frame port outrigger lower rail', (9.0, YI, BOT), (44.0, 1.2, 1.2), kit.steel, .1)
    stations = {1: (-22, 28.5), 2: (-8, 28.5), 3: (4.5, 30.0), 4: (20, 30.4), 5: (29, 30.4)}
    for i, (x, yo) in stations.items():
        kit.box(f'Frame port outrigger post {i}', (x, yo, (TOP+BOT)/2), (1.0, 1.0, TOP-BOT), kit.grey, .06)
        kit.box(f'Frame port outrigger strut {i}', (x, (25.4+yo)/2, POD_Z), (.9, yo-25.4, .9), kit.steel, .06)

    def frame(name, x0, x1, yo, nodes, chord_w, top_battens, deck=False):
        m = [_beam((x0, yo, TOP), (x1, yo, TOP), chord_w, 1.0), _beam((x0, yo, BOT), (x1, yo, BOT), 1.0, 1.0),
             _beam((x0, yo, (TOP+BOT)/2), (x1, yo, (TOP+BOT)/2), .6)]
        for x in nodes:
            if x not in [s[0] for s in stations.values()]:
                m.append(_beam((x, yo, BOT), (x, yo, TOP), .8))
            m.append(_beam((x, YI, BOT), (x, YI, TOP), .7))
            m.append(_beam((x, YI, BOT), (x, yo, BOT), .7))
            if top_battens is None or x in top_battens:
                m.append(_beam((x, YI, TOP), (x, yo, TOP), .7))
        zm = (TOP+BOT)/2
        for j, (xa, xb) in enumerate(zip(nodes, nodes[1:])):
            xm = (xa+xb)/2
            m += [_beam((xa, yo, BOT), (xm, yo, zm), .5), _beam((xm, yo, zm), (xb, yo, BOT), .5),
                  _beam((xa, yo, TOP), (xm, yo, zm), .5), _beam((xm, yo, zm), (xb, yo, TOP), .5)]   # outboard X-panels
            m.append(_beam((xa, YI, BOT if j % 2 else TOP), (xb, YI, TOP if j % 2 else BOT), .5))  # inboard face
            if top_battens is None:
                ya, yb = (YI, yo) if j % 2 == 0 else (yo, YI)
                m.append(_beam((xa, ya, TOP), (xb, yb, TOP), .5))  # top face Warren
        _hexes(kit, f'Frame port outrigger {name} truss', m, kit.steel)
        if deck:
            d = [_box(((x0+x1)/2, (YI+yo)/2, BOT+.55), (x1-x0-.6, yo-YI-.9, .25))]
            d += [_box((x, (YI+yo)/2, BOT+.75), (.2, yo-YI-.9, .15)) for x in
                  [x0+.8+k*1.2 for k in range(int((x1-x0-1.4)/1.2)+1)]]
            _hexes(kit, f'Frame port outrigger {name} walkway grating', d, kit.grey)

    frame('forward', 11.0, 31.0, 30.4, [11.5, 15.75, 20, 24.5, 29, 30.8], 1.2, None, deck=True)
    frame('mid', -6.2, 5.0, 30.0, [-5.8, -0.65, 4.5], 1.6, (-5.8, 4.5))
    frame('aft', -11.0, -6.0, 28.5, [-10.5, -8, -6.4], 1.0, (-8, -6.4))
    kit.cylinder('Frame port bow fender', (22, 31.2, -2), 2.4, 8, rubber, 'Z', 12, .15)
    for i, (x, y0, w, dy) in enumerate(((13.6, 31.0, 2.4, 1.0), (28.0, 31.0, 2.6, 1.1), (1.9, 30.8, 2.2, .8)), 1):
        kit.box(f'Frame port outrigger bumper {i}', (x, y0+dy/2, -2.5), (w, dy, 3.4), rubber, .3)
