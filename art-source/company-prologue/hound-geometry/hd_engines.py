"""The two drive pods: whole engine units hung on the spar either side.

Each pod is a drive unit in distinct sections along its thrust axis, each
its own faceted solid with a dark seam over a dark core: a domed intake cap,
a forward casing, the orange armour collar the spar enters (the sprite's
widest band, rows 37-52), a narrower aft casing with a reinforcing band, the
thrust housing (rows 63-69) and one engine can whose aft face is the
exhaust, the bell sunk into it. Sections are chamfered octagons, flat-shaded.

hound.ship has exactly two engine slots, LOW_TECH, width 16, length 80,
angle 180, at (-25, +22) and (-25, -22). Engine 1 is slot 0 (port, +Y),
Engine 2 is slot 1 (starboard). The nozzle centre is the slot XY at Z 0;
the bell mouth radius follows the Second Wind ratio (0.325 x slot width).

Pass 2 detail, read off hound_hegemony.png / hound_lights.png (port pod;
starboard mirrors it) and the lore of an old, rigorously serviced low-tech
drive:
- intake cap: a recessed intake grille ring on the cap's nose and the
  lighter sensor "eye" ring on its top (cols 5-11, rows 29-33);
- cowl and collar: lighter top plates (cols 6-12), the collar cut into three
  armour segments by seams over the dark core, and a brown outboard side
  skirt (cols 1-4) of bolted plates set into the collar's flank;
- aft casing: the actuator boss on the reinforcing band (cols 6-8, rows
  55-58), its bolts, recessed radiator fins outboard, keel ribs, and three
  louvred heat-vent wells glowing at the hound_lights.png points;
- thrust housing: two raised shroud bands (rows 64-66), a gimbal ring and an
  articulated petal shroud around the can, with four actuator rams; the
  bell stays sunk in the can and dark;
- fuel and coolant lines from the body across the spar into each pod's
  inboard side, clamped, and bolted maintenance hatches.
Everything stays inside the pass-1 plan footprint or the sprite outline.
"""
import math
import bpy
from mathutils import Euler, Vector
from hd_kit import boolean, loft_x, material, octagon, round_shading, sides

ENGINES = [(-25, 22, 16), (-25, -22, 16)]
BELL_R = .325 * 16
CAN_R = BELL_R + .5
CAN_X = (-26.0, -24.4)
SEAM = .4

# Port pod sections: (name, material key, [(x, (y0, y1, z0, z1, top chamfer,
# bottom chamfer)), ...]) with y0 inboard, y1 outboard. Mirrored for Engine 2.
PARTS = [
    ('intake cap', 'steel', [(11.0, (16, 27.4, -5.8, 6.3, 3, 2.5)), (14.6, (16, 27.4, -5.8, 6.3, 3, 2.5)),
                             (16.2, (17.4, 26, -4.3, 4.8, 2.4, 2)), (17.0, (18.6, 24.8, -2.6, 3.0, 1.4, 1.2))]),
    ('forward casing', 'rust', [(7.9, (14.4, 28.4, -6.5, 7, 3, 2.5)), (11.0, (14.6, 28.2, -6.3, 6.8, 3, 2.5))]),
    ('armour collar', 'orange', [(-8.6, (15.6, 29.2, -6.4, 6.8, 3.2, 2.8)), (-7.3, (14.2, 30, -7.1, 7.6, 3.6, 3)),
                                 (6.6, (14.2, 30, -7.1, 7.6, 3.6, 3)), (8.0, (14.4, 29.4, -6.6, 7.1, 3.4, 3))]),
    ('aft casing', 'rust', [(-18.0, (16.4, 27.4, -5.6, 6, 2.6, 2.2)), (-8.6, (16.4, 27.4, -5.6, 6, 2.6, 2.2))]),
    ('reinforcing band', 'steel', [(-14.0, (15.7, 28.2, -6.2, 6.6, 2.9, 2.5)), (-11.0, (15.7, 28.2, -6.2, 6.6, 2.9, 2.5))]),
    ('thrust housing', 'orange', [(-24.6, (16, 28.6, -6.2, 6.4, 3, 2.6)), (-23.8, (15, 29.6, -7, 7.1, 3.3, 3)),
                                  (-19.0, (15, 29.6, -7, 7.1, 3.3, 3)), (-18.0, (15.8, 28.6, -6.4, 6.5, 3, 2.6))]),
]
CORE = (17, 26.5, -4.6, 5.0, 2, 2)
COLLAR = PARTS[2][2][1][1]           # the collar's full section
COLLAR_SEAMS = (-4.9, 4.1)           # armour segment joints either side of the dorsal vent
HOUSING = PARTS[5][2][1][1]          # thrust housing's full section

# hound_lights.png glow points on the port pod (x, y): mirrored to starboard.
LIGHT_COLLAR = (-.5, 25.5)           # row 45 col 5, small
LIGHT_VENT_FORE = (-10.5, 18.5)      # row 55 col 12, inboard
LIGHT_VENT_AFT = (-15.5, 18.5)       # row 60 col 12, inboard
LIGHT_VENT_TOP = (-16.5, 24.5)       # row 61 col 6, outboard top, brightest


def oct_yz(spec, side):
    y0, y1, z0, z1, ct, cb = spec
    pts = octagon(y0, y1, z0, z1, ct, cb)
    return pts if side > 0 else [(-y, z) for y, z in pts]


def grow(spec, d):
    """The same chamfered section offset outward by d (negative shrinks)."""
    y0, y1, z0, z1, ct, cb = spec
    k = .586*d
    return (y0-d, y1+d, z0-d, z1+d, max(ct+k, .1), max(cb+k, .1))


HEX = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


class Batch:
    """Many small disjoint solids gathered into one named, flat-shaded object."""

    def __init__(self, side):
        self.side, self.v, self.f = side, [], []

    def p(self, x, y, z):
        return (x, self.side*y, z)

    def hexa(self, pts):
        b = len(self.v)
        self.v += pts
        self.f += [tuple(b+i for i in q) for q in HEX]

    def box(self, c, d, rot=(0, 0, 0)):
        """Box centred at port-side c; rot is applied before mirroring (X and Z flip)."""
        m = Euler((rot[0]*self.side, rot[1], rot[2]*self.side)).to_matrix()
        cx, cy, cz = self.p(*c)
        pts = []
        for sz in (-1, 1):
            for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                o = m @ Vector((sx*d[0]/2, sy*d[1]/2, sz*d[2]/2))
                pts.append((cx+o.x, cy+o.y, cz+o.z))
        self.hexa(pts)

    def peg(self, c, r, h, axis='Z', n=6, phase=0.0):
        """A round prism (at least n sides, by the segment rule) of radius r and length h
        along an axis, centred at port-side c."""
        n = sides(r, n)
        cx, cy, cz = self.p(*c)
        b = len(self.v)
        for w in (-h/2, h/2):
            for k in range(n):
                a = phase+2*math.pi*k/n
                u, v = r*math.cos(a), r*math.sin(a)
                off = {'X': (w, u, v), 'Y': (u, w, v), 'Z': (u, v, w)}[axis]
                self.v.append((cx+off[0], cy+off[1], cz+off[2]))
        self.f.append(tuple(b+k for k in range(n)))
        self.f.append(tuple(b+n+k for k in range(n)))
        self.f += [(b+k, b+(k+1) % n, b+n+(k+1) % n, b+n+k) for k in range(n)]

    def ring(self, c, r0, r1, h, axis='X', n=12, phase=math.pi/12):
        """An annulus of blocks forming one closed ring, at least n of them (segment rule)."""
        n = sides(r1, n)
        cx, cy, cz = self.p(*c)
        for k in range(n):
            pts = []
            for w in (-h/2, h/2):
                for r, a in ((r0, k), (r1, k), (r1, k+1), (r0, k+1)):
                    ang = phase+2*math.pi*a/n
                    u, v = r*math.cos(ang), r*math.sin(ang)
                    off = {'X': (w, u, v), 'Y': (u, w, v), 'Z': (u, v, w)}[axis]
                    pts.append((cx+off[0], cy+off[1], cz+off[2]))
            self.hexa(pts)

    def build(self, kit, name, mat, bevel=0):
        # Smooth with creases over 35 degrees sharp: the pegs read round, the boxes stay plate.
        return round_shading(kit.mesh(name, self.v, self.f, mat, bevel))


def solid_ring_x(kit, name, x0, x1, outer, inner, side, mat):
    """A ring cutter/solid: the outer chamfered section minus the inner one."""
    o = loft_x(kit, name, [(x0, oct_yz(outer, side)), (x1, oct_yz(outer, side))], mat, 0)
    i = loft_x(kit, 'Temporary ring bore', [(x0-1, oct_yz(inner, side)), (x1+1, oct_yz(inner, side))], mat, 0)
    boolean(o, i)
    return o


def build(kit):
    glow = material('Engine heat glow', (.30, .07, .02), 0, .5, emission=(1.0, .33, .07), strength=5.0)
    glow_hot = material('Engine heat glow bright', (.35, .09, .02), 0, .5, emission=(1.0, .38, .09), strength=9.0)
    pale = material('Engine worn top plate', (.50, .24, .12), .3, .6)
    light_steel = material('Engine bright steel', (.42, .43, .44), .7, .4)

    for i, (ex, ey, width) in enumerate(ENGINES, 1):
        side = 1 if ey > 0 else -1
        label = 'port' if side > 0 else 'starboard'
        P = f'Engine {i} {label}'
        Y = lambda y: side*y
        parts = {}
        for name, mat, stations in PARTS:
            # Seams: each section stops short of its neighbours over the core.
            xs = [x for x, _ in stations]
            lo, hi = min(xs), max(xs)
            st = [(min(max(x, lo+SEAM/2), hi-SEAM/2) if name not in ('reinforcing band', 'intake cap') else x,
                   oct_yz(s, side)) for x, s in stations]
            parts[name] = loft_x(kit, f'{P} {name}', st, getattr(kit, mat))
            for m in parts[name].modifiers:  # the pod sections keep their pass-1 chamfer rounding (30 deg)
                if m.type == 'BEVEL':
                    m.angle_limit = math.radians(30)
        # Dorsal vent housing on the collar: the ring the sprite shows there.
        kit.cylinder(f'{P} dorsal vent housing', (-.5, Y(22), 7.6), 4.2, 1.6, kit.rust, 'Z', 8, .12)
        kit.cylinder(f'{P} dorsal vent grille', (-.5, Y(22), 8.35), 3.0, .3, kit.dark, 'Z', 8, 0)
        loft_x(kit, f'{P} dark core', [(-24, oct_yz(CORE, side)), (11.5, oct_yz(CORE, side))], kit.dark, 0)
        # Engine can: a round shroud (segment rule) whose aft face is the exhaust,
        # with the bell sunk into it rather than a nozzle poking out.
        cx = sum(CAN_X)/2
        can = kit.cylinder(f'{P} engine can', (cx, ey, 0), CAN_R, CAN_X[1]-CAN_X[0], kit.steel, 'X', 12, .1)
        bpy.ops.mesh.primitive_cone_add(vertices=sides(BELL_R, 12), radius1=BELL_R, radius2=BELL_R*.45, depth=1.6,
                                        location=(CAN_X[0]+.6, ey, 0), rotation=(0, 1.5707963, 0))
        bore = bpy.context.object
        boolean(can, bore)
        bpy.ops.mesh.primitive_cone_add(vertices=sides(BELL_R, 12), radius1=BELL_R-.15, radius2=BELL_R*.4, depth=1.4,
                                        location=(CAN_X[0]+.75, ey, 0), rotation=(0, 1.5707963, 0))
        kit.finish(round_shading(bpy.context.object), f'{P} exhaust bell', kit.dark)
        kit.empty(f'{P} exhaust anchor', (ex, ey, 0), 180)

        detail_intake(kit, P, side, parts['intake cap'], light_steel)
        detail_collar(kit, P, side, parts, pale, glow)
        detail_aft_casing(kit, P, side, parts['aft casing'], light_steel, glow, glow_hot)
        detail_thrust(kit, P, side, ey, parts['thrust housing'])
        detail_lines(kit, P, side)
        detail_hatches(kit, P, side)


def detail_intake(kit, P, side, cap, light_steel):
    """Armoured intake dome: a recessed grille ring in its nose, the sensor eye on top."""
    cy, cz = 21.7, .2                      # centre of the cap's nose face
    bore = kit.cylinder('Temporary intake bore', (17.2, side*cy, cz), 2.05, 1.4, kit.dark, 'X', 12, 0)
    boolean(cap, bore)
    kit.cylinder(f'{P} intake grille backing', (16.55, side*cy, cz), 2.1, .3, kit.dark, 'X', 12, 0)
    slats = Batch(side)
    for z in (-1.2, -.6, 0, .6, 1.2):
        half = (2.0**2-z*z)**.5
        slats.box((16.8, cy, cz+z), (.22, 2*half, .22), (math.radians(30), 0, 0))
    slats.build(kit, f'{P} intake grille slats', kit.steel)
    ring = Batch(side)
    ring.ring((16.92, cy, cz), 1.95, 2.6, .22)
    ring.build(kit, f'{P} intake grille ring', light_steel, .04)
    bolts = Batch(side)
    for k in range(8):
        a = math.pi/8+k*math.pi/4
        bolts.peg((17.05, cy+2.28*math.cos(a), cz+2.28*math.sin(a)), .14, .12, 'X')
    bolts.build(kit, f'{P} intake ring bolts', kit.steel)
    # The lighter ring the sprite shows on the dome (cols 5-11, rows 29-33):
    # a flush sensor/deflector eye on the cap's top flat.
    eye = Batch(side)
    eye.ring((12.8, 21.7, 6.42), 1.25, 2.0, .24, 'Z')
    eye.build(kit, f'{P} intake sensor eye ring', light_steel, .03)
    kit.cylinder(f'{P} intake sensor eye lens', (12.8, side*21.7, 6.36), 1.3, .12, kit.dark, 'Z', 12, 0)
    # Clamp band seating the dome on the forward casing.
    band = loft_x(kit, f'{P} intake clamp band', [(11.05, oct_yz(grow(PARTS[0][2][0][1], .18), side)),
                                                    (11.75, oct_yz(grow(PARTS[0][2][0][1], .18), side))], kit.rust, .05)
    b = Batch(side)
    for y in (18.6, 21.7, 24.8):
        b.peg((11.4, y, 6.55), .13, .12, 'Z')
    for z in (-2.5, 0, 2.5):
        b.peg((11.4, 27.65, z), .13, .12, 'Y')
    b.build(kit, f'{P} intake clamp bolts', kit.steel)
    return band


def detail_collar(kit, P, side, parts, pale, glow):
    collar = parts['armour collar']
    # Armour segment seams over the dark core (the curved lines in the sprite).
    for k, x in enumerate(COLLAR_SEAMS, 1):
        cut = solid_ring_x(kit, 'Temporary collar seam', x-.18, x+.18, grow(COLLAR, 1), grow(COLLAR, -.5), side, kit.dark)
        boolean(collar, cut)
        loft_x(kit, f'{P} collar seam liner {k}', [(x-.17, oct_yz(grow(COLLAR, -.42), side)),
                                                   (x+.17, oct_yz(grow(COLLAR, -.42), side))], kit.dark, 0)
    # Side armour skirt: the brown outboard strip (cols 1-4), set into the flank.
    recess = kit.box('Temporary skirt recess', (-.4, side*30.3, -.1), (13.2, 1.8, 7.0), kit.dark, 0)
    boolean(collar, recess)
    kit.box(f'{P} skirt backing', (-.4, side*29.45, -.1), (13.0, .12, 6.8), kit.dark, 0)
    plates = [(-7.0, -5.1), (-4.7, -.7), (-.3, 3.9), (4.3, 6.2)]
    bolts = Batch(side)
    for k, (x0, x1) in enumerate(plates, 1):
        kit.box(f'{P} side skirt plate {k}', ((x0+x1)/2, side*29.72, -.1), (x1-x0, .5, 6.6), kit.rust, .08)
        for x in (x0+.45, x1-.45):
            for z in (-2.9, 2.7):
                bolts.peg((x, 29.95, z), .14, .12, 'Y')
    bolts.build(kit, f'{P} side skirt bolts', kit.steel)
    # Lighter top plates: the collar's (rows 44-50) framing the dorsal vent,
    # and the cowl's on the forward casing (rows 34-40).
    top = kit.box(f'{P} collar top plate', (.2, side*22, 7.8), (13.4, 8.0, .4), pale, .1)
    hole = kit.cylinder('Temporary vent coaming hole', (-.5, side*22, 7.8), 4.55, 2, kit.dark, 'Z', 12, 0)
    boolean(top, hole)
    for x0, x1 in ((COLLAR_SEAMS[0]-.2, COLLAR_SEAMS[0]+.2), (COLLAR_SEAMS[1]-.2, COLLAR_SEAMS[1]+.2)):
        gap = kit.box('Temporary top plate seam', ((x0+x1)/2, side*22, 7.8), (x1-x0, 10, 2), kit.dark, 0)
        boolean(top, gap)
    kit.box(f'{P} cowl top plate', (9.45, side*21.7, 7.18), (2.7, 6.8, .36), pale, .08)
    b = Batch(side)
    for x in (8.4, 10.5):
        for y in (18.8, 24.6):
            b.peg((x, y, 7.42), .14, .12, 'Z')
    for x in (-6.0, -5.5, 5.3, 6.4):
        for y in (18.6, 25.4):
            b.peg((x, y, 8.06), .14, .12, 'Z')
    b.build(kit, f'{P} top plate bolts', kit.steel)
    # Louvres over the dorsal vent grille.
    lv = Batch(side)
    for x in (-2.3, -1.4, -.5, .4, 1.3):
        dx = x+.5
        half = max((2.7**2-dx*dx), .5)**.5
        lv.box((x, 22, 8.62), (.55, 2*half, .1), (0, math.radians(-28), 0))
    lv.build(kit, f'{P} dorsal vent louvres', kit.steel)
    # Small heat port at the collar light (row 45 col 5), on the vent's outboard rim.
    lx, ly = LIGHT_COLLAR
    kit.box(f'{P} collar heat port hood', (lx, side*25.55, 8.25), (2.0, 1.3, .9), kit.steel, .06)
    kit.box(f'{P} collar heat port glow', (lx, side*26.21, 8.2), (1.4, .06, .4), glow, 0)
    # Forward casing clamp band bolts (the casing reads as the barrel band
    # between dome and collar).
    b = Batch(side)
    for x in (8.6, 10.3):
        for z in (-3.0, -1, 1, 3.0):
            b.peg((x, 28.35 if z < 0 or z > 0 else 28.35, z), .13, .14, 'Y')
    b.build(kit, f'{P} forward casing bolts', kit.steel)


def vent_well(kit, P, side, casing, name, x0, x1, y0, y1, z0, glow, slat_axis):
    """A louvred heat-vent well cut into the casing, glowing at its floor."""
    xm, ym = (x0+x1)/2, (y0+y1)/2
    cut = kit.box('Temporary vent well', (xm, side*ym, z0+3), (x1-x0, y1-y0, 6), kit.dark, 0)
    boolean(casing, cut)
    kit.box(f'{P} {name} liner', (xm, side*ym, z0+.12), (x1-x0-.04, y1-y0-.04, .3), kit.dark, 0)
    kit.box(f'{P} {name} glow', (xm, side*ym, z0+.3), (x1-x0-.5, y1-y0-.5, .1), glow, 0)
    lv = Batch(side)
    if slat_axis == 'X':   # slats run fore-aft, stepped across Y
        n = max(int((y1-y0)/.75), 2)
        for k in range(n):
            y = y0+(k+.5)*(y1-y0)/n
            lv.box((xm, y, z0+.75), (x1-x0-.1, .55, .1), (math.radians(35), 0, 0))
    else:                  # slats run across, stepped fore-aft
        n = max(int((x1-x0)/.75), 2)
        for k in range(n):
            x = x0+(k+.5)*(x1-x0)/n
            lv.box((x, ym-.6 if y1 > 26 else ym, z0+.7), (.55, y1-y0-1.4 if y1 > 26 else y1-y0-.1, .1), (0, math.radians(-35), 0))
    lv.build(kit, f'{P} {name} louvres', kit.steel)


def detail_aft_casing(kit, P, side, casing, light_steel, glow, glow_hot):
    # Heat-vent wells at the hound_lights.png points: two inboard in the dark
    # recess the sprite shows (cols 9-12), the biggest outboard on top.
    vent_well(kit, P, side, casing, 'fore heat vent', -10.8, -9.1, 17.0, 20.4, 4.4, glow, 'X')
    vent_well(kit, P, side, casing, 'aft heat vent', -17.6, -14.4, 17.0, 21.4, 4.4, glow, 'X')
    vent_well(kit, P, side, casing, 'top heat vent', -17.8, -14.8, 22.6, 26.6, 4.9, glow_hot, 'Y')
    # Radiator bay recessed into the outboard wall: fins inside the outline.
    bay = kit.box('Temporary radiator bay', (-16.1, side*28.0, 0), (3.0, 1.6, 5.6), kit.dark, 0)
    boolean(casing, bay)
    kit.box(f'{P} radiator bay back', (-16.1, side*27.24, 0), (2.96, .1, 5.5), kit.dark, 0)
    fins = Batch(side)
    for x in (-17.3, -16.7, -16.1, -15.5, -14.9):
        fins.box((x, 27.0, 0), (.16, .7, 5.4))
    fins.box((-16.1, 27.0, 0), (2.9, .5, .2))
    fins.build(kit, f'{P} radiator fins', kit.steel)
    # Keel radiator ribs under the aft casing.
    ribs = Batch(side)
    for x in (-17.3, -16.4, -15.5, -10.4, -9.5):
        ribs.box((x, 21.9, -5.85), (.3, 5.6, .5))
    ribs.build(kit, f'{P} keel radiator ribs', kit.steel)
    # Actuator boss on the reinforcing band (cols 6-8, rows 55-58) and the
    # band's bolts: it is a clamp, not a stripe.
    kit.cylinder(f'{P} band actuator boss', (-12.5, side*23.4, 6.95), 1.25, .8, light_steel, 'Z', 8, .06)
    kit.cylinder(f'{P} band actuator cap', (-12.5, side*23.4, 7.5), .6, .4, kit.steel, 'Z', 8, .04)
    b = Batch(side)
    for x in (-13.5, -11.5):
        for y in (18.9, 20.6):
            b.peg((x, y, 6.66), .15, .14, 'Z')
        for z in (-3.0, -1.0, 1.0, 3.0):
            b.peg((x, 28.26, z), .15, .14, 'Y')
        for y in (18.8, 21.9, 25.0):
            b.peg((x, y, -6.26), .15, .14, 'Z')
    b.build(kit, f'{P} reinforcing band bolts', kit.steel)


def detail_thrust(kit, P, side, ey, housing):
    # Shroud bands (the sprite's light rings, rows 64-66), proud top and bottom.
    hb = Batch(side)
    for k, (x0, x1) in enumerate(((-21.75, -21.05), (-20.35, -19.65)), 1):
        loft_x(kit, f'{P} shroud band {k}', [(x0, oct_yz((15.05, 29.55, -7.3, 7.4, 3.35, 3.05), side)),
                                             (x1, oct_yz((15.05, 29.55, -7.3, 7.4, 3.35, 3.05), side))], kit.steel, .05)
        for y in (19.0, 22.3, 25.6):
            hb.peg(((x0+x1)/2, y, 7.44), .13, .1, 'Z')
    hb.build(kit, f'{P} shroud band bolts', kit.steel)
    # Gimbal stage: a ring seated in a pocket in the housing's aft face.
    pocket = kit.cylinder('Temporary gimbal pocket', (-24.6, ey, 0), 6.05, 1.4, kit.dark, 'X', 12, 0)
    boolean(housing, pocket)
    g = Batch(side)
    g.ring((-24.25, 22, 0), 5.55, 5.95, .7)
    g.build(kit, f'{P} gimbal ring', kit.dark, .03)
    # Articulated nozzle shroud: twelve separate petals clasping the can.
    pet = Batch(side)
    n = 12
    for k in range(n):
        a0 = math.radians(15+k*30+2.2)
        a1 = math.radians(15+(k+1)*30-2.2)
        pts = []
        for x, r0, r1 in ((-25.95, 5.6, 5.8), (-24.55, 5.6, 6.12)):
            for r, a in ((r0, a0), (r1, a0), (r1, a1), (r0, a1)):
                pts.append((x, ey+r*math.cos(a), r*math.sin(a)))
        pet.hexa(pts)
    pet.build(kit, f'{P} nozzle shroud petals', kit.steel, .03)
    # Four actuator rams from the housing face to the petals.
    rams = Batch(side)
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        rams.peg((-25.05, 22+6.45*math.cos(r), 6.45*math.sin(r)), .32, 1.2, 'X', 8)
        rams.peg((-24.45, 22+6.45*math.cos(r), 6.45*math.sin(r)), .5, .3, 'X', 8)
    rams.build(kit, f'{P} nozzle actuator rams', kit.steel)


def detail_lines(kit, P, side):
    """Fuel and coolant lines from the body, across the spar, into the pod's inboard side."""
    fuel, cool, clamps = Batch(side), Batch(side), Batch(side)
    # Crossing over the spar between the body deck and the collar.
    for x, z in ((.8, 4.4), (2.1, 4.0)):
        fuel.peg((x, 13.4, z), .32, 3.6, 'Y', 8)
    fuel.box((1.45, 12.2, 4.2), (2.2, .8, 1.2))        # body-side manifold block
    # Forward feed: out of the body wall beside the spar, through the collar
    # and along the forward casing's inboard wall, kept inside the outline.
    cool.peg((3.4, 13.6, 2.2), .28, 1.6, 'Y', 8)
    cool.peg((6.9, 14.3, 2.2), .28, 7.4, 'X', 8)
    cool.peg((3.4, 14.3, 2.2), .36, .5, 'X', 8)        # elbow collars
    cool.peg((10.45, 14.55, 2.2), .36, .5, 'Y', 8)     # gland into the casing
    for x in (8.8, 9.9):
        clamps.box((x, 14.45, 2.2), (.3, .4, .9))
    # Aft feed: from the collar along the aft casing's inboard wall into the
    # thrust housing, half sunk into the wall to stay inside the outline.
    for z, r in ((.8, .3), (-.9, .26)):
        fuel.peg((-13.6, 16.45, z), r, 10.0, 'X', 8)
    for x in (-10.0, -13.0, -16.8):
        clamps.box((x, 16.25, -.05), (.4, .3, 2.6))
    fuel.build(kit, f'{P} fuel lines', kit.steel)
    cool.build(kit, f'{P} coolant line', kit.rust)
    clamps.build(kit, f'{P} line clamps', kit.dark)


def detail_hatches(kit, P, side):
    """Bolted maintenance hatches: a dark frame gap round a proud cover."""
    hatches = [  # name, centre, frame size, normal axis
        ('forward casing hatch', (9.5, 28.42, .3), (2.4, .12, 5.6), 'Y'),
        ('collar keel hatch', (-.4, 22, -7.16), (6.4, 5.4, .12), 'Z'),
        ('thrust housing hatch', (-22.7, 29.5, 0), (1.5, .12, 4.4), 'Y'),
    ]
    for name, c, d, ax in hatches:
        kit.box(f'{P} {name} frame', (c[0], side*c[1], c[2]), d, kit.dark, 0)
        k = {'X': 0, 'Y': 1, 'Z': 2}[ax]
        sgn = 1 if c[k] > 0 else -1
        pc = list(c)
        pc[k] += sgn*.08
        pd = [v-.36 for v in d]
        pd[k] = .14
        kit.box(f'{P} {name} cover', (pc[0], side*pc[1], pc[2]), pd, kit.steel, .04)
    h = Batch(side)
    h.box((9.5, 28.62, 1.6), (.9, .14, .18))
    h.box((-22.7, 29.7, 1.2), (.7, .14, .18))
    h.box((-2.6, 22, -7.36), (.18, 1.4, .14))
    h.box((1.8, 22, -7.36), (.18, 1.4, .14))
    h.build(kit, f'{P} hatch handles', kit.dark)
