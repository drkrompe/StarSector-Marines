"""Shepherd starboard side: the repair gantry, the turret deck aft of it and
the starboard outrigger frame.

Read off drone_tender.png: the starboard third (Y -11..-27, X 7..33) is
open grey machinery in a lattice - the built-in repair_gantry - flanked by
burnt-orange rails along its outboard edge (Y -24..-27). Aft of it a grey
lattice deck (X -14..7) carries the round mount of turret WS 007 at
(-3, -23). The hardpoint WS 008 at (27, -19) faces forward out of the
gantry's forward end. Outboard of both (Y -27..-32) the sprite shows a
tan lattice rail, the starboard outrigger, with a dark fender at its bow end.

Pass 2 reading of the repair_gantry as working repair plant:
  * four lattice columns and lattice box girders: the frame is open
    structure, not solid bars; the two long top girders are the crane
    runways;
  * a double-girder travelling bridge crane on those runways, with a
    trolley, a hoist drum, two cable falls, a sheave block and a hook,
    parked over the work bed;
  * the machinery core (kept as the pass-1 volume, which the hardpoint
    seats on) carries the plant on its top deck: a fabricator at the aft
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
from mathutils import Vector
from sh_kit import prism, section, material

X0, X1, Y0, Y1 = 8.0, 32.5, -12.0, -26.0   # gantry frame extents (column centres)
Z0, Z1 = -12.0, 15.0                       # bottom and top girder heights (ENVELOPE.md)
CORE_BOT, CORE_TOP = -10.0, 6.0            # 'Gantry machinery core'
CZ = (CORE_BOT+CORE_TOP)/2
BED_BOT, BED_TOP = -4.0, 8.0               # 'Gantry forward hardpoint bed' (WS 008 seats on its top)
DECK_BOT, DECK_TOP = -6.0, 3.0             # 'Frame starboard turret deck' (WS 007 seats on its top)
OUT_BOT, OUT_TOP = -9.0, 5.0               # starboard outrigger envelope
UP_BOT, UP_TOP = OUT_TOP-2.5, OUT_TOP-.5   # outrigger upper truss; walkway plate OUT_TOP-.5..OUT_TOP
LOW_Z = OUT_BOT+1.0                        # outrigger lower rail centre (1.6 deep)
FENDER_Z, FENDER_H = -2.0, 8.0
RAIL_UP = (CORE_TOP-1.5, CORE_TOP)         # orange outboard rails
RAIL_LO = (CORE_BOT+2.0, CORE_BOT+3.5)
FAB_H = 4.4                                # fabricator housing height above the core top
TURRET = (-3.0, -23.0)                     # WS 007; keep r 5 clear and nothing tall in its sweep
BED_X = 17.5                               # crane parked over the work bed centre
BED_Y = -19.0


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


def _frame(kit):
    # Columns: lattice towers on the corner nodes.
    for i, (x, y) in enumerate(((X0, Y0), (X1, Y0), (X1, Y1), (X0, Y1)), 1):
        p = _Parts()
        p.truss((x, y, Z0-.8), (x, y, Z1+.8), (1, 0, 0), 1.6, 1.6, .34, .17, 1.7)
        p.box((x, y, Z0+.75), (2.2, 2.2, .3))           # base plate on the deck
        p.make(kit, f'Gantry column {i}', kit.steel)
    # Long girders: the top pair are the crane runways.
    for z, tag in ((Z1, 'top'), (Z0, 'bottom')):
        for y, side in ((Y0, 'inboard'), (Y1, 'outboard')):
            p = _Parts()
            p.truss((X0, y, z), (X1, y, z), (0, 1, 0), 1.6, 1.6, .32, .16, 1.75)
            p.make(kit, f'Gantry {tag} {side} girder', kit.steel)
    for i, x in enumerate((X0, (X0+X1)/2, X1), 1):
        p = _Parts()
        p.truss((x, Y0, Z1), (x, Y1, Z1), (1, 0, 0), 1.6, 1.6, .32, .16, 1.75)
        p.make(kit, f'Gantry top cross girder {i}', kit.steel)
    # Gusset plates at the nodes where girders meet the columns.
    p = _Parts()
    for x in (X0, X1):
        for y in (Y0, Y1):
            for z in (Z1, Z0):
                for dx in (-.86, .86):
                    p.box((x+dx, y, z), (.1, 1.9, 1.9))
                for dy in (-.86, .86):
                    p.box((x, y+dy, z), (1.9, .1, 1.9))
    for y in (Y0, Y1):                                   # mid cross girder seats
        p.box(((X0+X1)/2, y, Z1-.86), (2.0, 1.9, .1))
    p.make(kit, 'Gantry node gusset plates', kit.grey)
    # Runway rails on the top girders.
    for y, side in ((Y0, 'inboard'), (Y1, 'outboard')):
        kit.box(f'Gantry crane runway rail {side}', ((X0+X1)/2, y, Z1+.92), (X1-X0+1.2, .5, .25), kit.dark, .04)


def _crane(kit):
    zr = Z1+1.05                                   # runway rail top
    gx = (BED_X-.75, BED_X+.75)
    # End trucks riding the runways, with wheels on the rails.
    p = _Parts()
    for y in (Y0, Y1):
        p.box((BED_X, y, zr+.45), (4.6, 1.4, .8))
        p.box((BED_X+2.45, y, zr+.45), (.3, 1.6, .9))       # buffers
        p.box((BED_X-2.45, y, zr+.45), (.3, 1.6, .9))
    p.make(kit, 'Gantry crane end trucks', kit.orange, .05)
    p = _Parts()
    for y in (Y0, Y1):
        for x in (BED_X-1.6, BED_X+1.6):
            p.cyl((x, y, zr+.25), .32, 1.0, 'Y', 10)
    p.make(kit, 'Gantry crane truck wheels', kit.dark)
    # Bridge: two plate girders with stiffeners, spanning the runways.
    p = _Parts()
    for x in gx:
        p.box((x, (Y0+Y1)/2, zr+.75), (.34, Y0-Y1-.4, 1.0))
        p.box((x, (Y0+Y1)/2, zr+1.28), (.7, Y0-Y1-.4, .12))    # top flange
        p.box((x, (Y0+Y1)/2, zr+.22), (.7, Y0-Y1-.4, .12))     # bottom flange
        for k in range(1, 7):
            y = Y0+(Y1-Y0)*k/7
            p.box((x, y, zr+.75), (.6, .1, .9))                 # web stiffeners
    p.make(kit, 'Gantry crane bridge girders', kit.orange, .03)
    p = _Parts()
    for x in gx:
        p.box((x, (Y0+Y1)/2, zr+1.4), (.2, Y0-Y1-1.0, .14))
    p.make(kit, 'Gantry crane trolley rails', kit.dark)
    zt = zr+1.47
    # Trolley frame, hoist drum and motor.
    kit.box('Gantry crane trolley', (BED_X, BED_Y, zt+.3), (2.6, 2.8, .6), kit.orange, .08)
    p = _Parts()
    p.cyl((BED_X, BED_Y, zt+1.15), .62, 2.0, 'Y', 12)                  # drum
    p.cyl((BED_X, BED_Y-1.15, zt+1.15), .75, .14, 'Y', 12)             # flanges
    p.cyl((BED_X, BED_Y+1.15, zt+1.15), .75, .14, 'Y', 12)
    p.box((BED_X+.95, BED_Y+.9, zt+1.0), (.7, 1.2, .9))                # motor and gearbox
    p.box((BED_X-.95, BED_Y-.9, zt+.9), (.6, 1.0, .7))                 # brake housing
    p.make(kit, 'Gantry crane hoist', kit.dark, .03)
    # Two cable falls down between the bridge girders to the sheave block.
    zb = CORE_TOP+3.2
    p = _Parts()
    for dx in (-.22, .22):
        p.strut((BED_X+dx, BED_Y, zt+.9), (BED_X+dx, BED_Y, zb+.45), .09)
    p.make(kit, 'Gantry crane hoist cable', kit.dark)
    p = _Parts()
    p.box((BED_X, BED_Y, zb), (.9, .7, .9))
    p.cyl((BED_X, BED_Y, zb+.25), .42, .8, 'Y', 10)
    p.make(kit, 'Gantry crane sheave block', kit.orange, .04)
    # Hook: shank, swivel and a J of short bars.
    p = _Parts()
    p.strut((BED_X, BED_Y, zb-.45), (BED_X, BED_Y, zb-1.0), .2)
    p.cyl((BED_X, BED_Y, zb-1.05), .22, .18, 'Z', 8)
    pts = [(0, 0)]
    for k in range(7):
        a = math.radians(180+k*35)
        pts.append((.45+.45*math.cos(a), -.5+.45*math.sin(a)))
    for (ax_, az), (bx, bz) in zip(pts, pts[1:]):
        p.strut((BED_X+ax_, BED_Y, zb-1.1+az), (BED_X+bx, BED_Y, zb-1.1+bz), .18, .14)
    p.make(kit, 'Gantry crane hook', kit.steel)


def _core_plant(kit):
    t = CORE_TOP
    # Work bed: dark grated table with clamp jaws, where a Borer is serviced.
    kit.box('Gantry work bed', (BED_X, BED_Y, t+.15), (8.0, 7.0, .3), kit.dark, .05)
    p = _Parts()
    for k in range(9):
        p.box((BED_X-3.6+k*.9, BED_Y, t+.33), (.12, 6.6, .08))
    p.make(kit, 'Gantry work bed grating', kit.steel)
    p = _Parts()
    for sx in (-1, 1):
        for sy in (-1, 1):
            cx, cy = BED_X+sx*2.6, BED_Y+sy*2.2
            p.box((cx, cy, t+.55), (.9, .9, .5))
            p.box((cx-sx*.55, cy-sy*.55, t+.85), (.5, .5, .35))   # jaw
    p.make(kit, 'Gantry work bed clamp jaws', kit.orange, .04)

    # Fabricator at the aft end of the deck, its output port facing the bed.
    fx0, fx1 = 8.8, 13.2
    kit.box('Gantry fabricator housing', ((fx0+fx1)/2, -18.5, t+FAB_H/2), (fx1-fx0, 10.0, FAB_H), kit.grey, .18)
    p = _Parts()
    p.box((fx1+.02, -18.5, t+1.6), (.12, 4.6, 2.4))                    # output port
    for k in range(6):
        p.box((10.0+k*.42, -15.4, t+FAB_H+.03), (.2, 2.8, .06))         # roof vent slats
    for k in range(6):
        p.box((10.0+k*.42, -21.6, t+FAB_H+.03), (.2, 2.8, .06))
    p.make(kit, 'Gantry fabricator port and vents', kit.dark)
    p = _Parts()
    p.box((fx1+.18, -18.5, t+FAB_H-.4), (.4, 5.4, .3))                 # print-head beam
    p.box((fx1+.45, -18.0, t+FAB_H-.8), (.5, .7, .6))                   # head carriage
    p.strut((fx1+.45, -18.0, t+FAB_H-1.1), (fx1+.45, -18.0, t+1.3), .18)  # nozzle
    p.box((fx1+.06, -13.9, t+FAB_H/2), (.12, 1.2, FAB_H-.9))           # bolted service cover
    p.box((fx1+.06, -23.1, t+FAB_H/2), (.12, 1.2, FAB_H-.9))
    p.make(kit, 'Gantry fabricator print head', kit.steel, .02)

    # Folding manipulator / welding arms at two corners of the bed.
    for i, (bx, by, yaw_to, fold) in enumerate(((14.2, -13.7, (16.2, -16.6), 1), (21.0, -23.8, (19.0, -21.2), -1)), 1):
        yaw = math.atan2(yaw_to[1]-by, yaw_to[0]-bx)
        d = Vector((math.cos(yaw), math.sin(yaw), 0))
        side = Vector((-d.y, d.x, 0))
        base = Vector((bx, by, t))
        sh = base+Vector((0, 0, 1.0))
        el = sh+d*(2.6*math.cos(math.radians(62)))+Vector((0, 0, 2.6*math.sin(math.radians(62))))
        wr = el+d*(2.3*math.cos(math.radians(38)))-Vector((0, 0, 2.3*math.sin(math.radians(38))))
        tip = wr+Vector((0, 0, -1.0))+d*.2
        p = _Parts()
        p.cyl(base+Vector((0, 0, .2)), .7, .4, 'Z', 12)
        p.cyl(base+Vector((0, 0, .6)), .5, .45, 'Z', 10)
        p.box(sh, (.7, .7, .6))
        p.make(kit, f'Gantry manipulator arm {i} base', kit.steel, .03)
        p = _Parts()
        p.strut(sh, el, .5, .4)
        p.strut(sh+d*.35-Vector((0, 0, .1))+side*.33, el-d*.9+side*.33, .16)   # hydraulic ram
        p.make(kit, f'Gantry manipulator arm {i} upper arm', kit.cream, .02)
        p = _Parts()
        p.cyl(el, .34, .75, 'X' if abs(side.x) > abs(side.y) else 'Y', 10)
        p.cyl(wr, .25, .55, 'X' if abs(side.x) > abs(side.y) else 'Y', 10)
        p.make(kit, f'Gantry manipulator arm {i} joints', kit.dark)
        p = _Parts()
        p.strut(el, wr, .38, .32)
        p.make(kit, f'Gantry manipulator arm {i} forearm', kit.cream, .02)
        p = _Parts()
        p.box(wr+Vector((0, 0, -.25)), (.45, .45, .5))
        p.strut(wr+Vector((0, 0, -.5)), tip, .16)
        p.strut(tip, tip+Vector((0, 0, -.25))+d*.1, .1)
        p.make(kit, f'Gantry manipulator arm {i} welding head', kit.steel, .01)

    # Compressor pair forward of the bed: finned cylinders on saddles.
    for i, x in enumerate((22.6, 24.2), 1):
        p = _Parts()
        p.cyl((x, -20.9, t+.95), .68, 5.0, 'Y', 12)
        for k in range(7):
            p.cyl((x, -22.9+k*.62, t+.95), .82, .12, 'Y', 12)
        p.box((x, -23.1, t+.3), (1.3, .5, .6))
        p.box((x, -18.7, t+.3), (1.3, .5, .6))
        p.make(kit, f'Gantry compressor {i}', kit.steel, .02)
    p = _Parts()
    p.cyl((23.4, -18.0, t+.95), .3, .6, 'Y', 8)                         # discharge header
    p.strut((22.6, -18.0, t+.95), (24.2, -18.0, t+.95), .42)
    p.strut((23.4, -17.7, t+.95), (23.4, -16.2, t+.95), .3)
    p.make(kit, 'Gantry compressor header', kit.dark)

    # Tool rack on the inboard strip forward of the bed.
    p = _Parts()
    for x in (22.0, 24.8):
        p.box((x, -14.4, t+1.2), (.2, .2, 2.4))
    p.box((23.4, -14.4, t+2.35), (3.0, .2, .2))
    p.box((23.4, -14.4, t+1.2), (3.0, .16, .14))
    p.box((23.4, -14.4, t+.12), (3.2, 1.4, .24))
    p.make(kit, 'Gantry tool rack frame', kit.steel)
    p = _Parts()
    for k, (dx, ln) in enumerate(((-1.0, 1.0), (-.5, .7), (0, 1.1), (.5, .8), (1.0, .9))):
        p.strut((23.4+dx, -14.55, t+2.25), (23.4+dx, -14.55, t+2.25-ln), .14)
        p.box((23.4+dx, -14.55, t+2.25-ln-.1), (.3, .12, .22))
    p.box((22.9, -14.25, t+.45), (.9, .6, .45))                         # tool chest
    p.box((24.0, -14.25, t+.45), (.9, .6, .45))
    p.make(kit, 'Gantry tool rack tools', kit.cream, .02)

    # Cable reels either side of the hardpoint bed.
    for i, (y, r) in enumerate(((-14.0, 1.15), (-23.5, .9)), 1):
        p = _Parts()
        p.cyl((28.5, y, t+r+.1), r*.62, 1.7, 'X', 12)
        for dx in (-.9, .9):
            p.cyl((28.5+dx, y, t+r+.1), r, .14, 'X', 14)
        p.box((28.5, y, t+.15), (2.0, .6, .3))
        p.make(kit, f'Gantry cable reel {i}', kit.steel, .02)
    p = _Parts()
    for y, r in ((-14.0, 1.15), (-23.5, .9)):
        p.cyl((28.5, y, t+r+.1), r*.72, 1.5, 'X', 12)
    p.make(kit, 'Gantry cable reel windings', kit.dark)

    # Pipe runs along the deck edges and down the aft face to a manifold.
    p = _Parts()
    pr = .24
    p.strut((9.0, -12.95, t+.3), (32.0, -12.95, t+.3), pr*2)
    p.strut((9.0, -24.15, t+.3), (26.5, -24.15, t+.3), pr*2)
    p.strut((9.0, -12.95, t+.3), (8.2, -12.95, t+.3), pr*2)
    p.strut((8.2, -12.95, t+.3), (8.2, -14.0, CZ-1.0), pr*2)
    p.strut((9.0, -24.15, t+.3), (8.2, -24.15, t+.3), pr*2)
    p.strut((8.2, -24.15, t+.3), (8.2, -23.0, CZ-1.0), pr*2)
    for x in (12, 17, 22, 27, 31):
        p.box((x, -12.95, t+.12), (.3, .7, .24))                        # pipe saddles
    for x in (12, 17, 22):
        p.box((x, -24.15, t+.12), (.3, .7, .24))
    p.strut((24.2, -23.2, t+.95), (24.2, -24.15, t+.5), .32)
    p.make(kit, 'Gantry core piping', kit.dark)
    p = _Parts()
    p.box((8.25, -18.5, CZ-1.2), (.5, 9.6, 1.6))                        # aft manifold
    for y in (-21.5, -18.5, -15.5):
        p.strut((8.0, y, CZ-1.2), (7.6, y, CZ-1.2), .2)
        p.cyl((7.5, y, CZ-1.2), .45, .1, 'X', 10)                          # valve wheels
    p.make(kit, 'Gantry core aft manifold', kit.orange, .03)

    # Core faces: plating seams, bolted service covers and grilles. The faces
    # run CORE_BOT..CORE_TOP; two strata bands split them into three courses.
    ch = CORE_TOP-CORE_BOT
    bands = (CORE_BOT+ch/3, CORE_BOT+2*ch/3)
    p = _Parts()
    for x in (12.5, 16.5, 20.5, 24.5, 28.5):
        p.box((x, -24.53, CZ), (.16, .1, ch-.4))
    for y in (-15.5, -18.5, -21.5):
        p.box((32.53, y, CZ), (.1, .16, ch-.4))
    for y in (-15.0, -22.0):
        p.box((8.47, y, CZ), (.1, .16, ch-.4))
    for z in bands:
        p.box((20.5, -24.53, z), (23.6, .1, .14))
        p.box((32.53, -18.5, z), (.1, 11.6, .14))
        p.box((8.47, -18.5, z), (.1, 11.6, .14))
    p.make(kit, 'Gantry core plating seams', kit.dark)
    covers = []                                   # (face, x or y, centre z, w, h)
    p = _Parts()
    for x in (14.5, 22.5):
        for z in (bands[0]-2.6, bands[1]-2.6):
            p.box((x, -24.56, z), (3.0, .14, 2.6))                     # outboard covers
            covers.append(('o', x, z, 3.0, 2.6))
    door = (CORE_BOT+.6, bands[0]-.4)
    p.box((32.56, -18.5, sum(door)/2), (.14, 5.4, door[1]-door[0]))    # forward access door
    for y in (-14.0, -23.0):
        p.box((32.56, y, bands[1]-2.0), (.14, 2.4, 2.6))
        covers.append(('f', y, bands[1]-2.0, 2.4, 2.6))
    p.make(kit, 'Gantry core service covers', kit.grey)
    p = _Parts()
    for face, u, z, w, h in covers:
        for du in (-w/2+.3, w/2-.3):
            for dz in (-h/2+.3, h/2-.3):
                if face == 'o':
                    p.box((u+du, -24.65, z+dz), (.18, .08, .18))
                else:
                    p.box((32.65, u+du, z+dz), (.08, .18, .18))
    for y in (-20.9, -16.1):
        for z in (door[0]+.3, door[1]-.3):
            p.box((32.65, y, z), (.08, .18, .18))
    p.make(kit, 'Gantry core bolt heads', kit.steel)
    gz = bands[1]+1.6
    p = _Parts()
    for x in (18.5, 26.5):
        for z in (bands[0]-1.6, gz):
            p.box((x, -24.52, z), (2.6, .1, 2.4))
    p.box((32.52, -18.5, gz), (.1, 4.0, 1.4))
    p.make(kit, 'Gantry core grille recesses', kit.dark)
    p = _Parts()
    for x in (18.5, 26.5):
        for z in (bands[0]-1.6, gz):
            for k in range(5):
                p.box((x, -24.6, z-.9+k*.45), (2.4, .1, .14))
    for k in range(3):
        p.box((32.6, -18.5, gz-.45+k*.45), (.1, 3.8, .14))
    p.make(kit, 'Gantry core grille slats', kit.steel)


def _lights_and_cab(kit):
    lamp = material('Gantry work floodlight lens', (.9, .86, .74), 0, .3, emission=(1.0, .9, .72), strength=6.0)
    p = _Parts()
    q = _Parts()
    for x, y in ((10.2, -13.5), (10.2, -24.5), (30.3, -13.5), (30.3, -24.5), ((X0+X1)/2-1.6, -24.5)):
        p.strut((x, y, Z1-.7), (x, y, Z1-1.05), .2)
        p.box((x, y, Z1-1.35), (1.0, .9, .6))
        q.box((x, y, Z1-1.68), (.8, .7, .06))
    p.make(kit, 'Gantry work floodlight housings', kit.dark, .03)
    q.make(kit, 'Gantry work floodlight lenses', lamp)

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
    # Vertical bracing on the outboard face between the rails, two tiers.
    p = _Parts()
    zl, zh = lo_top-.15, up_bot+.15
    zm = (zl+zh)/2
    xs = [7.0+k*3.0 for k in range(8)]
    for z0, z1 in ((zl, zm), (zm, zh)):
        for k in range(len(xs)-1):
            a, b = xs[k], xs[k+1]
            if k % 2:
                p.strut((a, -30.5, z1), (b, -30.5, z0), .2)
            else:
                p.strut((a, -30.5, z0), (b, -30.5, z1), .2)
    p.strut((xs[0], -30.5, zm), (xs[-1], -30.5, zm), .2)
    for x in xs:
        p.strut((x, -30.5, zl), (x, -30.5, zh), .18)
    p.make(kit, 'Frame starboard outrigger side bracing', kit.steel)
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
    core = kit.box('Gantry machinery core', (20.5, -18.5, CZ), (24, 12, CORE_TOP-CORE_BOT), kit.grey, .3)
    base = prism(kit, 'Gantry base deck', [(7, -29.5), (33, -29.5), (33, -11), (7, -11)],
                 section(Z0-1.2, Z0+.6, .4, .3), kit.steel)
    # The bed is a seat: WS 008 ray-casts onto its top (ENVELOPE.md: hardpoint on bed top +8).
    bed = kit.box('Gantry forward hardpoint bed', (28.5, -19, (BED_BOT+BED_TOP)/2), (7, 7, BED_TOP-BED_BOT),
                  kit.steel, .2)
    kit.hull += [core, base, bed]
    p = _Parts()
    for y in (-15.4, -22.6):
        p.box((28.5, y, BED_TOP+.12), (6.6, .2, .24))                 # bed coaming
    p.make(kit, 'Gantry forward hardpoint bed coaming', kit.dark)
    _frame(kit)
    _crane(kit)
    _core_plant(kit)
    _lights_and_cab(kit)
    _rails(kit)
    _turret_deck(kit)
    _outrigger(kit)
