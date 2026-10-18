"""Shepherd starboard side: the repair gantry, the turret deck aft of it and
the starboard outrigger frame.

Read off drone_tender.png: the starboard third (Y -11..-27, X 7..33) is
open grey machinery in a lattice - the built-in repair_gantry - flanked by
burnt-orange rails along its outboard edge (Y -24..-27). Aft of it a grey
lattice deck (X -14..7) carries the round mount of turret WS 007 at
(-3, -23). The hardpoint WS 008 at (27, -19) faces forward out of the
gantry's forward end. Outboard of both (Y -27..-32) the sprite shows a
tan lattice rail, the starboard outrigger, with a dark fender at its bow end.

Pass 3 (owner review: "steel square caging on the front right"): the
sprite's starboard bow is no cage. Read pixel by pixel it is one long rounded
machinery drum lying fore and aft (Y -12..-24, X 9..34, lit along its crown
at Y -17..-20 and rounding off at the bow, X 31..34), the burnt-orange rails
outboard of it and the outrigger beyond. So the lattice columns, girders and
travelling crane are gone, and so is the top-deck plant they served; the
core is that drum, on cradle saddles over the base deck, with the WS 008 bed
as a round pedestal on its forward crown. The rails, the operator cab, the
turret deck and the outrigger stand as pass 2 left them.

Pass 2 reading of the repair_gantry as working repair plant (superseded for
the frame, crane, floodlights and top-deck plant):
  * four lattice columns and lattice box girders: the frame is open
    structure, not solid bars; the two long top girders are the crane
    runways;
  * a double-girder travelling bridge crane on those runways, with a
    trolley, a hoist drum, two cable falls, a sheave block and a hook,
    parked over the work bed;
  * the machinery core (pass 3: a chamfered pod, flat-topped, whose nose
    rounds down in steps the way the sprite's starboard bow curves, in
    place of the pass-1 box) carries the plant on its top deck: a fabricator at the aft
    end feeding a dark work bed with clamp jaws, two folding manipulator /
    welding arms at its corners, a compressor pair, a tool rack, cable
    reels either side of the hardpoint bed, pipe runs and an aft manifold;
  * work floodlights hung under the top girders, aimed at the bed;
  * an operator cab cantilevered over the outrigger walkway, glazed toward
    the bed;
  * burnt-orange outboard mounting rails as an upper and lower rail on
    brackets, so the core's outboard plant shows between them.
The turret deck is a lattice-edged platform with plating laid round a clear
ring (radius 5) for the WS 007 barbette and a low outboard handrail that
stays under the barrels' sweep (nothing in barrel reach above deck top +2.5).

Names: 'Gantry ...' and 'Frame starboard ...'.
"""
import math
import bpy
from mathutils import Vector
from sh_kit import prism, section, material, loft_x, octagon, pod, ngon, boolean

Z0 = -12.0                                 # base deck height (ENVELOPE.md)
CORE_BOT, CORE_TOP = -10.0, 6.0            # rail heights (the pass-1 core envelope)
# The machinery drum (pass 3): a 12-sided body lying along X, flats top and
# bottom, rounding off at the bow; stations (x, half-width, half-height).
DRUM_Y, DRUM_Z = -18.5, -1.6
DRUM_ST = [(8.6, 4.8, 6.6), (9.6, 6.0, 8.0), (27.0, 6.0, 8.0), (30.2, 5.3, 7.0), (32.6, 4.0, 5.2),
           (34.0, 2.2, 2.8)]
DRUM_N = 12
SADDLE_X = (12.0, 20.0, 27.0)
# WS 008 sits recessed in a well cut into the drum's forward crown (owner, pass 3),
# with a trough running forward for the barrel. Sized for the hardpoint's 20 deg arc.
WELL = (24.0, 30.6, -19.0, 2.6, 2.6)       # x0, x1, centre y, half-width, floor z
TROUGH = (30.4, 35.5, 1.85, 2.95)          # x0, x1, half-width, floor z

BED_BOT, BED_TOP = 1.4, 2.62               # 'Gantry forward hardpoint bed' (WS 008 seats on its top)
DECK_BOT, DECK_TOP = -6.0, 3.0             # 'Frame starboard turret deck' (WS 007 seats on its top)
OUT_BOT, OUT_TOP = -9.0, 5.0               # starboard outrigger envelope
UP_BOT, UP_TOP = OUT_TOP-2.5, OUT_TOP-.5   # outrigger upper truss; walkway plate OUT_TOP-.5..OUT_TOP
LOW_Z = OUT_BOT+1.0                        # outrigger lower rail centre (1.6 deep)
FENDER_Z, FENDER_H = -2.0, 8.0
RAIL_UP = (CORE_TOP-1.5, CORE_TOP)         # orange outboard rails
RAIL_LO = (CORE_BOT+2.0, CORE_BOT+3.5)
TURRET = (-3.0, -23.0)                     # WS 007; keep r 5 clear and nothing tall in its sweep


class _Parts:
    """Many small solids merged into one flat-shaded mesh object."""

    def __init__(self):
        self.v, self.f = [], []

    def _hexa(self, pts):
        n = len(self.v)
        self.v += [tuple(p) for p in pts]
        for q in ((0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)):
            self.f.append(tuple(n+i for i in q))

    def box(self, c, d):
        cx, cy, cz = c
        hx, hy, hz = d[0]/2, d[1]/2, d[2]/2
        self._hexa([(cx-hx, cy-hy, cz-hz), (cx+hx, cy-hy, cz-hz), (cx+hx, cy+hy, cz-hz), (cx-hx, cy+hy, cz-hz),
                    (cx-hx, cy-hy, cz+hz), (cx+hx, cy-hy, cz+hz), (cx+hx, cy+hy, cz+hz), (cx-hx, cy+hy, cz+hz)])

    def strut(self, a, b, t, t2=None):
        """Square (t x t2) bar from a to b."""
        a, b = Vector(a), Vector(b)
        d = b-a
        if d.length < 1e-6:
            return
        d.normalize()
        ref = Vector((0, 0, 1)) if abs(d.z) < .95 else Vector((1, 0, 0))
        u = d.cross(ref).normalized()*(t/2)
        v = d.cross(u).normalized()*((t if t2 is None else t2)/2)
        self._hexa([a-u-v, a+u-v, a+u+v, a-u+v, b-u-v, b+u-v, b+u+v, b-u+v])

    def cyl(self, c, r, depth, axis='Z', n=10):
        c = Vector(c)
        ax = {'X': Vector((1, 0, 0)), 'Y': Vector((0, 1, 0)), 'Z': Vector((0, 0, 1))}[axis]
        e1 = Vector((0, 0, 1)) if axis != 'Z' else Vector((1, 0, 0))
        e2 = ax.cross(e1)
        base = len(self.v)
        for s in (-.5, .5):
            for i in range(n):
                a = 2*math.pi*(i+.5)/n
                self.v.append(tuple(c+ax*(depth*s)+e1*(r*math.cos(a))+e2*(r*math.sin(a))))
        self.f.append(tuple(base+i for i in range(n)))
        self.f.append(tuple(base+n+i for i in reversed(range(n))))
        for i in range(n):
            j = (i+1) % n
            self.f.append((base+i, base+j, base+n+j, base+n+i))

    def truss(self, p0, p1, side, w, h, chord=.3, lace=.16, bay=1.8, faces=(0, 1, 2, 3)):
        """Lattice box girder p0 -> p1: four chords, battens and zig-zag lacing.
        Face k joins corner k to k+1 (0 bottom, 1 +side, 2 top, 3 -side)."""
        p0, p1 = Vector(p0), Vector(p1)
        ax = p1-p0
        d = ax.normalized()
        s = Vector(side)
        s = (s-d*s.dot(d)).normalized()
        up = d.cross(s).normalized()
        if up.z < 0 or (abs(up.z) < 1e-6 and up.y < 0):
            up = -up
        hw, hh = w/2-chord/2, h/2-chord/2
        off = [s*a+up*b for a, b in ((-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh))]
        for o in off:
            self.strut(p0+o, p1+o, chord)
        n = max(1, round(ax.length/bay))
        for i in range(n+1):
            q = p0+ax*(i/n)
            for k in faces:
                self.strut(q+off[k], q+off[(k+1) % 4], lace)
        for i in range(n):
            q0, q1 = p0+ax*(i/n), p0+ax*((i+1)/n)
            for k in faces:
                a, b = off[k], off[(k+1) % 4]
                if (i+k) % 2:
                    a, b = b, a
                self.strut(q0+a, q1+b, lace)

    def make(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel)


def _inside(poly, x, y):
    c = False
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i-1], poly[i]
        if (ay > y) != (by > y) and x < ax+(y-ay)*(bx-ax)/(by-ay):
            c = not c
    return c


def _drum_top(x):
    """Crown height of the drum at x (the flat top face)."""
    for (xa, _, za), (xb, _, zb) in zip(DRUM_ST, DRUM_ST[1:]):
        if xa <= x <= xb:
            rz = za+(zb-za)*(x-xa)/(xb-xa)
            return DRUM_Z+rz*math.sin(math.radians(75))
    return DRUM_Z


def _skin(core, x, y):
    """Height of the drum's outer skin at (x, y), by a downward ray (None off the drum)."""
    bpy.context.view_layer.update()
    ev = core.evaluated_get(bpy.context.evaluated_depsgraph_get())
    hit, p, _, _ = ev.ray_cast(Vector((x, y, 40)), Vector((0, 0, -1)))
    return p.z if hit else None


def _rim(kit, name, core, edges, w, h, mat, step=.25):
    """One continuous coaming band laid on the skin along each edge [(a, b), ...]:
    w wide, standing h proud, its foot sunk a little so it never floats."""
    verts, faces = [], []
    for (ax, ay), (bx, by) in edges:
        L = math.hypot(bx-ax, by-ay)
        dx, dy = (bx-ax)/L, (by-ay)/L
        ax, ay, bx, by = ax-dx*w/2, ay-dy*w/2, bx+dx*w/2, by+dy*w/2   # overlap at the corners
        nx, ny = -dy*w/2, dx*w/2
        n = max(1, round((L+w)/step))
        base = len(verts)
        rows = 0
        for k in range(n+1):
            x, y = ax+(bx-ax)*k/n, ay+(by-ay)*k/n
            zi, zo = _skin(core, x-nx, y-ny), _skin(core, x+nx, y+ny)
            if zi is None or zo is None:
                continue
            verts += [(x-nx, y-ny, zi-.2), (x+nx, y+ny, zo-.2), (x+nx, y+ny, zo+h), (x-nx, y-ny, zi+h)]
            rows += 1
        for r in range(rows-1):
            a, b = base+4*r, base+4*(r+1)
            faces += [(a+q, a+(q+1) % 4, b+(q+1) % 4, b+q) for q in range(4)]
        if rows > 1:
            faces += [(base, base+1, base+2, base+3), tuple(base+4*(rows-1)+q for q in (3, 2, 1, 0))]
    return kit.mesh(name, verts, faces, mat, .02)


def _well(kit, core):
    """The WS 008 well, finished the way a recessed weapon pocket would be: a flush
    bolted armour coaming round the cut edge (following the drum's skin, not a
    level frame), a lined barrel trough with a ribbed blast plate under the muzzle,
    and drain grilles either side of the cradle. No hazard paint: nobody works on
    the drum's crown, and a hardpoint that traverses +-10 deg has no crush zone."""
    x0, x1, wy, hw, fz = WELL
    tx0, tx1, thw, tz = TROUGH
    bolts = _Parts()
    e = .3                                                     # coaming centreline outside the cut
    edge = [(x1, wy-thw-e), (x1, wy-hw-e), (x0-e, wy-hw-e), (x0-e, wy+hw+e), (x1, wy+hw+e), (x1, wy+thw+e)]
    runs = list(zip(edge, edge[1:]))+[((x1, wy+sg*(thw+e)), (34.0, wy+sg*(thw+e))) for sg in (-1, 1)]
    _rim(kit, 'Gantry hardpoint well coaming', core, runs, .55, .14, kit.steel)
    for (ax, ay), (bx, by) in zip(edge, edge[1:]):
        n = max(1, round(math.hypot(bx-ax, by-ay)/1.1))
        for k in range(n+1):
            x, y = ax+(bx-ax)*k/n, ay+(by-ay)*k/n
            z = _skin(core, x, y)
            if z is not None:
                bolts.box((x, y, z+.17), (.16, .16, .08))
    bolts.make(kit, 'Gantry hardpoint well coaming bolts', kit.dark)
    # Trough liner plates and the ribbed blast plate under the muzzle.
    lin, rib = _Parts(), _Parts()
    lin.box(((tx0+tx1)/2, wy, tz+.05), (tx1-tx0, 2*thw-.2, .1))
    for k in range(7):
        rib.box((32.2+k*.36, wy, tz+.16), (.14, 2*thw-.5, .12))
    lin.make(kit, 'Gantry hardpoint trough liner', kit.steel, .02)
    rib.make(kit, 'Gantry hardpoint trough blast plate ribs', kit.dark)
    # Drain grilles in the well floor, on the side strips clear of the cradle.
    g, sl = _Parts(), _Parts()
    for gy in (wy-hw+.38, wy+hw-.38):
        g.box(((x0+x1)/2, gy, BED_TOP+.02), (x1-x0-1.0, .5, .06))
        for k in range(10):
            sl.box((x0+.7+k*(x1-x0-1.4)/9, gy, BED_TOP+.06), (.12, .42, .06))
    g.make(kit, 'Gantry hardpoint well drain grilles', kit.dark)
    sl.make(kit, 'Gantry hardpoint well drain grille slats', kit.steel)


def _drum_plant(kit):
    """Frame bands, the crown plate and hatches, outboard covers and grilles, cradle saddles."""
    s75, c15 = math.sin(math.radians(75)), math.cos(math.radians(15))
    # Frame bands round the drum (dark rings a hair proud of the skin).
    for k, x in enumerate((12.0, 16.0, 20.0, 23.3), 1):
        pod(kit, f'Gantry drum frame band {k}', [(x-.13, 6.12, 8.12), (x+.13, 6.12, 8.12)], DRUM_Y, DRUM_Z,
            kit.dark, DRUM_N, 0)
    crown = DRUM_Z+8.0*s75
    # The lit crown the sprite shows (Y -17..-20): a pale plate down the flat top.
    p = _Parts()
    p.box((16.6, DRUM_Y, crown+.06), (13.2, 3.0, .12))
    p.make(kit, 'Gantry drum crown plate', kit.cream, .03)
    p = _Parts()
    for x in (11.2, 21.6):
        p.box((x, DRUM_Y, crown+.1), (1.8, 2.2, .2))
    p.make(kit, 'Gantry drum crown hatches', kit.dark, .03)
    # Outboard vertical facet (Y = DRUM_Y - 6 cos 15): bolted covers and grilles.
    yo = DRUM_Y-6.0*c15
    fz = DRUM_Z
    p = _Parts()
    for x in (14.8, 22.8):
        p.box((x, yo-.06, fz), (3.0, .14, 3.2))
    p.make(kit, 'Gantry core service covers', kit.grey)
    p = _Parts()
    for x in (14.8, 22.8):
        for du in (-1.2, 1.2):
            for dz in (-1.3, 1.3):
                p.box((x+du, yo-.15, fz+dz), (.18, .08, .18))
    p.make(kit, 'Gantry core bolt heads', kit.steel)
    p = _Parts()
    for x in (18.8, 26.0):
        p.box((x, yo-.02, fz), (2.6, .1, 2.8))
    p.make(kit, 'Gantry core grille recesses', kit.dark)
    p = _Parts()
    for x in (18.8, 26.0):
        for k in range(5):
            p.box((x, yo-.1, fz-1.0+k*.5), (2.4, .1, .14))
    p.make(kit, 'Gantry core grille slats', kit.steel)
    # Forward access hatch on the nose's last face.
    xn, ry, rz = DRUM_ST[-1]
    p = _Parts()
    p.box((xn+.06, DRUM_Y, DRUM_Z), (.14, 2.4, 2.6))
    p.make(kit, 'Gantry core forward access door', kit.grey)
    # Aft manifold and valve wheels on the aft cap.
    p = _Parts()
    p.box((8.35, DRUM_Y, DRUM_Z-1.2), (.5, 7.0, 1.6))
    for y in (-21.0, -18.5, -16.0):
        p.strut((8.1, y, DRUM_Z-1.2), (7.6, y, DRUM_Z-1.2), .2)
        p.cyl((7.5, y, DRUM_Z-1.2), .45, .1, 'X', 10)
    p.make(kit, 'Gantry core aft manifold', kit.orange, .03)
    # Pipe runs along the inboard flank to the manifold.
    yi = DRUM_Y+6.0*c15+.35
    p = _Parts()
    for z in (DRUM_Z+1.0, DRUM_Z-2.2):
        p.strut((8.4, yi, z), (29.0, yi, z), .44)
    for x in (12, 17, 22, 27):
        p.box((x, yi-.2, DRUM_Z-.6), (.3, .4, 3.8))
    p.make(kit, 'Gantry core piping', kit.dark)
    # Cradle saddles carrying the drum on the base deck.
    floor = Z0+.6
    for k, x in enumerate(SADDLE_X, 1):
        under = DRUM_Z-8.0*s75
        sec = [(DRUM_Y-4.6, floor), (DRUM_Y+4.6, floor), (DRUM_Y+4.6, under+1.4), (DRUM_Y+2.1, under+.3),
               (DRUM_Y-2.1, under+.3), (DRUM_Y-4.6, under+1.4)]
        loft_x(kit, f'Gantry drum cradle saddle {k}', [(x-.8, sec), (x+.8, sec)], kit.steel, .06)


def _cab(kit):
    # Operator cab cantilevered outboard over the outrigger walkway.
    cx0, cx1, cy0, cy1 = 9.2, 12.8, -29.6, -26.9
    cz0 = OUT_TOP+1.2
    cz1 = cz0+3.2
    kit.box('Gantry control cab', ((cx0+cx1)/2, (cy0+cy1)/2, (cz0+cz1)/2), (cx1-cx0, cy1-cy0, cz1-cz0),
            kit.tan, .15)
    p = _Parts()
    p.box(((cx0+cx1)/2, (cy0+cy1)/2, cz1+.15), (cx1-cx0+.4, cy1-cy0+.4, .3))     # roof
    p.box((cx0+.8, (cy0+cy1)/2, cz1+.55), (.8, .8, .5))                          # roof air plant
    p.strut((cx1-.5, cy0+.4, cz1+.3), (cx1-.5, cy0+.4, cz1+1.6), .08)           # aerial
    p.box(((cx0+cx1)/2, (cy0+cy1)/2, cz0-.1), (cx1-cx0+.2, cy1-cy0+.2, .2))      # floor
    for x in (cx0+.3, cx1-.3):                                                   # cantilever brackets
        p.strut((x, cy1-.2, cz0-.2), (x, -26.0, RAIL_UP[0]+.2), .26)
        p.strut((x, cy0+.3, cz0-.2), (x, cy0+.3, OUT_TOP), .26)
    p.make(kit, 'Gantry control cab frame', kit.steel, .03)
    g = _Parts()
    g.box((cx1+.03, (cy0+cy1)/2, cz0+1.9), (.1, cy1-cy0-.6, 1.3))              # forward glazing
    g.box(((cx0+cx1)/2+.3, cy1+.03, cz0+1.9), (cx1-cx0-1.2, .1, 1.3))          # inboard glazing
    g.make(kit, 'Gantry control cab glazing', kit.glass)
    p = _Parts()
    p.box((cx0-.03, (cy0+cy1)/2, cz0+1.25), (.1, 1.2, 2.3))                     # aft door
    p.box((cx0-.08, (cy0+cy1)/2-.4, cz0+1.2), (.1, .12, .3))                    # door handle
    p.make(kit, 'Gantry control cab door', kit.dark)


def _rails(kit):
    # Burnt-orange outboard mounting rails: upper and lower rails on brackets.
    zu, zl = sum(RAIL_UP)/2, sum(RAIL_LO)/2
    kit.box('Gantry outboard mounting rail', (20, -26.2, zu), (27, 1.6, RAIL_UP[1]-RAIL_UP[0]), kit.orange, .15)
    kit.box('Gantry outboard mounting rail lower', (20, -26.2, zl), (27, 1.6, RAIL_LO[1]-RAIL_LO[0]), kit.orange, .15)
    p = _Parts()
    for x in (11.5, 15.5, 20.25, 25, 29):
        p.box((x, -26.45, (RAIL_LO[1]+RAIL_UP[0])/2), (.9, .7, RAIL_UP[0]-RAIL_LO[1]+.2))
        p.strut((x+.4, -26.5, RAIL_LO[1]+.4), (x+2.6, -26.5, RAIL_UP[0]-.4), .3)   # diagonal tie
    p.make(kit, 'Gantry outboard rail brackets', kit.orange, .04)
    p = _Parts()
    for x in (7.0, 33.0):
        p.box((x, -26.2, zu), (.5, 1.9, RAIL_UP[1]-RAIL_UP[0]+.3))
        p.box((x, -26.2, zl), (.5, 1.9, RAIL_LO[1]-RAIL_LO[0]+.3))
    for x in (11.5, 15.5, 20.25, 25, 29):
        for z in (zu, zl):
            p.box((x, -27.05, z), (.3, .12, .3))
    p.make(kit, 'Gantry outboard rail clamps', kit.dark)


def _turret_deck(kit):
    plan = [(-14, -30), (-10, -32.5), (6, -32.5), (7, -30), (7, -11), (-14, -11)]
    kc, sc = 1.2, .8
    deck = prism(kit, 'Frame starboard turret deck', plan, section(DECK_BOT, DECK_TOP, kc, sc), kit.steel)
    kit.hull.append(deck)
    t = DECK_TOP
    # Deck plating laid round a clear ring for the WS 007 barbette.
    p = _Parts()
    tx, ty = TURRET
    step, size = 2.9, 2.62
    x = -8.8
    while x+size <= 6.4:
        y = -11.6
        while y-size >= -31.9:
            cx, cy = x+size/2, y-size/2
            corners = [(x, y), (x+size, y), (x+size, y-size), (x, y-size)]
            nx, ny = min(max(tx, x), x+size), min(max(ty, y-size), y)
            if all(_inside(plan, a, b) for a, b in corners) and math.hypot(nx-tx, ny-ty) > 5.2:
                p.box((cx, cy, t+.07), (size, size, .14))
            y -= step
        x += step
    p.make(kit, 'Frame starboard turret deck plating', kit.grey, .03)
    # Kerb round the barbette's clear ring, a hatch to the ammunition hoist and a locker.
    p = _Parts()
    n = 24
    for i in range(n):
        a0, a1 = 2*math.pi*i/n, 2*math.pi*(i+1)/n
        p.strut((tx+5.0*math.cos(a0), ty+5.0*math.sin(a0), t+.04), (tx+5.0*math.cos(a1), ty+5.0*math.sin(a1), t+.04), .22, .08)
    p.make(kit, 'Frame starboard turret deck ring kerb', kit.dark)
    p = _Parts()
    p.box((3.6, -14.0, t+.1), (2.0, 2.0, .2))
    p.box((3.6, -14.0, t+.25), (.8, .12, .1))
    p.box((-6.5, -13.6, t+.5), (2.2, 1.4, 1.0))                            # ready-use locker
    p.make(kit, 'Frame starboard turret deck hatch and locker', kit.tan, .03)
    # Outboard wall: two tiers of X-braced lattice over a dark recess, and a low handrail.
    w0, w1 = DECK_BOT+kc+.2, DECK_TOP-sc-.2
    kit.box('Frame starboard turret deck edge recess', (-2.0, -32.53, (w0+w1)/2), (15.0, .08, w1-w0), kit.dark, 0)
    p = _Parts()
    wm = (w0+w1)/2
    for z0, z1 in ((w0, wm), (wm, w1)):
        for k in range(7):
            x0 = -9.5+k*2.14
            x1 = x0+2.14
            p.strut((x0, -32.62, z0), (x1, -32.62, z1), .2)
            p.strut((x0, -32.62, z1), (x1, -32.62, z0), .2)
    for k in range(8):
        x0 = -9.5+k*2.14
        p.strut((x0, -32.62, w0-.1), (x0, -32.62, w1+.1), .22)
    for z in (w0, wm, w1):
        p.strut((-9.5, -32.62, z), (5.5, -32.62, z), .22)
    p.make(kit, 'Frame starboard turret deck edge truss', kit.grey)
    p = _Parts()
    for k in range(9):
        x = -8.8+k*1.75
        p.strut((x, -32.15, t), (x, -32.15, t+1.2), .12)
    p.strut((-8.8, -32.15, t+1.2), (5.2, -32.15, t+1.2), .12)
    p.strut((-8.8, -32.15, t+.6), (5.2, -32.15, t+.6), .08)
    p.make(kit, 'Frame starboard turret deck handrail', kit.cream)


def _outrigger(kit):
    # Upper rail: a tan box truss; a walkway plate over the stretch outboard of the gantry.
    zu = (UP_BOT+UP_TOP)/2
    p = _Parts()
    p.truss((-14, -29.25, zu), (31, -29.25, zu), (0, 1, 0), 4.0, UP_TOP-UP_BOT, .34, .18, 2.0)
    p.make(kit, 'Frame starboard outrigger upper truss', kit.tan)
    kit.box('Frame starboard outrigger upper rail', (19.0, -29.25, (UP_TOP+OUT_TOP)/2), (24.0, 4.0, OUT_TOP-UP_TOP),
            kit.tan, .1)
    p = _Parts()
    for k in range(12):
        p.box((7.6+k*2.0, -29.25, OUT_TOP+.02), (.12, 3.6, .06))
    p.make(kit, 'Frame starboard outrigger walkway seams', kit.dark)
    p = _Parts()
    for k in range(13):
        x = 7.5+k*1.95
        if 19.0 < x < 25.0:
            continue                                                       # fender bracket here
        p.strut((x, -31.0, OUT_TOP), (x, -31.0, OUT_TOP+1.2), .12)
    p.strut((7.5, -31.0, OUT_TOP+1.2), (19.0, -31.0, OUT_TOP+1.2), .12)
    p.strut((25.0, -31.0, OUT_TOP+1.2), (30.9, -31.0, OUT_TOP+1.2), .12)
    p.make(kit, 'Frame starboard outrigger walkway handrail', kit.cream)
    # Lower rail: a steel lattice girder.
    p = _Parts()
    p.truss((-12, -29.5, LOW_Z), (28, -29.5, LOW_Z), (0, 1, 0), 2.5, 1.6, .3, .16, 2.0)
    p.make(kit, 'Frame starboard outrigger lower rail', kit.steel)
    lo_top, up_bot = LOW_Z+.8, UP_BOT
    # Posts: small lattice towers between the rails.
    for i, x in enumerate((-12, 0, 14, 28), 1):
        p = _Parts()
        p.truss((x, -28.5, lo_top), (x, -28.5, up_bot), (1, 0, 0), 1.4, 1.4, .28, .14, 1.4)
        p.make(kit, f'Frame starboard outrigger post {i}', kit.steel)
    # Outboard skirt between the rails: dark armour plates, the solid dark band
    # the sprite shows outboard of the drum (pass 3: no open bracing here).
    p, q = _Parts(), _Parts()
    zl, zh = lo_top-.15, up_bot+.15
    edges = [7.0+k*3.0 for k in range(9)]
    for a, b in zip(edges, edges[1:]):
        p.box(((a+b)/2, -30.55, (zl+zh)/2), (b-a-.16, .4, zh-zl))
        for x in (a+.4, b-.4):
            for z in (zl+.4, zh-.4):
                q.box((x, -30.8, z), (.18, .1, .18))
    p.make(kit, 'Frame starboard outrigger side plating', kit.dark, .04)
    q.make(kit, 'Frame starboard outrigger side plating bolts', kit.steel)
    # Bow fender: a dark bumper on a bracket, with steel bands.
    fz, fh = FENDER_Z, FENDER_H
    kit.cylinder('Frame starboard bow fender', (22, -31.2, fz), 2.4, fh, kit.dark, 'Z', 12, .15)
    p = _Parts()
    for z in (fz-fh/4, fz, fz+fh/4):
        p.cyl((22, -31.2, z), 2.5, .3, 'Z', 12)
    p.cyl((22, -31.2, fz+fh/2+.15), 1.4, .3, 'Z', 12)
    for dx in (-1.2, 1.2):
        p.strut((22+dx, -30.0, fz+fh/2+.2), (22+dx, -28.8, UP_BOT+.6), .35)
        p.strut((22+dx, -30.0, fz-fh/2), (22+dx, -29.5, lo_top-.1), .35)
    p.make(kit, 'Frame starboard bow fender bands and bracket', kit.steel, .03)


def build(kit):
    core = pod(kit, 'Gantry machinery core', DRUM_ST, DRUM_Y, DRUM_Z, kit.grey, DRUM_N, .2)
    # The base deck's forward corners round off with the drum and the bow.
    base = prism(kit, 'Gantry base deck', [(7, -29.5), (29.5, -29.5), (33, -25.5), (33, -15.0),
                                           (30.0, -11), (7, -11)],
                 section(Z0-1.2, Z0+.6, .4, .3), kit.steel)
    # The hardpoint well and the barrel trough, cut out of the drum (dark walls).
    x0, x1, wy, hw, fz = WELL
    for name, (cx0, cx1, chw, cz) in (('well', (x0, x1, hw, fz)), ('trough', TROUGH)):
        cutter = kit.box(f'Temporary hardpoint {name} cutter', ((cx0+cx1)/2, wy, cz+10), (cx1-cx0, 2*chw, 20),
                         kit.dark, 0)
        boolean(core, cutter)
    # The bed is a seat: WS 008 ray-casts onto its top, the well floor plate.
    bed = kit.box('Gantry forward hardpoint bed', ((x0+x1)/2, wy, (BED_BOT+BED_TOP)/2),
                  (x1-x0-.3, 2*hw-.3, BED_TOP-BED_BOT), kit.steel, .04)
    kit.hull += [core, base, bed]
    _well(kit, core)
    _drum_plant(kit)
    _cab(kit)
    _rails(kit)
    _turret_deck(kit)
    _outrigger(kit)
