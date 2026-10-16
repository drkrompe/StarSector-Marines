"""The two wing arms and the fly-through hangar under each of them.

Owner review of the first blockout asked for wing-like arms off a thick torso
with the hangar bays under the wings, and later: "the hangar bays should flow
through and under the wing ... each wing is a fly-through hangar under it."
Each sponson (Y 74-147 at the beam) is a thin arm held out from the torso a
step below its deck (z -8..4), and under it hangs ONE hangar ('Wing <side>
hangar', X -64..104, Y 79.5-130) open at both ends:
- its forward mouth is the arm's angled leading edge (X 100, Y 81 to X 63,
  Y 123), facing forward and outboard; the .ship's WS 012/013 launch out of it
  (slot angle +-10);
- its aft mouth opens at X -64 into the notch between the arm's tail leg and
  the engine block (its outboard part under the tail leg); WS 010/011 launch
  out of it (slot angle 170 / 190).
A fighter can fly straight through from one mouth to the other. The clear
section is 46 wide aft of X 40 (Y 82-128) and 42 under the leading edge (Y
81-123), where the arm's edge comes in, and 13 high: room for the widest wing
the Legion carries (Hoplon, 36 long x 38 span in model units). The sprite's two
red-lit recesses on each arm are elevator wells through the arm into it. Each
slot keeps its own floor plate, lights, mouth and four launch points (from
legion.ship unchanged, on the floor). The arm deck stays intact over the hangar
(turrets WS 002/003, 014/015, 018/019 and the outer belt's arm run sit on it);
lg_droop shears all of it into the arms' anhedral after this builds level.

Pass 2 (plausible detail for C2, where the starboard arm's outboard wall is
the near flank, ~105 m away and in deep shadow): the arm's outboard wall is
real armour rather than one skin. The sponson core stops 3.5 inboard of the
plan edge and the edge is built out again as three stacked strakes - an upper
strake rising just proud of the deck, a proud main strake whose top is an
upward ledge for the high sun, and a lower skirt hanging below the arm, set
back so the main strake overhangs it (a downward face for planet-shine). Each
strake is a dark continuous backing carrying separate face plates with real
seam gaps; vertical buttresses tie the strakes together, and framed hatches
(2.1 m doors) and bolted service covers break the plating. The leading edge
over the forward mouth carries the same upper and main strakes over a header
beam; the tail leg's inboard edge and the notch carry upper and main strakes.
The hangar's walls carry frames, a rubbing strake and a cap strake whose
undersides slope for planet-shine; its roof carries transverse frames, wall
posts, two longitudinal girders and red light lines; its mouths have jambs,
header and an apron sill. Brackets and a girder carry the arm outboard of the
hangar, ribs cross under the tail leg, and the elevator wells have deck
coamings with the amber edge lamps the sprite shows.
"""
import math
import bpy
from lg_kit import boolean, ccw, clip_rect, inset, material, mirror_y, plane_cut, prism, section

KEEL, DECK = -8.0, 4.0       # the arm: its deck is a 6-unit step below the torso's (z 10)
FLOOR = -21.0                # hangar floor
ROOF = -8.0                  # hangar ceiling, under the arm (13 high: Hoplon plus clearance)
POD = (-25.0, -2.5)          # hangar pod underside and top (sunk into the arm)
POD_CHAMFER = (4.5, -20.5)   # the pod's keel chamfer: inset at the underside, height where the wall starts

SPONSON_PORT = [(104, 70), (104, 82), (96, 94), (88, 106), (84, 113), (76, 118), (72, 122), (60, 124),
                (52, 126), (40, 131), (28, 135), (20, 138), (-8, 139), (-24, 139), (-40, 142), (-52, 142),
                (-60, 145), (-68, 147), (-72, 146), (-76, 142), (-80, 140), (-92, 140), (-96, 132),
                (-98, 126), (-96, 125), (-92, 120), (-80, 119), (-76, 118), (-72, 113), (-68, 110),
                (-64, 108), (-64, 88), (-60, 76), (84, 74), (96, 72)]
SPONSON = section(KEEL, DECK, 4.0, 2.5)
TAPER = (84.0, 6.0)          # the arm's underside rises outboard of |Y| 84 at 6 degrees

# Outboard armour. The sponson core is inset by CORE at vertices 0..31 of SPONSON_PORT; the
# strakes fill that band back out to the plan edge. Chains are vertex index runs.
CORE = 3.5
CORE_VERTS = range(0, 32)
CHAIN_LEAD = (0, 6)          # the leading edge over the forward hangar mouth
CHAIN_OUT = (6, 23)          # the outboard wall and the tail leg's end
CHAIN_TAIL = (23, 30)        # the tail leg's inboard edge (the notch edge 30-31 is the aft mouth's)
SEAM = .8                    # face-plate seam gap
# Rows: (backing profile, plate profile, plate length), profiles as (inset d, z).
UPPER = ([(2.3, -.8), (2.3, 4.1), (3.9, 4.1), (3.9, -.8)],
         [(1.4, -.6), (1.4, 3.7), (2.0, 4.3), (2.5, 4.3), (2.5, -.6)], 14.0)
MID = ([(1.1, -5.4), (1.1, -.8), (3.9, -.8), (3.9, -5.4)],
       [(.6, -4.8), (0.0, -1.5), (.5, -1.0), (1.2, -1.0), (1.2, -4.8)], 17.0)
MID_SILL = [(.5, -5.4), (.5, -4.8), (1.2, -4.8), (1.2, -5.4)]   # continuous bar under the main plates
SKIRT = ([(3.4, -9.2), (3.4, -5.4), (4.4, -5.4), (4.4, -9.2)],
         [(1.6, -7.0), (3.2, -9.6), (3.6, -9.6), (3.6, -5.4), (1.6, -5.4)], 11.0)
HEADER = [(0.0, ROOF), (0.0, -5.4), (3.9, -5.4), (3.9, ROOF)]    # beam over the forward mouth
BUTTRESS = [(1.2, -10.2), (-.5, -7.6), (-.5, 4.0), (.4, 4.9), (2.6, 4.9), (2.6, -10.2)]
BUTTRESS_W, BUTTRESS_STEP = 1.8, 7.5
HATCH_W, HATCH_H = 2.4, 3.4  # 2.1 m doors at 0.61 m per unit
# Hatches and covers on the outboard chain: (segment start vertex, t along it).
HATCHES = [(9, .55), (12, .3), (14, .6), (20, .5)]
COVERS = [(7, .5), (10, .45), (12, .75), (13, .5), (16, .5), (21, .4)]
SKIRT_COVERS = [(11, .4), (15, .5)]

# Hidden LAUNCH_BAY slots, exactly as legion.ship has them.
LAUNCH_BAYS = [
    {'id': 'WS 010', 'angle': 170, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (-27, 103, -35, 91, -44, 105, -51, 92)},
    {'id': 'WS 011', 'angle': 190, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (-35, -90, -26, -102, -50, -92, -40, -104)},
    {'id': 'WS 012', 'angle': 10, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (47, 104, 59, 92, 63, 105, 72, 90)},
    {'id': 'WS 013', 'angle': -10, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (59, -92, 48, -104, 72, -90, 64, -105)},
]
# One fly-through hangar under each arm (port values; starboard mirrors).
# pod: the shell's X and Y extent, clipped to the arm's plan (its front to the
# armour core, so the forward mouth sits behind the leading edge's header and
# sill); tunnel: the clear hollow, two boxes (wider aft of X 40, narrower under
# the leading edge where the arm's edge comes in), open at both ends.
HANGAR = {'pod': (-64, 118, 79.5, 130),
          'tunnel': [(-80, 42, 82, 128), (38, 130, 81, 123)],
          'x': (-64.0, 104.0)}       # aft mouth plane to the forward mouth's inboard end
# Per slot end: the floor plate it launches from, its elevator well (the sprite's
# red-lit recess) and its mouth (centre and chord, model XY at mid-height).
DECKS = {
    'forward': {'deck_x': (40, 118), 'deck_y': (81, 123), 'open_above': (46, 80), 'well_y': (88, 110),
                'mouth': (82.0, 101.5), 'mouth_width': 56.0, 'clear_width': 42.0, 'normal': (.742, .671, 0)},
    'aft': {'deck_x': (-64, 40), 'deck_y': (82, 128), 'open_above': (-60, -16), 'well_y': (88, 110),
            'mouth': (-64.0, 105.0), 'mouth_width': 46.0, 'clear_width': 46.0, 'normal': (-1, 0, 0)},
}
LIGHT_ROWS = 4               # red light strips along each hangar floor (the sprite's red lines)
# Amber lamps the sprite shows beside the wells (port X, Y).
AMBER = {'forward': [(53, 111.15), (59, 111.15), (65, 111.15)],
         'aft': [(-50, 83), (-56, 83), (-62, 83), (-68, 83), (-62, 118), (-68, 118)]}


def bay_of(slot):
    side = 'port' if slot['locations'][1] > 0 else 'starboard'
    end = 'forward' if abs(slot['angle']) < 90 else 'aft'
    return side, end


def deck_record(slot):
    """The flight deck as the cinematic needs it: launch points, its own mouth, and the shared hangar."""
    side, end = bay_of(slot)
    d = DECKS[end]
    sy = 1 if side == 'port' else -1
    a = math.radians(slot['angle'])
    loc = slot['locations']
    mx, my = d['mouth']
    nx, ny, nz = d['normal']
    name = f"Flight deck {slot['id']}"
    ys = [y for t in HANGAR['tunnel'] for y in t[2:]]
    return {
        'slot': slot['id'], 'side': side, 'end': end, 'objects': name+' ...',
        'slot_angle': slot['angle'], 'arc': slot['arc'],
        'launch_direction': [round(math.cos(a), 4), round(math.sin(a), 4), 0.0],
        'recovery_approach': [round(-math.cos(a), 4), round(-math.sin(a), 4), 0.0],
        'launch_points': [[loc[i], loc[i+1], FLOOR+.5] for i in range(0, 8, 2)],
        'launch_point_anchors': [f'{name} launch point {k}' for k in range(1, 5)],
        'mouth': {'anchor': f'{name} mouth', 'centre': [mx, sy*my, (FLOOR+ROOF)/2],
                  'normal': [nx, sy*ny, nz], 'width': d['mouth_width'], 'height': ROOF-FLOOR,
                  'clear_width': d['clear_width'],
                  'note': 'the forward mouth is the angled leading edge, the aft mouth opens into the notch'},
        'deck': {'floor_z': FLOOR, 'roof_z': ROOF, 'x': list(d['deck_x']),
                 'y': sorted((sy*d['deck_y'][0], sy*d['deck_y'][1])), 'under_wing': True,
                 'elevator_well_x': list(d['open_above']),
                 'elevator_well_y': sorted((sy*d['well_y'][0], sy*d['well_y'][1]))},
        'hangar': {'name': f'Wing {side} hangar', 'x': list(HANGAR['x']),
                   'y': sorted((sy*min(ys), sy*max(ys))), 'floor': FLOOR, 'roof': ROOF, 'fly_through': True,
                   'clear_sections': [{'x': [t[0], t[1]], 'y': sorted((sy*t[2], sy*t[3]))} for t in HANGAR['tunnel']],
                   'note': 'one hangar per arm shared by both slots, open at both mouths; the roof carries '
                           'transverse frames (0.6 deep) and two longitudinal girders (0.9 deep)'},
    }


# --- merged-geometry helpers -------------------------------------------------

class Acc:
    """Vertices and faces of many small closed shells, merged into one object."""
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += [tuple(v) for v in verts]
        self.faces += [tuple(o+i for i in f) for f in faces]

    def hexa(self, c):
        """Hexahedron from 8 corners: bottom loop then top loop, same winding."""
        self.add(c, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)])

    def box(self, x0, x1, y0, y1, z0, z1):
        self.hexa([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                   (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)])

    def build(self, kit, name, mat, sy=1, hull=False):
        if not self.faces:
            return None
        verts = [(x, sy*y, z) for x, y, z in self.verts]
        ob = kit.mesh(name, verts, self.faces, mat)
        if hull:
            kit.hull.append(ob)
        return ob


class Edge:
    """Mitred offset lines of the sponson plan: point at (vertex i -> i+1, t, inset d)."""
    def __init__(self, plan):
        self.p0 = plan
        self.p1 = inset(plan, 1.0)
        self.n = len(plan)

    def off(self, i, d):
        (ax, ay), (bx, by) = self.p0[i % self.n], self.p1[i % self.n]
        return ax+d*(bx-ax), ay+d*(by-ay)

    def pt(self, i, t, d):
        a, b = self.off(i, d), self.off(i+1, d)
        return a[0]+t*(b[0]-a[0]), a[1]+t*(b[1]-a[1])

    def length(self, i):
        (ax, ay), (bx, by) = self.p0[i % self.n], self.p0[(i+1) % self.n]
        return math.hypot(bx-ax, by-ay)

    def module(self, acc, i, t0, t1, profile):
        """Prism of a (d, z) profile swept along segment i between t0 and t1."""
        m = len(profile)
        verts = [(*self.pt(i, t, d), z) for t in (t0, t1) for d, z in profile]
        faces = [tuple(reversed(range(m))), tuple(range(m, 2*m))]
        faces += [(k, (k+1) % m, m+(k+1) % m, m+k) for k in range(m)]
        acc.add(verts, faces)


def _plates(L, length, cuts=()):
    """Plate intervals (t0, t1) along a segment of length L, seams SEAM wide, minus cut intervals."""
    n = max(1, round(L/length))
    g = SEAM/2/L
    out = [(j/n+g, (j+1)/n-g) for j in range(n)]
    for c0, c1 in cuts:
        nxt = []
        for a, b in out:
            if b <= c0 or a >= c1:
                nxt.append((a, b))
                continue
            if c0-a > 1.0/L:
                nxt.append((a, c0))
            if b-c1 > 1.0/L:
                nxt.append((c1, b))
        out = nxt
    return [(a, b) for a, b in out if b > a]


def _blunt(poly, cut=3.0, limit=75.0):
    """A CCW plan with every corner sharper than `limit` degrees cut back by `cut`, so the pod's
    keel chamfer (a mitred inset) cannot fold through itself at a clipped sliver corner."""
    out = []
    n = len(poly)
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = poly[i-1], poly[i], poly[(i+1) % n]
        u, v = (ax-bx, ay-by), (cx-bx, cy-by)
        lu, lv = math.hypot(*u), math.hypot(*v)
        ang = math.degrees(math.acos(max(-1.0, min(1.0, (u[0]*v[0]+u[1]*v[1])/(lu*lv)))))
        if ang < limit and lu > cut and lv > cut:
            out += [(bx+u[0]/lu*cut, by+u[1]/lu*cut), (bx+v[0]/lv*cut, by+v[1]/lv*cut)]
        else:
            out.append((bx, by))
    return out


def _underside(y):
    """The arm's soffit height at |y| (taper outboard of TAPER[0])."""
    return KEEL+max(0.0, abs(y)-TAPER[0])*math.tan(math.radians(TAPER[1]))


def _edge_y(chain, x):
    """Y of a plan polyline (list of (x, y)) at station x: the first segment spanning x."""
    for (ax, ay), (bx, by) in zip(chain, chain[1:]):
        if min(ax, bx) <= x <= max(ax, bx) and ax != bx:
            return ay+(by-ay)*(x-ax)/(bx-ax)
    return None


def _edge_x(chain, y):
    for (ax, ay), (bx, by) in zip(chain, chain[1:]):
        if min(ay, by) <= y <= max(ay, by) and ay != by:
            return ax+(bx-ax)*(y-ay)/(by-ay)
    return None


# --- the outboard armour ----------------------------------------------------

def armour(edge, accs):
    """Strakes, buttresses, hatches and covers along the armour chains (port side)."""
    plate, tan, back, frame, door = accs

    def row(i, spec, cuts=(), phase=0):
        backing, prof, length = spec
        edge.module(back, i, 0.0, 1.0, backing)
        for k, (a, b) in enumerate(_plates(edge.length(i), length, cuts)):
            p = prof
            if spec is UPPER and (i+k) % 4 == 1:     # some rim plates stand a little prouder
                p = [(d, z+.4 if z > 4.0 else z) for d, z in prof]
            edge.module(tan if (i*7+k+phase) % 3 == 0 else plate, i, a, b, p)

    for i in range(*CHAIN_LEAD):
        row(i, UPPER)
        row(i, MID, phase=1)
        edge.module(frame, i, 0.0, 1.0, HEADER)
    for i in range(*CHAIN_TAIL):
        row(i, UPPER)
        row(i, MID, phase=1)
        edge.module(back, i, 0.0, 1.0, MID_SILL)
    row(CHAIN_TAIL[1], UPPER)          # the notch edge: upper strake over the aft mouth's header
    hatches = {}
    for i, t in HATCHES:
        hatches.setdefault(i, []).append(t)
    for i in range(*CHAIN_OUT):
        L = edge.length(i)
        cuts = [(t-(HATCH_W/2+.6)/L, t+(HATCH_W/2+.6)/L) for t in hatches.get(i, [])]
        row(i, UPPER)
        row(i, MID, cuts, phase=1)
        edge.module(back, i, 0.0, 1.0, MID_SILL)
        row(i, SKIRT, phase=2)
        # Framed hatches: a recessed door in a proud frame, set in the main strake.
        for t in hatches.get(i, []):
            hw, fw = HATCH_W/2/L, (HATCH_W/2+.5)/L
            z0, z1 = -4.75, -4.75+HATCH_H
            edge.module(door, i, t-hw, t+hw, [(.7, z0), (.7, z1), (1.3, z1), (1.3, z0)])
            for a, b in ((t-fw, t-hw), (t+hw, t+fw)):
                edge.module(frame, i, a, b, [(-.25, z0-.3), (-.25, z1+.3), (1.3, z1+.3), (1.3, z0-.3)])
            edge.module(frame, i, t-hw, t+hw, [(-.25, z1), (-.25, z1+.3), (1.3, z1+.3), (1.3, z1)])
            edge.module(frame, i, t-hw, t+hw, [(-.25, z0-.3), (-.25, z0), (1.3, z0), (1.3, z0-.3)])
        # Buttresses tie the three strakes together, avoiding the hatches.
        n = int(L//BUTTRESS_STEP)
        for j in range(1, n+1):
            t = j/(n+1)
            if any(abs(t-h)*L < 4 for h in hatches.get(i, [])):
                continue
            w = BUTTRESS_W/2/L
            edge.module(frame, i, t-w, t+w, BUTTRESS)
    # Bolted service covers: proud plates on the upper strake and the skirt.
    for i, t in COVERS:
        w = 1.5/edge.length(i)
        edge.module(frame, i, t-w, t+w, [(1.0, .4), (1.0, 2.8), (1.5, 2.8), (1.5, .4)])
    for i, t in SKIRT_COVERS:
        w = 1.6/edge.length(i)
        edge.module(frame, i, t-w, t+w, [(1.2, -6.9), (1.2, -5.6), (1.7, -5.6), (1.7, -6.9)])


def under_structure(acc, plan):
    """Ribs under the arm outboard of the hangar, and under the tail leg (port side)."""
    outer = [plan[k] for k in range(CHAIN_OUT[0], CHAIN_OUT[1]+1)]
    inner = [plan[k] for k in range(CHAIN_TAIL[0], CHAIN_TAIL[1]+1)]
    wall = HANGAR['pod'][3]

    def rib(x, y0, y1, w, depth):
        ys = [y0] + ([TAPER[0]] if y0 < TAPER[0] < y1 else []) + [y1]
        for a, b in zip(ys, ys[1:]):
            za, zb = _underside(a), _underside(b)
            acc.hexa([(x-w/2, a, za-depth), (x+w/2, a, za-depth), (x+w/2, b, zb-depth), (x-w/2, b, zb-depth),
                      (x-w/2, a, za+.8), (x+w/2, a, za+.8), (x+w/2, b, zb+.8), (x-w/2, b, zb+.8)])

    # Brackets from the hangar's outboard wall out to the skirt, where the arm reaches past it.
    x = -61.0
    while x < 40:
        y1 = _edge_y(outer, x)-4.4
        if y1-wall > 2.0:
            rib(x, wall-.5, y1, 1.3, 2.4)
        x += 7.5
    # A girder along the arm under those brackets.
    z = _underside(wall+3.0)
    acc.box(-58.0, 20.0, wall+2.4, wall+3.6, z-3.2, z+.8)
    # Under the tail leg: ribs across it and one keel girder along it.
    for x in (-71.0, -78.0, -85.0, -92.0):
        y0, y1 = _edge_y(inner, x), _edge_y(outer, x)
        if y0 is None or y1 is None:
            continue
        rib(x, y0+2.0, y1-4.4, 1.3, 2.0)
    yk = 131.0
    z = _underside(yk)
    acc.box(-95.0, -66.0, yk-.6, yk+.6, z-3.0, z+.8)


# --- the hangar ---------------------------------------------------------------

def _tunnel_y(x, lead):
    """The hangar's clear Y range at station x (port), or None past the forward mouth."""
    for x0, x1, y0, y1 in reversed(HANGAR['tunnel']):
        if x0 <= x <= x1:
            yf = _edge_y(lead, x)
            if yf is not None and yf < y0+6:
                return None
            return y0, (min(y1, yf-5.5) if yf is not None else y1)
    return None


def hangar_shell_detail(kit, side, sy, plan):
    """Frames and strakes on the hangar's walls, and the roof frames, girders and wall posts inside it."""
    frames, ribs = Acc(), Acc()
    lead = [plan[k] for k in range(0, 9)]   # (104,70) .. (52,126): the arm's leading edge
    px0, px1, py0, py1 = HANGAR['pod']
    zt = POD[1]-.9

    def band(fixed, a0, a1, w, z_top, z_in, z_out):
        """A proud strake along a wall at Y = fixed whose underside slopes down to the wall (planet-shine)."""
        f1 = fixed+w
        frames.hexa([(a0, fixed, z_in), (a1, fixed, z_in), (a1, f1, z_out), (a0, f1, z_out),
                     (a0, fixed, z_top), (a1, fixed, z_top), (a1, f1, z_top), (a0, f1, z_top)])

    def wall_frames(fixed, a0, a1, step, out):
        lo, hi = sorted((fixed, fixed+out))
        a = a0+step/2
        while a < a1-1:
            frames.box(a-.7, a+.7, lo, hi, POD_CHAMFER[1]+.3, zt)
            a += step
        band(fixed, a0, a1, 1.5*out, -13.0, -15.6, -14.0)     # rubbing strake
        band(fixed, a0, a1, 1.3*out, zt, -6.6, -5.4)          # cap strake under the arm

    wall_frames(py1, px0+1.0, 41.0, 6.0, .9)     # the long outboard wall
    wall_frames(79.0, 48.0, 99.0, 7.0, -.9)      # inboard wall where it stands clear of the torso
    # Roof: transverse frames and shallow wall posts the whole length, two girders along it.
    x = px0+3.0
    while True:
        r = _tunnel_y(x, lead)
        if r is None or x > 101:
            break
        y0, y1 = r
        ribs.box(x-.4, x+.4, y0, y1, ROOF-.6, ROOF+.3)
        ribs.box(x-.35, x+.35, y0, y0+.5, FLOOR, ROOF)
        if y1 >= HANGAR['tunnel'][0 if x <= 40 else 1][3]-.01:
            ribs.box(x-.35, x+.35, y1-.5, y1, FLOOR, ROOF)
        x += 6.0
    for y in (95.0, 115.0):
        xe = _edge_x(lead, y)-6.0
        ribs.box(px0+1.0, xe, y-.6, y+.6, ROOF-.9, ROOF+.3)
    frames.build(kit, f'Wing {side} hangar frames', kit.plate, sy)
    ribs.build(kit, f'Wing {side} hangar roof frames', kit.steel, sy)


def deck_detail(kit, name, end, sy, plan, mats):
    """One slot's end of the hangar: its mouth frame, ceiling lights, well coaming and lamps."""
    d = DECKS[end]
    wx0, wx1 = d['open_above']
    wy0, wy1 = d['well_y']
    mouth, coaming = Acc(), Acc()
    lead = [plan[k] for k in range(0, 9)]
    px0, px1, py0, py1 = HANGAR['pod']
    sill = [(-.3, FLOOR-.2), (0.0, POD[0]-.6), (4.5, POD[0]-.6), (4.5, FLOOR-.2)]
    if end == 'forward':
        ty0, ty1 = HANGAR['tunnel'][1][2:]
        e = Edge(plan)
        for i in range(0, 8):          # apron sill along the leading edge under the mouth
            ya, yb = plan[i][1], plan[i+1][1]
            if yb <= ya or yb <= 79.0 or ya >= ty1+2:
                continue
            t0 = max(0.0, (79.0-ya)/(yb-ya))
            t1 = min(1.0, (ty1+2-ya)/(yb-ya))
            e.module(mouth, i, t0, t1, sill)
        mouth.box(100.0, 104.4, 77.8, ty0, POD[0]-.4, ROOF+.5)                         # inboard jamb
        x_out = _edge_x(lead, ty1)
        mouth.box(x_out-4.0, x_out, ty1, ty1+2.0, POD[0]-.4, ROOF+.5)                  # outboard jamb
        lights = [(ty0+3.0, 40.0, _edge_x(lead, ty0+3.0)-6), (ty1-3.0, 40.0, _edge_x(lead, ty1-3.0)-6)]
        # The outer wall of the forward hangar box on the deck, outboard of the well (sprite X 40-76).
        box = Acc()
        box.box(40.0, 75.0, wy1+1.4, 119.8, DECK-.5, DECK+2.2)
        box.box(43.0, 72.0, wy1+2.6, 118.6, DECK+2.2, DECK+3.0)
        box.build(kit, name+' box wall', kit.plate, sy)
        lamp_z = DECK+1.4
    else:
        ty0, ty1 = HANGAR['tunnel'][0][2:]
        # (no two boxes share a face plane: coincident faces in one mesh rendered black)
        mouth.box(-66.5, -63.0, py0+.2, py1+1.1, ROOF-.4, POD[1]-.2)            # header
        mouth.box(-68.5, -61.6, py0-.3, py1+1.3, POD[0]-.6, FLOOR-.2)           # apron sill
        mouth.box(-67.5, -62.2, py0-1.5, ty0, POD[0]-.4, POD[1])                # jambs
        mouth.box(-67.5, -62.2, ty1, py1+1.6, POD[0]-.4, POD[1])
        lights = [(ty0+3.0, -63.0, 40.0), (ty1-3.0, -63.0, 40.0)]
        lamp_z = None
    c = 1.2                            # coaming round the elevator well on the arm deck
    coaming.box(wx0-c, wx1+c, wy0-c, wy0, DECK-.5, DECK+1.0)
    coaming.box(wx0-c, wx1+c, wy1, wy1+c, DECK-.5, DECK+1.0)
    coaming.box(wx0-c, wx0, wy0, wy1, DECK-.5, DECK+1.0)
    coaming.box(wx1, wx1+c, wy0, wy1, DECK-.5, DECK+1.0)
    mouth.build(kit, name+' mouth frame', kit.steel, sy)
    coaming.build(kit, name+' well coaming', kit.steel, sy)
    for k, (y, xa, xb) in enumerate(lights, 1):        # red light lines along the roof
        kit.box(f'{name} ceiling light {k}', ((xa+xb)/2, sy*y, ROOF-.1), (xb-xa, .7, .3), kit.bay_light, bevel=0)
    for k, y in enumerate((wy0+.15, wy1-.15), 1):       # and on the well walls
        kit.box(f'{name} well light {k}', ((wx0+wx1)/2, sy*y, -1.0), (wx1-wx0-2, .3, .5), kit.bay_light, bevel=0)
    for k, (x, y) in enumerate(AMBER[end], 1):          # amber edge lamps where the sprite shows them
        if lamp_z is None:
            hit = kit.surface(x, sy*y, top=120)
            z = (hit[0].z if hit else DECK)+.25
            kit.box(f'{name} amber lamp {k}', (x, sy*y, z), (.9, .9, .5), mats['amber'], bevel=0)
        else:
            kit.box(f'{name} amber lamp {k}', (x, sy*y, lamp_z), (.9, .5, .9), mats['amber'], bevel=0)


def build(kit):
    mats = {'tan': bpy.data.materials.get('XIV tan hull plating') or
            material('XIV tan hull plating', (.36, .30, .24), .3, .62),
            'amber': bpy.data.materials.get('Hangar amber lamp') or
            material('Hangar amber lamp', (.1, .05, .01), 0, .4, emission=(1.0, .55, .12), strength=10.0)}
    plan_port = ccw(SPONSON_PORT)
    core_in = inset(plan_port, CORE)
    core_port = [core_in[i] if i in CORE_VERTS else p for i, p in enumerate(plan_port)]
    # The hangar's front follows the armour core, so its forward mouth opens behind the header and sill.
    hpod_port = [core_in[i] if i <= CHAIN_LEAD[1] else p for i, p in enumerate(plan_port)]
    for side in ('port', 'starboard'):
        sy = 1 if side == 'port' else -1
        flip = (lambda p: p) if side == 'port' else mirror_y
        plan, core = flip(plan_port), flip(core_port)
        sponson = prism(kit, f'Wing {side} sponson', core, SPONSON, kit.plate)
        a = math.radians(TAPER[1])
        plane_cut(sponson, f'Wing {side} sponson underside taper', (0, sy*TAPER[0], KEEL),
                  (0, sy*math.sin(a), -math.cos(a)), size=600)
        kit.hull.append(sponson)
        # Outboard armour, built on the port plan and mirrored.
        accs = [Acc() for _ in range(5)]
        armour(Edge(plan_port), accs)
        accs[0].build(kit, f'Wing {side} armour plates', kit.plate, sy, hull=True)
        accs[1].build(kit, f'Wing {side} armour plates tan', mats['tan'], sy, hull=True)
        accs[2].build(kit, f'Wing {side} armour backing', kit.dark, sy, hull=True)
        accs[3].build(kit, f'Wing {side} armour frames', kit.steel, sy, hull=True)
        accs[4].build(kit, f'Wing {side} armour hatch doors', kit.dark, sy)
        under = Acc()
        under_structure(under, plan_port)
        under.build(kit, f'Wing {side} underside girders', kit.steel, sy)
        # The one fly-through hangar under the arm, clipped to the arm's plan, open at both ends.
        px0, px1, py0, py1 = HANGAR['pod']
        plo, phi = sorted((sy*py0, sy*py1))
        hangar = prism(kit, f'Wing {side} hangar', _blunt(ccw(clip_rect(flip(hpod_port), px0, px1, plo, phi))),
                       [(POD_CHAMFER[0], POD[0]), (0, POD_CHAMFER[1]), (0, POD[1])], kit.plate)
        for tx0, tx1, ty0, ty1 in HANGAR['tunnel']:
            tlo, thi = sorted((sy*ty0, sy*ty1))
            tunnel = kit.box('Temporary tunnel', ((tx0+tx1)/2, (tlo+thi)/2, (FLOOR+ROOF)/2),
                             (tx1-tx0, thi-tlo, ROOF-FLOOR), kit.dark, bevel=0)
            boolean(hangar, tunnel)
        hangar_shell_detail(kit, side, sy, plan_port)
        for slot in LAUNCH_BAYS:
            s, end = bay_of(slot)
            if s != side:
                continue
            d = DECKS[end]
            name = f"Flight deck {slot['id']}"
            # The elevator well through the arm into the hangar (the sprite's red-lit recess).
            ox0, ox1 = d['open_above']
            wlo, whi = sorted((sy*d['well_y'][0], sy*d['well_y'][1]))
            well = kit.box('Temporary well', ((ox0+ox1)/2, (wlo+whi)/2, (ROOF+30)/2),
                           (ox1-ox0, whi-wlo, 30-ROOF+.5), kit.dark, bevel=0)
            boolean(sponson, well, keep=True)
            boolean(hangar, well)
            # This slot's deck plate on the hangar floor, then the red light strips along it.
            dx0, dx1 = d['deck_x']
            dlo, dhi = sorted((sy*d['deck_y'][0], sy*d['deck_y'][1]))
            floor = ccw(clip_rect(plan, dx0+.5, dx1, dlo+.2, dhi-.2))
            prism(kit, name+' deck', floor, [(0, FLOOR-.8), (0, FLOOR+.1)], kit.deck, bevel=.05)
            for k in range(LIGHT_ROWS):
                ly = wlo+(k+1)*(whi-wlo)/(LIGHT_ROWS+1)
                kit.box(f'{name} red light {k+1}', ((ox0+ox1)/2, ly, FLOOR+.18), (ox1-ox0-4, .7, .15),
                        kit.bay_light, bevel=0)
            deck_detail(kit, name, end, sy, plan_port, mats)
            rec = deck_record(slot)
            kit.empty(name+' mouth', tuple(rec['mouth']['centre']),
                      math.degrees(math.atan2(rec['mouth']['normal'][1], rec['mouth']['normal'][0])))
            for k, p in enumerate(rec['launch_points'], 1):
                kit.empty(f'{name} launch point {k}', tuple(p), slot['angle'])
        kit.hull.append(hangar)
